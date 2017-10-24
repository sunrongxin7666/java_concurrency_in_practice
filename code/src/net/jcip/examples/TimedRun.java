package net.jcip.examples;

import java.util.concurrent.*;
import static net.jcip.examples.LaunderThrowable.launderThrowable;

/**
 * TimedRun
 * <p/>
 * Cancelling a task using Future
 *
 * @author Brian Goetz and Tim Peierls
 */
public class TimedRun {
    private static final ExecutorService taskExec = Executors.newCachedThreadPool();

    public static void timedRun(Runnable r,
                                long timeout, TimeUnit unit)
            throws InterruptedException {
        Future<?> task = taskExec.submit(r);
        try {
            task.get(timeout, unit);
        } catch (TimeoutException e) {
            // task will be cancelled below
            // 因超时而取消任务
        } catch (ExecutionException e) {
            // exception thrown in task; rethrow
            // 任务异常，重新抛出异常信息
            throw launderThrowable(e.getCause());
        } finally {
            // Harmless if task already completed
            // 如果该任务已经完成，将没有影响
            // 如果任务正在运行，将因为中断而被取消
            task.cancel(true); // interrupt if running
        }
    }
}
