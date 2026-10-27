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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.cert.Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongConsumer;
import java.util.stream.Stream;

/**
 * The launcher sets stage 2 can run: the embedded one of {@code $APPDIR/launcher/} (the installer
 * as shipped, never written) and the cached ones of {@code <cache>/launcher/<id>/}, downloaded from
 * the package, verified, and made visible by one atomic rename. A set is complete or it does not
 * exist.
 */
final class LauncherSets {

  /** A launcher set: the launcher jar first, then the libraries. */
  record LauncherSet(String id, Path dir, List<Path> jars, boolean embedded, String sha) {

    String kind() {
      return embedded ? "embedded" : "hub"; // NON-NLS
    }

    String classPath() {
      return String.join(java.io.File.pathSeparator, jars.stream().map(Path::toString).toList());
    }
  }

  /** Why a hub set is not used. */
  record Refusal(String reason) {}

  static final String LAUNCHER_JAR = "weasis-launcher.jar";
  static final String FAILED_MARKER = ".failed";
  static final String VERIFIED_MARKER = ".verified";
  static final String CURRENT_MARKER = "current";
  private static final String PART_INFIX = ".part-";
  private static final int KEPT_SETS = 2;
  private static final Duration REVERIFY_AFTER = Duration.ofDays(1);

  private final Path cacheDir;

  LauncherSets(Path cacheRoot) {
    this.cacheDir = cacheRoot.resolve("launcher"); // NON-NLS
  }

  Path cacheDir() {
    return cacheDir;
  }

  /** The embedded set, whose jars carry {@link Contract#EMBEDDED_SUFFIX}. */
  static Optional<LauncherSet> embedded(Path appDir) {
    Path dir = appDir.resolve("launcher"); // NON-NLS
    if (!Files.isDirectory(dir)) {
      return Optional.empty();
    }
    try (Stream<Path> files = Files.list(dir)) {
      List<Path> jars =
          files
              .filter(
                  p ->
                      p.getFileName()
                          .toString()
                          .endsWith(".jar" + Contract.EMBEDDED_SUFFIX)) // NON-NLS
              .sorted(LauncherSets::launcherFirst)
              .toList();
      if (jars.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(new LauncherSet("embedded", dir, jars, true, "")); // NON-NLS
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  private static int launcherFirst(Path a, Path b) {
    boolean la = a.getFileName().toString().startsWith("weasis-launcher"); // NON-NLS
    boolean lb = b.getFileName().toString().startsWith("weasis-launcher"); // NON-NLS
    if (la != lb) {
      return la ? -1 : 1;
    }
    return a.getFileName().compareTo(b.getFileName());
  }

  /** The set the application ran last, for the client launcher that fetches nothing. */
  Optional<LauncherSet> current() {
    Path marker = cacheDir.resolve(CURRENT_MARKER);
    try {
      if (Files.isRegularFile(marker)) {
        String id = Files.readString(marker, StandardCharsets.UTF_8).strip();
        Path dir = cacheDir.resolve(id);
        if (!id.isEmpty() && Files.isDirectory(dir) && !Files.exists(dir.resolve(FAILED_MARKER))) {
          return Optional.of(load(id, dir));
        }
      }
    } catch (IOException e) {
      BootLog.note("cannot read the current set: " + e); // NON-NLS
    }
    return Optional.empty();
  }

  void markCurrent(LauncherSet set) {
    if (set.embedded()) {
      return;
    }
    try {
      Files.createDirectories(cacheDir);
      Files.writeString(cacheDir.resolve(CURRENT_MARKER), set.id(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      BootLog.note("cannot write the current set: " + e); // NON-NLS
    }
  }

  /** The digest of the set whose last start failed before READY, for the hub to see. */
  Optional<String> failedSha() {
    if (!Files.isDirectory(cacheDir)) {
      return Optional.empty();
    }
    try (Stream<Path> dirs = Files.list(cacheDir)) {
      return dirs.filter(d -> Files.exists(d.resolve(FAILED_MARKER)))
          .max(Comparator.comparing(LauncherSets::lastModified))
          .map(d -> shaOf(d.getFileName().toString()));
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  private static String shaOf(String id) {
    int dash = id.lastIndexOf('-');
    return dash < 0 ? id : id.substring(dash + 1);
  }

  /**
   * Records that stage 2 died before READY with this set; it is skipped until the hub names
   * another.
   */
  void markFailed(LauncherSet set, String reason) {
    if (set.embedded()) {
      return;
    }
    Path marker = set.dir().resolve(FAILED_MARKER);
    try {
      int count = 1;
      if (Files.isRegularFile(marker)) {
        String first = Files.readString(marker, StandardCharsets.UTF_8).split("\\R")[0].strip();
        count = Integer.parseInt(first) + 1;
      }
      Files.writeString(
          marker, count + "\n" + Instant.now() + " " + reason + "\n", StandardCharsets.UTF_8);
    } catch (IOException | RuntimeException e) {
      BootLog.note("cannot mark the set failed: " + e); // NON-NLS
    }
  }

  /**
   * The cached set named by the package, downloaded and verified when absent. The manifest is
   * fetched first: its digest identifies the set. {@code progress} receives the bytes received.
   */
  Object resolve(
      PackageConfig config,
      Http http,
      Integrity.Level level,
      List<Certificate> trusted,
      LongConsumer progress) {
    URI manifestUrl = config.manifestUrl().orElse(null);
    if (manifestUrl == null) {
      return new Refusal("no launcher set declared"); // NON-NLS
    }
    byte[] manifest;
    try {
      manifest = http.fetch(manifestUrl);
    } catch (IOException e) {
      return new Refusal("manifest unreachable: " + e.getMessage()); // NON-NLS
    }
    Map<String, String> digests;
    try {
      digests = Integrity.parseManifest(manifest);
    } catch (RuntimeException e) {
      return new Refusal("manifest malformed: " + e.getMessage()); // NON-NLS
    }
    String sha = Integrity.sha256(manifest);
    String id = safeName(config.version()) + "-" + sha.substring(0, 12);
    Path dir = cacheDir.resolve(id);
    if (Files.exists(dir.resolve(FAILED_MARKER))) {
      return new Refusal("set " + id + " failed before READY at a previous launch"); // NON-NLS
    }
    try {
      cleanParts();
      if (Files.isDirectory(dir)) {
        LauncherSet cached = load(id, dir);
        String problem = recheck(cached, digests);
        if (problem.isEmpty()) {
          prune(dir);
          return cached;
        }
        BootLog.note("cached set " + id + " discarded: " + problem); // NON-NLS
        deleteTree(dir);
      }
      List<URI> urls = config.launcherSetUrls();
      if (urls.isEmpty()) {
        return new Refusal("no launcher set declared"); // NON-NLS
      }
      return download(urls, digests, id, dir, sha, http, level, trusted, progress);
    } catch (IOException e) {
      return new Refusal("cache unusable: " + e.getMessage()); // NON-NLS
    }
  }

  private Object download(
      List<URI> urls,
      Map<String, String> digests,
      String id,
      Path dir,
      String sha,
      Http http,
      Integrity.Level level,
      List<Certificate> trusted,
      LongConsumer progress)
      throws IOException {
    Path part = cacheDir.resolve(id + PART_INFIX + ProcessHandle.current().pid());
    Files.createDirectories(part);
    try {
      long[] received = new long[1];
      for (URI url : urls) {
        String name = fileName(url);
        Path target = part.resolve(name);
        if ("file".equalsIgnoreCase(url.getScheme())) { // NON-NLS
          Http.copy(Path.of(url), target);
        } else {
          long before = received[0];
          http.download(url, target, n -> progress.accept(before + n));
          received[0] = before + Files.size(target);
        }
        List<String> problems = Integrity.checkDigest(target, digests);
        if (!problems.isEmpty()) {
          return refuse(part, "download refused: " + problems.getFirst()); // NON-NLS
        }
        if (level == Integrity.Level.SIGNED) {
          String problem = Integrity.checkSignature(target, trusted);
          if (!problem.isEmpty()) {
            return refuse(part, "signature refused for " + name + ": " + problem); // NON-NLS
          }
        }
      }
      for (String expected : digests.keySet()) {
        if (!Files.exists(part.resolve(expected))) {
          return refuse(
              part, "manifest names " + expected + ", which the package does not serve"); // NON-NLS
        }
      }
      Files.writeString(
          part.resolve(VERIFIED_MARKER), Instant.now().toString(), StandardCharsets.UTF_8);
      try {
        Files.move(part, dir, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(part, dir);
      } catch (IOException e) {
        // A rename refused by a lock (Windows) keeps the part; the next launch retries.
        return new Refusal("cannot install the set: " + e.getMessage()); // NON-NLS
      }
      prune(dir);
      return load(id, dir);
    } catch (IOException e) {
      deleteTree(part);
      return new Refusal("download failed: " + e.getMessage()); // NON-NLS
    }
  }

  private Refusal refuse(Path part, String reason) throws IOException {
    deleteTree(part);
    return new Refusal(reason);
  }

  /** Sizes at every start; digests again once a day or when a file changed. */
  private String recheck(LauncherSet set, Map<String, String> digests) throws IOException {
    for (String name : digests.keySet()) {
      if (!Files.isRegularFile(set.dir().resolve(name))) {
        return name + " is missing"; // NON-NLS
      }
    }
    Path verified = set.dir().resolve(VERIFIED_MARKER);
    boolean stale =
        !Files.exists(verified)
            || Files.getLastModifiedTime(verified)
                .toInstant()
                .plus(REVERIFY_AFTER)
                .isBefore(Instant.now());
    for (Path jar : set.jars()) {
      if (!stale
          && Files.getLastModifiedTime(jar)
              .toInstant()
              .isAfter(Files.getLastModifiedTime(verified).toInstant())) {
        stale = true;
      }
    }
    if (stale) {
      for (Path jar : set.jars()) {
        List<String> problems = Integrity.checkDigest(jar, digests);
        if (!problems.isEmpty()) {
          return problems.getFirst();
        }
      }
      Files.writeString(verified, Instant.now().toString(), StandardCharsets.UTF_8);
    }
    return "";
  }

  private static LauncherSet load(String id, Path dir) throws IOException {
    try (Stream<Path> files = Files.list(dir)) {
      List<Path> jars =
          files
              .filter(p -> p.getFileName().toString().endsWith(".jar"))
              .sorted(LauncherSets::launcherFirst)
              .toList(); // NON-NLS
      return new LauncherSet(id, dir, jars, false, shaOf(id));
    }
  }

  private void cleanParts() throws IOException {
    if (!Files.isDirectory(cacheDir)) {
      return;
    }
    try (Stream<Path> dirs = Files.list(cacheDir)) {
      for (Path d : dirs.filter(d -> d.getFileName().toString().contains(PART_INFIX)).toList()) {
        deleteTree(d);
      }
    }
  }

  /**
   * Keeps the newest {@link #KEPT_SETS} sets besides {@code keep}; the rest is a download cache.
   */
  private void prune(Path keep) throws IOException {
    try (Stream<Path> dirs = Files.list(cacheDir)) {
      List<Path> others =
          dirs.filter(Files::isDirectory)
              .filter(d -> !d.equals(keep) && !d.getFileName().toString().contains(PART_INFIX))
              .sorted(Comparator.comparing(LauncherSets::lastModified).reversed())
              .toList();
      for (int i = KEPT_SETS; i < others.size(); i++) {
        deleteTree(others.get(i));
      }
    }
  }

  private static FileTime lastModified(Path p) {
    try {
      return Files.getLastModifiedTime(p);
    } catch (IOException e) {
      return FileTime.fromMillis(0);
    }
  }

  static void deleteTree(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(root)) {
      for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(p);
      }
    }
  }

  static String fileName(URI url) {
    String path = url.getPath();
    int slash = path.lastIndexOf('/');
    return slash < 0 ? path : path.substring(slash + 1);
  }

  private static String safeName(String version) {
    StringBuilder sb = new StringBuilder();
    for (char c : version.toCharArray()) {
      sb.append(Character.isLetterOrDigit(c) || c == '.' ? c : '_');
    }
    return sb.isEmpty() ? "set" : sb.toString(); // NON-NLS
  }

  static List<Path> jarsOf(List<Path> files) {
    return new ArrayList<>(files);
  }
}
