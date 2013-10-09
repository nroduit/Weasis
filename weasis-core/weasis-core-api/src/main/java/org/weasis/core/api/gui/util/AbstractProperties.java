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

import java.awt.Color;
import java.io.File;
import java.util.Locale;
import java.util.Properties;

import javax.swing.LookAndFeel;

import org.osgi.framework.BundleContext;
import org.weasis.core.api.util.FileUtil;

/**
 * The Class IniProps.
 * 
 * @author Nicolas Roduit
 */
public abstract class AbstractProperties {

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
        APP_TEMP_DIR =
            new File(tdir,
                "weasis-" + System.getProperty("user.name", "tmp") + "." + System.getProperty("weasis.source.id", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        System.setProperty("weasis.tmp.dir", APP_TEMP_DIR.getAbsolutePath());
        try {
            // Clean temp folder, necessary when the application has crashed.
            FileUtil.deleteDirectoryContents(APP_TEMP_DIR, 3, 0);
        } catch (Exception e1) {
        }
    }
    public static final File FILE_CACHE_DIR = buildAccessibleTempDirectory("cache"); //$NON-NLS-1$

    public static final String OPERATING_SYSTEM = System.getProperty("os.name", "unknown").toLowerCase(); //$NON-NLS-1$ //$NON-NLS-2$;

    /** Container for Properties */
    protected static Properties s_prop = new Properties();
    public static final GhostGlassPane glassPane = new GhostGlassPane();

    public static File getBundleDataFolder(BundleContext context) {
        if (context == null) {
            return null;
        }
        return new File(AbstractProperties.WEASIS_PATH + File.separator + "data", context.getBundle().getSymbolicName()); //$NON-NLS-1$;
    }

    protected static String checkProperty(String key, String defaultValue) {
        String result = null;
        result = s_prop.getProperty(key, defaultValue);
        s_prop.setProperty(key, result);
        return result;
    }

    public static File buildAccessibleTempDirectory(String... subFolderName) {
        if (subFolderName != null) {
            StringBuffer buf = new StringBuffer();
            for (String s : subFolderName) {
                buf.append(s);
                buf.append(File.separator);
            }
            File file = new File(AbstractProperties.APP_TEMP_DIR, buf.toString());
            try {
                file.mkdirs();
                return file;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return AbstractProperties.APP_TEMP_DIR;
    }

    public static boolean isMacNativeLookAndFeel() {
        if (AbstractProperties.OPERATING_SYSTEM.startsWith("mac")) { //$NON-NLS-1$
            LookAndFeel laf = javax.swing.UIManager.getLookAndFeel();
            if (laf != null && laf.isNativeLookAndFeel()) {
                return true;
            }
        }
        return false;
    }

    public static void setProperty(String key, String value) {
        if (s_prop == null) {
            s_prop = new Properties();
        }
        s_prop.setProperty(key, value);
    }

    public static void setProperty(String key, boolean[] value) {
        setProperty(key, boolToText(value));
    }

    public static void setProperty(String key, Locale value) {
        setProperty(key, localeToText(value));
    }

    public static void setProperty(String key, boolean value) {
        setProperty(key, value ? "Y" : "N"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public static void setProperty(String key, int value) {
        setProperty(key, String.valueOf(value));
    }

    public static void setProperty(String key, double value) {
        setProperty(key, String.valueOf(value));
    }

    public static void setProperty(String key, float value) {
        setProperty(key, String.valueOf(value));
    }

    public static void setProperty(String key, Color c) {
        setProperty(key, color2Hexadecimal(c));
    }

    public static String getProperty(String key) {
        if (key == null) {
            return ""; //$NON-NLS-1$
        }
        String retStr = s_prop.getProperty(key, ""); //$NON-NLS-1$
        if (retStr == null || retStr.length() == 0) {
            return ""; //$NON-NLS-1$
        }
        return retStr;
    }

    public static boolean getPropertyBool(String key) {
        return getProperty(key).equals("Y"); //$NON-NLS-1$
    }

    public static boolean[] getPropertyArrayBool(String key, int length) {
        boolean[] val = new boolean[length];
        if (key == null) {
            return val;
        }
        String retStr = s_prop.getProperty(key);
        if (retStr == null || retStr.length() == 0) {
            return val;
        }
        char[] c = retStr.toCharArray();
        if (c.length != val.length) {
            return val;
        }
        for (int i = 0; i < c.length; i++) {
            val[i] = String.valueOf(c[i]).equalsIgnoreCase("Y"); //$NON-NLS-1$
        }
        return val;
    }

    public static Locale getPropertyLocale(String key) {
        if (key == null) {
            return Locale.ENGLISH;
        }
        String retStr = s_prop.getProperty(key);
        if (retStr == null || retStr.length() == 0) {
            return Locale.ENGLISH;
        }
        char[] c = retStr.toCharArray();
        String[] val = new String[3];
        StringBuffer buffer = new StringBuffer();
        for (int i = 0, k = 0; i < c.length; i++) {
            if (c[i] == '_') {
                val[k] = buffer.toString();
                buffer = new StringBuffer();
                k++;
            } else {
                buffer.append(c[i]);
            }
        }
        return new Locale(val[0], val[1], val[2]);
    }

    public static int getPropertyInt(String key) {
        if (key == null) {
            return 1;
        }
        String retStr = s_prop.getProperty(key);
        if (retStr == null || retStr.length() == 0) {
            return 1;
        }
        try {
            return Integer.parseInt(retStr);
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    public static double getPropertyDouble(String key) {
        if (key == null) {
            return 1;
        }
        String retStr = s_prop.getProperty(key);
        if (retStr == null || retStr.length() == 0) {
            return 1;
        }
        try {
            return Double.parseDouble(retStr);
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    public static float getPropertyFloat(String key) {
        if (key == null) {
            return 1.0f;
        }
        String retStr = s_prop.getProperty(key);
        if (retStr == null || retStr.length() == 0) {
            return 1.0f;
        }
        try {
            return Float.parseFloat(retStr);
        } catch (NumberFormatException ex) {
            return 1.0f;
        }
    }

    public static Color getPropertyColor(String key) {
        return hexadecimal2Color(s_prop.getProperty(key));
    }

    public static String color2Hexadecimal(Color c) {
        int val = c == null ? 0 : c.getRGB() & 0x00ffffff;
        return Integer.toHexString(val);
    }

    public static Color hexadecimal2Color(String hexColor) {
        int intValue = 0;
        if (hexColor != null) {
            try {
                intValue = Integer.parseInt(hexColor, 16);
            } catch (NumberFormatException e) {
            }
        }
        return new Color(intValue);
    }

    protected static String boolToText(boolean[] value) {
        StringBuffer str = new StringBuffer();
        for (int i = 0; i < value.length; i++) {
            str.append(value[i] ? 'Y' : 'N');
        }
        return str.toString();
    }

    protected static String localeToText(Locale value) {
        return value.getLanguage() + "_" + value.getCountry() + "_" + value.getVariant() + "_"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    protected static String boolToText(boolean value) {
        return value ? "Y" : "N"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    public static Properties getProperties() {
        return s_prop;
    }

    // TODO move to explorer preferences
    public static boolean isThumbnailSortDesend() {
        return true;
    }

}
