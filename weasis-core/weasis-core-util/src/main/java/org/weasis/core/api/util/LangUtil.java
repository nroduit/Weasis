package org.weasis.core.api.util;

import java.util.Collection;
import java.util.Collections;

public class LangUtil {

    public static <T> Iterable<T> emptyIfNull(Iterable<T> iterable) {
        return iterable == null ? Collections.<T> emptyList() : iterable;
    }

    public static boolean getNULLtoFalse(Boolean val) {
        if (val != null) {
            return val.booleanValue();
        }
        return false;
    }

    public static boolean getNULLtoTrue(Boolean val) {
        if (val != null) {
            return val.booleanValue();
        }
        return true;
    }

    public static boolean getEmptytoFalse(String val) {
        if (StringUtil.hasText(val)) {
            return getBoolean(val);
        }
        return false;
    }

    public static boolean geEmptytoTrue(String val) {
        if (StringUtil.hasText(val)) {
            return getBoolean(val);
        }
        return true;
    }

    private static boolean getBoolean(String val) {
        return "true".equalsIgnoreCase(val); //$NON-NLS-1$
    }

    public static <T, C extends Collection<T>> C convertCollectionType(Iterable<?> from, C newCollection,
        Class<T> listClass) {
        for (Object item : from) {
            newCollection.add(listClass.cast(item));
        }
        return newCollection;
    }
}
