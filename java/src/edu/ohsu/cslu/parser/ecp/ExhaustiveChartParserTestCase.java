/*
 * Copyright 2010, 2011 Aaron Dunlop and Nathan Bodenstab
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
package edu.ohsu.cslu.parser.ecp;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;

import org.cjunit.DetailedTest;
import org.cjunit.FilteredRunner;
import org.cjunit.PerformanceTest;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import cltool4j.ConfigProperties;
import cltool4j.GlobalConfigProperties;
import edu.ohsu.cslu.grammar.Grammar;
import edu.ohsu.cslu.grammar.GrammarTestCase;
import edu.ohsu.cslu.parser.ChartParser;
import edu.ohsu.cslu.parser.Parser;
import edu.ohsu.cslu.parser.ParserDriver;
import edu.ohsu.cslu.parser.cellselector.CellSelector;
import edu.ohsu.cslu.parser.cellselector.CellSelectorModel;
import edu.ohsu.cslu.parser.cellselector.LeftRightBottomTopTraversal;
import edu.ohsu.cslu.parser.chart.Chart;
import edu.ohsu.cslu.parser.ml.SparseMatrixLoopParser;
import edu.ohsu.cslu.parser.spmv.SparseMatrixVectorParser;
import edu.ohsu.cslu.tests.JUnit;

/**
 * Base test case for all exhaustive parsers (or agenda-based parsers run to exhaustion). Tests a couple trivial
 * sentences using very simple grammars and the first 10 sentences of WSJ section 24 using a slightly more reasonable
 * PCFG. Profiles sentences 11-20 to aid in performance tuning and prevent performance regressions.
 * 
 * @author Aaron Dunlop
 * @since Dec 23, 2009
 */
@RunWith(FilteredRunner.class)
public abstract class ExhaustiveChartParserTestCase<P extends ChartParser<? extends Grammar, ? extends Chart>> {

    // Grammar file paths, relative to unit test data root directory
    protected final static String PCFG_FILE = "grammars/eng.R2.gr.gz";

    /** Very simple grammar for parsing 'systems analyst arbitration chef' */
    protected static Grammar simpleGrammar1;

    /** Slightly larger grammar for parsing 'The fish market stands last' */
    protected static Grammar simpleGrammar2;

    /** Grammar induced from WSJ sections 2-21 */
    protected static Grammar f2_21_grammar;

    /** WSJ section 24 sentences 1-20 */
    protected static ArrayList<String[]> sentences = new ArrayList<String[]>();

    /** The parser under test */
    protected ChartParser<?, ?> parser;

    /**
     * Creates the appropriate parser options for each test class.
     * 
     * @return options
     * @throws Exception if something breaks while constructing the options instance (e.g. failing to find a model
     *             file).
     */
    protected ParserDriver parserOptions() throws Exception {
        final ParserDriver options = new ParserDriver();
        options.binaryTreeOutput = true;
        return options;
    }

    @AfterClass
    public static void suiteTearDown() throws Exception {
        GlobalConfigProperties.singleton().clear();
    }

    /**
     * Returns parser configuration options.
     * 
     * @return options
     * @throws Exception if something breaks while constructing the options instance (e.g. failing to find a model
     *             file).
     */
    protected ConfigProperties configProperties() throws Exception {
        return new ConfigProperties();
    }

    /**
     * Creates the appropriate parser for each test class. Ugly reflection code, but at least it's all localized here.
     * 
     * @param grammar The grammar to use when parsing
     * @param cellSelectorModel Model to produce {@link CellSelector} controlling chart traversal
     * @return Parser instance
     */
    @SuppressWarnings("unchecked")
    protected final P createParser(final Grammar grammar, final CellSelectorModel cellSelectorModel,
            final ParserDriver options, final ConfigProperties configProperties) {
        if (options != null) {
            options.cellSelectorModel = cellSelectorModel;
        }
        try {
            final Class<P> parserClass = ((Class<P>) ((ParameterizedType) getClass().getGenericSuperclass())
                    .getActualTypeArguments()[0]);
            try {
                // First, try for a constructor that takes both ParserDriver (options) and ConfigProperties
                return parserClass.getConstructor(
                        new Class[] { ParserDriver.class, ConfigProperties.class, grammarClass() }).newInstance(
                        new Object[] { options, configProperties, grammar });

            } catch (final NoSuchMethodException e) {
                // If not found, use a constructor that takes only a ParserDriver instance.
                return parserClass.getConstructor(new Class[] { ParserDriver.class, grammarClass() }).newInstance(
                        new Object[] { options, grammar });
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Find the appropriate grammar class for the parser under test. Even more ugly reflection code. Dragons be here...
     * 
     * @return the grammar class appropriate for the parser under test
     */
    @SuppressWarnings("unchecked")
    protected final Class<? extends Grammar> grammarClass() {
        Class<P> parserClass = ((Class<P>) ((ParameterizedType) getClass().getGenericSuperclass())
                .getActualTypeArguments()[0]);
        try {
            final Class<? extends Grammar> grammarClass = ((Class<? extends Grammar>) ((ParameterizedType) parserClass
                    .getGenericSuperclass()).getActualTypeArguments()[0]);

            // If the grammar class is not annotated on this parser class, look up one level
            if (!Grammar.class.isAssignableFrom(grammarClass)) {
                throw new ClassCastException();
            }

            return grammarClass;

        } catch (final ClassCastException e) {

            // Look up one level in the parser hierarchy
            parserClass = (Class<P>) parserClass.getSuperclass();
            final Class<? extends Grammar> grammarClass = ((Class<? extends Grammar>) ((ParameterizedType) parserClass
                    .getGenericSuperclass()).getActualTypeArguments()[0]);
            return grammarClass;
        }
    }

    /**
     * @return the parser class under test
     */
    @SuppressWarnings("unchecked")
    protected final Class<P> parserClass() {
        return ((Class<P>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0]);
    }

    public Grammar createGrammar(final Reader grammarReader) throws Exception {
        return grammarClass().getConstructor(new Class[] { Reader.class }).newInstance(new Object[] { grammarReader });
    }

    /**
     * Reads in the first 20 sentences of WSJ section 24. Run once for the class, prior to execution of the first test
     * method.
     * 
     * @throws Exception if unable to read
     */
    @BeforeClass
    public static void suiteSetUp() throws Exception {
        // Read test sentences
        // TODO Parameterize test sentences (this will require a custom Runner implementation)
        final BufferedReader tokenizedReader = new BufferedReader(new InputStreamReader(
                JUnit.unitTestDataAsStream("parsing/wsj.24.tokens.1-20")));

        // We have two major hierarchies of chart structures, one which stores constituents in a sorted array and one
        // which uses a HashSet. Parsers which use different chart implementations iterate over child constituents in
        // differing orders, which produces variances in tie-breaking behavior. For testing, we store two versions of
        // the 'correct' parse.
        final BufferedReader hscParsedReader = new BufferedReader(new InputStreamReader(
                JUnit.unitTestDataAsStream("parsing/wsj.24.parsed.R2.hsccyk.1-20")));

        // TODO Reconcile differing parse output between sparse-matrix-vector and sparse-matrix-loop parser hierarchies
        final BufferedReader spmvParsedReader = new BufferedReader(new InputStreamReader(
                JUnit.unitTestDataAsStream("parsing/wsj.24.parsed.R2.spmvcyk.1-20")));

        final BufferedReader spmlParsedReader = new BufferedReader(new InputStreamReader(
                JUnit.unitTestDataAsStream("parsing/wsj.24.parsed.R2.spmlcyk.1-20")));

        for (String sentence = tokenizedReader.readLine(); sentence != null; sentence = tokenizedReader.readLine()) {
            sentences.add(new String[] { sentence, hscParsedReader.readLine(), spmvParsedReader.readLine(),
                    spmlParsedReader.readLine() });
        }
    }

    /**
     * Constructs the grammar (if necessary) and a new parser instance. Run prior to each test method.
     * 
     * @throws Exception if unable to construct grammar or parser.
     */
    @Before
    public void setUp() throws Exception {
        if (f2_21_grammar == null || f2_21_grammar.getClass() != grammarClass()) {
            f2_21_grammar = createGrammar(JUnit.unitTestDataAsReader(PCFG_FILE));
        }

        if (simpleGrammar1 == null || simpleGrammar1.getClass() != grammarClass()) {
            simpleGrammar1 = createGrammar(GrammarTestCase.simpleGrammar());
        }

        if (simpleGrammar2 == null || simpleGrammar2.getClass() != grammarClass()) {
            simpleGrammar2 = createGrammar(simpleGrammar2());
        }

        GlobalConfigProperties.singleton().setProperty(Parser.PROPERTY_MAX_BEAM_WIDTH, "0");
        parser = createParser(f2_21_grammar, LeftRightBottomTopTraversal.MODEL, parserOptions(), configProperties());
    }

    public static Reader simpleGrammar2() throws Exception {
        final StringBuilder sb = new StringBuilder(256);
        sb.append("format=CSLU start=ROOT\n");
        sb.append("S => NP VP 0\n");
        sb.append("ROOT => S 0\n");
        sb.append("NP => DT NP -1.386294361\n");
        sb.append("NP => DT NN -1.386294361\n");
        sb.append("NP => NN NN -1.791759469\n");
        sb.append("NP => NN NP|NN -1.791759469\n");
        sb.append("NP => NN RB -1.791759469\n");
        sb.append("NP|NN => NN NN 0\n");
        sb.append("VP => VB RB -0.693147181\n");
        sb.append("VP => VB VP|VB -1.386294361\n");
        sb.append("VP => VB -1.386294361\n");
        sb.append("VP|VB => NP 0\n");

        sb.append(Grammar.LEXICON_DELIMITER);
        sb.append('\n');

        sb.append("DT => The 0\n");
        sb.append("NN => fish 0\n");
        sb.append("NN => market -0.405465108\n");
        sb.append("VB => market -1.098612289\n");
        sb.append("NN => stands -0.693147181\n");
        sb.append("VB => stands -0.693147181\n");
        sb.append("RB => last -0.405465108\n");
        sb.append("VB => last -1.098612289\n");
        sb.append("NN => UNK 0\n");

        return new StringReader(sb.toString());
    }

    /**
     * Tests parsing with a _very_ simple grammar.
     * 
     * @throws Exception if something bad happens
     */
    @Test
    public void testSimpleGrammar1() throws Exception {
        parser = createParser(simpleGrammar1, LeftRightBottomTopTraversal.MODEL, parserOptions(), configProperties());

        String parse = parser.parseSentence("systems analyst arbitration chef").parseBracketString(true, false);
        assertEquals("(ROOT (NP (NP (NP (NN systems) (NN analyst)) (NN arbitration)) (NN chef)))", parse);

        // Tests with an unknown word.
        parse = parser.parseSentence("systems XXX arbitration chef").parseBracketString(true, false);
        assertEquals("(ROOT (NP (NP (NP (NN systems) (NN XXX)) (NN arbitration)) (NN chef)))", parse);

        parse = parser.parseSentence("systems analyst arbitration XXX").parseBracketString(true, false);
        assertEquals("(ROOT (NP (NP (NP (NN systems) (NN analyst)) (NN arbitration)) (NN XXX)))", parse);
    }

    /**
     * Tests parsing with a slightly larger grammar.
     * 
     * @throws Exception if something bad happens
     */
    @Test
    public void testSimpleGrammar2() throws Exception {
        parser = createParser(simpleGrammar2, LeftRightBottomTopTraversal.MODEL, parserOptions(), configProperties());

        String parse = parser.parseSentence("The fish market stands last").parseBracketString(true, false);
        assertEquals("(ROOT (S (NP (DT The) (NP (NN fish) (NN market))) (VP (VB stands) (RB last))))", parse);

        // Tests with an unknown word
        parse = parser.parseSentence("The XXX market stands last").parseBracketString(true, false);
        assertEquals("(ROOT (S (NP (DT The) (NP (NN XXX) (NN market))) (VP (VB stands) (RB last))))", parse);

        parse = parser.parseSentence("The fish market stands XXX").parseBracketString(true, false);
        assertEquals("(ROOT (S (NP (DT The) (NN fish)) (VP (VB market) (VP|VB (NP (NN stands) (NN XXX))))))", parse);

        parse = parser.parseSentence("The fish market stands last XXX").parseBracketString(true, false);
        assertEquals("()", parse);
    }

    @Test
    @DetailedTest
    public void testSentence1() throws Exception {
        parseTreebankSentence(0);
    }

    @Test
    public void testSentence2() throws Exception {
        parseTreebankSentence(1);
    }

    @Test
    @DetailedTest
    public void testSentence3() throws Exception {
        parseTreebankSentence(2);
    }

    @Test
    @DetailedTest
    public void testSentence4() throws Exception {
        parseTreebankSentence(3);
    }

    @Test
    @DetailedTest
    public void testSentence5() throws Exception {
        parseTreebankSentence(4);
    }

    @Test
    @DetailedTest
    public void testSentence6() throws Exception {
        parseTreebankSentence(5);
    }

    @Test
    public void testSentence7() throws Exception {
        parseTreebankSentence(6);
    }

    @Test
    @DetailedTest
    public void testSentence8() throws Exception {
        parseTreebankSentence(7);
    }

    @Test
    @DetailedTest
    public void testSentence9() throws Exception {
        parseTreebankSentence(8);
    }

    @Test
    @DetailedTest
    public void testSentence10() throws Exception {
        parseTreebankSentence(9);
    }

    /**
     * Profiles parsing sentences 11-20 of WSJ section 24. This method must be overridden (calling
     * {@link #internalProfileSentences11Through20()}) in each subclass, simply to allow re-annotating the
     * {@link PerformanceTest} annotation with the expected performance for that implementation.
     * 
     * @throws Exception
     */
    @Test
    @PerformanceTest({ "mbp", "0" })
    public abstract void profileSentences11Through20() throws Exception;

    protected void internalProfileSentences11Through20() throws Exception {
        for (int i = 10; i < 20; i++) {
            parseTreebankSentence(i);
        }
    }

    protected void parseTreebankSentence(final int index) throws Exception {
        final String parse = parser.parseSentence(sentences.get(index)[0]).parseBracketString(true, false);

        String correctParse;
        final Class<P> parserClass = parserClass();
        if (SparseMatrixVectorParser.class.isAssignableFrom(parserClass)) {
            correctParse = sentences.get(index)[2];
        } else if (SparseMatrixLoopParser.class.isAssignableFrom(parserClass)) {
            correctParse = sentences.get(index)[3];
        } else {
            correctParse = sentences.get(index)[1];
        }

        // System.out.println(parse);
        assertEquals(correctParse, parse);
    }

}
