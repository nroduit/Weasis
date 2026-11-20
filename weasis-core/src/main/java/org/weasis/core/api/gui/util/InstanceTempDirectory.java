/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.FileUtil;

/**
 * The temporary directory of one running instance.
 *
 * <p>Instances started from the same source share a base name ({@code weasis-<user>.<source>}).
 * Each one works in its own {@code <base>-<pid>} directory and holds a lock on a {@value
 * #LOCK_FILE} file inside it for its whole life. At startup, the sibling directories whose lock is
 * free belong to an instance that has exited or crashed and are deleted; a directory whose lock is
 * held is left alone, so starting a second instance never deletes the images another one still has
 * in its DICOM model.
 */
final class InstanceTempDirectory {
  private static final Logger LOGGER = LoggerFactory.getLogger(InstanceTempDirectory.class);

  static final String LOCK_FILE = ".lock"; // NON-NLS

  /**
   * A directory without a lock file younger than this may be one another instance has just created
   * and not locked yet.
   */
  static final Duration UNLOCKED_GRACE = Duration.ofMinutes(1);

  private static final int MAX_ATTEMPTS = 100;

  // Strong references: the lock is released when its channel is closed or collected
  private static final List<FileChannel> HELD_CHANNELS = new ArrayList<>(1);

  private InstanceTempDirectory() {}

  /**
   * Deletes the directories of the instances that are gone, then creates and locks the directory of
   * this instance.
   *
   * @param parent the directory holding the instance directories (the system temp directory)
   * @param baseName the name shared by the instances of the same user and source
   * @return the locked directory of this instance, or {@code parent/baseName} when no directory can
   *     be locked (a file system without locks): that shared directory is emptied as before, with
   *     the risk of deleting the files of another running instance
   */
  static Path create(Path parent, String baseName) {
    deleteStale(parent, baseName);
    long pid = ProcessHandle.current().pid();
    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      Path dir = parent.resolve(baseName + "-" + pid + (i == 0 ? "" : "-" + i));
      if (lock(dir)) {
        return dir;
      }
    }
    Path fallback = parent.resolve(baseName);
    LOGGER.warn("Cannot lock a temporary directory, using the shared one: {}", fallback);
    FileUtil.deleteDirectoryContents(fallback, 3, 0);
    return fallback;
  }

  /** Creates {@code dir} and locks it; a directory left by an exited process is emptied first. */
  private static boolean lock(Path dir) {
    try {
      Files.createDirectories(dir);
      FileChannel channel =
          FileChannel.open(
              dir.resolve(LOCK_FILE), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
      FileLock lock = tryLock(channel);
      if (lock == null) {
        channel.close();
        return false;
      }
      synchronized (HELD_CHANNELS) {
        HELD_CHANNELS.add(channel);
      }
      // The same pid after a reboot or a crash: what is there belongs to nobody
      try (Stream<Path> entries = Files.list(dir)) {
        entries
            .filter(p -> !LOCK_FILE.equals(p.getFileName().toString()))
            .forEach(FileUtil::delete);
      }
      return true;
    } catch (IOException | RuntimeException e) {
      LOGGER.debug("Cannot lock the temporary directory {}", dir, e);
      return false;
    }
  }

  private static FileLock tryLock(FileChannel channel) throws IOException {
    try {
      return channel.tryLock();
    } catch (OverlappingFileLockException e) {
      return null; // Held by this JVM
    }
  }

  /** Deletes the sibling directories of the instances that are no longer running. */
  static void deleteStale(Path parent, String baseName) {
    Pattern instanceName = Pattern.compile(Pattern.quote(baseName) + "-\\d+(-\\d+)?");
    try (Stream<Path> entries = Files.list(parent)) {
      entries
          .filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS))
          .forEach(
              p -> {
                String name = p.getFileName().toString();
                if (instanceName.matcher(name).matches() && isStale(p)) {
                  LOGGER.info("Delete the temporary directory of an exited instance: {}", p);
                  FileUtil.recursiveDelete(p);
                }
              });
    } catch (IOException e) {
      LOGGER.debug("Cannot list {}", parent, e);
    }
  }

  /** True when no running process holds the lock of {@code dir}. */
  static boolean isStale(Path dir) {
    Path lockFile = dir.resolve(LOCK_FILE);
    if (!Files.exists(lockFile, LinkOption.NOFOLLOW_LINKS)) {
      try {
        Instant modified = Files.getLastModifiedTime(dir).toInstant();
        return modified.isBefore(Instant.now().minus(UNLOCKED_GRACE));
      } catch (IOException e) {
        return false;
      }
    }
    try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE)) {
      FileLock lock = tryLock(channel);
      return lock != null; // Released by closing the channel
    } catch (IOException e) {
      // On Windows the lock file of a running instance cannot be opened for writing
      return false;
    }
  }
}
