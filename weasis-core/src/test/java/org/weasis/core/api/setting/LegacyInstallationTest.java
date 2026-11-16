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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.setting.LegacyInstallation.Report;
import org.weasis.core.api.setting.LegacyInstallation.Roots;
import org.weasis.core.api.util.DocumentStore;

/** The explicit import of a Weasis 4 installation into the stores of this one. */
class LegacyInstallationTest {

  /** The import test points the static document store at its temporary folder: put it back. */
  @AfterEach
  void unconfigureDocuments() {
    DocumentStore.configure(null, null);
  }

  private static Roots legacy(Path home) throws IOException {
    Path root = home.resolve(".weasis");
    Path pref = root.resolve("preferences").resolve("alice").resolve("default");
    Files.createDirectories(pref);
    Files.writeString(
        pref.resolve("weasis-core-ui.xml"),
        "<preferences><zoom><factor>3</factor><mode>fit</mode></zoom></preferences>"); // NON-NLS
    Files.writeString(
        pref.resolve("weasis-dicom-explorer.xml"),
        "<preferences><thumbnails>96</thumbnails></preferences>"); // NON-NLS
    Files.writeString(pref.resolve("colormaps.json"), "{\"entries\": []}"); // NON-NLS
    Path core = root.resolve("data").resolve("weasis-core");
    Files.createDirectories(core);
    Files.writeString(core.resolve("persistence.properties"), "win.x=10\nproxy.auth.user=x\n");
    Files.writeString(core.resolve("import-export.properties"), "exp.path=/tmp/x\n");
    return LegacyInstallation.find(root, "alice", "default").orElseThrow();
  }

  @Test
  void the_installation_is_found_with_or_without_a_profile_folder(@TempDir Path home)
      throws IOException {
    Roots roots = legacy(home);
    Path root = home.resolve(".weasis");
    assertAll(
        () -> assertEquals(root, roots.root()),
        () ->
            assertEquals(
                root.resolve("preferences").resolve("alice").resolve("default"), roots.prefDir()),
        () -> assertEquals(root.resolve("data"), roots.dataDir()),
        () ->
            assertEquals(
                root.resolve("preferences").resolve("alice"),
                LegacyInstallation.find(root, "alice", "other").orElseThrow().prefDir()),
        () -> assertTrue(LegacyInstallation.find(home.resolve("nowhere"), "a", "b").isEmpty()));
  }

  @Test
  void a_dry_run_counts_and_changes_nothing(@TempDir Path home, @TempDir Path here)
      throws IOException {
    Roots roots = legacy(home);
    SettingsFileStore user = new SettingsFileStore(0);
    user.configure(here.resolve("settings"), List.of());
    SettingsFileStore machine = new SettingsFileStore(0);
    machine.configure(here.resolve("machine"), List.of());
    user.group("weasis-core").node("zoom").put("factor", "2"); // already set in 5
    Report report = LegacyInstallation.run(roots, null, user, machine, false, true);
    assertAll(
        () -> assertEquals(3, report.items().size(), report.toString()),
        () -> assertEquals(2, report.count(LegacyInstallation.SETTINGS)),
        () -> assertEquals(1, report.count(LegacyInstallation.MACHINE)),
        () -> assertEquals("2", user.group("weasis-core").node("zoom").get("factor", null)),
        () -> assertEquals(null, user.group("weasis-core").node("zoom").get("mode", null)),
        () -> assertEquals(1, report.totals().kept()),
        () -> assertEquals(4, report.totals().added()), // mode, thumbnails, win.x, exp.path
        () -> assertEquals(0, report.totals().replaced()));
  }

  @Test
  void the_import_fills_the_gaps_and_replaces_only_on_request(
      @TempDir Path home, @TempDir Path here) throws IOException {
    Roots roots = legacy(home);
    SettingsFileStore user = new SettingsFileStore(0);
    user.configure(here.resolve("settings"), List.of());
    SettingsFileStore machine = new SettingsFileStore(0);
    machine.configure(here.resolve("machine"), List.of());
    user.group("weasis-core").node("zoom").put("factor", "2");
    Path prefDir = here.resolve("pref");
    DocumentStore.configure(prefDir, here.resolve("config"));
    Path target =
        DocumentStore.locate(
            org.weasis.core.api.setting.Scope.USER,
            "colormaps.json", // NON-NLS
            prefDir.resolve("colormaps.json")); // NON-NLS
    Roots current = new Roots(null, prefDir, here.resolve("data"));

    Report merged = LegacyInstallation.run(roots, current, user, machine, false, false);
    assertAll(
        () -> assertEquals("2", user.group("weasis-core").node("zoom").get("factor", null)),
        () -> assertEquals("fit", user.group("weasis-core").node("zoom").get("mode", null)),
        () -> assertEquals("96", user.group("weasis-dicom-explorer").get("thumbnails", null)),
        () -> assertEquals("10", machine.group("weasis-core").get("win.x", null)),
        () -> assertEquals(null, machine.group("weasis-core").get("proxy.auth.user", null)),
        () ->
            assertEquals(
                "/tmp/x", machine.group("weasis-core").node("import-export").get("exp.path", null)),
        () -> assertTrue(Files.isRegularFile(target), "document copied"),
        () -> assertEquals(1, merged.count(LegacyInstallation.DOCUMENT)),
        () -> assertEquals(1, merged.totals().kept()));

    Report replaced = LegacyInstallation.run(roots, current, user, machine, true, false);
    assertAll(
        () -> assertEquals("3", user.group("weasis-core").node("zoom").get("factor", null)),
        () ->
            assertEquals(2, replaced.totals().replaced(), replaced.toString()), // factor + document
        () ->
            assertFalse(
                Files.exists(roots.prefDir().resolve("weasis-core-ui.json")), "4.x untouched"));
  }
}
