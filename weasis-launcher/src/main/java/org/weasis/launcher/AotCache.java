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

import java.awt.SplashScreen;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.pref.PlatformDirectories;

/**
 * Manages the per-user JDK AOT cache (JEP 483/514/515) for packaged launches. The first process
 * (stage 1) re-executes the native launcher with {@code JAVA_TOOL_OPTIONS} carrying either {@code
 * -XX:AOTCache} (cache present) or {@code -XX:AOTCacheOutput} (training run) — the two flags are
 * mutually exclusive. The child (stage 2) is the real application and the only Singleton
 * participant. Every failure degrades to a normal launch.
 */
public final class AotCache {

  private static final Logger LOGGER = LoggerFactory.getLogger(AotCache.class);

  /** Set to "2" in the re-executed application process. */
  public static final String STAGE_PROP = "weasis.aot.stage";

  /** Set to "false" to disable the AOT cache relaunch entirely. */
  public static final String ENABLE_PROP = "weasis.aot.cache";

  /** Start time of the stage-1 process, used to measure the relaunch overhead. */
  private static final String PARENT_START_PROP = "weasis.aot.t0";

  private AotCache() {}

  /**
   * Re-executes the native launcher with the AOT flags when eligible. Does not return on success
   * (the parent exits with the child's exit code); returns false to continue a normal launch.
   */
  public static boolean relaunch(String[] args) {
    if (!isEligible()) {
      return false;
    }
    Optional<String> command = nativeCommand();
    if (command.isEmpty()) {
      return false;
    }
    try {
      Path cache = cacheFile();
      // Quoted tokens and ${...} sequences in JAVA_TOOL_OPTIONS break the jpackage native
      // launcher, so the path must be passed unquoted and free of such characters.
      if (cache
          .toString()
          .chars()
          .anyMatch(c -> Character.isWhitespace(c) || c == '$' || c == '"')) {
        LOGGER.warn(
            "AOT cache path contains unsupported characters, skipping AOT relaunch: {}", cache);
        return false;
      }
      Files.createDirectories(cache.getParent());
      pruneOtherCaches(cache.getParent(), cache);

      closeSplashScreen();

      List<String> cmd = new ArrayList<>();
      cmd.add(command.get());
      cmd.addAll(List.of(args));
      boolean useMode = Files.exists(cache);
      long start = System.currentTimeMillis();
      int exitCode = spawnStage2(command.get(), args, buildToolOptions(cache));
      if (useMode && exitCode != 0 && System.currentTimeMillis() - start < 10_000) {
        // The child died right away: the cache is likely unusable. Discard it and run once
        // without any AOT flag so a bad cache can never prevent the application from starting.
        LOGGER.warn("Child process failed early with AOT cache {}, retrying without it", cache);
        FileUtil.delete(cache);
        exitCode = spawnStage2(command.get(), args, "-D%s=2".formatted(STAGE_PROP));
      }
      System.exit(exitCode);
      return true; // unreachable
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOGGER.warn("AOT relaunch interrupted, continuing with a normal launch", e);
      return false;
    } catch (Exception e) {
      LOGGER.warn("AOT relaunch failed, continuing with a normal launch", e);
      return false;
    }
  }

  /** Logs the AOT mode of the current process and the stage-1 relaunch overhead. */
  public static void logStatus() {
    String mode = "off"; // NON-NLS
    for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
      if (arg.startsWith("-XX:AOTCacheOutput=")) {
        mode = "train"; // NON-NLS
        break;
      } else if (arg.startsWith("-XX:AOTCache=")) {
        mode = "use"; // NON-NLS
        break;
      }
    }
    LOGGER.info("*PERF* AOT cache, type:INIT mode:{}", mode);

    Long parentStart = Long.getLong(PARENT_START_PROP);
    if (parentStart != null) {
      long overhead = ManagementFactory.getRuntimeMXBean().getStartTime() - parentStart;
      LOGGER.info("*PERF* AOT relaunch overhead, type:INIT time:{}", overhead);
    }
  }

  static boolean isEligible() {
    return System.getProperty("jpackage.app-version") != null
        && !"2".equals(System.getProperty(STAGE_PROP))
        && !"false".equals(System.getProperty(ENABLE_PROP))
        && !System.getProperty("os.name", "").toLowerCase().startsWith("mac"); // NON-NLS
  }

  /** Resolves the native launcher executable of the current process (a jpackage runtime). */
  private static Optional<String> nativeCommand() {
    Optional<String> command = ProcessHandle.current().info().command();
    if (command.isEmpty()) {
      LOGGER.warn("Cannot resolve the native launcher command, skipping AOT relaunch");
      return Optional.empty();
    }
    String name = Path.of(command.get()).getFileName().toString();
    if (name.equals("java") || name.equals("java.exe")) { // NON-NLS
      LOGGER.warn("Process started by a plain java command, skipping AOT relaunch");
      return Optional.empty();
    }
    return command;
  }

  /**
   * Cache file stamped with the application version and the runtime-image timestamp, so an
   * application or runtime update always trains a fresh cache.
   */
  static Path cacheFile() {
    String version = System.getProperty("jpackage.app-version", "unknown"); // NON-NLS
    long runtimeMtime = 0;
    try {
      runtimeMtime = Files.getLastModifiedTime(Path.of(System.getProperty("java.home"))).toMillis();
    } catch (Exception e) {
      LOGGER.warn("Cannot read the runtime image timestamp", e);
    }
    String name = "weasis-%s-%s.aot".formatted(version, Long.toHexString(runtimeMtime)); // NON-NLS
    return PlatformDirectories.get(PlatformDirectories.BaseDir.CACHE)
        .resolve("aot") // NON-NLS
        .resolve(name);
  }

  static void pruneOtherCaches(Path aotDir, Path keep) {
    try (Stream<Path> stream = Files.list(aotDir)) {
      stream
          .filter(p -> p.getFileName().toString().endsWith(".aot") && !p.equals(keep))
          .forEach(FileUtil::delete);
    } catch (Exception e) {
      LOGGER.warn("Cannot prune old AOT caches in {}", aotDir, e);
    }
  }

  /** Spawns the native launcher with the given JAVA_TOOL_OPTIONS and waits for it. */
  private static int spawnStage2(String command, String[] args, String toolOptions)
      throws Exception {
    List<String> cmd = new ArrayList<>();
    cmd.add(command);
    cmd.addAll(List.of(args));
    ProcessBuilder pb = new ProcessBuilder(cmd);
    // The Linux jpackage launcher exports _JPACKAGE_LAUNCHER to detect a self-execve; a child
    // launcher inheriting it skips its .cfg ("Not building JVM arguments from cfg file") and
    // dies on the java usage screen, so it must not leak into the re-executed process.
    pb.environment().remove("_JPACKAGE_LAUNCHER");
    pb.environment().merge("JAVA_TOOL_OPTIONS", toolOptions, (old, opts) -> old + " " + opts);
    pb.inheritIO();
    return pb.start().waitFor();
  }

  /**
   * Builds the {@code JAVA_TOOL_OPTIONS} value for the child: use the cache when it exists,
   * otherwise train it this launch ({@code -XX:AOTCache} and {@code -XX:AOTCacheOutput} must never
   * be combined — the JVM refuses to start). Training disables {@code AOTClassLinking}: a cache
   * with AOT-linked classes trained against this application crashes the JVM at use time
   * (InternalError in AppClassLoader.&lt;clinit&gt; on JDK 26); the class-parsing tier is stable.
   */
  static String buildToolOptions(Path cache) {
    if (Files.exists(cache)) {
      return "-D%s=2 -D%s=%d -XX:AOTCache=%s"
          .formatted(STAGE_PROP, PARENT_START_PROP, System.currentTimeMillis(), cache);
    }
    return "-D%s=2 -D%s=%d -XX:AOTCacheOutput=%s -XX:-AOTClassLinking"
        .formatted(STAGE_PROP, PARENT_START_PROP, System.currentTimeMillis(), cache);
  }

  /** Closes the stage-1 native splash so only the child shows one. */
  private static void closeSplashScreen() {
    try {
      SplashScreen splash = SplashScreen.getSplashScreen();
      if (splash != null) {
        splash.close();
      }
    } catch (Exception e) {
      LOGGER.debug("Cannot close the splash screen", e);
    }
  }
}
