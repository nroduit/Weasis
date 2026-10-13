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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.JsonObject;
import java.awt.Color;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.service.WProperties;

@DisplayNameGeneration(ReplaceUnderscores.class)
class SettingCatalogTest {
  private static final String GROUP = "test-catalog"; // NON-NLS

  @Test
  void the_catalog_describes_each_declared_setting() {
    Setting<DayOfWeek> day =
        Setting.builder(
                SettingType.of(DayOfWeek.class), GROUP, "test.catalog.day", DayOfWeek.MONDAY)
            .contextual()
            .since("5.0") // NON-NLS
            .description("First day of the week") // NON-NLS
            .declare();

    JsonObject json = entry(day.key());
    assertAll(
        () -> assertEquals(SettingCatalog.KIND, SettingCatalog.toJson().getString("kind")),
        () -> assertEquals(GROUP, json.getString("group")),
        () -> assertEquals("enum", json.getString("type")),
        () -> assertEquals(7, json.getJsonArray("values").size()),
        () -> assertEquals("MONDAY", json.getString("default")),
        () -> assertEquals("user", json.getString("scope")),
        () -> assertEquals("system-preferences", json.getString("store")),
        () -> assertTrue(json.getBoolean("lockable")),
        () -> assertTrue(json.getBoolean("contextual")),
        () -> assertEquals("5.0", json.getString("since")),
        () -> assertEquals("First day of the week", json.getString("description")));
  }

  @Test
  void a_secret_shows_no_default() {
    Setting<String> password =
        Setting.builder(SettingType.STRING, GROUP, "test.catalog.password", "changeit") // NON-NLS
            .scope(Scope.MACHINE)
            .secret()
            .declare();

    JsonObject json = entry(password.key());
    assertAll(
        () -> assertFalse(json.containsKey("default")),
        () -> assertTrue(json.getBoolean("secret")),
        () -> assertEquals("local-persistence", json.getString("store")));
  }

  @Test
  void a_conflicting_declaration_of_a_key_keeps_the_first() {
    Setting<Integer> first = Setting.integer(GROUP, "test.catalog.twice", 1, Scope.USER);
    Setting.string(GROUP, "test.catalog.twice", "1", Scope.USER); // NON-NLS
    Setting<Integer> reloaded = Setting.integer(GROUP, "test.catalog.twice", 1, Scope.USER);

    Optional<Setting<?>> found = SettingCatalog.find(first.key());
    assertAll(
        () -> assertEquals(SettingType.INTEGER, found.orElseThrow().type()),
        () -> assertSame(reloaded, found.orElseThrow()));
  }

  @Test
  void declare_registers_the_constants_of_a_holder() {
    SettingCatalog.declare(CoreSettings.class);
    assertTrue(SettingCatalog.find("weasis.confirm.closing").isPresent()); // NON-NLS
  }

  @Test
  void a_declaration_refuses_incoherent_flags() {
    assertAll(
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () ->
                    Setting.builder(SettingType.BOOLEAN, GROUP, "test.catalog.bad1", false)
                        .scope(Scope.SITE)
                        .contextual()
                        .declare()),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () ->
                    Setting.builder(SettingType.STRING, GROUP, "test.catalog.bad2", "")
                        .secret()
                        .contextual()
                        .declare()),
        () ->
            assertThrows(
                IllegalArgumentException.class, () -> Setting.bool(GROUP, " ", false, Scope.USER)),
        () -> assertTrue(SettingCatalog.find("test.catalog.bad1").isEmpty()));
  }

  @Test
  void types_read_the_text_the_existing_stores_hold() {
    Color translucent = new Color(10, 20, 30, 128);
    assertAll(
        () -> assertEquals(Optional.of(true), SettingType.BOOLEAN.parse(" TRUE ")),
        () -> assertTrue(SettingType.BOOLEAN.parse("yes").isEmpty()),
        () -> assertTrue(SettingType.INTEGER.parse("12px").isEmpty()),
        () -> assertTrue(SettingType.INTEGER.parse(null).isEmpty()),
        () ->
            assertEquals(
                Optional.of(translucent),
                SettingType.COLOR.parse(WProperties.color2Hexadecimal(translucent, true))),
        () -> assertEquals(Optional.of(new Color(0xff8000)), SettingType.COLOR.parse("ff8000")),
        () -> assertTrue(SettingType.COLOR.parse("orange").isEmpty()),
        () ->
            assertEquals(
                Optional.of(DayOfWeek.FRIDAY), SettingType.of(DayOfWeek.class).parse("FRIDAY")),
        () -> assertTrue(SettingType.of(DayOfWeek.class).parse("friday").isEmpty()));
  }

  private static JsonObject entry(String key) {
    List<JsonObject> found =
        SettingCatalog.toJson().getJsonArray("settings").getValuesAs(JsonObject.class).stream()
            .filter(o -> key.equals(o.getString("key")))
            .toList();
    assertEquals(1, found.size());
    return found.getFirst();
  }
}
