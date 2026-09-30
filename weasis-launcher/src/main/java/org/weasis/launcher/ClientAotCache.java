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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Per-user JDK AOT cache of the {@code weasis-cli} launcher. Unlike the application, the client
 * never re-executes itself: a call answers first, then trains the cache in a detached process; a
 * later call verifies the trained cache in a child and persists {@code -XX:AOTCache} into the
 * launcher {@code .cfg}, so every following call starts with it. Read-only installs, macOS and
 * plain {@code java} launches are left alone.
 */
final class ClientAotCache {

  static final String PREFIX = "weasis-cli"; // NON-NLS
  static final String SUBDIR = "cli"; // NON-NLS

  /** Hidden client options run by the training and verification children. */
  static final String TRAIN_OPTION = "--aot-train"; // NON-NLS

  static final String VERIFY_OPTION = "--aot-verify"; // NON-NLS
  private static final Duration TRAINING_GRACE = Duration.ofMinutes(5);
  private static final long VERIFY_TIMEOUT_SECONDS = 30;

  private ClientAotCache() {}

  /** Called after a normal call answered; never throws and never delays the answer. */
  static void afterRun() {
    try {
      if (!AotCache.isEligible() || AotCache.hasAotCacheFlag()) {
        return;
      }
      Optional<String> command = AotCache.nativeCommand();
      if (command.isEmpty()) {
        return;
      }
      Optional<Path> config = AotCache.configFile();
      if (config.isEmpty() || !Files.isWritable(config.get())) {
        return;
      }
      Path cache = AotCache.cacheFile(PREFIX, SUBDIR);
      Path disabled = AotCache.disabledMarker(cache);
      if (Files.exists(disabled) || !AotCache.hasSafePath(cache)) {
        return;
      }
      Files.createDirectories(cache.getParent());
      AotCache.pruneOtherCaches(cache.getParent(), cache);
      if (Files.exists(cache)) {
        verifyAndPersist(command.get(), cache, config.get(), disabled);
      } else {
        train(command.get(), cache);
      }
    } catch (Exception e) {
      // the cache is an optimization: a failure must never surface to the caller
    }
  }

  /** Starts a detached training run, at most once per grace period. */
  private static void train(String command, Path cache) throws IOException {
    Path marker = cache.resolveSibling(cache.getFileName() + ".training"); // NON-NLS
    if (Files.exists(marker)
        && Files.getLastModifiedTime(marker)
            .toInstant()
            .plus(TRAINING_GRACE)
            .isAfter(Instant.now())) {
      return;
    }
    Files.writeString(marker, Instant.now().toString());
    spawn(command, TRAIN_OPTION, AotCache.trainingOptions(cache)).start();
  }

  /** Runs a child with the cache; persists the flag on success, disables the cache otherwise. */
  private static void verifyAndPersist(String command, Path cache, Path config, Path disabled)
      throws Exception {
    Process child = spawn(command, VERIFY_OPTION, "-XX:AOTCache=" + cache).start(); // NON-NLS
    boolean finished = child.waitFor(VERIFY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    if (finished && child.exitValue() == 0) {
      AotCache.writeAotOption(config, cache);
    } else {
      child.destroyForcibly();
      FileUtil.delete(cache);
      AotCache.markAotDisabled(disabled);
    }
  }

  private static ProcessBuilder spawn(String command, String option, String toolOptions) {
    ProcessBuilder pb = AotCache.childLauncher(command, List.of(option), toolOptions);
    pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
    pb.redirectError(ProcessBuilder.Redirect.DISCARD);
    return pb;
  }

  /** A representative workload for the training run: parsing and help rendering, no server. */
  static void trainingWorkload() {
    CommandClient.run(
        new String[] {"--help"}, // NON-NLS
        List::of,
        new java.io.PrintStream(java.io.OutputStream.nullOutputStream()),
        new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
    SingletonFiles.list();
  }
}
