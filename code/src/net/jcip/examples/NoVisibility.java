package net.jcip.examples;

/**
 * NoVisibility
 * <p/>
 * Sharing variables without synchronization
 *
 * @author Brian Goetz and Tim Peierls
 */

public class NoVisibility {
    private static boolean ready = false;
    private static int number;

    private static class ReaderThread extends Thread {
        public void run() {
            while (!ready)
                // 线程让步,使当前线程从执行状态（运行状态）变为可执行态（就绪状态）。
                // 就是说当一个线程使用了这个方法之后，它就会把自己CPU执行的时间让掉，
                // 让自己或者其它的线程运行。
                Thread.yield();
            System.out.println(number);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        new ReaderThread().start();
        //JVM可能
        Thread.sleep(1000);
        number = 42;
        ready = true;
    }
}
