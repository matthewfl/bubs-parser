package edu.ohsu.cslu.parser.ml;

import edu.ohsu.cslu.grammar.SparseMatrixGrammar;
import edu.ohsu.cslu.parser.ParserOptions;
import edu.ohsu.cslu.parser.chart.DenseVectorChart;

public abstract class DenseVectorMlParser<G extends SparseMatrixGrammar> extends
        SparseMatrixLoopParser<G, DenseVectorChart> {

    public DenseVectorMlParser(final G grammar) {
        super(grammar);
    }

    public DenseVectorMlParser(final ParserOptions opts, final G grammar) {
        super(opts, grammar);
    }
}