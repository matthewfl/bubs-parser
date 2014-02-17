/*
 * Copyright 2010-2012 Aaron Dunlop and Nathan Bodenstab
 * 
 * This file is part of the BUBS Parser.
 * 
 * The BUBS Parser is free software: you can redistribute it and/or 
 * modify  it under the terms of the GNU Affero General Public License 
 * as published by the Free Software Foundation, either version 3 of 
 * the License, or (at your option) any later version.
 * 
 * The BUBS Parser is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with the BUBS Parser. If not, see <http://www.gnu.org/licenses/>
 */

package edu.berkeley.nlp.PCFGLA;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cltool4j.BaseLogger;
import cltool4j.GlobalConfigProperties;
import edu.berkeley.nlp.PCFGLA.GrammarMerger.MergeCandidate;
import edu.berkeley.nlp.PCFGLA.GrammarMerger.MergeRanking;
import edu.berkeley.nlp.PCFGLA.GrammarMerger.MergeRanking.MergeObjectiveFunction;
import edu.berkeley.nlp.util.Numberer;
import edu.ohsu.cslu.datastructs.narytree.BinaryTree;
import edu.ohsu.cslu.datastructs.narytree.NaryTree;
import edu.ohsu.cslu.grammar.DecisionTreeTokenClassifier;
import edu.ohsu.cslu.grammar.GrammarFormatType;
import edu.ohsu.cslu.grammar.LeftCscSparseMatrixGrammar;
import edu.ohsu.cslu.lela.ConstrainedCellSelector;
import edu.ohsu.cslu.parser.ParseTask;
import edu.ohsu.cslu.parser.Parser;
import edu.ohsu.cslu.parser.Parser.ResearchParserType;
import edu.ohsu.cslu.parser.ParserDriver;
import edu.ohsu.cslu.parser.cellselector.CompleteClosureModel;
import edu.ohsu.cslu.parser.fom.BoundaryPosModel;
import edu.ohsu.cslu.parser.fom.FigureOfMeritModel.FOMType;
import edu.ohsu.cslu.parser.ml.CartesianProductHashSpmlParser;
import edu.ohsu.cslu.parser.ml.ConstrainedCphSpmlParser;
import edu.ohsu.cslu.util.Evalb.BracketEvaluator;

/**
 * Performs pruned inference on a development set using each {@link MergeCandidate}, and ranks the candidates by a
 * combination of those two factors (Note that in this case, {@link MergeCandidate#estimatedAccuracyRanking()} and
 * {@link MergeCandidate#estimatedSpeedRanking()} are determined empirically and not in fact estimates).
 * 
 * @author Aaron Dunlop
 */
public class DiscriminativeMergeObjectiveFunction extends MergeObjectiveFunction {

    private final static Numberer NUMBERER = Numberer.getGlobalNumberer("tags");

    private float splitF1;
    private float splitSpeed;

    private Grammar splitGrammar;
    private Lexicon splitLexicon;
    private float minRuleProbability;
    private List<String> trainingCorpus;
    private List<String> developmentSet;
    private CompleteClosureModel ccModel;
    private int beamWidth;

    private final static String PROPERTY_BEAM_WIDTHS = "beamWidths";
    private final static String DEFAULT_BEAM_WIDTHS = "15,15,20,20,30,30";

    private final static String PROPERTY_PARSE_FRACTION = "discParseFraction";
    private final static float DEFAULT_PARSE_FRACTION = .5f;

    /**
     * Fraction of merge candidates to parse - if x < 1, we estimate likelihood loss (as in the Likelihood
     * {@link MergeRanking}), retain the top (1 - x) / 2 and discard the bottom (1 - x) / 2, and perform inference to
     * evaluate the candidates in between
     */
    private final static float PARSE_FRACTION = GlobalConfigProperties.singleton().getFloatProperty(
            PROPERTY_PARSE_FRACTION, DEFAULT_PARSE_FRACTION);

    @SuppressWarnings("hiding")
    public void init(final CompleteClosureModel ccModel, final List<String> trainingCorpus,
            final List<String> developmentSet, final float minRuleProbability) {

        // Store CC model, training corpus, and dev-set (they'll be consistent throughout all training cycles)
        this.ccModel = ccModel;
        this.minRuleProbability = minRuleProbability;
        this.ccModel = ccModel;
        this.trainingCorpus = trainingCorpus;

        // Un-binarize the dev-set
        this.developmentSet = new ArrayList<String>();
        for (final String binarizedTree : developmentSet) {
            this.developmentSet.add(BinaryTree.read(binarizedTree, String.class).unfactor(GrammarFormatType.Berkeley)
                    .toString());
        }
    }

    /**
     * Evaluates speed and accuracy with the baseline (fully-split) grammar
     * 
     * @param grammar
     * @param lexicon
     * @param cycle
     */
    @Override
    public void initMergeCycle(final Grammar grammar, final Lexicon lexicon, final int cycle) {

        this.splitGrammar = grammar;
        this.splitLexicon = lexicon;
        final String[] split = GlobalConfigProperties.singleton()
                .getProperty(PROPERTY_BEAM_WIDTHS, DEFAULT_BEAM_WIDTHS).split(",");
        this.beamWidth = Integer.parseInt(split[cycle - 1]);

        // Convert the grammar to BUBS sparse-matrix format and train a Boundary POS FOM
        BaseLogger.singleton().info("Constrained parsing the training-set and training a prioritization model");
        final LeftCscSparseMatrixGrammar sparseMatrixGrammar = convertGrammarToSparseMatrix(splitGrammar, splitLexicon);
        final BoundaryPosModel posFom = trainPosFom(sparseMatrixGrammar);

        // Record accuracy with the full split grammar (so we can later compare with MergeCandidates)
        BaseLogger.singleton().info("Parsing the dev-set with the fully-split grammar");
        final float[] parseResult = parseDevSet(sparseMatrixGrammar, posFom, beamWidth);
        splitF1 = parseResult[0];
        splitSpeed = parseResult[1];
        BaseLogger.singleton().info(String.format("F1 = %.3f  Speed = %.3f", splitF1 * 100, splitSpeed));
    }

    @Override
    public void initMergeCandidates(final List<MergeCandidate> mergeCandidates,
            final double[][] substateConditionalProbabilities, final float minimumRuleProbability) {

        final int totalMergeCandidates = mergeCandidates.size();

        // Assign estimated speed rankings to be the same as estimated accuracy rankings (already populated by log
        // likelihood)
        for (final MergeCandidate mc : mergeCandidates) {
            mc.setEstimatedSpeedRanking(mc.estimatedAccuracyRanking());
        }

        for (final MergeCandidate mergeCandidate : mergeCandidates) {
            // Skip inference for a portion of the merge candidates by estimated likelihood-loss. Return infinite merge
            // cost for those with the greatest likelihood loss and negative-infinite cost for those with the least.
            if (PARSE_FRACTION < 1) {
                final int toParse = Math.round(totalMergeCandidates * PARSE_FRACTION);

                if (mergeCandidate.estimatedAccuracyRanking() < totalMergeCandidates / 2 - toParse / 2) {
                    mergeCandidate.estimatedAccuracyDelta = Float.NEGATIVE_INFINITY;
                    mergeCandidate.estimatedInferenceSpeedDelta = Float.NEGATIVE_INFINITY;
                    continue;

                } else if (mergeCandidate.estimatedAccuracyRanking() > totalMergeCandidates / 2 + toParse / 2) {
                    mergeCandidate.estimatedAccuracyDelta = Float.POSITIVE_INFINITY;
                    mergeCandidate.estimatedInferenceSpeedDelta = Float.POSITIVE_INFINITY;
                    continue;
                }
            }

            final String sState = NUMBERER.symbol(mergeCandidate.state);

            // Merge the candidate substates
            final Grammar mergedGrammar = splitGrammar.merge(mergeCandidate, substateConditionalProbabilities);
            final Lexicon mergedLexicon = splitLexicon.merge(mergeCandidate);

            // Convert the grammar to BUBS sparse-matrix format and train a Boundary POS FOM
            final long t0 = System.currentTimeMillis();
            final LeftCscSparseMatrixGrammar sparseMatrixGrammar = convertGrammarToSparseMatrix(mergedGrammar,
                    mergedLexicon);
            final BoundaryPosModel posFom = trainPosFom(sparseMatrixGrammar);

            final long t1 = System.currentTimeMillis();

            // Parse the development set using the complete-closure model and lexical FOM
            final float[] parseResult = parseDevSet(sparseMatrixGrammar, posFom, beamWidth);
            final long t2 = System.currentTimeMillis();

            mergeCandidate.estimatedAccuracyDelta = splitF1 - parseResult[0];
            mergeCandidate.estimatedInferenceSpeedDelta = splitSpeed - parseResult[1];

            BaseLogger
                    .singleton()
                    .info(String
                            .format("Testing merge of %s_%d and %s_%d : Training time: %d ms  F1 = %.3f (%.3f)  Speed = %.3f (%.3f)  Parse time: %d ms",
                                    sState, mergeCandidate.substate1, sState, mergeCandidate.substate2, t1 - t0,
                                    parseResult[0] * 100, mergeCandidate.estimatedAccuracyDelta * -100, parseResult[1],
                                    -mergeCandidate.estimatedInferenceSpeedDelta, t2 - t1));
        }

        // Sort and assign ordinal rankings by F1 and inference speed
        Collections.sort(mergeCandidates, MergeCandidate.estimatedAccuracyComparator());
        for (int i = 0; i < mergeCandidates.size(); i++) {
            mergeCandidates.get(i).setEstimatedAccuracyRanking(i);
        }

        Collections.sort(mergeCandidates, MergeCandidate.estimatedSpeedComparator());
        for (int i = 0; i < mergeCandidates.size(); i++) {
            mergeCandidates.get(i).setEstimatedSpeedRanking(i);
        }
    }

    /**
     * Parses the development set with the specified grammar and FOM. Returns the accuracy (F1) of the resulting parses.
     * Uses the specified <code>beamWidth</code> for all cells spanning >= 2 words. For lexical cells, uses
     * <code>beamWidth</code> x 3 (since the FOM does not prioritize entries in lexical cells), and allocates
     * <code>beamWidth</code> entries in lexical cells for unary productions.
     * 
     * @param sparseMatrixGrammar
     * @param posFom
     * @param beamWidth
     * @return Accuracy (F1) and speed (w/s)
     */
    private float[] parseDevSet(final LeftCscSparseMatrixGrammar sparseMatrixGrammar, final BoundaryPosModel posFom,
            final int cycle) {

        // Initialize the parser
        final ParserDriver opts = new ParserDriver();
        opts.researchParserType = ResearchParserType.CartesianProductHashMl;
        opts.cellSelectorModel = ccModel;
        opts.fomModel = posFom;

        // Set beam-width configuration properties
        GlobalConfigProperties.singleton().setProperty(Parser.PROPERTY_MAX_BEAM_WIDTH, Integer.toString(beamWidth));
        GlobalConfigProperties.singleton().setProperty(Parser.PROPERTY_LEXICAL_ROW_BEAM_WIDTH,
                Integer.toString(beamWidth * 3));
        GlobalConfigProperties.singleton()
                .setProperty(Parser.PROPERTY_LEXICAL_ROW_UNARIES, Integer.toString(beamWidth));

        // Parse the dev-set
        final CartesianProductHashSpmlParser parser = new CartesianProductHashSpmlParser(opts, sparseMatrixGrammar);
        final long t0 = System.currentTimeMillis();
        int words = 0;
        final BracketEvaluator evaluator = new BracketEvaluator();
        for (final String inputTree : developmentSet) {
            parser.parseSentence(inputTree).evaluate(evaluator);
            final NaryTree<String> naryTree = NaryTree.read(inputTree, String.class);
            words += naryTree.leaves();
        }
        final long t1 = System.currentTimeMillis();

        return new float[] { (float) evaluator.accumulatedResult().f1(), words * 1000f / (t1 - t0) };
    }

    /**
     * Converts a {@link Grammar} and {@link Lexicon} to BUBS sparse-matrix format, specifically
     * {@link LeftCscSparseMatrixGrammar}. Prunes rules below the minimum rule probability threshold specified when the
     * {@link DiscriminativeMergeObjectiveFunction} was initialized with
     * {@link #init(CompleteClosureModel, List, List, float)}.
     * 
     * @param grammar
     * @param lexicon
     * @return {@link LeftCscSparseMatrixGrammar}
     */
    private LeftCscSparseMatrixGrammar convertGrammarToSparseMatrix(final Grammar grammar, final Lexicon lexicon) {
        try {
            final Writer w = new StringWriter(150 * 1024 * 1024);
            // TODO We could use a PipedOutputStream / PipedInputStream combination (with 2 threads) to write and read
            // at the same time, and avoid using enough memory to serialize the entire grammar. But memory isn't a huge
            // constraint during training, so this optimization can wait.
            w.write(grammar.toString(lexicon.totalRules(minRuleProbability), minRuleProbability, 0, 0));
            w.write("===== LEXICON =====\n");
            w.write(lexicon.toString(minRuleProbability));

            return new LeftCscSparseMatrixGrammar(new StringReader(w.toString()), new DecisionTreeTokenClassifier());
        } catch (final IOException e) {
            // StringWriter should never IOException
            throw new RuntimeException(e);
        }
    }

    /**
     * Trains a boundary POS prioritization model (AKA a figure-of-merit, or FOM). Parses the training corpus,
     * constrained by the gold trees, and learns prioritization probabilities from the resulting parses.
     * 
     * @param sparseMatrixGrammar
     * @return a boundary POS figure of merit model.
     */
    private BoundaryPosModel trainPosFom(final LeftCscSparseMatrixGrammar sparseMatrixGrammar) {

        try {
            // Constrained parse the training corpus
            final ParserDriver opts = new ParserDriver();
            opts.cellSelectorModel = ConstrainedCellSelector.MODEL;
            opts.researchParserType = ResearchParserType.ConstrainedCartesianProductHashMl;
            final ConstrainedCphSpmlParser constrainedParser = new ConstrainedCphSpmlParser(opts, sparseMatrixGrammar);

            final StringWriter binaryConstrainedParses = new StringWriter(30 * 1024 * 1024);
            for (final String inputTree : trainingCorpus) {
                final ParseTask parseTask = constrainedParser.parseSentence(inputTree);
                binaryConstrainedParses.write(parseTask.binaryParse.toString());
                binaryConstrainedParses.write('\n');
            }

            final StringWriter serializedFomModel = new StringWriter(30 * 1024 * 1024);
            BoundaryPosModel.train(sparseMatrixGrammar,
                    new BufferedReader(new StringReader(binaryConstrainedParses.toString())), new BufferedWriter(
                            serializedFomModel), .5f, false, 2);

            final BufferedReader fomModelReader = new BufferedReader(new StringReader(serializedFomModel.toString()));
            return new BoundaryPosModel(FOMType.BoundaryPOS, sparseMatrixGrammar, fomModelReader);
        } catch (final IOException e) {
            // StringWriter and StringReader should never IOException
            throw new RuntimeException(e);
        }
    }
}
