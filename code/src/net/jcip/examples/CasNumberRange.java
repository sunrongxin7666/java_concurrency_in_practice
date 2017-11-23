package net.jcip.examples;

import java.util.concurrent.atomic.*;

import net.jcip.annotations.*;

/**
 * CasNumberRange
 * <p/>
 * Preserving multivariable invariants using CAS
 *
 * @author Brian Goetz and Tim Peierls
 */
@ThreadSafe
public class CasNumberRange {
    @Immutable
    private static class IntPair {
        // INVARIANT: lower <= upper
        final int lower;
        final int upper;

        public IntPair(int lower, int upper) {
            this.lower = lower;
            this.upper = upper;
        }
    }


    //源自引用 IntPair 初始化为[0，0]
    private final AtomicReference<IntPair> values =
            new AtomicReference<IntPair>(new IntPair(0, 0));

    public int getLower() {
        return values.get().lower;
    }

    public int getUpper() {
        return values.get().upper;
    }

    //设置下限
    public void setLower(int i) {
        while (true) {
            IntPair oldv = values.get();
            // 如果下限设置比当前上限还要大
            if (i > oldv.upper)
                //抛出异常
                throw new IllegalArgumentException("Can't set lower to " + i + " > upper");
            IntPair newv = new IntPair(i, oldv.upper);
            //原子性更新
            if (values.compareAndSet(oldv, newv))
                return;
        }
    }

    //设置上限 过程和setLower类似
    public void setUpper(int i) {
        while (true) {
            IntPair oldv = values.get();
            if (i < oldv.lower)
                throw new IllegalArgumentException("Can't set upper to " + i + " < lower");
            IntPair newv = new IntPair(oldv.lower, i);
            if (values.compareAndSet(oldv, newv))
                return;
        }
    }
}
