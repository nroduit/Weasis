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

  /**
   * Overrides the JDK-version gate for {@code AOTClassLinking} (JEP 515 method profiles +
   * pre-linked classes). By default the linked tier is used only from JDK 27, where JDK-8373392
   * fixed the use-time crash present on JDK ≤ 26 ({@code InternalError} in {@code
   * AppClassLoader.<clinit>}); set "true"/"false" to force it on/off regardless of the runtime
   * version.
   */
  public static final String CLASSLINKING_PROP = "weasis.aot.classlinking";

  /** Start time of the stage-1 process, used to measure the relaunch overhead. */
  private static final String PARENT_START_PROP = "weasis.aot.t0";

  private AotCache() {}

  /**
   * Ensures the AOT cache is used with the least overhead, safely. Once the {@code -XX:AOTCache}
   * flag has been persisted into the launcher {@code .cfg}, warm launches start with it already
   * active and this is a no-op — a single process, no re-execution. The flag is persisted only
   * <em>after</em> a re-exec has used the cache without an early crash (verify-before-persist), so
   * a fatal cache — one that aborts the JVM at init, where single-process mode has no recovery hook
   * — can never be baked into the {@code .cfg}. A cache that does crash early is discarded, its
   * flag stripped, and AOT is marked disabled for this build so startup neither loops nor bricks.
   * Returns true only when the parent must exit (it re-executed and waited for the child).
   */
  public static boolean relaunch(String[] args) {
    if (!isEligible()) {
      return false;
    }
    // Warm launch: the JVM already started with the persisted (and previously verified) flag, so
    // this process is the real, single application process — nothing to re-execute.
    if (hasAotCacheFlag()) {
      return false;
    }
    Optional<String> command = nativeCommand();
    if (command.isEmpty()) {
      LOGGER.warn("Not started by the native launcher, skipping AOT relaunch");
      return false;
    }
    try {
      Path cache = cacheFile();
      if (!hasSafePath(cache)) {
        LOGGER.warn(
            "AOT cache path contains unsupported characters, skipping AOT relaunch: {}", cache);
        return false;
      }
      Path disabled = disabledMarker(cache);
      if (Files.exists(disabled)) {
        // A previous launch found this build's cache fatal; run without AOT until an app/runtime
        // update changes the stamp (and so this marker's name).
        return false;
      }
      Files.createDirectories(cache.getParent());
      pruneOtherCaches(cache.getParent(), cache);
      closeSplashScreen();

      boolean useMode = Files.exists(cache);
      long start = System.currentTimeMillis();
      int exitCode = spawnStage2(command.get(), args, buildToolOptions(cache));

      if (useMode && exitCode != 0 && System.currentTimeMillis() - start < 10_000) {
        // The child died right away: the cache is unusable. Discard it and any persisted flag,
        // disable AOT for this build, and run once without AOT so startup can never be blocked.
        LOGGER.warn("Child failed early with AOT cache {}; disabling AOT for this build", cache);
        FileUtil.delete(cache);
        clearAotFlag();
        markAotDisabled(disabled);
        exitCode = spawnStage2(command.get(), args, "-D%s=2".formatted(STAGE_PROP));
      } else if (useMode) {
        // The cache was used without an early crash → verified safe; persist the flag so the next
        // launch is single-process (no-op on a read-only install, which keeps re-executing).
        persistAotFlag(cache);
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

  /** True when this JVM was started with {@code -XX:AOTCache=} (not {@code -XX:AOTCacheOutput}). */
  static boolean hasAotCacheFlag() {
    return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
        .anyMatch(arg -> arg.startsWith("-XX:AOTCache="));
  }

  /** Locates the jpackage launcher configuration file ({@code <app>/lib/app/<launcher>.cfg}). */
  static Optional<Path> configFile() {
    Optional<String> command = ProcessHandle.current().info().command();
    if (command.isEmpty()) {
      return Optional.empty();
    }
    Path launcher = Path.of(command.get());
    Path appRoot = launcher.getParent() == null ? null : launcher.getParent().getParent();
    if (appRoot == null) {
      return Optional.empty();
    }
    String name = launcher.getFileName().toString();
    if (name.toLowerCase().endsWith(".exe")) { // NON-NLS
      name = name.substring(0, name.length() - 4);
    }
    return Optional.of(appRoot.resolve("lib").resolve("app").resolve(name + ".cfg")); // NON-NLS
  }

  /**
   * Appends {@code java-options=-XX:AOTCache=<cache>} to the launcher {@code .cfg} (replacing any
   * stale entry) so the next launch starts with the cache and skips the re-execution. Returns false
   * when the file is absent or not writable (a read-only system install), keeping the re-exec path.
   */
  static boolean persistAotFlag(Path cache) {
    Optional<Path> configFile = configFile();
    if (configFile.isEmpty() || !Files.isWritable(configFile.get())) {
      return false;
    }
    return writeAotOption(configFile.get(), cache);
  }

  /** Rewrites {@code file} so it carries exactly one {@code -XX:AOTCache=<cache>} java-option. */
  static boolean writeAotOption(Path file, Path cache) {
    try {
      String option = "java-options=-XX:AOTCache=" + cache; // NON-NLS
      List<String> lines = new ArrayList<>(Files.readAllLines(file));
      if (lines.contains(option)) {
        return true;
      }
      lines.removeIf(line -> line.startsWith("java-options=-XX:AOTCache=")); // NON-NLS
      lines.add(option);
      Files.write(file, lines);
      LOGGER.info("Persisted AOT cache flag to {}", file);
      return true;
    } catch (Exception e) {
      LOGGER.warn("Cannot persist the AOT cache flag to {}", file, e);
      return false;
    }
  }

  /** Removes any persisted {@code -XX:AOTCache} option from the launcher {@code .cfg}. */
  static void clearAotFlag() {
    Optional<Path> configFile = configFile();
    if (configFile.isEmpty() || !Files.isWritable(configFile.get())) {
      return;
    }
    Path file = configFile.get();
    try {
      List<String> lines = new ArrayList<>(Files.readAllLines(file));
      if (lines.removeIf(line -> line.startsWith("java-options=-XX:AOTCache="))) { // NON-NLS
        Files.write(file, lines);
        LOGGER.info("Removed the AOT cache flag from {}", file);
      }
    } catch (Exception e) {
      LOGGER.warn("Cannot clear the AOT cache flag from {}", file, e);
    }
  }

  /**
   * Marker written next to a fatal cache to skip AOT for this build (same version+runtime stamp).
   */
  static Path disabledMarker(Path cache) {
    String name = cache.getFileName().toString();
    String base = name.endsWith(".aot") ? name.substring(0, name.length() - 4) : name; // NON-NLS
    return cache.resolveSibling(base + ".disabled"); // NON-NLS
  }

  static void markAotDisabled(Path marker) {
    try {
      Files.writeString(marker, ""); // NON-NLS
    } catch (Exception e) {
      LOGGER.warn("Cannot write the AOT disabled marker {}", marker, e);
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

  /**
   * The native launcher executable of the current process (a jpackage runtime); empty when the
   * process was started by a plain {@code java} command.
   */
  static Optional<String> nativeCommand() {
    return ProcessHandle.current()
        .info()
        .command()
        .filter(
            command -> {
              String name = Path.of(command).getFileName().toString();
              return !name.equals("java") && !name.equals("java.exe"); // NON-NLS
            });
  }

  /**
   * Quoted tokens and {@code ${...}} sequences in {@code JAVA_TOOL_OPTIONS} break the jpackage
   * native launcher, so a cache path is passed unquoted and must be free of such characters.
   */
  static boolean hasSafePath(Path cache) {
    return cache
        .toString()
        .chars()
        .noneMatch(c -> Character.isWhitespace(c) || c == '$' || c == '"');
  }

  /**
   * Cache file stamped with the application version and the runtime-image timestamp, so an
   * application or runtime update always trains a fresh cache.
   */
  static Path cacheFile() {
    return cacheFile("weasis", ""); // NON-NLS
  }

  /**
   * Cache file of a launcher: {@code <cache>/aot/<subdir>/<prefix>-<version>-<runtime>.aot}, so
   * each launcher trains and prunes its own caches.
   */
  static Path cacheFile(String prefix, String subdir) {
    String version = System.getProperty("jpackage.app-version", "unknown"); // NON-NLS
    long runtimeMtime = 0;
    try {
      runtimeMtime = Files.getLastModifiedTime(Path.of(System.getProperty("java.home"))).toMillis();
    } catch (Exception e) {
      LOGGER.warn("Cannot read the runtime image timestamp", e);
    }
    String name =
        "%s-%s-%s.aot".formatted(prefix, version, Long.toHexString(runtimeMtime)); // NON-NLS
    Path dir = PlatformDirectories.get(PlatformDirectories.BaseDir.CACHE).resolve("aot"); // NON-NLS
    return (subdir.isEmpty() ? dir : dir.resolve(subdir)).resolve(name);
  }

  static void pruneOtherCaches(Path aotDir, Path keep) {
    try (Stream<Path> stream = Files.list(aotDir)) {
      stream
          .filter(
              p -> {
                String name = p.getFileName().toString();
                return (name.endsWith(".aot") || name.endsWith(".disabled")) && !p.equals(keep);
              })
          .forEach(FileUtil::delete);
    } catch (Exception e) {
      LOGGER.warn("Cannot prune old AOT caches in {}", aotDir, e);
    }
  }

  /** Spawns the native launcher with the given JAVA_TOOL_OPTIONS and waits for it. */
  private static int spawnStage2(String command, String[] args, String toolOptions)
      throws Exception {
    ProcessBuilder pb = childLauncher(command, List.of(args), toolOptions);
    pb.inheritIO();
    return pb.start().waitFor();
  }

  /** A child of the native launcher {@code command} that gets {@code toolOptions} appended. */
  static ProcessBuilder childLauncher(String command, List<String> args, String toolOptions) {
    List<String> cmd = new ArrayList<>();
    cmd.add(command);
    cmd.addAll(args);
    ProcessBuilder pb = new ProcessBuilder(cmd);
    // The Linux jpackage launcher exports _JPACKAGE_LAUNCHER to detect a self-execve; a child
    // launcher inheriting it skips its .cfg ("Not building JVM arguments from cfg file") and
    // dies on the java usage screen, so it must not leak into the re-executed process.
    pb.environment().remove("_JPACKAGE_LAUNCHER");
    pb.environment().merge("JAVA_TOOL_OPTIONS", toolOptions, (old, opts) -> old + " " + opts);
    return pb;
  }

  /**
   * Builds the {@code JAVA_TOOL_OPTIONS} value for the child: use the cache when it exists,
   * otherwise train it this launch ({@code -XX:AOTCache} and {@code -XX:AOTCacheOutput} must never
   * be combined — the JVM refuses to start). Training disables {@code AOTClassLinking} on JDK ≤ 26,
   * where a cache with AOT-linked classes crashes at use on the jpackage runtime (InternalError in
   * AppClassLoader.&lt;clinit&gt;, JDK-8373392); from JDK 27 the fix landed so the faster linked
   * tier is used.
   */
  static String buildToolOptions(Path cache) {
    // t0 is the parent JVM start (not the spawn time), so the child's relaunch-overhead metric
    // captures the whole stage-1 cost — including the class loading the Bootstrap split removes.
    long t0 = ManagementFactory.getRuntimeMXBean().getStartTime();
    if (Files.exists(cache)) {
      return "-D%s=2 -D%s=%d -XX:AOTCache=%s".formatted(STAGE_PROP, PARENT_START_PROP, t0, cache);
    }
    return "-D%s=2 -D%s=%d %s".formatted(STAGE_PROP, PARENT_START_PROP, t0, trainingOptions(cache));
  }

  /** The {@code JAVA_TOOL_OPTIONS} that train {@code cache}, with the class-linking tier gate. */
  static String trainingOptions(Path cache) {
    boolean linked =
        useClassLinking(Runtime.version().feature(), System.getProperty(CLASSLINKING_PROP));
    String linking = linked ? "" : " -XX:-AOTClassLinking";
    return "-XX:AOTCacheOutput=%s%s".formatted(cache, linking);
  }

  /**
   * Whether to train with {@code AOTClassLinking} (the faster pre-linked + JEP 515 profile tier).
   * Enabled from JDK 27, where JDK-8373392 fixed the use-time crash seen on JDK ≤ 26; an explicit
   * {@link #CLASSLINKING_PROP} value overrides the version gate (for testing / a known-good build).
   */
  static boolean useClassLinking(int runtimeFeature, String override) {
    if (override != null) {
      return Boolean.parseBoolean(override);
    }
    return runtimeFeature >= 27;
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
