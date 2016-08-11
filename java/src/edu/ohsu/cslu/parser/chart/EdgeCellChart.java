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

import edu.ohsu.cslu.grammar.Grammar;
import edu.ohsu.cslu.grammar.Production;
import edu.ohsu.cslu.parser.Parser;
import org.apache.commons.lang.NotImplementedException;

import java.util.LinkedList;
import java.util.List;

public class EdgeCellChart extends CellChart {

    protected EdgeCellChart() {

    }

    public EdgeCellChart(final int size, final boolean viterbiMax, final Parser<Grammar> parser) {
        // super(size, viterbiMax, parser);
        this.size = size;
        this.viterbiMax = true;
        this.parser = parser;

        throw new NotImplementedException();
//        chart = new ChartCell[size][size + 1];
//        for (int start = 0; start < size; start++) {
//            for (int end = start + 1; end < size + 1; end++) {
//                setCell(start,end, new ChartCell((short) start, (short) end));
//            }
//        }
    }

    @Override
    public ChartCell getCell(final int start, final int end) {
        return (ChartCell) super.getCell(start,end); //chart[start][end];
    }

    @Override
    public ChartCell getRootCell() {
        throw new NotImplementedException();
//        return (ChartCell) chart[0][size];
    }

    @Override
    public float getInside(final int start, final int end, final int nt) {
        return getCell(start, end).getInside(nt);

    }

    @Override
    public void updateInside(final int start, final int end, final int nt, final float insideProb) {
        getCell(start, end).updateInside(nt, insideProb);
    }

    public class ChartCell extends CellChart.HashSetChartCell {

        public ChartCell(final short start, final short end) {
            super(start, end);
        }

        public List<ChartEdge> getEdges() {
            final List<ChartEdge> edges = new LinkedList<ChartEdge>();
            for (int i = 0; i < bestEdge.length(); i++) {
                ChartEdge e = bestEdge.get(i);
                if (e != null) {
                    edges.add(e);
                }
            }
            return edges;
        }

        public boolean hasEdge(final ChartEdge edge) {
            final ChartEdge curEdge = bestEdge.get(edge.prod.parent);
            if (curEdge == null)
                return false;
            if (!curEdge.prod.equals(edge.prod))
                return false;
            // make sure midpoints are the same if it's a binary production/edge
            if (edge.prod.isBinaryProd() && (edge.midpt() != curEdge.midpt()))
                return false;

            return true;
        }

        private boolean insertNewEdge(final ChartEdge edge) {
            // assuming bestEdge[parent] == null
            final int parent = edge.prod.parent;
            numEdgesAdded++;

            throw new NotImplementedException();
            //bestEdge[parent] = edge;

//            if (isLexCell && grammar.isPos((short) parent)) {
//                // posNTs.addLast(parent);
//                posNTs.add(parent);
//            }
//            return true;
        }

        // @Override
        public boolean addEdge(final ChartEdge edge, final float insideProb) {
            // public void addEdge(final ChartEdge edge, final float insideProb) {
            final int parent = edge.prod.parent;
            final ChartEdge prevBestEdge = bestEdge.get(parent);
            numEdgesConsidered++;

            if (prevBestEdge == null) {
                return insertNewEdge(edge);
                // insertNewEdge(edge);
            } else if (insideProb > getInside(parent)) {
                throw new NotImplementedException();
//                bestEdge[parent] = edge;
//                return true;
            }

            return false;
        }

        /**
         * Alternate addEdge() function so we aren't required to create a new ChartEdge object in the CYK inner loop for
         * every potential new edge entry. Adds an edge to the cell if the edge's probability is greater than an
         * existing edge with the same non-terminal. Optional operation (some {@link ChartCell} implementations may be
         * immutable).
         * 
         * @param p The production to add
         * @param leftCell The left child of this production
         * @param rightCell The right child of this production
         * @param insideProb The production probability
         */
        // @Override
        public boolean addEdge(final Production p, final ChartCell leftCell, final ChartCell rightCell,
                final float insideProb) {
            final int parent = p.parent;
            final ChartEdge prevBestEdge = bestEdge.get(parent);
            numEdgesConsidered++;

            // System.out.println("Considering: " + new ChartEdge(p, leftCell, rightCell, insideProb));
            if (prevBestEdge == null) {
                return insertNewEdge(new ChartEdge(p, leftCell, rightCell));
            } else if (getInside(parent) < insideProb) {
                prevBestEdge.prod = p;
                prevBestEdge.leftCell = leftCell;
                prevBestEdge.rightCell = rightCell;
                // prevBestEdge.inside = insideProb;
                // bestLeftEdgesHasChanged = true; // pointer to old edge will still be correct
                // numEdgesAdded++; // we are replacing an edge, so the same number are in the chart
                return true;
            }
            return false;
        }

        public boolean addEdgeForceOverwrite(final ChartEdge edge) {
            final int parent = edge.prod.parent;
            final ChartEdge prevBestEdge = bestEdge.get(parent);
            numEdgesConsidered++;

            if (prevBestEdge == null) {
                return insertNewEdge(edge);
            }
            throw new NotImplementedException();
//            bestEdge[parent] = edge;
//            return false;
        }

        public boolean canBuild(final ChartEdge edge) {
            if (edge.prod.isBinaryProd()) {
                final int midpt = edge.midpt();
                if (getCell(start, midpt).hasNT(edge.prod.leftChild) && getCell(midpt, end).hasNT(edge.prod.rightChild)) {
                    return true;
                }
            } else {
                if (hasNT(edge.prod.child())) {
                    return true;
                }
            }
            return false;
        }
    }
}
