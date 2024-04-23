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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;
import org.weasis.launcher.FileUtil;
import org.weasis.launcher.Utils;

public class AppPreferences extends HashMap<String, Preference> {
  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(AppPreferences.class);
  private static final String DELIM_START = "${";
  private static final String DELIM_STOP = "}";
  static final String CODE = "code"; // NON-NLS
  static final String VAL = "value"; // NON-NLS
  static final String DESC = "description"; // NON-NLS
  static final String TYPE = "type"; // NON-NLS
  static final String JAVA_TYPE = "javaType"; // NON-NLS
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
      LOGGER.error("Cannot read json file: {}", uri, ex);
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

  public String getValue(String key) {
    return Optional.ofNullable(get(key)).map(Preference::getValue).orElse(null);
  }

  public String getValue(String key, String defaultVal) {
    return Optional.ofNullable(get(key))
        .map(Preference::getValue)
        .filter(Utils::hasText)
        .orElse(defaultVal);
  }

  public String substVars(String val, String currentKey, Map<String, String> cycleMap)
      throws IllegalArgumentException {
    if (cycleMap == null) {
      cycleMap = new HashMap<>();
    }
    cycleMap.put(currentKey, currentKey);
    if (val == null) {
      return StringUtil.EMPTY_STRING;
    }

    int stopDelim = -1;
    int startDelim;

    do {
      stopDelim = val.indexOf(DELIM_STOP, stopDelim + 1);
      if (stopDelim < 0) {
        return val;
      }
      startDelim = val.lastIndexOf(DELIM_START, stopDelim);
      if (startDelim < 0) {
        return val;
      }
    } while (startDelim > stopDelim);

    String variable = val.substring(startDelim + DELIM_START.length(), stopDelim);

    if (cycleMap.get(variable) != null) {
      throw new IllegalArgumentException("recursive variable reference: " + variable);
    }

    String substValue = getValue(variable);
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
