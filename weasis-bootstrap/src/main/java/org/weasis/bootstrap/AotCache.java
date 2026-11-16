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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The per-user JDK AOT cache of a stage-2 entry (JEP 483/514/515). The flags are plain arguments of
 * the stage-2 command line: {@code -XX:AOTCache} when the cache exists, {@code -XX:AOTCacheOutput}
 * to train it, never both. The file is keyed by the launcher set and the runtime image, so a new
 * set or runtime trains again; a cache that crashed the JVM early is deleted and marked disabled
 * for that key.
 */
final class AotCache {

  private final Path file;

  private AotCache(Path file) {
    this.file = file;
  }

  /**
   * {@code <cache>/aot[/<subdir>]/<prefix>-<setId>-<runtimeStamp>.aot}; empty when the launch opted
   * out ({@code -Dweasis.aot.cache=false}).
   */
  static AotCache of(Path cacheRoot, String prefix, String subdir, String setId) {
    if ("false".equals(System.getProperty(Contract.P_AOT))) { // NON-NLS
      return null;
    }
    long runtimeMtime = 0;
    try {
      runtimeMtime =
          Files.getLastModifiedTime(Path.of(System.getProperty("java.home"))).toMillis(); // NON-NLS
    } catch (IOException | RuntimeException e) {
      BootLog.note("cannot read the runtime image timestamp: " + e); // NON-NLS
    }
    Path dir = cacheRoot.resolve("aot"); // NON-NLS
    if (!subdir.isEmpty()) {
      dir = dir.resolve(subdir);
    }
    String name =
        "%s-%s-%s.aot".formatted(prefix, setId, Long.toHexString(runtimeMtime)); // NON-NLS
    return new AotCache(dir.resolve(name));
  }

  Path file() {
    return file;
  }

  boolean exists() {
    return Files.exists(file);
  }

  boolean disabled() {
    return Files.exists(disabledMarker());
  }

  Path disabledMarker() {
    String name = file.getFileName().toString();
    return file.resolveSibling(name.substring(0, name.length() - 4) + ".disabled"); // NON-NLS
  }

  /** Creates the directory and removes the caches of other sets or runtimes. */
  void prepare() throws IOException {
    Files.createDirectories(file.getParent());
    try (Stream<Path> files = Files.list(file.getParent())) {
      for (Path p : files.toList()) {
        String name = p.getFileName().toString();
        if ((name.endsWith(".aot")
                || name.endsWith(".disabled")
                || name.endsWith(".training")
                || name.endsWith(".pending")) // NON-NLS
            && !p.equals(file)
            && !p.equals(disabledMarker())
            && !p.equals(trainingMarker())
            && !p.equals(pendingMarker())) {
          Files.deleteIfExists(p);
        }
      }
    }
  }

  Path trainingMarker() {
    return file.resolveSibling(file.getFileName() + ".training"); // NON-NLS
  }

  /**
   * Written before stage 1 replaces itself (macOS) by a launcher using this cache, deleted by that
   * process once it came up ({@link Contract#P_PENDING}).
   */
  Path pendingMarker() {
    return file.resolveSibling(file.getFileName() + ".pending"); // NON-NLS
  }

  /** The flags that use the cache when it exists, or train it when {@code train} is allowed. */
  List<String> flags(boolean train) {
    List<String> flags = new ArrayList<>();
    if (disabled()) {
      return flags;
    }
    if (exists()) {
      flags.add("-XX:AOTCache=" + file); // NON-NLS
    } else if (train) {
      flags.addAll(trainingFlags());
    }
    return flags;
  }

  /**
   * Training flags. {@code AOTClassLinking} is enabled from JDK 27, where JDK-8373392 fixed the
   * use-time crash of JDK ≤ 26; {@link Contract#P_AOT_CLASSLINKING} overrides the gate.
   */
  List<String> trainingFlags() {
    List<String> flags = new ArrayList<>();
    flags.add("-XX:AOTCacheOutput=" + file); // NON-NLS
    if (!useClassLinking(
        Runtime.version().feature(), System.getProperty(Contract.P_AOT_CLASSLINKING))) {
      flags.add("-XX:-AOTClassLinking"); // NON-NLS
    }
    return flags;
  }

  static boolean useClassLinking(int runtimeFeature, String override) {
    if (override != null) {
      return Boolean.parseBoolean(override);
    }
    return runtimeFeature >= 27;
  }

  /** A cache that crashed the JVM: deleted, and disabled for this key. */
  void discard() {
    try {
      Files.deleteIfExists(file);
      Files.writeString(disabledMarker(), ""); // NON-NLS
    } catch (IOException e) {
      BootLog.note("cannot discard the AOT cache: " + e); // NON-NLS
    }
  }
}
