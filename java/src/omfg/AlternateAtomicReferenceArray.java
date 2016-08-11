package omfg;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Created by matthewfl
 *
 * This is SUPPER BAD
 * It gets a reference to the array object
 * and then allows for avoiding violate read operations from the array
 * since those seem to be having a significant performance impact and
 * given how we have implemented our algo doesn't affect correctness
 */
public class AlternateAtomicReferenceArray<E> extends AtomicReferenceArray<E> {

    protected final Object[] arrayRef;

    private static Field arrayRefField;
    static {
        Field f = null;
        try {
            f = AtomicReferenceArray.class.getDeclaredField("array");
            f.setAccessible(true);
        } catch(NoSuchFieldException e) {}
        arrayRefField = f;
    }

    private void init() {
    }

    public AlternateAtomicReferenceArray(int length) {
        super(length);
        Object arr = null;
        try {
            arr = arrayRefField.get(this);
        } catch(IllegalAccessException e) {}
        arrayRef = (Object[])arr;
    }

    public E fastGet(int i) {
        return (E)arrayRef[i];
    }

}
