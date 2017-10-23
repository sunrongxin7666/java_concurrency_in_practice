package net.jcip.examples;

import java.util.concurrent.*;

/**
 * NoncancelableTask
 * <p/>
 * Noncancelable task that restores interruption before exit
 *
 * @author Brian Goetz and Tim Peierls
 */
public class NoncancelableTask {
    public Task getNextTask(BlockingQueue<Task> queue) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    return queue.take();
                } catch (InterruptedException e) {
                    interrupted = true;
                    // fall through and retry
                    // 重新尝试
                }
            }
        } finally {
            //回复中断状态
            if (interrupted)
                Thread.currentThread().interrupt();
        }
    }

    interface Task {
    }
}
