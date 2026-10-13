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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.setting.SettingsPackage.Change;
import org.weasis.core.api.setting.SettingsPackage.Environment;
import org.weasis.core.api.setting.SettingsPackage.Layer;
import org.weasis.core.api.setting.SettingsPackage.Mode;
import org.weasis.core.api.setting.SettingsPackage.Plan;

@DisplayNameGeneration(ReplaceUnderscores.class)
class SettingsPackageTest {
  private static final long NO_DELAY = 60_000;
  private static final String PRESETS = "windowPresets.json"; // NON-NLS

  /** One installation: its stores, folders and launch values under {@code root}. */
  private static final class Install {
    final Path root;
    final SettingsFileStore user = new SettingsFileStore(NO_DELAY);
    final SettingsFileStore machine = new SettingsFileStore(NO_DELAY);
    final Path userDocs;
    final Path machineDocs;
    final Path site;
    final Properties launcher = new Properties();
    Set<String> locked = Set.of();

    Install(Path root) throws IOException {
      this.root = root;
      user.configure(root.resolve("pref/settings"), List.of()); // NON-NLS
      machine.configure(root.resolve("machine/settings"), List.of()); // NON-NLS
      userDocs = Files.createDirectories(root.resolve("pref/documents")); // NON-NLS
      machineDocs = Files.createDirectories(root.resolve("machine/documents")); // NON-NLS
      site = Files.createDirectories(root.resolve("site")); // NON-NLS
    }

    Environment env() {
      return new Environment(
          user,
          machine,
          userDocs,
          machineDocs,
          launcher,
          () -> {},
          name -> Optional.of(site.resolve(name)).filter(Files::isRegularFile),
          locked,
          Map.of("user", "alice", "profile", "default")); // NON-NLS
    }
  }

  private static String presets(String... idAndWindow) {
    StringBuilder b = new StringBuilder("{\"schema\":1,\"presets\":["); // NON-NLS
    for (int i = 0; i < idAndWindow.length; i += 2) {
      b.append(i > 0 ? "," : "")
          .append("{\"id\":\"")
          .append(idAndWindow[i])
          .append("\",\"window\":")
          .append(idAndWindow[i + 1])
          .append('}');
    }
    return b.append("]}").toString();
  }

  private static JsonObject read(Path file) throws IOException {
    return Json.createReader(new StringReader(Files.readString(file))).readObject();
  }

  private static Install source(Path dir) throws IOException {
    Install a = new Install(dir.resolve("a"));
    a.user.group("weasis-core").put("weasis.theme", "dark"); // NON-NLS
    a.user.group("weasis-core").node("zoom").put("factor", "2"); // NON-NLS
    a.machine.group("weasis-core").put("last.window.x", "10"); // NON-NLS
    Files.writeString(
        a.userDocs.resolve(PRESETS), presets("user.bone", "1800", "user.lung", "1500"));
    Files.writeString(a.machineDocs.resolve("dicomLaunchers.json"), "{\"launchers\":[]}");
    Files.writeString(a.root.resolve("pref/auth-secrets.json"), "{\"secrets\":{}}"); // NON-NLS
    a.launcher.setProperty("weasis.confirm.closing", "true"); // NON-NLS
    a.launcher.setProperty("flatlaf.uiScale", "2"); // NON-NLS
    a.launcher.setProperty("org.apache.sling.commons.log.level", "DEBUG"); // NON-NLS
    return a;
  }

  @Test
  void a_user_package_holds_settings_documents_and_portable_launch_values(@TempDir Path dir)
      throws IOException {
    Install a = source(dir);
    Path zip = dir.resolve("user.zip");
    var manifest = SettingsPackage.write(zip, Layer.USER, false, a.env());
    Map<String, byte[]> files = SettingsPackage.readZip(zip);
    String launcher = new String(files.get(SettingsPackage.LAUNCHER), StandardCharsets.ISO_8859_1);

    assertAll(
        () -> assertEquals("user", manifest.layer()),
        () -> assertTrue(files.containsKey("manifest.json")),
        () -> assertTrue(files.containsKey("settings/weasis-core.json")),
        () -> assertTrue(files.containsKey("documents/" + PRESETS)),
        () -> assertFalse(files.containsKey("machine/settings/weasis-core.json")),
        () -> assertTrue(files.keySet().stream().noneMatch(p -> p.contains("secrets"))),
        () -> assertTrue(launcher.contains("weasis.confirm.closing=true")),
        () -> assertFalse(launcher.contains("uiScale")),
        () -> assertFalse(launcher.contains("sling")),
        () ->
            assertEquals(
                SettingsPackage.sha256(files.get("documents/" + PRESETS)),
                manifest.entries().stream()
                    .filter(e -> e.path().equals("documents/" + PRESETS))
                    .findFirst()
                    .orElseThrow()
                    .sha256()));
  }

  @Test
  void the_machine_comes_with_the_user_on_request_and_a_site_package_is_a_config_folder(
      @TempDir Path dir) throws IOException {
    Install a = source(dir);
    Path withMachine = dir.resolve("all.zip");
    Path site = dir.resolve("site.zip");
    SettingsPackage.write(withMachine, Layer.USER, true, a.env());
    SettingsPackage.write(site, Layer.SITE, false, a.env());

    assertAll(
        () ->
            assertTrue(
                SettingsPackage.readZip(withMachine)
                    .containsKey("machine/settings/weasis-core.json")),
        () ->
            assertTrue(
                SettingsPackage.readZip(withMachine)
                    .containsKey("machine/documents/dicomLaunchers.json")),
        () ->
            assertEquals(
                Set.of("manifest.json", "config/" + PRESETS),
                SettingsPackage.readZip(site).keySet()));
  }

  @Test
  void a_dry_run_plans_and_changes_nothing(@TempDir Path dir) throws IOException {
    Path zip = dir.resolve("user.zip");
    SettingsPackage.write(zip, Layer.USER, false, source(dir).env());
    Install b = new Install(dir.resolve("b"));
    Files.writeString(b.userDocs.resolve(PRESETS), presets("user.bone", "2000"));

    Plan plan = SettingsPackage.importPackage(zip, Mode.MERGE, true, b.env(), b.root);
    Change docs = change(plan, "documents/" + PRESETS);
    Change settings = change(plan, "settings/weasis-core.json");

    assertAll(
        () -> assertEquals(List.of(), plan.errors()),
        () -> assertNull(plan.backup()),
        () -> assertEquals(1, docs.added()),
        () -> assertEquals(1, docs.replaced()),
        () -> assertEquals(2, settings.added()),
        () ->
            assertEquals(
                presets("user.bone", "2000"), Files.readString(b.userDocs.resolve(PRESETS))),
        () -> assertFalse(Files.exists(b.root.resolve("pref/settings/weasis-core.json"))),
        () -> assertNull(b.launcher.getProperty("weasis.confirm.closing")));
  }

  @Test
  void a_merge_keeps_what_the_package_lacks_and_skips_what_the_site_locks(@TempDir Path dir)
      throws IOException {
    Path zip = dir.resolve("user.zip");
    SettingsPackage.write(zip, Layer.USER, false, source(dir).env());
    Install b = new Install(dir.resolve("b"));
    Files.writeString(b.userDocs.resolve(PRESETS), presets("user.mine", "400"));
    Files.writeString(
        b.site.resolve(PRESETS),
        "{\"presets\":[{\"id\":\"user.lung\",\"window\":1,\"locked\":true}]}"); // NON-NLS
    b.locked = Set.of("weasis.theme"); // NON-NLS
    b.user.group("weasis-core").put("kept", "1"); // NON-NLS

    Plan plan = SettingsPackage.importPackage(zip, Mode.MERGE, false, b.env(), b.root);
    JsonObject doc = read(b.userDocs.resolve(PRESETS));
    List<String> ids =
        doc.getJsonArray("presets").getValuesAs(JsonObject.class).stream()
            .map(o -> o.getString("id"))
            .toList();

    assertAll(
        () -> assertEquals(List.of("user.mine", "user.bone"), ids),
        () -> assertEquals(1, change(plan, "documents/" + PRESETS).skipped()),
        () -> assertEquals("weasis.document", doc.getString("kind")),
        () -> assertNull(b.user.group("weasis-core").get("weasis.theme", null)),
        () -> assertEquals("1", b.user.group("weasis-core").get("kept", null)),
        () -> assertEquals("2", b.user.group("weasis-core").node("zoom").get("factor", null)),
        () -> assertEquals("true", b.launcher.getProperty("weasis.confirm.closing")),
        () -> assertNotNull(plan.backup()),
        () -> assertTrue(Files.isRegularFile(plan.backup())));
  }

  @Test
  void a_replace_makes_each_document_and_group_the_package_one(@TempDir Path dir)
      throws IOException {
    Path zip = dir.resolve("user.zip");
    SettingsPackage.write(zip, Layer.USER, false, source(dir).env());
    Install b = new Install(dir.resolve("b"));
    Files.writeString(b.userDocs.resolve(PRESETS), presets("user.mine", "400"));
    b.user.group("weasis-core").put("dropped", "1"); // NON-NLS
    b.user.flushAll();

    SettingsPackage.importPackage(zip, Mode.REPLACE, false, b.env(), b.root);
    List<String> ids =
        read(b.userDocs.resolve(PRESETS))
            .getJsonArray("presets")
            .getValuesAs(JsonObject.class)
            .stream()
            .map(o -> o.getString("id"))
            .toList();

    assertAll(
        () -> assertEquals(List.of("user.bone", "user.lung"), ids),
        () -> assertNull(b.user.group("weasis-core").get("dropped", null)),
        () -> assertEquals("dark", b.user.group("weasis-core").get("weasis.theme", null)));
  }

  @Test
  void a_file_whose_checksum_does_not_match_is_skipped(@TempDir Path dir) throws IOException {
    Path good = dir.resolve("user.zip");
    SettingsPackage.write(good, Layer.USER, false, source(dir).env());
    Map<String, byte[]> files = SettingsPackage.readZip(good);
    files.put("documents/" + PRESETS, presets("user.evil", "1").getBytes(StandardCharsets.UTF_8));
    Path tampered = zip(dir.resolve("tampered.zip"), files);
    Install b = new Install(dir.resolve("b"));

    Plan plan = SettingsPackage.importPackage(tampered, Mode.MERGE, false, b.env(), b.root);
    assertAll(
        () -> assertTrue(plan.errors().stream().anyMatch(e -> e.contains("checksum"))),
        () -> assertFalse(Files.exists(b.userDocs.resolve(PRESETS))));
  }

  @Test
  void a_site_package_and_a_foreign_zip_are_refused(@TempDir Path dir) throws IOException {
    Path site = dir.resolve("site.zip");
    SettingsPackage.write(site, Layer.SITE, false, source(dir).env());
    Path foreign = zip(dir.resolve("foreign.zip"), Map.of("manifest.json", "{}".getBytes()));
    Path escaping =
        zip(dir.resolve("escaping.zip"), Map.of("../evil.json", "{}".getBytes())); // NON-NLS
    Install b = new Install(dir.resolve("b"));

    assertAll(
        () ->
            assertThrows(
                IOException.class,
                () -> SettingsPackage.importPackage(site, Mode.MERGE, true, b.env(), b.root)),
        () ->
            assertThrows(
                IOException.class,
                () -> SettingsPackage.importPackage(foreign, Mode.MERGE, true, b.env(), b.root)),
        () ->
            assertThrows(
                IOException.class,
                () -> SettingsPackage.importPackage(escaping, Mode.MERGE, true, b.env(), b.root)));
  }

  private static Change change(Plan plan, String path) {
    return plan.changes().stream()
        .filter(c -> c.path().equals(path))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No change for " + path + " in " + plan.changes()));
  }

  private static Path zip(Path file, Map<String, byte[]> files) throws IOException {
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
      for (Map.Entry<String, byte[]> e : files.entrySet()) {
        out.putNextEntry(new ZipEntry(e.getKey()));
        out.write(e.getValue());
        out.closeEntry();
      }
    }
    return file;
  }
}
