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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.formdev.flatlaf.util.SystemInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Covers the removability rule, whose outcome only depends on the mount point, the file-system type
 * and the description. The cases below hold on every platform.
 */
class HardwareInfoTest {

  @Test
  void windowsRemovableDescriptionIsEnough() {
    assertTrue(HardwareInfo.isRemovable("E:\\", "FAT32", "Removable drive"));
  }

  @Test
  void mediaMountsAreRemovable() {
    // Where the desktop environment of this platform mounts removable media.
    String mount = SystemInfo.isMacOS ? "/Volumes/USB" : "/media/user/USB";
    assertTrue(HardwareInfo.isRemovable(mount, "vfat", "Local Disk"));
  }

  @Test
  void udisksRunMediaMountsAreRemovable() {
    // udisks2 mounts under /run/media/<user> on a systemd desktop, which OSHI hides by default;
    // widenLinuxMountScan() lifts that exclusion, so the rule must accept the path.
    String mount = SystemInfo.isMacOS ? "/Volumes/USB" : "/run/media/user/USB";
    assertTrue(HardwareInfo.isRemovable(mount, "vfat", "Local Disk"));
  }

  @Test
  void networkAndRamDisksAreNeverRemovable() {
    assertFalse(HardwareInfo.isRemovable("Z:\\", "NTFS", "Network drive"));
    assertFalse(HardwareInfo.isRemovable("/media/user/cache", "tmpfs", "Ram Disk"));
  }

  @Test
  void pseudoFileSystemsAreNeverRemovable() {
    // A virtual file system mounted where removable media usually appear is still not media.
    assertFalse(HardwareInfo.isRemovable("/media/user/overlay", "squashfs", "Local Disk"));
    assertFalse(HardwareInfo.isRemovable("/Volumes/tmp", "tmpfs", "Local Disk"));
  }

  @Test
  void windowsFixedDrivesAreNotRemovable() {
    // The mount-root rule cannot help on Windows, so the description must carry the whole verdict.
    assertFalse(HardwareInfo.isRemovable("C:\\", "NTFS", "Fixed drive"));
    assertFalse(HardwareInfo.isRemovable("R:\\", "NTFS", "RAM drive"));
  }

  @Test
  void systemVolumesAreNotRemovable() {
    assertFalse(HardwareInfo.isRemovable("/", "ext4", "Local Disk"));
    assertFalse(HardwareInfo.isRemovable("/home/user/data", "ext4", "Local Disk"));
    assertFalse(HardwareInfo.isRemovable("", "ext4", "Local Disk"));
  }

  @Test
  void the_linux_mount_table_keeps_the_mount_in_use_and_decodes_escapes() {
    List<HardwareInfo.Volume> volumes =
        HardwareInfo.parseLinuxMounts(
            List.of(
                "/dev/nvme0n1p2 / ext4 rw,relatime 0 0",
                "systemd-1 /mnt/nas autofs rw,relatime,fd=60 0 0",
                "//nas/share /mnt/nas cifs rw,relatime,vers=3.1.1 0 0",
                "/dev/loop87 /run/media/user/My\\040Disc iso9660 ro,nosuid 0 0",
                "broken"));

    assertEquals(3, volumes.size());
    assertEquals(new HardwareInfo.Volume("/mnt/nas", "cifs", "", true), volumes.get(1));
    assertEquals("/run/media/user/My Disc", volumes.get(2).mount());
    assertTrue(HardwareInfo.isOptical(volumes.get(2).type(), volumes.get(2).description()));
    assertEquals("a\\b", HardwareInfo.unescapeMountField("a\\b"));
  }
}
