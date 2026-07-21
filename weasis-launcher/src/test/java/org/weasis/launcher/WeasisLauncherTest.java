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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WeasisLauncherTest {

  private static Map<String, String> config(String... keyValues) {
    Map<String, String> map = new HashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put(keyValues[i], keyValues[i + 1]);
    }
    return map;
  }

  @Test
  void noConfigMeansStartAllAtOnce() {
    assertTrue(WeasisLauncher.deferredPluginIds(config()).isEmpty());
  }

  @Test
  void progressiveFalseIsStillEmpty() {
    assertTrue(
        WeasisLauncher.deferredPluginIds(config("weasis.plugins.progressive", "false")).isEmpty());
  }

  @Test
  void progressiveTrueUsesBuiltInDeferredSet() {
    Set<String> deferred =
        WeasisLauncher.deferredPluginIds(config("weasis.plugins.progressive", "true"));
    assertEquals(Set.copyOf(WeasisLauncher.DEFAULT_DEFERRED_PLUGINS), deferred);
  }

  @Test
  void explicitListWinsOverProgressive() {
    Set<String> deferred =
        WeasisLauncher.deferredPluginIds(
            config(
                "weasis.plugins.progressive", "true",
                "weasis.plugins.deferred", "  weasis-dicom-rt   weasis-dicom-sr "));
    assertEquals(Set.of("weasis-dicom-rt", "weasis-dicom-sr"), deferred);
  }

  @Test
  void blankExplicitListFallsBackToStartAll() {
    assertTrue(
        WeasisLauncher.deferredPluginIds(config("weasis.plugins.deferred", "   ")).isEmpty());
  }
}
