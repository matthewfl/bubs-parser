package edu.ohsu.cslu.datastructs.narytree;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Iterator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import edu.ohsu.cslu.parser.ParserOptions.GrammarFormatType;
import edu.ohsu.cslu.tests.FilteredRunner;

import static junit.framework.Assert.*;

import static org.junit.Assert.assertArrayEquals;

/**
 * Unit tests for {@link BaseNaryTree} using Strings
 * 
 * @author Aaron Dunlop
 * @since Sep 29, 2008
 * 
 *        $Id$
 */
@RunWith(FilteredRunner.class)
public class TestStringNaryTree {

    private BaseNaryTree<String> sampleTree;
    private String stringSampleTree;

    private final static String[] SAMPLE_IN_ORDER_ARRAY = new String[] { "a", "b", "c", "d", "e", "f", "g",
            "h", "i", "j", "k" };
    private final static String[] SAMPLE_PRE_ORDER_ARRAY = new String[] { "f", "d", "b", "a", "c", "e", "g",
            "i", "h", "k", "j" };
    private final static String[] SAMPLE_POST_ORDER_ARRAY = new String[] { "a", "c", "b", "e", "d", "g", "h",
            "j", "k", "i", "f" };

    @Before
    public void setUp() {
        sampleTree = new BaseNaryTree<String>("f");

        BaseNaryTree<String> tmp1 = new BaseNaryTree<String>("b");
        tmp1.addChild("a");
        tmp1.addChild("c");

        BaseNaryTree<String> tmp2 = new BaseNaryTree<String>("d");
        tmp2.addSubtree(tmp1);
        tmp2.addChild("e");

        sampleTree.addSubtree(tmp2);
        sampleTree.addChild("g");

        tmp1 = new BaseNaryTree<String>("i");
        tmp1.addChild("h");
        tmp2 = tmp1.addChild("k");
        tmp2.addChild("j");
        sampleTree.addSubtree(tmp1);

        /**
         * <pre>
         *             f 
         *             |
         *       --------------
         *       |     |     |
         *       d     g     i
         *       |           |
         *    -------     --------
         *    |     |     |      |
         *    b     e     h      k
         *    |                  |
         *  -----                j
         *  |   |
         *  a   c
         * </pre>
         */
        stringSampleTree = "(f (d (b a c) e) g (i h (k j)))";
    }

    @Test
    public void testAddChild() throws Exception {
        final BaseNaryTree<String> tree = new BaseNaryTree<String>("a");
        assertEquals(1, tree.size());
        tree.addChild("b");
        assertEquals(2, tree.size());
        assertNull(tree.subtree("a"));
        assertNotNull(tree.subtree("b"));
    }

    @Test
    public void testAddChildren() throws Exception {
        final BaseNaryTree<String> tree = new BaseNaryTree<String>("a");
        assertEquals(1, tree.size());
        tree.addChildren(new String[] { "b", "c" });
        assertEquals(3, tree.size());
        assertNull(tree.subtree("a"));
        assertNotNull(tree.subtree("b"));
        assertNotNull(tree.subtree("c"));
    }

    @Test
    public void testAddSubtree() throws Exception {
        final BaseNaryTree<String> tree = new BaseNaryTree<String>("a");
        final BaseNaryTree<String> tmp = new BaseNaryTree<String>("b");
        tmp.addChildren(new String[] { "c", "d" });
        tree.addSubtree(tmp);
        assertEquals(4, tree.size());
        assertNotNull(tree.subtree("b"));
        assertNotNull(tree.subtree("b").subtree("c"));
        assertNotNull(tree.subtree("b").subtree("d"));
    }

    @Test
    public void testRemoveChild() throws Exception {
        assertEquals(11, sampleTree.size());

        sampleTree.removeChild("g");
        assertEquals(10, sampleTree.size());

        // Removing the "d" node should move its children up
        assertNull(sampleTree.subtree("b"));
        assertNull(sampleTree.subtree("e"));

        sampleTree.removeChild("d");
        assertEquals(9, sampleTree.size());
        assertNotNull(sampleTree.subtree("b"));
        assertNotNull(sampleTree.subtree("e"));

        // TODO: Validate that the children were inserted at the proper place with iteration order
    }

    @Test
    public void testRemoveChildrenByStringArray() throws Exception {
        assertEquals(11, sampleTree.size());

        assertNull(sampleTree.subtree("h"));
        assertNull(sampleTree.subtree("j"));

        // Removing the "i" node should move its children up ("g" has no children)
        sampleTree.removeChildren(new String[] { "g", "i" });
        assertEquals(9, sampleTree.size());

        assertNotNull(sampleTree.subtree("h"));
        assertNotNull(sampleTree.subtree("k"));
    }

    @Test
    public void testRemoveSubtree() throws Exception {
        assertEquals(11, sampleTree.size());

        sampleTree.removeSubtree("g");
        assertEquals(10, sampleTree.size());

        // Removing the "4" node should remove all its children as well
        sampleTree.removeSubtree("d");
        assertEquals(5, sampleTree.size());
        assertNull(sampleTree.subtree("b"));
        assertNull(sampleTree.subtree("e"));
    }

    @Test
    public void testSubtree() throws Exception {
        final BaseNaryTree<String> subtree = sampleTree.subtree("d");
        assertEquals(5, subtree.size());
        assertNotNull(subtree.subtree("b"));
        assertNotNull(subtree.subtree("e"));
        assertEquals(3, subtree.subtree("b").size());

        assertEquals(4, sampleTree.subtree("i").size());
    }

    @Test
    public void testSize() throws Exception {
        assertEquals(11, sampleTree.size());
        sampleTree.subtree("i").removeSubtree("k");
        assertEquals(9, sampleTree.size());
        sampleTree.subtree("g").addChild("z");
        assertEquals(10, sampleTree.size());
    }

    @Test
    public void testDepthFromRoot() throws Exception {
        assertEquals(0, sampleTree.depthFromRoot());
        assertEquals(1, sampleTree.subtree("d").depthFromRoot());
        assertEquals(2, sampleTree.subtree("i").subtree("k").depthFromRoot());
        assertEquals(3, sampleTree.subtree("i").subtree("k").subtree("j").depthFromRoot());
    }

    @Test
    public void testChildLabels() throws Exception {
        assertArrayEquals(new String[] { "d", "g", "i" }, sampleTree.childLabels().toArray(new String[0]));
    }

    @Test
    public void testLeaves() throws Exception {
        assertEquals(6, sampleTree.leaves());
        assertEquals(3, sampleTree.subtree("d").leaves());

        sampleTree.subtree("g").addChild("g2");
        assertEquals(6, sampleTree.leaves());

        sampleTree.subtree("g").removeChild("g3");
        assertEquals(6, sampleTree.leaves());

        sampleTree.subtree("i").removeSubtree("k");
        assertEquals(5, sampleTree.leaves());

        sampleTree.removeSubtree("d");
        assertEquals(2, sampleTree.leaves());

        sampleTree.removeChild("g");
        assertEquals(2, sampleTree.leaves());

        sampleTree.removeChild("g2");
        assertEquals(1, sampleTree.leaves());
    }

    @Test
    public void testInOrderIterator() throws Exception {
        final Iterator<NaryTree<String>> iter = sampleTree.inOrderIterator();
        for (int i = 0; i < sampleTree.size(); i++) {
            assertEquals(SAMPLE_IN_ORDER_ARRAY[i], iter.next().label().toString());
        }
    }

    @Test
    public void testInOrderLabelIterator() throws Exception {
        final Iterator<String> iter = sampleTree.inOrderLabelIterator();
        for (int i = 0; i < sampleTree.size(); i++) {
            assertEquals(SAMPLE_IN_ORDER_ARRAY[i], iter.next());
        }
    }

    @Test
    public void testPreOrderIterator() throws Exception {
        final Iterator<? extends NaryTree<String>> iter = sampleTree.preOrderIterator();
        for (int i = 0; i < sampleTree.size(); i++) {
            assertEquals(SAMPLE_PRE_ORDER_ARRAY[i], iter.next().label().toString());
        }
    }

    @Test
    public void testPreOrderLabelIterator() throws Exception {
        final Iterator<String> iter = sampleTree.preOrderLabelIterator();
        for (int i = 0; i < sampleTree.size(); i++) {
            assertEquals(SAMPLE_PRE_ORDER_ARRAY[i], iter.next());
        }
    }

    @Test
    public void testPostOrderIterator() throws Exception {
        final Iterator<? extends NaryTree<String>> iter = sampleTree.postOrderIterator();
        for (int i = 0; i < sampleTree.size(); i++) {
            assertEquals(SAMPLE_POST_ORDER_ARRAY[i], iter.next().label().toString());
        }
    }

    @Test
    public void testPostOrderLabelIterator() throws Exception {
        final Iterator<String> iter = sampleTree.postOrderLabelIterator();
        for (int i = 0; i < sampleTree.size(); i++) {
            assertEquals(SAMPLE_POST_ORDER_ARRAY[i], iter.next());
        }
    }

    @Test
    public void testReadFromReader() throws Exception {

        final String stringSimpleTree = "(a (b c) d)";
        final BaseNaryTree<String> simpleTree = BaseNaryTree.read(new StringReader(stringSimpleTree),
            String.class);
        assertEquals(4, simpleTree.size());
        assertEquals(2, simpleTree.subtree("b").size());

        assertEquals("a", simpleTree.label());
        assertEquals("b", simpleTree.subtree("b").label());
        assertEquals("c", simpleTree.subtree("b").subtree("c").label());
        assertEquals("d", simpleTree.subtree("d").label());

        final String stringTestTree = "(a (b (c (d (e f) (g h)) (i j)) (k l)))";
        final BaseNaryTree<String> testTree = BaseNaryTree.read(new StringReader(stringTestTree),
            String.class);
        assertEquals(12, testTree.size());
        assertEquals(8, testTree.subtree("b").subtree("c").size());

        assertEquals("a", testTree.label());
        assertEquals("b", testTree.subtree("b").label());
        assertEquals("c", testTree.subtree("b").subtree("c").label());
        assertEquals("d", testTree.subtree("b").subtree("c").subtree("d").label());
        assertEquals("e", testTree.subtree("b").subtree("c").subtree("d").subtree("e").label());
        assertEquals("f", testTree.subtree("b").subtree("c").subtree("d").subtree("e").subtree("f").label());
        assertEquals("g", testTree.subtree("b").subtree("c").subtree("d").subtree("g").label());
        assertEquals("h", testTree.subtree("b").subtree("c").subtree("d").subtree("g").subtree("h").label());
        assertEquals("i", testTree.subtree("b").subtree("c").subtree("i").label());
        assertEquals("j", testTree.subtree("b").subtree("c").subtree("i").subtree("j").label());
        assertEquals("k", testTree.subtree("b").subtree("k").label());
        assertEquals("l", testTree.subtree("b").subtree("k").subtree("l").label());

        final BaseNaryTree<String> tree = BaseNaryTree.read(new StringReader(stringSampleTree), String.class);
        assertEquals(sampleTree, tree);
    }

    @Test
    public void testWriteToWriter() throws Exception {
        StringWriter writer = new StringWriter();
        sampleTree.write(writer);
        assertEquals(stringSampleTree, writer.toString());

        final String stringSimpleTree = "(a (b (c (d (e f) (g h)) (i j)) (k l)))";
        final BaseNaryTree<String> tree = new BaseNaryTree<String>("a");
        tree.addChild("b").addChild("c").addChild("d").addChild("e").addChild("f");
        tree.subtree("b").subtree("c").subtree("d").addChild("g").addChild("h");
        tree.subtree("b").subtree("c").addChild("i").addChild("j");
        tree.subtree("b").addChild("k").addChild("l");

        writer = new StringWriter();
        tree.write(writer);
        assertEquals(stringSimpleTree, writer.toString());
    }

    @Test
    public void testIsLeaf() throws Exception {
        assertFalse(sampleTree.isLeaf());
        assertFalse(sampleTree.subtree("d").isLeaf());
        assertTrue(sampleTree.subtree("g").isLeaf());
        assertTrue(sampleTree.subtree("i").subtree("h").isLeaf());
    }

    @Test
    public void testEquals() throws Exception {
        final BaseNaryTree<String> tree1 = new BaseNaryTree<String>("a");
        tree1.addChildren(new String[] { "b", "c" });

        final BaseNaryTree<String> tree2 = new BaseNaryTree<String>("a");
        tree2.addChildren(new String[] { "b", "c" });

        final BaseNaryTree<String> tree3 = new BaseNaryTree<String>("a");
        tree3.addChildren(new String[] { "b", "d" });

        assertTrue(tree1.equals(tree2));
        assertFalse(tree1.equals(tree3));

        tree2.subtree("c").addChild("d");
        assertFalse(tree1.equals(tree2));
    }

    /**
     * Tests Java serialization and deserialization of trees
     * 
     * @throws Exception if something bad happens
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testSerialize() throws Exception {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final ObjectOutputStream oos = new ObjectOutputStream(bos);

        oos.writeObject(sampleTree);

        final ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
        final BaseNaryTree<String> t = (BaseNaryTree<String>) ois.readObject();
        assertTrue(sampleTree.equals(t));
    }

    @Test
    public void testLeftFactor() {
        assertEquals(BinaryTree.read("(S (NP C0 C1))", String.class),
            BaseNaryTree.read("(S (NP C0 C1))", String.class).leftFactor(GrammarFormatType.Berkeley));

        assertEquals(BinaryTree.read("(S (NP C0 (@NP C1 C2)))", String.class),
            BaseNaryTree.read("(S (NP C0 C1 C2))", String.class).leftFactor(GrammarFormatType.Berkeley));
    }

    @Test
    public void testRightFactor() {
        assertEquals(BinaryTree.read("(S (NP C0 C1))", String.class),
            BaseNaryTree.read("(S (NP C0 C1))", String.class).rightFactor(GrammarFormatType.Berkeley));

        assertEquals(BinaryTree.read("(S (NP (@NP C0 C1) C2))", String.class),
            BaseNaryTree.read("(S (NP C0 C1 C2))", String.class).rightFactor(GrammarFormatType.Berkeley));
    }
}
