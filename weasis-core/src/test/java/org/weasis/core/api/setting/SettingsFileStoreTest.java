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
import java.util.Map;
import java.util.Properties;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.service.WProperties;

@DisplayNameGeneration(ReplaceUnderscores.class)
class SettingsFileStoreTest {
  private static final String GROUP = "weasis-core"; // NON-NLS
  private static final long NO_DELAY = 60_000; // saves only on flush in these tests

  private static SettingsFileStore configured(Path dir) {
    SettingsFileStore store = new SettingsFileStore(NO_DELAY);
    store.configure(dir, List.of());
    return store;
  }

  private static JsonObject read(Path file) throws IOException {
    return Json.createReader(new StringReader(Files.readString(file))).readObject();
  }

  @Test
  void a_group_is_saved_as_one_json_file_with_its_nodes(@TempDir Path dir) throws IOException {
    SettingsFileStore store = configured(dir);
    Preferences core = store.group(GROUP);
    core.put("weasis.theme", "dark"); // NON-NLS
    core.node("view2d.default/font").putInt("size", 12); // NON-NLS
    core.node("empty.node"); // asked for, never written
    store.flushAll();

    JsonObject json = read(dir.resolve("weasis-core.json"));
    assertAll(
        () -> assertEquals("/org/weasis/weasis-core", core.absolutePath()),
        () -> assertEquals(SettingsFileStore.KIND, json.getString("kind")),
        () -> assertEquals(GROUP, json.getString("id")),
        () -> assertEquals(SettingsFileStore.SCHEMA, json.getInt("schema")),
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
    SettingsFileStore first = configured(dir);
    first.group(GROUP).node("zoom").putDouble("factor", 1.5); // NON-NLS
    first.group("weasis-dicom-viewer2d").putBoolean("mpr", true); // NON-NLS
    first.flushAll();

    SettingsFileStore next = configured(dir);
    assertAll(
        () -> assertEquals(1.5, next.group(GROUP).node("zoom").getDouble("factor", 0)),
        () -> assertArrayEquals(new String[] {"zoom"}, next.group(GROUP).childrenNames()),
        () -> assertTrue(next.group("weasis-dicom-viewer2d").getBoolean("mpr", false)),
        () -> assertTrue(Files.isRegularFile(dir.resolve("weasis-dicom-viewer2d.json"))));
  }

  @Test
  void the_earlier_store_is_copied_once_and_left_as_it_was(@TempDir Path dir) throws IOException {
    SettingsFileStore earlier = new SettingsFileStore(NO_DELAY); // stands for the JDK store
    earlier.group(GROUP).put("weasis.theme", "light"); // NON-NLS
    earlier.group(GROUP).node("mouse.action").put("left", "pan"); // NON-NLS

    SettingsFileStore store = new SettingsFileStore(NO_DELAY);
    store.configure(dir, List.of(LegacySettings.Source.of("test", earlier::group)));
    Preferences core = store.group(GROUP);
    store.flushAll();
    assertAll(
        () -> assertEquals("light", core.get("weasis.theme", null)),
        () -> assertEquals("pan", core.node("mouse.action").get("left", null)),
        () -> assertTrue(Files.isRegularFile(dir.resolve("weasis-core.json"))));

    core.remove("weasis.theme");
    store.flushAll();
    SettingsFileStore next = new SettingsFileStore(NO_DELAY);
    next.configure(dir, List.of(LegacySettings.Source.of("test", earlier::group)));
    assertAll(
        () -> assertNull(next.group(GROUP).get("weasis.theme", null)),
        () -> assertEquals("light", earlier.group(GROUP).get("weasis.theme", null)));
  }

  @Test
  void a_group_without_earlier_values_is_still_written_once(@TempDir Path dir) {
    SettingsFileStore store = configured(dir);
    store.group("weasis-acquire"); // NON-NLS
    store.flushAll();
    assertTrue(Files.isRegularFile(dir.resolve("weasis-acquire.json")));
  }

  @Test
  void values_set_before_the_folder_is_known_win_over_the_file(@TempDir Path dir)
      throws IOException {
    SettingsFileStore first = configured(dir);
    first.group(GROUP).put("a", "file"); // NON-NLS
    first.group(GROUP).put("b", "file"); // NON-NLS
    first.flushAll();

    SettingsFileStore early = new SettingsFileStore(NO_DELAY);
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
    SettingsFileStore store = configured(dir);
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
    SettingsFileStore store = configured(dir);
    Preferences core = store.group(GROUP);
    core.put("b", "2"); // NON-NLS
    store.flushAll();

    assertAll(
        () -> assertEquals("future", core.get("a", null)),
        () -> assertEquals(newer, Files.readString(file)));
  }

  @Test
  void a_removed_node_leaves_the_file(@TempDir Path dir) throws Exception {
    SettingsFileStore store = configured(dir);
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
    SettingsFileStore store = new SettingsFileStore(20);
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
    SettingsFileStore earlier = new SettingsFileStore(NO_DELAY); // stands for the JDK store
    earlier.group(GROUP).put("theme", "dark"); // NON-NLS

    SettingsFileStore store = new SettingsFileStore(NO_DELAY);
    store.configure(
        prefDir.resolve(SettingsFileStore.FOLDER),
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
        () -> assertEquals("weasis-core.json", SettingsFileStore.fileName("weasis-core")),
        () -> assertEquals("a_b_c.json", SettingsFileStore.fileName("a/b:c")));
  }

  @Test
  void the_data_folder_files_become_the_machine_settings(@TempDir Path dataDir) throws Exception {
    Path core = Files.createDirectories(dataDir.resolve("weasis-core"));
    Files.writeString(
        core.resolve("persistence.properties"),
        "last.window.x=10\nproxy.manual=true\nproxy.auth.pwd=secret\n"); // NON-NLS
    Path core4x = Files.createDirectories(dataDir.resolve("weasis-core-ui"));
    Files.writeString(
        core4x.resolve("persitence.properties"), "last.window.x=99\nold=1\n"); // NON-NLS
    Path explorer = Files.createDirectories(dataDir.resolve("weasis-dicom-explorer"));
    Files.writeString(
        explorer.resolve("import-export.properties"), "lastDicomDir=/tmp/a\n"); // NON-NLS
    Path iso4x = Files.createDirectories(dataDir.resolve("weasis-isowriter"));
    Files.writeString(iso4x.resolve("export.properties"), "exp.cd=true\n"); // NON-NLS

    SettingsFileStore store = new SettingsFileStore(NO_DELAY);
    store.configure(dataDir.resolve("machine"), List.of(LegacySettings.ofDataFolder(dataDir)));
    Properties persistence = new Properties();
    store.load(GROUP, "", persistence);
    Properties importExport = new Properties();
    store.load("weasis-dicom-explorer", "import-export", importExport); // NON-NLS
    Properties export = new Properties();
    store.load("weasis-dicom-isowriter", "export", export); // NON-NLS

    assertAll(
        () -> assertEquals("10", persistence.getProperty("last.window.x")),
        () -> assertEquals("1", persistence.getProperty("old")),
        () -> assertEquals("true", persistence.getProperty("proxy.manual")),
        () -> assertNull(persistence.getProperty("proxy.auth.pwd")),
        () -> assertEquals("/tmp/a", importExport.getProperty("lastDicomDir")),
        () -> assertEquals("true", export.getProperty("exp.cd")),
        () -> assertTrue(Files.isRegularFile(core.resolve("persistence.properties"))));
  }

  @Test
  void store_makes_a_node_hold_the_entries_of_the_properties_only(@TempDir Path dir) {
    SettingsFileStore store = configured(dir);
    Preferences node = store.group(GROUP).node("export"); // NON-NLS
    node.put("stale", "1"); // NON-NLS
    WProperties source = new WProperties(Map.of("from.site", "x")); // NON-NLS
    source.setProperty("exp.cd", "true"); // NON-NLS

    store.store(GROUP, "export", source); // NON-NLS
    assertAll(
        () -> assertNull(node.get("stale", null)),
        () -> assertNull(node.get("from.site", null)),
        () -> assertEquals("true", node.get("exp.cd", null)));
  }
}
