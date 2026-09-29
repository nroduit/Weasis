/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.export;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.media.data.MaskingProfile;
import org.weasis.dicom.codec.export.DicomScFrameSink.ScCodec;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

/**
 * The uncompressed codec needs no native encoder, so the writer itself — attributes, frame count,
 * timing and pixel layout — can be checked by reading the file back.
 */
@DisplayName("DICOM Secondary Capture sink")
class DicomScFrameSinkTest {

  @BeforeAll
  static void classify() {
    DicomMediaUtils.classifyTags();
  }

  private static BufferedImage frame(int gray) {
    BufferedImage image = new BufferedImage(4, 2, BufferedImage.TYPE_3BYTE_BGR);
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        image.setRGB(x, y, new java.awt.Color(gray, gray / 2, 0).getRGB());
      }
    }
    return image;
  }

  private static Attributes source() {
    Attributes attributes = new Attributes();
    attributes.setString(Tag.PatientName, VR.PN, "DOE^JANE");
    attributes.setString(Tag.PatientID, VR.LO, "12345");
    attributes.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.4.5");
    attributes.setString(Tag.StudyDate, VR.DA, "20260101");
    attributes.setString(Tag.PatientBirthDate, VR.DA, "19700315");
    attributes.setString(Tag.AccessionNumber, VR.SH, "ACC-7");
    return attributes;
  }

  private static Attributes write(Path file, int[] durations) throws IOException {
    return write(file, durations, null, false);
  }

  private static Attributes write(
      Path file, int[] durations, MaskingProfile profile, boolean burnedInPixels)
      throws IOException {
    DicomScFrameSink sink =
        new DicomScFrameSink(
            file, source(), "3D rotation", ScCodec.UNCOMPRESSED, profile, burnedInPixels);
    sink.open(4, 2);
    for (int i = 0; i < durations.length; i++) {
      sink.write(frame(200 - i * 10), durations[i]);
    }
    sink.close();
    try (DicomInputStream in = new DicomInputStream(file.toFile())) {
      return in.readDataset();
    }
  }

  @Test
  @DisplayName("keeps the patient and the study of the source series")
  void inheritsIdentity(@TempDir Path dir) throws IOException {
    Attributes attributes = write(dir.resolve("a.dcm"), new int[] {40, 40, 40});
    assertAll(
        () -> assertEquals("DOE^JANE", attributes.getString(Tag.PatientName)),
        () -> assertEquals("12345", attributes.getString(Tag.PatientID)),
        () -> assertEquals("1.2.3.4.5", attributes.getString(Tag.StudyInstanceUID)),
        () -> assertEquals("20260101", attributes.getString(Tag.StudyDate)),
        () ->
            assertEquals(
                UID.MultiFrameTrueColorSecondaryCaptureImageStorage,
                attributes.getString(Tag.SOPClassUID)),
        () -> assertEquals("3D rotation", attributes.getString(Tag.SeriesDescription)));
  }

  @Test
  @DisplayName("describes a true colour multi-frame image")
  void describesThePixels(@TempDir Path dir) throws IOException {
    Attributes attributes = write(dir.resolve("b.dcm"), new int[] {40, 40, 40});
    assertAll(
        () -> assertEquals(2, attributes.getInt(Tag.Rows, 0)),
        () -> assertEquals(4, attributes.getInt(Tag.Columns, 0)),
        () -> assertEquals(3, attributes.getInt(Tag.NumberOfFrames, 0)),
        () -> assertEquals(3, attributes.getInt(Tag.SamplesPerPixel, 0)),
        () -> assertEquals("RGB", attributes.getString(Tag.PhotometricInterpretation)),
        () -> assertEquals(8, attributes.getInt(Tag.BitsAllocated, 0)),
        () -> assertEquals(0, attributes.getInt(Tag.PlanarConfiguration, -1)),
        () -> assertEquals(3 * 4 * 2 * 3, attributes.getBytes(Tag.PixelData).length));
  }

  @Test
  @DisplayName("states a single frame time when every frame lasts the same")
  void uniformTiming(@TempDir Path dir) throws IOException {
    Attributes attributes = write(dir.resolve("c.dcm"), new int[] {40, 40, 40});
    assertAll(
        () -> assertEquals(40.0, attributes.getDouble(Tag.FrameTime, 0), 0.001),
        () -> assertEquals(25, attributes.getInt(Tag.CineRate, 0)),
        () -> assertFalse(attributes.containsValue(Tag.FrameTimeVector)));
  }

  @Test
  @DisplayName("keeps the real intervals of a recording in a frame time vector")
  void variableTiming(@TempDir Path dir) throws IOException {
    Attributes attributes = write(dir.resolve("d.dcm"), new int[] {80, 240, 80});
    assertAll(
        () -> assertTrue(attributes.containsValue(Tag.FrameTimeVector)),
        () ->
            assertArrayEquals(
                new double[] {80, 240, 80}, attributes.getDoubles(Tag.FrameTimeVector), 0.001),
        () -> assertFalse(attributes.containsValue(Tag.FrameTime)));
  }

  @Test
  @DisplayName("carries the mandatory SC, equipment and cine attributes")
  void mandatoryAttributes(@TempDir Path dir) throws IOException {
    Attributes attributes = write(dir.resolve("f.dcm"), new int[] {40, 40, 40});
    assertAll(
        () -> assertEquals("YES", attributes.getString(Tag.BurnedInAnnotation)),
        () -> assertEquals("IDENTITY", attributes.getString(Tag.PresentationLUTShape)),
        () -> assertEquals("00", attributes.getString(Tag.LossyImageCompression)),
        () -> assertEquals("WSD", attributes.getString(Tag.ConversionType)),
        () -> assertEquals(AppProperties.WEASIS_NAME, attributes.getString(Tag.Manufacturer)),
        () ->
            assertEquals(
                AppProperties.WEASIS_VERSION,
                attributes.getString(Tag.SecondaryCaptureDeviceSoftwareVersions)),
        () -> assertTrue(attributes.contains(Tag.PatientOrientation)),
        () -> assertFalse(attributes.containsValue(Tag.PatientOrientation)),
        () -> assertEquals(25, attributes.getInt(Tag.RecommendedDisplayFrameRate, 0)),
        () -> assertTrue(attributes.getString(Tag.DerivationDescription).endsWith("3D rotation")));
  }

  @Test
  @DisplayName("backfills the type 2 attributes the source series lacks, empty")
  void type2Backfill(@TempDir Path dir) throws IOException {
    Attributes attributes = write(dir.resolve("g.dcm"), new int[] {40});
    assertAll(
        () -> assertTrue(attributes.contains(Tag.PatientSex)),
        () -> assertFalse(attributes.containsValue(Tag.PatientSex)),
        () -> assertTrue(attributes.contains(Tag.AccessionNumber)),
        () -> assertTrue(attributes.contains(Tag.ReferringPhysicianName)),
        () -> assertTrue(attributes.contains(Tag.StudyTime)),
        () -> assertEquals("20260101", attributes.getString(Tag.StudyDate)));
  }

  @Test
  @DisplayName("states the lossy method and ratio for an encapsulated codec")
  void lossyIndication() {
    Attributes jpeg = new Attributes();
    DicomScFrameSink.setCompression(jpeg, ScCodec.JPEG_BASELINE, 3_000_000, 200_000);
    Attributes jxl = new Attributes();
    DicomScFrameSink.setCompression(jxl, ScCodec.JPEG_XL, 100, 30);
    assertAll(
        () -> assertEquals("01", jpeg.getString(Tag.LossyImageCompression)),
        () -> assertEquals("ISO_10918_1", jpeg.getString(Tag.LossyImageCompressionMethod)),
        () -> assertEquals(15.0, jpeg.getDouble(Tag.LossyImageCompressionRatio, 0), 0.001),
        () -> assertEquals("ISO_18181_1", jxl.getString(Tag.LossyImageCompressionMethod)),
        () -> assertEquals(3.33, jxl.getDouble(Tag.LossyImageCompressionRatio, 0), 0.001));
  }

  @Test
  @DisplayName("masks the inherited header under the profile of the capture")
  void maskedHeader(@TempDir Path dir) throws IOException {
    Attributes attributes =
        write(dir.resolve("h.dcm"), new int[] {40}, MaskingProfile.display(), false);
    assertAll(
        () -> assertTrue(attributes.getString(Tag.PatientName).startsWith("ANONYMOUS^")),
        () -> assertNotEquals("12345", attributes.getString(Tag.PatientID)),
        () -> assertTrue(attributes.getString(Tag.StudyInstanceUID).matches("[0-9.]+")),
        () -> assertTrue(attributes.contains(Tag.PatientBirthDate)),
        () -> assertNotEquals("19700315", attributes.getString(Tag.PatientBirthDate, "")),
        () -> assertEquals("NO", attributes.getString(Tag.BurnedInAnnotation)));
  }

  @Test
  @DisplayName("drops the dates a publication profile removes and keeps the type 2 slots")
  void publicationHeader(@TempDir Path dir) throws IOException {
    Attributes attributes =
        write(dir.resolve("i.dcm"), new int[] {40}, MaskingProfile.publication(), false);
    assertAll(
        () -> assertTrue(attributes.contains(Tag.StudyDate)),
        () -> assertNotEquals("20260101", attributes.getString(Tag.StudyDate, "")),
        () -> assertNotEquals("ACC-7", attributes.getString(Tag.AccessionNumber, "")),
        () -> assertTrue(attributes.contains(Tag.AccessionNumber)));
  }

  @Test
  @DisplayName("keeps the burned-in flag when the source pixels carry identity")
  void burnedInPixelsWin(@TempDir Path dir) throws IOException {
    Attributes attributes =
        write(dir.resolve("j.dcm"), new int[] {40}, MaskingProfile.display(), true);
    assertEquals("YES", attributes.getString(Tag.BurnedInAnnotation));
  }

  @Test
  @DisplayName("swaps the captured BGR bytes into the declared RGB order")
  void rgbConversion() {
    BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR);
    image.setRGB(0, 0, new java.awt.Color(10, 20, 30).getRGB());
    assertArrayEquals(new byte[] {10, 20, 30}, DicomScFrameSink.toRgbBytes(image));
  }

  @Test
  @DisplayName("refuses to write a file with no frame and leaves nothing behind")
  void emptyAnimation(@TempDir Path dir) throws IOException {
    Path file = dir.resolve("e.dcm");
    DicomScFrameSink sink =
        new DicomScFrameSink(file, source(), "empty", ScCodec.UNCOMPRESSED, null, false);
    sink.open(4, 2);
    assertThrows(IOException.class, sink::close);
    assertFalse(Files.exists(file));
  }
}
