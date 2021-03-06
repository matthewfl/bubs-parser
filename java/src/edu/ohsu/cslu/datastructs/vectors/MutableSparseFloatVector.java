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
package edu.ohsu.cslu.datastructs.vectors;

import it.unimi.dsi.fastutil.longs.Long2FloatMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongRBTreeSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.io.IOException;
import java.io.Writer;

import edu.ohsu.cslu.datastructs.Semiring;

/**
 * Sparse float vector which stores entries in a hash.
 * 
 * @author Aaron Dunlop
 * @since Dec 16, 2009
 * 
 * @version $Revision$ $Date$ $Author$
 */
public final class MutableSparseFloatVector extends BaseNumericVector implements FloatVector, LargeVector, SparseVector {

    private static final long serialVersionUID = 1L;

    private Long2FloatOpenHashMap map = new Long2FloatOpenHashMap();

    /**
     * Initializes all elements to a default value.
     * 
     * @param length The size of the vector
     * @param defaultValue The value assigned to all elements
     */
    public MutableSparseFloatVector(final long length, final float defaultValue) {
        super(length);
        map.defaultReturnValue(defaultValue);
    }

    /**
     * Initializes all elements to 0.
     * 
     * @param length The size of the vector
     */
    public MutableSparseFloatVector(final long length) {
        this(length, 0);
    }

    /**
     * Initializes a {@link MutableSparseFloatVector} from a floating-point array.
     * 
     * @param vector The populated vector elements
     */
    public MutableSparseFloatVector(final float[] vector) {
        this(vector.length);
        for (int i = 0; i < vector.length; i++) {
            set(i, vector[i]);
        }
    }

    /**
     * Copies an {@link Long2FloatMap} into a new {@link MutableSparseFloatVector}.
     * 
     * @param length
     * @param map
     */
    MutableSparseFloatVector(final long length, final Long2FloatMap map) {
        this(length, map.defaultReturnValue());
        for (final long key : map.keySet()) {
            this.map.put((int) key, map.get(key));
        }
    }

    @Override
    public float getFloat(final int i) {
        return map.get(i);
    }

    @Override
    public float getFloat(final long i) {
        return map.get(i);
    }

    @Override
    public int getInt(final int i) {
        return Math.round(map.get(i));
    }

    public int getInt(final long i) {
        return Math.round(map.get(i));
    }

    @Override
    public float infinity() {
        return Float.POSITIVE_INFINITY;
    }

    @Override
    public float negativeInfinity() {
        return Float.NEGATIVE_INFINITY;
    }

    @Override
    public void set(final int i, final int value) {
        map.put(i, value);
    }

    @Override
    public void set(final long i, final int value) {
        map.put((int) i, value);
    }

    @Override
    public void set(final int i, final float value) {
        map.put(i, value);
    }

    @Override
    public void set(final long i, final float value) {
        map.put((int) i, value);
    }

    @Override
    public void set(final int i, final boolean value) {
        map.put(i, value ? 1 : 0);
    }

    @Override
    public void set(final long i, final boolean value) {
        map.put((int) i, value ? 1 : 0);
    }

    @Override
    public void set(final int i, final String newValue) {
        map.put(i, Float.parseFloat(newValue));
    }

    @Override
    public void set(final long i, final String newValue) {
        map.put((int) i, Float.parseFloat(newValue));
    }

    @Override
    public float max() {
        float max = Float.NEGATIVE_INFINITY;
        for (final float x : map.values()) {
            if (x > max) {
                max = x;
            }
        }
        return max;
    }

    @Override
    public float min() {
        float min = Float.POSITIVE_INFINITY;
        for (final float x : map.values()) {
            if (x < min) {
                min = x;
            }
        }
        return min;
    }

    @Override
    public LongSet populatedDimensions() {
        final LongSet d = new LongRBTreeSet();
        for (final long i : map.keySet()) {
            if (map.get(i) != 0) {
                d.add(i);
            }
        }
        return d;
    }

    /** Type-strengthen return-type */
    @Override
    public MutableSparseFloatVector elementwiseDivide(final NumericVector v) {

        if (v.length() != length) {
            throw new IllegalArgumentException("Vector length mismatch");
        }

        final MutableSparseFloatVector newVector = new MutableSparseFloatVector(length);
        if (v instanceof LargeVector) {
            final LargeVector lv = (LargeVector) v;
            for (final long i : map.keySet()) {
                newVector.set(i, getFloat(i) / lv.getFloat(i));
            }

        } else {
            for (final long i : map.keySet()) {
                newVector.set(i, getFloat(i) / v.getFloat((int) i));
            }
        }

        return newVector;
    }

    /** Type-strengthen return-type */
    @Override
    public MutableSparseFloatVector elementwiseLog() {
        final MutableSparseFloatVector newVector = new MutableSparseFloatVector(length);
        for (final long i : map.keySet()) {
            newVector.set(i, (float) Math.log(getFloat(i)));
        }

        return newVector;
    }

    @Override
    public MutableSparseFloatVector inPlaceAdd(final Vector v) {
        // Special-case for SparseBitVector
        if (v instanceof SparseBitVector) {
            if (v.length() > length) {
                throw new IllegalArgumentException("Vector length mismatch");
            }

            for (final int i : ((SparseBitVector) v).values()) {
                set(i, getFloat(i) + 1);
            }
            return this;
        }

        // Special-case for MutableSparseBitVector
        if (v instanceof MutableSparseBitVector) {
            if (v.length() > length) {
                throw new IllegalArgumentException("Vector length mismatch");
            }

            for (final int i : ((MutableSparseBitVector) v).valueIterator()) {
                set(i, getFloat(i) + 1);
            }
            return this;
        }

        if (v.length() != length) {
            throw new IllegalArgumentException("Vector length mismatch");
        }

        for (int i = 0; i < length; i++) {
            set(i, getFloat(i) + v.getFloat(i));
        }
        return this;
    }

    @Override
    public MutableSparseFloatVector inPlaceAdd(final BitVector v, final float addend) {
        // Special-case for SparseBitVector
        if (v instanceof SparseBitVector) {
            if (v.length() > length) {
                throw new IllegalArgumentException("Vector length mismatch");
            }

            for (final int i : ((SparseBitVector) v).values()) {
                set(i, getFloat(i) + addend);
            }
            return this;
        }

        // Special-case for MutableSparseBitVector
        if (v instanceof MutableSparseBitVector) {
            if (v.length() > length) {
                throw new IllegalArgumentException("Vector length mismatch");
            }

            for (final int i : ((MutableSparseBitVector) v).valueIterator()) {
                set(i, getFloat(i) + addend);
            }
            return this;
        }

        if (v.length() != length) {
            throw new IllegalArgumentException("Vector length mismatch");
        }

        for (int i = 0; i < length; i++) {
            if (v.getBoolean(i)) {
                set(i, getFloat(i) + addend);
            }
        }
        return this;
    }

    @Override
    public MutableSparseFloatVector inPlaceElementwiseMultiply(final Vector v) {
        if (v.length() != length && !(v instanceof SparseBitVector && v.length() <= length)) {
            throw new IllegalArgumentException("Vector length mismatch");
        }

        // TODO: This could be more efficient for SparseBitVectors

        for (int i = 0; i < length; i++) {
            set(i, getFloat(i) * v.getFloat(i));
        }
        return this;
    }

    @Override
    public MutableSparseFloatVector inPlaceElementwiseDivide(final Vector v) {
        if (v.length() != length) {
            throw new IllegalArgumentException("Vector length mismatch");
        }

        for (int i = 0; i < length; i++) {
            set(i, getFloat(i) / v.getFloat(i));
        }
        return this;
    }

    @Override
    public MutableSparseFloatVector inPlaceScalarAdd(final float addend) {
        for (int i = 0; i < length; i++) {
            set(i, getFloat(i) + addend);
        }
        return this;
    }

    @Override
    public MutableSparseFloatVector inPlaceScalarMultiply(final float multiplicand) {
        for (int i = 0; i < length; i++) {
            set(i, getFloat(i) * multiplicand);
        }
        return this;
    }

    @Override
    public MutableSparseFloatVector inPlaceElementwiseLog() {
        for (int i = 0; i < length; i++) {
            set(i, (float) Math.log(getFloat(i)));
        }
        return this;
    }

    @Override
    public Vector subVector(final int i0, final int i1) {
        final Long2FloatOpenHashMap newMap = new Long2FloatOpenHashMap();

        for (int i = i0; i <= i1; i++) {
            if (map.containsKey(i)) {
                newMap.put(i - i0, map.get(i));
            }
        }

        return new MutableSparseFloatVector(i1 - i0 + 1, newMap);
    }

    public MutableSparseFloatVector union(final MutableSparseFloatVector other, final Semiring semiring) {
        if (semiring != Semiring.TROPICAL) {
            throw new UnsupportedOperationException("Only the tropical semiring is supported");
        }

        if (other.map.defaultReturnValue() != map.defaultReturnValue()) {
            throw new IllegalArgumentException("Default value of vectors must match");
        }

        final MutableSparseFloatVector newVector = clone();
        final Long2FloatOpenHashMap newMap = newVector.map;
        final Long2FloatOpenHashMap otherMap = other.map;

        newVector.length = Math.max(length, other.length);
        for (final long key : otherMap.keySet()) {
            newMap.put(key, Math.max(map.get(key), otherMap.get(key)));
        }
        return newVector;
    }

    @Override
    public void trim() {
        map.trim();
    }

    @Override
    protected MutableSparseFloatVector createIntVector(final long newVectorLength) {
        return new MutableSparseFloatVector(newVectorLength);
    }

    @Override
    protected MutableSparseFloatVector createFloatVector(final long newVectorLength) {
        return new MutableSparseFloatVector(newVectorLength);
    }

    @Override
    public void write(final Writer writer) throws IOException {
        writer.write(String.format("vector type=mutable-sparse-float length=%d sparse=true\n", length));

        // Write Vector contents
        for (int i = 0; i < length - 1; i++) {
            if (map.containsKey(i)) {
                writer.write(String.format("%d %f ", i, getFloat(i)));
            }
        }
        writer.write(String.format("%d %f\n", length - 1, getFloat((int) (length - 1))));
        writer.flush();
    }

    @Override
    public MutableSparseFloatVector clone() {
        final MutableSparseFloatVector clone = new MutableSparseFloatVector(length);
        for (final long key : map.keySet()) {
            clone.map.put(key, map.get(key));
        }
        return clone;
    }
}
