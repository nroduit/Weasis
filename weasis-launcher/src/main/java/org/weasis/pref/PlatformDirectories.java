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

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the per-OS base directories used by Weasis.
 *
 * <p>On Linux (and other Unix systems) the <a
 * href="https://specifications.freedesktop.org/basedir-spec/basedir-spec-latest.html">XDG Base
 * Directory Specification</a> is honored, including the {@code XDG_*} environment variables. macOS
 * uses the {@code ~/Library} conventions and Windows the {@code %APPDATA%}/{@code %LOCALAPPDATA%}
 * folders. Any other platform falls back to the historical {@code ~/.weasis} directory.
 *
 * <p>This class is a pure static utility with no dependency on {@link ConfigData}, so it can be
 * used very early during bootstrap (e.g. the launcher boot logging).
 */
public final class PlatformDirectories {
  private static final Logger LOGGER = LoggerFactory.getLogger(PlatformDirectories.class);

  /** Application folder name used for XDG locations (lowercase, no leading dot). */
  private static final String APP_DIR_XDG = "weasis"; // NON-NLS

  /** Application folder name used for native macOS/Windows locations. */
  private static final String APP_DIR_NATIVE = "Weasis"; // NON-NLS

  /** Categories of base directories, mapped to the XDG roots on Linux. */
  public enum BaseDir {
    /** User configuration (preferences, proxy). XDG_CONFIG_HOME. */
    CONFIG,
    /** User data (per-bundle data, downloaded resources). XDG_DATA_HOME. */
    DATA,
    /** Non-essential, regenerable cached data (plugin cache). XDG_CACHE_HOME. */
    CACHE,
    /** State data such as logs. XDG_STATE_HOME. */
    STATE
  }

  private PlatformDirectories() {}

  /** Resolves the application directory for the given category on the current platform. */
  public static Path get(BaseDir dir) {
    return get(dir, System.getProperty("os.name", ""), userHome(), System::getenv); // NON-NLS
  }

  /** Deterministic resolution used by {@link #get(BaseDir)} and by tests. */
  static Path get(BaseDir dir, String osName, Path home, UnaryOperator<String> env) {
    String os = osName.toLowerCase();
    if (os.startsWith("win")) { // NON-NLS
      return windows(dir, home, env);
    }
    if (os.startsWith("mac") || os.contains("darwin")) { // NON-NLS
      return macos(dir, home);
    }
    return xdg(dir, home, env);
  }

  /** The historical single-root directory {@code ~/.weasis}. */
  public static Path legacyDir() {
    return userHome().resolve(".weasis"); // NON-NLS
  }

  private static Path userHome() {
    return Paths.get(System.getProperty("user.home", "")); // NON-NLS
  }

  private static Path xdg(BaseDir dir, Path home, UnaryOperator<String> env) {
    return switch (dir) {
      case CONFIG -> envDir(env, "XDG_CONFIG_HOME", home.resolve(".config")).resolve(APP_DIR_XDG);
      case DATA ->
          envDir(env, "XDG_DATA_HOME", home.resolve(".local").resolve("share"))
              .resolve(APP_DIR_XDG);
      case CACHE -> envDir(env, "XDG_CACHE_HOME", home.resolve(".cache")).resolve(APP_DIR_XDG);
      case STATE ->
          envDir(env, "XDG_STATE_HOME", home.resolve(".local").resolve("state"))
              .resolve(APP_DIR_XDG);
    };
  }

  private static Path macos(BaseDir dir, Path home) {
    Path library = home.resolve("Library"); // NON-NLS
    return switch (dir) {
      case CONFIG, DATA ->
          library.resolve("Application Support").resolve(APP_DIR_NATIVE); // NON-NLS
      case CACHE -> library.resolve("Caches").resolve(APP_DIR_NATIVE); // NON-NLS
      case STATE -> library.resolve("Logs").resolve(APP_DIR_NATIVE); // NON-NLS
    };
  }

  private static Path windows(BaseDir dir, Path home, UnaryOperator<String> env) {
    Path roaming = envDir(env, "APPDATA", home.resolve("AppData").resolve("Roaming")); // NON-NLS
    Path local = envDir(env, "LOCALAPPDATA", home.resolve("AppData").resolve("Local")); // NON-NLS
    return switch (dir) {
      case CONFIG, DATA -> roaming.resolve(APP_DIR_NATIVE);
      case CACHE, STATE -> local.resolve(APP_DIR_NATIVE);
    };
  }

  /** Returns the directory from the environment variable if it holds an absolute path. */
  private static Path envDir(UnaryOperator<String> env, String envVar, Path defaultDir) {
    String value = env.apply(envVar);
    if (value != null && !value.isBlank()) {
      Path p = Paths.get(value);
      // The XDG specification requires environment values to be ignored if not absolute.
      if (p.isAbsolute()) {
        return p;
      }
      LOGGER.warn("Ignoring non-absolute {}={}", envVar, value);
    }
    return defaultDir;
  }

  /**
   * Migrates a legacy {@code ~/.weasis} installation to the resolved base directories. The bundle
   * cache is regenerable, so it is deleted rather than moved. Runs at most once (a marker file is
   * written in the legacy directory) and is a no-op when the legacy directory is already one of the
   * targets (legacy layout) or has nothing to migrate.
   */
  public static void migrateLegacyLayout(Path config, Path data, Path state) {
    migrateLegacyLayout(legacyDir(), config, data, state);
  }

  /** Migration core with an explicit legacy directory (testable seam). */
  static void migrateLegacyLayout(Path legacy, Path config, Path data, Path state) {
    if (!Files.isDirectory(legacy) || legacy.equals(data) || legacy.equals(config)) {
      return;
    }
    Path marker = legacy.resolve(".xdg-migrated"); // NON-NLS
    if (Files.exists(marker)) {
      return;
    }
    try {
      moveDirectory(legacy.resolve("preferences"), config.resolve("preferences")); // NON-NLS
      moveDirectory(legacy.resolve("data"), data.resolve("data")); // NON-NLS
      moveDirectory(legacy.resolve("log"), state.resolve("log")); // NON-NLS
      deleteRecursively(legacy.resolve("cache")); // NON-NLS
      Files.writeString(marker, "Migrated to XDG base directories.\n"); // NON-NLS
      LOGGER.info(
          "Migrated legacy {} to config={}, data={}, state={}", legacy, config, data, state);
    } catch (IOException e) {
      LOGGER.error("Cannot migrate legacy directory {}", legacy, e);
    }
  }

  /** Recursively moves source into target, merging without overwriting existing files. */
  private static void moveDirectory(Path source, Path target) throws IOException {
    if (!Files.isDirectory(source)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(source)) {
      for (Path src : (Iterable<Path>) walk::iterator) {
        Path dest = target.resolve(source.relativize(src));
        if (Files.isDirectory(src)) {
          Files.createDirectories(dest);
        } else if (!Files.exists(dest)) {
          Files.createDirectories(dest.getParent());
          moveOrCopy(src, dest);
        }
      }
    }
    deleteRecursively(source);
  }

  private static void moveOrCopy(Path src, Path dest) throws IOException {
    try {
      Files.move(src, dest);
    } catch (FileAlreadyExistsException e) {
      // Keep the destination already present from a previous launch.
    } catch (IOException e) {
      // Different filesystem: fall back to a plain copy.
      Files.copy(src, dest);
    }
  }

  private static void deleteRecursively(Path dir) throws IOException {
    if (!Files.exists(dir)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(dir)) {
      for (Path p : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) {
        Files.deleteIfExists(p);
      }
    }
  }
}
