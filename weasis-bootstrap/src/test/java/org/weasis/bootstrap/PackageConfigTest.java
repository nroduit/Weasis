/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PackageConfigTest {

  static final String BASE =
      """
      {"weasisPreferences": [
        {"code": "weasis.version", "value": "5.0.0"},
        {"code": "weasis.launcher.url", "value": "${weasis.codebase.url}/launcher/weasis-launcher.jar"},
        {"code": "weasis.launcher.libs", "value": "${weasis.codebase.url}/launcher/b.jar ${weasis.codebase.url}/launcher/a.jar"},
        {"code": "weasis.launcher.sha256", "value": "${weasis.codebase.url}/launcher/launcher.sha256"},
        {"code": "weasis.launcher.jvm-options", "value": "-Xmx1g -splash:$APPDIR/s.png"},
        {"code": "weasis.launcher.jvm-options.linux", "value": "-Dsun.awt.disablegrab=true"},
        {"code": "weasis.requires.jdk", "value": "27"},
        {"code": "weasis.requires.modules", "value": "java.base, java.desktop"},
        {"code": "weasis.requires.bootstrap", "value": "1-2"}
      ]}
      """;

  static PackageConfig config() {
    return PackageConfig.parse(BASE.getBytes(StandardCharsets.UTF_8), "https://hub/weasis");
  }

  @Test
  void resolvesTheCodebaseVariableOnly() {
    PackageConfig config = config();
    assertTrue(config.declaresLauncherSet());
    assertEquals(
        List.of(
            URI.create("https://hub/weasis/launcher/weasis-launcher.jar"),
            URI.create("https://hub/weasis/launcher/b.jar"),
            URI.create("https://hub/weasis/launcher/a.jar")),
        config.launcherSetUrls());
    assertEquals("5.0.0", config.version());
    assertEquals(
        List.of("-Xmx1g", "-splash:$APPDIR/s.png", "-Dsun.awt.disablegrab=true"),
        config.jvmOptions("linux"));
    assertEquals(List.of("-Xmx1g", "-splash:$APPDIR/s.png"), config.jvmOptions("windows"));
  }

  @Test
  void readsTheRequirements() {
    PackageConfig config = config();
    assertEquals(27, config.requiresJdk().orElseThrow());
    assertEquals(Set.of("java.base", "java.desktop"), config.requiresModules());
    assertEquals(Set.of(1, 2), config.requiresBootstrap());
    assertEquals(8, PackageConfig.major("1.8.0"));
    assertEquals(27, PackageConfig.major("27.0.1+5"));
  }

  @Test
  void mergeOverridesAndEmptyDeclaresNothing() {
    PackageConfig merged =
        config()
            .merge(
                """
                {"weasisPreferences": [{"code": "weasis.launcher.jvm-options", "value": "-Xmx2g"}]}
                """
                    .getBytes(StandardCharsets.UTF_8));
    assertEquals(List.of("-Xmx2g"), merged.jvmOptions("windows"));
    assertFalse(PackageConfig.empty().declaresLauncherSet());
    assertEquals(
        "org.weasis.launcher.Weasis",
        PackageConfig.empty().entryClass("org.weasis.launcher.Weasis"));
  }
}
