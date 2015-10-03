/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.api.gui.util;

import java.io.File;

import javax.swing.LookAndFeel;

import org.osgi.framework.BundleContext;
import org.weasis.core.api.util.FileUtil;

/**
 * The Class AppProperties.
 *
 * @author Nicolas Roduit
 */
public class AppProperties {

    /**
     * The version of the application (for display)
     */
    public static final String WEASIS_VERSION = System.getProperty("weasis.version"); //$NON-NLS-1$
    /**
     * The path of the directory “.weasis” (containing the installation and the preferences)
     */
    public static final String WEASIS_PATH = System.getProperty("weasis.path"); //$NON-NLS-1$
    /**
     * The name of the application (for display)
     */
    public static final String WEASIS_NAME = System.getProperty("weasis.name"); //$NON-NLS-1$
    /**
     * The current user of the application (defined either in JNLP by the property "weasis.user" or by the user of the
     * operating system session if the property is null)
     */
    public static final String WEASIS_USER = System.getProperty("weasis.user"); //$NON-NLS-1$
    /**
     * The name of the configuration profile (defined in config-ext.properties). The value is “default” if null. This
     * property allows to have separated preferences (in a new directory).
     */
    public static final String WEASIS_PROFILE = System.getProperty("weasis.profile"); //$NON-NLS-1$
    /**
     * The directory for writing temporary files
     */
    public static final File APP_TEMP_DIR;

    static {
        String tempDir = System.getProperty("java.io.tmpdir"); //$NON-NLS-1$
        File tdir;
        if (tempDir == null || tempDir.length() == 1) {
            String dir = System.getProperty("user.home", ""); //$NON-NLS-1$ //$NON-NLS-2$
            tdir = new File(dir);
        } else {
            tdir = new File(tempDir);
        }
        /*
         * Set the user name and the id (weasis source instance on web) to avoid mixing files by several users (Linux)
         * or by running multiple instances of Weasis from different sources.
         */
        APP_TEMP_DIR = new File(tdir,
            "weasis-" + System.getProperty("user.name", "tmp") + "." + System.getProperty("weasis.source.id", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
        System.setProperty("weasis.tmp.dir", APP_TEMP_DIR.getAbsolutePath()); //$NON-NLS-1$
        try {
            // Clean temp folder, necessary when the application has crashed.
            FileUtil.deleteDirectoryContents(APP_TEMP_DIR, 3, 0);
        } catch (Exception e1) {
        }
    }

    public static final File FILE_CACHE_DIR = buildAccessibleTempDirectory("cache"); //$NON-NLS-1$

    public static final String OPERATING_SYSTEM = System.getProperty("os.name", "unknown").toLowerCase(); //$NON-NLS-1$ //$NON-NLS-2$
                                                                                                          //$NON-NLS-1$ ;

    public static final GhostGlassPane glassPane = new GhostGlassPane();

    private AppProperties() {

    }

    public static File getBundleDataFolder(BundleContext context) {
        if (context == null) {
            return null;
        }
        return new File(AppProperties.WEASIS_PATH + File.separator + "data", context.getBundle().getSymbolicName()); //$NON-NLS-1$ ;
    }

    public static File buildAccessibleTempDirectory(String... subFolderName) {
        if (subFolderName != null) {
            StringBuilder buf = new StringBuilder();
            for (String s : subFolderName) {
                buf.append(s);
                buf.append(File.separator);
            }
            File file = new File(AppProperties.APP_TEMP_DIR, buf.toString());
            try {
                file.mkdirs();
                return file;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return AppProperties.APP_TEMP_DIR;
    }

    public static boolean isMacNativeLookAndFeel() {
        if (AppProperties.OPERATING_SYSTEM.startsWith("mac")) { //$NON-NLS-1$
            LookAndFeel laf = javax.swing.UIManager.getLookAndFeel();
            if (laf != null && laf.isNativeLookAndFeel()) {
                return true;
            }
        }
        return false;
    }

}
