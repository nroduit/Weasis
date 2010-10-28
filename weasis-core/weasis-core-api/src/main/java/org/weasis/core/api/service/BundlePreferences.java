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

import java.io.File;
import java.util.Arrays;

import org.osgi.framework.BundleContext;
import org.osgi.service.prefs.Preferences;
import org.osgi.service.prefs.PreferencesService;
import org.osgi.util.tracker.ServiceTracker;
import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.util.AbstractProperties;

public class BundlePreferences {

    private ServiceTracker tracker;
    private File dataFolder;

    public synchronized void init(final BundleContext context) throws Exception {
        if (tracker != null) {
            tracker.close();
        }
        dataFolder =
            new File(AbstractProperties.WEASIS_PATH + File.separator + "data", context.getBundle().getSymbolicName()); //$NON-NLS-1$
        tracker = new ServiceTracker(context, PreferencesService.class.getName(), null);
        tracker.open();
    }

    public final synchronized void close() {
        if (tracker != null) {
            tracker.close();
            tracker = null;
        }
    }

    public PreferencesService getPreferencesService() {
        // track only one service service
        return tracker == null ? null : (PreferencesService) tracker.getService();
    }

    public Preferences getDefaultPreferences() {
        String user = System.getProperty("weasis.user", null); //$NON-NLS-1$
        if (user == null) {
            return getSystemPreferences();
        }
        return getUserPreferences(user);
    }

    public Preferences getSystemPreferences() {
        PreferencesService service = getPreferencesService();
        return service == null ? null : service.getSystemPreferences();
    }

    public Preferences getUserPreferences(String name) {
        PreferencesService service = getPreferencesService();
        return service == null ? null : service.getUserPreferences(name);
    }

    public File getDataFolder() {
        dataFolder.mkdirs();
        return dataFolder;
    }

    public static Preferences getDefaultPreferences(final BundleContext context) {
        String user = System.getProperty("weasis.user", null); //$NON-NLS-1$
        if (user == null) {
            return getSystemPreferences(context);
        }
        return getUserPreferences(context, user);
    }

    public static Preferences getSystemPreferences(final BundleContext context) {
        ServiceTracker track = new ServiceTracker(context, PreferencesService.class.getName(), null);
        track.open();
        PreferencesService service = (PreferencesService) track.getService();
        Preferences pref = service == null ? null : service.getSystemPreferences();
        track.close();
        return pref;
    }

    public static Preferences getUserPreferences(final BundleContext context, String name) {
        ServiceTracker track = new ServiceTracker(context, PreferencesService.class.getName(), null);
        track.open();
        PreferencesService service = (PreferencesService) track.getService();
        Preferences pref = service == null ? null : service.getUserPreferences(name);
        track.close();
        return pref;
    }

    public static void putStringPreferences(Preferences pref, String key, String value) {
        // Does not support null key or value
        if (pref != null && key != null && value != null) {
            String val2 = pref.get(key, null);
            // Update only if the value is different to avoid setting the changeSet to true
            if (val2 == null || !value.equals(val2)) {
                pref.put(key, value);
            }
        }
    }

    public static void putBooleanPreferences(Preferences pref, String key, boolean value) {
        // Does not support null key
        if (pref != null && key != null) {
            Boolean result = null;
            final String s = pref.get(key, null);
            if (s != null) {
                result = Boolean.valueOf(s);
            }
            // Update only if the value is different to avoid setting the changeSet to true
            if (result == null || result.booleanValue() != value) {
                pref.putBoolean(key, value);
            }
        }
    }

    public static void putByteArrayPreferences(Preferences pref, String key, byte[] value) {
        // Does not support null key or value
        if (pref != null && key != null && value != null) {
            byte[] val2 = pref.getByteArray(key, null);
            // Update only if the value is different to avoid setting the changeSet to true
            if (val2 == null || !Arrays.equals(value, val2)) {
                pref.putByteArray(key, value);
            }
        }
    }

    public static void putDoublePreferences(Preferences pref, String key, double value) {
        // Does not support null key
        if (pref != null && key != null) {
            Double result = null;
            final String s = pref.get(key, null);
            if (s != null) {
                try {
                    result = Double.parseDouble(s);
                } catch (NumberFormatException ignore) {
                }
            }
            // Update only if the value is different to avoid setting the changeSet to true
            if (result == null || result.doubleValue() != value) {
                pref.putDouble(key, value);
            }
        }
    }

    public static void putFloatPreferences(Preferences pref, String key, float value) {
        // Does not support null key
        if (pref != null && key != null) {
            Float result = null;
            final String s = pref.get(key, null);
            if (s != null) {
                try {
                    result = Float.parseFloat(s);
                } catch (NumberFormatException ignore) {
                }
            }
            // Update only if the value is different to avoid setting the changeSet to true
            if (result == null || result.floatValue() != value) {
                pref.putFloat(key, value);
            }
        }
    }

    public static void putIntPreferences(Preferences pref, String key, int value) {
        // Does not support null key
        if (pref != null && key != null) {
            Integer result = null;
            final String s = pref.get(key, null);
            if (s != null) {
                try {
                    result = Integer.parseInt(s);
                } catch (NumberFormatException ignore) {
                }
            }
            // Update only if the value is different to avoid setting the changeSet to true
            if (result == null || result.intValue() != value) {
                pref.putInt(key, value);
            }
        }
    }

    public static void putLongPreferences(Preferences pref, String key, long value) {
        // Does not support null key
        if (pref != null && key != null) {
            Long result = null;
            final String s = pref.get(key, null);
            if (s != null) {
                try {
                    result = Long.parseLong(s);
                } catch (NumberFormatException ignore) {
                }
            }
            // Update only if the value is different to avoid setting the changeSet to true
            if (result == null || result.longValue() != value) {
                pref.putLong(key, value);
            }
        }
    }
}
