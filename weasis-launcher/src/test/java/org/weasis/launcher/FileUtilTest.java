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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests {@link FileUtil#unzip(InputStream, Path)} — the launcher's ZIP extraction routine.
 *
 * <p>Beyond plain round-trip extraction, the method is hardened against malicious archives. These
 * tests pin down those guards so a regression cannot silently re-open a known attack surface:
 *
 * <ul>
 *   <li><strong>Zip slip</strong>: an entry whose normalized path escapes the target directory must
 *       be rejected, never written outside {@code targetDir}.
 *   <li><strong>Zip bomb (size)</strong>: extraction aborts once the total uncompressed output
 *       exceeds the budget, enforced while streaming rather than after the fact.
 *   <li><strong>Argument validation</strong>: null stream or null target fail fast.
 * </ul>
 */
class FileUtilTest {

  @Test
  void unzipExtractsFilesAndNestedDirectories(@TempDir Path target) throws IOException {
    byte[] rootContent = "root file content".getBytes(StandardCharsets.UTF_8);
    byte[] nestedContent = "nested file content".getBytes(StandardCharsets.UTF_8);

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(buffer)) {
      writeEntry(zos, "root.txt", rootContent);
      zos.putNextEntry(new ZipEntry("sub/"));
      zos.closeEntry();
      writeEntry(zos, "sub/nested.txt", nestedContent);
    }

    FileUtil.unzip(new ByteArrayInputStream(buffer.toByteArray()), target);

    Path root = target.resolve("root.txt");
    Path nested = target.resolve("sub/nested.txt");
    assertTrue(Files.isRegularFile(root), "root entry should be extracted");
    assertTrue(Files.isDirectory(target.resolve("sub")), "directory entry should be created");
    assertTrue(Files.isRegularFile(nested), "nested entry should be extracted");
    assertArrayEquals(rootContent, Files.readAllBytes(root));
    assertArrayEquals(nestedContent, Files.readAllBytes(nested));
  }

  @Test
  void unzipCreatesTargetDirectoryWhenMissing(@TempDir Path parent) throws IOException {
    Path target = parent.resolve("does/not/exist/yet");
    assertFalse(Files.exists(target));

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(buffer)) {
      writeEntry(zos, "a.txt", "x".getBytes(StandardCharsets.UTF_8));
    }

    FileUtil.unzip(new ByteArrayInputStream(buffer.toByteArray()), target);

    assertTrue(Files.isDirectory(target));
    assertTrue(Files.isRegularFile(target.resolve("a.txt")));
  }

  @Test
  void unzipRejectsZipSlipEntry(@TempDir Path parent) throws IOException {
    Path target = parent.resolve("target");
    Files.createDirectories(target);

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(buffer)) {
      writeEntry(zos, "../escaped.txt", "evil".getBytes(StandardCharsets.UTF_8));
    }

    IOException ex =
        assertThrows(
            IOException.class,
            () -> FileUtil.unzip(new ByteArrayInputStream(buffer.toByteArray()), target));
    assertTrue(ex.getMessage().contains("outside the target directory"));
    assertFalse(
        Files.exists(parent.resolve("escaped.txt")), "no file may be written outside target");
  }

  @Test
  void unzipAbortsWhenUncompressedSizeExceedsBudget(@TempDir Path target) throws IOException {
    // A single entry whose uncompressed size is larger than the 1 GB budget. Highly compressible
    // zeros keep the test archive tiny while the streamed output trips the size guard.
    long oversized = 1_073_741_824L + 1L;

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(buffer)) {
      ZipEntry entry = new ZipEntry("bomb.bin");
      zos.putNextEntry(entry);
      byte[] chunk = new byte[1 << 20]; // 1 MiB of zeros
      long written = 0;
      while (written < oversized) {
        int len = (int) Math.min(chunk.length, oversized - written);
        zos.write(chunk, 0, len);
        written += len;
      }
      zos.closeEntry();
    }

    IOException ex =
        assertThrows(
            IOException.class,
            () -> FileUtil.unzip(new ByteArrayInputStream(buffer.toByteArray()), target));
    assertTrue(ex.getMessage().contains("maximum allowed uncompressed size"));
  }

  @Test
  void unzipRejectsNullArguments(@TempDir Path target) {
    assertThrows(IllegalArgumentException.class, () -> FileUtil.unzip(null, target));
    assertThrows(
        IllegalArgumentException.class,
        () -> FileUtil.unzip(new ByteArrayInputStream(new byte[0]), null));
  }

  @Test
  void unzipClosesTheSourceStream(@TempDir Path target) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(buffer)) {
      writeEntry(zos, "a.txt", "data".getBytes(StandardCharsets.UTF_8));
    }

    boolean[] closed = {false};
    InputStream tracking =
        new ByteArrayInputStream(buffer.toByteArray()) {
          @Override
          public void close() throws IOException {
            closed[0] = true;
            super.close();
          }
        };

    FileUtil.unzip(tracking, target);

    assertTrue(closed[0], "source stream must be closed after extraction");
  }

  private static void writeEntry(ZipOutputStream zos, String name, byte[] content)
      throws IOException {
    zos.putNextEntry(new ZipEntry(name));
    zos.write(content);
    zos.closeEntry();
  }
}
