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

import java.util.Properties;

import org.weasis.core.api.Messages;

public class WProperties extends Properties {

    public WProperties() {

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
                if (value.equalsIgnoreCase("true")) { //$NON-NLS-1$
                    result = true;
                } else if (value.equalsIgnoreCase("false")) { //$NON-NLS-1$
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

    // Does not have an accessible Base64 class (the one use by preferences is protected)

    // public void putByteArrayProperty(String key, byte[] value) {
    // if (isValid(key, value)) {
    // try {
    // this.put(key, new String(Base64.encodeBase64(value), "utf-8"));
    // } catch (UnsupportedEncodingException ignore) {
    // // utf-8 is always available
    // }
    // }
    // }
    //
    // public byte[] getByteArrayProperty(String key, byte[] def) {
    // byte[] result = def;
    // if (isKeyValid(key)) {
    // String value = this.getProperty(key);
    // if (value != null) {
    // try {
    // result = Base64.decodeBase64(value.getBytes("utf-8"));
    // } catch (UnsupportedEncodingException ignore) {
    // // utf-8 is always available
    // }
    // }
    // }
    // return result;
    // }

    private boolean isValid(String key, Object value) {
        return key != null && value != null;
    }

    private boolean isKeyValid(String key) {
        return key != null;
    }
}
