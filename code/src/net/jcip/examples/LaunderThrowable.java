package net.jcip.examples;

/**
 * StaticUtilities
 *
 * @author Brian Goetz and Tim Peierls
 */
public class LaunderThrowable {

    /**
     * Coerce an unchecked Throwable to a RuntimeException
     * <p/>
     * If the Throwable is an Error, throw it; if it is a
     * RuntimeException return it, otherwise throw IllegalStateException
     */
    public static RuntimeException launderThrowable(Throwable t) {
        // 运行时异常，直接抛出
        if (t instanceof RuntimeException)
            return (RuntimeException) t;
        else if (t instanceof Error) //Error 直接抛出
            throw (Error) t;
        else //其他异常，抛出逻辑异常
            throw new IllegalStateException("Not unchecked", t);
    }
}
