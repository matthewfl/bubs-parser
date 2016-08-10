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
package edu.ohsu.cslu.parser.agenda;

import cltool4j.BaseLogger;
import cltool4j.GlobalConfigProperties;
import edu.ohsu.cslu.datastructs.narytree.BinaryTree;
import edu.ohsu.cslu.grammar.LeftRightListsGrammar;
import edu.ohsu.cslu.grammar.Production;
import edu.ohsu.cslu.parser.ParseTask;
import edu.ohsu.cslu.parser.Parser;
import edu.ohsu.cslu.parser.ParserDriver;
import edu.ohsu.cslu.parser.chart.CellChart;
import edu.ohsu.cslu.parser.chart.CellChart.ChartEdge;
import edu.ohsu.cslu.parser.chart.CellChart.HashSetChartCell;
import edu.rice.hj.api.HjSuspendable;
import edu.rice.hj.api.SuspendableException;

import java.util.Iterator;
import java.util.concurrent.PriorityBlockingQueue;

import static edu.rice.hj.Module0.finish;
import static edu.rice.hj.Module1.async;


/**
 * @author Nathan Bodenstab
 */
public class AgendaParser extends Parser<LeftRightListsGrammar> {

//    protected PriorityQueue<ChartEdge> agenda;
    protected PriorityBlockingQueue<ChartEdge> agenda;
    public CellChart chart;
    protected int nAgendaPush, nAgendaPop, nChartEdges;
    float overParseTune = GlobalConfigProperties.singleton().getFloatProperty("overParseTune");

    public AgendaParser(final ParserDriver opts, final LeftRightListsGrammar grammar) {
        super(opts, grammar);
    }

    protected void initParser(final ParseTask parseTask) {
        chart = new CellChart(parseTask, this);

//        agenda = new PriorityQueue<ChartEdge>();
        agenda = new PriorityBlockingQueue<>();
        nAgendaPush = nAgendaPop = nChartEdges = 0;
    }

    @Override
    public BinaryTree<String> findBestParse(final ParseTask parseTask) {
//        ChartEdge edge;
//        HashSetChartCell cell;

        initParser(parseTask);
        addLexicalProductions(parseTask.tokens);
        figureOfMerit.initSentence(parseTask, chart);

        for (int i = 0; i < parseTask.sentenceLength(); i++) {
            HashSetChartCell cell = chart.getCell(i, i + 1);
            for (final int nt : cell.getPosNTs()) {
                expandFrontier(nt, i, i + 1);
            }
        }

        class Objp {
            boolean doneParsing = false;
            int targetNumPops = -1;
        }

        Objp objp = new Objp();

//        boolean doneParsing = false;
//        int targetNumPops = -1;
//        while (!agenda.isEmpty() && !doneParsing) {
//            edge = agenda.poll(); // get and remove top agenda edge
//            nAgendaPop += 1;
//            if (collectDetailedStatistics) {
//                BaseLogger.singleton().finer("Popping: " + edge.toString());
//            }
//
//            cell = chart.getCell(edge.start(), edge.end());
//            final int nt = edge.prod.parent;
//            if (edge.inside() > cell.getInside(nt)) {
//
//                cell.updateInside(edge);
//                // if A->B C is added to chart but A->X Y was already in this chart cell, then the
//                // first edge must have been better than the current edge because we pull edges
//                // from the agenda best-first. This also means that the entire frontier
//                // has already been added.
//                expandFrontier(nt, edge.start(), edge.end());
//                nChartEdges += 1;
//            }
//
//            if (chart.hasCompleteParse(grammar.startSymbol)) {
//                if (targetNumPops < 0) {
//                    targetNumPops = (int) (nAgendaPop * overParseTune);
//                }
//                if (nAgendaPop >= targetNumPops) {
//                    doneParsing = true;
//                }
//            }
//        }

        // iterator which will pull elements out of the queue in order
        Iterator<ChartEdge> agendaIterator = new Iterator<ChartEdge>() {
            @Override
            public boolean hasNext() {
                return !objp.doneParsing;
            }

            @Override
            public ChartEdge next() {
                // this blocks until there is something in the queue to work on
                try {
                    return agenda.take();
                } catch(InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        Iterable<ChartEdge> agendaIterable = new Iterable<ChartEdge>() {
            @Override
            public Iterator<ChartEdge> iterator() {
                return agendaIterator;
            }
        };

        try {
            finish(() -> {
                //forall(agendaIterable, (ChartEdge edge) -> {
//                while(true) {
                    while (agendaIterator.hasNext()) {
                        // this is a "hack" to make it sorta support the order of the queue by limiting the number of tasks
                        // that it is going to end up creating
                        finish(() -> {
                            for(int i = 0; i < 100 && agendaIterator.hasNext(); i++) {
                                async(new HjSuspendable() {
                                    @Override
                                    public void run() throws SuspendableException {
                                        nAgendaPop += 1;
                                        if (collectDetailedStatistics) {
                                            BaseLogger.singleton().finer("Popping: " + edge.toString());
                                        }
                                        HashSetChartCell cell = chart.getCell(edge.start(), edge.end());
                                        final int nt = edge.prod.parent;
                                        if (edge.inside() > cell.getInside(nt)) {
                                            cell.updateInside(edge);
                                            expandFrontier(nt, edge.start(), edge.end());
                                            nChartEdges += 1;
                                        }

                                        if (objp.targetNumPops < 0 && chart.hasCompleteParse(grammar.startSymbol)) {
                                            objp.targetNumPops = (int) (nAgendaPop * overParseTune);
                                        }
                                        if (objp.targetNumPops > 0 && nAgendaPop >= objp.targetNumPops)
                                            objp.doneParsing = true;
                                    }

                                    private ChartEdge edge = agendaIterator.next();
                                });

                            }
                        });
//                    }
//                    //});
                }
            });
        } catch(SuspendableException e) {
            throw new RuntimeException(e);
        }

        if (agenda.isEmpty()) {
            BaseLogger.singleton().info("WARNING: Agenda is empty.  All edges have been added to chart.");
        }

        return chart.extractBestParse(grammar.startSymbol);
    }

    protected void addEdgeToFrontier(final ChartEdge edge) {
        nAgendaPush += 1;
        agenda.add(edge);
    }

    protected void addEdgeToFrontier(final Production p, final int start, final int mid, final int end) {
        ChartEdge edge;
        nAgendaPush += 1;
        if (mid < 0) {
            edge = chart.new ChartEdge(p, chart.getCell(start, end));
        } else {
            edge = chart.new ChartEdge(p, chart.getCell(start, mid), chart.getCell(mid, end));
        }
        agenda.add(edge);

        if (collectDetailedStatistics) {
            BaseLogger.singleton().finer("Pushing: " + edge.toString());
        }
    }

    protected void addLexicalProductions(final int sent[]) {
        HashSetChartCell cell;

        // add lexical productions and unary productions to the base cells of the chart
        for (int i = 0; i < chart.size(); i++) {
            cell = chart.getCell(i, i + 1);
            for (final Production lexProd : grammar.getLexicalProductionsWithChild(sent[i])) {
                // Add lexical prods directly to the chart instead of to the agenda because
                // the boundary FOM (and possibly others use the surrounding POS tags to calculate
                // the fit of a new edge. If the POS tags don't exist yet (are still in the agenda)
                // it skew probs (to -Infinity) and never allow some edges that should be allowed
                cell.updateInside(lexProd, lexProd.prob);

            }
        }
    }

    protected void expandFrontier(final int nt, final int start, final int end) {
        HashSetChartCell rightCell, leftCell;
        int mid;

        // unary edges are always possible in any cell. Unary chains can exist. They will just compete on the agenda
        for (final Production p : grammar.getUnaryProductionsWithChild(nt)) {
            addEdgeToFrontier(p, start, -1, end);
        }

        // connect edge as possible right non-term
        mid = start;
        for (int newStart = 0; newStart < mid; newStart++) {
            leftCell = chart.getCell(newStart, mid);
            for (final Production p : grammar.getBinaryProductionsWithRightChild(nt)) {
                // NB: For some reason getInside() and getBestEdge() return different values. There
                // should really only be one because they are somehow getting out of sync.
                // if (leftCell.getInside(p.leftChild) > Float.NEGATIVE_INFINITY) {
                if (leftCell.getBestEdge(p.leftChild) != null) {
                    addEdgeToFrontier(p, newStart, mid, end);
                }
            }
        }

        // connect edge as possible left non-term
        mid = end;
        for (int newEnd = mid + 1; newEnd <= chart.size(); newEnd++) {
            rightCell = chart.getCell(mid, newEnd);
            for (final Production p : grammar.getBinaryProductionsWithLeftChild(nt)) {
                // if (rightCell.getInside(p.rightChild) > Float.NEGATIVE_INFINITY) {
                if (rightCell.getBestEdge(p.rightChild) != null) {
                    addEdgeToFrontier(p, start, mid, newEnd);
                }
            }
        }
    }

    @Override
    public String getStats() {
        return " chartEdges=" + nChartEdges + " agendaPush=" + nAgendaPush + " agendaPop=" + nAgendaPop;
    }

    @Override
    public float getInside(final int start, final int end, final int nt) {
        return chart.getInside(start, end, nt);
    }
}
