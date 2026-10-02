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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.media.data.MaskingModel.TagRule;
import org.weasis.core.api.media.data.MaskingModelRegistry.Origin;

@DisplayName("MaskingModelRegistry user document")
class MaskingModelRegistryWriteTest {

  @TempDir Path dir;

  private Path userFile() {
    return dir.resolve("identityMasking.json");
  }

  private MaskingModelRegistry configured() {
    MaskingModelRegistry registry = new MaskingModelRegistry(MaskingModelRegistry.loadBuiltIn());
    registry.configure(null, userFile());
    return registry;
  }

  private static MaskingProfile profile(String id, AnonymizationAction directId) {
    Map<TagCategory, AnonymizationAction> actions = new EnumMap<>(TagCategory.class);
    actions.put(TagCategory.DIRECT_ID, directId);
    return new MaskingProfile(id, id, Map.of(), actions, Map.of(), false, true);
  }

  @Test
  @DisplayName("a saved profile is written, merged as a user entry and notified")
  void saveProfile() throws IOException {
    MaskingModelRegistry registry = configured();
    AtomicInteger reloads = new AtomicInteger();
    registry.addListener(reloads::incrementAndGet);

    registry.saveUserProfile(profile("user-demo", AnonymizationAction.CLEAR));

    assertAll(
        () -> assertTrue(Files.isRegularFile(userFile())),
        () -> assertEquals(1, reloads.get()),
        () -> assertTrue(registry.profile("user-demo").isPresent()),
        () -> assertEquals(Origin.USER, registry.origin("user-demo")),
        () -> assertEquals(Origin.BUILT_IN, registry.origin("display")),
        () -> assertEquals(List.of("user-demo"), ids(registry.userModel())),
        () -> assertEquals(List.of("user-demo"), ids(MaskingModel.read(userFile()))));
  }

  @Test
  @DisplayName("a user profile of a built-in id overrides it, and deleting restores it")
  void overrideAndDelete() throws IOException {
    MaskingModelRegistry registry = configured();
    registry.saveUserProfile(profile(MaskingProfile.DISPLAY_ID, AnonymizationAction.REMOVE));
    AnonymizationAction overridden =
        registry.profile(MaskingProfile.DISPLAY_ID).orElseThrow().actionFor(TagCategory.DIRECT_ID);

    boolean deleted = registry.deleteUserProfile(MaskingProfile.DISPLAY_ID);

    assertAll(
        () -> assertEquals(AnonymizationAction.REMOVE, overridden),
        () -> assertTrue(deleted),
        () -> assertFalse(registry.deleteUserProfile("no-such-profile")),
        () ->
            assertEquals(
                AnonymizationAction.PSEUDONYMIZE,
                registry
                    .profile(MaskingProfile.DISPLAY_ID)
                    .orElseThrow()
                    .actionFor(TagCategory.DIRECT_ID)),
        () -> assertEquals(Origin.BUILT_IN, registry.origin(MaskingProfile.DISPLAY_ID)));
  }

  @Test
  @DisplayName("a tag is classified and unclassified through the user document")
  void classifyTag() throws IOException {
    MaskingModelRegistry registry = configured();
    registry.saveUserTag("(0010,1040)", TagCategory.DIRECT_ID);
    String key = "00101040";

    assertAll(
        () -> assertEquals(Origin.USER, registry.tagOrigin(key)),
        () ->
            assertTrue(
                registry.tagRules().stream()
                    .anyMatch(r -> r.key().equals(key) && r.category() == TagCategory.DIRECT_ID)),
        () -> assertTrue(registry.deleteUserTag(key)),
        () -> assertFalse(registry.tagRules().stream().anyMatch(r -> r.key().equals(key))),
        () -> assertThrows(IllegalArgumentException.class, () -> registry.saveUserTag("", null)));
  }

  @Test
  @DisplayName("a profile that keeps direct identifiers is refused and nothing is written")
  void refuseProfileKeepingIdentifiers() {
    MaskingModelRegistry registry = configured();
    MaskingProfile open = profile("user-open", AnonymizationAction.KEEP);

    assertAll(
        () -> assertThrows(IllegalArgumentException.class, () -> registry.saveUserProfile(open)),
        () -> assertFalse(Files.exists(userFile())),
        () -> assertTrue(registry.profile("user-open").isEmpty()));
  }

  @Test
  @DisplayName("a locked site document makes the configuration read-only")
  void lockedSite() throws IOException {
    Path site =
        Files.writeString(
            dir.resolve("site.json"),
            """
            { "locked": true,
              "profiles": [ { "id": "site-demo", "name": "Site",
                  "actions": { "DIRECT_ID": "REMOVE" } } ] }
            """);
    MaskingModel.read(site).write(userFile()); // a user document that must stay ignored
    MaskingModelRegistry registry = new MaskingModelRegistry(MaskingModelRegistry.loadBuiltIn());
    registry.configure(site, userFile());

    assertAll(
        () -> assertTrue(registry.isLocked()),
        () -> assertEquals(Origin.SITE, registry.origin("site-demo")),
        () ->
            assertThrows(
                IllegalStateException.class,
                () -> registry.saveUserProfile(profile("user-demo", AnonymizationAction.CLEAR))));
  }

  @Test
  @DisplayName("an unreadable user document is kept aside by the next write")
  void unreadableUserFile() throws IOException {
    Files.writeString(userFile(), "{ not json");
    MaskingModelRegistry registry = configured();

    registry.saveUserProfile(profile("user-demo", AnonymizationAction.CLEAR));

    assertAll(
        () -> assertTrue(Files.isRegularFile(dir.resolve("identityMasking.json.bak"))),
        () -> assertEquals(List.of("user-demo"), ids(MaskingModel.read(userFile()))));
  }

  @Test
  @DisplayName("an imported document must hold something")
  void importEmpty() throws IOException {
    Path empty = dir.resolve("empty.json");
    new MaskingModel(List.of(), List.of(), null, null, false).write(empty);
    Path document = dir.resolve("import.json");
    new MaskingModel(
            List.of(new TagRule("OperatorsName", TagCategory.DIRECT_ID)),
            List.of(),
            null,
            null,
            false)
        .write(document);

    assertAll(
        () -> assertThrows(IOException.class, () -> MaskingModelRegistry.readImport(empty)),
        () -> assertEquals(1, MaskingModelRegistry.readImport(document).tags().size()));
  }

  private static List<String> ids(MaskingModel model) {
    return model.profiles().stream().map(MaskingProfile::id).toList();
  }
}
