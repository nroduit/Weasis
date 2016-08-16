/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
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
        return url == null ? null : url.toString();
    }

    public InputStream getResourceAsStream(String name, Class<?> c) {
        URL url = getResourceURL(name, c);
        try {
            return url != null ? url.openStream() : null;
        } catch (IOException e) {
            LOGGER.error("Cannot read resource", e); //$NON-NLS-1$
        }
        return null;
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
        File file = getResource(filename);
        if (file.canRead()) {
            try {
                return new ImageIcon(file.toURI().toURL());
            } catch (Exception e) {
                LOGGER.error("Cannot read logo image:{}", e); //$NON-NLS-1$
            }
        }
        return null;
    }

    private static String getResourcePath() {
        String path = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.resources.path"); //$NON-NLS-1$
        if (!StringUtil.hasText(path)) {
            throw new IllegalArgumentException("Empty system property: weasis.resources.path"); //$NON-NLS-1$
        }
        return path;
    }

    public static File getResource(String filename) {
        if (!StringUtil.hasText(filename)) {
            throw new IllegalArgumentException("Empty filename"); //$NON-NLS-1$
        }
        return new File(getResourcePath(), filename);
    }

    public static File getResource(String filename, String... subFolderName) {
        if (!StringUtil.hasText(filename)) {
            throw new IllegalArgumentException("Empty filename"); //$NON-NLS-1$
        }
        String path = getResourcePath();
        if (subFolderName != null) {
            StringBuilder buf = new StringBuilder(path);
            for (String s : subFolderName) {
                buf.append(File.separator);
                buf.append(s);
            }
            path = buf.toString();
        }
        return new File(path, filename);
    }
}
