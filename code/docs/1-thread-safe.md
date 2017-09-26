要编写多线程安全的代码，最关键的一点就是需要对于**共享的**和**可变的**状态进行访问控制：
- 所谓共享的，指的是该变量可能同时被多个线程访问；
- 所谓可变的，指的是该变量在生命周期内其值可能放生变化。

如果在多线程同时访问一个共享可变的状态变量，但是没有进行有效的访问控制的话，那么程序的运行就可能带来意料之外的错误。为了解决这个问题，有以下三种办法：
1. 不在线程中共享该状态变量（很可惜，不可能完全避免，但是能尽量减少不必要的共享）。
2. 共享的变量设置为不可变状态（简单而有效，不可变的对象天然就是多线程安全的，比如String和BigInteger）。
3. 访问状态变量时使用同步机制（这是并发编程的重点）。

大部分情况下，讨论的多线程安全都是关于访问共享可变的状态变量，因此都不得不涉及到数据同步机制，但是在具体讨论数据同步的访问控制之前，我们需要先讨论一个问题，什么是多线程安全？

## 1. 多线程安全性
多线程安全性的定义可能众说纷纭，但是其最核心的一点就是**正确性**，也就是程序的行为结果和预期一致。
>当多个线程访问某个类时，不管运行环境采用何种线程调度算法或者这些线程如何交替执行，且不需要在主程序中添加任何额外的协同机制，这个类都能表现出正确的行为，那么这个类就是**线程安全**的。——《Java Concurrency in Practice》

这里需要额外注意下，一般都是把必要的同步机制封装在类中，让使用该类的客户端无需考虑多线程的问题。

当然也不一定需要同步机制才能保持多线程安全，比如一个类是无状态的：

```java
public class StatelessFactorizer extends GenericServlet implements Servlet {

    public void service(ServletRequest req, ServletResponse resp) {
        BigInteger i = extractFromRequest(req);
        BigInteger[] factors = factor(i);
        encodeIntoResponse(resp, factors);
    }

    void encodeIntoResponse(ServletResponse resp, BigInteger[] factors) {
    }

    BigInteger extractFromRequest(ServletRequest req) {
        return new BigInteger("7");
    }

    BigInteger[] factor(BigInteger i) {
        // Doesn't really factor
        return new BigInteger[] { i };
    }
}
```
这是一个最简单的网络服务，提供因式分解的功能。该服务是状态无关的，即使再多的请求同时处理，也不会相互影响。

## 2. 原子性
如何确保多线程安全呢？简单说就是让对于共享可变的状态变量的访问操作都是**原子性**的，也就是不可分隔的。

```
public class UnsafeCountingFactorizer extends GenericServlet implements Servlet {
    private long count = 0;

    public long getCount() {
        return count;
    }

    public void service(ServletRequest req, ServletResponse resp) {
        BigInteger i = extractFromRequest(req);
        BigInteger[] factors = factor(i);
        //Not Thread-Safe
        ++count;
        encodeIntoResponse(resp, factors);
    }
    ......
}
```
稍有多线程编程经验的人，都是知道上面的代码不是多线程安全的。当使用count变量来记录服务被调用的次数时，该类就变成有状态的了。但是自增长++操作不是原子性的，其可以分解为读取数值，增加数值和回写数值，其中每一步都是可以被打断和暂停的，多线程访问count变量是就可能会造成问题。这是一种由于不恰当的执行顺序而造成的多线程错误，被称为**竞态条件**。

竞态条件多是由于“先检查后执行”，也就是先去检查一个值的状态，根据这个状态再去执行响应的动作。但是在多线程中，读取这个值的状态后，该值就可能被其他线程修改了，因而失效。

要解决以上问题，就需要将一组操作组成一个原子性的复合操作。在复合操作没有执行完之前，该操作过程不能被打断。

```
public class CountingFactorizer extends GenericServlet implements Servlet {
    private final AtomicLong count = new AtomicLong(0);

    public long getCount() { return count.get(); }

    public void service(ServletRequest req, ServletResponse resp) {
        BigInteger i = extractFromRequest(req);
        BigInteger[] factors = factor(i);
        //Thread-Safe
        count.incrementAndGet();
        encodeIntoResponse(resp, factors);
    }
}
```
比如上面的代码使用原子类**AtomicLong**的**incrementAndGet**方法保证自增长是原子性的。

## 3. 加锁机制
如果多线程中的共享状态变量有多个，该如何处理呢？只靠每个变量为原子类型是不够的，还需要把所有状态变量之间的操作都设置成原子性的才行。
> 多线程安全要求在一个原子性操作中更新所有相关状态的变量。

这样的要求在Java中可以使用内置锁和同步代码块来实现。

```
synchronized (lock){
	 // doing someting;
}
```
每个对象内部都会有一个内置锁，当进入同步代码块时，对象的内置锁就会被自动获得，在退出同步代码块（包括抛出异常）都会自动释放内置锁。同步代码块中的程序，将会保证是原子性的，这是因为内置锁是一种互斥锁，每次只能有一个线程获得该锁，从而保证多线程之间相互不干扰。

需要说明的是，内置锁提供**重入**机制，也就是说如果当前线程已经获得某个对象的内置锁，当它再去请求该锁时也会成功，这就代表着内置锁的操作粒度是线程，而不是调用。

内置锁的重入机制设计有着良苦用心，尤其是在继承父类代码中同步操作时，比如：
```java
public class A{
	public synchronized void function(){
	}
}

public class B extends A{
	public synchronized void funtcion(){
		super.function();
		//......
	}
}
```
如果没有重入机制，当B类中的function已经获得内置锁之后，再去调用父类中同步方法function，就会因内置锁未获得而等待，但是外部的子类的方法因内部父类的方法被拥塞也一直都不能释放内置锁，故而产生死锁。

## 4. 用内置锁来保护状态
锁的出现，让并行执行的代码路径出现了**必要的串行**。不过需要注意的是，如果使用锁来控制某个变量的访问，对于该变量的所有访问位置上都需要加入锁。

>每个共享可变的变量，都应该只有一个锁来保护。如果由多个变量协同完成操作，则这些变量应该由同一个锁来保护。

在设置同步代码块时，应该*避免同步控制的滥用*。最极端的例子就是全部代码都在同步代码块中，这样虽然是多线程安全的，但是会造成所有线程变为串行，丧失了并发的意义。

为了提高性能，我们应该尽可能缩小同步代码块的范围，只在需要的情况下使用。比如这样：

```
public class CachedFactorizer extends GenericServlet implements Servlet {
    @GuardedBy("this") private BigInteger lastNumber;
    @GuardedBy("this") private BigInteger[] lastFactors;
    @GuardedBy("this") private long hits;
    @GuardedBy("this") private long cacheHits;

    public synchronized long getHits() {
        return hits;
    }

    public synchronized double getCacheHitRatio() {
        return (double) cacheHits / (double) hits;
    }

    public void service(ServletRequest req, ServletResponse resp) {
        BigInteger i = extractFromRequest(req);
        BigInteger[] factors = null;
        synchronized (this) {
            ++hits;
            if (i.equals(lastNumber)) {
                ++cacheHits;
                factors = lastFactors.clone();
            }
        }
        if (factors == null) {
            factors = factor(i);
            synchronized (this) {
                lastNumber = i;
                lastFactors = factors.clone();
            }
        }
        encodeIntoResponse(resp, factors);
    }

    void encodeIntoResponse(ServletResponse resp, BigInteger[] factors) {
    }
}
```
如何选取合适的同步代码块范围，需要基于实际需求，在简单性和并发性之间平衡。

在同一个代码块中最好**只使用一种同步机制**，这样便于维护。另外，不要在同步代码块中进行**耗时操作**，这样对于性能是很大的消耗。