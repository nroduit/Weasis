package com.codeminders.demo.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collection;

public final class StringUtils {

    private StringUtils() {
    }

    public static boolean isNotBlank(String str) {
        return str != null
                && !str.trim().isEmpty();
    }

    public static String urlEncode(String str) {
        try {
            return URLEncoder.encode(str, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException("Error on encoding url " + str, ex);
        }
    }

    public static String join(Collection<String> collection, String joinString) {
        StringBuilder builder = new StringBuilder();
        for (String str : collection) {
            if (builder.length() > 0) {
                builder.append(joinString);
            }
            builder.append(str);
        }

        return builder.toString();
    }
}
