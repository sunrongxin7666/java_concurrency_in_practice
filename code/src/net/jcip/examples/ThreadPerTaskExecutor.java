package net.jcip.examples;

import java.util.concurrent.*;

/**
 * ThreadPerTaskExecutor
 * <p/>
 * Executor that starts a new thread for each task
 *
 * @author Brian Goetz and Tim Peierls
 */
public class ThreadPerTaskExecutor implements Executor {
    public void execute(Runnable r) {
        //为每个任务分配一个新的线程
        new Thread(r).start();
    }
}
