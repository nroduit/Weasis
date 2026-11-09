/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.ui.gui;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The feedback link names the form and carries the version and the platform, encoded. */
class FeedbackLinkTest {

  @Test
  void theLinkOpensTheFormWithTheVersionAndThePlatform() {
    String url = FeedbackLink.url("5.0.0-beta.1", "Linux 6.8 amd64, 2 monitors 2560x1440", null);
    assertAll(
        () ->
            assertTrue(url.startsWith(FeedbackLink.ISSUES + "?template=preview_finding.yml"), url),
        () -> assertTrue(url.contains("&version=5.0.0-beta.1"), url),
        () ->
            assertTrue(
                url.contains("&platform=Linux%206.8%20amd64%2C%202%20monitors%202560x1440"), url),
        () -> assertFalse(url.contains("feature="), url),
        () -> assertFalse(url.contains("+"), "spaces are %20, not +"));
  }

  @Test
  void aFeatureIsAddedWhenGiven() {
    String url = FeedbackLink.url("5.0.0-beta.1", "macOS", "Hanging protocols");
    assertAll(
        () -> assertTrue(url.endsWith("&feature=Hanging%20protocols"), url),
        () -> assertEquals(1, url.chars().filter(c -> c == '?').count()));
  }

  @Test
  void thePlatformNamesTheOperatingSystem() {
    String platform = FeedbackLink.platform();
    assertTrue(platform.contains(System.getProperty("os.name")), platform);
  }
}
