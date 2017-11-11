package net.jcip.examples;

import java.util.concurrent.*;

/**
 * TestHarness
 * <p/>
 * Using CountDownLatch for starting and stopping threads in timing tests
 *
 * @author Brian Goetz and Tim Peierls
 */
public class TestHarness {
    public long timeTasks(int nThreads, final Runnable task)
            throws InterruptedException {
        //开始锁
        final CountDownLatch startGate = new CountDownLatch(1);
        //结束锁
        final CountDownLatch endGate = new CountDownLatch(nThreads);

        for (int i = 0; i < nThreads; i++) {
            Thread t = new Thread() {
                public void run() {
                    try {
                        // 等待主线程初始化完毕
                        startGate.await();
                        try {
                            task.run();
                        } finally {
                            // 结束锁释放一个
                            endGate.countDown();
                        }
                    } catch (InterruptedException ignored) {
                    }
                }
            };
            t.start();
        }

        // 记录当前时间为开始时间
        long start = System.nanoTime();
        // 初始化完毕，开启开始锁，子线程可以运行
        startGate.countDown();
        // 等到个子线程运行完毕
        endGate.await();
        // 统计执行时间
        long end = System.nanoTime();
        return end - start;
    }
}
