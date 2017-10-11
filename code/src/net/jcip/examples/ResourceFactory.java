package net.jcip.examples;

import net.jcip.annotations.*;

/**
 * ResourceFactory
 * <p/>
 * Lazy initialization holder class idiom
 *
 * @author Brian Goetz and Tim Peierls
 */
@ThreadSafe
public class ResourceFactory {
    //静态内部类，提前加载
    private static class ResourceHolder {
        public static Resource resource = new Resource();
    }

    //执行该方法前，resource一定已经被初始化了
    public static Resource getResource() {
        return ResourceHolder.resource;
    }

    static class Resource {
    }
}
