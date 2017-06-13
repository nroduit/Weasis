package org.weasis.core.api.util;

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
}
