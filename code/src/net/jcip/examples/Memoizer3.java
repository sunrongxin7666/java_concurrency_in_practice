package net.jcip.examples;

import java.util.*;
import java.util.concurrent.*;

/**
 * Memoizer3
 * <p/>
 * Memoizing wrapper using FutureTask
 *
 * @author Brian Goetz and Tim Peierls
 */
public class Memoizer3 <A, V> implements Computable<A, V> {
    private final Map<A, Future<V>> cache
            = new ConcurrentHashMap<>();
    private final Computable<A, V> c;

    public Memoizer3(Computable<A, V> c) {
        this.c = c;
    }

    public V compute(final A arg) throws InterruptedException {
        Future<V> f = cache.get(arg);
        // 判断任务是否已经开始
        if (f == null) {// 如果任务没有开始，则开始计算
            Callable<V> eval = new Callable<V>() {
                public V call() throws InterruptedException {
                    return c.compute(arg);
                }
            };
            FutureTask<V> ft = new FutureTask<>(eval);
            f = ft;
            // 任务提交（有小概率的事件发生冲突）
            cache.put(arg, ft);
            // 任务运行；
            ft.run(); // call to c.compute happens here
        }
        try {
            // 获得计算结果
            return f.get();
        } catch (ExecutionException e) {
            throw LaunderThrowable.launderThrowable(e.getCause());
        }
    }
}