/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.pref;

import com.formdev.flatlaf.json.Json;
import com.formdev.flatlaf.json.ParseException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.weasis.launcher.FileUtil;
import org.weasis.launcher.Utils;

public class AppPreferences extends HashMap<String, Preference> {
  private static final Logger LOGGER = System.getLogger(AppPreferences.class.getName());
  private static final String DELIM_START = "${";
  private static final String DELIM_STOP = "}";
  static final String CODE = "code"; // NON-NLS
  static final String VAL = "value"; // NON-NLS
  static final String DESC = "description"; // NON-NLS
  static final String TYPE = "type"; // NON-NLS
  static final String JAVA_TYPE = "JavaType"; // NON-NLS
  static final String DEFAULT = "defaultValue"; // NON-NLS
  static final String CATEGORY = "category"; // NON-NLS

  public AppPreferences() {
    super(50);
  }

  public AppPreferences(Map<String, Preference> m) {
    super(m);
  }

  public void readJson(URI uri) {
    Map<String, Object> json;
    try (InputStream is = FileUtil.getAdaptedConnection(uri.toURL(), false).getInputStream();
        Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
      json = (Map<String, Object>) Json.parse(reader);
    } catch (ParseException | IOException ex) {
      LOGGER.log(
          Level.ERROR,
          () -> String.format("Cannot read json file: %s", uri), // NON-NLS
          ex);
      return;
    }

    for (Map<String, Object> map : (List<Map<String, Object>>) json.get("weasisPreferences")) {
      String code = (String) map.get(CODE);
      Preference p =
          new Preference(
              code,
              (String) map.get(TYPE),
              (String) map.get(JAVA_TYPE),
              (String) map.get(CATEGORY));

      p.setValue((String) map.get(VAL));
      p.setDescription((String) map.get(DESC));
      p.setDefaultValue((String) map.get(DEFAULT));
      put(code, p);
    }
  }

  public String getProperty(String key) {
    Preference p = get(key);
    if (p != null) {
      return p.getValue();
    }
    return null;
  }

  public String getProperty(String key, String defaultVal) {
    String val = null;
    Preference p = get(key);
    if (p != null) {
      val = p.getValue();
    }
    return Utils.hasText(val) ? val : defaultVal;
  }

  public String substVars(String val, String currentKey, Map<String, String> cycleMap)
      throws IllegalArgumentException {
    if (cycleMap == null) {
      cycleMap = new HashMap<>();
    }
    cycleMap.put(currentKey, currentKey);

    int stopDelim = -1;
    int startDelim = -1;

    do {
      stopDelim = val.indexOf(DELIM_STOP, stopDelim + 1);
      if (stopDelim < 0) {
        return val;
      }
      startDelim = val.indexOf(DELIM_START);
      if (startDelim < 0) {
        return val;
      }
      while (true) {
        int idx = val.indexOf(DELIM_START, startDelim + DELIM_START.length());
        if ((idx < 0) || (idx > stopDelim)) {
          break;
        } else if (idx < stopDelim) {
          startDelim = idx;
        }
      }
    } while (startDelim > stopDelim);

    String variable = val.substring(startDelim + DELIM_START.length(), stopDelim);

    if (cycleMap.get(variable) != null) {
      throw new IllegalArgumentException("recursive variable reference: " + variable);
    }

    String substValue = getProperty(variable);
    if (substValue == null) {
      // Ignore unknown property values.
      substValue = System.getProperty(variable, "");
    }

    // Remove the found variable from the cycle map, since
    // it may appear more than once in the value and we don't
    // want such situations to appear as a recursive reference.
    cycleMap.remove(variable);
    val =
        val.substring(0, startDelim) + substValue + val.substring(stopDelim + DELIM_STOP.length());
    val = substVars(val, currentKey, cycleMap);
    return val;
  }
}
