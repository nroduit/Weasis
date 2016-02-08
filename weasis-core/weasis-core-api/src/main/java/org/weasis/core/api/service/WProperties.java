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
package org.weasis.core.api.service;

import java.awt.Color;
import java.io.IOException;
import java.util.Properties;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.Base64;

public class WProperties extends Properties {

    private static final Logger LOGGER = LoggerFactory.getLogger(WProperties.class);

    private final transient BundleContext context;

    public WProperties() {
        context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
    }

    // Get value from: 1) Local preferences 2) Java System properties 3) config.properties
    @Override
    public String getProperty(String key) {
        String value = super.getProperty(key);
        if (value == null) {
            value = System.getProperty(key, null);
            if (value == null) {
                value = context.getProperty(key);
            }
        }
        return value;
    }

    public void resetProperty(String key, String defaultValue) {
        if (isKeyValid(key)) {
            String value = System.getProperty(key, null);
            if (value == null) {
                value = context.getProperty(key);
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
                value = context.getProperty("def." + key); //$NON-NLS-1$
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
        if (isValid(key, value)) {
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
        if (isValid(key, value)) {
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
        if (isValid(key, value)) {
            this.put(key, String.valueOf(value));
        }
    }

    public boolean getBooleanProperty(String key, boolean def) {
        boolean result = def;
        if (isKeyValid(key)) {
            final String value = this.getProperty(key);
            if (value != null) {
                if ("true".equalsIgnoreCase(value)) { //$NON-NLS-1$
                    result = true;
                } else if ("false".equalsIgnoreCase(value)) { //$NON-NLS-1$
                    result = false;
                }
            }
        }
        return result;
    }

    public void putFloatProperty(String key, float value) {
        if (isValid(key, value)) {
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
        if (isValid(key, value)) {
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

    public void setColorProperty(String key, Color color) {
        if (isValid(key, color)) {
            this.put(key, color2Hexadecimal(color, true));
        }
    }

    public void putByteArrayProperty(String key, byte[] value) {
        if (isValid(key, value)) {
            try {
                this.put(key, Base64.encodeBytes(value, Base64.GZIP));
            } catch (IOException e) {
                LOGGER.error("Set byte property", e);
            }
        }
    }

    public byte[] getByteArrayProperty(String key, byte[] def) {
        byte[] result = def;
        if (isKeyValid(key)) {
            String value = this.getProperty(key);
            if (value != null) {
                try {
                    result = Base64.decode(value);
                } catch (IOException e) {
                    LOGGER.error("Get byte property", e);
                }
            }
        }
        return result;
    }

    private boolean isValid(String key, Object value) {
        return key != null && value != null;
    }

    private boolean isKeyValid(String key) {
        return key != null;
    }

    public static String color2Hexadecimal(Color c, boolean alpha) {
        int val = c == null ? 0 : alpha ? c.getRGB() : c.getRGB() & 0x00ffffff;
        return Integer.toHexString(val);
    }

    public static Color hexadecimal2Color(String hexColor) {
        int intValue = 0xff000000;
        if (hexColor != null) {
            try {
                if (hexColor.length() > 6) {
                    intValue = (int) (Long.parseLong(hexColor, 16) & 0xffffffff);
                } else {
                    intValue |= Integer.parseInt(hexColor, 16);
                }
            } catch (NumberFormatException e) {
                LOGGER.error("Cannot parse color {} into int", hexColor);
            }
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
