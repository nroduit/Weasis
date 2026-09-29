/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.profile;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayNameGeneration(ReplaceUnderscores.class)
class MeasurementProfileRegistryTest {

  private static MeasurementProfile profile(String id, String name, String... modalities) {
    return new MeasurementProfile(id, name, List.of(modalities), null, null, null, null, false);
  }

  @Test
  void user_overrides_site_which_overrides_built_in(@TempDir Path dir) throws IOException {
    Path site = dir.resolve("site.json"); // NON-NLS
    Path user = dir.resolve("user.json"); // NON-NLS
    MeasurementProfileJson.write(
        site,
        List.of(
            profile("ct", "Site CT", "CT"), profile("site-only", "Site only", "NM"))); // NON-NLS
    MeasurementProfileJson.write(user, List.of(profile("ct", "My CT", "CT"))); // NON-NLS
    MeasurementProfileRegistry registry =
        new MeasurementProfileRegistry(
            List.of(profile("default", "Default"), profile("ct", "Built-in CT", "CT")), // NON-NLS
            site.toString(),
            user);
    assertAll(
        () -> assertEquals("My CT", registry.profile("ct").orElseThrow().name()),
        () -> assertFalse(registry.profile("ct").orElseThrow().builtIn()),
        () -> assertTrue(registry.profile("site-only").orElseThrow().builtIn()),
        () -> assertTrue(registry.profile("default").orElseThrow().builtIn()),
        () -> assertEquals(1, registry.userProfiles().size()));
  }

  @Test
  void for_one_modality_the_users_profile_wins_then_the_sites_then_the_bundled_one(
      @TempDir Path dir) throws IOException {
    Path site = dir.resolve("site.json"); // NON-NLS
    Path user = dir.resolve("user.json"); // NON-NLS
    MeasurementProfileJson.write(
        site,
        List.of(
            profile("site-ct", "Site CT", "CT"), profile("site-mr", "Site MR", "MR"))); // NON-NLS
    MeasurementProfileRegistry registry =
        new MeasurementProfileRegistry(
            List.of(
                profile("default", "Default"), // NON-NLS
                profile("cross-sectional", "Cross-sectional", "CT", "MR", "PT")), // NON-NLS
            site.toString(),
            user);
    registry.saveUser(profile("my-ct", "My CT", "CT")); // NON-NLS
    registry.saveUser(profile("my-ct-2", "My second CT", "CT")); // NON-NLS
    assertAll(
        () -> assertEquals("my-ct", registry.matching("CT").orElseThrow().id()),
        () -> assertEquals("site-mr", registry.matching("MR").orElseThrow().id()),
        () -> assertEquals("cross-sectional", registry.matching("PT").orElseThrow().id()),
        () -> assertTrue(registry.matching("US").isEmpty()));

    registry.setModality("CT"); // NON-NLS
    assertEquals("my-ct", registry.active().id());
    registry.deleteUser("my-ct"); // NON-NLS
    registry.deleteUser("my-ct-2"); // NON-NLS
    assertEquals("site-ct", registry.active().id());
  }

  @Test
  void saving_and_deleting_user_profiles_persists_and_notifies(@TempDir Path dir) {
    Path user = dir.resolve("profiles.json"); // NON-NLS
    MeasurementProfileRegistry registry =
        new MeasurementProfileRegistry(List.of(profile("default", "Default")), null, user);
    AtomicInteger changes = new AtomicInteger();
    registry.addListener(changes::incrementAndGet);

    registry.saveUser(profile("mine", "Mine", "US")); // NON-NLS
    assertAll(
        () -> assertTrue(Files.isRegularFile(user)),
        () -> assertEquals(1, changes.get()),
        () -> assertTrue(registry.profile("mine").isPresent()));
    MeasurementProfileRegistry again =
        new MeasurementProfileRegistry(List.of(profile("default", "Default")), null, user);
    assertEquals("Mine", again.profile("mine").orElseThrow().name());

    registry.deleteUser("mine"); // NON-NLS
    assertAll(
        () -> assertTrue(registry.profile("mine").isEmpty()), () -> assertEquals(2, changes.get()));
  }

  @Test
  void the_active_profile_is_the_choice_else_the_modality_match_else_the_default() {
    MeasurementProfileRegistry registry =
        new MeasurementProfileRegistry(
            List.of(
                profile("default", "Default"),
                profile("ct", "CT", "CT", "MR"),
                profile("us", "US", "US")),
            null,
            null);
    assertTrue(registry.isAutomatic());
    assertEquals("default", registry.active().id());
    registry.setModality("mr");
    assertEquals("ct", registry.active().id());
    registry.setModality("XA");
    assertEquals("default", registry.active().id());
    registry.select("us");
    registry.setModality("CT");
    assertAll(
        () -> assertEquals("us", registry.active().id()),
        () -> assertFalse(registry.isAutomatic()));
    registry.select("unknown");
    assertAll(
        () -> assertTrue(registry.isAutomatic()), () -> assertEquals("ct", registry.active().id()));
  }

  @Test
  void a_windows_path_is_not_taken_for_a_url() {
    assertAll(
        () -> assertTrue(MeasurementProfileRegistry.isUrl("https://pacs.example/profiles.json")),
        () -> assertTrue(MeasurementProfileRegistry.isUrl("file:///opt/weasis/profiles.json")),
        () -> assertFalse(MeasurementProfileRegistry.isUrl("C:\\Weasis\\profiles.json")),
        () -> assertFalse(MeasurementProfileRegistry.isUrl("/opt/weasis/profiles.json")));
  }

  @Test
  void saving_a_user_profile_does_not_read_the_site_document_again(@TempDir Path dir)
      throws IOException {
    Path site = dir.resolve("site.json"); // NON-NLS
    MeasurementProfileJson.write(site, List.of(profile("site-ct", "Site CT", "CT"))); // NON-NLS
    MeasurementProfileRegistry registry =
        new MeasurementProfileRegistry(
            List.of(profile("default", "Default")), site.toString(), dir.resolve("user.json"));
    Files.delete(site);
    registry.saveUser(profile("mine", "Mine", "US")); // NON-NLS
    assertAll(
        () -> assertTrue(registry.profile("site-ct").isPresent()),
        () -> assertTrue(registry.profile("mine").isPresent()));
  }

  @Test
  void a_default_profile_always_exists() {
    MeasurementProfileRegistry registry =
        new MeasurementProfileRegistry(List.of(profile("ct", "CT", "CT")), null, null);
    assertTrue(registry.profile(MeasurementProfile.DEFAULT_ID).isPresent());
  }
}
