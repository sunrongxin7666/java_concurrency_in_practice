#并发实战 8.  线程池的使用
@(Java并发)[java, 并发, jcip]
>Executor是一个强大多线程工作框架，其不仅提供了完善的执行策略便于用户使用，还提供多样的接口和参数供用户自定义配置，保证了框架的可扩展性和灵活性。本文将为大家介绍如何配置和使用线程池。

## 1. 任务与执行策略的耦合性
*Executor*框架可以帮助将任务的提交和任务的执行解耦合，用户只需要将任务提交给*Executor*之后，其自会按照既定的执行策略来执行任务。但是要注意并不是所有的任务都适合于所有的执行策略。如下任务需要制定特殊的执行策略。

- 依赖性任务：大多数任务都是相互独立的，但是有些情况下 ，任务之间会有依赖的关系，这个时候就需要维护任务之间的依赖关系，保证他们都能提交并允许，所以线程池应该足够大；如果一个被提交，而另一个因饱和而被丢弃，就可能造成两个相互依赖的任务都是失败，甚至造成死锁。
- 使用线程封闭机制的任务：和线程池相比，单线程的*Executor*是可以保证多线程安全的。如果一个利用线程封闭机制的任务，就要求*Executor*是单线程的的来执行，否者就会有并发安全问题。
- 对响应时间敏感的任务：如果一个时间敏感的任务提交到只包含着少量进程的*Executor*时，就很难保证任务的响应能力，降低用户体验，这时候就要求进程池足够的大，以提高响应速度，同时任务应该能响应中断，以防止耗时过多。
- 使用*ThreadLocal*的任务：由于*Executor*可以复用*Thread*对象就可能造成之前任务保存在*ThreadLocal*中的变量被后一个任务所获得，这种情况应该避免。

>只有当任务都是同类且独立时，线程池的性能才能最大化。

## 1.1设置进程池的大小
上面诸多情况都提到要合理设置进程池的大小，但是到底该如何设置呢？为了挖掘计算资源的能力，一般进程池的大小是由CPU、内存以及其他资源环境确定的。一般而言，对于计算密集型任务，在拥有$N_{cpu}$个处理器的系统上，线程池的大小为$$N_{thread}=1+N_{cpu}$$
才能充分使用CPU，保证时钟周期不浪费。而对于IO密集型任务，其任务因拥塞而不一定都在执行，线程池可以被设计的更大，等于：
$$N_{thread}=（1+W/C）*N_{cpu}*U_{cpu}$$
其中$W/C$为任务用于等待和计算的时间比，$U_{cpu}$为CPU利用率。


## 2. 配置ThreadPoolExecutor
*ThreadPoolExecutor*提供了*Executor*的基本实现，除了使用*newFixedThreadPool*、*newCachedThreadPool*、*newScheduledThreadPool*、*newSingleThreadExecutor*这四种常见的方法来获得特定配置的进程池，还可以进行各种定制，以获得灵活稳定的线程池。

以下是*ThreadPoolExecutor*的构造函数：

```
public ThreadPoolExecutor(
	int corePoolSize,//基本大小
	int maximumPoolSize, //最大大小
	long keepAliveTime, //线程保活时间
	TimeUnit unit, //保活时间单位                 
	BlockingQueue<Runnable> workQueue,//任务队列
    ThreadFactory threadFactory,//任务工厂
	RejectedExecutionHandler handler) {...}//饱和策略
```

每个参数如何使用，将在以下章节具体说明。
### 2.1 线程的创建和销毁
线程池的基本大小，最大大小和保活时间等因素共同负责线程的创建和销毁。
- 基本大小：线程池的目标大小，也就是没有任务时的线程池的初始大小，只有当任务队列已满时，线程池才回去创建超出这个数目的线程。
- 最大大小： 线程池中存活线程数的上限，如果线程池中的线程已经达到这个数目，则不能再继续创建线程。
- 线程保活时间：线程池中空闲线程的存活时间，当某个线程空闲时间达到该值之后，线程池可以将其回收，如果当前线程池的线程数目已经超过基本大小，则该线程会被终止。

通过设置以上三个参数，可以控制线程池使用资源的规模，如*newFixedThreadPool*方法就是将基本大小和最大大小设置为相同的值，所以只能创建固定规模的线程；而*newCachedThreadPool*方法则是将基本大小设置为0，最大大小设置为*MAX_VALUE*，因此可以自由伸缩，无限扩展。
![newFixedThreadPool](./1509541584070.png)
![newCachedThreadPool](./1509542115220.png)

### 2.2 管理队列任务
前文中提过，Executor框架的本质就是线程池加上任务队列，根据使用场景和任务特性使用不同任务队列才能将线程池的性能提高到最大。ThreadPoolExecutor使用拥塞队列BlockingQueue来保存等待的任务，任务队列共分为三种：无界队列，有解队列和同步队列。
- **无界队列**：*newFixedThreadPool*和*newSingleThreadExecutor*方法在默认情况下都是使用无界队列，当线程池中所有的任务都在忙碌时，达到的任务将会保存在队列中，如果任务达到的速率大于线程池处理任务的速率，任务队列就会无限地扩展。
- **有界队列**：如*ArrayBlockingQueue*和有界的*LinkedBlockingQueue*，这是一种更为稳健的做法，可以防止任务队列无限扩展而耗尽资源，所以建议根据任务规模设置为进程池设置有界队列。
- **同步队列**：为了避免任务的排队，可以使用同步队列*SynchronousQueue*,将任务从生产者直接提交给工作者（工作线程）。其实本质而言，同步队列不是一种队列，而是一种线程间进行移交的机制。当一个元素被的放入同步队列时，要求必须有一个线程（作为工作者）正在等待使用这个元素。如果线程池发现并没有线程在等待，且线程池大小没有达到最大时，便会新创建一个线程作为工作者去消费该任务。newCachedThreadPool方法便是使用同步队列，以提高效率。

### 2.3 饱和策略
可能有的读者会有疑问，如果任务队列装满该怎么办？这是就需要线程池指定**饱和策略**来规定任务队列满了之后线程池该如何行动。

ThreadPoolExecutor通过参数*RejectedExecutionHandler*来设定饱和策略，JDK中提供的实现共有四种：
- 中止策略(Abort Policy)：默认的策略，队列满时，会抛出异常RejectedExecutionException，调用者在捕获异常之后自行判断如何处理该任务；
- 抛弃策略(Discard Policy)：队列满时，进程池抛弃新任务，并不通知调用者；
- 抛弃最久策略(Discard-oldest Policy)：队列满时，进程池将抛弃队列中被提交最久的任务；
- 调用者运行策略(Caller-Runs Policy)：该策略不会抛弃任务，也不会抛出异常，而是将任务退还给调用者，也就是说当队列满时，新任务将在调用ThreadPoolExecutor的线程中执行。

以下代码就是一个制定饱和策略的进程池的实例，其中线程池的大小固定，饱和策略为“调用者运行”。
```
public class BoundedExecutor {
    private final Executor exec;
    //信号量
    private final Semaphore semaphore;

    public BoundedExecutor(int bound) {
        int N_Thread= Runtime.getRuntime().availableProcessors();
        this.exec = new ThreadPoolExecutor(N_Thread+1,
                N_Thread+1,
                0L, 
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(N_Thread));//设置固定大小的队列
        //设置调用者运行的绑定策略
        ((ThreadPoolExecutor) exec).setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
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
	        // 都会释放信号量
            semaphore.release();
        }
    }
}
```
值得一提的是，该例子中还使用信号量semaphore来控制任务达到数量，在饱和时拥塞线程，防止任务过多。

### 2.4 线程工厂
当线程池需要创建新的线程时，就会通过线程工厂来创建Thread对象。默认情况下，线程池的线程工厂会创建简单的新线程，如果需要用户可以为线程池定制线程工厂。

ThreadFactory接口只有一个方法，就是创建线程对象。开发人员可以根据自己的需求，扩展该方法，比如标记所属线程池的名字：
```
public interface ThreadFactory {
    Thread newThread(Runnable r);
}

public class MyThreadFactory implements ThreadFactory {
    private final String poolName;

    public MyThreadFactory(String poolName) {
        this.poolName = poolName;
    }

    public Thread newThread(Runnable runnable) {
        return new MyAppThread(runnable, poolName);
    }
}
```
出此之外，线程工厂还可以创建定制的线程类，比如为线程统一异常处理器。如下面的代码：
```java
// 
public class MyAppThread extends Thread {
    public static final String DEFAULT_NAME = "MyAppThread";
    private static volatile boolean debugLifecycle = false;
    //线程编号标记位
    private static final AtomicInteger created = new AtomicInteger();
    //运行个数标记位
    private static final AtomicInteger alive = new AtomicInteger();
    private static final Logger log = Logger.getAnonymousLogger();

    public MyAppThread(Runnable r) {
        this(r, DEFAULT_NAME);
    }

    public MyAppThread(Runnable runnable, String name) {
	    //新线程被创建，编号加一
        super(runnable, name + "-" + created.incrementAndGet());
        //定义如何处理未定义的异常处理器
        setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            public void uncaughtException(Thread t,
                                          Throwable e) {
                log.log(Level.SEVERE,
                        "UNCAUGHT in thread " + t.getName(), e);
            }
        });
    }

    public void run() {
        // 赋值Debug标志位；
        boolean debug = debugLifecycle;
        if (debug) log.log(Level.FINE, "Created " + getName());
        try {
	        //有任务被执行，活动线程数加一
            alive.incrementAndGet();
            super.run();
        } finally {
	        //线程执行完毕，活动线程数减一
            alive.decrementAndGet();
            if (debug) log.log(Level.FINE, "Exiting " + getName());
        }
    }

    public static int getThreadsCreated() {
        return created.get();
    }

    public static int getThreadsAlive() {
        return alive.get();
    }

    public static boolean getDebug() {
        return debugLifecycle;
    }

    public static void setDebug(boolean b) {
        debugLifecycle = b;
    }
}
```
该类中扩展了Thread的功能，比如为线程设置名字，设定异常处理器，以及维护一些统计信息等等。

## 3. 扩展ThreadPoolExecutor
ThreadPoolExecutor提供了可扩展的方法：
- beforeExecute: 在任务被执行之前被调用;
- afterExecute: 无论任务执行成功和还是抛出异常，都在返回后执行；如果任务执行中出现Error或是beforeExecute抛出异常，则afterExecutor不会被执行。
- terminated: 进程池完成之后被调用，可以用于释放进程池在生命周期内分配的各种资源和日志等工作。

在下面的例子中，其扩展ThreadPoolExecutor为进程池中加入日志功能：
```java
public class TimingThreadPool extends ThreadPoolExecutor {

    public TimingThreadPool() {
        super(1, 1, 0L, TimeUnit.SECONDS, null);
    }
	//任务开始时间
    private final ThreadLocal<Long> startTime = new ThreadLocal<Long>();
    //日志对象
    private final Logger log = Logger.getLogger("TimingThreadPool");
    //任务个数
    private final AtomicLong numTasks = new AtomicLong();
    //总时间
    private final AtomicLong totalTime = new AtomicLong();

    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
        //记录任务执行的开始时间
        log.fine(String.format("Thread %s: start %s", t, r));
        startTime.set(System.nanoTime());
    }

    protected void afterExecute(Runnable r, Throwable t) {
        try {
            long endTime = System.nanoTime();
            long taskTime = endTime - startTime.get();
            //原子性增长
            numTasks.incrementAndGet();
            totalTime.addAndGet(taskTime);
            //记录任务结束时间和执行时间长度
            log.fine(String.format("Thread %s: end %s, time=%dns",
                    t, r, taskTime));
        } finally {
            super.afterExecute(r, t);
        }
    }

    protected void terminated() {
        try {
            //统计整个进程池在执行期间的平均执行时间
            log.info(String.format("Terminated: avg time=%dns",
                    totalTime.get() / numTasks.get()));
        } finally {
            super.terminated();
        }
    }
}
```

## 4. 递归算法的并行化
现在来谈谈一个使用进程池的重要领域——递归算法的并行化。在解决实际问题中，递归是一种常见的思想，其中常常用到循环。如果每一次循环都是独立的且耗时得的，则可以将其并行化以提高效率。

```
// 顺序执行
void processSequentially(List<Element> elements) {
    for (Element e : elements)
        process(e);
}

// 并行化执行
void processInParallel(Executor exec, List<Element> elements) {
    for (final Element e : elements)
        exec.execute(new Runnable() {
            public void run() {
                process(e);
            }
        });
}
```
这种思想推而广之，如果递归在每一次的迭代中都是独立的，且不依赖后续迭代的结果，则也可以使用并行化的方式改写递归过程。以深度优先遍历树节点为例：

```

interface Node <T> {
     T compute();

     List<Node<T>> getChildren();
}


 public <T> void sequentialRecursive(List<Node<T>> nodes,
                                        Collection<T> results) {
        for (Node<T> n : nodes) {
            results.add(n.compute());
            sequentialRecursive(n.getChildren(), results);
        }
    }

    public <T> void parallelRecursive(final Executor exec,
                                      List<Node<T>> nodes,
                                      final Collection<T> results) {
        for (final Node<T> n : nodes) {
            exec.execute(new Runnable() {
                public void run() {
                    results.add(n.compute());
                }
            });
            parallelRecursive(exec, n.getChildren(), results);
        }
    }
```
需要注意的是，在迭代的过程中往往不清楚会有多少次迭代，因此进程池的大小是不确定的，所以要适应可扩展的进程池；同时因为涉及到多线程间的数据共享，结果集要使用多线程安全的数据结构。
```
public <T> Collection<T> getParallelResults(List<Node<T>> nodes)
        throws InterruptedException {
    ExecutorService exec = Executors.newCachedThreadPool(); //可伸缩的缓存进程池
    Queue<T> resultQueue = new ConcurrentLinkedQueue<T>(); //多线程安全的队列
    //并发执行任务
    parallelRecursive(exec, nodes, resultQueue);
    exec.shutdown();//平缓关闭，等待提交的任务结束
    exec.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS); //设置等待时间上限；
    return resultQueue;
}
```
由于迭代的过程时间难以估计，可以为其设定时间上限，如果超过时间上限则终止任务，以防止递归的过程中将资源消耗殆尽。
