package org.weasis.core.api.util;

import java.util.Collection;

public class LangUtil {

    public static boolean getNULLtoFalse(Object val) {
        if (val instanceof Boolean) {
            return ((Boolean) val).booleanValue();
        } else if (val instanceof String) {
            return "true".equalsIgnoreCase((String) val);
        }
        return false;
    }

    public static boolean getNULLtoTrue(Object val) {
        if (val instanceof Boolean) {
            return ((Boolean) val).booleanValue();
        } else if (val instanceof String) {
            return "true".equalsIgnoreCase((String) val);
        }
        return true;
    }

    public static <T, C extends Collection<T>> C convertCollectionType(Iterable<?> from, C newCollection,
        Class<T> listClass) {
        for (Object item : from) {
            newCollection.add(listClass.cast(item));
        }
        return newCollection;
    }
}
