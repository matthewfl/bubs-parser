package edu.ohsu.cslu.datastructs.vectors;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;

/**
 * A {@link Vector} implementation which stores 32-bit ints.
 * 
 * @author Aaron Dunlop
 * @since Dec 11, 2008
 * 
 * @version $Revision$ $Date$ $Author$
 */
public class IntVector extends BaseNumericVector {

    private final static long serialVersionUID = 379752896212698724L;

    private final int[] vector;

    public IntVector(final int length) {
        super(length);
        this.vector = new int[length];
    }

    public IntVector(final int length, final int defaultValue) {
        super(length);
        this.vector = new int[length];
        Arrays.fill(this.vector, defaultValue);
    }

    public IntVector(final int[] vector) {
        super(vector.length);
        this.vector = vector;
    }

    public final void fill(final int value) {
        Arrays.fill(vector, value);
    }

    @Override
    public final float getFloat(final int i) {
        return vector[i];
    }

    @Override
    public final int getInt(final int i) {
        return vector[i];
    }

    @Override
    public void set(final int i, final int value) {
        vector[i] = value;
    }

    @Override
    public void set(final int i, final float value) {
        set(i, Math.round(value));
    }

    @Override
    public void set(final int i, final boolean value) {
        set(i, value ? 1 : 0);
    }

    @Override
    public void set(final int i, final String newValue) {
        set(i, Float.parseFloat(newValue));
    }

    @Override
    public final float infinity() {
        return Integer.MAX_VALUE;
    }

    @Override
    public final float negativeInfinity() {
        return Integer.MIN_VALUE;
    }

    /**
     * Adds an addend to the elements of this vector indicated by the populated indices of a bit vector, returning a
     * reference to this vector
     * 
     * Caution: This method changes the contents of this vector
     * 
     * @param v a {@link BitVector} of the same length.
     * @param addend the float to be added to the elements indicated by populated elements in v
     * @return a reference to this vector.
     */
    public IntVector inPlaceAdd(final BitVector v, final int addend) {
        // Special-case for SparseBitVector
        if (v instanceof SparseBitVector) {
            if (v.length() > length) {
                throw new IllegalArgumentException("Vector length mismatch");
            }

            for (final int i : ((SparseBitVector) v).values()) {
                vector[i] += addend;
            }
            return this;
        }

        // Special-case for MutableSparseBitVector
        if (v instanceof MutableSparseBitVector) {
            if (v.length() > length) {
                throw new IllegalArgumentException("Vector length mismatch");
            }

            for (final int i : ((MutableSparseBitVector) v).intSet()) {
                vector[i] += addend;
            }
            return this;
        }

        if (v.length() != length) {
            throw new IllegalArgumentException("Vector length mismatch");
        }

        for (int i = 0; i < length; i++) {
            if (v.getBoolean(i)) {
                vector[i] += addend;
            }
        }
        return this;
    }

    @Override
    public Vector subVector(final int i0, final int i1) {
        final int[] newVector = new int[i1 - i0 + 1];
        System.arraycopy(vector, i0, newVector, 0, newVector.length);
        return new IntVector(newVector);
    }

    @Override
    public IntVector clone() {
        final int[] newVector = new int[length];
        System.arraycopy(vector, 0, newVector, 0, length);
        return new IntVector(newVector);
    }

    @Override
    public void write(final Writer writer) throws IOException {
        write(writer, String.format("vector type=int length=%d\n", length));
    }
}
