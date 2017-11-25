#并发实战 15.  原子变量和非阻塞同步机制
@(Java并发)[java, 并发, jcip]

>今年以来，并发算法领域的重点都围绕在**非拥塞算法**，该种算法依赖底与层硬件对于原子性指令的支持，避免使用锁来维护数据一致性和多线程安全。非拥塞算法虽然在设计上更为复杂，但是拥有更好的可伸缩性和性能，被广泛应用于实现计数器、序列发生器和统计数据收集器等
>
## 1. 锁的劣势
前文中曾经对比同步方法的内置锁相比和显式锁，来说明它们各自的优势，但是无论是内置说还是显式锁，其本质都是通过上锁来维护多线程安全。

由于加锁机制，线程在申请锁和等待锁的过程中，必然会造成线程的挂起和恢复，这样的线程上线文间切换会带来很大的资源开销，尤其是在锁资源竞争激烈的情况下。

同时，线程在等待锁的过程中，因为阻塞而什么也做，无限条件的等待不仅性能效率不佳，同时也容易造成死锁。

## 2. 悲观锁和乐观锁
无论是内置锁还是显式锁，都是一种独占锁，也是**悲观锁**。所谓悲观锁，就是以悲观的角度出发，认为如果不上锁，一定会有其他线程修改数据，破坏一致性，影响多线程安全，所以必须通过加锁让线程独占资源。

与悲观锁相对，还有更高效的方法——**乐观锁**，这种锁需要借助**冲突检查机制**来判断在更新的过程中是否存在来气其他线程的干扰，如果没有干扰，则操作成功，如果存在则操作失败，并且可以重试或采取其他策略。换而言之，**乐观锁**需要原子性“读-改-写”指令的支持，来读取数据是否被其他线程修改，改写数据内容并见最新的数据写回到原有地址。现在大部分处理器以及可以支持这样的操作。

## 3. 比较并交换操作CAS
大部分处理器框架是通过实现比较并交换（Compare and Swap，CAS）指令来实现乐观锁。CAS指令包含三个操作数：需要读写的内存位置V，进行比较的值A和拟写入新值B。当且仅当V处的值等于A时，才说明V处的值没有被修改过，指令才会使用原子方式更新其为B值，否者将不会执行任何操作。无论操作是否执行， CAS都会返回V处原有的值。下面的代码模仿了CAS的语义。
```
public class SimulatedCAS {
    @GuardedBy("this") private int value;

    public synchronized int get() {
        return value;
    }

    // CAS = compare and swap
    public synchronized int compareAndSwap(int expectedValue,
                                           int newValue) {
        int oldValue = value;
        if (oldValue == expectedValue)
            value = newValue;
        return oldValue;
    }

    public synchronized boolean compareAndSet(int expectedValue,
                                              int newValue) {
        return (expectedValue
                == compareAndSwap(expectedValue, newValue));
    }
}
```
当多个线程尝试更新同一个值时，只会有一个线程成功，其他线程都会失败，但是在CAS中，失败的线程不会被拥塞，可以自主定义失败后该如何处理，是重试还是取消操作，更具有灵活性。

通常CAS的使用方法为，
```
public class CasCounter {
    private SimulatedCAS value;

    public int getValue() {
        return value.get();
    }

    public int increment() {
        int v;
        do {
            // 获得当前的值
            v = value.get();
        } while (v != value.compareAndSwap(v, v + 1));
        // 如果返回值不同，则说明更新成功了
        return v + 1;
    }
}
```
4. 原子变量
一种更好volatile变量
5. 非阻塞算法
5.1 非阻塞的栈
5.2 非阻塞的链表
 




```
public class CasNumberRange {
    @Immutable
    private static class IntPair {
        // INVARIANT: lower <= upper
        final int lower;
        final int upper;

        public IntPair(int lower, int upper) {
            this.lower = lower;
            this.upper = upper;
        }
    }

    
    //源自引用 IntPair 初始化为[0，0]
    private final AtomicReference<IntPair> values =
            new AtomicReference<IntPair>(new IntPair(0, 0));

    public int getLower() {
        return values.get().lower;
    }

    public int getUpper() {
        return values.get().upper;
    }

    //设置下限
    public void setLower(int i) {
        while (true) {
            IntPair oldv = values.get();
            // 如果下限设置比当前上限还要大
            if (i > oldv.upper)
                //抛出异常
                throw new IllegalArgumentException("Can't set lower to " + i + " > upper");
            IntPair newv = new IntPair(i, oldv.upper);
            //原子性更新
            if (values.compareAndSet(oldv, newv))
                return;
        }
    }

    //设置上限 过程和setLower类似
    public void setUpper(int i) {
        while (true) {
            IntPair oldv = values.get();
            if (i < oldv.lower)
                throw new IllegalArgumentException("Can't set upper to " + i + " < lower");
            IntPair newv = new IntPair(oldv.lower, i);
            if (values.compareAndSet(oldv, newv))
                return;
        }
    }
}
```

```
//非阻塞的并发栈
public class ConcurrentStack <E> {
    AtomicReference<Node<E>> top = new AtomicReference<Node<E>>();

    public void push(E item) {
        Node<E> newHead = new Node<E>(item);
        Node<E> oldHead;
        do {
            oldHead = top.get();
            newHead.next = oldHead;
        } while (!top.compareAndSet(oldHead, newHead));
    }

    public E pop() {
        Node<E> oldHead;
        Node<E> newHead;
        do {
            oldHead = top.get();
            if (oldHead == null)
                return null;
            newHead = oldHead.next;
        } while (!top.compareAndSet(oldHead, newHead));
        return oldHead.item;
    }

    private static class Node <E> {
        public final E item;
        public Node<E> next;

        public Node(E item) {
            this.item = item;
        }
    }
}
```

```
public class LinkedQueue <E> {

    private static class Node <E> {
        final E item;
        //下一个节点
        final AtomicReference<Node<E>> next;

        public Node(E item, Node<E> next) {
            this.item = item;
            this.next = new AtomicReference<Node<E>>(next);
        }
    }

    //哑结点 也是头结点
    private final Node<E> dummy = new Node<E>(null, null);
    private final AtomicReference<Node<E>> head
            = new AtomicReference<Node<E>>(dummy);
    //尾部节点
    private final AtomicReference<Node<E>> tail
            = new AtomicReference<Node<E>>(dummy);

    public boolean put(E item) {
        Node<E> newNode = new Node<E>(item, null);
        while (true) {
            Node<E> curTail = tail.get();
            Node<E> tailNext = curTail.next.get();
            //得到尾部节点
            if (curTail == tail.get()) {
                // 1. 尾部节点的后续节点不为空，则队列处于不一致的状态
                if (tailNext != null) {
                    // 2. 将为尾部节点向后退进；
                    tail.compareAndSet(curTail, tailNext);
                } else {
                    // 3. 尾部节点的后续节点为空，则队列处于一致的状态，尝试更新
                    if (curTail.next.compareAndSet(null, newNode)) {
                        // 4. 更新成功，将为尾部节点向后退进；
                        tail.compareAndSet(curTail, newNode);
                        return true;
                    }
                }
            }
        }
    }
}
```

```java
AtomicReferenceFieldUpdater
    /**
    * @param tclass the class of the objects holding the field
     * @param vclass the class of the field
     * @param fieldName the name of the field to be updated
     */
    public static <U,W> AtomicReferenceFieldUpdater<U,W> newUpdater(Class<U> tclass,
                                                                    Class<W> vclass,
                                                                    String fieldName) {
        return new AtomicReferenceFieldUpdaterImpl<U,W>
            (tclass, vclass, fieldName, Reflection.getCallerClass());
    }
AtomicStampedReference
    public AtomicStampedReference(V initialRef, int initialStamp) {
        pair = Pair.of(initialRef, initialStamp);
    }
```
