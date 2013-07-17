package org.weasis.core.api.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class ResourceUtil {
    private ResourceUtil() {
    }

    public static String getResource(String resource, Class<?> c) {
        URL url = getResourceURL(resource, c);
        return url != null ? url.toString() : null;
    }

    public InputStream getResourceAsStream(String name, Class<?> c) {
        URL url = getResourceURL(name, c);
        try {
            return url != null ? url.openStream() : null;
        } catch (IOException e) {
            return null;
        }
    }

    public static URL getResourceURL(String resource, Class<?> c) {
        URL url = null;
        if (c != null) {
            ClassLoader classLoader = c.getClassLoader();
            if (classLoader != null) {
                url = classLoader.getResource(resource);
            }
        }
        if (url == null) {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader != null) {
                url = classLoader.getResource(resource);
            }
        }
        if (url == null) {
            url = ClassLoader.getSystemResource(resource);
        }
        return url;
    }
}
