package net.jcip.examples;

import java.util.*;
import java.util.concurrent.*;

/**
 * BoundedHashSet
 * <p/>
 * Using Semaphore to bound a collection
 *
 * @author Brian Goetz and Tim Peierls
 */
public class BoundedHashSet <T> {
    private final Set<T> set;
    //信号量
    private final Semaphore sem;

    public BoundedHashSet(int bound) {
        //获得同步容器
        this.set = Collections.synchronizedSet(new HashSet<T>());
        sem = new Semaphore(bound);
    }

    public boolean add(T o) throws InterruptedException {
        //请求获得信号量
        sem.acquire();
        boolean wasAdded = false;
        try {
            wasAdded = set.add(o);
            return wasAdded;
        } finally {
            if (!wasAdded)
                //无论添加操作是否成功，都释放信号量
                sem.release();
        }
    }

    public boolean remove(T o) {
        boolean wasRemoved = set.remove(o);
        //移除成功之后，会释放一个信号量
        if (wasRemoved)
            sem.release();
        return wasRemoved;
    }
}
