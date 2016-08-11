/*
 * Copyright 2010-2014, Oregon Health & Science University
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
 * along with the BUBS Parser. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Further documentation and contact information is available at
 *   https://code.google.com/p/bubs-parser/ 
 */
package edu.ohsu.cslu.parser.chart;

import cltool4j.BaseLogger;
import edu.ohsu.cslu.parser.ParseTask;
import edu.ohsu.cslu.parser.Parser;
import edu.ohsu.cslu.parser.Util;
import org.apache.commons.lang.NotImplementedException;

import java.util.Arrays;

public class InOutCellChart extends CellChart {

    public InOutCellChart(final ParseTask parseTask, final Parser<?> parser) {
        super(parseTask, parser);

        throw new NotImplementedException();
//        chart = new ChartCell[size][size + 1];
//        for (int start = 0; start < size; start++) {
//            for (int end = start + 1; end < size + 1; end++) {
//                chart[start][end] = new ChartCell((short) start, (short) end);
//            }
//        }
    }

    @Override
    public ChartCell getCell(final int start, final int end) {
        return (ChartCell) super.getCell(start,end);//chart[start][end];
    }

    @Override
    public float getOutside(final int start, final int end, final int nt) {
        return getCell(start, end).getOutside(nt);
    }

    public class ChartCell extends edu.ohsu.cslu.parser.chart.CellChart.HashSetChartCell {

        public float outside[];

        public ChartCell(final short start, final short end) {
            super(start, end);
            outside = new float[parser.grammar.numNonTerms()];
            Arrays.fill(outside, Float.NEGATIVE_INFINITY);

            if (start == 0 && end == size()) {
                outside[parser.grammar.startSymbol] = 0; // log(1)
                BaseLogger.singleton().finest(
                        "setting " + parser.grammar.startSymbol + " index=" + parser.grammar.startSymbol + " to 0");
            }
        }

        public float getOutside(final int nt) {
            return outside[nt];
        }

        public void updateOutside(final int nt, final float outsideProb) {
            if (viterbiMax) {
                if (outsideProb > outside[nt]) {
                    outside[nt] = outsideProb;
                }
            } else {
                // System.out.println("Adding: " + start + "," + end + "," + parser.grammar.mapNonterminal(nt)
                // + " : " +
                // outside[nt] + " + " + outsideProb + " = "+
                // ParserUtil.logSum(outside[nt], outsideProb));
                outside[nt] = (float) Util.logSum(outside[nt], outsideProb);
            }
        }
    }
}
