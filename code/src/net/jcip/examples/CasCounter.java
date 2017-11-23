package net.jcip.examples;

import net.jcip.annotations.*;

/**
 * CasCounter
 * <p/>
 * Nonblocking counter using CAS
 *
 * @author Brian Goetz and Tim Peierls
 */
@ThreadSafe
public class CasCounter {
    private SimulatedCAS value;

    public int getValue() {
        return value.get();
    }

    public int increment() {
        int v;
        do {
            // 获得当前的值
            v = value.get();
        } while (v != value.compareAndSwap(v, v + 1));
        // 如果返回值不同，则说明更新成功了
        return v + 1;
    }
}
