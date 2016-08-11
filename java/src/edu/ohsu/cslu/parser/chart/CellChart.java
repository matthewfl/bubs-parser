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
import edu.ohsu.cslu.parser.ParseTask;
import edu.ohsu.cslu.parser.Parser;
import edu.ohsu.cslu.parser.Parser.DecodeMethod;
import omfg.AlternateAtomicReferenceArray;
import org.apache.commons.lang.NotImplementedException;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

public class CellChart extends Chart {

    protected Parser<? extends Grammar> parser;
    //protected HashSetChartCell chart[][];
    protected HashSetChartCell chart[];
    protected int parseTaskSentenceLength;
    protected boolean viterbiMax;

    protected CellChart() {

    }

    public CellChart(final ParseTask parseTask, final Parser<?> parser) {
        super(parseTask, parser.grammar);
        this.parser = parser;
        this.viterbiMax = (parser.opts.decodeMethod == DecodeMethod.ViterbiMax);
//        chart = new HashSetChartCell[parseTask.sentenceLength()][parseTask.sentenceLength() + 1];
        parseTaskSentenceLength = parseTask.sentenceLength();
        chart = new HashSetChartCell[parseTaskSentenceLength * (parseTaskSentenceLength + 1)];
        reset(parseTask);
    }

    @Override
    public void reset(final ParseTask newParseTask) {
        this.parseTask = newParseTask;
        final int n = parseTask.sentenceLength();
        for (int start = 0; start < n; start++) {
            for (int end = start + 1; end < n + 1; end++) {
                setCell(start,end, new HashSetChartCell(start, end));
            }
        }
    }

    @Override
    public HashSetChartCell getCell(final int start, final int end) {
//        return chart[start][end];
        return chart[start + end * parseTaskSentenceLength];
    }

    protected void setCell(final int start, final int end, HashSetChartCell e) {
//        chart[start][end] = e;
        chart[start + end * parseTaskSentenceLength] = e;
    }

    @Override
    public HashSetChartCell getRootCell() {
        return getCell(0, size);
    }

    @Override
    public float getInside(final int start, final int end, final int nt) {
        return getCell(start, end).getInside(nt);

    }

    @Override
    public void updateInside(final int start, final int end, final int nt, final float insideProb) {
        getCell(start, end).updateInside(nt, insideProb);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(10240);
        for (int span = 1; span <= size; span++) {
            for (int start = 0; start <= size - span; start++) {
                final int end = start + span;
                sb.append(getCell(start, end).toString());
                sb.append("\n\n");
            }
        }
        return sb.toString();
    }

    // TODO: why is this not its own class in its own file? Do we actually need
    // the abstraction of a ChartCell? Can we put this all into Chart?
    public class HashSetChartCell extends ChartCell implements Comparable<HashSetChartCell> {

        public float fom = Float.NEGATIVE_INFINITY;
        protected boolean isLexCell;

        // NB: why do we have bestEdge AND inside? These could get out of sync...
        //public ChartEdge[] bestEdge;
//        public AtomicReferenceArray<ChartEdge> bestEdge;
        public AlternateAtomicReferenceArray<ChartEdge> bestEdge;
        //public float[] inside;
        protected HashSet<Integer> childNTs = new HashSet<Integer>();
        protected HashSet<Integer> leftChildNTs = new HashSet<Integer>();
        protected HashSet<Integer> rightChildNTs = new HashSet<Integer>();
        protected HashSet<Integer> posNTs = new HashSet<Integer>();

        public HashSetChartCell(final int start, final int end) {
            super(start, end);

            if (end - start == 1) {
                isLexCell = true;
            } else {
                isLexCell = false;
            }

            bestEdge = new AlternateAtomicReferenceArray<ChartEdge>(grammar.numNonTerms());
            //bestEdge = new ChartEdge[grammar.numNonTerms()];

            //inside = new float[grammar.numNonTerms()];
            //Arrays.fill(inside, Float.NEGATIVE_INFINITY);
        }

        @Override
        public float getInside(final int nt) {
            ChartEdge e = bestEdge.get(nt);
            return e == null ? Float.NEGATIVE_INFINITY : e.insideCachedValue;
            //return inside[nt];
        }

        public void updateInside(final int nt, final float insideProb) {
            throw new NotImplementedException();
            //            if (viterbiMax) {
//                if (insideProb > inside[nt]) {
//                    inside[nt] = insideProb;
//                    addToHashSets(nt);
//                }
//            } else {
//                inside[nt] = (float) Util.logSum(inside[nt], insideProb);
//                addToHashSets(nt);
//            }
        }

        // this will just not have an exact count since it isn't being locked/using atomics
        private void updateCounts(final Production p) {
            if (p.isBinaryProd()) {
                parseTask.nBinaryConsidered++;
            } else if (p.isLexProd()) {
                parseTask.nLex++;
            } else {
                parseTask.nUnaryConsidered++;
                if (this.width() == 1) {
                    parseTask.nLexUnary++;
                }
            }
        }

        @Override
        public void updateInside(final Chart.ChartEdge edge) {
            final int nt = edge.prod.parent;
            final float insideProb = edge.inside();
            boolean did_update = false;
            ChartEdge old_edge;
            ChartEdge new_edge = (ChartEdge)edge;
            new_edge.insideCachedValue = edge.inside();
            if (viterbiMax && insideProb > getInside(nt)) {
                while (true) {
                    old_edge = bestEdge.get(nt);
                    if (!(old_edge == null || old_edge.inside() < insideProb)) {
                        break;
                    }
                    if (bestEdge.weakCompareAndSet(nt, old_edge, new_edge)) {
                        did_update = true;
                        break;
                    }
                }
                //bestEdge[nt] = (ChartEdge) edge;
            }
            if(did_update)
                addToHashSets(nt);
            //updateInside(nt, insideProb);
            updateCounts(edge.prod);
        }

        // unary edges
        public void updateInside(final Production p, final float insideProb) {
            final int nt = p.parent;
            boolean did_update = false;
            ChartEdge old_edge, new_edge;
            if(viterbiMax && insideProb > getInside(nt)) {
                // there is a chance that we do the update
                new_edge = new ChartEdge(p, this);
                new_edge.insideCachedValue = new_edge.inside();
                while (true) {
                    old_edge = bestEdge.get(nt);
                    if (!(old_edge == null || old_edge.inside() < insideProb)) {
                        break;
                    }
                    if (bestEdge.weakCompareAndSet(nt, old_edge, new_edge)) {
                        did_update = true;
                        break;
                    }
                }
            }

//            if (viterbiMax && insideProb > getInside(nt)) {
//                throw new NotImplementedException();
////                bestEdge[nt] = new ChartEdge(p, this);
//            }
            if(did_update)
                addToHashSets(nt);
            //updateInside(nt, insideProb);
            updateCounts(p);
        }

        // binary edges
        @Override
        public void updateInside(final Production p, final ChartCell leftCell, final ChartCell rightCell,
                final float insideProb) {
            final int nt = p.parent;
            if (viterbiMax && insideProb > getInside(nt)) {
//                if (bestEdge[nt] == null) {
//                    bestEdge[nt] = new ChartEdge(p, leftCell, rightCell);
//                } else {
//                    bestEdge[nt].prod = p;
//                    bestEdge[nt].leftCell = leftCell;
//                    bestEdge[nt].rightCell = rightCell;
//                }
                throw new NotImplementedException();
            }
            updateInside(nt, insideProb);
            updateCounts(p);
        }

        @Override
        public ChartEdge getBestEdge(final int nt) {
            return bestEdge.get(nt);
        }

        public List<ChartEdge> getBestEdgeList() {
            final List<ChartEdge> bestEdges = new LinkedList<ChartEdge>();
            for (int i = 0; i < bestEdge.length(); i++) {
                ChartEdge e = bestEdge.get(i);
                if (e != null) {
                    bestEdges.add(e);
                }
            }
            return bestEdges;
        }

        public boolean hasNT(final int nt) {
            return bestEdge.get(nt) != null;
//            return inside[nt] > Float.NEGATIVE_INFINITY;
        }

        protected synchronized void addToHashSets(final int ntIndex) {
            childNTs.add(ntIndex);
            if (grammar.isLeftChild((short) ntIndex)) {
                leftChildNTs.add(ntIndex);
            }
            if (grammar.isRightChild((short) ntIndex)) {
                rightChildNTs.add(ntIndex);
            }
            if (grammar.isPos((short) ntIndex)) {
                posNTs.add(ntIndex);
            }
        }

        public synchronized HashSet<Integer> getNTs() {
            return (HashSet<Integer>)childNTs.clone();
        }

        // TODO: this is called a lot but it is creating a new array for each call!
        // the whole point was NOT to do this. We need to use getNTs() whereever we can.
        public int[] getNtArray() {
            final int[] array = new int[childNTs.size()];
            int i = 0;
            for (final int nt : childNTs) {
                array[i++] = nt;
            }
            return array;
        }

        public HashSet<Integer> getPosNTs() {
            return posNTs;
        }

        public HashSet<Integer> getLeftChildNTs() {
            return leftChildNTs;
        }

        public HashSet<Integer> getRightChildNTs() {
            return rightChildNTs;
        }

        @Override
        public int width() {
            return end() - start();
        }

        @Override
        public int getNumNTs() {
            return childNTs.size();
        }

        /**
         * This certainly isn't efficient, but it's not used in the main parsing loop
         * 
         * @return The number of unfactored non-terminals populated in this cell
         */
        @Override
        public int getNumUnfactoredNTs() {
            int count = 0;
            for (final int child : childNTs) {
                if (grammar.nonTermSet.isFactored((short) child)) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public boolean equals(final Object o) {
            return this == o;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder(1024);
            sb.append(getClass().getName() + "[" + start() + "][" + end() + "] with " + getNumNTs() + " (of "
                    + grammar.numNonTerms() + ") edges");
            for (int i = 0; i < bestEdge.length(); i++) {
                ChartEdge e = bestEdge.get(i);
                if (e != null) {
                    sb.append(e.toString());
                    sb.append('\n');
                }
            }
            return sb.toString();
        }

        public String toStringDetails() {
            final String result = this.toString() + "\n";
            return result;
        }

        @Override
        public int compareTo(final HashSetChartCell otherCell) {
            if (this.fom == otherCell.fom) {
                return 0;
            } else if (fom > otherCell.fom) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    public class ChartEdge extends Chart.ChartEdge implements Comparable<ChartEdge> {

        public float fom = 0; // figure of merit

        // binary production
        public ChartEdge(final Production prod, final ChartCell leftCell, final ChartCell rightCell) {
            super(prod, leftCell, rightCell);
            if (parser.figureOfMerit != null) {
                // this.fom = parser.fomModel.calcFOM(this);
                this.fom = parser.figureOfMerit.calcFOM(this.start(), this.end(), (short) this.prod.parent, this.inside());
            }
        }

        // unary production
        public ChartEdge(final Production prod, final HashSetChartCell childCell) {
            super(prod, childCell);

            if (parser.figureOfMerit != null) {
                if (prod.isLexProd()) {
                    this.fom = parser.figureOfMerit.calcLexicalFOM(this.start(), this.end(), (short) this.prod.parent,
                            this.inside());
                } else {
                    this.fom = parser.figureOfMerit.calcFOM(this.start(), this.end(), (short) this.prod.parent,
                            this.inside());
                }
            }
        }

        @Override
        public int compareTo(final ChartEdge otherEdge) {
            if (this.equals(otherEdge)) {
                return 0;
            } else if (fom > otherEdge.fom) {
                return -1;
            } else {
                return 1;
            }
        }

        @Override
        public String toString() {
            return super.toString() + String.format(" fom=%.3f", fom);
        }
    }
}
