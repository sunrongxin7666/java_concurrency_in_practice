#并发实战 13.  显式锁
@(Java并发)[java, 并发, jcip]

Java 5.0 加入了新的并发机制：**ReentrantLock**（重入锁），它和同步(*Synchronized*)方法的内置锁不同，这是一种**显式锁**。

## 1 Lock和ReentrantLock
>Lock作为显式锁，其提供了一种无条件的、可轮询和定时的、可中断的锁操作，其获得锁和释放锁的操作都是显示。

*Lock*是Java 5.0 中加入的接口，表示显式锁的功能，其接口定义如下：
```
public interface Lock {
    void lock(); //获取锁
    void lockInterruptibly() throws InterruptedException; //可中断的获取锁操作
    boolean tryLock(); //尝试获取锁，不会被拥塞，如果失败立刻返回
    boolean tryLock(long time, TimeUnit unit) throws InterruptedException; //在一定时间内尝试获得锁，如果超时则失败
    void unlock(); // 释放锁
    Condition newCondition();
}
```
前文中，我们已经讨论过，显式锁和同步代码块中的内置锁有着相同的互斥性和内存可见性。*ReentrantLock*是*Lock*的一种实现，提供对于线程的**重入机制**。和同步方法（*Synchronized*）相比，有着更强性能和灵活性。

虽然同步方法的内置锁已经很强大和完备了，但是在功能上还有一定的局限性：**不能实现非拥塞的锁操作**。比如不能提供响应中断的获得锁操作，不能提供支持超时的获得锁操作等等。因此，在某些情况下需要使用更为灵活的加锁方式，也就是显式锁。

在Java官方的注解中，给出了这样的代码示例：
```
 Lock l = new ReentrantLock();
 l.lock();
 try {
   // access the resource protected by this lock
 } finally {
   l.unlock();
 }
```
显式锁需要在手动调用*lock*方法来获得锁，并在使用后在*finally*代码块中调用*unlock*方法释放锁，以保证无论操作是否成功都能释放掉锁。

显式锁支持非拥塞的锁操作，具体的功能有：支持可轮询和定时的、以及可中断的锁获得操作。

### 1.1 轮询锁和定时锁
使用***tryLock***方法可以用于实现**轮询锁**和**定时锁**。和无条件的获得锁操作相比，*tryLock*方法具有更完善的错误恢复机制，可以避免死锁的放生。相比之下，同步方法发生死锁，其恢复方法就只能重新启动程序。

避免死锁的方式之一为打破“请求与保持条件”（死锁的四个条件），比如在要获得多个锁才能工作的情况下，如果不能获得全部的锁，就会释放掉已经持有的锁，一段时间之后再去重新尝试获得所有的锁。也就是说**要么获得所有锁，要么一个锁都不占有**。

下面的代码中以转账为例，演示了轮询锁的工作机制。
```
public class DeadlockAvoidance {
    private static Random rnd = new Random();

	// 转账
    public boolean transferMoney(Account fromAcct, //转出账户
                                 Account toAcct, //转入账户
                                 DollarAmount amount, //金额
                                 long timeout, //超时时间
                                 TimeUnit unit) 
            throws InsufficientFundsException, InterruptedException {
        long fixedDelay = getFixedDelayComponentNanos(timeout, unit);
        long randMod = getRandomDelayModulusNanos(timeout, unit);
        long stopTime = System.nanoTime() + unit.toNanos(timeout);

        while (true) {
            // 尝试获得fromAcct的锁
            if (fromAcct.lock.tryLock()) {
                try {
                    // 尝试获得toAcct的锁
                    if (toAcct.lock.tryLock()) {
                        try {
                            if  (fromAcct.getBalance().compareTo(amount) < 0) //余额不足
                                throw new InsufficientFundsException();
                            else { // 余额满足，转账
                                fromAcct.debit(amount);
                                toAcct.credit(amount);
                                return true;
                            }
                        } finally { //释放toAcct锁
                            toAcct.lock.unlock();
                        }
                    }
                } finally { //释放fromAcct锁
                    fromAcct.lock.unlock();
                }
            }
            // 获得锁失败
            // 判断是否超时 如果超时则立刻失败
            if (System.nanoTime() < stopTime)
                return false;

            // 如果没有超时，随机睡眠一段时间
            NANOSECONDS.sleep(fixedDelay + rnd.nextLong() % randMod);
        }
    }


    class Account {
        //显示锁
        public Lock lock;

        void debit(DollarAmount d) {
        }

        void credit(DollarAmount d) {
        }

        DollarAmount getBalance() {
            return null;
        }
    }

    class InsufficientFundsException extends Exception {
    }
}
```
只有同时获得转出账户和转入账户的锁后，才会进行转账。如果不能同时获得两个锁，就释放掉已经获得的锁，并随机随眠一段时间，再去尝试获得全部的锁，循环这个过程直到超时。

除了轮询申请获得锁之外，也可以使用带有时间限制的定时锁操作，即获得锁的操作具有时间限制，超过一定时间后仍没有获得锁就会返回失败。示例如下：

```
public class TimedLocking {
    private Lock lock = new ReentrantLock();

    public boolean trySendOnSharedLine(String message,
                                       long timeout, TimeUnit unit)
            throws InterruptedException {
        // 设定超时时间
        long nanosToLock = unit.toNanos(timeout)
                - estimatedNanosToSend(message);
        // 在规定时间内等待锁 否者就会返回false
        if (!lock.tryLock(nanosToLock, NANOSECONDS))
            return false;
        try {
            return sendOnSharedLine(message);
        } finally {
            lock.unlock();
        }
    }

    private boolean sendOnSharedLine(String message) {
        /* send something */
        return true;
    }

    long estimatedNanosToSend(String message) {
        return message.length();
    }
}
```

### 1.2 中断锁
如果要将显式锁应用到可以取消的任务重，就需要让获得锁的操作是支持中断。 ***lockInterruptibly***方法可以应用到这样情况中，其不仅能获得锁，还能保持对于中断的响应。
```
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
```
### 1.3 非块结构的加锁
在内置锁中，锁的获得和锁的释放都是在同一块代码的，这样简洁清楚还便于使用，不用考虑如何退出代码块。但是加锁的位置不一定只有代码块，比如之前谈过的*分段锁*。*ConcurrentHashMap*中利用了分段锁对散列表中的元素分段上锁，实现了并发访问容器元素的功能。如果是这种非块结构的加锁，就不能应用内置锁，而是需要使用显式锁控制。同样，链表类的容器可以应用分段锁，来支持并发访问不同链表元素。

## 2 性能因素考虑
前文中曾经提过，*ConcurrentHashMap*和同步的*HashMap*相比，其性能优势在于利用了分段锁对散列表中的元素分段上锁，故而支持并发访问容器中不同的元素。同理，和内置锁相比，显式锁都优势在于更好的性性。锁的实现方式越好，就越可以避免不必要的系统调用和上下文切换，以提高效率。

>线程间的切换，涉及线程挂起和恢复等一系列操作，这样的线程上下文的切换很是消耗性能，所以要避免不必要的线程切换。

Java 6中对内置锁的进行了优化，现在内置锁和显式锁相比性能已经很接近，只略低一些。

## 3. 公平锁
*ReentrantLock*的构造函数中提供两种锁的类型：
- **公平锁**：线程将按照它们请求锁的顺序来获得锁；
- **非公平锁**：允许插队，如果一个线程请求非公平锁的那个时刻，锁的状态正好为可用，则该线程将跳过所有等待中的线程获得该锁。

非公平锁在线程间竞争锁资源激烈的情况下，性能更高，这是由于：在恢复一个被挂起线程与该线程真正开始运行之间，存在着一个很**严重的延迟**，这是由于线程间上下文切换带来的。正是这个延迟，造成了公平锁在使用中出现CPU空闲。非公平锁正是将这个延迟带来的时间差利用起来，优先让正在运行的线程获得锁，避免线程的上下文切换。

如果每个线程获得锁的时间都很长，或者请求锁的竞争很稀疏或不频繁，则公平锁更为适合。

内置锁和显式锁都是默认使用非公平锁，但是显式锁可以设置公平锁，内置锁无法做到。



## 4. 同步方法和显式锁的选择
显式锁虽然更为灵活，提供更为丰富的功能，且性能更好，但是还是推荐先使用同步（*Synchronized*）方法，这是因为同步方法的内置锁，使用起来更为方便，简洁紧凑 ，还便于理解，也更为开发人员所熟悉。

>建议只有在一些内置锁无法满足的情况下，再将显式锁*ReentrantLock*作为高级工具使用，比如要使用轮询锁、定时锁、可中断锁或者是公平锁。除此之外，还应该优先使用*synchronized*方法。

## 5. 读-写锁
无论是显式锁还是内置锁，都是互斥锁，也就是同一时刻只能有一个线程得到锁。互斥锁是保守的加锁策略，可以避免“写-写”冲突、“写-读”冲突”和"读-读"冲突。但是有时候不需要这么严格 ，同时多个任务读取数据是被允许，这有助于提升效率，不需要避免“读-读”操作。为此，Java 5.0 中出现了读-写锁*ReadWriteLock*。

*ReadWriteLock*可以提供两种锁：
- 读锁*readLock*：允许多个线程同时执行读操作，但是同时只能有一个线程执行写操作；
- 写锁*writeLock*：正常的互斥锁，同一时刻只能有一个线程执行读写操作。

*ReentrantReadWriteLock*是读写锁支持重入的实现，下面的例子中利用读写锁实现了支持并发读取元素的多线程安全Map:
```
public class ReadWriteMap <K,V> {
    private final Map<K, V> map;
    // 读写锁
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    // 读锁
    private final Lock r = lock.readLock();
    // 写锁
    private final Lock w = lock.writeLock();

    public ReadWriteMap(Map<K, V> map) {
        this.map = map;
    }

    public V put(K key, V value) {
        w.lock();
        try {
            return map.put(key, value);
        } finally {
            w.unlock();
        }
    }
    
    public V get(Object key) {
        r.lock();
        try {
            return map.get(key);
        } finally {
            r.unlock();
        }
    }
    .....
}
```
不过需要注意的是，虽然读写锁的出现是为了提高效率，但只适用于对多线程频繁并发执行读操作的情况。如果是在正常的情况下使用读写锁，反而会降低效率，因为*ReadWriteLock*需要额外的开销维护分别维护读锁和写锁，得不偿失。
