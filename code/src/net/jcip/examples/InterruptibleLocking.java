package net.jcip.examples;

import java.util.concurrent.locks.*;

/**
 * InterruptibleLocking
 *
 * @author Brian Goetz and Tim Peierls
 */
public class InterruptibleLocking {
    private Lock lock = new ReentrantLock();

    public boolean sendOnSharedLine(String message)
            throws InterruptedException {
        // 可以响应中断的锁
        lock.lockInterruptibly();
        try {
            return cancellableSendOnSharedLine(message);
        } finally {
            lock.unlock();
        }
    }

    // 可能会抛出中断异常
    private boolean cancellableSendOnSharedLine(String message) throws InterruptedException {
        /* send something */
        return true;
    }

}
