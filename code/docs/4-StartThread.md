#并发实战 6. 结构化并发应用程序
@(Java并发)[java, 并发, jcip]

>并发设计的本质，就是要把程序的逻辑分解为多个任务，这些任务独立而又协作的完成程序的功能。而其中最关键的地方就是如何将逻辑上的任务分配到实际的线程中去执行。换而言之，**任务是目的，而线程是载体**，线程的实现要以任务为目标。

## 1. 在线程中执行任务
并发程序设计的第一步就是要划分任务的边界，理想情况下就是所有的任务都独立的:每个任务都是不依赖于其他任务的状态，结果和边界。因为独立的任务是最有利于并发设计的。

有一种最自然的任务划分方法就是一独立的客户请求为任务边界。每个用户请求是独立的，则处理任务请求的任务也是独立的。

再划分完任务之后，下一问题就是如何调度这些任务，最简单的方法就是串行调用所有任务，也就是一个一个的执行。

比如下面的这个套接字服务程序，每次都只能响应一个请求，下一个请求需要等上一个请求执行完毕之后再被处理。
```java
public class SingleThreadWebServer {
    public static void main(String[] args) throws IOException {
        ServerSocket socket = new ServerSocket(80);
        while (true) {
            Socket connection = socket.accept();
            handleRequest(connection);
        }
    }

    private static void handleRequest(Socket connection) {
        // request-handling logic here
    }
}
```
这种设计当然是不能满足要求的，并发的高吞吐和高响应速度的优势都没发挥出来。

### 1.1 显示地创建线程
上述代码的优化版就是为每个请求都分配独立的线程来执行，也就是每一个请求任务都是一个独立线程。
```
public class ThreadPerTaskWebServer {
    public static void main(String[] args) throws IOException {
        ServerSocket socket = new ServerSocket(80);
        while (true) {
            final Socket connection = socket.accept();
            //为每个请求创建单独的线程任务，保证并发性
            Runnable task = new Runnable() {
                public void run() {
                    handleRequest(connection);
                }
            };
            new Thread(task).start();
        }
    }

    private static void handleRequest(Socket connection) {
        // request-handling logic here
    }
}
```
这样设计的优点在于：
- 任务处理线程从主线程分离出来，使得主线程不用等待任务完毕就可以去快速地去响应下一个请求，以达到高响应速度；
- 任务处理可以并行，支持同时处理多个请求；
- 任务处理是线程安全的，因为每个任务都是独立的。

不过需要注意的是，**任务必须是线程安全的**，否者多线程并发时会有问题。

### 1.3 无限制创建线程的不足
但是以上的方案还是有不足的：
1. 线程的生命周期的**开销**很大：每创建一个线程都是要消耗大量的计算资源；
2. **资源的消耗**：活跃的线程要消耗内存资源，如果有太多的空闲资源就会使得很多内存资源浪费，导致内存资源不足，多线程并发时就会出现资源强占的问题；
3. 稳定性：可创建线程的个数是有限制的，过多的线程数会造成内存溢出；

利用创建线程来攻击的例子中，最显而易见的就是不断创建死循环的线程，最终导致整个计算机的资源都耗尽。

## 2.Executor框架
任务是一组逻辑工作单元，而线程则是任务异步执行的机制。为了让任务更好地分配到线程中执行，java.util.concurrent提供了Executor框架。

Executor基于生产者-消费者模式:提交任务的操作相当于生产者（生成待完成的工作单元），执行任务的线程则相当于消费者（执行完这些工作单元）。

将以上的服务端代码改造为Executor框架如下：
```
public class TaskExecutionWebServer {
    //设定线程池大小；
    private static final int NTHREADS = 100;
    private static final Executor exec
            = Executors.newFixedThreadPool(NTHREADS);

    public static void main(String[] args) throws IOException {
        ServerSocket socket = new ServerSocket(80);
        while (true) {
            final Socket connection = socket.accept();
            Runnable task = new Runnable() {
                public void run() {
                    handleRequest(connection);
                }
            };
            exec.execute(task);
        }
    }

    private static void handleRequest(Socket connection) {
        // request-handling logic here
    }
}
```

### 2.1 线程池
Executor的本质就是管理和调度线程池。所谓线程池就是指管理一组同构工作线程的资源池。线程池和任务队列相辅相成：任务队列中保存着所有带执行的任务，而线程池中有着可以去执行任务的工作线程，工作线程从任务队列中领域一个任务执行，执行任务完毕之后在回到线程池中等待下一个任务的到来。

任务池的优势在于：
1. 通过复用现有线程而不是创建新的线程，降低创建线程时的开销；
2. 复用现有线程，可以直接执行任务，避免因创建线程而让任务等待，提高响应速度。

Executor可以创建的线程池共有四种：
1. newFixedThreadPool，即固定大小的线程池，如果有线程因发生了异常而崩溃，会创建新的线程代替:
2. newCachedThreadPool，即支持缓存的线程池，如果线程池的规模超过了需求的规模，就会回收空闲线程，如果需求增加，则会增加线程池的规模;
3. newScheduledThreadPool，固定大小的线程池，而且以延时或者定时的方式执行;
4. newSingleThreadExecutor，单线程模式，串行执行任务;
### 2.2 Executor的生命周期
这里需要单独说下Executor的生命周期。由于JVM只有在非守护线程全部终止才会退出，所以如果没㕛正确退出Executor，就会导致JVM无法正常结束。但是Executor是采用异步的方式执行线程，并不能立刻知道所有线程的状态。为了更好的管理Executor的生命周期，提供了Executor的扩展接口**ExecutorService**。

ExecutorService提供了两种方法关闭方法：
- shutdown: 平缓的关闭过程，即不再接受新的任务，等到已提交的任务执行完毕后关闭进程池；
- shutdownNow: 立刻关闭所有任务，无论是否再执行；

服务端ExecutorService版的实现如下：
```
public class LifecycleWebServer {
    private final ExecutorService exec = Executors.newCachedThreadPool();

    public void start() throws IOException {
        ServerSocket socket = new ServerSocket(80);
        while (!exec.isShutdown()) {
            try {
                final Socket conn = socket.accept();
                exec.execute(new Runnable() {
                    public void run() {
                        handleRequest(conn);
                    }
                });
            } catch (RejectedExecutionException e) {
                if (!exec.isShutdown())
                    log("task submission rejected", e);
            }
        }
    }

    public void stop() {
        exec.shutdown();
    }

    private void log(String msg, Exception e) {
        Logger.getAnonymousLogger().log(Level.WARNING, msg, e);
    }

    void handleRequest(Socket connection) {
        Request req = readRequest(connection);
        if (isShutdownRequest(req))
            stop();
        else
            dispatchRequest(req);
    }

    interface Request {
    }

    private Request readRequest(Socket s) {
        return null;
    }

    private void dispatchRequest(Request r) {
    }

    private boolean isShutdownRequest(Request r) {
        return false;
    }
}
```

### 2.3 延迟任务和周期性任务
Java中提供Timer来执行延时任务和周期任务，但是Timer类有以下的缺陷：
1. Timer只会创建一个线程来执行任务，如果有一个TimerTask执行时间太长，就会影响到其他TimerTask的定时精度；
2. Timer不会捕捉TimerTask未定义的异常，所以当有异常抛出到Timer中时，Timer就会崩溃，而且也无法恢复，就会影响到已经被调度但是没有执行的任务，造成“线程泄露”。

因此建议使用ScheduledThreadPoolExcutor来代替Timer类。



