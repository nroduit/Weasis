/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WeasisPluginManagerTest {

  @Test
  void classpathJarNamesKeepsOnlyJarFileNames() {
    String cp =
        String.join(
            File.pathSeparator,
            "/opt/weasis/app/weasis-launcher.jar",
            "/opt/weasis/app/common-lib/weasis-core-5.0.0.jar",
            "/opt/weasis/app/common-lib/joml-1.10.9.jar",
            "/opt/weasis/app/classes",
            "relative/zip4j-2.11.5.jar");
    Set<String> names = WeasisPluginManager.classpathJarNames(cp);
    assertEquals(
        Set.of(
            "weasis-launcher.jar", "weasis-core-5.0.0.jar", "joml-1.10.9.jar", "zip4j-2.11.5.jar"),
        names);
  }

  @Test
  void classpathJarNamesHandlesEmptyClasspath() {
    assertTrue(WeasisPluginManager.classpathJarNames("").isEmpty());
  }
}
