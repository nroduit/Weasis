package org.weasis.core.api.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.swing.ImageIcon;

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
            LOGGER.error("Cannot read resource:{}", e.getMessage()); //$NON-NLS-1$
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
        return getLogo("images" + File.separator + "about.png"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public static ImageIcon getIconLogo64() {
        return getLogo("images" + File.separator + "logo-button.png"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public static ImageIcon getLogo(String filename) {
        ImageIcon icon = null;
        try {
            File file = getResource(filename);
            if (file != null && file.canRead()) {
                icon = new ImageIcon(file.toURI().toURL());
            }
        } catch (Exception e) {
            LOGGER.error("Cannot read logo image:{}", e.getMessage()); //$NON-NLS-1$
        }
        return icon;
    }

    public static File getResource(String filename) {
        if (filename != null) {
            return new File(BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.resources.path"), filename); //$NON-NLS-1$
        }
        return null;
    }

}
