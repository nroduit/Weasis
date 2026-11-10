/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.imp;

import com.formdev.flatlaf.util.SystemInfo;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.HardwareInfo;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.wado.LoadSeries;

/**
 * The DICOMDIR side of the local import: finding the file set of an inserted medium (IHE PDI, RAD
 * TF-2 §4.47) and reading its directory records instead of scanning the files.
 */
public final class DicomDirImport {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomDirImport.class);
  private static final List<String> DICOMDIR_NAMES =
      List.of("DICOMDIR", "dicomdir", "DICOMDIR.", "dicomdir."); // NON-NLS
  private static final String DICOMDIR = "DICOMDIR"; // NON-NLS

  private DicomDirImport() {}

  /** Whether the file is a DICOMDIR, by its name: {@code DICOMDIR} or {@code DICOMDIR.xxx}. */
  public static boolean isDicomDir(Path file) {
    if (file == null || file.getFileName() == null || !Files.isRegularFile(file)) {
      return false;
    }
    String name = file.getFileName().toString().toUpperCase(Locale.ROOT);
    return name.equals(DICOMDIR) || name.startsWith(DICOMDIR + '.');
  }

  /** The series of a DICOMDIR, null when the file cannot be read. */
  public static List<LoadSeries> loadDicomDir(
      File file, DicomModel dicomModel, boolean writeInCache) {
    List<LoadSeries> loadSeries = null;
    if (file != null && file.canRead()) {
      DicomDirLoader dirImport = new DicomDirLoader(file, dicomModel, writeInCache);
      loadSeries = dirImport.readDicomDir();
    }
    return loadSeries;
  }

  /**
   * Finds the DICOMDIR of the inserted media: optical discs first, then removable drives, as the
   * operating system reports them. Network drives and fixed disks are never probed, so a
   * disconnected share cannot stall the detection.
   *
   * @return the DICOMDIR file, or null when no media holds one
   */
  public static File getDcmDirFromMedia() {
    for (Path mount : mediaMounts()) {
      Path dicomdir = findDicomDir(mount);
      if (dicomdir != null) {
        return dicomdir.toFile();
      }
    }
    return null;
  }

  private static List<Path> mediaMounts() {
    List<Path> mounts = new ArrayList<>();
    if (HardwareInfo.isAvailable()) {
      HardwareInfo.opticalDrives().forEach(d -> addMount(mounts, d.mount()));
      HardwareInfo.removableDrives().forEach(d -> addMount(mounts, d.mount()));
    } else {
      legacyMediaDirectories().forEach(dir -> addMount(mounts, dir));
    }
    return mounts;
  }

  private static void addMount(List<Path> mounts, String mount) {
    try {
      Path path = Path.of(mount);
      if (!mounts.contains(path)) {
        mounts.add(path);
      }
    } catch (InvalidPathException e) {
      LOGGER.debug("Ignoring mount point {}", mount);
    }
  }

  /**
   * The DICOMDIR at the root of a mount, tolerating the lower-case and dotted names of some media.
   */
  static Path findDicomDir(Path mount) {
    for (String name : DICOMDIR_NAMES) {
      Path candidate = mount.resolve(name);
      if (Files.isReadable(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  /** Where desktop environments mount media, when the hardware probe cannot list the drives. */
  private static List<String> legacyMediaDirectories() {
    List<String> dirs = new ArrayList<>();
    if (SystemInfo.isWindows) {
      for (File root : File.listRoots()) {
        dirs.add(root.getPath());
      }
    } else if (SystemInfo.isMacOS) {
      addChildren(dirs, "/Volumes"); // NON-NLS
    } else {
      String user = System.getProperty("user.name", "local"); // NON-NLS
      addChildren(dirs, "/run/media/" + user); // NON-NLS
      addChildren(dirs, "/media/" + user); // NON-NLS
      addChildren(dirs, "/media"); // NON-NLS
      addChildren(dirs, "/mnt"); // NON-NLS
    }
    return dirs;
  }

  private static void addChildren(List<String> dirs, String directory) {
    try (Stream<Path> children = Files.list(Path.of(directory))) {
      children.filter(Files::isDirectory).map(Path::toString).forEach(dirs::add);
    } catch (IOException | RuntimeException e) {
      LOGGER.debug("Cannot list {}", directory);
    }
  }
}
