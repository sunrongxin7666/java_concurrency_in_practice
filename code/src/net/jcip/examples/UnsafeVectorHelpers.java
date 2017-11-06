package net.jcip.examples;

import java.util.*;

/**
 * UnsafeVectorHelpers
 * <p/>
 * Compound actions on a Vector that may produce confusing results
 *
 * @author Brian Goetz and Tim Peierls
 */
public class UnsafeVectorHelpers {
    //符合操作，先检查再运行，并不是经常安全的，
    public static Object getLast(Vector list) {
        int lastIndex = list.size() - 1;
        return list.get(lastIndex);
    }

    public static void deleteLast(Vector list) {
        int lastIndex = list.size() - 1;
        list.remove(lastIndex);
    }
}
