package net.jcip.examples;

import java.util.concurrent.*;

import net.jcip.annotations.*;

/**
 * BoundedExecutor
 * <p/>
 * Using a Semaphore to throttle task submission
 *
 * @author Brian Goetz and Tim Peierls
 */
@ThreadSafe
public class BoundedExecutor {
    private final Executor exec;
    //信号量
    private final Semaphore semaphore;

    public BoundedExecutor(Executor exec, int bound) {
        this.exec = exec;
        this.semaphore = new Semaphore(bound);
    }

    public void submitTask(final Runnable command)
            throws InterruptedException {
        //获得信号量
        semaphore.acquire();
        try {
            //开始执行任务
            exec.execute(new Runnable() {
                public void run() {
                    try {
                        command.run();
                    } finally {
                        // 无论任务执行完毕，还是任务报错，
                        // 都会释放信号量
                        semaphore.release();
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            semaphore.release();
        }
    }
}
