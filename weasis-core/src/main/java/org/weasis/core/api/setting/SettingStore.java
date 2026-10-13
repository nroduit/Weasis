/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.setting;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.Preferences;

/**
 * The stored text values of one store, by key. A store only holds what was written into it: no
 * fallback to system properties or to the launch configuration, which are the site layer of the
 * resolver. Saving the store to disk stays with its owner.
 */
public interface SettingStore {

  /** The stored text, or null when the store holds none. */
  String get(String key);

  void put(String key, String value);

  void remove(String key);

  /** The values written into a {@link Properties}, read without its defaults. */
  static SettingStore of(Properties properties) {
    return new SettingStore() {
      @Override
      public String get(String key) {
        // Hashtable.get, not getProperty: WProperties falls back to the system and site values
        return properties.get(key) instanceof String s ? s : null;
      }

      @Override
      public void put(String key, String value) {
        properties.setProperty(key, value);
      }

      @Override
      public void remove(String key) {
        properties.remove(key);
      }
    };
  }

  /** The keys of one {@code java.util.prefs} node. */
  static SettingStore of(Preferences node) {
    return new SettingStore() {
      @Override
      public String get(String key) {
        return node.get(key, null);
      }

      @Override
      public void put(String key, String value) {
        node.put(key, value);
      }

      @Override
      public void remove(String key) {
        node.remove(key);
      }
    };
  }

  /** A store in memory, for tests and for code running before the application is set up. */
  static SettingStore inMemory() {
    Map<String, String> values = new ConcurrentHashMap<>();
    return new SettingStore() {
      @Override
      public String get(String key) {
        return values.get(key);
      }

      @Override
      public void put(String key, String value) {
        values.put(key, value);
      }

      @Override
      public void remove(String key) {
        values.remove(key);
      }
    };
  }
}
