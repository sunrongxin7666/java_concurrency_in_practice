package net.jcip.examples;

import java.util.concurrent.*;
import static java.util.concurrent.Executors.newScheduledThreadPool;
import static net.jcip.examples.LaunderThrowable.launderThrowable;

/**
 * TimedRun2
 * <p/>
 * Interrupting a task in a dedicated thread
 *
 * @author Brian Goetz and Tim Peierls
 */
public class TimedRun2 {
    private static final ScheduledExecutorService cancelExec = newScheduledThreadPool(1);

    public static void timedRun(final Runnable r,
                                long timeout, TimeUnit unit)
            throws InterruptedException {
        class RethrowableTask implements Runnable {
            private volatile Throwable t;

            public void run() {
                try {
                    r.run();
                } catch (Throwable t) {
                    //中断策略，保存当前抛出的异常，退出
                    this.t = t;
                }
            }

            void rethrow() {
                if (t != null)
                    throw launderThrowable(t);
            }
        }

        RethrowableTask task = new RethrowableTask();
        final Thread taskThread = new Thread(task);
        //开启任务子线程
        taskThread.start();
        //定时中断任务子线程
        cancelExec.schedule(new Runnable() {
            public void run() {
                taskThread.interrupt();
            }
        }, timeout, unit);

        //限时等待任务子线程执行完毕
        taskThread.join(unit.toMillis(timeout));
        //尝试抛出task在执行中抛出到异常
        task.rethrow();
    }
}
