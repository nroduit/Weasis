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
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.pref.PlatformDirectories;

/**
 * The per-user files that advertise a running instance: {@code <id>_<port>} in the state directory,
 * holding the secret a client must present on the singleton socket.
 */
public final class SingletonFiles {
  private static final Logger LOGGER = LoggerFactory.getLogger(SingletonFiles.class);
  private static final String POSIX = "posix"; // NON-NLS
  private static final Set<PosixFilePermission> OWNER_ONLY =
      Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

  /** A running instance as advertised by its singleton file. */
  public record Instance(String id, int port, String secret, Path file) {}

  private SingletonFiles() {}

  public static Path directory() {
    return PlatformDirectories.get(PlatformDirectories.BaseDir.STATE)
        .resolve("singleton"); // NON-NLS
  }

  public static List<Instance> list() {
    return list(directory());
  }

  public static List<Instance> list(Path dir) {
    if (dir == null || !Files.isDirectory(dir)) {
      return List.of();
    }
    List<Instance> instances = new ArrayList<>();
    try (Stream<Path> files = Files.list(dir)) {
      files.filter(Files::isRegularFile).sorted().forEach(f -> read(f).ifPresent(instances::add));
    } catch (IOException e) {
      LOGGER.warn("Cannot list the singleton directory {}", dir, e);
    }
    return List.copyOf(instances);
  }

  /** The instance whose id starts with {@code idPrefix}, if any. */
  public static Optional<Instance> find(Path dir, String idPrefix) {
    return list(dir).stream().filter(i -> i.id().startsWith(idPrefix)).findFirst();
  }

  public static Path file(Path dir, String id, int port) {
    return dir.resolve(id + "_" + port);
  }

  /** Writes the file for {@code id} (owner-only on POSIX), removing stale files of the same id. */
  public static Path create(Path dir, String id, int port, String secret) throws IOException {
    Files.createDirectories(dir);
    try (Stream<Path> files = Files.list(dir)) {
      files
          .filter(f -> f.getFileName().toString().startsWith(id))
          .forEach(
              f -> {
                LOGGER.info("Remove stale singleton file {}", f);
                FileUtil.delete(f);
              });
    }
    Path file = file(dir, id, port);
    if (isPosix()) {
      Files.createFile(file, PosixFilePermissions.asFileAttribute(OWNER_ONLY));
    } else {
      Files.createFile(file);
    }
    Files.writeString(file, secret, StandardCharsets.UTF_8);
    return file;
  }

  /** True when no one but the owner can read the file (always true on non-POSIX systems). */
  public static boolean isPrivate(Path file) {
    if (!isPosix()) {
      return true;
    }
    try {
      return OWNER_ONLY.containsAll(Files.getPosixFilePermissions(file));
    } catch (IOException e) {
      return false;
    }
  }

  private static Optional<Instance> read(Path file) {
    String name = file.getFileName().toString();
    int sep = name.lastIndexOf('_');
    if (sep <= 0) {
      return Optional.empty();
    }
    try {
      int port = Integer.parseInt(name.substring(sep + 1));
      String secret = Files.readString(file, StandardCharsets.UTF_8).strip();
      if (secret.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(new Instance(name.substring(0, sep), port, secret, file));
    } catch (NumberFormatException | IOException e) {
      LOGGER.debug("Ignoring singleton file {}", file, e);
      return Optional.empty();
    }
  }

  private static boolean isPosix() {
    return FileSystems.getDefault().supportedFileAttributeViews().contains(POSIX);
  }
}
