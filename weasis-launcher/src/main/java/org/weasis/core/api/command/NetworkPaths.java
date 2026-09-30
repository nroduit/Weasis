/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.command;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Whether a path lives on a share rather than on this machine, as the file system type reported by
 * the operating system says. Used by the restriction policies, which refuse to write a remote
 * caller's files to a share. The application has a richer detector built on the mount table ({@code
 * NetworkFileSystems} of the core); this one stays in the launcher, where nothing of the
 * application is loaded yet, and is deliberately conservative: an unreadable path counts as local
 * and is caught by the root check instead.
 */
public final class NetworkPaths {

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
          "sshfs");

  private NetworkPaths() {}

  public static boolean isNetwork(Path path) {
    if (path == null) {
      return false;
    }
    Path absolute = path.toAbsolutePath();
    // A Windows UNC path names a share whatever the file system reports
    if (absolute.toString().startsWith("\\\\")) {
      return true;
    }
    Path existing = deepestExisting(absolute);
    if (existing == null) {
      return false;
    }
    try {
      FileStore store = Files.getFileStore(existing);
      String type = store.type().toLowerCase(Locale.ROOT);
      return NETWORK_TYPES.contains(type) || type.startsWith("fuse.") && isFuseNetwork(type);
    } catch (IOException | SecurityException e) {
      return false;
    }
  }

  private static boolean isFuseNetwork(String type) {
    String name = type.substring("fuse.".length()); // NON-NLS
    return NETWORK_TYPES.contains(name)
        || name.startsWith("s3") // NON-NLS
        || name.startsWith("rclone") // NON-NLS
        || name.startsWith("gvfs"); // NON-NLS
  }

  /** The closest ancestor that exists, so a file still to be written can be located. */
  static Path deepestExisting(Path path) {
    for (Path p = path; p != null; p = p.getParent()) {
      if (Files.exists(p)) {
        return p;
      }
    }
    return null;
  }
}
