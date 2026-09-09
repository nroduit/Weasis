/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.wado;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * Thumbnail mode resolution: what the manifest declares, what the archive teaches, and how the two
 * combine. Each test uses its own base URL because the registry is a session-wide singleton and the
 * tests run in parallel; the system-property fallbacks are intentionally not exercised for the same
 * reason.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ThumbnailServiceRegistryTest {

  private static final List<ThumbnailMode> CANDIDATES =
      List.of(ThumbnailMode.RS_THUMBNAIL, ThumbnailMode.RENDERED, ThumbnailMode.WADO_URI);

  @Test
  void an_archive_without_configuration_starts_with_the_first_candidate() {
    assertAll(
        () ->
            assertEquals(
                ThumbnailMode.RS_THUMBNAIL,
                ThumbnailServiceRegistry.currentMode("http://arc-unknown/rs", CANDIDATES)),
        () ->
            assertEquals(
                ThumbnailMode.NONE,
                ThumbnailServiceRegistry.currentMode("http://arc-unknown/rs", List.of())));
  }

  @Test
  void an_auto_archive_walks_the_candidates_until_none_is_left() {
    String baseUrl = "http://arc-auto/rs";
    ThumbnailServiceRegistry.configure(baseUrl, "AUTO");

    assertEquals(
        ThumbnailMode.RENDERED,
        ThumbnailServiceRegistry.downgrade(baseUrl, ThumbnailMode.RS_THUMBNAIL, CANDIDATES));
    assertEquals(ThumbnailMode.RENDERED, ThumbnailServiceRegistry.currentMode(baseUrl, CANDIDATES));

    assertEquals(
        ThumbnailMode.WADO_URI,
        ThumbnailServiceRegistry.downgrade(baseUrl, ThumbnailMode.RENDERED, CANDIDATES));
    assertEquals(
        ThumbnailMode.NONE,
        ThumbnailServiceRegistry.downgrade(baseUrl, ThumbnailMode.WADO_URI, CANDIDATES));
    assertEquals(ThumbnailMode.NONE, ThumbnailServiceRegistry.currentMode(baseUrl, CANDIDATES));
  }

  @Test
  void an_explicit_mode_is_never_replaced_by_another_service() {
    String baseUrl = "http://arc-pinned/rs";
    ThumbnailServiceRegistry.configure(baseUrl, "RENDERED");

    assertEquals(ThumbnailMode.RENDERED, ThumbnailServiceRegistry.currentMode(baseUrl, CANDIDATES));
    // A failing explicit mode disables the thumbnails of that archive instead of probing further.
    assertEquals(
        ThumbnailMode.NONE,
        ThumbnailServiceRegistry.downgrade(baseUrl, ThumbnailMode.RENDERED, CANDIDATES));
  }

  @Test
  void an_unknown_mode_falls_back_to_probing() {
    String baseUrl = "http://arc-garbage/rs";
    ThumbnailServiceRegistry.configure(baseUrl, "renderedd");

    assertEquals(ThumbnailMode.AUTO, ThumbnailServiceRegistry.configuredMode(baseUrl));
    assertEquals(
        ThumbnailMode.RS_THUMBNAIL, ThumbnailServiceRegistry.currentMode(baseUrl, CANDIDATES));
  }

  @Test
  void a_new_manifest_drops_what_the_archive_taught_a_previous_one() {
    String baseUrl = "http://arc-reload/rs";
    ThumbnailServiceRegistry.configure(baseUrl, null);
    ThumbnailServiceRegistry.downgrade(baseUrl, ThumbnailMode.RS_THUMBNAIL, CANDIDATES);

    ThumbnailServiceRegistry.configure(baseUrl + "/", null);
    assertEquals(
        ThumbnailMode.RS_THUMBNAIL, ThumbnailServiceRegistry.currentMode(baseUrl, CANDIDATES));
  }
}
