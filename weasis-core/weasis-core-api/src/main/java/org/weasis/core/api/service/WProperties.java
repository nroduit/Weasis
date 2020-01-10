/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.service;

import java.awt.Color;
import java.io.IOException;
import java.util.Base64;
import java.util.Properties;

import org.osgi.framework.BundleContext;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.util.GzipManager;
import org.weasis.core.api.util.StringUtil;

public class WProperties extends Properties {
    private static final long serialVersionUID = 3647479963645248145L;

    private static final Logger LOGGER = LoggerFactory.getLogger(WProperties.class);

    private final transient BundleContext context;

    public WProperties() {
        context = AppProperties.getBundleContext();
    }

    @Override
    public synchronized Object setProperty(String key, String value) {
        if (isValid(key, value)) {
            return super.setProperty(key, value);
        }
        return null;
    }

    // Get value from: 1) Local preferences 2) Java System properties 3) config.properties
    @Override
    public String getProperty(String key) {
        String value = super.getProperty(key);
        if (value == null) {
            value = System.getProperty(key, null);
            if (value == null) {
                value = context == null ? null : context.getProperty(key);
            }
        }
        return value;
    }

    public void resetProperty(String key, String defaultValue) {
        if (isKeyValid(key)) {
            String value = System.getProperty(key, null);
            if (value == null) {
                value = context == null ? null : context.getProperty(key);
                if (value == null) {
                    value = defaultValue;
                }
            }
            if (isValid(key, value)) {
                this.put(key, value);
            }
        }
    }

    // Special property used by a OSGI service through BundleContext
    public void resetServiceProperty(String key, String defaultValue) {
        if (isKeyValid(key)) {
            String value = System.getProperty(key, null);
            if (value == null) {
                value = context == null ? null : context.getProperty("def." + key); //$NON-NLS-1$
                if (value == null) {
                    value = defaultValue;
                }
            }
            if (isValid(key, value)) {
                this.put(key, value);
            }
        }
    }

    public void putIntProperty(String key, int value) {
        if (isKeyValid(key)) {
            this.put(key, String.valueOf(value));
        }
    }

    public int getIntProperty(String key, int def) {
        int result = def;
        if (isKeyValid(key)) {
            final String value = this.getProperty(key);
            if (value != null) {
                try {
                    result = Integer.parseInt(value);
                } catch (NumberFormatException ignore) {
                    // return the default value
                }
            }
        }
        return result;
    }

    public void putLongProperty(String key, long value) {
        if (isKeyValid(key)) {
            this.put(key, String.valueOf(value));
        }
    }

    public long getLongProperty(String key, long def) {
        long result = def;
        if (isKeyValid(key)) {
            final String value = this.getProperty(key);
            if (value != null) {
                try {
                    result = Long.parseLong(value);
                } catch (NumberFormatException ignore) {
                    // return the default value
                }
            }
        }
        return result;
    }

    public void putBooleanProperty(String key, boolean value) {
        if (isKeyValid(key)) {
            this.put(key, String.valueOf(value));
        }
    }

    public boolean getBooleanProperty(String key, boolean def) {
        boolean result = def;
        if (isKeyValid(key)) {
            final String value = this.getProperty(key);
            if (value != null) {
                if (Boolean.TRUE.toString().equalsIgnoreCase(value)) {
                    result = true;
                } else if (Boolean.FALSE.toString().equalsIgnoreCase(value)) {
                    result = false;
                }
            }
        }
        return result;
    }

    public void putFloatProperty(String key, float value) {
        if (isKeyValid(key)) {
            this.put(key, String.valueOf(value));
        }
    }

    public float getFloatProperty(String key, float def) {
        float result = def;
        if (isKeyValid(key)) {
            final String value = this.getProperty(key);
            if (value != null) {
                try {
                    result = Float.parseFloat(value);
                } catch (NumberFormatException ignore) {
                    // return the default value
                }
            }
        }
        return result;
    }

    public void putDoubleProperty(String key, double value) {
        if (isKeyValid(key)) {
            this.put(key, String.valueOf(value));
        }
    }

    public double getDoubleProperty(String key, double def) {
        double result = def;
        if (isKeyValid(key)) {
            final String value = this.getProperty(key);
            if (value != null) {
                try {
                    result = Double.parseDouble(value);
                } catch (NumberFormatException ignore) {
                    // return the default value
                }
            }
        }
        return result;
    }

    public Color getColorProperty(String key) {
        return hexadecimal2Color(this.getProperty(key));
    }

    public void putColorProperty(String key, Color color) {
        if (isValid(key, color)) {
            this.put(key, color2Hexadecimal(color, true));
        }
    }

    public void putByteArrayProperty(String key, byte[] value) {
        if (isKeyValid(key)) {
            try {
                String val = StringUtil.EMPTY_STRING;
                if(value != null && value.length > 0) {
                    val = new String(Base64.getEncoder().encode(GzipManager.gzipCompressToByte(value)));
                }
                this.put(key, val);
            } catch (IOException e) {
                LOGGER.error("Set byte property", e); //$NON-NLS-1$
            }
        }
    }

    public byte[] getByteArrayProperty(String key, byte[] def) {
        byte[] result = def;
        if (isKeyValid(key)) {
            String value = this.getProperty(key);
            if (StringUtil.hasText(value)) {
                try {
                    result = GzipManager.gzipUncompressToByte(Base64.getDecoder().decode(value.getBytes()));
                } catch (IOException e) {
                    LOGGER.error("Get byte property", e); //$NON-NLS-1$
                }
            }
        }
        return result;
    }

    private static boolean isValid(String key, Object value) {
        return key != null && value != null;
    }

    private static boolean isKeyValid(String key) {
        return key != null;
    }

    public static String color2Hexadecimal(Color c, boolean alpha) {
        int val = c == null ? 0 : alpha ? c.getRGB() : c.getRGB() & 0x00ffffff;
        return Integer.toHexString(val);
    }

    public static Color hexadecimal2Color(String hexColor) {
        int intValue = 0xff000000;

        try {
            if (hexColor != null && hexColor.length() > 6) {
                intValue = (int) (Long.parseLong(hexColor, 16) & 0xffffffff);
            } else {
                intValue |= Integer.parseInt(hexColor, 16);
            }
        } catch (NumberFormatException e) {
            LOGGER.error("Cannot parse color {} into int", hexColor); //$NON-NLS-1$
        }
        return new Color(intValue, true);
    }

    public static void setProperty(WProperties properties, String key, Preferences prefNode, String defaultValue) {
        if (properties != null && key != null) {
            String val = prefNode.get(key, null);
            if (val == null) {
                val = properties.getProperty(key, defaultValue);
            }
            properties.setProperty(key, val);
        }
    }
}
