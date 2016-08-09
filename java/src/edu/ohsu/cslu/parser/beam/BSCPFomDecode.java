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
package edu.ohsu.cslu.parser.beam;

import cltool4j.BaseLogger;
import edu.ohsu.cslu.grammar.LeftHashGrammar;
import edu.ohsu.cslu.grammar.Production;
import edu.ohsu.cslu.parser.ParseTask;
import edu.ohsu.cslu.parser.Parser;
import edu.ohsu.cslu.parser.ParserDriver;
import edu.ohsu.cslu.parser.Util;
import edu.ohsu.cslu.parser.chart.CellChart;
import edu.ohsu.cslu.parser.chart.CellChart.ChartEdge;
import edu.ohsu.cslu.parser.chart.CellChart.HashSetChartCell;
import org.apache.commons.lang.NotImplementedException;

import java.util.Arrays;
import java.util.PriorityQueue;

/**
 * @author Nathan Bodenstab
 */
public class BSCPFomDecode extends BSCPPruneViterbi {

    // NOTE: this isn't exactly the same as the exaustive method since
    // only the edges with the *locally* optimal FOM for each parent=A
    // are compared to each other
    float maxcFOM[][][];
    ChartEdge maxcBackptr[];
    boolean maxProduct = true;

    public BSCPFomDecode(final ParserDriver opts, final LeftHashGrammar grammar) {
        super(opts, grammar);

        if (opts.decodeMethod == Parser.DecodeMethod.FOMSum) {
            maxProduct = false;
        }
    }

    @Override
    protected void initSentence(final ParseTask parseTask) {
        chart = new CellChart(parseTask, this);

        final int n = parseTask.sentenceLength();
        maxcFOM = new float[n][n + 1][grammar.numNonTerms()];
    }

    private void updateMaxcFOM(final ChartEdge edge) {
        final int start = edge.start();
        final int end = edge.end();
        final int A = edge.prod.parent;
        float score;

        if (edge.prod.isBinaryProd()) {
            final int midpt = edge.midpt();
            if (maxProduct) {
                score = edge.fom + maxcFOM[start][midpt][edge.prod.leftChild]
                        + maxcFOM[midpt][end][edge.prod.rightChild];
            } else {
                score = (float) Util.logSum(edge.fom, Util.logSum(maxcFOM[start][midpt][edge.prod.leftChild],
                        maxcFOM[midpt][end][edge.prod.rightChild]));
            }
        } else if (edge.prod.isUnaryProd()) {
            if (maxProduct) {
                score = edge.fom + maxcFOM[start][end][edge.prod.child()];
            } else {
                score = (float) Util.logSum(edge.fom, maxcFOM[start][end][edge.prod.child()]);
            }
        } else {
            score = edge.fom;
        }

        if (score > maxcFOM[start][end][A]) {
            maxcFOM[start][end][A] = score;
            maxcBackptr[A] = edge;
            BaseLogger.singleton().fine("" + edge);
        }
    }

    @Override
    protected void addEdgeCollectionToChart(final HashSetChartCell cell) {

        agenda = new PriorityQueue<ChartEdge>();
        for (final ChartEdge viterbiEdge : bestEdges) {
            // if (viterbiEdge != null && viterbiEdge.fom > bestFOM - beamDeltaThresh) {
            if (fomCheckAndUpdate(viterbiEdge)) {
                agenda.add(viterbiEdge);
                cellPushed++;
            }
        }

        Arrays.fill(maxcFOM[cell.start()][cell.end()], Float.NEGATIVE_INFINITY);
        maxcBackptr = new ChartEdge[grammar.numNonTerms()];

        if (cell.width() == 1) {
            for (final ChartEdge lexEdge : cell.getBestEdgeList()) {
                updateMaxcFOM(lexEdge);
            }
        }

        ChartEdge edge = agenda.poll();
        while (edge != null && cellPopped < beamWidth && fomCheckAndUpdate(edge)) {
            cellPopped++;
            updateMaxcFOM(edge);
            if (edge.inside() > cell.getInside(edge.prod.parent)) {
                cell.updateInside(edge);

                // Add unary productions to agenda so they can compete with binary productions
                // No unary productions are added if CellConstraints are turned on and this cell
                // is only open to factored non-terms because getUnaryProd..() will return null
                for (final Production p : grammar.getUnaryProductionsWithChild(edge.prod.parent)) {
                    addEdgeToCollection(chart.new ChartEdge(p, cell));
                }
            }
            edge = agenda.poll();
        }

        // while (agenda.isEmpty() == false && cellPopped < beamWidth && fomCheckAndUpdate(edge)) {
        // edge = agenda.poll();
        // if (edge.inside() > cell.getInside(edge.prod.parent)) {
        // cell.updateInside(edge);
        // cellPopped++;
        // updateMaxcFOM(edge);
        //
        // // Add unary productions to agenda so they can compete with binary productions
        // for (final Production p : grammar.getUnaryProductionsWithChild(edge.prod.parent)) {
        // unaryEdge = chart.new ChartEdge(p, cell);
        // final int nt = p.parent;
        // cellConsidered++;
        // if ((bestEdges[nt] == null || unaryEdge.fom > bestEdges[nt].fom)
        // && fomCheckAndUpdate(unaryEdge)) {
        // agenda.add(unaryEdge);
        // cellPushed++;
        // }
        // }
        // }
        // }

        throw new NotImplementedException();
//        cell.bestEdge = maxcBackptr;

//        if (collectDetailedStatistics) {
//            System.out.println(cell.width() + " [" + cell.start() + "," + cell.end() + "] #pop=" + cellPopped
//                    + " #push=" + cellPushed + " #considered=" + cellConsidered);
//        }
    }
}
