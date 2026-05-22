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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests {@link GzipManager} — the gzip codec used by {@link
 * org.weasis.core.api.service.WProperties#putByteArrayProperty} and {@code getByteArrayProperty} to
 * persist byte-array user preferences (Base64-encoded over JSON).
 *
 * <p>A regression in this layer corrupts every byte-array preference silently: the user sees
 * default values on next launch, with no error message. This is the lowest-layer guard against
 * <strong>WEA-004 R3 / R4</strong> ("loss of user preferences via programming error / deployment
 * error") for the binary preference subtree.
 *
 * <p>Verified contracts:
 *
 * <ul>
 *   <li><strong>Round-trip integrity</strong>: any byte array passes through compress+uncompress
 *       unchanged.
 *   <li><strong>Magic-number gating</strong>: {@code gzipUncompressToByte} returns the input as-is
 *       when it is <em>not</em> gzip-encoded — this is the policy that keeps legacy uncompressed
 *       preference values readable after a code change.
 *   <li><strong>Compression threshold</strong>: inputs shorter than the configured threshold are
 *       returned unchanged (no compression overhead on small values).
 *   <li><strong>File round-trip</strong>: compress to file → uncompress from file → identity.
 * </ul>
 */
class GzipManagerTest {

  // ---------------------------------------------------------------------------
  // isGzip — magic-number detection
  // ---------------------------------------------------------------------------

  @Test
  void isGzip_trueForActualGzipBytes() throws IOException {
    byte[] payload = "weasis-prefs".getBytes(StandardCharsets.UTF_8);
    byte[] compressed = GzipManager.gzipCompressToByte(payload);
    assertTrue(GzipManager.isGzip(compressed));
  }

  @Test
  void isGzip_falseForPlainAscii() {
    assertFalse(GzipManager.isGzip("not-gzip".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void isGzip_falseForNull() {
    assertFalse(GzipManager.isGzip(null));
  }

  @Test
  void isGzip_falseForShortByteArray() {
    // Magic-number check requires at least 4 bytes — 3 bytes must NOT be misclassified.
    assertFalse(GzipManager.isGzip(new byte[] {0x1f, (byte) 0x8b, 0x08}));
  }

  @Test
  void isGzip_falseForArrayWithSwappedMagicBytes() {
    // First two bytes swapped: not a valid gzip header.
    assertFalse(GzipManager.isGzip(new byte[] {(byte) 0x8b, 0x1f, 0x08, 0x00}));
  }

  // ---------------------------------------------------------------------------
  // Byte-array round-trip — the load-bearing path for WProperties byte-array
  // preferences. A bug here would silently corrupt every binary preference.
  // ---------------------------------------------------------------------------

  @Test
  void byteRoundTrip_preservesArbitraryBinaryContent() throws IOException {
    // Include zero bytes, high bytes, and bytes that look like ASCII so we exercise
    // the full byte range, not just printable characters.
    byte[] original = new byte[1024];
    for (int i = 0; i < original.length; i++) {
      original[i] = (byte) (i & 0xff);
    }
    byte[] compressed = GzipManager.gzipCompressToByte(original);
    assertTrue(GzipManager.isGzip(compressed), "compressed bytes must carry the gzip header");
    byte[] decompressed = GzipManager.gzipUncompressToByte(compressed);
    assertArrayEquals(original, decompressed);
  }

  @Test
  void byteRoundTrip_emptyArrayReturnsEmpty() throws IOException {
    // The default threshold is 1, so empty input falls below the threshold and is returned as-is.
    byte[] empty = new byte[0];
    assertSame(empty, GzipManager.gzipCompressToByte(empty));
  }

  // ---------------------------------------------------------------------------
  // Compression threshold — when bytes.length < requiredByteNumber the input
  // is returned unchanged (no gzip overhead on small values).
  // ---------------------------------------------------------------------------

  @Test
  void compressToByte_belowThresholdReturnsInputUnchanged() throws IOException {
    byte[] small = "tiny".getBytes(StandardCharsets.UTF_8);
    byte[] result = GzipManager.gzipCompressToByte(small, 1400);
    // Same reference (no compression performed)
    assertSame(small, result);
    assertFalse(GzipManager.isGzip(result));
  }

  @Test
  void compressToByte_atOrAboveThresholdCompresses() throws IOException {
    byte[] payload = new byte[1400];
    java.util.Arrays.fill(payload, (byte) 'a');
    byte[] result = GzipManager.gzipCompressToByte(payload, 1400);
    assertNotEquals(payload.length, result.length, "highly compressible 1400 bytes should shrink");
    assertTrue(GzipManager.isGzip(result));
    assertArrayEquals(payload, GzipManager.gzipUncompressToByte(result));
  }

  // ---------------------------------------------------------------------------
  // Defensive uncompress — non-gzip input must be returned unchanged (the
  // legacy-uncompressed-preference compatibility path).
  // ---------------------------------------------------------------------------

  @Test
  void gzipUncompressToByte_passThroughForNonGzipInput() throws IOException {
    byte[] plain = "legacy-pref-value".getBytes(StandardCharsets.UTF_8);
    assertArrayEquals(plain, GzipManager.gzipUncompressToByte(plain));
  }

  // ---------------------------------------------------------------------------
  // File round-trip — drives the disk-backed Base64+gzip preference flow.
  // ---------------------------------------------------------------------------

  @Test
  void gzipCompressThenUncompressToFile_preservesContent(@TempDir Path tmp) throws IOException {
    byte[] payload = "patient-prefs-blob".getBytes(StandardCharsets.UTF_8);
    Path gz = tmp.resolve("prefs.gz");
    boolean compressed = GzipManager.gzipCompress(new ByteArrayInputStream(payload), gz.toString());
    assertTrue(compressed, "gzipCompress must succeed for valid input");
    assertTrue(Files.size(gz) > 0);

    Path out = tmp.resolve("prefs.bin");
    boolean uncompressed = GzipManager.gzipUncompressToFile(gz.toFile(), out.toFile());
    assertTrue(uncompressed);
    assertArrayEquals(payload, Files.readAllBytes(out));
  }

  @Test
  void gzipUncompressToFile_writesRawWhenInputIsNotGzip(@TempDir Path tmp) throws IOException {
    // gzipUncompressToFile(byte[], File) detects non-gzip input and writes it as-is
    // (no spurious decompression of plain bytes).
    byte[] plain = "raw-prefs".getBytes(StandardCharsets.UTF_8);
    Path out = tmp.resolve("plain.bin");
    boolean ok = GzipManager.gzipUncompressToFile(plain, out.toFile());
    assertTrue(ok);
    assertArrayEquals(plain, Files.readAllBytes(out));
  }

  @Test
  void gzipCompress_failsCleanlyForInvalidTargetPath() throws IOException {
    // A non-existent parent directory must yield false (not throw) — the WProperties
    // call site treats false as "preference not persisted, fall back to in-memory".
    boolean ok =
        GzipManager.gzipCompress(
            new ByteArrayInputStream(new byte[] {1, 2, 3}),
            "/this/path/does/not/exist/" + System.nanoTime() + "/out.gz");
    assertFalse(ok);
  }

  @Test
  void gzipUncompressToFile_returnsFalseForCorruptInput(@TempDir Path tmp) throws IOException {
    // Trailing 0x1f 0x8b bytes (gzip magic) followed by garbage triggers IOException
    // during decompression — must surface as false, not propagate the IOException.
    Path corrupt = tmp.resolve("corrupt.gz");
    Files.write(corrupt, new byte[] {0x1f, (byte) 0x8b, 0x08, 0x00, 0x00, 0x00, 0x00});
    Path out = tmp.resolve("out.bin");
    assertFalse(GzipManager.gzipUncompressToFile(corrupt.toFile(), out.toFile()));
  }

  // ---------------------------------------------------------------------------
  // Compression ratio sanity — highly compressible data should be smaller.
  // ---------------------------------------------------------------------------

  @Test
  void compressionRatio_repeatedBytesShrinkSignificantly() throws IOException {
    byte[] payload = new byte[8192];
    java.util.Arrays.fill(payload, (byte) 'X');
    byte[] compressed = GzipManager.gzipCompressToByte(payload);
    // Gzip header + a single back-reference for 8KB of 'X' should compress to well under 1 KB.
    assertTrue(
        compressed.length < 256,
        "8 KB of repeated 'X' should compress under 256 bytes, was " + compressed.length);
    assertEquals(8192, GzipManager.gzipUncompressToByte(compressed).length);
  }
}
