package edu.ohsu.cslu.dep;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.util.LinkedList;
import java.util.logging.Level;

import cltool4j.BaseLogger;
import cltool4j.args4j.Option;
import edu.ohsu.cslu.datastructs.vectors.BitVector;
import edu.ohsu.cslu.dep.DependencyGraph.Arc;
import edu.ohsu.cslu.grammar.SymbolSet;
import edu.ohsu.cslu.grammar.Tokenizer;
import edu.ohsu.cslu.perceptron.AveragedPerceptron;
import edu.ohsu.cslu.perceptron.AveragedPerceptron.ScoredClassification;
import edu.ohsu.cslu.perceptron.Perceptron;

/**
 * Trains a perceptron classifier for greedy dependency parsing.
 * 
 * Input: Dependency treebank in CoNLL 2007 format
 * 
 * Output: Training status and training/dev-set accuracies
 * 
 * The model (3 averaged perceptrons, vocabulary and lexicon) will be serialized to the specified model file.
 */
public class EvalDepClassifiers extends BaseDepParser {

    @Option(name = "-i", metaVar = "count", usage = "Training iterations")
    private int trainingIterations = 10;

    @Option(name = "-d", required = true, metaVar = "file", usage = "Development set in CoNLL 2007 format")
    private File devSet;

    @Option(name = "-f", metaVar = "features", usage = "Feature templates")
    private String featureTemplates = "st1,st2,st3,it1,it2,it3,sw1,sw2,iw1";

    @Option(name = "-l", usage = "Label arcs (if false, no arc labels will be assigned)")
    private boolean classifyLabels = false;

    @Option(name = "-msl", metaVar = "loss", usage = "Missed shift loss (vs. 1 for missed-reduce)")
    private float missedShiftLoss = 1f;

    @Option(name = "-as", metaVar = "file", usage = "Output arc scores to file")
    private File arcScores;

    @Override
    protected void run() throws Exception {

        //
        // Read each training instance
        //
        final LinkedList<DependencyGraph> trainingExamples = new LinkedList<DependencyGraph>();
        for (final BufferedReader br = inputAsBufferedReader(); br.ready();) {
            try {
                final DependencyGraph g = DependencyGraph.readConll(br);
                // If we can't produce a derivation, skip this example
                g.derivation();
                trainingExamples.add(g);
            } catch (final IllegalArgumentException ignore) {
            }
        }

        LinkedList<DependencyGraph> devExamples = null;
        if (devSet != null) {
            devExamples = new LinkedList<DependencyGraph>();
            for (final BufferedReader br = fileAsBufferedReader(devSet); br.ready();) {
                devExamples.add(DependencyGraph.readConll(br));
            }
        }

        final SymbolSet<String> tokens = new SymbolSet<String>();
        tokens.addSymbol(DependencyGraph.NULL);
        tokens.addSymbol(DependencyGraph.ROOT.token);
        final SymbolSet<String> pos = new SymbolSet<String>();
        pos.addSymbol(DependencyGraph.NULL);
        pos.addSymbol(DependencyGraph.ROOT.pos);
        final SymbolSet<String> labels = new SymbolSet<String>();
        labels.addSymbol(DependencyGraph.NULL);
        labels.addSymbol(DependencyGraph.ROOT.label);

        for (final DependencyGraph example : trainingExamples) {
            for (int i = 0; i < example.arcs.length; i++) {
                final Arc arc = example.arcs[i];
                tokens.addSymbol(arc.token);

                // Add an entry for the UNK label as well
                tokens.addSymbol(Tokenizer.berkeleyGetSignature(arc.token, i == 0, tokens));
                pos.addSymbol(arc.pos);
                labels.addSymbol(arc.label);
            }
        }

        final NivreParserFeatureExtractor fe = new NivreParserFeatureExtractor(featureTemplates, tokens, pos, labels);

        // At each step, we have 3 possible actions (shift, reduce-left, reduce-right), but we divide them into 2
        // classifiers - one to decide between shift and reduce, and one to select reduce direction. For the moment, we
        // use the same feature-set for both.
        final AveragedPerceptron shiftReduceClassifier = new AveragedPerceptron(new Perceptron.BiasedLoss(new float[] {
                missedShiftLoss, 1f }), 2, fe.featureCount());
        final AveragedPerceptron reduceDirectionClassifier = new AveragedPerceptron(2, fe.featureCount());
        // Label arcs, with a third classifier
        final AveragedPerceptron labelClassifier = classifyLabels ? new AveragedPerceptron(labels.size(),
                fe.featureCount()) : null;
        //
        // Iterate through the training instances
        //
        for (int iteration = 0, examples = 0; iteration < trainingIterations; iteration++, examples = 0) {
            for (final DependencyGraph example : trainingExamples) {
                example.clear();
                try {
                    final DependencyGraph.DerivationAction[] derivation = example.derivation();

                    final Arc[] arcs = example.arcs;
                    final LinkedList<Arc> stack = new LinkedList<Arc>();
                    final NivreParserContext context = new NivreParserContext(stack, arcs);

                    for (int step = 0, i = 0; step < derivation.length; step++) {
                        final BitVector featureVector = fe.forwardFeatureVector(context, i);

                        switch (derivation[step]) {

                        case SHIFT:
                            if (stack.size() >= 2) {
                                shiftReduceClassifier.train(ParserAction.SHIFT.ordinal(), featureVector);
                            }
                            stack.addFirst(arcs[i++]);
                            break;

                        case REDUCE_LEFT: {
                            shiftReduceClassifier.train(ParserAction.REDUCE.ordinal(), featureVector);
                            reduceDirectionClassifier.train(ReduceDirection.LEFT.ordinal(), featureVector);
                            final Arc top = stack.removeFirst();
                            top.predictedHead = stack.peek().index;

                            if (labelClassifier != null) {
                                labelClassifier.train(labels.getIndex(top.label), featureVector);
                            }
                            break;
                        }
                        case REDUCE_RIGHT: {
                            shiftReduceClassifier.train(ParserAction.REDUCE.ordinal(), featureVector);
                            reduceDirectionClassifier.train(ReduceDirection.RIGHT.ordinal(), featureVector);

                            final Arc top = stack.removeFirst();
                            final Arc second = stack.removeFirst();
                            second.predictedHead = top.index;
                            stack.addFirst(top);

                            if (labelClassifier != null) {
                                labelClassifier.train(labels.getIndex(second.label), featureVector);
                            }
                            break;
                        }
                        }
                    }
                } catch (final IllegalArgumentException ignore) {
                    // Ignore non-projective dependencies
                }
                if (examples++ % 1000 == 0) {
                    System.out.print('.');
                }
            }

            FileWriter arcScoreWriter;
            if (arcScores != null && iteration == trainingIterations - 1) {
                arcScoreWriter = new FileWriter(arcScores);
                arcScoreWriter
                        .write("sr_score,sr_margin,sr_correct,lr_score,lr_margin,lr_correct,l_index,span,buf,sent_len,label_score,label_margin,label_correct\n");
            } else {
                arcScoreWriter = null;
            }

            int shiftReduceClassifications = 0, correctShiftReduceClassifications = 0;
            int missedShifts = 0, missedReduces = 0;
            int reduceDirectionClassifications = 0, correctReduceDirectionClassifications = 0;

            for (final DependencyGraph example : devExamples) {
                example.clear();
                try {
                    final DependencyGraph.DerivationAction[] derivation = example.derivation();

                    final Arc[] arcs = example.arcs;
                    final LinkedList<Arc> stack = new LinkedList<Arc>();
                    final NivreParserContext context = new NivreParserContext(stack, arcs);

                    for (int step = 0, i = 0; step < derivation.length; step++) {
                        final BitVector featureVector = fe.forwardFeatureVector(context, i);
                        final ScoredClassification srClassification = shiftReduceClassifier
                                .scoredClassify(featureVector);

                        switch (derivation[step]) {

                        case SHIFT:
                            if (stack.size() >= 2) {
                                shiftReduceClassifications++;
                                if (srClassification.classification == ParserAction.SHIFT.ordinal()) {
                                    correctShiftReduceClassifications++;
                                } else {
                                    missedShifts++;
                                    if (BaseLogger.singleton().isLoggable(Level.FINER)) {
                                        outputParserState("DEBUG: Shift", context, i);
                                    }
                                }
                            }
                            stack.addFirst(arcs[i++]);
                            break;

                        case REDUCE_LEFT: {
                            shiftReduceClassifications++;
                            int srCorrect = 0;
                            if (srClassification.classification == ParserAction.REDUCE.ordinal()) {
                                correctShiftReduceClassifications++;
                                srCorrect = 1;
                            } else {
                                missedReduces++;
                            }

                            reduceDirectionClassifications++;
                            final ScoredClassification lrClassification = reduceDirectionClassifier
                                    .scoredClassify(featureVector);
                            int lrCorrect = 0;
                            if (lrClassification.classification == ReduceDirection.LEFT.ordinal()) {
                                correctReduceDirectionClassifications++;
                                lrCorrect = 1;
                            }

                            final Arc top = stack.removeFirst();
                            top.predictedHead = stack.peek().index;

                            final ScoredClassification labelClassification = labelClassifier != null ? labelClassifier
                                    .scoredClassify(featureVector) : null;
                            int labelCorrect = 0;
                            if (labelClassification != null) {
                                top.predictedLabel = labels.getSymbol(labelClassification.classification);
                                if (top.predictedLabel.equals(top.label)) {
                                    labelCorrect = 1;
                                }
                            }

                            if (arcScoreWriter != null) {
                                arcScoreWriter.write(String.format(
                                        "%.3f,%.3f,%d,%.3f,%.3f,%d,%d,%d,%d,%d,%.3f,%.3f,%d\n", srClassification.score,
                                        srClassification.margin, srCorrect, lrClassification.score,
                                        lrClassification.margin, lrCorrect, top.predictedHead, top.index
                                                - top.predictedHead, context.arcs.length - i, context.arcs.length - 1,
                                        labelClassification != null ? labelClassification.score : 0,
                                        labelClassification != null ? labelClassification.margin : 0, labelCorrect));
                            }

                            break;
                        }
                        case REDUCE_RIGHT: {
                            shiftReduceClassifications++;
                            int srCorrect = 0;
                            if (shiftReduceClassifier.classify(featureVector) == ParserAction.REDUCE.ordinal()) {
                                correctShiftReduceClassifications++;
                                srCorrect = 1;
                            } else {
                                missedReduces++;
                            }

                            reduceDirectionClassifications++;
                            final ScoredClassification lrClassification = reduceDirectionClassifier
                                    .scoredClassify(featureVector);
                            int lrCorrect = 0;
                            if (reduceDirectionClassifier.classify(featureVector) == ReduceDirection.RIGHT.ordinal()) {
                                correctReduceDirectionClassifications++;
                                lrCorrect = 1;
                            }

                            final Arc top = stack.removeFirst();
                            final Arc second = stack.removeFirst();
                            second.predictedHead = top.index;
                            stack.addFirst(top);

                            final ScoredClassification labelClassification = labelClassifier != null ? labelClassifier
                                    .scoredClassify(featureVector) : null;
                            int labelCorrect = 0;
                            if (labelClassifier != null) {
                                top.predictedLabel = labels.getSymbol(labelClassifier.classify(featureVector));
                                if (top.predictedLabel.equals(top.label)) {
                                    labelCorrect = 1;
                                }
                            }

                            if (arcScoreWriter != null) {
                                arcScoreWriter.write(String.format(
                                        "%.3f,%.3f,%d,%.3f,%.3f,%d,%d,%d,%d,%d,%.3f,%.3f,%d\n", srClassification.score,
                                        srClassification.margin, srCorrect, lrClassification.score,
                                        lrClassification.margin, lrCorrect, second.index, top.index - second.index,
                                        context.arcs.length - i, context.arcs.length - 1,
                                        labelClassification != null ? labelClassification.score : 0,
                                        labelClassification != null ? labelClassification.margin : 0, labelCorrect));
                            }
                            break;
                        }
                        }
                    }
                } catch (final IllegalArgumentException ignore) {
                    // Ignore non-projective dependencies
                }
            }

            if (arcScoreWriter != null) {
                arcScoreWriter.close();
            }

            System.out.println(iteration + 1);

            if (BaseLogger.singleton().isLoggable(Level.FINE)) {
                test(trainingExamples, "Training-set", fe, shiftReduceClassifier, reduceDirectionClassifier,
                        labelClassifier, tokens, pos, labels);
            }
            test(devExamples, "Dev-set", fe, shiftReduceClassifier, reduceDirectionClassifier, labelClassifier, tokens,
                    pos, labels);
            BaseLogger.singleton().info(
                    String.format("Shift/Reduce: %d/%d (%.2f%%)", correctShiftReduceClassifications,
                            shiftReduceClassifications, 1.0 * correctShiftReduceClassifications
                                    / shiftReduceClassifications));
            BaseLogger.singleton()
                    .info(String.format("Missed Shifts: %d/%d", missedShifts, shiftReduceClassifications));
            BaseLogger.singleton().info(
                    String.format("Missed Reduces: %d/%d", missedReduces, shiftReduceClassifications));
            BaseLogger.singleton().info(
                    String.format("Reduce Direction: %d/%d (%.2f%%)", correctReduceDirectionClassifications,
                            reduceDirectionClassifications, 1.0 * correctReduceDirectionClassifications
                                    / reduceDirectionClassifications));
        }
    }

    private void outputParserState(final String prefix, final NivreParserContext state, final int i) {
        final StringBuilder sb = new StringBuilder();

        sb.append(String.format("%10s | ", prefix));

        Arc a = state.stack.get(1);
        sb.append(a.pos);
        sb.append('/');
        sb.append(a.predictedLabel != null ? a.predictedLabel : "");

        sb.append("   ");
        a = state.stack.get(0);
        sb.append(a.pos);
        sb.append('/');
        sb.append(a.predictedLabel != null ? a.predictedLabel : "");

        sb.append("   ");
        a = state.arcs[i];
        sb.append(a.pos);
        sb.append('/');
        sb.append(a.predictedLabel != null ? a.predictedLabel : "");

        BaseLogger.singleton().finer(sb.toString());
    }

    private void test(final LinkedList<DependencyGraph> examples, final String label,
            final NivreParserFeatureExtractor featureExtractor, final AveragedPerceptron shiftReduceClassifier,
            final AveragedPerceptron reduceDirectionClassifier, final AveragedPerceptron labelClassifier,
            final SymbolSet<String> tokens, final SymbolSet<String> pos, final SymbolSet<String> labels) {

        final long startTime = System.currentTimeMillis();

        int correctArcs = 0, correctLabels = 0, total = 0;

        for (final DependencyGraph example : examples) {
            total += example.size() - 1;
            final int sentenceCorrect = 0;
            // float sentenceScore = 0f;
            final DependencyGraph parse = parse(example, featureExtractor, shiftReduceClassifier,
                    reduceDirectionClassifier, labelClassifier, tokens, pos, labels);
            correctArcs += parse.correctArcs();
            correctLabels += parse.correctLabels();

            // for (int i = 0; i < example.size() - 1; i++) {
            // if (parse.arcs[i].head == example.arcs[i].head) {
            // correctArcs++;
            // sentenceCorrect++;
            // sentenceScore += parse.arcs[i].score;
            //
            // if (example.arcs[i].label.equals(parse.arcs[i].label)) {
            // correctLabels++;
            // }
            // } else {
            // sentenceScore -= parse.arcs[i].score;
            // }
            // }

            // BaseLogger.singleton().finer(
            // String.format("%.3f %.3f", sentenceCorrect * 1.0 / (example.size() - 1), sentenceScore));
        }
        final long time = System.currentTimeMillis() - startTime;
        System.out.format("%s accuracy - unlabeled: %.3f  labeled %.3f  (%d ms, %.2f words/sec)\n", label, correctArcs
                * 1.0 / total, correctLabels * 1.0 / total, time, total * 1000.0 / time);
    }

    public static void main(final String[] args) {
        run(args);
    }
}
