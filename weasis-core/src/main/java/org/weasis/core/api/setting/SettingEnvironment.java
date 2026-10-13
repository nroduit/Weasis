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
import java.util.function.Function;
import java.util.prefs.Preferences;

/**
 * What the resolver reads from: the store of each setting, and the site values. Installed once by
 * the application with {@link Settings#install(SettingEnvironment)}; a test installs one in memory.
 */
public interface SettingEnvironment {

  /** The store that holds the stored value of a user or machine setting. */
  SettingStore store(Setting<?> setting);

  /**
   * The site value of a key: a Java system property (command line, launcher arguments), else the
   * launch configuration ({@code base.json} with the Viewer Hub overrides); null when neither
   * defines it.
   */
  String site(String key);

  /**
   * The environment of the running application.
   *
   * @param systemPreferences {@code weasis.properties}
   * @param localPersistence {@code UICore.getLocalPersistence()}, saved in the machine store
   * @param nodes the {@code java.util.prefs} root node of a group
   * @param launchConfiguration the values of {@code base.json} after the Viewer Hub overrides
   */
  static SettingEnvironment of(
      Properties systemPreferences,
      Properties localPersistence,
      Function<String, Preferences> nodes,
      Map<String, String> launchConfiguration) {
    SettingStore system = SettingStore.of(systemPreferences);
    SettingStore local = SettingStore.of(localPersistence);
    return new SettingEnvironment() {
      @Override
      public SettingStore store(Setting<?> setting) {
        Backing backing = setting.backing();
        if (backing == Backing.SYSTEM_PREFERENCES) {
          return system;
        }
        if (backing == Backing.LOCAL_PERSISTENCE) {
          return local;
        }
        if (backing instanceof Backing.PreferenceNode(String path)) {
          Preferences root = nodes.apply(setting.group());
          return SettingStore.of(path.isEmpty() ? root : root.node(path));
        }
        throw new IllegalArgumentException(setting.key() + " has no store");
      }

      @Override
      public String site(String key) {
        String value = System.getProperty(key);
        return value != null || launchConfiguration == null ? value : launchConfiguration.get(key);
      }
    };
  }

  /**
   * An environment in memory: one store per backing and group, site values from the given map only
   * (no system properties, so a test does not depend on the JVM it runs in).
   */
  static SettingEnvironment inMemory(Map<String, String> site) {
    Map<String, SettingStore> stores = new ConcurrentHashMap<>();
    return new SettingEnvironment() {
      @Override
      public SettingStore store(Setting<?> setting) {
        String id = setting.backing() instanceof Backing.PreferenceNode ? setting.group() : "";
        return stores.computeIfAbsent(
            setting.backing().id() + "|" + id, k -> SettingStore.inMemory());
      }

      @Override
      public String site(String key) {
        return site.get(key);
      }
    };
  }
}
