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

import org.weasis.core.api.util.FileUtil;

/**
 * The Class IniProps.
 * 
 * @author Nicolas Roduit
 */
public abstract class AbstractProperties {

    public final static String WEASIS_VERSION = System.getProperty("weasis.version"); //$NON-NLS-1$
    public final static String WEASIS_PATH = System.getProperty("weasis.path"); //$NON-NLS-1$
    public final static File APP_TEMP_DIR;

    static {
        String tempDir = System.getProperty("java.io.tmpdir"); //$NON-NLS-1$
        File tdir;
        if (tempDir == null || tempDir.length() == 1) {
            String dir = System.getProperty("user.home"); //$NON-NLS-1$
            if (dir == null) {
                dir = ""; //$NON-NLS-1$
            }
            tdir = new File(dir);
        } else {
            tdir = new File(tempDir);
        }
        APP_TEMP_DIR = new File(tdir, "weasis"); //$NON-NLS-1$
        try {
            // Clean temp folder, necessary when the application has crashed.
            FileUtil.deleteDirectoryContents(APP_TEMP_DIR);
        } catch (Exception e1) {
        }
        try {
            APP_TEMP_DIR.mkdirs();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
    public static final String OPERATING_SYSTEM = System.getProperty("os.name", "unknown").toLowerCase(); //$NON-NLS-1$ //$NON-NLS-2$;

    /** This array contains the 16 hex digits '0'-'F'. */
    public static final char[] hexDigits = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E',
        'F' };
    /** Container for Properties */
    protected static Properties s_prop = new Properties();
    public final static GhostGlassPane glassPane = new GhostGlassPane();

    protected static String checkProperty(String key, String defaultValue) {
        String result = null;
        result = s_prop.getProperty(key, defaultValue);
        s_prop.setProperty(key, result);
        return result;
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
        setProperty(key, c2hex(c));
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
        int i = getInt(key, 0xaaa);
        if (i == 0xaaa) {
            return new Color(128, 128, 128);
        }
        return new Color((i >> 16) & 0xFF, (i >> 8) & 0xFF, i & 0xFF);
    }

    protected static int getInt(String key, int defaultValue) {
        String s = s_prop.getProperty(key);
        if (s != null) {
            try {
                return Integer.decode(s).intValue();
            } catch (NumberFormatException e) {
            }
        }
        return defaultValue;
    }

    protected static String c2hex(Color c) {
        int i = c.getRGB();
        char[] buf7 = new char[7];
        buf7[0] = '#';
        for (int pos = 6; pos >= 1; pos--) {
            buf7[pos] = hexDigits[i & 0xf];
            i >>>= 4;
        }
        return new String(buf7);
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
        // TODO Auto-generated method stub
        return true;
    }

}
