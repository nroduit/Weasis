/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.pref;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.weasis.pref.PlatformDirectories.BaseDir;

class PlatformDirectoriesTest {

  private static final Path HOME = Paths.get("/home/jane");

  private static UnaryOperator<String> env(Map<String, String> values) {
    return values::get;
  }

  private static UnaryOperator<String> noEnv() {
    return key -> null;
  }

  @Test
  void linuxUsesXdgDefaultsWhenNoEnv() {
    assertAll(
        () ->
            assertEquals(
                HOME.resolve(".config/weasis"), get(BaseDir.CONFIG, "Linux", HOME, noEnv())),
        () ->
            assertEquals(
                HOME.resolve(".local/share/weasis"), get(BaseDir.DATA, "Linux", HOME, noEnv())),
        () ->
            assertEquals(HOME.resolve(".cache/weasis"), get(BaseDir.CACHE, "Linux", HOME, noEnv())),
        () ->
            assertEquals(
                HOME.resolve(".local/state/weasis"), get(BaseDir.STATE, "Linux", HOME, noEnv())));
  }

  @Test
  void linuxHonorsAbsoluteXdgEnvVars() {
    UnaryOperator<String> env =
        env(
            Map.of(
                "XDG_CONFIG_HOME", "/cfg",
                "XDG_DATA_HOME", "/dat",
                "XDG_CACHE_HOME", "/cch",
                "XDG_STATE_HOME", "/st"));
    assertAll(
        () -> assertEquals(Paths.get("/cfg/weasis"), get(BaseDir.CONFIG, "Linux", HOME, env)),
        () -> assertEquals(Paths.get("/dat/weasis"), get(BaseDir.DATA, "Linux", HOME, env)),
        () -> assertEquals(Paths.get("/cch/weasis"), get(BaseDir.CACHE, "Linux", HOME, env)),
        () -> assertEquals(Paths.get("/st/weasis"), get(BaseDir.STATE, "Linux", HOME, env)));
  }

  @Test
  void relativeXdgEnvVarsAreIgnored() {
    UnaryOperator<String> env = env(Map.of("XDG_CONFIG_HOME", "relative/path"));
    assertEquals(HOME.resolve(".config/weasis"), get(BaseDir.CONFIG, "Linux", HOME, env));
  }

  @Test
  void macosUsesLibraryConventions() {
    assertAll(
        () ->
            assertEquals(
                HOME.resolve("Library/Application Support/Weasis"),
                get(BaseDir.CONFIG, "Mac OS X", HOME, noEnv())),
        () ->
            assertEquals(
                HOME.resolve("Library/Application Support/Weasis"),
                get(BaseDir.DATA, "Mac OS X", HOME, noEnv())),
        () ->
            assertEquals(
                HOME.resolve("Library/Caches/Weasis"),
                get(BaseDir.CACHE, "Mac OS X", HOME, noEnv())),
        () ->
            assertEquals(
                HOME.resolve("Library/Logs/Weasis"),
                get(BaseDir.STATE, "Mac OS X", HOME, noEnv())));
  }

  @Test
  void windowsUsesAppDataFolders() {
    // Absolute values must be platform-appropriate so isAbsolute() holds on the test host.
    Path roaming = HOME.resolve("AppData").resolve("Roaming");
    Path local = HOME.resolve("AppData").resolve("Local");
    UnaryOperator<String> env =
        env(Map.of("APPDATA", roaming.toString(), "LOCALAPPDATA", local.toString()));
    assertAll(
        () -> assertEquals(roaming.resolve("Weasis"), get(BaseDir.CONFIG, "Windows 11", HOME, env)),
        () -> assertEquals(local.resolve("Weasis"), get(BaseDir.CACHE, "Windows 11", HOME, env)));
  }

  @Test
  void windowsFallsBackToHomeWhenEnvMissing() {
    assertEquals(
        HOME.resolve("AppData").resolve("Roaming").resolve("Weasis"),
        get(BaseDir.CONFIG, "Windows 11", HOME, noEnv()));
  }

  private static Path get(BaseDir dir, String os, Path home, UnaryOperator<String> env) {
    return PlatformDirectories.get(dir, os, home, env);
  }
}
