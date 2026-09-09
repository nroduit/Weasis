/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.exp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.dcm4che3.media.DicomDirWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.util.NetworkFileSystems;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.explorer.imp.DicomDirLoader;

/**
 * Destination of a file export. When the destination is a network share, files and the DICOMDIR are
 * produced in a local staging directory and moved in one bulk transfer each: DICOM writers emit one
 * small write per attribute and the DICOMDIR writer seeks back and forth, which is fine on a local
 * disk and a round trip per call on a share.
 */
final class ExportTarget implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(ExportTarget.class);
  private static final String DICOMDIR = "DICOMDIR"; // NON-NLS

  private final File root;
  private final Path stagingDir;
  private final Set<File> createdDirectories = new HashSet<>();
  private Path stagedDicomDir;

  ExportTarget(File root) {
    this.root = root;
    boolean network = NetworkFileSystems.isNetworkPath(root.toPath());
    this.stagingDir =
        network
            ? FileUtil.createTempDir(AppProperties.buildAccessibleTempDirectory("tmp", "export"))
            : null;
    if (network) {
      LOGGER.info("Exporting to a network share, files are staged in {}", stagingDir);
    }
  }

  File root() {
    return root;
  }

  boolean isStaged() {
    return stagingDir != null;
  }

  /** Creates the directory once; later calls for the same directory cost nothing. */
  void ensureDirectory(File directory) throws IOException {
    if (createdDirectories.add(directory)) {
      Files.createDirectories(directory.toPath());
    }
  }

  /**
   * Produces {@code destination} with {@code writer}. On a network share the writer runs against a
   * local file that is then moved to the destination.
   *
   * @param success tells from the writer result whether a file was produced
   * @return the writer result
   */
  <T> T write(File destination, Function<File, T> writer, Predicate<T> success) throws IOException {
    if (stagingDir == null) {
      return writer.apply(destination);
    }
    Path staged = stagingDir.resolve(destination.getName());
    T result = writer.apply(staged.toFile());
    if (success.test(result) && Files.isRegularFile(staged)) {
      Files.move(staged, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } else {
      FileUtil.delete(staged);
    }
    return result;
  }

  /** Moves a companion file written next to a staged file (e.g. a presentation sidecar). */
  void moveCompanion(File destination, String suffix) throws IOException {
    if (stagingDir == null) {
      return;
    }
    Path staged = stagingDir.resolve(destination.getName() + suffix);
    if (Files.isRegularFile(staged)) {
      Path target = destination.toPath().resolveSibling(destination.getName() + suffix);
      Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /** Path components of a file relative to the export root, as Referenced File ID (0004,1500). */
  String[] toFileIDs(File file) {
    Path relative = root.toPath().toAbsolutePath().relativize(file.toPath().toAbsolutePath());
    String[] ids = new String[relative.getNameCount()];
    for (int i = 0; i < ids.length; i++) {
      ids[i] = relative.getName(i).toString();
    }
    return ids;
  }

  /** Opens (or creates) the DICOMDIR of the export root, on a local copy when staged. */
  DicomDirWriter openDicomDir() throws IOException {
    File dicomDir = new File(root, DICOMDIR);
    if (stagingDir == null) {
      return DicomDirLoader.open(dicomDir);
    }
    stagedDicomDir = stagingDir.resolve(DICOMDIR);
    if (dicomDir.isFile()) {
      Files.copy(dicomDir.toPath(), stagedDicomDir, StandardCopyOption.REPLACE_EXISTING);
    }
    return DicomDirLoader.open(stagedDicomDir.toFile());
  }

  /** Closes the writer, then publishes the staged DICOMDIR to the export root. */
  void closeDicomDir(DicomDirWriter writer) throws IOException {
    if (writer == null) {
      return;
    }
    writer.close();
    if (stagedDicomDir != null) {
      Files.move(
          stagedDicomDir, new File(root, DICOMDIR).toPath(), StandardCopyOption.REPLACE_EXISTING);
      stagedDicomDir = null;
    }
  }

  @Override
  public void close() {
    if (stagingDir != null) {
      FileUtil.recursiveDelete(stagingDir);
    }
  }
}
