package net.jcip.examples;

import java.util.concurrent.*;

/**
 * InterruptBorrowedThread
 * <p/>
 * Scheduling an interrupt on a borrowed thread
 *
 * @author Brian Goetz and Tim Peierls
 */
public class TimedRun1 {
    private static final ScheduledExecutorService cancelExec = Executors.newScheduledThreadPool(1);

    public static void timedRun(Runnable r,
                                long timeout, TimeUnit unit) {
        final Thread taskThread = Thread.currentThread();
        cancelExec.schedule(new Runnable() {
            public void run() {
                // 中断调用线程，
                // 违规，不能在不知道中断策略的前提下调用中断，
                // 该方法可能被任意线程调用。
                taskThread.interrupt();
            }
        }, timeout, unit);
        r.run();
    }
}
