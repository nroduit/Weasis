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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayNameGeneration(ReplaceUnderscores.class)
class UserSettingsStoreTest {
  private static final String GROUP = "weasis-core"; // NON-NLS
  private static final long NO_DELAY = 60_000; // saves only on flush in these tests

  private static UserSettingsStore configured(Path dir) {
    UserSettingsStore store = new UserSettingsStore(NO_DELAY);
    store.configure(dir, List.of());
    return store;
  }

  private static JsonObject read(Path file) throws IOException {
    return Json.createReader(new StringReader(Files.readString(file))).readObject();
  }

  @Test
  void a_group_is_saved_as_one_json_file_with_its_nodes(@TempDir Path dir) throws IOException {
    UserSettingsStore store = configured(dir);
    Preferences core = store.group(GROUP);
    core.put("weasis.theme", "dark"); // NON-NLS
    core.node("view2d.default/font").putInt("size", 12); // NON-NLS
    core.node("empty.node"); // asked for, never written
    store.flushAll();

    JsonObject json = read(dir.resolve("weasis-core.json"));
    assertAll(
        () -> assertEquals("/org/weasis/weasis-core", core.absolutePath()),
        () -> assertEquals(UserSettingsStore.KIND, json.getString("kind")),
        () -> assertEquals(GROUP, json.getString("id")),
        () -> assertEquals(UserSettingsStore.SCHEMA, json.getInt("schema")),
        () -> assertTrue(json.containsKey("updated")),
        () -> assertEquals("dark", json.getJsonObject("values").getString("weasis.theme")),
        () ->
            assertEquals(
                "12",
                json.getJsonObject("nodes")
                    .getJsonObject("view2d.default")
                    .getJsonObject("nodes")
                    .getJsonObject("font")
                    .getJsonObject("values")
                    .getString("size")),
        () -> assertFalse(json.getJsonObject("nodes").containsKey("empty.node")));
  }

  @Test
  void a_saved_group_is_read_back_by_the_next_session(@TempDir Path dir)
      throws BackingStoreException {
    UserSettingsStore first = configured(dir);
    first.group(GROUP).node("zoom").putDouble("factor", 1.5); // NON-NLS
    first.group("weasis-dicom-viewer2d").putBoolean("mpr", true); // NON-NLS
    first.flushAll();

    UserSettingsStore next = configured(dir);
    assertAll(
        () -> assertEquals(1.5, next.group(GROUP).node("zoom").getDouble("factor", 0)),
        () -> assertArrayEquals(new String[] {"zoom"}, next.group(GROUP).childrenNames()),
        () -> assertTrue(next.group("weasis-dicom-viewer2d").getBoolean("mpr", false)),
        () -> assertTrue(Files.isRegularFile(dir.resolve("weasis-dicom-viewer2d.json"))));
  }

  @Test
  void the_earlier_store_is_copied_once_and_left_as_it_was(@TempDir Path dir) throws IOException {
    UserSettingsStore earlier = new UserSettingsStore(NO_DELAY); // stands for the JDK store
    earlier.group(GROUP).put("weasis.theme", "light"); // NON-NLS
    earlier.group(GROUP).node("mouse.action").put("left", "pan"); // NON-NLS

    UserSettingsStore store = new UserSettingsStore(NO_DELAY);
    store.configure(dir, List.of(LegacySettings.Source.of("test", earlier::group)));
    Preferences core = store.group(GROUP);
    store.flushAll();
    assertAll(
        () -> assertEquals("light", core.get("weasis.theme", null)),
        () -> assertEquals("pan", core.node("mouse.action").get("left", null)),
        () -> assertTrue(Files.isRegularFile(dir.resolve("weasis-core.json"))));

    core.remove("weasis.theme");
    store.flushAll();
    UserSettingsStore next = new UserSettingsStore(NO_DELAY);
    next.configure(dir, List.of(LegacySettings.Source.of("test", earlier::group)));
    assertAll(
        () -> assertNull(next.group(GROUP).get("weasis.theme", null)),
        () -> assertEquals("light", earlier.group(GROUP).get("weasis.theme", null)));
  }

  @Test
  void a_group_without_earlier_values_is_still_written_once(@TempDir Path dir) {
    UserSettingsStore store = configured(dir);
    store.group("weasis-acquire"); // NON-NLS
    store.flushAll();
    assertTrue(Files.isRegularFile(dir.resolve("weasis-acquire.json")));
  }

  @Test
  void values_set_before_the_folder_is_known_win_over_the_file(@TempDir Path dir)
      throws IOException {
    UserSettingsStore first = configured(dir);
    first.group(GROUP).put("a", "file"); // NON-NLS
    first.group(GROUP).put("b", "file"); // NON-NLS
    first.flushAll();

    UserSettingsStore early = new UserSettingsStore(NO_DELAY);
    early.group(GROUP).put("a", "memory"); // NON-NLS
    early.configure(dir, List.of());
    early.flushAll();

    JsonObject values = read(dir.resolve("weasis-core.json")).getJsonObject("values");
    assertAll(
        () -> assertEquals("memory", values.getString("a")),
        () -> assertEquals("file", values.getString("b")));
  }

  @Test
  void a_file_that_does_not_parse_is_set_aside(@TempDir Path dir) throws IOException {
    Files.writeString(dir.resolve("weasis-core.json"), "{ not json"); // NON-NLS
    UserSettingsStore store = configured(dir);
    store.group(GROUP).put("a", "1"); // NON-NLS
    store.flushAll();

    assertAll(
        () -> assertEquals("{ not json", Files.readString(dir.resolve("weasis-core.json.invalid"))),
        () ->
            assertEquals(
                "1", read(dir.resolve("weasis-core.json")).getJsonObject("values").getString("a")));
  }

  @Test
  void a_file_of_a_newer_schema_is_read_and_never_overwritten(@TempDir Path dir)
      throws IOException {
    String newer =
        "{\"kind\":\"weasis.settings\",\"id\":\"weasis-core\",\"schema\":99," // NON-NLS
            + "\"values\":{\"a\":\"future\"}}"; // NON-NLS
    Path file = Files.writeString(dir.resolve("weasis-core.json"), newer);
    UserSettingsStore store = configured(dir);
    Preferences core = store.group(GROUP);
    core.put("b", "2"); // NON-NLS
    store.flushAll();

    assertAll(
        () -> assertEquals("future", core.get("a", null)),
        () -> assertEquals(newer, Files.readString(file)));
  }

  @Test
  void a_removed_node_leaves_the_file(@TempDir Path dir) throws Exception {
    UserSettingsStore store = configured(dir);
    Preferences core = store.group(GROUP);
    core.node("keyboard.shortcuts.linux").put("zoom", "Z"); // NON-NLS
    core.node("zoom").put("factor", "2"); // NON-NLS
    store.flushAll();
    core.node("keyboard.shortcuts.linux").removeNode();
    store.flushAll();

    JsonObject nodes = read(dir.resolve("weasis-core.json")).getJsonObject("nodes");
    assertAll(
        () -> assertFalse(nodes.containsKey("keyboard.shortcuts.linux")),
        () -> assertTrue(nodes.containsKey("zoom")));
  }

  @Test
  void a_change_is_saved_shortly_without_a_flush(@TempDir Path dir) throws Exception {
    UserSettingsStore store = new UserSettingsStore(20);
    store.configure(dir, List.of());
    store.group(GROUP).put("a", "1"); // NON-NLS

    Path file = dir.resolve("weasis-core.json");
    // The group file is first written empty (nothing to copy), then with the value
    for (int i = 0;
        i < 100 && !(Files.exists(file) && Files.readString(file).contains("\"a\""));
        i++) {
      Thread.sleep(20);
    }
    assertEquals("1", read(file).getJsonObject("values").getString("a"));
  }

  @Test
  void the_4x_files_of_the_profile_fill_what_the_jdk_store_lacks(@TempDir Path prefDir)
      throws IOException {
    Files.writeString(
        prefDir.resolve("weasis-core.xml"),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><preferences>" // NON-NLS
            + "<zoom><factor>2</factor></zoom><theme>light</theme><empty/></preferences>"); // NON-NLS
    Files.writeString(
        prefDir.resolve("weasis-core-ui.xml"),
        "<preferences><zoom><factor>3</factor><mode>fit</mode></zoom></preferences>"); // NON-NLS
    UserSettingsStore earlier = new UserSettingsStore(NO_DELAY); // stands for the JDK store
    earlier.group(GROUP).put("theme", "dark"); // NON-NLS

    UserSettingsStore store = new UserSettingsStore(NO_DELAY);
    store.configure(
        prefDir.resolve(UserSettingsStore.FOLDER),
        List.of(
            LegacySettings.Source.of("test", earlier::group),
            (group, into) -> LegacySettings.copy4x(prefDir, group, into)));
    Preferences core = store.group(GROUP);

    assertAll(
        () -> assertEquals("dark", core.get("theme", null)), // the JDK store is newer
        () -> assertEquals("2", core.node("zoom").get("factor", null)), // weasis-core.xml first
        () -> assertEquals("fit", core.node("zoom").get("mode", null)), // then weasis-core-ui
        () -> assertNull(core.get("empty", null)),
        () -> assertTrue(Files.isRegularFile(prefDir.resolve("weasis-core.xml"))));
  }

  @Test
  void a_4x_file_that_does_not_parse_is_skipped(@TempDir Path prefDir) throws IOException {
    Files.writeString(
        prefDir.resolve("weasis-dicom-viewer2d.xml"), "<preferences><broken>"); // NON-NLS
    SettingsNode.Data data = new SettingsNode.Data();
    assertFalse(LegacySettings.copy4x(prefDir, "weasis-dicom-viewer2d", data)); // NON-NLS
  }

  @Test
  void a_group_id_is_made_a_safe_file_name() {
    assertAll(
        () -> assertEquals("weasis-core.json", UserSettingsStore.fileName("weasis-core")),
        () -> assertEquals("a_b_c.json", UserSettingsStore.fileName("a/b:c")));
  }
}
