/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.media.data;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("MaskingModelRegistry")
class MaskingModelRegistryTest {

  @TempDir Path dir;

  private static MaskingModelRegistry bundled() {
    return new MaskingModelRegistry(MaskingModelRegistry.loadBuiltIn());
  }

  private Path write(String name, String json) throws IOException {
    return Files.writeString(dir.resolve(name), json);
  }

  @Test
  @DisplayName("the bundled document defines the four profiles")
  void bundledProfiles() {
    MaskingModelRegistry registry = bundled();
    assertAll(
        () ->
            assertEquals(
                List.of("display", "teaching", "publication", "ai-request"),
                registry.profiles().stream().map(MaskingProfile::id).toList()),
        () -> assertEquals("display", registry.sessionProfile().id()),
        () -> assertEquals("ai-request", registry.aiProfile().id()),
        () ->
            assertEquals(
                List.of("display", "teaching", "publication"),
                registry.exportProfiles().stream().map(MaskingProfile::id).toList()),
        () ->
            assertTrue(
                registry.tagRules().stream()
                    .anyMatch(r -> r.key().equals("weasis:PatientPseudoUID"))));
  }

  @Test
  @DisplayName("a site document adds tags and profiles and selects the session profile")
  void siteDocumentExtends() throws IOException {
    Path site =
        write(
            "site.json",
            """
            { "tags": [ { "tag": "OperatorsName", "category": "DIRECT_ID" } ],
              "profiles": [ { "id": "demo", "name": "Demo", "offerInExport": true,
                  "actions": { "DIRECT_ID": "CLEAR" } } ],
              "sessionProfile": "demo" }
            """);
    MaskingModelRegistry registry = bundled();
    registry.configure(site.toString(), null);
    assertAll(
        () -> assertEquals("demo", registry.sessionProfile().id()),
        () -> assertEquals(4, registry.exportProfiles().size()),
        () ->
            assertTrue(
                registry.tagRules().stream().anyMatch(r -> r.key().equals("OperatorsName"))));
  }

  @Test
  @DisplayName("a profile keeping direct identifiers or a missing session id is refused")
  void unsafeConfigurationRefused() throws IOException {
    Path site =
        write(
            "site.json",
            """
            { "profiles": [ { "id": "display", "actions": {} } ],
              "sessionProfile": "missing" }
            """);
    MaskingModelRegistry registry = bundled();
    registry.configure(site.toString(), null);
    assertAll(
        () -> assertEquals("display", registry.sessionProfile().id()),
        () -> assertTrue(registry.sessionProfile().hidesDirectIdentifiers()));
  }

  @Test
  @DisplayName("a locked site document excludes the user document")
  void lockedSiteIgnoresUser() throws IOException {
    Path user = write("user.json", "{ \"sessionProfile\": \"teaching\" }");
    Path locked = write("locked.json", "{ \"locked\": true }");
    Path open = write("open.json", "{ \"locked\": false }");

    MaskingModelRegistry registry = bundled();
    registry.configure(locked.toString(), user);
    String lockedSession = registry.sessionProfile().id();
    registry.configure(open.toString(), user);

    assertAll(
        () -> assertEquals("display", lockedSession),
        () -> assertEquals("teaching", registry.sessionProfile().id()));
  }

  @Test
  @DisplayName("an unreadable site document leaves the defaults and still notifies")
  void unreadableSiteKeepsDefaults() {
    MaskingModelRegistry registry = bundled();
    int[] calls = {0};
    registry.addListener(() -> calls[0]++);
    registry.configure(dir.resolve("absent.json").toString(), dir.resolve("absent-user.json"));
    assertAll(
        () -> assertEquals("display", registry.sessionProfile().id()),
        () -> assertEquals(1, calls[0]));
  }
}
