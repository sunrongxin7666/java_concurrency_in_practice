package net.jcip.examples;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

/**
 * ReaderThread
 * <p/>
 * Encapsulating nonstandard cancellation in a Thread by overriding interrupt
 *
 * @author Brian Goetz and Tim Peierls
 */
public class ReaderThread extends Thread {
    private static final int BUFSZ = 512;
    private final Socket socket;
    private final InputStream in;

    public ReaderThread(Socket socket) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
    }

    public void interrupt() {
        try {
            // 关闭套接字
            // 此时in.read会抛出异常
            socket.close();
        } catch (IOException ignored) {
        } finally {
            // 正常的中断
            super.interrupt();
        }
    }

    public void run() {
        try {
            byte[] buf = new byte[BUFSZ];
            while (true) {
                int count = in.read(buf);
                if (count < 0)
                    break;
                else if (count > 0)
                    processBuffer(buf, count);
            }
        } catch (IOException e) { /* Allow thread to exit */
            // 如果socket关闭，in.read方法将会抛出异常
            // 借此机会，运行线程退出
        }
    }

    public void processBuffer(byte[] buf, int count) {
    }
}
