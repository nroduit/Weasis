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

import java.nio.file.Path;
import java.util.function.UnaryOperator;

/**
 * The per-OS cache and state directories, resolved exactly as the launcher does ({@code
 * org.weasis.pref.PlatformDirectories}): XDG on Linux, {@code ~/Library} on macOS, {@code
 * %LOCALAPPDATA%} on Windows, and one root when the launch is portable or {@code weasis.path} is
 * set.
 */
final class PlatformDirs {

  enum Dir {
    CONFIG,
    CACHE,
    STATE
  }

  private PlatformDirs() {}

  static Path get(Dir dir) {
    String explicit = System.getProperty("weasis.path"); // NON-NLS
    Path home = Path.of(System.getProperty("user.home", "")); // NON-NLS
    if (System.getProperty(Contract.P_PORTABLE) != null) {
      return home.resolve(".weasis"); // NON-NLS
    }
    if (explicit != null && !explicit.isBlank()) {
      return Path.of(explicit);
    }
    return get(dir, System.getProperty("os.name", ""), home, System::getenv); // NON-NLS
  }

  static Path get(Dir dir, String osName, Path home, UnaryOperator<String> env) {
    String os = osName.toLowerCase();
    if (os.startsWith("win")) { // NON-NLS
      if (dir == Dir.CONFIG) {
        return envDir(env, "APPDATA", home.resolve("AppData").resolve("Roaming")) // NON-NLS
            .resolve("Weasis"); // NON-NLS
      }
      return envDir(env, "LOCALAPPDATA", home.resolve("AppData").resolve("Local")) // NON-NLS
          .resolve("Weasis"); // NON-NLS
    }
    if (os.startsWith("mac") || os.contains("darwin")) { // NON-NLS
      Path library = home.resolve("Library"); // NON-NLS
      return switch (dir) {
        case CONFIG -> library.resolve("Application Support").resolve("Weasis"); // NON-NLS
        case CACHE -> library.resolve("Caches").resolve("Weasis"); // NON-NLS
        case STATE -> library.resolve("Logs").resolve("Weasis"); // NON-NLS
      };
    }
    return switch (dir) {
      case CONFIG ->
          envDir(env, "XDG_CONFIG_HOME", home.resolve(".config")).resolve("weasis"); // NON-NLS
      case CACHE -> envDir(env, "XDG_CACHE_HOME", home.resolve(".cache")).resolve("weasis");
      case STATE ->
          envDir(env, "XDG_STATE_HOME", home.resolve(".local").resolve("state"))
              .resolve("weasis"); // NON-NLS
    };
  }

  private static Path envDir(UnaryOperator<String> env, String name, Path fallback) {
    String value = env.apply(name);
    if (value != null && !value.isBlank()) {
      Path p = Path.of(value);
      if (p.isAbsolute()) {
        return p;
      }
    }
    return fallback;
  }

  static boolean isMacOS() {
    return System.getProperty("os.name", "").toLowerCase().startsWith("mac"); // NON-NLS
  }

  static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase().startsWith("win"); // NON-NLS
  }

  /**
   * The platform name used by the per-OS option keys: {@code linux}, {@code windows}, {@code
   * macosx}.
   */
  static String osKey() {
    if (isWindows()) {
      return "windows"; // NON-NLS
    }
    return isMacOS() ? "macosx" : "linux"; // NON-NLS
  }
}
