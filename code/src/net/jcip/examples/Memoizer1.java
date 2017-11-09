package net.jcip.examples;

import java.math.BigInteger;
import java.util.*;

import net.jcip.annotations.*;

/**
 * Memoizer1
 *
 * Initial cache attempt using HashMap and synchronization
 *
 * @author Brian Goetz and Tim Peierls
 */
public class Memoizer1 <A, V> implements Computable<A, V> {
    @GuardedBy("this") private final Map<A, V> cache = new HashMap<>();
    private final Computable<A, V> c;

    public Memoizer1(Computable<A, V> c) {
        this.c = c;
    }

    // 该方法对整个容器上锁，如果容器过大可能导致操作时间比没有缓存的情况更久
    // 建议使用并发容器；
    public synchronized V compute(A arg) throws InterruptedException {
        V result = cache.get(arg);
        if (result == null) {
            result = c.compute(arg);
            cache.put(arg, result);
        }
        return result;
    }
}


interface Computable <A, V> {
    V compute(A arg) throws InterruptedException;
}

class ExpensiveFunction
        implements Computable<String, BigInteger> {
    public BigInteger compute(String arg) {
        // after deep thought...
        return new BigInteger(arg);
    }
}
