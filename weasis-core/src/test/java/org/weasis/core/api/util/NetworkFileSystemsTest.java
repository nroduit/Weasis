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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.formdev.flatlaf.util.SystemInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.util.HardwareInfo.Volume;
import org.weasis.core.api.util.NetworkFileSystems.Kind;
import org.weasis.core.api.util.NetworkFileSystems.MountTable;

@DisplayNameGeneration(ReplaceUnderscores.class)
class NetworkFileSystemsTest {

  @TempDir Path tempDir;

  @Test
  void a_null_path_is_local() {
    assertFalse(NetworkFileSystems.isNetworkPath(null));
    assertFalse(NetworkFileSystems.isSlowRandomAccess(null));
  }

  @Test
  void a_missing_file_is_classified_like_its_nearest_existing_ancestor() {
    Path missing = tempDir.resolve("missing").resolve("deeper").resolve("file.dcm");

    assertEquals(
        NetworkFileSystems.isNetworkPath(tempDir), NetworkFileSystems.isNetworkPath(missing));
    assertTrue(Files.notExists(missing));
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  void a_UNC_path_is_a_network_path() {
    assertTrue(NetworkFileSystems.isNetworkPath(Path.of("\\\\server\\share\\study\\img.dcm")));
  }

  @Test
  void the_windows_drive_type_decides_for_a_mapped_drive() {
    // A mapped drive reports the server file system (NTFS), only the drive type tells
    assertEquals(Kind.NETWORK, NetworkFileSystems.classify("NTFS", "Network drive", true));
    assertEquals(Kind.LOCAL, NetworkFileSystems.classify("NTFS", "Fixed drive", true));
    assertEquals(Kind.LOCAL, NetworkFileSystems.classify("FAT32", "Removable drive", true));
    assertEquals(Kind.OPTICAL, NetworkFileSystems.classify("CDFS", "CD-ROM", true));
  }

  @Test
  void the_local_flag_and_the_file_system_type_decide_elsewhere() {
    assertEquals(Kind.NETWORK, NetworkFileSystems.classify("smbfs", "Network Drive", false));
    assertEquals(Kind.NETWORK, NetworkFileSystems.classify("nfs4", "Local Disk", false));
    // Types OSHI does not know as network are still recognized
    assertEquals(Kind.NETWORK, NetworkFileSystems.classify("fuse.sshfs", "", true));
    assertEquals(Kind.OPTICAL, NetworkFileSystems.classify("iso9660", "", true));
    assertEquals(Kind.LOCAL, NetworkFileSystems.classify("ext4", "", true));
    assertEquals(Kind.LOCAL, NetworkFileSystems.classify(null, null, true));
  }

  @Test
  void the_deepest_mount_point_wins() {
    String root = SystemInfo.isWindows ? "C:\\" : "/";
    Path share = tempDir.resolve("share");
    Path outside = tempDir.resolve("elsewhere");
    MountTable table =
        new MountTable(
            List.of(
                new Volume(root, "ext4", "Local Disk", true),
                new Volume(share.toString(), "cifs", "Network Drive", false)));

    assertEquals("cifs", table.volumeOf(share.resolve("study").resolve("img.dcm")).type());
    assertEquals("ext4", table.volumeOf(outside).type());
  }

  @Test
  void a_path_outside_every_mount_has_no_volume() {
    MountTable table = new MountTable(List.of(new Volume("not a valid \0 mount", "x", "", true)));

    assertNull(table.volumeOf(tempDir));
  }
}
