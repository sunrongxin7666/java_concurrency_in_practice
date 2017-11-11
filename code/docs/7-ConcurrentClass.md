#并发实战 5.  基础构建模块
@(Java并发)[java, 并发, jcip]
>本章节将为大家介绍Java库所提供的丰富并发基础模块，这些多线程安全的模块作为并发工具类将帮助大家应对并发开发的各种需求。
>
## 1. 同步容器类
同步容器类的代表就是*Vector*和*HashTable*，这是早期JDK中提供的类。此外*Collections.synchronizedXXX*等工厂方法也可以把普通的容器（如HashMap）封装成同步容器。这些同步容器类的共同点就是：使用**同步**（*Synchronized*）方法来封装容器的操作方法，以保证容器多线程安全，但这样也使得容器的每次操作都会对整个容器上锁，所以同一时刻**只能有一个线程**访问容器。

### 1.1 同步容器的复合操作问题
同步容器类虽然对于单一操作是线程安全的，但是对于复合操作（即由多个操作组合而成，如迭代，跳转），就不一定能保证线程安全。如下面的代码：
```
public class UnsafeVectorHelpers {
    //复合操作，先检查再运行，并不是经常安全的，
    public static Object getLast(Vector list) {
        int lastIndex = list.size() - 1;
        return list.get(lastIndex);
    }
}
```
*getLast*方法中存在“先检查再运行”的情况：先去获得容器大小，再去获得容器中最后一个元素。虽然这两个操作单独都是同步的，但是复合在一起并不能保证整个方法的原子性，所以还需要额外的同步操作。线程安全的代码如下：
```
public class SafeVectorHelpers {
    public static Object getLast(Vector list) {
	    //额外的同步操作
        synchronized (list) {
            int lastIndex = list.size() - 1;
            return list.get(lastIndex);
        }
    }
}
```
### 1.2 同步容器类与迭代器
正因为同步容器类没有解决复合操作的线程安全问题，所以在使用迭代器时，其也不能避免迭代器被修改。甚至同步容器类的迭代器在设计时就没有考虑并发修改的问题，而是采用快速失败（*fail-fast*）的处理方法，即在容器迭代的过程中，发现容器被修改了，就抛出异常*ConcurrentModificationException*。

虽然也可以通过给容器上锁解来决迭代器被并发修改的问题，但是这样做也会带来性能问题：如果迭代的过程很费事，其他访问容器的操作都会被拥塞。

除此之外，一些**隐式调用迭代器**的情况让同步容器的使用情况更为复杂。
```
public class HiddenIterator {
    //应该使用并发容器
    @GuardedBy("this") private final Set<Integer> set = new HashSet<Integer>();

    public synchronized void add(Integer i) {
        set.add(i);
    }

    public synchronized void remove(Integer i) {
        set.remove(i);
    }

    public void addTenThings() {
        Random r = new Random();
        for (int i = 0; i < 10; i++)
            add(r.nextInt());
        // 隐式地调用了迭代器，
        // 连接字符串操作会调用StringBuilder.append(Object),
        // 而这个方法又会调用容器Set的toString(),
        // 标准容器（不仅是Set）的ToString方法会使用迭代器依次使用容器内元素的toString方法。
        System.out.println("DEBUG: added ten elements to " + set);
    }
}
```
注释中已经解释了容器的*toString()*方法是如何迭代调用容器元素的*toString*方法。同样的，容器的*hashCode*和*equals*方法都是隐式调用迭代器。
## 2. 并发容器
从Java 5开始，JDK中提供了并发容器类来改进同步容器类的不足。Java 5 中提供了*ConcurrentHashMap*来代替同步的HashMap，提供了*CopyOnWriteArrayList*来代替同步都是List。

Java 6 中又继续引入了*ConcurrentSkipListMap*和*ConcurrentSkipLIstSet*来分别代替同步的*SortedMap*和*SortedList*
>并发容器并不对整个容器上锁，故而允许多个线程同时访问容器，改进了同步容器因串行化而效率低的问题。

### 2.1 ConcurrentHashMap
*ConcurrentHashMap*也是基于散列的Map，但是并不是在操作的过程中对整个容器上锁，而是使用一种粒度更细的锁，即**分段锁**。
> 在ConcurrentHashMap的实现中，其使用了16锁来分段保护容器，每个锁保护着散列表的1/16，其第N个散列桶的位置由第（N mod 16）个锁来保护。如果访问的元素不是由同一个锁来保护，则允许并发被访问。这样做虽然增加了维护和管理的开销，但是提高并发性。不过，ConcurrentHashMap中也存在对整个容器加锁的情况，比如容器要扩容，需要重新计算所有元素的散列值， 就需要获得全部的分段锁。

*ConcurrentHashMap*所提供的迭代器也不会抛出*ConcurrentModificationException*异常，所以不需要为其加锁。并发容器的迭代器具有弱一致性（Weakly Consistent）,容忍并发的修改，可以（但是不保证）将迭代器上的修改操作反映给容器。

需要注意的是，为了提高对元素访问的并发性，*ConcurrentHashMap*中对容器整体操作的语义被消弱，比如size和isEmpty等方法，其返回的结果都是估计值，可能是过期的。

### 2.2 CopyOnWriteArrayList
*CopyOnWriteArrayList*用于代替同步的List，其为“写时复制（Copy-on-Write）”容器，本质为事实不可变对象，一旦需要修改，就会创建一个新的容器副本并发布。容器的迭代器会保留一个指向底层基础数组的引用，这个数组是不变的，且其当前位置位于迭代器的起始位置。

由于每次修改CopyOnWriteArrayList都会有容器元素复制的开销，所以其更适合迭代操作远远多于修改操作的使用场景中。

### 2.3 拥塞队列
Java 5 还新增了两种容器类型：*Queue*和*BlockingQueue*：
- 队列Queue，其实现有*ConcurrentLinkedQueue*（并发的先进先出队列）和*PriorityQueue*（非并发的优先级队列）；*Queue*上的操作不会被拥塞，如果队列为空 ，会立刻返回null，如果队列已满，则会立刻返回失败；
- 拥塞队列BlockingQueue，是Queue的一种扩展，其上的操作是可拥塞的：如果队列为空，则获取元素的操作将被拥塞直到队列中有可用元素，同理如果队列已满，则放入元素的操作也会被用塞到队列有可用的空间。

队列的相关内容在前文中已经介绍过了，这里不再展开。

此外Java 6 还提供了**双端队列** *Deque*和*BlockingDeque*，即队列头尾都可以都可以插入和移除元素。双端队列适用于一种特殊的生产者-消费者模式——**密取模式**：即每个消费者都有一个双端队列，当自己队列中的元素被消费完之后，就可以秘密地从别的消费者队列的末端取出元素使用。

## 3. 同步工具类
Java中还提供了**同步工具类**，这些同步工具类可以根据自身的状态来协调线程的控制流，上面提到的拥塞队列就是一种同步工具类，除此之外还有**闭锁（Latch）**，**信号量（Semaphore）**和**栅栏（Barrier）**等
### 3.1 闭锁
闭锁是一种同步工具类 ，可以延迟线程的进度直到其到达终止状态。闭锁的作用就像一扇门：在闭锁到达结束状态之前，这扇门处于关闭状态，所有的线程都不能通过；当闭锁达到终止状态后，这扇门打开，所有线程都可以通过。闭锁一旦到达终止状态后，其状态就不会再被改变。

闭锁可以用来保证一些活动在其所依赖的活动执行完毕之后再继续执行，如等待资源初始化，等待依赖的服务完毕等等。

**CountDownLatch**是闭锁的一种实现，其包括一个计数器，其被初始化为一个正整数，表示要等到事件数量。*countDown*方法表示一个事件已经放生了，*await*方法表示等到闭锁达到终止状态（拥塞方法，支持中断和超时）。

下面是一个使用闭锁的实例，来实现任务计时功能：
```
public class TestHarness {
    public long timeTasks(int nThreads, final Runnable task)
            throws InterruptedException {
        // 开始锁
        final CountDownLatch startGate = new CountDownLatch(1);
        // 结束锁
        final CountDownLatch endGate = new CountDownLatch(nThreads);

        for (int i = 0; i < nThreads; i++) {
            Thread t = new Thread() {
                public void run() {
                    try {
                        // 等待主线程初始化完毕
                        startGate.await();
                        try {
                            task.run();
                        } finally {
                            // 结束锁释放一个
                            endGate.countDown();
                        }
                    } catch (InterruptedException ignored) {
                    }
                }
            };
            t.start();
        }

        // 记录当前时间为开始时间
        long start = System.nanoTime();
        // 初始化完毕，开启开始锁，子线程可以运行
        startGate.countDown();
        // 等到个子线程运行完毕
        endGate.await();
        // 统计执行时间
        long end = System.nanoTime();
        return end - start;
    }
}
```
### 3.2 FutureTask
之前讨论过的*FutureTask*其实也可以作为闭门使用，*Future.get*方法会被拥塞直到对应的任务完成。

下面的例子中使用*FutureTask*来等到预加载任务的完成。
```
public class Preloader {
    ProductInfo loadProductInfo() throws DataLoadException {
        return null;
    }

    //FutureTask 实现了Runnable和Future
    private final FutureTask<ProductInfo> future =
        new FutureTask<ProductInfo>(new Callable<ProductInfo>() {
            public ProductInfo call() throws DataLoadException {
                return loadProductInfo();
            }
        });
    private final Thread thread = new Thread(future);

    //预先开始加载任务
    public void start() { thread.start(); }

    public ProductInfo get()
            throws DataLoadException, InterruptedException {
        try {
	        //等待任务完成
            return future.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            //已知异常
            if (cause instanceof DataLoadException)
                throw (DataLoadException) cause;
            else //未知异常
                throw LaunderThrowable.launderThrowable(cause);
        }
    }

    interface ProductInfo {
    }
}

//自定义的异常类型
class DataLoadException extends Exception { }
```
### 5.3 信号量
*Semaphore*是信号量的实现，用来控制的特定资源的操作数，也就是一组虚拟的资源许可：得到资源的同时获得信号量，使用完资源时释放信号量，如果当前没有可用信号量就得等待。如果是二值信号量，也就是一种互斥锁。

下面的例子使用信号量将普通的容器变为有界阻塞的容器
```
public class BoundedHashSet <T> {
    private final Set<T> set;
    // 信号量
    private final Semaphore sem;

    public BoundedHashSet(int bound) {
        // 获得同步容器
        this.set = Collections.synchronizedSet(new HashSet<T>());
        sem = new Semaphore(bound);
    }

    public boolean add(T o) throws InterruptedException {
        // 请求获得信号量，可能拥塞
        sem.acquire();
        boolean wasAdded = false;
        try {
            wasAdded = set.add(o);
            return wasAdded;
        } finally {
            if (!wasAdded)
                // 无论添加操作是否成功，都释放信号量
                sem.release();
        }
    }

    public boolean remove(T o) {
        boolean wasRemoved = set.remove(o);
        // 移除成功之后，会释放一个信号量
        if (wasRemoved)
            sem.release();
        return wasRemoved;
    }
}
```
### 5.3 栅栏
栅栏（Barrier）和闭锁是类似的，能拥塞一种线程直到某个事件的发生，只有当所有的线程都达到栅栏的位置，才能继续执行。栅栏用于等待其他线程，而闭锁用于等待某个事件。

栅栏的使用场景类似于“明天早上八点，所有人学校操场集合（栅栏），然后再去春游”。

CyclicBarrier是栅栏的一种实现，其可以让一定数量的参与方反复在栅栏的位置汇聚，其*await*方法表示某个方法到达栅栏。这个模型在并行迭代算法中很有意思，以下是**《java concurrency in practive》**中给出的使用范例。
```
public class CellularAutomata {
    private final Board mainBoard;
    //栅栏
    private final CyclicBarrier barrier;
    //子任务
    private final Worker[] workers;

    public CellularAutomata(Board board) {
        this.mainBoard = board;
        //环境中CPU的个数
        int count = Runtime.getRuntime().availableProcessors();
        this.barrier = new CyclicBarrier(count,
                new Runnable() {
                    public void run() {
                        //当所有子任务完成，更新数值
                        mainBoard.commitNewValues();
                    }});
        this.workers = new Worker[count];
        //划分子任务；
        for (int i = 0; i < count; i++)
            workers[i] = new Worker(mainBoard.getSubBoard(count, i));
    }

    private class Worker implements Runnable {
        private final Board board;

        public Worker(Board board) { this.board = board; }
        public void run() {
            while (!board.hasConverged()) {
                for (int x = 0; x < board.getMaxX(); x++)
                    for (int y = 0; y < board.getMaxY(); y++)
                        //设置当前子任务的结果
                        board.setNewValue(x, y, computeValue(x, y));
                try {
                    //完成计算，等待其他任务完成
                    barrier.await();
                } catch (InterruptedException ex) {
                    return;
                } catch (BrokenBarrierException ex) {
                    return;
                }
            }
        }

        private int computeValue(int x, int y) {
            // Compute the new value that goes in (x,y)
            return 0;
        }
    }

    public void start() {
        for (int i = 0; i < workers.length; i++)
            new Thread(workers[i]).start();
        mainBoard.waitForConvergence();
    }

    interface Board {
        int getMaxX();
        int getMaxY();
        int getValue(int x, int y);
        int setNewValue(int x, int y, int value);
        void commitNewValues();
        boolean hasConverged();
        void waitForConvergence();
        Board getSubBoard(int numPartitions, int index);
    }
}
```
要说明的是，CyclicBarrier的构造器中可以传进一个Runnable对象，表示当所有线程到达栅栏之后要执行什么任务。


栅栏的一种特殊形式是***Exchange***，它是一种**两方栅栏**（Two-party Barrier） ，双方会在栅栏处交换数据，这是一种线程间安全交互数据的方法。具体交换数据的时机取决于程序的响应需求，最简单的方案为：当缓冲区被填满时，由填充任务进行数据交换；当缓冲区为空时，由读取任务交换数据。这样的模型在双方执行不对等操作时很有用，比如一个任务向缓冲区A写数据，另一个从缓冲区B读数据，然后使用Exchange来汇合两个任务，将被写满或是被读空的缓冲区相互交换。


### 5.4 实例：高效的结果缓存

最后展示一个并发容器类的使用实例：计算结果缓存，即将已经计算完的结果保存起来，如果调用有缓存的计算结果，则直接返回，如果没有缓存再进行计算。

以下是同步方法的实现方式：
```
public class Memoizer1 <A, V> implements Computable<A, V> {
    @GuardedBy("this") private final Map<A, V> cache = new HashMap<>();
    private final Computable<A, V> c;

    public Memoizer1(Computable<A, V> c) {
        this.c = c;
    }

    // 该方法对整个容器上锁，如果容器过大可能导致操作时间比没有缓存的情况更久
    // 建议使用并发容器；
    public synchronized V compute(A arg) throws InterruptedException {
        V result = cache.get(arg);
        if (result == null) {
            result = c.compute(arg);
            cache.put(arg, result);
        }
        return result;
    }
}
```
由于同步方法是对整个容器上锁，所以并发的效率不好，因此要使用并发容器作为计算结果的缓存，改进代码如下：
```
public class Memoizer2 <A, V> implements Computable<A, V> {
    private final Map<A, V> cache = new ConcurrentHashMap<>();
    private final Computable<A, V> c;

    public Memoizer2(Computable<A, V> c) {
        this.c = c;
    }

    // cache是并发容器，支持多线程同时访问，
    // 但是不能表示出某个结果正在被计算
    public V compute(A arg) throws InterruptedException {
        V result = cache.get(arg);
        if (result == null) {
            result = c.compute(arg);
            cache.put(arg, result);
        }
        return result;
    }
}
```
这样代码的并发效率就可以被大大提升了。不过这样使用并发容器类还有一点小问题：缓存仅仅记录下那些结果被计算出来，但是不能反映出那些结果正在被计算，如果计算的过程很漫长，也会照成重复计算，而浪费大量时间。这时就可以使用*Future*来表示任务的生命周期，存进缓存中。完善的代码如下：
```
public class Memoizer <A, V> implements Computable<A, V> {
	// 记录那些结果的计算已经开始
    private final ConcurrentMap<A, Future<V>> cache
            = new ConcurrentHashMap<>();
    private final Computable<A, V> c;

    public Memoizer(Computable<A, V> c) {
        this.c = c;
    }

    public V compute(final A arg) throws InterruptedException {
        while (true) {
            Future<V> f = cache.get(arg);
            if (f == null) { // 没有缓存结果，添加计算结果
                Callable<V> eval = new Callable<V>() {
                    public V call() throws InterruptedException {
                        return c.compute(arg);
                    }
                };
                FutureTask<V> ft = new FutureTask<>(eval);
                // 如果不存在缓存则提交任务， 
                // 如果已经缓存则得到缓存值;
                f = cache.putIfAbsent(arg, ft);
                if (f == null) { 不存在缓存结果
                    f = ft;
                    ft.run(); //开始计算
                }
            }
            try {
            	// 获得计算结果，如果已经计算完毕，则会立刻返回
	            // 如果计算还在进行中，就会拥塞
                return f.get(); 
            } catch (CancellationException e) {
                cache.remove(arg, f);
            } catch (ExecutionException e) {
                throw LaunderThrowable.launderThrowable(e.getCause());
            }
        }
    }
}
```






