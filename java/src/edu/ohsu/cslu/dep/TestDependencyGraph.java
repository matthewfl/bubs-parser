package edu.ohsu.cslu.dep;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import cltool4j.BaseCommandlineTool;
import edu.ohsu.cslu.datastructs.narytree.NaryTree;
import edu.ohsu.cslu.dep.DependencyGraph.DerivationAction;

public class TestDependencyGraph extends BaseCommandlineTool {

    private DependencyGraph conllExample;

    @Before
    public void setUp() throws IOException {
        final StringBuilder sb = new StringBuilder();
        sb.append("1	The	_	DT	DT	_	4	NMOD	_	_\n");
        sb.append("2	luxury	_	NN	NN	_	4	NMOD	_	_\n");
        sb.append("3	auto	_	NN	NN	_	4	NMOD	_	_\n");
        sb.append("4	maker	_	NN	NN	_	7	SBJ	_	_\n");
        sb.append("5	last	_	JJ	JJ	_	6	NMOD	_	_\n");
        sb.append("6	year	_	NN	NN	_	7	VMOD	_	_\n");
        sb.append("7	sold	_	VB	VBD	_	0	ROOT	_	_\n");
        sb.append("8	1,214	_	CD	CD	_	9	NMOD	_	_\n");
        sb.append("9	cars	_	NN	NNS	_	7	OBJ	_	_\n");
        sb.append("10	in	_	IN	IN	_	7	ADV	_	_\n");
        sb.append("11	the	_	DT	DT	_	12	NMOD	_	_\n");
        sb.append("12	U.S.	_	NN	NNP	_	10	PMOD	_	_\n");

        conllExample = DependencyGraph.readConll(sb.toString());
    }

    @Test
    public void testDerivation() throws IOException {

        final StringBuilder sb = new StringBuilder();
        sb.append("1	The	_	DT	DT	_	3	NMOD	_	_\n");
        sb.append("2	aged	_	NN	NN	_	3	NMOD	_	_\n");
        sb.append("3	bottle	_	NN	NN	_	4	NMOD	_	_\n");
        sb.append("4	flies	_	NN	NN	_	0	SBJ	_	_\n");
        sb.append("5	fast	_	NN	NN	_	4	SBJ	_	_\n");

        final DependencyGraph g = DependencyGraph.readConll(sb.toString());
        DependencyGraph.DerivationAction[] derivation = g.derivation();
        assertArrayEquals(new DerivationAction[] { DerivationAction.SHIFT, DerivationAction.SHIFT,
                DerivationAction.SHIFT, DerivationAction.REDUCE_RIGHT, DerivationAction.REDUCE_RIGHT,
                DerivationAction.SHIFT, DerivationAction.REDUCE_RIGHT, DerivationAction.SHIFT,
                DerivationAction.REDUCE_LEFT, DerivationAction.SHIFT, DerivationAction.REDUCE_RIGHT }, derivation);

        derivation = conllExample.derivation();
        assertArrayEquals(new DerivationAction[] { DerivationAction.SHIFT, DerivationAction.SHIFT,
                DerivationAction.SHIFT, DerivationAction.SHIFT, DerivationAction.REDUCE_RIGHT,
                DerivationAction.REDUCE_RIGHT, DerivationAction.REDUCE_RIGHT, DerivationAction.SHIFT,
                DerivationAction.SHIFT, DerivationAction.REDUCE_RIGHT, DerivationAction.SHIFT,
                DerivationAction.REDUCE_RIGHT, DerivationAction.REDUCE_RIGHT, DerivationAction.SHIFT,
                DerivationAction.SHIFT, DerivationAction.REDUCE_RIGHT, DerivationAction.REDUCE_LEFT,
                DerivationAction.SHIFT, DerivationAction.SHIFT, DerivationAction.SHIFT, DerivationAction.REDUCE_RIGHT,
                DerivationAction.REDUCE_LEFT, DerivationAction.REDUCE_LEFT, DerivationAction.SHIFT,
                DerivationAction.REDUCE_RIGHT }, derivation);
    }

    @Test
    public void testTokenTree() {
        final NaryTree<String> tree = conllExample.tokenTree();
        assertEquals("(ROOT (sold (maker The luxury auto) (year last) (cars 1,214) (in (U.S. the))))", tree.toString());
    }

    @Test
    public void testSubtreeScore() {
        for (int i = 0; i < conllExample.size(); i++) {
            conllExample.arcs[i].score = 0.5f;
        }
        final NaryTree<DependencyNode> root = conllExample.tree();
        final float subtreeScore = root.label().subtreeScore(root.children());
        assertEquals(Math.log(1.0 / 4096), subtreeScore, 0.001);
    }

    @Override
    protected void run() throws Exception {

        int errors = 0, sentences = 0;
        final BufferedReader br = inputAsBufferedReader();
        for (DependencyGraph deps = DependencyGraph.readConll(br); deps != null; deps = DependencyGraph.readConll(br)) {
            try {
                deps.printDerivation();
            } catch (final Exception e) {
                System.err.println("Error on sentence of " + deps.size() + " words");
                // e.printStackTrace();
                errors++;
            }
            sentences++;
            System.out.println();
        }
        System.err.println("Total sentences: " + sentences + " Errors: " + errors);
    }

    public static void main(final String[] args) {
        run(args);
    }
}