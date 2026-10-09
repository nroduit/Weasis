/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.launcher;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.util.ListDocument;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.SiteDocuments;

@DisplayNameGeneration(ReplaceUnderscores.class)
class LauncherDocumentsTest {

  private static final String SITE =
      """
      [
        {"name": "OsiriX", "enable": true, "button": true,
         "configuration": {"launchType": "URI", "uri": "osirix://open"}},
        {"id": "site.launcher.web", "name": "Portal", "enable": true, "locked": true,
         "configuration": {"launchType": "URI", "uri": "https://portal.example.org"}},
        {"name": "OsiriX", "enable": false,
         "configuration": {"launchType": "URI", "uri": "osirix://other"}}
      ]
      """;

  @Test
  void the_site_document_under_config_wins_over_the_legacy_location(@TempDir Path dir)
      throws IOException {
    String name = Launcher.Type.OTHER.getFilename();
    Files.writeString(dir.resolve(name), launchers("Legacy"));
    Path config = Files.createDirectories(dir.resolve(SiteDocuments.FOLDER));
    Files.writeString(config.resolve(name), launchers("Site"));
    // setResourcePath refuses an empty path, the default: fall back on the working directory
    String before = ResourceUtil.getResource(Path.of("")).toString();
    before = before.isEmpty() ? "." : before;
    try {
      ResourceUtil.setResourcePath(dir.toString());
      assertEquals(config.resolve(name), Launcher.siteDocument(Launcher.Type.OTHER));
      Files.delete(config.resolve(name));
      assertEquals(dir.resolve(name), Launcher.siteDocument(Launcher.Type.OTHER));
    } finally {
      ResourceUtil.setResourcePath(before);
    }
  }

  @Test
  void ids_are_derived_from_the_name_once_and_stay_stable(@TempDir Path dir) throws IOException {
    Path site = Files.writeString(dir.resolve("site.json"), SITE);

    List<Launcher> first = Launcher.loadLaunchers(site, dir.resolve("absent.json"));
    List<Launcher> second = Launcher.loadLaunchers(site, dir.resolve("absent.json"));

    List<String> ids = first.stream().map(Launcher::getId).toList();
    assertAll(
        () ->
            assertEquals(
                List.of("site.launcher.osirix", "site.launcher.web", "site.launcher.osirix#2"),
                ids),
        () -> assertEquals(ids, second.stream().map(Launcher::getId).toList()),
        () -> assertTrue(first.stream().noneMatch(Launcher::isLocal)),
        () ->
            assertEquals(
                "site.launcher.osirix", Launcher.fromJson(first.getFirst().toJson()).getId()));
  }

  @Test
  void a_user_launcher_replaces_the_site_one_of_the_same_id(@TempDir Path dir) throws IOException {
    Path site = Files.writeString(dir.resolve("site.json"), SITE);
    Path user =
        Files.writeString(
            dir.resolve("user.json"),
            """
            {"schema": 1, "launchers": [
              {"id": "site.launcher.osirix", "name": "My OsiriX", "enable": true,
               "configuration": {"launchType": "URI", "uri": "osirix://mine"}},
              {"name": "Notes", "enable": true,
               "configuration": {"launchType": "Application", "binaryPath": "/usr/bin/notes"}}
            ]}
            """);

    List<Launcher> merged = Launcher.loadLaunchers(site, user);

    assertAll(
        () ->
            assertEquals(
                List.of(
                    "site.launcher.osirix",
                    "site.launcher.web",
                    "site.launcher.osirix#2",
                    "user.launcher.notes"),
                merged.stream().map(Launcher::getId).toList()),
        () -> assertEquals("My OsiriX", merged.getFirst().getName()),
        () -> assertTrue(merged.getFirst().isLocal()),
        () -> assertFalse(merged.get(1).isLocal()),
        () -> assertTrue(merged.get(3).isLocal()));
  }

  @Test
  void a_locked_site_launcher_refuses_the_user_one(@TempDir Path dir) throws IOException {
    Path site = Files.writeString(dir.resolve("site.json"), SITE);
    Path user =
        Files.writeString(
            dir.resolve("user.json"),
            """
            [{"id": "site.launcher.web", "name": "Hijacked", "enable": true, "hidden": true,
              "configuration": {"launchType": "URI", "uri": "https://evil.example.org"}}]
            """);

    List<Launcher> merged = Launcher.loadLaunchers(site, user);
    Launcher portal = merged.get(1);

    assertAll(
        () -> assertEquals("Portal", portal.getName()),
        () -> assertTrue(portal.isLocked()),
        () -> assertFalse(portal.isHidden()),
        () -> assertFalse(portal.isLocal()),
        () -> assertEquals(3, merged.size()));
  }

  @Test
  void a_hidden_user_entry_stays_in_the_list_but_out_of_the_views(@TempDir Path dir)
      throws IOException {
    Path site = Files.writeString(dir.resolve("site.json"), SITE);
    Path user =
        Files.writeString(
            dir.resolve("user.json"),
            """
            [{"id": "site.launcher.osirix", "extends": "site.launcher.osirix", "hidden": true}]
            """);

    List<Launcher> merged = Launcher.loadLaunchers(site, user);
    Launcher osirix = merged.getFirst();

    assertAll(
        () -> assertEquals("OsiriX", osirix.getName()),
        () -> assertTrue(osirix.isHidden()),
        () -> assertTrue(osirix.isLocal()),
        () -> assertEquals(3, merged.size()),
        () -> assertTrue(Launcher.fromJson(osirix.toJson()).isHidden()));
  }

  @Test
  void a_user_entry_extends_a_site_one_and_changes_only_what_it_says(@TempDir Path dir)
      throws IOException {
    Path site = Files.writeString(dir.resolve("site.json"), SITE);
    Path user =
        Files.writeString(
            dir.resolve("user.json"),
            """
            [{"id": "user.launcher.osirix-button", "extends": "site.launcher.osirix",
              "button": false},
             {"extends": "site.launcher.web", "iconPath": "mine.svg"},
             {"id": "user.launcher.orphan", "extends": "site.launcher.none"}]
            """);

    List<Launcher> merged = Launcher.loadLaunchers(site, user);

    assertEquals(5, merged.size());
    Launcher button = merged.get(3);
    Launcher web = merged.get(4);
    assertAll(
        () -> assertEquals("user.launcher.osirix-button", button.getId()),
        () -> assertEquals("OsiriX", button.getName()),
        () -> assertTrue(button.isEnable()),
        () -> assertFalse(button.isButton()),
        () ->
            assertEquals(
                "osirix://open",
                assertInstanceOf(Launcher.URIConfiguration.class, button.getConfiguration())
                    .getUri()),
        () -> assertEquals("user.launcher.portal", web.getId()),
        () -> assertEquals("mine.svg", web.getIconPath()),
        () -> assertFalse(web.isLocked(), "locked is never inherited"),
        () -> assertTrue(merged.stream().noneMatch(l -> "user.launcher.orphan".equals(l.getId()))));
  }

  @Test
  void the_user_document_keeps_only_the_user_launchers_and_gives_them_ids(@TempDir Path dir)
      throws IOException {
    Path site = Files.writeString(dir.resolve("site.json"), SITE);
    Path user = dir.resolve("user.json");
    List<Launcher> merged = Launcher.loadLaunchers(site, user);
    Launcher added = new Launcher();
    added.setName("New one");
    added.setEnable(true);
    Launcher.URIConfiguration config = new Launcher.URIConfiguration();
    config.setUri("https://example.org");
    added.setConfiguration(config);
    merged.add(added);

    Launcher.writeUserDocument(user, merged);

    ListDocument.Content content = ListDocument.read(user, Launcher.ENTRIES);
    List<Launcher> reloaded = Launcher.loadLaunchers(site, user);
    assertAll(
        () -> assertEquals("user.launcher.new-one", added.getId()),
        () -> assertEquals(1, content.entries().size()),
        () -> assertEquals("user.launcher.new-one", content.entries().getFirst().getString("id")),
        () -> assertEquals(4, reloaded.size()),
        () -> assertEquals("user.launcher.new-one", reloaded.get(3).getId()),
        () -> assertTrue(reloaded.get(3).isLocal()));
  }

  private static String launchers(String name) {
    return """
        [{"name": "%s", "enable": true, "configuration": {"launchType": "URI", "uri": "x://y"}}]
        """
        .formatted(name);
  }
}
