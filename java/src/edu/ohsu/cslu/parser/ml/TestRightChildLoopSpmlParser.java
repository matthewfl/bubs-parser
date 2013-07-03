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
package edu.ohsu.cslu.parser.ml;

import java.io.Reader;

import org.cjunit.PerformanceTest;
import org.junit.Test;

import edu.ohsu.cslu.grammar.Grammar;
import edu.ohsu.cslu.grammar.SparseMatrixGrammar.PackingFunction;
import edu.ohsu.cslu.grammar.SparseMatrixGrammar.RightShiftFunction;
import edu.ohsu.cslu.grammar.TokenClassifier.TokenClassifierType;

/**
 * Unit tests for {@link RightChildLoopSpmlParser}
 */
public class TestRightChildLoopSpmlParser extends SparseMatrixLoopParserTestCase<RightChildLoopSpmlParser> {

    @Override
    public Grammar createGrammar(final Reader grammarReader) throws Exception {
        return grammarClass()
                .getConstructor(new Class[] { Reader.class, TokenClassifierType.class, Class.class })
                .newInstance(new Object[] { grammarReader, TokenClassifierType.DecisionTree, RightShiftFunction.class });
    }

    @Override
    protected Class<? extends PackingFunction> packingFunctionClass() {
        return RightShiftFunction.class;
    }

    @Override
    @Test
    @PerformanceTest({ "mbp", "4939", "mbp2012", "2805" })
    public void profileSentences11Through20() throws Exception {
        internalProfileSentences11Through20();
    }
}
