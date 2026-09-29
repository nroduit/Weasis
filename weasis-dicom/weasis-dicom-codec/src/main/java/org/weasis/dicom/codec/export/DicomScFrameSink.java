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

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.MaskingProfile;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.export.FrameSink;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.codec.TagD;
import org.weasis.opencv.op.ImageConversion;

/**
 * Writes a Multi-frame True Color Secondary Capture, so the animation stays in the study and cines
 * in Weasis and in any PACS. dcm4che has no multi-frame image entry point, so the writer is ours.
 *
 * <p>Frames are encoded as they arrive and appended to a temporary payload, which is copied into
 * the final file once the frame count is known. Nothing but one frame is ever held in memory, which
 * is what makes this the only sink without a length limit.
 */
public class DicomScFrameSink implements FrameSink {

  private static final Logger LOGGER = LoggerFactory.getLogger(DicomScFrameSink.class);

  /** Frame codec, which also decides the transfer syntax and the photometric interpretation. */
  public enum ScCodec {
    JPEG_BASELINE(UID.JPEGBaseline8Bit, ".jpg", "YBR_FULL_422", "ISO_10918_1"), // NON-NLS
    /** Distance 1 is visually lossless, not lossless: the file says so. */
    JPEG_XL(UID.JPEGXL, ".jxl", "RGB", "ISO_18181_1"), // NON-NLS
    UNCOMPRESSED(UID.ExplicitVRLittleEndian, null, "RGB", null); // NON-NLS

    private final String transferSyntax;
    private final String extension;
    private final String photometricInterpretation;
    private final String lossyMethod;

    ScCodec(
        String transferSyntax,
        String extension,
        String photometricInterpretation,
        String lossyMethod) {
      this.transferSyntax = transferSyntax;
      this.extension = extension;
      this.photometricInterpretation = photometricInterpretation;
      this.lossyMethod = lossyMethod;
    }

    public boolean isEncapsulated() {
      return extension != null;
    }

    public boolean isLossy() {
      return lossyMethod != null;
    }
  }

  /** Patient and study identity the new series must share with its source, in tag order. */
  private static final int[] INHERITED_TAGS = {
    Tag.SpecificCharacterSet,
    Tag.StudyDate,
    Tag.StudyTime,
    Tag.AccessionNumber,
    Tag.ReferringPhysicianName,
    Tag.StudyDescription,
    Tag.PatientName,
    Tag.PatientID,
    Tag.PatientBirthDate,
    Tag.PatientSex,
    Tag.StudyInstanceUID,
    Tag.StudyID
  };

  /** Type 2 of the inherited modules: present, even empty, when the source lacks them. */
  private static final int[] TYPE2_TAGS = {
    Tag.StudyDate,
    Tag.StudyTime,
    Tag.AccessionNumber,
    Tag.ReferringPhysicianName,
    Tag.PatientName,
    Tag.PatientID,
    Tag.PatientBirthDate,
    Tag.PatientSex,
    Tag.StudyID
  };

  private final Path file;
  private final Attributes source;
  private final String seriesDescription;
  private final ScCodec codec;
  private final MaskingProfile profile;
  private final boolean burnedInPixels;
  private final List<Integer> frameLengths = new ArrayList<>();
  private final List<Integer> frameDurations = new ArrayList<>();

  private Path payload;
  private OutputStream payloadOut;
  private int width;
  private int height;
  private long rawBytes;
  private long encodedBytes;

  /**
   * @param profile the profile the frames were captured under, applied to the inherited header so
   *     the file never carries more identity than its pixels; {@code null} keeps the source values
   * @param burnedInPixels whether the source pixels carry identity that no profile removes
   */
  public DicomScFrameSink(
      Path file,
      Attributes source,
      String seriesDescription,
      ScCodec codec,
      MaskingProfile profile,
      boolean burnedInPixels) {
    this.file = Objects.requireNonNull(file);
    this.source = source;
    this.seriesDescription = seriesDescription;
    this.codec = Objects.requireNonNull(codec);
    this.profile = profile;
    this.burnedInPixels = burnedInPixels;
  }

  @Override
  public void open(int width, int height) throws IOException {
    this.width = width;
    this.height = height;
    this.frameLengths.clear();
    this.frameDurations.clear();
    this.rawBytes = 0;
    this.encodedBytes = 0;
    this.payload = Files.createTempFile("weasis-sc", ".raw"); // NON-NLS
    this.payloadOut = new BufferedOutputStream(Files.newOutputStream(payload));
  }

  @Override
  public void write(BufferedImage frame, int durationMs) throws IOException {
    byte[] data = encode(frame);
    payloadOut.write(data);
    int length = data.length;
    if (codec.isEncapsulated() && (length & 1) == 1) {
      payloadOut.write(0);
      length++;
    }
    frameLengths.add(length);
    frameDurations.add(Math.max(1, durationMs));
    rawBytes += (long) width * height * 3;
    encodedBytes += data.length;
  }

  private byte[] encode(BufferedImage frame) throws IOException {
    if (!codec.isEncapsulated()) {
      return toRgbBytes(frame);
    }
    Mat mat = ImageConversion.toMat(frame);
    try {
      return compress(mat);
    } finally {
      ImageConversion.releaseMat(mat);
    }
  }

  private byte[] compress(Mat bgr) throws IOException {
    MatOfByte buffer = new MatOfByte();
    try {
      if (!Imgcodecs.imencode(codec.extension, bgr, buffer, encoderParams())) {
        throw new IOException("Cannot encode the frame as " + codec.extension);
      }
      return buffer.toArray();
    } finally {
      ImageConversion.releaseMat(buffer);
    }
  }

  private MatOfInt encoderParams() {
    if (codec == ScCodec.JPEG_XL) {
      return new MatOfInt(Imgcodecs.IMWRITE_JPEGXL_DISTANCE, 1);
    }
    return new MatOfInt(
        Imgcodecs.IMWRITE_JPEG_QUALITY,
        90,
        Imgcodecs.IMWRITE_JPEG_SAMPLING_FACTOR,
        Imgcodecs.IMWRITE_JPEG_SAMPLING_FACTOR_422);
  }

  /**
   * The captured frames are already 8-bit interleaved BGR, so the uncompressed form is one channel
   * swap away from the RGB the attribute declares.
   */
  static byte[] toRgbBytes(BufferedImage frame) {
    byte[] bgr = ((DataBufferByte) frame.getRaster().getDataBuffer()).getData();
    byte[] rgb = new byte[bgr.length];
    for (int i = 0; i + 2 < bgr.length; i += 3) {
      rgb[i] = bgr[i + 2];
      rgb[i + 1] = bgr[i + 1];
      rgb[i + 2] = bgr[i];
    }
    return rgb;
  }

  @Override
  public void close() throws IOException {
    closePayload();
    if (frameLengths.isEmpty()) {
      throw new IOException("No frame to write");
    }
    try {
      Attributes attributes = buildAttributes();
      try (DicomOutputStream dos = new DicomOutputStream(file.toFile())) {
        dos.writeDataset(attributes.createFileMetaInformation(codec.transferSyntax), attributes);
        writePixelData(dos);
        dos.finish();
      }
    } finally {
      FileUtil.delete(payload);
      payload = null;
    }
  }

  private void writePixelData(DicomOutputStream dos) throws IOException {
    try (InputStream in = Files.newInputStream(payload)) {
      if (codec.isEncapsulated()) {
        dos.writeHeader(Tag.PixelData, VR.OB, -1);
        dos.writeHeader(Tag.Item, null, 0);
        for (int length : frameLengths) {
          dos.writeHeader(Tag.Item, null, length);
          copy(in, dos, length);
        }
        dos.writeHeader(Tag.SequenceDelimitationItem, null, 0);
      } else {
        long total = 0;
        for (int length : frameLengths) {
          total += length;
        }
        if (total > Integer.MAX_VALUE) {
          throw new IOException(
              "Uncompressed pixel data exceeds what a single DICOM element can hold");
        }
        dos.writeHeader(Tag.PixelData, VR.OB, (int) total);
        copy(in, dos, total);
      }
    }
  }

  private static void copy(InputStream in, OutputStream out, long length) throws IOException {
    byte[] buffer = new byte[64 * 1024];
    long remaining = length;
    while (remaining > 0) {
      int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
      if (read < 0) {
        throw new IOException("Truncated frame payload");
      }
      out.write(buffer, 0, read);
      remaining -= read;
    }
  }

  private void closePayload() {
    if (payloadOut != null) {
      try {
        payloadOut.close();
      } catch (IOException e) {
        LOGGER.error("Cannot close the frame payload", e);
      }
      payloadOut = null;
    }
  }

  private Attributes buildAttributes() {
    Attributes attributes = inheritedHeader();
    if (!attributes.containsValue(Tag.StudyInstanceUID)) {
      attributes.setString(Tag.StudyInstanceUID, VR.UI, UIDUtils.createUID());
    }
    for (int tag : TYPE2_TAGS) {
      if (!attributes.contains(tag)) {
        attributes.setNull(tag, ElementDictionary.vrOf(tag, null));
      }
    }
    attributes.setString(
        Tag.SOPClassUID, VR.UI, UID.MultiFrameTrueColorSecondaryCaptureImageStorage);
    attributes.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
    attributes.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
    attributes.setString(Tag.Modality, VR.CS, "OT"); // NON-NLS
    attributes.setString(Tag.ConversionType, VR.CS, "WSD"); // NON-NLS
    attributes.setString(Tag.SeriesDescription, VR.LO, seriesDescription);
    attributes.setInt(Tag.SeriesNumber, VR.IS, 99);
    attributes.setInt(Tag.InstanceNumber, VR.IS, 1);
    attributes.setString(Tag.ImageType, VR.CS, "DERIVED", "SECONDARY"); // NON-NLS
    attributes.setString(
        Tag.DerivationDescription,
        VR.ST,
        "Rendered by "
            + AppProperties.WEASIS_NAME
            + " "
            + AppProperties.WEASIS_VERSION
            + ": " // NON-NLS
            + seriesDescription);
    attributes.setNull(Tag.PatientOrientation, VR.CS);
    setEquipment(attributes);
    Date now = new Date();
    attributes.setDate(Tag.ContentDateAndTime, now);
    attributes.setDate(Tag.SeriesDateAndTime, now);

    attributes.setInt(Tag.Rows, VR.US, height);
    attributes.setInt(Tag.Columns, VR.US, width);
    attributes.setInt(Tag.SamplesPerPixel, VR.US, 3);
    attributes.setString(Tag.PhotometricInterpretation, VR.CS, codec.photometricInterpretation);
    attributes.setInt(Tag.PlanarConfiguration, VR.US, 0);
    attributes.setInt(Tag.BitsAllocated, VR.US, 8);
    attributes.setInt(Tag.BitsStored, VR.US, 8);
    attributes.setInt(Tag.HighBit, VR.US, 7);
    attributes.setInt(Tag.PixelRepresentation, VR.US, 0);
    attributes.setInt(Tag.NumberOfFrames, VR.IS, frameLengths.size());
    attributes.setString(Tag.BurnedInAnnotation, VR.CS, burnedInAnnotation()); // NON-NLS
    attributes.setString(Tag.PresentationLUTShape, VR.CS, "IDENTITY"); // NON-NLS
    setCompression(attributes, codec, rawBytes, encodedBytes);

    setTiming(attributes);
    return attributes;
  }

  /**
   * The inherited identity, under the same profile as the pixels. A tag the profile removes is left
   * out (the type 2 backfill adds it empty), a cleared one is written empty, a pseudonymized or
   * shifted one is rewritten in its VR; a UID that may not stay is replaced by a fresh one, since a
   * pseudonym is not a UID.
   */
  private Attributes inheritedHeader() {
    Attributes attributes = new Attributes();
    if (source == null) {
      return attributes;
    }
    if (profile == null) {
      attributes.addSelected(source, INHERITED_TAGS);
      return attributes;
    }
    IdentityMask mask = IdentityMask.forProfile(profile);
    for (int tag : INHERITED_TAGS) {
      TagW tagW = TagD.getNullable(tag, null);
      if (!source.containsValue(tag) || tagW == null) {
        attributes.addSelected(source, tag);
        continue;
      }
      VR vr = source.getVR(tag);
      switch (mask.actionFor(tagW)) {
        case KEEP -> attributes.addSelected(source, tag);
        case REMOVE -> {}
        case CLEAR -> attributes.setNull(tag, vr);
        default -> {
          if (vr == VR.UI) {
            attributes.setString(tag, vr, UIDUtils.createUID());
          } else {
            setMasked(attributes, tag, vr, mask.apply(tagW, tagW.getValue(source)));
          }
        }
      }
    }
    return attributes;
  }

  /** A masked value that is no longer of the VR's kind — a pseudonymized date — is hidden. */
  private static void setMasked(Attributes attributes, int tag, VR vr, Object value) {
    String text =
        switch (value) {
          case LocalDate date when vr == VR.DA -> TagD.formatDicomDate(date);
          case LocalTime time when vr == VR.TM -> TagD.formatDicomTime(time);
          case LocalDateTime dateTime when vr == VR.DT ->
              TagD.formatDicomDate(dateTime.toLocalDate())
                  + TagD.formatDicomTime(dateTime.toLocalTime());
          case String s when vr != VR.DA && vr != VR.TM && vr != VR.DT -> s;
          case null, default -> null;
        };
    if (text == null) {
      attributes.setNull(tag, vr);
    } else {
      attributes.setString(tag, vr, text);
    }
  }

  /**
   * PS3.3 C.8.6.3, type 1: the frames carry the corner annotations, which name the patient. They
   * stop doing so once a profile that hides direct identifiers was applied to the capture — unless
   * the source pixels themselves carry identity, which no profile touches.
   */
  private String burnedInAnnotation() {
    boolean masked = profile != null && profile.hidesDirectIdentifiers();
    return masked && !burnedInPixels ? "NO" : "YES"; // NON-NLS
  }

  /** General Equipment (Manufacturer is type 2) and the SC Equipment device attributes. */
  private static void setEquipment(Attributes attributes) {
    String name = AppProperties.WEASIS_NAME;
    String version = AppProperties.WEASIS_VERSION;
    attributes.setString(Tag.Manufacturer, VR.LO, name);
    attributes.setString(Tag.ManufacturerModelName, VR.LO, name);
    attributes.setString(Tag.SoftwareVersions, VR.LO, version);
    attributes.setString(Tag.SecondaryCaptureDeviceManufacturer, VR.LO, name);
    attributes.setString(Tag.SecondaryCaptureDeviceManufacturerModelName, VR.LO, name);
    attributes.setString(Tag.SecondaryCaptureDeviceSoftwareVersions, VR.LO, version);
  }

  /**
   * The lossy indication a BIR viewer displays (PS3.3 C.7.6.1.1.5): flag, method and the ratio of
   * the raw frames to their encoded size.
   */
  static void setCompression(
      Attributes attributes, ScCodec codec, long rawBytes, long encodedBytes) {
    if (!codec.isLossy()) {
      attributes.setString(Tag.LossyImageCompression, VR.CS, "00"); // NON-NLS
      return;
    }
    attributes.setString(Tag.LossyImageCompression, VR.CS, "01"); // NON-NLS
    attributes.setString(Tag.LossyImageCompressionMethod, VR.CS, codec.lossyMethod);
    double ratio = encodedBytes <= 0 ? 1.0 : rawBytes / (double) encodedBytes;
    attributes.setDouble(Tag.LossyImageCompressionRatio, VR.DS, Math.round(ratio * 100) / 100.0);
  }

  /**
   * A recording samples what the user does, so its frames rarely last the same time; the vector
   * form keeps the real intervals instead of averaging them away.
   */
  private void setTiming(Attributes attributes) {
    int first = frameDurations.getFirst();
    boolean uniform = frameDurations.stream().allMatch(d -> d == first);
    if (uniform) {
      attributes.setInt(Tag.FrameIncrementPointer, VR.AT, Tag.FrameTime);
      attributes.setDouble(Tag.FrameTime, VR.DS, first);
    } else {
      attributes.setInt(Tag.FrameIncrementPointer, VR.AT, Tag.FrameTimeVector);
      attributes.setDouble(
          Tag.FrameTimeVector,
          VR.DS,
          frameDurations.stream().mapToDouble(Integer::doubleValue).toArray());
    }
    int rate = (int) Math.round(1000.0 / first);
    attributes.setInt(Tag.CineRate, VR.IS, rate);
    attributes.setInt(Tag.RecommendedDisplayFrameRate, VR.IS, rate);
  }

  @Override
  public void abort() {
    closePayload();
    if (payload != null) {
      FileUtil.delete(payload);
      payload = null;
    }
    FileUtil.delete(file);
  }
}
