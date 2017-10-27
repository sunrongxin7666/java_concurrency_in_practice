package net.jcip.examples;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.concurrent.*;

import net.jcip.annotations.*;

/**
 * LogService
 * <p/>
 * Adding reliable cancellation to LogWriter
 *
 * @author Brian Goetz and Tim Peierls
 */
public class LogService {
    private final BlockingQueue<String> queue;
    private final LoggerThread loggerThread;
    private final PrintWriter writer;
    @GuardedBy("this") private boolean isShutdown;
    // 信号量 用来记录队列中消息的个数
    @GuardedBy("this") private int reservations;

    public LogService(Writer writer) {
        this.queue = new LinkedBlockingQueue<String>();
        this.loggerThread = new LoggerThread();
        this.writer = new PrintWriter(writer);
    }

    public void start() {
        loggerThread.start();
    }

    public void stop() {
        synchronized (this) {
            isShutdown = true;
        }
        loggerThread.interrupt();
    }

    public void log(String msg) throws InterruptedException {
        synchronized (this) {
            //同步方法判断是否关闭和修改信息量
            if (isShutdown) // 如果已关闭，则不再允许生产者将消息添加到队列，会抛出异常
                throw new IllegalStateException(/*...*/);
            //如果在工作状态，信号量增加
            ++reservations;
        }
        // 消息入队列；
        queue.put(msg);
    }

    private class LoggerThread extends Thread {
        public void run() {
            try {
                while (true) {
                    try {
                        //同步方法读取关闭状态和信息量
                        synchronized (LogService.this) {
                            //如果进程被关闭且队列中已经没有消息了，则消费者退出
                            if (isShutdown && reservations == 0)
                                break;
                        }
                        // 取出消息
                        String msg = queue.take();
                        // 消费消息前，修改信号量
                        synchronized (LogService.this) {
                            --reservations;
                        }
                        writer.println(msg);
                    } catch (InterruptedException e) { /* retry */
                    }
                }
            } finally {
                writer.close();
            }
        }
    }
}

