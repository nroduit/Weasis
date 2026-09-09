/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import com.formdev.flatlaf.util.SystemInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.HardwareInfo.Volume;

/**
 * Tells whether a path lives on storage where every file operation is a network round trip (SMB/NFS
 * mounts, Windows UNC paths and mapped drives) or a slow seek (optical media). The answer comes
 * from the mount table the operating system reports through OSHI: the drive type on Windows, the
 * {@code MNT_LOCAL} flag on macOS, the file system type on Linux. A mount OSHI does not list falls
 * back to the file system type seen by {@link Files#getFileStore}. Callers switch to sequential
 * bulk transfers on a positive answer, so a wrong one only costs a local copy.
 */
public final class NetworkFileSystems {
  private static final Logger LOGGER = LoggerFactory.getLogger(NetworkFileSystems.class);

  /** File system types OSHI does not flag as network on every platform. */
  private static final Set<String> NETWORK_TYPES =
      Set.of(
          "cifs",
          "smb",
          "smb2",
          "smb3",
          "smbfs",
          "nfs",
          "nfs3",
          "nfs4",
          "afpfs",
          "afs",
          "ncpfs",
          "coda",
          "9p",
          "davfs",
          "webdav",
          "ceph",
          "glusterfs",
          "lustre",
          "gpfs",
          "sshfs",
          "fuse.sshfs",
          "fuse.gvfsd-fuse",
          "fuse.rclone",
          "fuse.s3fs",
          "fuse.davfs2",
          "fuse.cifs",
          "fuse.smb");

  enum Kind {
    LOCAL,
    NETWORK,
    OPTICAL
  }

  private static final Map<Path, Kind> CACHE = new ConcurrentHashMap<>();
  private static final int MAX_CACHE_SIZE = 512;
  private static final long MOUNT_TABLE_TTL_MS = 30_000;
  private static final long MOUNT_TABLE_MIN_REFRESH_MS = 5_000;

  private static final AtomicReference<MountTable> MOUNT_TABLE = new AtomicReference<>();

  private NetworkFileSystems() {}

  /** Returns true when the path lives on a network share, a UNC path or a mapped network drive. */
  public static boolean isNetworkPath(Path path) {
    return kindOf(path) == Kind.NETWORK;
  }

  /** Returns true when random access to the path is expensive: network share or optical media. */
  public static boolean isSlowRandomAccess(Path path) {
    return kindOf(path) != Kind.LOCAL;
  }

  private static Kind kindOf(Path path) {
    if (path == null) {
      return Kind.LOCAL;
    }
    Path key = cacheKey(path);
    Kind kind = CACHE.get(key);
    if (kind == null) {
      kind = detect(key);
      if (CACHE.size() >= MAX_CACHE_SIZE) {
        CACHE.clear();
      }
      CACHE.put(key, kind);
    }
    return kind;
  }

  /** Windows roots identify the volume; elsewhere the mount point is unknown, so key by parent. */
  private static Path cacheKey(Path path) {
    Path absolute = path.toAbsolutePath();
    if (SystemInfo.isWindows) {
      Path root = absolute.getRoot();
      return root == null ? absolute : root;
    }
    Path parent = absolute.getParent();
    return parent == null ? absolute : parent;
  }

  private static Kind detect(Path path) {
    if (SystemInfo.isWindows && isUncPath(path)) {
      return Kind.NETWORK;
    }
    Volume volume = mountTable().volumeOf(path);
    if (volume == null) {
      // A drive mapped after the table was read: refresh once rather than guess
      volume = refreshMountTable().volumeOf(path);
    }
    if (volume != null) {
      Kind kind = classify(volume.type(), volume.description(), volume.local());
      if (kind != Kind.LOCAL) {
        return kind;
      }
    }
    // A mount OSHI hides (its Linux scan excludes /run, for instance) is still seen by NIO
    try {
      return classify(Files.getFileStore(existingAncestor(path)).type(), null, true);
    } catch (IOException | RuntimeException e) {
      LOGGER.debug("Cannot get file store of {}", path, e);
      return Kind.LOCAL;
    }
  }

  /**
   * Classifies a mount from what the operating system says about it: the Windows drive type in
   * {@code description}, the local flag, and the file system type.
   */
  static Kind classify(String type, String description, boolean local) {
    // Optical first: OSHI does not flag a Windows CD-ROM drive as local
    if (HardwareInfo.isOptical(type, description)) {
      return Kind.OPTICAL;
    }
    String kind = description == null ? "" : description.toLowerCase(Locale.ROOT);
    String fsType = type == null ? "" : type.toLowerCase(Locale.ROOT);
    if (!local || kind.contains("network") || NETWORK_TYPES.contains(fsType)) { // NON-NLS
      return Kind.NETWORK;
    }
    return Kind.LOCAL;
  }

  private static MountTable mountTable() {
    MountTable table = MOUNT_TABLE.get();
    return table == null || table.isOlderThan(MOUNT_TABLE_TTL_MS) ? refreshMountTable() : table;
  }

  /** Re-reads the mount table, at most once every few seconds: it is a WMI query on Windows. */
  private static synchronized MountTable refreshMountTable() {
    MountTable table = MOUNT_TABLE.get();
    if (table == null || table.isOlderThan(MOUNT_TABLE_MIN_REFRESH_MS)) {
      table = new MountTable(HardwareInfo.mountedVolumes());
      MOUNT_TABLE.set(table);
    }
    return table;
  }

  /** The mount table at one point in time; a share mounted later shows up after the TTL. */
  static final class MountTable {
    private final List<Volume> volumes;
    private final long createdAt = System.currentTimeMillis();

    MountTable(List<Volume> volumes) {
      this.volumes = List.copyOf(volumes);
    }

    boolean isOlderThan(long millis) {
      return System.currentTimeMillis() - createdAt > millis;
    }

    /** The volume whose mount point is the longest prefix of the path, or null. */
    Volume volumeOf(Path path) {
      Path absolute = path.toAbsolutePath().normalize();
      Volume best = null;
      int bestDepth = -1;
      for (Volume volume : volumes) {
        Path mount = toPath(volume.mount());
        if (mount != null && absolute.startsWith(mount) && mount.getNameCount() > bestDepth) {
          best = volume;
          bestDepth = mount.getNameCount();
        }
      }
      return best;
    }

    private static Path toPath(String mount) {
      try {
        return Path.of(mount).toAbsolutePath().normalize();
      } catch (InvalidPathException e) {
        return null;
      }
    }
  }

  private static Path existingAncestor(Path path) {
    Path current = path;
    while (current != null && !Files.exists(current)) {
      current = current.getParent();
    }
    return current == null ? path : current;
  }

  private static boolean isUncPath(Path path) {
    String value = path.toString();
    return value.startsWith("\\\\") || value.startsWith("//");
  }
}
