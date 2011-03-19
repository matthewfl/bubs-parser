package edu.ohsu.cslu.parser.spmv;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({ TestCscSpmvParser.class, TestRowParallelCscSpmvParser.class, TestBeamCscSpmvParser.class,
        TestPrunedBeamCscSpmvParser.class, TestCsrSpmvParser.class, TestCellParallelCsrSpmvParser.class,
        TestDenseVectorOpenClSpmvParser.class
// , TestPackedOpenClSpmvParser
})
public class AllSparseMatrixVectorParserTests {
}
