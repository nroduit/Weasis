/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui;

import java.util.List;
import java.util.Properties;
import org.osgi.service.prefs.Preferences;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.BundleTools;

public class InsertableUtil {
  public static final String ALL_BUNDLE = "weasis"; // NON-NLS
  public static final String ALL = "all"; // NON-NLS

  private InsertableUtil() {}

  public static void sortInsertable(List<? extends Insertable> list) {
    list.sort(
        (o1, o2) -> {
          int val1 = o1.getComponentPosition();
          int val2 = o2.getComponentPosition();
          return Integer.compare(val1, val2);
        });
  }

  public static void applyPreferences(
      List<? extends Insertable> list,
      Preferences prefs,
      String bundleName,
      String componentName,
      Type type) {
    if (list != null && prefs != null && bundleName != null && componentName != null) {
      Preferences prefNode = prefs.node(componentName).node(type.name().toLowerCase() + "s");
      synchronized (list) { // NOSONAR lock object is the list for iterating its elements safely
        for (Insertable c : list) {
          if (!Type.EMPTY.equals(c.getType())) {
            String nodeName = getCName(c.getClass());
            String key = "visible"; // NON-NLS
            Preferences node = prefNode.node(nodeName);
            String valString = node.get(key, null);
            // If not specify, value is true
            boolean val = true;
            if (valString == null) {
              val =
                  getBooleanProperty(
                      BundleTools.SYSTEM_PREFERENCES,
                      bundleName,
                      componentName,
                      nodeName,
                      key,
                      val);
            } else if (Boolean.FALSE.toString().equalsIgnoreCase(valString)) {
              val = false;
            }
            c.setComponentEnabled(val);

            key = "cPosition";
            valString = node.get(key, null);
            // If not specify, value is true
            int index = c.getComponentPosition();
            if (valString == null) {
              index =
                  getIntProperty(
                      BundleTools.SYSTEM_PREFERENCES,
                      bundleName,
                      componentName,
                      nodeName,
                      key,
                      index);
            } else {
              try {
                index = Integer.parseInt(valString);
              } catch (NumberFormatException ignore) {
                // return the default value
              }
            }
            c.setComponentPosition(index);
          }
        }
      }
    }
  }

  public static void savePreferences(
      List<? extends Insertable> list, Preferences prefs, Type type) {
    if (list != null && prefs != null) {
      Preferences prefNode = prefs.node(type.name().toLowerCase() + "s");
      synchronized (list) { // NOSONAR lock object is the list for iterating its elements safely
        for (Insertable c : list) {
          if (!Type.EMPTY.equals(c.getType())) {
            String cname = getCName(c.getClass());
            Preferences node = prefNode.node(cname);
            BundlePreferences.putBooleanPreferences(
                node, "visible", c.isComponentEnabled()); // NON-NLS
            BundlePreferences.putIntPreferences(node, "cPosition", c.getComponentPosition());
          }
        }
      }
    }
  }

  public static boolean getBooleanProperty(
      Properties props,
      String bundleName,
      String className,
      String componentName,
      String key,
      boolean def) {
    if (props != null && bundleName != null && className != null && key != null) {
      for (String bundle : new String[] {bundleName, ALL_BUNDLE}) {
        for (String cl : new String[] {className, ALL}) {
          StringBuilder buf = new StringBuilder(bundle);
          buf.append('.');
          buf.append(cl);
          buf.append('.');
          buf.append(componentName);
          buf.append('.');
          buf.append(key);
          final String value = props.getProperty(buf.toString());
          if (value != null) {
            if (Boolean.TRUE.toString().equalsIgnoreCase(value)) {
              return true;
            } else if (Boolean.FALSE.toString().equalsIgnoreCase(value)) {
              return false;
            }
          }
        }
      }
    }
    return def;
  }

  private static int getIntProperty(
      Properties props,
      String bundleName,
      String className,
      String componentName,
      String key,
      int def) {
    if (props != null && bundleName != null && className != null && key != null) {
      for (String bundle : new String[] {bundleName, ALL_BUNDLE}) {
        for (String cl : new String[] {className, ALL}) {
          StringBuilder buf = new StringBuilder(bundle);
          buf.append('.');
          buf.append(cl);
          buf.append('.');
          buf.append(componentName);
          buf.append('.');
          buf.append(key);
          final String value = props.getProperty(buf.toString());
          if (value != null) {
            try {
              return Integer.parseInt(value);
            } catch (NumberFormatException ignore) {
              // returns the default value
            }
          }
        }
      }
    }
    return def;
  }

  public static String getCName(Class<?> clazz) {
    if (clazz != null) {
      return clazz.getSimpleName().toLowerCase();
    }
    return "";
  }
}
