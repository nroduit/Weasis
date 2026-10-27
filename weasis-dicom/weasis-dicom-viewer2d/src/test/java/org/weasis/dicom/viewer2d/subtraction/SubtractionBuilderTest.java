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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.viewer2d.mpr.DerivedStack;
import org.weasis.dicom.viewer2d.mpr.RawImageIO;
import org.weasis.dicom.viewer2d.subtraction.SubtractionBuilder.Result;
import org.weasis.dicom.viewer2d.subtraction.SubtractionCompatibility.Pairing;
import org.weasis.opencv.data.FileRawImage;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

/** The derived series holds the real difference of its operands and says where it comes from. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SubtractionBuilderTest {

  private static final int SIZE = 8;
  private static final String POST = "1.2.3.10"; // NON-NLS
  private static final String PRE = "1.2.3.11"; // NON-NLS

  private final List<Result> results = new ArrayList<>();

  @BeforeAll
  static void loadOpenCv() {
    assumeTrue(OpenCv.tryLoad(), "OpenCV native library unavailable, skipping");
  }

  @AfterEach
  void deleteDerived() {
    results.forEach(r -> FileUtil.delete(r.cacheDir()));
  }

  @Test
  void stored_values_read_back_as_the_real_difference(@TempDir Path dir) throws IOException {
    DicomSeries post = series(dir, POST, "T1 post", 5, 3, (z, i) -> 1000 + 10 * z + i, 1.0);
    DicomSeries pre = series(dir, PRE, "T1 pre", 6, 3, (z, i) -> 400 + i, 1.0);
    Result result = build(SubtractionSpec.of(POST, PRE), post, pre);

    assertAll(
        () -> assertEquals(3, result.slices()),
        () -> assertEquals(0, result.clipped()),
        () -> assertEquals(3, result.series().size(null)),
        () -> assertEquals("SUB", result.series().getTagValue(TagW.Derivation)));
    List<DicomImageElement> slices = result.series().copyOfMedias(null, null);
    for (int z = 0; z < 3; z++) {
      double[] values = values(slices.get(z));
      for (int i = 0; i < values.length; i++) {
        assertEquals(600 + 10 * z, values[i], 1e-6, "slice " + z + " pixel " + i);
      }
    }
  }

  @Test
  void the_rescale_slope_of_the_series_is_kept_so_a_stored_difference_is_exact(@TempDir Path dir)
      throws IOException {
    // Wide ranges: the modality LUT of the codec saturates a narrow range on an 8-bit table
    DicomSeries post = series(dir, POST, "post", 5, 1, (z, i) -> 1000 + 50 * i, 2.0);
    DicomSeries pre = series(dir, PRE, "pre", 6, 1, (z, i) -> 300 + 40 * i, 2.0);
    Result result = build(SubtractionSpec.of(POST, PRE), post, pre);
    DicomImageElement slice = result.series().copyOfMedias(null, null).getFirst();
    Attributes attributes = slice.getMediaReader().getDicomObject();
    double[] values = values(slice);
    assertAll(
        () -> assertEquals(2.0, attributes.getDouble(Tag.RescaleSlope, 0)),
        () -> assertEquals(0.0, attributes.getDouble(Tag.RescaleIntercept, 1)),
        () -> assertEquals(1, attributes.getInt(Tag.PixelRepresentation, 0)),
        () -> assertEquals(0, result.clipped()));
    for (int i = 0; i < values.length; i++) {
      // real values 2*(1000+50i) - 2*(300+40i) = 1400 + 20i, stored 700 + 10i with slope 2
      assertEquals(1400 + 20 * i, values[i], 1e-6, "pixel " + i);
    }
  }

  @Test
  void factor_offset_and_absolute_value_apply_and_clipping_is_counted(@TempDir Path dir)
      throws IOException {
    DicomSeries post = series(dir, POST, "post", 5, 1, (z, i) -> 400, 1.0);
    DicomSeries pre = series(dir, PRE, "pre", 6, 1, (z, i) -> 1000, 1.0);
    Result weighted =
        build(new SubtractionSpec(POST, PRE, 0.5, 100, false, false), post, pre); // 400-500+100
    Result absolute = build(new SubtractionSpec(POST, PRE, 1, 0, true, false), post, pre);

    DicomSeries bright = series(dir, "1.2.3.12", "bright", 7, 1, (z, i) -> 40000, 1.0);
    Result clipped = build(SubtractionSpec.of("1.2.3.12", PRE), bright, pre);
    assertAll(
        () -> assertEquals(0, values(first(weighted))[0], 1e-6),
        () -> assertEquals(600, values(first(absolute))[0], 1e-6),
        () -> assertEquals((long) SIZE * SIZE, clipped.clipped()),
        () -> assertEquals(Short.MAX_VALUE, values(first(clipped))[0], 1e-6));
  }

  @Test
  void the_derived_instances_name_their_sources_and_the_derivation(@TempDir Path dir)
      throws IOException {
    DicomSeries post = series(dir, POST, "T1 FS post", 5, 2, (z, i) -> 1000 + i, 1.0);
    DicomSeries pre = series(dir, PRE, "T1 FS pre", 6, 2, (z, i) -> 400, 1.0);
    SubtractionSpec spec = SubtractionSpec.of(POST, PRE);
    Result result = build(spec, post, pre);
    DicomImageElement slice = result.series().copyOfMedias(null, null).getFirst();
    Attributes attributes = slice.getMediaReader().getDicomObject();
    DicomImageElement source = post.copyOfMedias(null, null).getFirst();

    assertAll(
        () ->
            assertEquals("SUB T1 FS post - T1 FS pre", attributes.getString(Tag.SeriesDescription)),
        () ->
            assertArrayEquals(
                new String[] {"DERIVED", "SECONDARY", "M", "SUBTRACTION"},
                attributes.getStrings(Tag.ImageType)),
        () -> assertEquals(1005, attributes.getInt(Tag.SeriesNumber, 0)),
        () -> assertEquals(spec.derivedSeriesUid(), attributes.getString(Tag.SeriesInstanceUID)),
        () ->
            assertEquals(
                spec.derivedInstanceUid(TagD.getTagValue(source, Tag.SOPInstanceUID, String.class)),
                attributes.getString(Tag.SOPInstanceUID)),
        () -> assertEquals(SyntheticSeries.FRAME, attributes.getString(Tag.FrameOfReferenceUID)),
        () ->
            assertArrayEquals(
                TagD.getTagValue(source, Tag.ImagePositionPatient, double[].class),
                attributes.getDoubles(Tag.ImagePositionPatient)),
        () ->
            assertEquals(
                "113062",
                attributes.getNestedDataset(Tag.DerivationCodeSequence).getString(Tag.CodeValue)),
        () -> assertEquals(2, attributes.getSequence(Tag.SourceImageSequence).size()),
        () ->
            assertEquals(
                TagD.getTagValue(source, Tag.SOPInstanceUID, String.class),
                attributes
                    .getSequence(Tag.SourceImageSequence)
                    .getFirst()
                    .getString(Tag.ReferencedSOPInstanceUID)),
        () -> assertTrue(attributes.getString(Tag.DerivationDescription).contains("k=1.00")),
        () -> assertEquals("MONOCHROME2", attributes.getString(Tag.PhotometricInterpretation)));
  }

  @Test
  void the_default_window_runs_from_black_to_the_99th_percentile(@TempDir Path dir)
      throws IOException {
    // Differences from 0 to 63 on each slice: p1 = 1, p99 = 62
    DicomSeries post = series(dir, POST, "post", 5, 3, (z, i) -> 1000 + i, 1.0);
    DicomSeries pre = series(dir, PRE, "pre", 6, 3, (z, i) -> 1000, 1.0);
    Result plain = build(SubtractionSpec.of(POST, PRE), post, pre);
    Result signed = build(new SubtractionSpec(POST, PRE, 1, 0, false, true), post, pre);
    Attributes attributes = first(plain).getMediaReader().getDicomObject();
    Attributes signedAttributes = first(signed).getMediaReader().getDicomObject();
    assertAll(
        () -> assertEquals(1, plain.percentiles().low(), 1e-6),
        () -> assertEquals(62, plain.percentiles().high(), 1e-6),
        () -> assertArrayEquals(new double[] {31, 31.5}, attributes.getDoubles(Tag.WindowCenter)),
        () -> assertArrayEquals(new double[] {62, 61}, attributes.getDoubles(Tag.WindowWidth)),
        () ->
            assertArrayEquals(
                new String[] {"Subtraction", "Signed"},
                attributes.getStrings(Tag.WindowCenterWidthExplanation)),
        () -> assertEquals("LINEAR_EXACT", attributes.getString(Tag.VOILUTFunction)),
        () ->
            assertArrayEquals(
                new String[] {"Signed", "Subtraction"},
                signedAttributes.getStrings(Tag.WindowCenterWidthExplanation)),
        () ->
            assertNotEquals(
                plain.series().getTagValue(TagD.get(Tag.SeriesInstanceUID)),
                signed.series().getTagValue(TagD.get(Tag.SeriesInstanceUID))));
  }

  @Test
  void the_same_spec_names_the_same_series_twice(@TempDir Path dir) throws IOException {
    DicomSeries post = series(dir, POST, "post", 5, 2, (z, i) -> 1000, 1.0);
    DicomSeries pre = series(dir, PRE, "pre", 6, 2, (z, i) -> 400, 1.0);
    Result once = build(SubtractionSpec.of(POST, PRE), post, pre);
    Object uid = once.series().getTagValue(TagD.get(Tag.SeriesInstanceUID));
    String firstInstance = TagD.getTagValue(first(once), Tag.SOPInstanceUID, String.class);
    Result twice = build(SubtractionSpec.of(POST, PRE), post, pre);
    assertAll(
        () -> assertEquals(uid, twice.series().getTagValue(TagD.get(Tag.SeriesInstanceUID))),
        () ->
            assertEquals(
                firstInstance, TagD.getTagValue(first(twice), Tag.SOPInstanceUID, String.class)),
        () ->
            assertNotEquals(
                uid,
                build(SubtractionSpec.of(PRE, POST), pre, post)
                    .series()
                    .getTagValue(TagD.get(Tag.SeriesInstanceUID))));
  }

  private Result build(SubtractionSpec spec, DicomSeries series, DicomSeries mask)
      throws IOException {
    Pairing pairing = (Pairing) SubtractionCompatibility.pair(series, mask);
    assertNotNull(pairing, "the operands pair on the direct path");
    Result result = SubtractionBuilder.build(spec, series, mask, pairing);
    results.add(result);
    return result;
  }

  private static DicomImageElement first(Result result) {
    return result.series().copyOfMedias(null, null).getFirst();
  }

  /** The modality-LUT values of a derived slice, row by row. */
  private static double[] values(DicomImageElement slice) {
    PlanarImage image = slice.getUncachedModalityLutImage(null);
    assertNotNull(image, "the derived slice reads back");
    Mat doubles = new Mat();
    image.toMat().convertTo(doubles, CvType.CV_64F);
    double[] values = new double[(int) doubles.total()];
    doubles.get(0, 0, values);
    doubles.release();
    image.release();
    return values;
  }

  @FunctionalInterface
  private interface Pixel {
    int value(int slice, int index);
  }

  /** An MR series of 16-bit unsigned slices 2 mm apart, written as raw images. */
  private static DicomSeries series(
      Path dir,
      String seriesUid,
      String description,
      int seriesNumber,
      int count,
      Pixel pixel,
      double slope)
      throws IOException {
    Path seriesDir = Files.createDirectories(dir.resolve(seriesUid));
    Attributes base = new Attributes();
    base.setString(Tag.SeriesDescription, VR.LO, description);
    base.setInt(Tag.SeriesNumber, VR.IS, seriesNumber);
    base.setString(Tag.ImageType, VR.CS, "ORIGINAL", "PRIMARY", "M", "ND"); // NON-NLS
    base.setDouble(Tag.RescaleSlope, VR.DS, slope);
    base.setDouble(Tag.RescaleIntercept, VR.DS, 0);
    base.setString(Tag.PatientID, VR.LO, "P1"); // NON-NLS
    base.setString(Tag.PatientName, VR.PN, "Doe^Jane"); // NON-NLS
    DicomSeries series = new DicomSeries(seriesUid);
    series.setTag(TagD.get(Tag.Modality), "MR"); // NON-NLS
    series.setTag(TagD.get(Tag.SeriesDescription), description);
    series.setTag(TagD.get(Tag.SeriesNumber), seriesNumber);
    for (int z = 0; z < count; z++) {
      short[] pixels = new short[SIZE * SIZE];
      for (int i = 0; i < pixels.length; i++) {
        pixels[i] = (short) pixel.value(z, i);
      }
      series.addMedia(slice(seriesDir, seriesUid, z, pixels, base, slope));
    }
    return series;
  }

  private static DicomImageElement slice(
      Path dir, String seriesUid, int z, short[] pixels, Attributes base, double slope) {
    ImageCV image = new ImageCV(SIZE, SIZE, CvType.CV_16UC1);
    image.put(0, 0, pixels);
    FileRawImage raw = new FileRawImage(dir.resolve("slice_" + z + ".wcv")); // NON-NLS
    assertTrue(raw.write(image));
    image.release();

    RawImageIO io = new RawImageIO(raw, null);
    io.getFileCache().setOriginalTempFile(raw.path());
    io.setBaseAttributes(base);
    Attributes a =
        SyntheticSeries.slice(seriesUid, "MR", z + 1, 2.0 * z, 1.0, SIZE, SyntheticSeries.FRAME);
    Stream.of(
            Tag.SOPClassUID,
            Tag.SOPInstanceUID,
            Tag.Modality,
            Tag.StudyInstanceUID,
            Tag.SeriesInstanceUID,
            Tag.InstanceNumber,
            Tag.ImageOrientationPatient,
            Tag.ImagePositionPatient,
            Tag.SliceThickness,
            Tag.PixelSpacing,
            Tag.Rows,
            Tag.Columns,
            Tag.FrameOfReferenceUID)
        .forEach(tag -> io.setTag(TagD.get(tag), TagD.get(tag).getValue(a)));
    io.setTag(TagD.get(Tag.SOPClassUID), UID.MRImageStorage);
    io.setTag(TagD.get(Tag.SamplesPerPixel), 1);
    io.setTag(TagD.get(Tag.BitsAllocated), 16);
    io.setTag(TagD.get(Tag.BitsStored), 16);
    io.setTag(TagD.get(Tag.HighBit), 15);
    io.setTag(TagD.get(Tag.PixelRepresentation), 0);
    io.setTag(TagD.get(Tag.PhotometricInterpretation), "MONOCHROME2"); // NON-NLS
    io.setTag(TagD.get(Tag.RescaleSlope), slope);
    io.setTag(TagD.get(Tag.RescaleIntercept), 0.0);
    io.setTag(TagW.MonoChrome, true);
    return DerivedStack.buildDicomImageElement(io);
  }
}
