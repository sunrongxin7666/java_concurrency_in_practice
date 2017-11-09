package net.jcip.examples;

import java.util.*;
import java.util.concurrent.*;

/**
 * Memoizer2
 * <p/>
 * Replacing HashMap with ConcurrentHashMap
 *
 * @author Brian Goetz and Tim Peierls
 */
public class Memoizer2 <A, V> implements Computable<A, V> {
    private final Map<A, V> cache = new ConcurrentHashMap<>();
    private final Computable<A, V> c;

    public Memoizer2(Computable<A, V> c) {
        this.c = c;
    }

    // cache是并发容器，支持多线程同时访问，
    // 但是不能表示出某个结果正在被计算
    public V compute(A arg) throws InterruptedException {
        V result = cache.get(arg);
        if (result == null) {
            result = c.compute(arg);
            cache.put(arg, result);
        }
        return result;
    }
}
