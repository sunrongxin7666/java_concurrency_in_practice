#并发实战 15.  原子变量和非阻塞同步机制
@(Java并发)[java, 并发, jcip]

>今年以来，并发算法领域的重点都围绕在**非拥塞算法**，该种算法依赖底层硬件对于原子性指令的支持，避免使用锁来维护数据一致性和多线程安全。非拥塞算法虽然在设计上更为复杂，但是拥有更好的可伸缩性和性能，被广泛应用于实现计数器、序列发生器和统计数据收集器等
>
## 1. 锁的劣势
前文中曾经对比同步方法的内置锁相比和显式锁，来说明它们各自的优势，但是无论是内置说还是显式锁，其本质都是通过加锁来维护多线程安全。

由于加锁机制，线程在申请锁和等待锁的过程中，必然会造成线程的挂起和恢复，这样的线程上线文间切换会带来很大的资源开销，尤其是在锁资源竞争激烈的情况下。

同时，线程在等待锁的过程中，因为阻塞而什么也做，无限条件的等待不仅性能效率不佳，同时也容易造成死锁。

## 2. 悲观锁和乐观锁
无论是内置锁还是显式锁，都是一种独占锁，也是**悲观锁**。所谓悲观锁，就是以悲观的角度出发，认为如果不上锁，一定会有其他线程修改数据，破坏一致性，影响多线程安全，所以必须通过加锁让线程独占资源。

与悲观锁相对，还有更高效的方法——**乐观锁**，这种锁需要借助**冲突检查机制**来判断在更新的过程中是否存在来气其他线程的干扰，如果没有干扰，则操作成功，如果存在干扰则操作失败，并且可以重试或采取其他策略。换而言之，**乐观锁**需要原子性“读-改-写”指令的支持，来读取数据是否被其他线程修改，改写数据内容并将最新的数据写回到原有地址。现在大部分处理器以及可以支持这样的操作。

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

通常CAS的使用方法为：先从V中读取A值，并根据A值计算B值，然后再通过CAS以原子的方法各部分更新V中的值。以计数器为例
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
以不加锁的方式实现了原子的“读-改-写”操作。

CAS的方法在性能上有很大优势：在竞争程度不是很大的情况下，基于CAS的操作，在性能上远远超过基于锁的计数器；在没有竞争的情况下，CAS的性能更高。

但是CAS的缺点是：将竞争的问题交给调用者来处理，但是悲观锁自身就能处理竞争。

## 4. 原子变量
随着硬件上对于原子操作指令的支持，Java中也引入CAS。对于int、long和对象的引用，Java都支持CAS操作，也就是**原子变量类**，JVM会把对于原子变量类的操作编译为底层硬件提供的最有效的方法：如果硬件支持CAS，则编译为CAS指令，如果不支持，则编译为上锁的操作。

原子变量比锁的粒度更细， 更为轻量级，将竞争控制在单个变量之上。因为其不需要上锁，所以不会引发线程的挂起和恢复，因此避免了线程间上下文的切换，性能更好，不易出现延迟和死锁的现象。

常见的原子变量有*AtomicInteger*、*AtomicLong*、*AtomicBoolean*和*AtomicReference*，这些类都支持原子操作，使用get和set方法来获取和更新对象。*原子变量数组*只支持*AtomicInteger*、*AtomicLong*和*AtomicReference*类型，保证数组中每个元素都是可以以volatile语义被访问。

需要注意的是原子变量没有定义hashCode和equals方法，所以每个实例都是不同的，不适合作为散列容器的key。

原子变量可以被视为一种更好volatile变量，通过**compareAndSet**方法尝试以CAS方式更新数据，下面以实现数字区间为示例代码展示如何使用*AtomicReference*。
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
	    //开始循环尝试
        while (true) {
	        // 获得变量值
            IntPair oldv = values.get();
            // 如果下限设置比当前上限还要大
            if (i > oldv.upper)
                //抛出异常
                throw new IllegalArgumentException("Can't set lower to " + i + " > upper");
            IntPair newv = new IntPair(i, oldv.upper);
            //原子性更新
            if (values.compareAndSet(oldv, newv))
	            //如果更新成功则直接返回，否者重新尝试
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
###性能对比：

前文已经提过，原子变量因其使用CAS的方法，在性能上有很大优势：在竞争程度不是很大的情况下，基于CAS的操作，在性能上远远超过基于锁的计数器；在没有竞争的情况下，CAS的性能更高；但是在高竞争的情况下，加锁的性能将会超过原子变量性能（类似于，交通略拥堵时，环岛疏通效果好，但是当交通十分拥堵时，信号灯能够实现更高的吞吐量）。

不过需要说明的是，在真实的使用环境下，资源竞争的强度绝大多数情况下不会大到可以让锁的性能超过原子变量。所以还是应该优先考虑使用原子变量。

>锁和原子变量在不同竞争程度上性能差异很好地说明了各自的优势：在中低程度的竞争之下，原子变量能提供更高的可伸缩性，而在高强度的竞争下，锁能够有效地避免竞争。

当然，如果能避免在多线程间使用共享状态，转而使用线程封闭（如*ThreadLocal*），代码的性能将会更进一步地提高。

## 5. 非阻塞算法
如果某种算法中，一个线程的失败或者挂起不会导致其他线程也失败和挂起，这该种算法是**非阻塞的算法**。如果在算法的每一步中都存在某个线程能够执行下去，那么该算法是无锁（Lock-free）的算法。

如果在算法中仅仅使用CAS用于协调线程间的操作，并且能够正确的实现，那么该算法既是一种无阻塞算法，也是一种无锁算法。在非拥塞算法中，不会出现死锁的优先级反转的问题（但是不排除活锁和资源饥饿的问题，因为算法中会反复尝试）。

上文中的*CasNumberRange* 就是一种非阻塞算法，其很好的说明了非拥塞算法设计的基本模式：在更新某个值时存在不确定性，如果失败就重新尝试。其中关键点在于将执行CAS的范围缩小在单一变量上。
### 5.1 非阻塞的栈
我们以非阻塞的栈为例说明非拥塞算法的设计思路。创建非阻塞算法的关键在于将原子修改的范围缩小到单个变量上，同时保证数据一致性。

栈是最简单的链式数据结构：每个元素仅仅指向一个元素，每个元素也仅被一个元素引用，关键的操作入栈（push）和出栈（pop）都是针对于栈顶元素（top）的。因此每次操作只需要保证栈顶元素的一致性，将原子操作的范围控制在指向栈顶元素的引用即可。实例代码如下：
```
//非阻塞的并发栈
public class ConcurrentStack <E> {
	//原子对象 栈顶元素
    AtomicReference<Node<E>> top = new AtomicReference<Node<E>>();

    public void push(E item) {
        Node<E> newHead = new Node<E>(item);
        Node<E> oldHead;
        do { //循环尝试
            oldHead = top.get();//获得旧值
            newHead.next = oldHead;
        } while (!top.compareAndSet(oldHead, newHead)); //比较旧值是否被修改，如果没有则操作成功，否者继续尝试；
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
以上代码充分体现了非阻塞算法的特点：*某项操作的完成具有不确定性，如不成功必须重新执行*。这个栈通过*compareAndSet*来修改栈顶元素，该方法为原子操作，如果发现被其他线程干扰，则修改操作失败，方法将重新尝试。

算法中的多线程安全性依赖于*compareAndSet*，其提供和加锁机制一样的安全性。既保证原子性，有保证了可见性。除此之外，*AtomicReference*对象上使用get方法，也保证了内存可见性， 和使用*volatile*变量一样。
### 5.2 非阻塞的链表
链表的结构比栈更为复杂，其必须支持头指针和尾指针，且同时有两个指针指向尾部，分别是尾指针和最后一个元素next指针。如何保证两个指针的数据一致性是一个难题，这不能通过一个CAS操作来完成。

>这个难题可以应用这样一个技巧来解决：当线程B发现线程A正在修改数据结构时，数据结构中应该有足够多的信息使得线程B能帮助线程A完成操作，保证数据结构维持一致性。

我们以插入操作为例分析。在插入过程中有两个步骤：
1. 插入新节点，将原有尾节点的next域指向该节点；
2. 将尾指针移动到新的尾节点处。

所以我们可以根据尾节点的next域判断链表是否在稳定状态：如尾节点的next域为null，则说明该链表是稳定状态，没有其他线程在执行插入操作；反之，节点的next域不为null，则说明有其他线程在插入数据。

如果链表不处于稳定状态该怎么办呢？可以让后到的线程帮助正在插入的线程将尾部指针向后推移到新插入的节点处。示例代码如下：
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
假如步骤一处发现链表处在非稳定状态，则会以原子的方法尝试将尾指针移动到新插入的节点，无论是否成功这时链表都会回到稳定状态，tail.next=null，此时再去重新新尝试。如果步骤二出已经将链表的尾指针移动，则步骤四处的原子操作就会失败，不过这没有关系，因为别的线程已经帮助其完成了该操作，链表保持稳定状态。

### 5.3 原子域更新器
上面提到的非拥塞链表，在*ConcurrentLinkedQueue*就有所应用，但是*ConcurrentLinkedQueue*并不是使用原子变量，而是使用普通的volatile变量，通过基于反射的原子域更新器（*AtomicReferenceFieldUpdater*）来进行更新。

原子域更新器是现有volatile域的一种基于反射的“视图”，能够在volatile域上使用CAS指令。原子域更新器没有构造器，要构建对象需要使用工厂方法*newUpdater*，函数然注释如下
```
    /**
    * @param tclass 持有待更新域的类
     * @param vclass 待更新域的类型
     * @param fieldName 待更新域的名字
     */
    public static <U,W> AtomicReferenceFieldUpdater<U,W> newUpdater(Class<U> tclass,                                                           
    Class<W> vclass,
    String fieldName)；
```
使用更新器的好处在于避免构建原子变量的开销，但是这只适用于那些频繁分配且生命周期很短对象，比如列表的节点，其他情况下使用原子变量即可。

### 5.4 带有版本号原子变量

CAS操作是通过比较值来判断原值是否被修改，但是还有可能出现这样的情况：原值为A被修改为B，然后又被修改为A，也就是A-B-A的修改情况。这时再通过比较原值就不能判断是否被修改了。这个问题也被称为**ABA问题**。

ABA问题的解决方案是为变量的值加上版本号，只要版本号变化，就说明原值被修改了，这就是带有时间戳的原子变量*AtomicStampedReference*
```
//原值和时间戳
public AtomicStampedReference(V initialRef, int initialStamp)；
```

## 总结
非拥塞算法通过底层CAS指令来维护多线程的安全性，CAS指令被封装成原子变量的形式对外公开，是一种更好的volatile变量，可以提供更好伸缩性，防止死锁，但是设计和实现较为复杂，对开发人员要求很高。
