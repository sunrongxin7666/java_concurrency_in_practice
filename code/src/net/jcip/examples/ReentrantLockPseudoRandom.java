package net.jcip.examples;

import java.util.concurrent.locks.*;

import net.jcip.annotations.*;

/**
 * ReentrantLockPseudoRandom
 * <p/>
 * Random number generator using ReentrantLock
 *
 * @author Brian Goetz and Tim Peierls
 */
@ThreadSafe
public class ReentrantLockPseudoRandom extends PseudoRandom {
    // 非公平锁
    private final Lock lock = new ReentrantLock(false);
    private int seed;

    ReentrantLockPseudoRandom(int seed) {
        this.seed = seed;
    }

    public int nextInt(int n) {
        lock.lock();
        try {
            int s = seed;
            seed = calculateNext(s);
            int remainder = s % n;
            return remainder > 0 ? remainder : remainder + n;
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) {

        String a = "Programming";

        String b = new String("Programming");

        String c = "Program" + "ming";

        System.out.println(a == b);

        System.out.println(a == c);

        System.out.println(a.equals(b));

        System.out.println(a.equals(c));

        System.out.println(a.intern() == b.intern());

    }
}
