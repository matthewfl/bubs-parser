package edu.ohsu.cslu.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;

import cltool4j.BaseLogger;
import cltool4j.ConfigProperties;
import cltool4j.GlobalConfigProperties;
import cltool4j.ThreadLocalLinewiseClTool;
import cltool4j.Threadable;
import cltool4j.args4j.CmdLineException;
import cltool4j.args4j.Option;
import edu.ohsu.cslu.grammar.ChildMatrixGrammar;
import edu.ohsu.cslu.grammar.CsrSparseMatrixGrammar;
import edu.ohsu.cslu.grammar.Grammar;
import edu.ohsu.cslu.grammar.LeftCscSparseMatrixGrammar;
import edu.ohsu.cslu.grammar.LeftHashGrammar;
import edu.ohsu.cslu.grammar.LeftListGrammar;
import edu.ohsu.cslu.grammar.LeftRightListsGrammar;
import edu.ohsu.cslu.grammar.RightCscSparseMatrixGrammar;
import edu.ohsu.cslu.grammar.SparseMatrixGrammar.LeftShiftFunction;
import edu.ohsu.cslu.grammar.SparseMatrixGrammar.PerfectIntPairHashPackingFunction;
import edu.ohsu.cslu.parser.Parser.ParserType;
import edu.ohsu.cslu.parser.Parser.ResearchParserType;
import edu.ohsu.cslu.parser.agenda.APDecodeFOM;
import edu.ohsu.cslu.parser.agenda.APGhostEdges;
import edu.ohsu.cslu.parser.agenda.APWithMemory;
import edu.ohsu.cslu.parser.agenda.AgendaParser;
import edu.ohsu.cslu.parser.agenda.CoarseCellAgendaParser;
import edu.ohsu.cslu.parser.beam.BSCPBeamConfTrain;
import edu.ohsu.cslu.parser.beam.BSCPBoundedHeap;
import edu.ohsu.cslu.parser.beam.BSCPExpDecay;
import edu.ohsu.cslu.parser.beam.BSCPFomDecode;
import edu.ohsu.cslu.parser.beam.BSCPPruneViterbi;
import edu.ohsu.cslu.parser.beam.BSCPSkipBaseCells;
import edu.ohsu.cslu.parser.beam.BSCPWeakThresh;
import edu.ohsu.cslu.parser.beam.BeamSearchChartParser;
import edu.ohsu.cslu.parser.cellselector.CellSelectorFactory;
import edu.ohsu.cslu.parser.cellselector.LeftRightBottomTopTraversal;
import edu.ohsu.cslu.parser.cellselector.OHSUCellConstraintsFactory;
import edu.ohsu.cslu.parser.cellselector.PerceptronBeamWidthFactory;
import edu.ohsu.cslu.parser.chart.CellChart;
import edu.ohsu.cslu.parser.edgeselector.BoundaryInOut;
import edu.ohsu.cslu.parser.edgeselector.EdgeSelector.EdgeSelectorType;
import edu.ohsu.cslu.parser.edgeselector.EdgeSelectorFactory;
import edu.ohsu.cslu.parser.ml.CartesianProductBinarySearchLeftChildSpmlParser;
import edu.ohsu.cslu.parser.ml.CartesianProductBinarySearchSpmlParser;
import edu.ohsu.cslu.parser.ml.CartesianProductHashSpmlParser;
import edu.ohsu.cslu.parser.ml.CartesianProductLeftChildHashSpmlParser;
import edu.ohsu.cslu.parser.ml.GrammarLoopSpmlParser;
import edu.ohsu.cslu.parser.ml.LeftChildLoopSpmlParser;
import edu.ohsu.cslu.parser.ml.RightChildLoopSpmlParser;
import edu.ohsu.cslu.parser.spmv.BeamCscSpmvParser;
import edu.ohsu.cslu.parser.spmv.CellParallelCsrSpmvParser;
import edu.ohsu.cslu.parser.spmv.CscSpmvParser;
import edu.ohsu.cslu.parser.spmv.CsrSpmvParser;
import edu.ohsu.cslu.parser.spmv.CsrSpmvPerMidpointParser;
import edu.ohsu.cslu.parser.spmv.DenseVectorOpenClSpmvParser;
import edu.ohsu.cslu.parser.spmv.PackedOpenClSpmvParser;
import edu.ohsu.cslu.parser.spmv.RowParallelCscSpmvParser;
import edu.ohsu.cslu.parser.spmv.SparseMatrixVectorParser;
import edu.ohsu.cslu.parser.spmv.SparseMatrixVectorParser.CartesianProductFunctionType;

/**
 * Driver class for all parser implementations.
 * 
 * @author Nathan Bodenstab
 * @author Aaron Dunlop
 * @since 2009
 * 
 * @version $Revision$ $Date$ $Author$
 */
@Threadable(defaultThreads = 1)
public class ParserDriver extends ThreadLocalLinewiseClTool<Parser<?>, ParseResult> {

    // Global vars to create parser
    public CellSelectorFactory cellSelectorFactory = LeftRightBottomTopTraversal.FACTORY;
    public EdgeSelectorFactory edgeSelectorFactory = new EdgeSelectorFactory(EdgeSelectorType.Inside);
    private Grammar grammar;

    // == Parser options ==
    @Option(name = "-p", aliases = { "--parser" }, metaVar = "parser", usage = "Parser implementation")
    private ParserType parserType = ParserType.CKY;

    @Option(name = "-rp", aliases = { "--research-parser" }, hidden = true, metaVar = "parser", usage = "Research Parser implementation")
    private ResearchParserType researchParserType = null;

    @Option(name = "-real", usage = "Use real semiring (sum) instead of tropical (max) for inside/outside calculations")
    public boolean realSemiring = false;

    // @Option(name = "-cpf", hidden = true, aliases = { "--cartesian-product-function" }, metaVar = "function", usage =
    // "Cartesian-product function (only used for SpMV parsers)")
    @Option(name = "-cpf", hidden = true, metaVar = "function", usage = "Cartesian-product function (only used for SpMV parsers)")
    private CartesianProductFunctionType cartesianProductFunctionType = CartesianProductFunctionType.PerfectHash2;

    // @Option(name = "-cp", aliases = { "--cell-processing-type" }, metaVar = "type", usage =
    // "Chart cell processing type")
    // private ChartCellProcessingType chartCellProcessingType = ChartCellProcessingType.CellCrossList;

    @Option(name = "-fom", metaVar = "fom", hidden = true, usage = "Figure of Merit to use for parser (name or model file)")
    private String fomTypeOrModel = "Inside";

    @Option(name = "-beamConfModel", usage = "Beam Confidence Model for beam-search parsers")
    private String beamConfModelFileName = null;
    // private AveragedPerceptron beamConfModel = null;

    @Option(name = "-beamConfBias", usage = "comma seperated bias for each bin in model; default is no bias")
    public String beamConfBias = null;

    // @Option(name = "-beamMultiBin", hidden = true, usage =
    // "Use old multi-bin classification instead of multiple binary classifiers")
    // public static boolean multiBin = false;

    @Option(name = "-reparse", metaVar = "N", hidden = true, usage = "If no solution, loosen constraints and reparse N times")
    public int reparse = 0;

    // Nate: I don't think we need to expose this to the user. Instead
    // there should be different possible parsers since changing the
    // cell selection strategy only matters for a few of them
    // @Option(name = "-cellSelect", hidden = true, metaVar = "TYPE", usage = "Method for cell selection")
    // public CellSelectorType cellSelectorType = CellSelectorType.LeftRightBottomTop;
    // public CellSelector cellSelector;
    //
    // @Option(name = "-cellModel", metaVar = "file", hidden = true, usage = "Model for span selection")
    // private String cellModelFileName = null;
    // public BufferedReader cellModelStream = null;

    @Option(name = "-beamTune", usage = "Tuning params for beam search: maxBeamWidth,globalScoreDelta,localScoreDelta,factoredCellBeamWidth")
    public String beamTune = "15,INF,7,15";

    @Option(name = "-ccModel", metaVar = "file", usage = "CSLU Chart Constraints model (Roark and Hollingshead, 2009)")
    private String chartConstraintsModel = null;

    @Option(name = "-ccTune", metaVar = "val", usage = "CSLU Chart Constraints for Absolute (A), High Precision (P), or Linear (N): A,start,end,unary | P,pct | N,int")
    public String chartConstraintsThresh = "A,120,120,inf";

    // (1) absolute thresh A,start,end,unary
    // (2) high precision P,pct (pct cells closed score > 0)
    // (3) linear complexity N,int (x*N max open)

    @Option(name = "-ccPrint", hidden = true, usage = "Print Cell Constraints for each input sentence and exit (no parsing done)")
    public static boolean chartConstraintsPrint = false;

    // == Grammar options ==
    @Option(name = "-g", metaVar = "grammar", usage = "Grammar file (text, gzipped text, or binary serialized")
    private String grammarFile = null;

    @Option(name = "-m", metaVar = "file", usage = "Model file (binary serialized")
    private File modelFile = null;

    // == Output options ==
    @Option(name = "-max", aliases = { "--max-length" }, metaVar = "len", usage = "Skip sentences longer than LEN")
    int maxLength = 200;

    // TODO: option doesn't work anymore
    // @Option(name = "-scores", usage = "Print inside scores for each non-term in result tree")
    // boolean printInsideProbs = false;

    @Option(name = "-unk", usage = "Print unknown words as their UNK replacement class")
    boolean printUnkLabels = false;

    @Option(name = "-binary", usage = "Leave parse tree output in binary-branching form")
    public boolean binaryTreeOutput = false;

    // == Other options ==
    // TODO These shouldn't really be static. Parser implementations should use the ParserDriver instance passed in
    @Option(name = "-x1", hidden = true, usage = "Tuning param #1")
    public static float param1 = -1;

    @Option(name = "-x2", hidden = true, usage = "Tuning param #2")
    public static float param2 = -1;

    @Option(name = "-x3", hidden = true, usage = "Tuning param #3")
    public static float param3 = -1;

    @Option(name = "-feats", hidden = true, usage = "Feature template string: lt rt lt_lt-1 rw_rt loc ...")
    public static String featTemplate;

    // TODO: embed this info into the grammar file as meta data and remove these options
    @Option(name = "-hMarkov", hidden = true, usage = "Horizontal Markov order of input Grammar")
    private int horizontalMarkov = 0;

    @Option(name = "-vMarkov", hidden = true, usage = "Vertical Markov order of input Grammar")
    private int verticalMarkov = 0;

    @Option(name = "-annotatePOS", hidden = true, usage = "Input Grammar has annotation on POS tags")
    private boolean annotatePOS = false;

    @Option(name = "-oldunk", hidden = true, usage = "Use old method of UNK replacement to match old grammars")
    public static boolean oldUNK = false;

    private long parseStartTime;
    private LinkedList<Parser<?>> parserInstances = new LinkedList<Parser<?>>();

    /**
     * Configuration property key for the number of row-level or cell-level threads requested by the user. We handle
     * threading at three levels; threading per-sentence is handled by the command-line tool infrastructure and
     * specified with the standard '-xt' parameter. Row-level and cell-level threading is handled by the parser instance
     * and specified with this option.
     */
    public final static String OPT_REQUESTED_THREAD_COUNT = "requestedThreads";

    /**
     * Configuration property key for the number of row-level or cell-level threads actually used. In some cases the
     * number of threads requested is impractical (e.g., if it is greater than the maximum number of cells in a row or
     * greater than the number of grammar rows). {@link Parser} instances which make use of
     * {@link #OPT_REQUESTED_THREAD_COUNT} should populate this property to indicate the number of threads actually
     * used. Among other potential uses, this allows {@link #cleanup()} to report accurate timing information.
     */
    public final static String OPT_CONFIGURED_THREAD_COUNT = "actualThreads";

    public static void main(final String[] args) throws Exception {
        run(args);
    }

    @Override
    // run once at initialization regardless of number of threads
    public void setup() throws Exception {

        // map simplified parser choices to the specific research version
        if (researchParserType == null) {
            switch (parserType) {
            case CKY:
                researchParserType = ResearchParserType.ECPCellCrossList;
                break;
            case Agenda:
                researchParserType = ResearchParserType.APWithMemory;
                break;
            case Beam:
                researchParserType = ResearchParserType.BeamSearchChartParser;
                // Using the above beam parser until all the model stuff has been finished
                // researchParserType = ResearchParserType.BeamCscSpmv;
                break;
            case Matrix:
                researchParserType = ResearchParserType.CscSpmv;
                cartesianProductFunctionType = CartesianProductFunctionType.PerfectHash2;
                break;
            default:
                throw new IllegalArgumentException("Unsupported parser type");
            }
        }

        if (researchParserType == ResearchParserType.ECPInsideOutside) {
            this.realSemiring = true;
        }

        grammar = readGrammar(grammarFile, researchParserType, cartesianProductFunctionType);

        if (modelFile != null) {
            final ObjectInputStream ois = new ObjectInputStream(fileAsInputStream(modelFile));
            final String metadata = (String) ois.readObject();
            final ConfigProperties props = (ConfigProperties) ois.readObject();
            GlobalConfigProperties.singleton().mergeUnder(props);

            BaseLogger.singleton().fine("Reading grammar...");
            this.grammar = (Grammar) ois.readObject();

            BaseLogger.singleton().fine("Reading FOM...");
            edgeSelectorFactory = (EdgeSelectorFactory) ois.readObject();

        } else {

            if (fomTypeOrModel.equals("Inside")) {
                edgeSelectorFactory = new EdgeSelectorFactory(EdgeSelectorType.Inside);
            } else if (fomTypeOrModel.equals("NormalizedInside")) {
                edgeSelectorFactory = new EdgeSelectorFactory(EdgeSelectorType.NormalizedInside);
            } else if (fomTypeOrModel.equals("InsideWithFwdBkwd")) {
                edgeSelectorFactory = new EdgeSelectorFactory(EdgeSelectorType.InsideWithFwdBkwd);
            } else if (new File(fomTypeOrModel).exists()) {
                // Assuming boundary FOM
                edgeSelectorFactory = new BoundaryInOut(EdgeSelectorType.BoundaryInOut, grammar,
                        fileAsBufferedReader(fomTypeOrModel));
            } else {
                throw new IllegalArgumentException("-fom value '" + fomTypeOrModel + "' not valid.");
            }

            if (researchParserType == ResearchParserType.BSCPBeamConfTrain && featTemplate == null) {
                throw new CmdLineException("ERROR: BSCPTrainFOMConfidence requires -feats to be non-empty");
            }

            if (chartConstraintsModel != null) {
                cellSelectorFactory = new OHSUCellConstraintsFactory(fileAsBufferedReader(chartConstraintsModel),
                        chartConstraintsThresh, grammar.isLeftFactored());
            }

            if (beamConfModelFileName != null) {
                cellSelectorFactory = new PerceptronBeamWidthFactory(fileAsBufferedReader(beamConfModelFileName),
                        beamConfBias);
            }
        }

        BaseLogger.singleton().fine(grammar.getStats());
        BaseLogger.singleton().fine(optionsToString());

        // TODO: until we embed this info into the model itself ... read it from args
        grammar.annotatePOS = annotatePOS;
        grammar.isLatentVariableGrammar = true;
        grammar.horizontalMarkov = horizontalMarkov;
        grammar.verticalMarkov = verticalMarkov;

        parseStartTime = System.currentTimeMillis();
    }

    public static Grammar readGrammar(final String grammarFile, final ResearchParserType researchParserType,
            final CartesianProductFunctionType cartesianProductFunctionType) throws Exception {

        // Handle gzipped and non-gzipped grammar files
        final InputStream grammarInputStream = grammarFile.endsWith(".gz") ? new GZIPInputStream(new FileInputStream(
                grammarFile)) : new FileInputStream(grammarFile);

        // Read the generic grammar in either text or binary-serialized format.
        final Grammar genericGrammar = Grammar.read(grammarInputStream);

        // Construct the requested grammar type from the generic grammar
        return createGrammar(genericGrammar, researchParserType, cartesianProductFunctionType);
    }

    /**
     * Creates a specific {@link Grammar} subclass, based on the generic instance passed in.
     * 
     * TODO Use the generic grammar types on the parser class to eliminate this massive switch?
     * 
     * @param genericGrammar
     * @param researchParserType
     * @return a Grammar instance
     * @throws Exception
     */
    public static Grammar createGrammar(final Grammar genericGrammar, final ResearchParserType researchParserType,
            final CartesianProductFunctionType cartesianProductFunctionType) throws Exception {

        switch (researchParserType) {
        case ECPInsideOutside:
        case ECPCellCrossList:
            return new LeftListGrammar(genericGrammar);

        case ECPCellCrossHash:
            return new LeftHashGrammar(genericGrammar);

        case ECPCellCrossMatrix:
            return new ChildMatrixGrammar(genericGrammar);

        case ECPGrammarLoop:
        case ECPGrammarLoopBerkeleyFilter:
            return genericGrammar;

        case AgendaParser:
        case APWithMemory:
        case APGhostEdges:
        case APDecodeFOM:
            return new LeftRightListsGrammar(genericGrammar);

        case BeamSearchChartParser:
        case BSCPPruneViterbi:
        case BSCPOnlineBeam:
        case BSCPBoundedHeap:
        case BSCPExpDecay:
        case BSCPPerceptronCell:
        case BSCPFomDecode:
        case BSCPBeamConfTrain:
            // case BSCPBeamConf:
        case CoarseCellAgenda:
        case CoarseCellAgendaCSLUT:
            return new LeftHashGrammar(genericGrammar);

        case CsrSpmv:
        case CellParallelCsrSpmv:
        case CsrSpmvPerMidpoint:
            switch (cartesianProductFunctionType) {
            case Simple:
                return new CsrSparseMatrixGrammar(genericGrammar, LeftShiftFunction.class);
            case PerfectHash2:
                return new CsrSparseMatrixGrammar(genericGrammar, PerfectIntPairHashPackingFunction.class);
            default:
                throw new Exception("Unsupported cartesian-product-function type: " + cartesianProductFunctionType);
            }

        case PackedOpenClSparseMatrixVector:
        case DenseVectorOpenClSparseMatrixVector:
            return new CsrSparseMatrixGrammar(genericGrammar, LeftShiftFunction.class);

        case CscSpmv:
        case RowParallelCscSpmv:
        case BeamCscSpmv:
            switch (cartesianProductFunctionType) {
            case Simple:
                return new LeftCscSparseMatrixGrammar(genericGrammar, LeftShiftFunction.class);
            case PerfectHash2:
                return new LeftCscSparseMatrixGrammar(genericGrammar, PerfectIntPairHashPackingFunction.class);
            default:
                throw new Exception("Unsupported cartesian-product-function type: " + cartesianProductFunctionType);
            }

        case LeftChildMatrixLoop:
        case CartesianProductBinarySearch:
        case CartesianProductBinarySearchLeftChild:
        case CartesianProductHash:
        case CartesianProductLeftChildHash:
            return new LeftCscSparseMatrixGrammar(genericGrammar, LeftShiftFunction.class);
        case RightChildMatrixLoop:
            return new RightCscSparseMatrixGrammar(genericGrammar, LeftShiftFunction.class);
        case GrammarLoopMatrixLoop:
            return new CsrSparseMatrixGrammar(genericGrammar, LeftShiftFunction.class);

        default:
            throw new Exception("Unsupported parser type: " + researchParserType);
        }
    }

    @Override
    public Parser<?> createLocal() {
        final Parser<?> parser = createParser(researchParserType, grammar, this);
        parserInstances.add(parser);
        return parser;
    }

    public Parser<?> createParser(final ResearchParserType researchParserType, final Grammar grammar,
            final ParserDriver parserOptions) {
        switch (researchParserType) {
        case ECPCellCrossList:
            return new ECPCellCrossList(parserOptions, (LeftListGrammar) grammar);
        case ECPCellCrossHash:
            return new ECPCellCrossHash(parserOptions, (LeftHashGrammar) grammar);
        case ECPCellCrossMatrix:
            return new ECPCellCrossMatrix(parserOptions, (ChildMatrixGrammar) grammar);
        case ECPGrammarLoop:
            return new ECPGrammarLoop(parserOptions, grammar);
        case ECPGrammarLoopBerkeleyFilter:
            return new ECPGrammarLoopBerkFilter(parserOptions, grammar);
        case ECPInsideOutside:
            return new ECPInsideOutside(parserOptions, (LeftListGrammar) grammar);

        case AgendaParser:
            return new AgendaParser(parserOptions, (LeftRightListsGrammar) grammar);
        case APWithMemory:
            return new APWithMemory(parserOptions, (LeftRightListsGrammar) grammar);
        case APGhostEdges:
            return new APGhostEdges(parserOptions, (LeftRightListsGrammar) grammar);
        case APDecodeFOM:
            return new APDecodeFOM(parserOptions, (LeftRightListsGrammar) grammar);

        case BeamSearchChartParser:
            return new BeamSearchChartParser<LeftHashGrammar, CellChart>(parserOptions, (LeftHashGrammar) grammar);
        case BSCPPruneViterbi:
            return new BSCPPruneViterbi(parserOptions, (LeftHashGrammar) grammar);
        case BSCPOnlineBeam:
            return new BSCPWeakThresh(parserOptions, (LeftHashGrammar) grammar);
        case BSCPBoundedHeap:
            return new BSCPBoundedHeap(parserOptions, (LeftHashGrammar) grammar);
        case BSCPExpDecay:
            return new BSCPExpDecay(parserOptions, (LeftHashGrammar) grammar);
        case BSCPPerceptronCell:
            return new BSCPSkipBaseCells(parserOptions, (LeftHashGrammar) grammar);
        case BSCPFomDecode:
            return new BSCPFomDecode(parserOptions, (LeftHashGrammar) grammar);
            // case BSCPBeamConf:
            // return new BSCPBeamConf(parserOptions, (LeftHashGrammar) grammar, parserOptions.beamConfModel);
        case BSCPBeamConfTrain:
            return new BSCPBeamConfTrain(parserOptions, (LeftHashGrammar) grammar);

        case CoarseCellAgenda:
            return new CoarseCellAgendaParser(parserOptions, (LeftHashGrammar) grammar);
            // case CoarseCellAgendaCSLUT:
            // final CSLUTBlockedCells cslutScores = (CSLUTBlockedCells) CellSelector.create(
            // parserOptions.cellSelectorType, parserOptions.cellModelStream, parserOptions.cslutScoresStream);
            // return new CoarseCellAgendaParserWithCSLUT(parserOptions, (LeftHashGrammar) grammar, cslutScores);

        case CsrSpmv:
            return new CsrSpmvParser(parserOptions, (CsrSparseMatrixGrammar) grammar);
        case CellParallelCsrSpmv:
            return new CellParallelCsrSpmvParser(parserOptions, (CsrSparseMatrixGrammar) grammar);
        case CsrSpmvPerMidpoint:
            return new CsrSpmvPerMidpointParser(parserOptions, (CsrSparseMatrixGrammar) grammar);
        case CscSpmv:
            return new CscSpmvParser(parserOptions, (LeftCscSparseMatrixGrammar) grammar);
        case RowParallelCscSpmv:
            return new RowParallelCscSpmvParser(parserOptions, (LeftCscSparseMatrixGrammar) grammar);
        case BeamCscSpmv:
            return new BeamCscSpmvParser(parserOptions, (LeftCscSparseMatrixGrammar) grammar);
        case DenseVectorOpenClSparseMatrixVector:
            return new DenseVectorOpenClSpmvParser(parserOptions, (CsrSparseMatrixGrammar) grammar);
        case PackedOpenClSparseMatrixVector:
            return new PackedOpenClSpmvParser(parserOptions, (CsrSparseMatrixGrammar) grammar);

        case LeftChildMatrixLoop:
            return new LeftChildLoopSpmlParser(parserOptions, (LeftCscSparseMatrixGrammar) grammar);
        case RightChildMatrixLoop:
            return new RightChildLoopSpmlParser(parserOptions, (RightCscSparseMatrixGrammar) grammar);
        case GrammarLoopMatrixLoop:
            return new GrammarLoopSpmlParser(parserOptions, (CsrSparseMatrixGrammar) grammar);
        case CartesianProductBinarySearch:
            return new CartesianProductBinarySearchSpmlParser(parserOptions, (LeftCscSparseMatrixGrammar) grammar);
        case CartesianProductBinarySearchLeftChild:
            return new CartesianProductBinarySearchLeftChildSpmlParser(parserOptions,
                    (LeftCscSparseMatrixGrammar) grammar);
        case CartesianProductHash:
            return new CartesianProductHashSpmlParser(parserOptions, (LeftCscSparseMatrixGrammar) grammar);
        case CartesianProductLeftChildHash:
            return new CartesianProductLeftChildHashSpmlParser(parserOptions, (LeftCscSparseMatrixGrammar) grammar);

        default:
            throw new IllegalArgumentException("Unsupported parser type");
        }
    }

    @Override
    protected FutureTask<ParseResult> lineTask(final String sentence) {
        return new FutureTask<ParseResult>(new Callable<ParseResult>() {
            @Override
            public ParseResult call() throws Exception {
                return getLocal().parseSentence(sentence);
            }
        });
    }

    @Override
    protected void output(final ParseResult parseResult) {

        System.out.println(parseResult.parseBracketString);
        BaseLogger.singleton().fine(parseResult.toString() + " " + parseResult.parserStats);
    }

    @Override
    protected void cleanup() {
        final float parseTime = (System.currentTimeMillis() - parseStartTime) / 1000f;

        // If the individual parser configured a thread count (e.g. CellParallelCsrSpmvParser), compute CPU-time using
        // that thread count; otherwise, assume maxThreads is correct
        final int threads = GlobalConfigProperties.singleton().containsKey(OPT_CONFIGURED_THREAD_COUNT) ? GlobalConfigProperties
                .singleton().getIntProperty(OPT_CONFIGURED_THREAD_COUNT) : maxThreads;

        // Note that this CPU-time computation does not include GC time
        final float cpuTime = parseTime * threads;
        final int sentencesParsed = Parser.sentenceNumber;

        final StringBuilder sb = new StringBuilder();
        sb.append(String.format("INFO: numSentences=%d totalSeconds=%.3f cpuSeconds=%.3f avgSecondsPerSent=%.3f",
                sentencesParsed, parseTime, cpuTime, cpuTime / sentencesParsed));
        if (parserInstances.getFirst() instanceof SparseMatrixVectorParser) {
            sb.append(String.format(" totalXProductTime=%d totalBinarySpMVTime=%d",
                    SparseMatrixVectorParser.totalCartesianProductTime, SparseMatrixVectorParser.totalBinarySpMVTime));
        }

        BaseLogger.singleton().info(sb.toString());

        for (final Parser<?> p : parserInstances) {
            p.shutdown();
        }
    }

    public String optionsToString() {
        String s = "OPTS:";
        s += " ParserType=" + researchParserType;
        // s += prefix + "CellSelector=" + cellSelectorType + "\n";
        s += " FOM=" + fomTypeOrModel;
        s += " ViterbiMax=" + viterbiMax();
        s += " x1=" + param1;
        s += " x2=" + param2;
        s += " x3=" + param3;
        return s;
    }

    static public ParserDriver defaultTestOptions() {
        final ParserDriver opts = new ParserDriver();
        BaseLogger.singleton().setLevel(Level.FINER);
        return opts;
    }

    public boolean viterbiMax() {
        return !this.realSemiring;
    }
}
