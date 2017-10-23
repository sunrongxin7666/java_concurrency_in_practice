package net.jcip.examples;

import static java.util.concurrent.TimeUnit.SECONDS;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;

import net.jcip.annotations.*;

/**
 * PrimeGenerator
 * <p/>
 * Using a volatile field to hold cancellation state
 *
 * @author Brian Goetz and Tim Peierls
 */
@ThreadSafe
public class PrimeGenerator implements Runnable {
    private static ExecutorService exec = Executors.newCachedThreadPool();

    @GuardedBy("this") private final List<BigInteger> primes
            = new ArrayList<BigInteger>();
    //取消标志位
    private volatile boolean cancelled;

    public void run() {
        BigInteger p = BigInteger.ONE;
        //每次在生成下一个素数时坚持是否取消
        //如果取消，则退出
        while (!cancelled) {
            p = p.nextProbablePrime();
            synchronized (this) {
                primes.add(p);
            }
        }
    }

    public void cancel() {
        cancelled = true;
    }

    public synchronized List<BigInteger> get() {
        return new ArrayList<BigInteger>(primes);
    }

    static List<BigInteger> aSecondOfPrimes() throws InterruptedException {
        PrimeGenerator generator = new PrimeGenerator();
        exec.execute(generator);
        try {
            SECONDS.sleep(1);
        } finally {
            generator.cancel();
        }
        return generator.get();
    }

    public static void main(String[] args) {
        try {
            System.out.println(PrimeGenerator.aSecondOfPrimes());
            System.out.println("结束");
            return;
        } catch (InterruptedException e) {
            System.out.println("中断");
        }
    }
}
