/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.subtraction;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.io.DicomInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.codec.DicomElement.DicomExportParameters;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.viewer2d.subtraction.SubtractionBuilder.Result;
import org.weasis.dicom.viewer2d.subtraction.SubtractionCompatibility.Pairing;

/** A derived slice written as a DICOM file is an MR image carrying its provenance and values. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SubtractionExportTest {

  private static final String POST = "1.2.3.40"; // NON-NLS
  private static final String PRE = "1.2.3.41"; // NON-NLS

  private Result result;

  @BeforeAll
  static void loadOpenCv() {
    assumeTrue(OpenCv.tryLoad(), "OpenCV native library unavailable, skipping");
  }

  @AfterEach
  void deleteDerived() {
    if (result != null) {
      FileUtil.delete(result.cacheDir());
    }
  }

  @Test
  void a_written_slice_is_a_signed_mr_image_with_its_provenance_and_the_same_values(
      @TempDir Path dir) throws IOException {
    DicomSeries post =
        SubtractionBuilderTest.series(dir, POST, "T1 post", 5, 2, (z, i) -> 1000 + i, 1.0);
    DicomSeries pre = SubtractionBuilderTest.series(dir, PRE, "T1 pre", 6, 2, (z, i) -> 400, 1.0);
    Pairing pairing = (Pairing) SubtractionCompatibility.pair(post, pre);
    result = SubtractionBuilder.build(SubtractionSpec.of(POST, PRE), post, pre, pairing);
    DicomImageElement slice = result.series().copyOfMedias(null, null).getFirst();

    Path file = dir.resolve("sub.dcm"); // NON-NLS
    slice.saveToFile(file.toFile(), new DicomExportParameters(null, false, List.of(), 0, 0));
    assertTrue(Files.size(file) > 2 * 8 * 8, "the file holds the pixel data");

    Attributes dataset;
    try (DicomInputStream in = new DicomInputStream(file.toFile())) {
      dataset = in.readDataset();
    }
    assertAll(
        () -> assertEquals(UID.MRImageStorage, dataset.getString(Tag.SOPClassUID)),
        () -> assertEquals(1, dataset.getInt(Tag.PixelRepresentation, 0)),
        () -> assertEquals(16, dataset.getInt(Tag.BitsAllocated, 0)),
        () -> assertEquals("MONOCHROME2", dataset.getString(Tag.PhotometricInterpretation)),
        () -> assertEquals(1.0, dataset.getDouble(Tag.RescaleSlope, 0)),
        () -> assertEquals("SUB T1 post - T1 pre", dataset.getString(Tag.SeriesDescription)),
        () ->
            assertArrayEquals(
                new String[] {"DERIVED", "SECONDARY", "M", "SUBTRACTION"},
                dataset.getStrings(Tag.ImageType)),
        () ->
            assertEquals(
                "113062",
                dataset.getNestedDataset(Tag.DerivationCodeSequence).getString(Tag.CodeValue)),
        () -> assertEquals(2, dataset.getSequence(Tag.SourceImageSequence).size()),
        () -> assertEquals(2 * 8 * 8, dataset.getBytes(Tag.PixelData).length));

    // The stored values, read as the loader will: signed 16-bit little endian, slope 1
    byte[] pixels = dataset.getBytes(Tag.PixelData);
    ByteBuffer buffer = ByteBuffer.wrap(pixels).order(ByteOrder.LITTLE_ENDIAN);
    double slope = dataset.getDouble(Tag.RescaleSlope, 1);
    double intercept = dataset.getDouble(Tag.RescaleIntercept, 0);
    for (int i = 0; i < pixels.length / 2; i++) {
      assertEquals(600 + i, buffer.getShort(2 * i) * slope + intercept, 1e-6, "pixel " + i);
    }
  }
}
