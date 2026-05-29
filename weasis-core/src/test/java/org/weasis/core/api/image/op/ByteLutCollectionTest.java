/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.op;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.opencv.op.lut.ByteLut;

class ByteLutCollectionTest {

  private static final int CHANNELS = 3;
  private static final int LUT_SIZE = 256;

  // -- invert() --------------------------------------------------------------

  @Test
  void invert_nullInputReturnsNull() {
    assertNull(ByteLutCollection.invert(null));
  }

  @Test
  void invert_returnsNewInstanceAndDoesNotMutateInput() {
    byte[][] original = identityLut();
    byte[][] originalSnapshot = deepCopy(original);

    byte[][] inverted = ByteLutCollection.invert(original);

    assertAll(
        () -> assertNotSame(original, inverted),
        () -> assertNotSame(original[0], inverted[0]),
        () -> assertArrayEquals(originalSnapshot[0], original[0], "input channel 0 unchanged"),
        () -> assertArrayEquals(originalSnapshot[1], original[1], "input channel 1 unchanged"),
        () -> assertArrayEquals(originalSnapshot[2], original[2], "input channel 2 unchanged"));
  }

  @Test
  void invert_reversesEachBand() {
    // Identity LUT [0..255]; inverted must have [255..0] in every channel.
    byte[][] inverted = ByteLutCollection.invert(identityLut());

    for (int band = 0; band < CHANNELS; band++) {
      for (int i = 0; i < LUT_SIZE; i++) {
        int expected = LUT_SIZE - 1 - i;
        assertEquals((byte) expected, inverted[band][i], "band %d index %d".formatted(band, i));
      }
    }
  }

  @Test
  void invert_isInvolutive() {
    // invert(invert(x)) == x — a critical property: applying MONOCHROME1 inversion twice
    // must return to the original LUT, otherwise display would drift on repeated toggle.
    byte[][] original = randomLut(42L);

    byte[][] roundTrip = ByteLutCollection.invert(ByteLutCollection.invert(original));

    for (int band = 0; band < CHANNELS; band++) {
      assertArrayEquals(original[band], roundTrip[band], "band " + band);
    }
  }

  @Test
  void invert_emptyBandsArrayThrows() {
    assertThrows(IllegalArgumentException.class, () -> ByteLutCollection.invert(new byte[0][]));
  }

  @Test
  void invert_unevenBandLengthsThrows() {
    byte[][] lut = new byte[3][];
    lut[0] = new byte[256];
    lut[1] = new byte[128]; // mismatched length
    lut[2] = new byte[256];

    assertThrows(IllegalArgumentException.class, () -> ByteLutCollection.invert(lut));
  }

  @Test
  void invert_supportsArbitraryBandLength() {
    // Non-standard LUT size (e.g. 16-bit display ramps) must still reverse correctly.
    byte[][] lut = new byte[3][8];
    for (int band = 0; band < 3; band++) {
      for (int i = 0; i < 8; i++) {
        lut[band][i] = (byte) i;
      }
    }

    byte[][] inverted = ByteLutCollection.invert(lut);

    for (int band = 0; band < 3; band++) {
      assertArrayEquals(new byte[] {7, 6, 5, 4, 3, 2, 1, 0}, inverted[band], "band " + band);
    }
  }

  // -- readLutFile(Scanner) -------------------------------------------------

  @Test
  void readLutFile_nullScannerThrowsNpe() {
    assertThrows(NullPointerException.class, () -> ByteLutCollection.readLutFile(null));
  }

  @Test
  void readLutFile_parsesStandard256EntryRgbFile() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < LUT_SIZE; i++) {
      sb.append(i).append(' ').append(LUT_SIZE - 1 - i).append(' ').append(128).append('\n');
    }

    byte[][] lut = ByteLutCollection.readLutFile(new Scanner(sb.toString()));

    assertNotNull(lut);
    assertEquals(CHANNELS, lut.length);
    assertEquals(LUT_SIZE, lut[0].length);
    // File order is R G B; storage order is B G R.
    for (int i = 0; i < LUT_SIZE; i++) {
      assertEquals((byte) i, lut[2][i], "R channel @ " + i);
      assertEquals((byte) (LUT_SIZE - 1 - i), lut[1][i], "G channel @ " + i);
      assertEquals((byte) 128, lut[0][i], "B channel @ " + i);
    }
  }

  @Test
  void readLutFile_skipsCommentsAndEmptyLines() {
    StringBuilder sb = new StringBuilder();
    sb.append("# header comment\n");
    sb.append("\n");
    sb.append("  \n");
    for (int i = 0; i < LUT_SIZE; i++) {
      if (i == 10) sb.append("# inline comment\n");
      if (i == 20) sb.append("\n");
      sb.append(i).append(' ').append(i).append(' ').append(i).append('\n');
    }

    byte[][] lut = ByteLutCollection.readLutFile(new Scanner(sb.toString()));

    for (int i = 0; i < LUT_SIZE; i++) {
      assertEquals((byte) i, lut[2][i], "R @ " + i); // R channel
    }
  }

  @Test
  void readLutFile_clampsValuesOutsideByteRange() {
    String input = "-50 999 128\n300 -10 0\n";

    byte[][] lut = ByteLutCollection.readLutFile(new Scanner(input));

    // R channel index 0: -50 -> clamped to 0
    // G channel index 0: 999 -> clamped to 255
    // B channel index 0: 128 -> stays
    assertAll(
        () -> assertEquals((byte) 0, lut[2][0], "R clamped low"),
        () -> assertEquals((byte) 255, lut[1][0], "G clamped high"),
        () -> assertEquals((byte) 128, lut[0][0], "B preserved"),
        () -> assertEquals((byte) 255, lut[2][1], "R clamped high"),
        () -> assertEquals((byte) 0, lut[1][1], "G clamped low"));
  }

  @Test
  void readLutFile_skipsMalformedLines() {
    // Wrong component count and non-numeric token are silently skipped — valid lines still
    // populate the LUT in order.
    String input = "10 20 30\nonly two\nfoo bar baz\n40 50 60\n";

    byte[][] lut = ByteLutCollection.readLutFile(new Scanner(input));

    assertAll(
        () -> assertEquals((byte) 10, lut[2][0], "first valid R"),
        () -> assertEquals((byte) 40, lut[2][1], "second valid R after skips"));
  }

  @Test
  void readLutFile_shortFileBackfillsRemainingEntriesWithLastValid() {
    // Only two valid entries — the rest of the 256 slots should be filled with the LAST valid
    // value, not zero. Otherwise a short LUT silently truncates to black past row N.
    String input = "10 20 30\n40 50 60\n";

    byte[][] lut = ByteLutCollection.readLutFile(new Scanner(input));

    assertAll(
        () -> assertEquals((byte) 10, lut[2][0]),
        () -> assertEquals((byte) 40, lut[2][1]),
        () -> assertEquals((byte) 40, lut[2][2], "R back-filled with last valid"),
        () -> assertEquals((byte) 40, lut[2][255], "R back-filled to the last slot"),
        () -> assertEquals((byte) 50, lut[1][255], "G back-filled to the last slot"),
        () -> assertEquals((byte) 60, lut[0][255], "B back-filled to the last slot"));
  }

  @Test
  void readLutFile_completelyEmptyFileProducesIdentityLut() {
    // No valid lines — fall-back rule: each band[i] = i (identity ramp).
    byte[][] lut = ByteLutCollection.readLutFile(new Scanner(""));

    for (int band = 0; band < CHANNELS; band++) {
      for (int i = 0; i < LUT_SIZE; i++) {
        assertEquals(
            (byte) i,
            lut[band][i],
            "empty-file fallback identity ramp, band " + band + " idx " + i);
      }
    }
  }

  // -- readLutFilesFromResourcesDir -----------------------------------------

  @Test
  void readLutFilesFromResourcesDir_nullListThrowsNpe() {
    assertThrows(
        NullPointerException.class,
        () -> ByteLutCollection.readLutFilesFromResourcesDir(null, Path.of("/tmp")));
  }

  @Test
  void readLutFilesFromResourcesDir_nullFolderIsNoOp() {
    List<ByteLut> entries = new ArrayList<>();

    assertDoesNotThrow(() -> ByteLutCollection.readLutFilesFromResourcesDir(entries, null));

    assertTrue(entries.isEmpty(), "no entries added for null folder");
  }

  @Test
  void readLutFilesFromResourcesDir_nonExistentFolderIsNoOp() {
    List<ByteLut> entries = new ArrayList<>();
    Path nonExistent = Path.of("/var/empty/this-path-does-not-exist-xyz");

    assertDoesNotThrow(() -> ByteLutCollection.readLutFilesFromResourcesDir(entries, nonExistent));

    assertTrue(entries.isEmpty());
  }

  @Test
  void readLutFilesFromResourcesDir_loadsAndSortsByName(@TempDir Path tempDir) throws IOException {
    // Write two valid LUT files in non-alphabetical order; result must be sorted by name.
    Files.writeString(tempDir.resolve("zebra.lut"), "0 0 0\n255 255 255\n");
    Files.writeString(tempDir.resolve("alpha.lut"), "128 128 128\n");
    List<ByteLut> entries = new ArrayList<>();

    ByteLutCollection.readLutFilesFromResourcesDir(entries, tempDir);

    assertAll(
        () -> assertEquals(2, entries.size()),
        () -> assertEquals("alpha", entries.get(0).name()),
        () -> assertEquals("zebra", entries.get(1).name()));
  }

  @Test
  void readLutFilesFromResourcesDir_unreadableFileSkippedWithoutThrowing(@TempDir Path tempDir)
      throws IOException {
    // Garbled content -> ByteLut construction may fail; the loader must swallow the error
    // and continue with the next file rather than aborting the whole directory.
    Files.writeString(tempDir.resolve("good.lut"), "0 0 0\n");
    Files.writeString(tempDir.resolve("bad.lut"), "absolutely not a lut file");
    List<ByteLut> entries = new ArrayList<>();

    assertDoesNotThrow(() -> ByteLutCollection.readLutFilesFromResourcesDir(entries, tempDir));

    // 'good.lut' should be present; the bad one may be present (with back-filled identity) or
    // absent depending on parser tolerance — only assert that the good one wasn't lost.
    assertTrue(
        entries.stream().anyMatch(b -> "good".equals(b.name())),
        "good LUT must be loaded even when another file is malformed");
  }

  // -- helpers --------------------------------------------------------------

  private static byte[][] identityLut() {
    byte[][] lut = new byte[CHANNELS][LUT_SIZE];
    for (int band = 0; band < CHANNELS; band++) {
      for (int i = 0; i < LUT_SIZE; i++) {
        lut[band][i] = (byte) i;
      }
    }
    return lut;
  }

  private static byte[][] randomLut(long seed) {
    byte[][] lut = new byte[CHANNELS][LUT_SIZE];
    java.util.Random random = new java.util.Random(seed);
    for (int band = 0; band < CHANNELS; band++) {
      random.nextBytes(lut[band]);
    }
    return lut;
  }

  private static byte[][] deepCopy(byte[][] src) {
    byte[][] copy = new byte[src.length][];
    for (int i = 0; i < src.length; i++) {
      copy[i] = src[i].clone();
    }
    return copy;
  }
}
