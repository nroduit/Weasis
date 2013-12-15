package org.weasis.core.api.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.swing.ImageIcon;

import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.service.BundleTools;

public class ResourceUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceUtil.class);

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
            LOGGER.error("Cannot read resource:{}", e.getMessage());
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

    public static ImageIcon getLargeLogo() {
        return getLogo("about.png");
    }

    public static ImageIcon getIconLogo64() {
        return getLogo("logo-button.png");
    }

    public static ImageIcon getLogo(String filename) {
        ImageIcon icon = null;
        try {
            File file = new File(BundleTools.SYSTEM_PREFERENCES.getProperty(Constants.FRAMEWORK_STORAGE), filename);
            icon = new ImageIcon(file.toURI().toURL());
        } catch (Exception e) {
            LOGGER.error("Cannot read logo image:{}", e.getMessage());
        }
        return icon;
    }

}
