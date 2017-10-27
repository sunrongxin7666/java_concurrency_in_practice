#并发实战 7. 关闭线程
@(Java并发)[java, 并发, jcip]

线程在启动之后，正常的情况下会运行到任务完成，但是有的情况下会需要提前结束任务，如用户取消操作等。可是，让线程安全、快速和可靠地停止并不是件容易的事情，因为Java中**没有提供安全的机制**来终止线程。虽然有*Thread.stop/suspend*等方法，但是这些方法存在缺陷，不能保证线程中共享数据的一致性，所以应该避免直接调用。

>线程在终止的过程中，应该先进行操作来清除当前的任务，保持**共享数据的一致性**，然后再停止。

庆幸的是，Java中提供了**中断**机制，来让多线程之间相互协作，由一个进程来安全地终止另一个进程。

## 1. 任务的取消
如果外部的代码能在某个操作正常完成之前将其设置为完成状态，则该操作为**可取消的**（*Cancellable*）。

操作被取消的原因有很多，比如超时，异常，请求被取消等等。

>一个可取消的任务要求必须设置**取消策略**，即如何取消，何时检查取消命令，以及接收到取消命令之后如何处理。

最简单的取消办法就是利用**取消标志位**，如下所示：

```
public class PrimeGenerator implements Runnable {
    private static ExecutorService exec = Executors.newCachedThreadPool();

    private final List<BigInteger> primes
            = new ArrayList<BigInteger>();
    //取消标志位
    private volatile boolean cancelled;

    public void run() {
        BigInteger p = BigInteger.ONE;
        //每次在生成下一个素数时坚持是否取消
        //如果取消，则退出
        while (!cancelled) {
            p = p.nextProbablePrime();
            synchronized (this) {
                primes.add(p);
            }
        }
    }

    public void cancel() {
        cancelled = true;
    }

    public synchronized List<BigInteger> get() {
        return new ArrayList<BigInteger>(primes);
    }

    static List<BigInteger> aSecondOfPrimes() throws InterruptedException {
        PrimeGenerator generator = new PrimeGenerator();
        exec.execute(generator);
        try {
            SECONDS.sleep(1);
        } finally {
            generator.cancel();
        }
        return generator.get();
    }
}
```
这段代码用于生成素数，并在任务运行一秒钟之后终止。其取消策略为：通过改变取消标志位取消任务，任务在每次生成下一随机素数之前检查任务是否被取消，被取消后任务将退出。

然而，该机制的最大的问题就是无法应用于**拥塞方法**。假设在循环中调用了拥塞方法，任务可能因拥塞而永远不会去检查取消标志位，甚至会造成永远不能停止。

### 1.1 中断
为了解决拥塞方法带来的问题，就需要使用中断机制来取消任务。

>虽然在Java规范中，线程的取消和中断没有必然联系，但是在实践中发现：*中断是取消线程的最合理的方式*。

Thread类中和中断相关的方法如下：

``` java
public class Thread {
	// 中断当前线程
	public void interrupt();
	// 判断当前线程是否被中断
	public boolen isInterrupt();
	// 清除当前线程的中断状态，并返回之前的值
	public static boolen interrupt();	
}
```

调用Interrupt方法并不是意味着要立刻停止目标线程，而只是传递请求中断的消息。所以对于中断操作的正确理解为：正在运行的线程收到中断请求之后，在下一个**合适的时刻**中断自己。


使用中断方法改进素数生成类如下：
```
public class PrimeProducer extends Thread {
    private final BlockingQueue<BigInteger> queue;
    PrimeProducer(BlockingQueue<BigInteger> queue) {
        this.queue = queue;
    }

    public void run() {
        try {
            BigInteger p = BigInteger.ONE;
            //使用中断的方式来取消任务
            while (!Thread.currentThread().isInterrupted())
                //put方法会隐式检查并响应中断
                queue.put(p = p.nextProbablePrime());
        } catch (InterruptedException consumed) {
            /* 允许任务退出 */
        }
    }

    public void cancel() {
        interrupt();
    }
}
```
代码中有两次检查中断请求：
- 第一次是在循环开始前，显示检查中断请求；
- 第二次是在put方法，该方法为拥塞的，会隐式坚持当前线程是否被中断；

### 1.2 中断策略
和取消策略类似，可以被中断的任务也需要有**中断策略**:
即如何中断，合适检查中断请求，以及接收到中断请求之后如何处理。

>由于每个线程拥有各自的中断策略，因此除非清楚中断对目标线程的含义，否者不要中断该线程。

正是由于以上原因，大多数拥塞的库函数在检测到中断都是抛出中断异常（*InterruptedException*）作为中断响应，让线程的所有者去处理，而不是去真的中断当前线程。

虽然有人质疑Java没有提供抢占式的中断机制，但是开发人员通过处理中断异常的方法，可以定制更为灵活的中断策略，从而在响应性和健壮性之间做出合理的平衡。

一般情况的中断响应方法为：
1. 传递异常：收到中断异常之后，直接将该异常抛出；
2. 回复中断状态：即再次调用Interrupt方法，恢复中断状态，让调用堆栈的上层能看到中断状态进而处理它。

>切记，只有实现了线程中断策略的代码才能屏蔽中断请求，在常规的任务和库代码中都不应该屏蔽中断请求。中断请求是线程中断和取消的基础。

### 1.3 定时运行
定时运行一个任务是很常见的场景，很多问题是很费时间的，就需在规定时间内完成，如果没有完成则取消任务。

以下代码就是一个定时执行任务的实例：
```
public class TimedRun1 {
    private static final ScheduledExecutorService cancelExec = Executors.newScheduledThreadPool(1);

    public static void timedRun(Runnable r,
                                long timeout, TimeUnit unit) {
        final Thread taskThread = Thread.currentThread();
        cancelExec.schedule(new Runnable() {
            public void run() {
                // 中断线程，
                // 违规，不能在不知道中断策略的前提下调用中断，
                // 该方法可能被任意线程调用。
                taskThread.interrupt();
            }
        }, timeout, unit);
        r.run();
    }
}
```
很可惜，这是反面的例子，因为*timedRun*方法在不知道*Runnable*对象的中断策略的情况下，就中断该任务，这样会承担很大的风险。而且如果*Runnable*对象不支持中断， 则该定时模型就会失效。

为了解决上述问题，就需要执行任务都线程有自己的中断策略，如下：
```
public class LaunderThrowable {
    public static RuntimeException launderThrowable(Throwable t) {
        if (t instanceof RuntimeException)
            return (RuntimeException) t;
        else if (t instanceof Error)
            throw (Error) t;
        else
            throw new IllegalStateException("Not unchecked", t);
    }
}

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

			// 再次抛出异常
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
```
无论*Runnable*对象是否支持中断，*RethrowableTask*对象都会记录下来发生的异常信息并结束任务，并将该异常再次抛出。

### 1.4 通过Future取消任务
*Future*用来管理任务的生命周期，自然也可以来取消任务，调用*Future.cancel*方法就是用中断请求结束任务并退出，这也是*Executor*的默认中断策略。

用Future实现定时任务的代码如下：
```
public class TimedRun {
    private static final ExecutorService taskExec = Executors.newCachedThreadPool();

    public static void timedRun(Runnable r,
                                long timeout, TimeUnit unit)
            throws InterruptedException {
        Future<?> task = taskExec.submit(r);
        try {
            task.get(timeout, unit);
        } catch (TimeoutException e) {
            // 因超时而取消任务
        } catch (ExecutionException e) {
            // 任务异常，重新抛出异常信息
            throw launderThrowable(e.getCause());
        } finally {
            // 如果该任务已经完成，将没有影响
            // 如果任务正在运行，将因为中断而被取消
            task.cancel(true); // interrupt if running
        }
    }
}
```

### 1.5 不可中断的拥塞
一些的方法的拥塞是不能响应中断请求的，这类操作以I/O操作居多，但是可以让其抛出类似的异常，来停止任务：
- Socket I/O: 关闭底层*socket*，所有因执行读写操作而拥塞的线程会抛出*SocketException*；
- 同步 I/O：大部分Channel都实现了*InterruptiableChannel*接口，可以响应中断请求，抛出异常*ClosedByInterruptException*;
- Selector的异步 I/O：*Selector*执行*select*方法之后，再执行*close*和*wakeUp*方法就会抛出异常*ClosedSelectorException*。

以套接字为例，其利用关闭*socket*对象来响应异常的实例如下：
```
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
        } catch (IOException e) { 
            // 如果socket关闭，in.read方法将会抛出异常
            // 借此机会，响应中断，线程退出
        }
    }

    public void processBuffer(byte[] buf, int count) {
    }
}
```

## 2. 停止基于线程的服务
一个应用程序是由多个服务构成的，而每个服务会拥有多个线程为其工作。当应用程序关闭服务时，由服务来关闭其所拥有的线程。服务为了便于管理自己所拥有的线程，应该**提供生命周期**方来关闭这些线程。对于*ExecutorService*，其包含线程池，是其下属线程的拥有者，所提供的生命周期方法就是*shutdown*和*shutdownNow*方法。

>如果服务的生命周期大于所创建线程的生命周期，服务就应该提供生命周期方法来管理线程。

### 2.1 强行关闭和平缓关闭
我们以日志服务为例，来说明两种关闭方式的不同。首先，如下代码是不支持关闭的日志服务，其采用*多生产者-单消费者模式*，生产者将日志消息放入拥塞队列中，消费者从队列中取出日志打印出来。
```
public class LogWriter {
    // 拥塞队列作为缓存区
    private final BlockingQueue<String> queue;
    // 日志线程
    private final LoggerThread logger;
    // 队列大小
    private static final int CAPACITY = 1000;

    public LogWriter(Writer writer) {
        this.queue = new LinkedBlockingQueue<String>(CAPACITY);
        this.logger = new LoggerThread(writer);
    }

    public void start() {
        logger.start();
    }

    public void log(String msg) throws InterruptedException {
        queue.put(msg);
    }

    private class LoggerThread extends Thread {
        //线程安全的字节流
        private final PrintWriter writer;

        public LoggerThread(Writer writer) {
            this.writer = new PrintWriter(writer, true); // autoflush
        }

        public void run() {
            try {
                while (true)
                    writer.println(queue.take());
            } catch (InterruptedException ignored) {
            } finally {
                writer.close();
            }
        }
    }
}
```
如果没有终止操作，以上任务将无法停止，从而使得JVM也无法正常退出。但是，让以上的日志服务停下来其实并非难事，因为拥塞队列的*take*方法支持响应中断，这样直接关闭服务的方法就是**强行关闭**，强行关闭的方式不会去处理已经提交但还未开始执行的任务。

但是，关闭日志服务前，拥塞队列中可能还有没有及时打印出来的日志消息，所以强行关闭日志服务并不合适，需要等队列中已经存在的消息都打印完毕之后再停止，这就是**平缓关闭**，也就是在关闭服务时会等待已提交任务全部执行完毕之后再退出。

除此之外，在取消生产者-消费者操作时，还需要同时告知消费者和生产者相关操作已经被取消。

平缓关闭的日志服务如下，其采用了类似信号量的方式记录队列中尚未处理的消息数量。

```
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
```

### 2.2 关闭ExecutorService
在*ExecutorService*中，其提供了*shutdown*和*shutdownNow*方法来分别实现平缓关闭和强制关闭：
- *shutdownNow*：强制关闭，响应速度快，但是会有风险，因为有任务肯执行到一半被终止；
- *shutdown*：平缓关闭，响应速度较慢，会等到全部已提交的任务执行完毕之后再退出，更为安全。

这里还需要说明下*shutdownNow*方法的局限性，因为强行关闭直接关闭线程，所以无法通过常规的方法获得哪些任务还没有被执行。这就会导致我们无纺知道线程的工作状态，就需要服务自身去记录任务状态。如下为示例代码：
```
public class TrackingExecutor extends AbstractExecutorService {
    private final ExecutorService exec;

    //被取消任务的队列
    private final Set<Runnable> tasksCancelledAtShutdown =
            Collections.synchronizedSet(new HashSet<Runnable>());

    public TrackingExecutor(ExecutorService exec) {
        this.exec = exec;
    }

    public void shutdown() {
        exec.shutdown();
    }

    public List<Runnable> shutdownNow() {
        return exec.shutdownNow();
    }

    public boolean isShutdown() {
        return exec.isShutdown();
    }

    public boolean isTerminated() {
        return exec.isTerminated();
    }

    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        return exec.awaitTermination(timeout, unit);
    }

    public List<Runnable> getCancelledTasks() {
        if (!exec.isTerminated())
            throw new IllegalStateException(/*...*/);
        return new ArrayList<Runnable>(tasksCancelledAtShutdown);
    }

    public void execute(final Runnable runnable) {
        exec.execute(new Runnable() {
            public void run() {
                try {
                    runnable.run();
                } finally {
                    // 如果当前任务被中断且执行器被关闭，则将该任务加入到容器中
                    if (isShutdown()
                            && Thread.currentThread().isInterrupted())
                        tasksCancelledAtShutdown.add(runnable);
                }
            }
        });
    }
}
```

## 3. 处理非正常线程终止

导致线程非正常终止的主要原因就是*RuntimeException*，其表示为不可修复的错误。一旦子线程抛出异常，该异常并不会被父线程捕获，而是会直接抛出到控制台。所以要认真处理线程中的异常，尽量设计完备的try-catch-finally代码块。

当然，异常总是会发生的，为了处理能主动解决未检测异常问题，*Thread.API*提供了接口**UncaughtExceptionHandler**。
```
public interface UncaughtExceptionHandler {
    void uncaughtException(Thread t, Throwable e);
}
```
如果JVM发现一个线程因未捕获异常而退出，就会把该异常交个*Thread*对象设置的*UncaughtExceptionHandler*来处理，如果*Thread*对象没有设置任何异常处理器，那么默认的行为就是上面提到的抛出到控制台，在*System.err*中输出。

Thread对象通过**setUncaughtExceptionHandler**方法来设置*UncaughtExceptionHandler*，比如这样：
```
public class WitchCaughtThread  
{  
    public static void main(String args[])  
    {  
        Thread thread = new Thread(new Task());  
        thread.setUncaughtExceptionHandler(new ExceptionHandler());  
        thread.start();  
    }  
}  
  
class ExceptionHandler implements UncaughtExceptionHandler  
{  
    @Override  
    public void uncaughtException(Thread t, Throwable e)  
    {  
        System.out.println("==Exception: "+e.getMessage());  
    }  
}  
```
同样可以为所有的Thread设置一个默认的*UncaughtExceptionHandler*，通过调用*Thread.setDefaultUncaughtExceptionHandler(Thread.UncaughtExceptionHandler eh)*方法，这是*Thread*的一个*static*方法。

下面是一个例子，即发生为捕获异常时将异常写入日志：
```
public class UEHLogger implements Thread.UncaughtExceptionHandler {

    // 将未知的错误计入到日志中
    public void uncaughtException(Thread t, Throwable e) {
        Logger logger = Logger.getAnonymousLogger();
        logger.log(Level.SEVERE, "Thread terminated with exception: " + t.getName(), e);
    }
}
```

>在*Executor*框架中，需要将异常的捕获封装到*Runnable*或者*Callable*中并通过*execute*提交的任务，才能将它抛出的异常交给*UncaughtExceptionHandler*，而通过*submit*提交的任务，无论是抛出的未检测异常还是已检查异常，都将被认为是任务返回状态的一部分。如果一个由*submit*提交的任务由于抛出了异常而结束，那么这个异常将被*Future.get*封装在*ExecutionException*中重新抛出。

```
public class ExecuteCaught  
{  
    public static void main(String[] args)  
    {  
        ExecutorService exec = Executors.newCachedThreadPool();  
        exec.execute(new ThreadPoolTask());  
        exec.shutdown();  
    }  
}  
  
class ThreadPoolTask implements Runnable  
{  
    @Override  
    public void run()  
    {  
        Thread.currentThread().setUncaughtExceptionHandler(new ExceptionHandler());  
        System.out.println(3/2);  
        System.out.println(3/0);  
        System.out.println(3/1);  
    }  
}  
```