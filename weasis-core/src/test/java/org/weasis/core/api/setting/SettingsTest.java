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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.setting.Settings.Origin;
import org.weasis.core.api.setting.Settings.Resolution;

@DisplayNameGeneration(ReplaceUnderscores.class)
class SettingsTest {
  private static final String GROUP = "test-settings"; // NON-NLS

  static final Setting<Boolean> CONFIRM =
      Setting.bool(GROUP, "test.settings.confirm", false, Scope.USER); // NON-NLS
  static final Setting<Integer> WIDTH =
      Setting.integer(GROUP, "test.settings.width", 0, Scope.MACHINE); // NON-NLS
  static final Setting<String> SERVER =
      Setting.string(GROUP, "test.settings.server", "none", Scope.SITE); // NON-NLS
  static final Setting<Integer> LAYOUT =
      Setting.builder(SettingType.INTEGER, GROUP, "test.settings.layout", 1) // NON-NLS
          .contextual()
          .declare();
  static final Setting<Boolean> FREE =
      Setting.builder(SettingType.BOOLEAN, GROUP, "test.settings.free", false) // NON-NLS
          .notLockable()
          .declare();

  private final Map<String, String> site = new HashMap<>();

  @BeforeEach
  void installEmptyEnvironment() {
    site.clear();
    Settings.install(SettingEnvironment.inMemory(site));
  }

  @Test
  void the_default_applies_when_no_layer_defines_a_value() {
    Resolution<Boolean> resolution = Settings.resolve(CONFIRM);
    assertAll(
        () -> assertFalse(resolution.value()),
        () -> assertEquals(Origin.DEFAULT, resolution.origin()),
        () -> assertFalse(resolution.locked()));
  }

  @Test
  void a_stored_value_wins_over_the_site_and_the_site_over_the_default() {
    site.put(CONFIRM.key(), "true");
    assertEquals(new Resolution<>(true, Origin.SITE, false), Settings.resolve(CONFIRM));

    assertTrue(Settings.set(CONFIRM, false));
    assertEquals(new Resolution<>(false, Origin.USER, false), Settings.resolve(CONFIRM));

    assertTrue(Settings.reset(CONFIRM));
    assertEquals(Origin.SITE, Settings.resolve(CONFIRM).origin());
  }

  @Test
  void a_machine_setting_reports_the_machine_layer() {
    Settings.set(WIDTH, 240);
    assertEquals(new Resolution<>(240, Origin.MACHINE, false), Settings.resolve(WIDTH));
  }

  @Test
  void a_value_that_does_not_parse_falls_through_to_the_next_layer() {
    site.put(WIDTH.key(), "300");
    SettingEnvironment env = SettingEnvironment.inMemory(site);
    Settings.install(env);
    env.store(WIDTH).put(WIDTH.key(), "wide");

    assertEquals(new Resolution<>(300, Origin.SITE, false), Settings.resolve(WIDTH));
  }

  @Test
  void the_computed_default_replaces_only_the_declared_one() {
    assertEquals(350, Settings.get(WIDTH, () -> 350));
    Settings.set(WIDTH, 120);
    assertEquals(120, Settings.get(WIDTH, () -> 350));
  }

  @Test
  void a_site_lock_imposes_the_site_value_and_refuses_writes() {
    Settings.set(CONFIRM, false);
    site.put(CONFIRM.key(), "true");
    site.put(Settings.LOCKED_PROPERTY, " other.key , " + CONFIRM.key());

    assertAll(
        () -> assertEquals(new Resolution<>(true, Origin.SITE, true), Settings.resolve(CONFIRM)),
        () -> assertTrue(Settings.isLocked(CONFIRM)),
        () -> assertFalse(Settings.set(CONFIRM, false)),
        () -> assertFalse(Settings.reset(CONFIRM)));
  }

  @Test
  void a_lock_without_site_value_imposes_the_default() {
    Settings.set(CONFIRM, true);
    site.put(Settings.LOCKED_PROPERTY, CONFIRM.key());

    assertEquals(new Resolution<>(false, Origin.DEFAULT, true), Settings.resolve(CONFIRM));
  }

  @Test
  void a_setting_declared_not_lockable_ignores_the_lock() {
    site.put(Settings.LOCKED_PROPERTY, FREE.key());

    assertAll(
        () -> assertFalse(Settings.isLocked(FREE)),
        () -> assertTrue(Settings.set(FREE, true)),
        () -> assertEquals(new Resolution<>(true, Origin.USER, false), Settings.resolve(FREE)));
  }

  @Test
  void a_site_setting_is_read_from_the_site_and_never_written() {
    site.put(SERVER.key(), "https://hub.example"); // NON-NLS

    assertAll(
        () -> assertEquals("https://hub.example", Settings.get(SERVER)), // NON-NLS
        () -> assertFalse(Settings.set(SERVER, "other")), // NON-NLS
        () -> assertFalse(Settings.reset(SERVER)));
  }

  @Test
  void contexts_stack_over_the_stored_value_until_closed() {
    Settings.set(LAYOUT, 2);
    Settings.Context protocol = Settings.applyContext("protocol", Map.of(LAYOUT, 4)); // NON-NLS
    Settings.Context session = Settings.applyContext("session", Map.of(LAYOUT, 6)); // NON-NLS

    assertEquals(new Resolution<>(6, Origin.CONTEXT, false), Settings.resolve(LAYOUT));
    session.close();
    assertEquals(4, Settings.get(LAYOUT));
    protocol.close();
    protocol.close();
    assertEquals(new Resolution<>(2, Origin.USER, false), Settings.resolve(LAYOUT));
  }

  @Test
  void a_context_accepts_contextual_settings_only() {
    Map<Setting<?>, Object> values = Map.of(CONFIRM, true);
    assertThrows(IllegalArgumentException.class, () -> Settings.applyContext("protocol", values));
  }

  @Test
  void a_site_lock_wins_over_a_context() {
    site.put(Settings.LOCKED_PROPERTY, LAYOUT.key());
    try (Settings.Context ignored = Settings.applyContext("protocol", Map.of(LAYOUT, 4))) {
      assertEquals(new Resolution<>(1, Origin.DEFAULT, true), Settings.resolve(LAYOUT));
    }
  }

  @Test
  void listeners_receive_the_value_in_force_after_each_change() {
    List<Integer> seen = new ArrayList<>();
    Consumer<Integer> listener = seen::add;
    Settings.addListener(LAYOUT, listener);
    try {
      Settings.set(LAYOUT, 3);
      try (Settings.Context ignored = Settings.applyContext("protocol", Map.of(LAYOUT, 5))) {
        Settings.reset(LAYOUT);
      }
    } finally {
      Settings.removeListener(LAYOUT, listener);
    }
    // set, context applied, reset under the context, context closed
    assertEquals(List.of(3, 5, 5, 1), seen);
  }

  @Test
  void the_properties_store_ignores_the_fallbacks_of_wproperties() {
    WProperties systemPreferences = new WProperties(Map.of(CONFIRM.key(), "true"));
    SettingEnvironment env =
        SettingEnvironment.of(systemPreferences, new Properties(), g -> null, Map.of());

    assertNull(env.store(CONFIRM).get(CONFIRM.key()));
    env.store(CONFIRM).put(CONFIRM.key(), "false");
    assertEquals("false", systemPreferences.getProperty(CONFIRM.key()));
  }

  @Test
  void a_java_system_property_wins_over_the_launch_configuration() {
    String key = "test.settings.system.property"; // NON-NLS
    SettingEnvironment env =
        SettingEnvironment.of(
            new Properties(), new Properties(), g -> null, Map.of(key, "launch")); // NON-NLS
    assertEquals("launch", env.site(key));
    System.setProperty(key, "system"); // NON-NLS
    try {
      assertEquals("system", env.site(key));
    } finally {
      System.clearProperty(key);
    }
  }
}
