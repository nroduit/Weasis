/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.source;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.stream.BytesWithImageDescriptor;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Frames read back by position from synthetic whole slide files. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class LocalFrameStoreTest {

  // Fake codestreams: only their first bytes matter to the frame index
  private static final byte[] A = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3, 4, 5, 6, 7, 8};
  private static final byte[] B = {9, 10, 11, 12, 13, 14};
  private static final byte[] C = {(byte) 0xFF, (byte) 0xD8, 21, 22, 23, 24, 25, 26};

  @TempDir Path dir;

  private static Attributes header(String uid, int frames, String photometric) {
    Attributes dcm = new Attributes();
    dcm.setString(Tag.SOPClassUID, VR.UI, UID.VLWholeSlideMicroscopyImageStorage);
    dcm.setString(Tag.SOPInstanceUID, VR.UI, uid);
    dcm.setString(Tag.ImageType, VR.CS, "ORIGINAL", "PRIMARY", "VOLUME", "NONE"); // NON-NLS
    dcm.setString(Tag.DimensionOrganizationType, VR.CS, "TILED_FULL"); // NON-NLS
    dcm.setInt(Tag.TotalPixelMatrixColumns, VR.UL, 2 * frames);
    dcm.setInt(Tag.TotalPixelMatrixRows, VR.UL, 2);
    dcm.setInt(Tag.Columns, VR.US, 2);
    dcm.setInt(Tag.Rows, VR.US, 2);
    dcm.setInt(Tag.NumberOfFrames, VR.IS, frames);
    dcm.setInt(Tag.SamplesPerPixel, VR.US, 3);
    dcm.setString(Tag.PhotometricInterpretation, VR.CS, photometric);
    dcm.setInt(Tag.PlanarConfiguration, VR.US, 0);
    dcm.setInt(Tag.BitsAllocated, VR.US, 8);
    dcm.setInt(Tag.BitsStored, VR.US, 8);
    dcm.setInt(Tag.HighBit, VR.US, 7);
    dcm.setInt(Tag.PixelRepresentation, VR.US, 0);
    return dcm;
  }

  private Path write(Attributes dcm, String tsuid) throws IOException {
    Path file = dir.resolve(dcm.getString(Tag.SOPInstanceUID) + ".dcm");
    try (DicomOutputStream out = new DicomOutputStream(file.toFile())) {
      out.writeDataset(dcm.createFileMetaInformation(tsuid), dcm);
    }
    return file;
  }

  /** Frame 1 is fragments A and B, frame 2 is fragment C. */
  private Path twoFramesInThreeFragments(String uid, byte[] basicOffsetTable) throws IOException {
    Attributes dcm = header(uid, 2, "YBR_FULL_422"); // NON-NLS
    Fragments fragments = dcm.newFragments(Tag.PixelData, VR.OB, 4);
    fragments.add(basicOffsetTable);
    fragments.add(A);
    fragments.add(B);
    fragments.add(C);
    return write(dcm, UID.JPEGBaseline8Bit);
  }

  private static byte[] concat(byte[] first, byte[] second) {
    byte[] all = new byte[first.length + second.length];
    System.arraycopy(first, 0, all, 0, first.length);
    System.arraycopy(second, 0, all, first.length, second.length);
    return all;
  }

  private static byte[] bytes(ByteBuffer buffer) {
    byte[] b = new byte[buffer.remaining()];
    buffer.duplicate().get(b);
    return b;
  }

  private static void assertFrames(Path file, String uid) throws IOException {
    try (LocalFrameStore store = LocalFrameStore.open(List.of(file))) {
      BytesWithImageDescriptor frames = store.frames(uid);
      assertAll(
          () -> assertArrayEquals(concat(A, B), bytes(frames.getBytes(0))),
          () -> assertArrayEquals(C, bytes(frames.getBytes(1))),
          () -> assertEquals(UID.JPEGBaseline8Bit, frames.getTransferSyntax()));
    }
  }

  @Test
  void one_fragment_per_frame_is_read_directly() throws IOException {
    Attributes dcm = header("1.1", 3, "YBR_FULL_422"); // NON-NLS
    Fragments fragments = dcm.newFragments(Tag.PixelData, VR.OB, 4);
    fragments.add(new byte[0]);
    fragments.add(A);
    fragments.add(B);
    fragments.add(C);
    Path file = write(dcm, UID.JPEGBaseline8Bit);
    try (LocalFrameStore store = LocalFrameStore.open(List.of(file))) {
      BytesWithImageDescriptor frames = store.frames("1.1"); // NON-NLS
      ByteBuffer frame = frames.getBytes(1);
      assertAll(
          () -> assertArrayEquals(B, bytes(frame)),
          () -> assertEquals(frame.array().length, frame.limit(), "the reader takes the array"),
          () -> assertArrayEquals(C, bytes(frames.getBytes(2))),
          () -> assertThrows(IllegalArgumentException.class, () -> frames.getBytes(3)),
          () -> assertEquals(1, store.slides().size()),
          () -> assertEquals(6, store.slides().getFirst().width()));
    }
  }

  @Test
  void the_basic_offset_table_groups_the_fragments_of_a_frame() throws IOException {
    // Item offsets count the 8-byte item headers: A and B take 18 + 14 bytes
    byte[] table =
        ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(0).putInt(32).array();
    assertFrames(twoFramesInThreeFragments("1.2", table), "1.2"); // NON-NLS
  }

  @Test
  void without_offset_table_frames_start_at_each_codestream() throws IOException {
    assertFrames(twoFramesInThreeFragments("1.3", new byte[0]), "1.3"); // NON-NLS
  }

  @Test
  void the_extended_offset_table_wins_over_an_empty_basic_table() throws IOException {
    Attributes dcm = header("1.4", 2, "YBR_FULL_422"); // NON-NLS
    // Fragment B also starts like a codestream, so only the table can group A and B
    byte[] b = {(byte) 0xFF, (byte) 0xD8, 11, 12, 13, 14};
    Fragments fragments = dcm.newFragments(Tag.PixelData, VR.OB, 4);
    fragments.add(new byte[0]);
    fragments.add(A);
    fragments.add(b);
    fragments.add(C);
    byte[] table =
        ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putLong(0).putLong(32).array();
    dcm.setBytes(Tag.ExtendedOffsetTable, VR.OV, table);
    Path file = write(dcm, UID.JPEGBaseline8Bit);
    try (LocalFrameStore store = LocalFrameStore.open(List.of(file))) {
      assertArrayEquals(concat(A, b), bytes(store.frames("1.4").getBytes(0))); // NON-NLS
    }
  }

  @Test
  void native_frames_follow_each_other() throws IOException {
    Attributes dcm = header("1.5", 3, "RGB"); // NON-NLS
    byte[] pixels = new byte[3 * 12];
    for (int i = 0; i < pixels.length; i++) {
      pixels[i] = (byte) i;
    }
    dcm.setBytes(Tag.PixelData, VR.OB, pixels);
    Path file = write(dcm, UID.ExplicitVRLittleEndian);
    try (LocalFrameStore store = LocalFrameStore.open(List.of(file))) {
      byte[] expected = new byte[12];
      System.arraycopy(pixels, 24, expected, 0, 12);
      assertArrayEquals(expected, bytes(store.frames("1.5").getBytes(2))); // NON-NLS
    }
  }

  @Test
  void other_files_are_skipped_and_unknown_instances_refused() throws IOException {
    Attributes ct = header("2.1", 1, "RGB"); // NON-NLS
    ct.setString(Tag.SOPClassUID, VR.UI, UID.CTImageStorage);
    ct.setBytes(Tag.PixelData, VR.OB, new byte[12]);
    Path missing = dir.resolve("missing.dcm"); // NON-NLS
    try (LocalFrameStore store =
        LocalFrameStore.open(List.of(write(ct, UID.ExplicitVRLittleEndian), missing))) {
      assertAll(
          () -> assertTrue(store.slides().isEmpty()),
          () -> assertThrows(IOException.class, () -> store.frames("2.1"))); // NON-NLS
    }
  }

  @Test
  void codestream_signatures_are_recognised() {
    assertAll(
        () -> assertTrue(FrameIndex.isCodestreamStart(ByteBuffer.wrap(new byte[] {-1, -40, 0, 0}))),
        () ->
            assertTrue(
                FrameIndex.isCodestreamStart(ByteBuffer.wrap(new byte[] {-1, 0x4F, -1, 0x51}))),
        () -> assertTrue(FrameIndex.isCodestreamStart(ByteBuffer.wrap(new byte[] {0, 0, 0, 12}))),
        () ->
            assertTrue(FrameIndex.isCodestreamStart(ByteBuffer.wrap(new byte[] {-1, 0x0A, 0, 0}))),
        () ->
            assertFalse(FrameIndex.isCodestreamStart(ByteBuffer.wrap(new byte[] {9, 10, 11, 12}))));
  }
}
