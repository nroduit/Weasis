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

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every declared setting, by key. A setting registers itself when its declaring class is
 * initialized; a plugin calls {@link #declare(Class[])} at start so that its settings are listed
 * before any of them is read.
 *
 * <p>The catalog is what an administrator and Viewer Hub see: {@link #toJson()} gives type,
 * default, scope and flags of each key, so a site configuration can be edited as typed values and
 * the {@code base.json} documentation generated from it.
 */
public final class SettingCatalog {
  private static final Logger LOGGER = LoggerFactory.getLogger(SettingCatalog.class);

  /** Kind and schema of the catalog document. */
  public static final String KIND = "weasis.catalog"; // NON-NLS

  public static final int SCHEMA = 1;

  private static final Map<String, Setting<?>> SETTINGS = new ConcurrentHashMap<>();

  private SettingCatalog() {}

  /**
   * Registers a declaration. A second declaration of a key with the same group, type and scope
   * replaces the first (a plugin reloaded by a new class loader); one that differs is refused and
   * logged, the first stays in the catalog.
   */
  static <T> Setting<T> register(Setting<T> setting) {
    Setting<?> known = SETTINGS.putIfAbsent(setting.key(), setting);
    if (known != null && known != setting) {
      if (sameShape(known, setting)) {
        SETTINGS.put(setting.key(), setting);
      } else {
        LOGGER.error(
            "Setting {} declared twice: {} {} {} kept, {} {} {} refused",
            setting.key(),
            known.group(),
            known.type(),
            known.scope().id(),
            setting.group(),
            setting.type(),
            setting.scope().id());
      }
    }
    return setting;
  }

  /** Initializes the classes that declare settings, so that their constants are registered. */
  public static void declare(Class<?>... holders) {
    for (Class<?> holder : holders) {
      try {
        Class.forName(holder.getName(), true, holder.getClassLoader());
      } catch (ClassNotFoundException e) {
        LOGGER.error("Cannot load the settings of {}", holder.getName(), e);
      }
    }
  }

  public static Optional<Setting<?>> find(String key) {
    return key == null ? Optional.empty() : Optional.ofNullable(SETTINGS.get(key));
  }

  /** Every declaration, sorted by group then key. */
  public static List<Setting<?>> all() {
    return SETTINGS.values().stream()
        .sorted(Comparator.comparing(Setting<?>::group).thenComparing(Setting::key))
        .toList();
  }

  /** The catalog document: kind, schema and one object per setting. */
  public static JsonObject toJson() {
    JsonArrayBuilder settings = Json.createArrayBuilder();
    all().forEach(s -> settings.add(toJson(s)));
    return Json.createObjectBuilder()
        .add("kind", KIND) // NON-NLS
        .add("schema", SCHEMA) // NON-NLS
        .add("settings", settings) // NON-NLS
        .build();
  }

  static <T> JsonObject toJson(Setting<T> setting) {
    JsonObjectBuilder o =
        Json.createObjectBuilder()
            .add("key", setting.key()) // NON-NLS
            .add("group", setting.group()) // NON-NLS
            .add("type", setting.type().name()); // NON-NLS
    if (!setting.type().values().isEmpty()) {
      o.add("values", Json.createArrayBuilder(setting.type().values())); // NON-NLS
    }
    if (setting.defaultValue() != null && !setting.secret()) {
      o.add("default", setting.type().format(setting.defaultValue())); // NON-NLS
    }
    o.add("scope", setting.scope().id()) // NON-NLS
        .add("store", setting.backing().id()) // NON-NLS
        .add("lockable", setting.lockable()) // NON-NLS
        .add("secret", setting.secret()) // NON-NLS
        .add("contextual", setting.contextual()); // NON-NLS
    if (setting.since() != null) {
      o.add("since", setting.since()); // NON-NLS
    }
    if (setting.description() != null) {
      o.add("description", setting.description()); // NON-NLS
    }
    return o.build();
  }

  private static boolean sameShape(Setting<?> a, Setting<?> b) {
    return a.group().equals(b.group())
        && a.type().name().equals(b.type().name())
        && a.scope() == b.scope();
  }

  /** Test hook: forgets a declaration. */
  static void unregister(String key) {
    SETTINGS.remove(key);
  }
}
