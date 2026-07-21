/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AutoProcessorTest {

  @ParameterizedTest
  @CsvSource({
    "weasis.plugins.7, 7",
    "weasis.plugins.120, 120",
    "WEASIS.PLUGINS.30, 30",
    "weasis.plugins.0, 0",
  })
  void getPluginLevelReturnsNumericSuffix(String key, int expected) {
    assertEquals(expected, AutoProcessor.getPluginLevel(key));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "weasis.plugins.max.level",
        "weasis.plugins.license",
        "weasis.plugins.",
        "weasis.plugins.7a",
        "weasis.plugin.cache.dir",
        "felix.auto.start.10",
        "other.key"
      })
  void getPluginLevelRejectsNonNumericEntries(String key) {
    assertEquals(-1, AutoProcessor.getPluginLevel(key));
  }

  @Test
  void getPluginLevelRejectsNull() {
    assertEquals(-1, AutoProcessor.getPluginLevel(null));
  }

  @ParameterizedTest
  @CsvSource({
    "https://server/weasis/bundle/weasis-core-5.0.0.jar.xz, weasis-core-5.0.0",
    "file:/repo/org/joml/joml-1.10.9.jar, joml-1.10.9",
    "weasis-dicom-codec-5.0.0.jar, weasis-dicom-codec-5.0.0",
    "plain-name, plain-name",
  })
  void getPluginNameFromLocationStripsPathAndExtensions(String location, String expected) {
    assertEquals(expected, AutoProcessor.getPluginNameFromLocation(location));
  }

  @Test
  void getPluginNameFromLocationHandlesNull() {
    assertNull(AutoProcessor.getPluginNameFromLocation(null));
  }

  @Test
  void releaseJarsAreAlwaysCacheable() {
    assertTrue(AutoProcessor.isCacheable("weasis-core-5.0.0", false));
    assertTrue(AutoProcessor.isCacheable("weasis-core-5.0.0", true));
  }

  @Test
  void snapshotJarsAreCacheableOnlyInWarmupMode() {
    assertFalse(AutoProcessor.isCacheable("weasis-core-5.0.0-SNAPSHOT", false));
    assertTrue(AutoProcessor.isCacheable("weasis-core-5.0.0-SNAPSHOT", true));
  }
}
