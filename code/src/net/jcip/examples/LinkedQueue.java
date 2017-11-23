package net.jcip.examples;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.*;

import net.jcip.annotations.*;

/**
 * LinkedQueue
 * <p/>
 * Insertion in the Michael-Scott nonblocking queue algorithm
 *
 * @author Brian Goetz and Tim Peierls
 */
@ThreadSafe
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
                    // 更新操作失败，再次尝试
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
