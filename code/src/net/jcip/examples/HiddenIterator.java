package net.jcip.examples;

import java.util.*;

import net.jcip.annotations.*;

/**
 * HiddenIterator
 * <p/>
 * Iteration hidden within string concatenation
 *
 * @author Brian Goetz and Tim Peierls
 */
public class HiddenIterator {
    //应该使用并发容器
    @GuardedBy("this") private final Set<Integer> set = new HashSet<Integer>();

    public synchronized void add(Integer i) {
        set.add(i);
    }

    public synchronized void remove(Integer i) {
        set.remove(i);
    }

    public void addTenThings() {
        Random r = new Random();
        for (int i = 0; i < 10; i++)
            add(r.nextInt());
        // 隐式地调用了迭代器，
        // 连接字符串操作会调用StringBuilder.append(Object),
        // 而这个方法又会调用容器Set的toString(),
        // 标准容器（不仅是Set）的ToString方法会使用迭代器以此调用容器内元素的toString方法。
        System.out.println("DEBUG: added ten elements to " + set);
    }
}
