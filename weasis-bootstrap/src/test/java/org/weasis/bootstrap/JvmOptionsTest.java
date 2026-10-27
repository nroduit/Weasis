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

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JvmOptionsTest {

  @Test
  void tokenizesWithQuotes() {
    assertEquals(
        List.of("-Xmx1g", "-Dweasis.name=My Viewer", "-splash:x"),
        JvmOptions.tokenize("-Xmx1g  \"-Dweasis.name=My Viewer\" -splash:x"));
    assertTrue(JvmOptions.tokenize(null).isEmpty());
  }

  @Test
  void allowListRefusesWhatLoadsCode() {
    for (String refused :
        List.of(
            "-javaagent:x.jar",
            "-agentpath:/x.so",
            "-agentlib:x",
            "-Xbootclasspath/a:x.jar",
            "--patch-module",
            "--module-path=/m",
            "-cp",
            "--class-path",
            "--upgrade-module-path=x",
            "-XX:AOTCache=/x",
            "-XX:OnError=rm -rf",
            "-Djava.library.path=/x",
            "-Dweasis.bootstrap.version=9",
            "--add-modules=x")) {
      assertFalse(JvmOptions.isAllowed(refused), refused);
    }
    for (String allowed :
        List.of(
            "-Xmx2g",
            "-XX:MaxRAMPercentage=25",
            "-Dsun.awt.disablegrab=true",
            "--enable-native-access=ALL-UNNAMED",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--enable-preview",
            "-splash:$APPDIR/s.png")) {
      assertTrue(JvmOptions.isAllowed(allowed), allowed);
    }
    List<String> refused = new ArrayList<>();
    assertEquals(List.of("-Xmx2g"), JvmOptions.allowed(List.of("-Xmx2g", "-javaagent:x"), refused));
    assertEquals(List.of("-javaagent:x"), refused);
  }

  @Test
  void substitutesTheApplicationDirectory() {
    assertEquals(
        List.of("-splash:/opt/weasis/lib/app/s.png"),
        JvmOptions.substitute(List.of("-splash:$APPDIR/s.png"), "/opt/weasis/lib/app"));
  }
}
