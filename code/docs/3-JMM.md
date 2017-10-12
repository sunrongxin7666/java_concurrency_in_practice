# 并发实战 16. Java内存模型
@(Java并发)[java, 并发, jcip]


本文将简要介绍java内存模型（JMM）底层的需求以及所提供的保障，这将有助于更深一步地理解更高层面的并发同步机制背后的原理。

##1. 何为内存模型
如大家所知，Java代码在编译和运行的过程中，其实会有很多意想不到的事情发生：
- 在生成指令顺序可能和源代码中顺序不相同；
- 编译器还会把变量保存到寄存器中而非内存中；
- 处理器可以采用乱序或者并行的方式执行指令；
- 缓存可能会改变将写入变量提交到主内存的次序；
- 保存在处理器本地缓存中的值，对于其他处理器是不可见的；
- .....

以上所有的这些情况都可能会导致多线程同步的问题。

其实，在单线程的环境下，这些底层的技术都是为了提高执行效率而存在，不会影响运行结果：JVM只会在运行结果和严格串行执行结果相同的情况下进行如上的优化操作。我们需要知道近些年以来计算性能的提高很大程度上要感谢这些重新排序的操作。

为了进一步提高效率，多核处理器已经广泛被使用，程序在多数时间内都是并发执行，只有在需要的时候才回去协调各个线程之间的操作。那什么是需要的时候呢，JVM将这个问题抛给了程序，要求在代码中使用**同步机制**来保证多线程安全。

### 1.1 多处理器架构中的内存模型

在多核理器架构中，每个处理器都拥有自己的缓存，并且会定期地与主内存进行协调。这样的架构就需要解决**缓存一致性（Cache Coherence）**的问题。很可惜，一些框架中只提供了**最小保证**，即允许不同处理器在任意时刻从同一存储位置上看到不同的值。

>正因此存在上面所述的硬件能力和线程安全需求的差异，才导致需要在代码中使用同步机制来保证多线程安全。

这样“不靠谱”的设计还是为了追求性能，因为要保证每个处理器都能在任意时刻知道其他处理器在做什么需要很大的开销，而且大部分情况下处理器也没有这样的需求，放宽对于存储一致性的保障，以换取性能的提升。

架构中定义了一些特殊的指令，也就是内存栅栏，当需要多线程间数据共享的时，这些指令将会提供额外的存储协调。

值得庆幸的是JMM为我们屏蔽了各个框架在内存模型上的差异，让开发人员不用再去关系这些底层问题。

### 1.2 重排序

JVM不光会改变命令执行的顺序，甚至还会让不同线程看到的代码执行的顺序也是不同的，这就会让在没有同步操作的情况下预测代码执行结果边变的困难。

下面的代码是《Java Concurrency in Practice》给出的一个例子
```
public class PossibleReordering {
    static int x = 0, y = 0;
    static int a = 0, b = 0;

    public static void main(String[] args) throws InterruptedException {
        //对于每个线程内部而言，语句的执行顺序和结果无关
        //但是对于线程之间，语句的执行顺序却和结果密切相关
        //而不同线程之间的见到的代码执行顺序可能都是不同的
        Thread one = new Thread(new Runnable() {
            public void run() {
                a = 1;
                x = b;
            }
        });
        Thread other = new Thread(new Runnable() {
            public void run() {
                b = 1;
                y = a;
            }
        });
        one.start();
        other.start();
        one.join();
        other.join();
        System.out.println("( " + x + "," + y + ")");
    }
}
```
以上代码的输出结果可能是（1，0）、（0，1）、（1，1）甚至是（0，0），这是由于两个线程的执行先后顺序可能不同，线程内部的赋值操作的顺序也有可能相互颠倒。

上面这样简单的代码，如果缺少合理的同步机制都很难预测其结果，复杂的程序将更为困难，这也正是通过同步机制限制编译器和运行时对于内存操作重排序限制的意义所在。

### 1.3 Java内存模型与Happens-Before规则
Java内存模型是通过各种操作来定义的，包括对于变量的对写操作，监视器的加锁和释放锁操作，以及线程的启动和合并，而这些操作都要满足一种偏序关系——Happen-Before规则：想要保证执行操作B的线程看到执行操作A的结果，而无论两个操作是否在同一线程，则操作A和操作B之间必须满足Happens-Before关系，否者JVM将可以对他们的执行顺序任意安排。

>Happens-Before规则：
- 程序顺序规则：一个线程中的每个操作，先于随后该线程中的任意后续操作执行（针对可见性而言）;
- 监视器锁规则：对一个锁的解锁操作，先于随后对这个锁的获取操作执行;
- volatile变量规则：对一个volatile变量的写操作，先于对这个变量的读操作执行;
- 传递性：如果A happens-before B，B happens-before C，那么A happens-before C;
- start规则：如果线程A执行线程B的start方法，那么线程A的ThreadB.start()先于线程B的任意操作执行;
- join规则：如果线程A执行线程B的join方法，那么线程B的任意操作先于线程A从TreadB.join()方法成功返回之前执行；
- 中断规则：当线程A调用另一个线程B的interrupt方法时，必须在线程A检测到线程B被中断（抛出InterruptException，或者调用ThreadB.isInterrupted()）之前执行。
- 终结器规则：一个对象的构造函数先于该对象的finalizer方法执行前完成；


## 2. 安全发布与内存模型

之前的文章中曾介绍过安全发布和数据共享的问题，而造成不正确的发布的根源就在于发布对象的操作和访问对象的操作之间缺少Happens-Before关系。

请看下面这个例子，这是一个不安全的懒加载，只有在用到Resource对象时采取初始化该对象
```
public class UnsafeLazyInitialization {
    private static Resource resource;

    public static Resource getInstance() {
        if (resource == null)
            resource = new Resource(); // unsafe publication
        return resource;
    }

    static class Resource {
    }
}
```
getInstance() 方法是一个静态方法，可以被多个线程同时调用，就有可能出现数据竞争的问题，在Java内存模型的角度来说就是读取resource对象判断是都为空，和对resource赋值的写操作并不存在Happens-Before关系，彼此不一定是多线程环境中可见的。跟进一步，new Resource()来创建一个类对象，要先分配内存空间，对象各个域都是被赋予默认值，然后再调用构造函数对写入各个域，由于这个过程和读取Resource对象的操作并不满足Happens-Before关系，所以可能一个线程中正在创建对象但是没有执行完毕，而这时另一个线程看到的Resource对象的确不是为空，但却是个失效的状态。

真正线程安全的懒加载应该是这样的，通过同步机制上锁，让读操作和写操作满足Happens-Before规则。
```
public class SafeLazyInitialization {
    private static Resource resource;

    //释放锁的操作一定在获得锁的操作之前，所以一线程获得内置所之后，一定要
    public synchronized static Resource getInstance() {
        if (resource == null)
            resource = new Resource();
        return resource;
    }

    static class Resource {
    }
}
```

```
public class EagerInitialization {
    private static Resource resource = new Resource();

    public static Resource getResource() {
        return resource;
    }

    static class Resource {
    }
}
```

```
public class ResourceFactory {
    private static class ResourceHolder {
        public static Resource resource = new Resource();
    }

    public static Resource getResource() {
        return ResourceHolder.resource;
    }

    static class Resource {
    }
}
```

```
public class DoubleCheckedLocking {
    private static Resource resource;

    public static Resource getInstance() {
        if (resource == null) {
            synchronized (DoubleCheckedLocking.class) {
                if (resource == null)
                    resource = new Resource();
            }
        }
        return resource;
    }

    static class Resource {

    }
}
```

