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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.viewer2d.fusion.FusionVolumeResampler;
import org.weasis.dicom.viewer2d.mip.SeriesBuilder;
import org.weasis.dicom.viewer2d.mpr.DerivedStack;
import org.weasis.dicom.viewer2d.mpr.OwnedVolume;
import org.weasis.dicom.viewer2d.mpr.RawImageIO;
import org.weasis.dicom.viewer2d.mpr.Volume;
import org.weasis.dicom.viewer2d.subtraction.SubtractionCompatibility.Pairing;
import org.weasis.dicom.viewer2d.subtraction.SubtractionCompatibility.SlicePair;
import org.weasis.dicom.viewer2d.subtraction.SubtractionSpec.Dsa;
import org.weasis.opencv.data.FileRawImage;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

/**
 * Computes a subtraction slice by slice on modality-LUT values and writes the result as a derived
 * series: signed 16-bit pixels whose rescale maps back to the real difference, DICOM attributes
 * copied from the series with the derivation recorded, and a default window measured on the middle
 * slice. The slices are written to a cache directory of the derived series, as the MIP does.
 */
public final class SubtractionBuilder {

  public static final Path CACHE_DIR =
      AppProperties.buildAccessibleTempDirectory(AppProperties.CACHE_NAME, "derived"); // NON-NLS

  /** Derivation label of the series and Image Type value 4 of its instances. */
  public static final String DERIVATION = "SUB"; // NON-NLS

  private static final String PIXEL_SUBTRACTION_CODE = "113062"; // NON-NLS
  private static final String SOURCE_FOR_PROCESSING_CODE = "121322"; // NON-NLS
  private static final String DCM = "DCM"; // NON-NLS
  private static final int MAX_DESCRIPTION_LENGTH = 64;

  /** The 1st and 99th percentiles of the real differences of the middle slice. */
  public record Percentiles(double low, double high) {}

  /**
   * The derived series and what its computation reports.
   *
   * @param clipped the pixels whose stored value fell outside the signed 16-bit range
   */
  /**
   * The derived series and what its computation reports.
   *
   * @param clipped the pixels whose stored value fell outside the signed 16-bit range
   * @param resampled whether the mask was resliced onto the grid of the series
   * @param corrected the slices whose mask was moved by the motion correction
   * @param maxShift the largest motion correction in pixels
   */
  public record Result(
      DicomSeries series,
      Path cacheDir,
      int slices,
      long clipped,
      Percentiles percentiles,
      boolean resampled,
      int corrected,
      double maxShift) {

    public String path() {
      return resampled ? "resampled" : "direct"; // NON-NLS
    }
  }

  /**
   * The mask weight that equalizes the mask to the series over a region: the ratio of their mean
   * values over every pair.
   */
  public record Normalization(double factor, double meanSeries, double meanMask, Rect region) {}

  /**
   * Measures the means of the series and of the mask over the region of every pair (the whole slice
   * when null) and returns the weight that equalizes them; one when the mask is empty.
   */
  public static Normalization normalize(
      SubtractionSpec spec, MediaSeries<DicomImageElement> mask, Pairing pairing, Rect region) {
    MaskVolume volume = pairing.resampled() ? MaskVolume.of(mask) : null;
    if (pairing.resampled() && volume == null) {
      throw new IllegalStateException("The volume of the mask cannot be built to resample it");
    }
    MaskSource source = volume != null ? volume : pair -> maskOf(spec, pair);
    double sumSeries = 0;
    double sumMask = 0;
    long pixels = 0;
    try {
      for (SlicePair pair : pairing.slices()) {
        PlanarImage image = pair.image().getUncachedModalityLutImage(null);
        if (image == null) {
          throw new IllegalStateException("A slice of an operand cannot be read");
        }
        Mat a = new Mat();
        Mat b = source.maskOf(pair);
        try {
          image.toMat().convertTo(a, CvType.CV_32F);
          Rect window = region == null ? new Rect(0, 0, a.cols(), a.rows()) : clip(region, a);
          sumSeries += Core.sumElems(a.submat(window)).val[0];
          sumMask += Core.sumElems(b.submat(window)).val[0];
          pixels += window.area();
        } finally {
          a.release();
          b.release();
          image.release();
        }
      }
    } finally {
      if (volume != null) {
        volume.close();
      }
    }
    double meanSeries = pixels == 0 ? 0 : sumSeries / pixels;
    double meanMask = pixels == 0 ? 0 : sumMask / pixels;
    double factor = meanMask == 0 ? 1.0 : meanSeries / meanMask;
    return new Normalization(factor, meanSeries, meanMask, region);
  }

  private static Rect clip(Rect region, Mat image) {
    int x = Math.clamp(region.x, 0, image.cols() - 1);
    int y = Math.clamp(region.y, 0, image.rows() - 1);
    int width = Math.clamp(region.width, 1, image.cols() - x);
    int height = Math.clamp(region.height, 1, image.rows() - y);
    return new Rect(x, y, width, height);
  }

  /** The translation the motion correction applied to a mask slice, in pixels. */
  private record Motion(double rows, double columns) {
    static final Motion NONE = new Motion(0, 0);

    double length() {
      return Math.hypot(rows, columns);
    }
  }

  /** The mask values of a pair come from its mask slices, or from this volume once resampled. */
  @FunctionalInterface
  interface MaskSource {
    Mat maskOf(SlicePair pair);
  }

  /** The reslicing of a mask volume onto the grid of each slice, built once per mask series. */
  public static final class MaskVolume implements MaskSource, AutoCloseable {
    private final OwnedVolume owned;
    private final Volume<?, ?> volume;

    private MaskVolume(OwnedVolume owned) {
      this.owned = owned;
      this.volume = owned.volume();
    }

    /** The volume of the mask series, or null when it cannot be built. */
    public static MaskVolume of(MediaSeries<DicomImageElement> mask) {
      OwnedVolume owned = OwnedVolume.of(mask);
      return owned == null ? null : new MaskVolume(owned);
    }

    @Override
    public Mat maskOf(SlicePair pair) {
      DicomImageElement image = pair.image();
      GeometryOfSlice plane = image.getSliceGeometry();
      Integer columns = TagD.getTagValue(image, Tag.Columns, Integer.class);
      Integer rows = TagD.getTagValue(image, Tag.Rows, Integer.class);
      if (plane == null || columns == null || rows == null) {
        throw new IllegalStateException("A slice has no geometry to reslice the mask onto");
      }
      PlanarImage values = FusionVolumeResampler.resampleToValue(volume, plane, columns, rows);
      if (values == null) {
        throw new IllegalStateException("The mask volume cannot be resliced on the series");
      }
      Mat mask = values.toMat();
      // Outside the mask volume nothing is known: the difference there is zero
      Core.patchNaNs(mask, 0);
      return mask;
    }

    @Override
    public void close() {
      owned.close();
    }
  }

  private SubtractionBuilder() {}

  /** Builds the derived series of the spec from the paired slices of its operands. */
  public static Result build(
      SubtractionSpec spec,
      MediaSeries<DicomImageElement> series,
      MediaSeries<DicomImageElement> mask,
      Pairing pairing)
      throws IOException {
    List<SlicePair> pairs = pairing.slices();
    String seriesUid = spec.derivedSeriesUid();
    Path dir = CACHE_DIR.resolve(seriesUid);
    FileUtil.delete(dir);
    Files.createDirectories(dir);

    int middle = pairs.size() / 2;
    DicomImageElement reference = pairs.get(middle).image();
    Attributes source = reference.getMediaReader().getDicomObject();
    double slope = rescaleSlope(source);
    AtomicLong clipped = new AtomicLong();
    Percentiles[] percentiles = new Percentiles[1];
    FileRawImage[] files = new FileRawImage[pairs.size()];
    MaskVolume volume = pairing.resampled() ? MaskVolume.of(mask) : null;
    if (pairing.resampled() && volume == null) {
      FileUtil.delete(dir);
      throw new IllegalStateException("The volume of the mask cannot be built to resample it");
    }
    MaskSource masks = volume != null ? volume : pair -> maskOf(spec, pair);
    // One mask for every frame of a run: computed once, read by every slice, unless each slice
    // moves its own
    Mat shared =
        volume == null
                && !spec.motionCorrection()
                && pairs.stream().allMatch(p -> p.masks().equals(pairs.getFirst().masks()))
            ? maskOf(spec, pairs.getFirst())
            : null;
    Motion[] motions = new Motion[pairs.size()];
    try {
      IntStream.range(0, pairs.size())
          .parallel()
          .forEach(
              i -> {
                Slice slice = subtract(spec, pairs.get(i), slope, shared, masks);
                clipped.addAndGet(slice.clipped());
                motions[i] = slice.motion();
                if (i == middle) {
                  percentiles[0] = slice.percentiles();
                }
                files[i] =
                    write(
                        slice.stored(),
                        new FileRawImage(dir.resolve("sub_" + i + ".wcv"))); // NON-NLS
              });
    } catch (RuntimeException e) {
      FileUtil.delete(dir);
      throw e;
    } finally {
      if (shared != null) {
        shared.release();
      }
      if (volume != null) {
        volume.close();
      }
    }
    int corrected = (int) Arrays.stream(motions).filter(m -> m != Motion.NONE).count();
    double maxShift = Arrays.stream(motions).mapToDouble(Motion::length).max().orElse(0);

    Attributes base =
        baseAttributes(
            spec,
            source,
            series,
            mask,
            pairs.get(middle).mask(),
            percentiles[0],
            pairing.resampled());
    List<DicomImageElement> images =
        IntStream.range(0, pairs.size())
            .mapToObj(i -> element(spec, pairs.get(i), files[i], base, slope, seriesUid, i))
            .toList();
    DicomSeries derived = new DicomSeries(seriesUid);
    derived.addAll(images);
    images.getFirst().getMediaReader().writeMetaData(derived);
    derived.setTag(TagW.Derivation, DERIVATION);
    return new Result(
        derived,
        dir,
        pairs.size(),
        clipped.get(),
        percentiles[0],
        pairing.resampled(),
        corrected,
        maxShift);
  }

  /**
   * One subtracted slice for a live preview, written as a new file of {@code dir} and numbered as
   * its source slice. The window presets come from {@code window} when given, so every slice of one
   * preview shares them, else from the slice itself.
   */
  public static DicomImageElement preview(
      SubtractionSpec spec,
      MediaSeries<DicomImageElement> series,
      MediaSeries<DicomImageElement> mask,
      SlicePair pair,
      Path dir,
      Percentiles window,
      int instanceNumber,
      MaskVolume volume)
      throws IOException {
    Attributes source = pair.image().getMediaReader().getDicomObject();
    double slope = rescaleSlope(source);
    Slice slice = subtract(spec, pair, slope, null, maskSource(spec, volume));
    FileRawImage raw =
        write(
            slice.stored(), new FileRawImage(Files.createTempFile(dir, "sub_", ".wcv"))); // NON-NLS
    Percentiles percentiles = window == null ? slice.percentiles() : window;
    Attributes base =
        baseAttributes(spec, source, series, mask, pair.mask(), percentiles, volume != null);
    return element(spec, pair, raw, base, slope, spec.derivedSeriesUid(), instanceNumber - 1);
  }

  /** The window percentiles of one pair, the slice computed and dropped: what a preview shares. */
  public static Percentiles percentiles(SubtractionSpec spec, SlicePair pair, MaskVolume volume) {
    Attributes source = pair.image().getMediaReader().getDicomObject();
    Slice slice = subtract(spec, pair, rescaleSlope(source), null, maskSource(spec, volume));
    slice.stored().release();
    return slice.percentiles();
  }

  private static MaskSource maskSource(SubtractionSpec spec, MaskVolume volume) {
    return volume != null ? volume : pair -> maskOf(spec, pair);
  }

  private record Slice(ImageCV stored, long clipped, Percentiles percentiles, Motion motion) {}

  /**
   * The difference of one pair; {@code shared} is the mask already computed for it, if any, else
   * the source gives it.
   */
  private static Slice subtract(
      SubtractionSpec spec, SlicePair pair, double slope, Mat shared, MaskSource source) {
    PlanarImage image = pair.image().getUncachedModalityLutImage(null);
    Mat b = shared;
    Motion motion = Motion.NONE;
    try {
      if (image == null) {
        throw new IllegalStateException("A slice of an operand cannot be read");
      }
      if (b == null) {
        b = source.maskOf(pair);
      }
      if (image.width() != b.cols() || image.height() != b.rows()) {
        throw new IllegalStateException("A mask slice has not the size of its slice");
      }
      // Both operands as 32-bit floats: the sources may differ in depth and sign
      Mat a = new Mat();
      image.toMat().convertTo(a, CvType.CV_32F);
      if (spec.motionCorrection()) {
        motion = motionOf(a, b);
        if (motion != Motion.NONE) {
          Mat moved = translate(b, motion.rows(), motion.columns());
          if (b != shared) {
            b.release();
          }
          b = moved;
        }
      }
      Mat difference = new Mat();
      Core.addWeighted(a, 1.0, b, -spec.factor(), spec.offset(), difference);
      a.release();
      if (spec.absolute()) {
        Core.absdiff(difference, Scalar.all(0), difference);
      }
      Percentiles percentiles = percentiles(difference);
      if (slope != 1.0) {
        Core.multiply(difference, Scalar.all(1.0 / slope), difference);
      }
      Mat inRange = new Mat();
      Core.inRange(difference, Scalar.all(Short.MIN_VALUE), Scalar.all(Short.MAX_VALUE), inRange);
      long clipped = difference.total() - Core.countNonZero(inRange);
      inRange.release();
      ImageCV stored = new ImageCV();
      difference.convertTo(stored, CvType.CV_16S);
      difference.release();
      return new Slice(stored, clipped, percentiles, motion);
    } finally {
      release(image);
      if (b != null && b != shared) {
        b.release();
      }
    }
  }

  /**
   * The mask of a pair as 32-bit floats: its mask slices averaged, then, for a DSA mask, moved by
   * the sub-pixel shift (bilinear, edge pixels repeated), positive values moving the mask down and
   * right.
   */
  private static Mat maskOf(SubtractionSpec spec, SlicePair pair) {
    Mat sum = null;
    for (DicomImageElement element : pair.masks()) {
      PlanarImage mask = element.getUncachedModalityLutImage(null);
      if (mask == null) {
        release(sum);
        throw new IllegalStateException("A mask slice cannot be read");
      }
      try {
        if (sum == null) {
          sum = new Mat();
          mask.toMat().convertTo(sum, CvType.CV_32F);
        } else {
          if (mask.width() != sum.cols() || mask.height() != sum.rows()) {
            throw new IllegalStateException("The mask frames have different sizes");
          }
          Mat next = new Mat();
          mask.toMat().convertTo(next, CvType.CV_32F);
          Core.add(sum, next, sum);
          next.release();
        }
      } finally {
        mask.release();
      }
    }
    if (pair.masks().size() > 1) {
      Core.multiply(sum, Scalar.all(1.0 / pair.masks().size()), sum);
    }
    Dsa dsa = spec.dsa();
    if (dsa != null && dsa.isShifted()) {
      Mat shifted = translate(sum, dsa.shiftRows(), dsa.shiftColumns());
      sum.release();
      sum = shifted;
    }
    return sum;
  }

  /** The image moved by that many rows and columns, bilinear, edge pixels repeated. */
  private static Mat translate(Mat image, double rows, double columns) {
    Mat translation = new Mat(2, 3, CvType.CV_64F);
    translation.put(0, 0, 1, 0, columns, 0, 1, rows);
    Mat moved = new Mat();
    Imgproc.warpAffine(
        image, moved, translation, image.size(), Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE);
    translation.release();
    return moved;
  }

  /** A correction is ignored when the correlation is weak or the shift implausibly large. */
  private static final double MIN_CORRELATION = 0.05;

  private static final double MAX_SHIFT_FRACTION = 0.1;

  /**
   * The translation that brings the mask onto the slice, measured by phase correlation under a Hann
   * window; none when the two do not correlate or the measured shift is beyond a tenth of the
   * image.
   */
  private static Motion motionOf(Mat slice, Mat mask) {
    Mat window = new Mat();
    Imgproc.createHanningWindow(window, slice.size(), CvType.CV_32F);
    double[] response = new double[1];
    Point shift = Imgproc.phaseCorrelate(mask, slice, window, response);
    window.release();
    double limit = MAX_SHIFT_FRACTION * Math.min(slice.cols(), slice.rows());
    if (response[0] < MIN_CORRELATION
        || !Double.isFinite(shift.x)
        || !Double.isFinite(shift.y)
        || Math.abs(shift.x) > limit
        || Math.abs(shift.y) > limit
        || (Math.abs(shift.x) < 1e-3 && Math.abs(shift.y) < 1e-3)) {
      return Motion.NONE;
    }
    // phaseCorrelate(mask, slice) is the shift of the slice relative to the mask: moving the mask
    // by it aligns the two
    return new Motion(shift.y, shift.x);
  }

  private static void release(Mat mat) {
    if (mat != null) {
      mat.release();
    }
  }

  private static void release(PlanarImage image) {
    if (image != null) {
      image.release();
    }
  }

  private static Percentiles percentiles(Mat values) {
    float[] data = new float[(int) values.total()];
    values.get(0, 0, data);
    Arrays.sort(data);
    return new Percentiles(data[rank(data.length, 0.01)], data[rank(data.length, 0.99)]);
  }

  private static int rank(int count, double fraction) {
    return Math.clamp((int) Math.round(fraction * (count - 1)), 0, count - 1);
  }

  private static FileRawImage write(ImageCV stored, FileRawImage raw) {
    try {
      if (!raw.write(stored)) {
        throw new IllegalStateException("Cannot write the slice " + raw.path());
      }
      return raw;
    } finally {
      stored.release();
    }
  }

  /** The attributes shared by every derived instance, from those of the series. */
  private static Attributes baseAttributes(
      SubtractionSpec spec,
      Attributes source,
      MediaSeries<DicomImageElement> series,
      MediaSeries<DicomImageElement> mask,
      DicomImageElement maskReference,
      Percentiles percentiles,
      boolean resampled) {
    Attributes base = SeriesBuilder.getBaseAttributes(source);
    base.remove(Tag.VOILUTSequence);
    base.setString(
        Tag.SeriesDescription,
        VR.LO,
        description(
            series,
            source,
            mask,
            maskReference,
            spec.dsa() == null ? "" : spec.dsa().label(),
            resampled));
    base.setString(Tag.ImageType, VR.CS, imageType(source));
    base.setInt(Tag.SeriesNumber, VR.IS, source.getInt(Tag.SeriesNumber, 0) + 1000);
    base.setString(Tag.DerivationDescription, VR.ST, spec.derivationDescription());
    Sequence derivation = base.newSequence(Tag.DerivationCodeSequence, 1);
    derivation.add(
        new Code(PIXEL_SUBTRACTION_CODE, DCM, null, "Pixel by pixel subtraction").toItem());
    String agent = source.getString(Tag.ContrastBolusAgent);
    if (StringUtil.hasText(agent)) {
      base.setString(Tag.ContrastBolusAgent, VR.LO, agent);
    }
    base.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2"); // NON-NLS
    window(base, spec, percentiles);
    return base;
  }

  /** {@code SUB <series> - <mask>}, cut to the 64 characters of a LO value. */
  static String description(
      MediaSeries<DicomImageElement> operand,
      Attributes source,
      MediaSeries<DicomImageElement> mask,
      DicomImageElement maskReference,
      String maskLabel,
      boolean resampled) {
    String series = TagD.getTagValue(operand, Tag.SeriesDescription, String.class);
    if (series == null) {
      series = source.getString(Tag.SeriesDescription, "");
    }
    String maskDescription = TagD.getTagValue(mask, Tag.SeriesDescription, String.class);
    if (maskDescription == null) {
      maskDescription =
          maskReference.getMediaReader().getDicomObject().getString(Tag.SeriesDescription, "");
    }
    String text = DERIVATION + " " + series + " - " + maskDescription;
    if (operand == mask) {
      // A run minus its own frames: SUB <run> (mask 1)
      text = DERIVATION + " " + series + " (mask " + maskLabel + ")"; // NON-NLS
    }
    if (resampled) {
      text += " (resampled)"; // NON-NLS
    }
    return text.length() <= MAX_DESCRIPTION_LENGTH
        ? text.strip()
        : text.substring(0, MAX_DESCRIPTION_LENGTH).strip();
  }

  /** {@code DERIVED\SECONDARY\<value 3 of the source>\SUBTRACTION}. */
  static String[] imageType(Attributes source) {
    String[] type = source.getStrings(Tag.ImageType);
    String third = type != null && type.length > 2 ? type[2] : "OTHER"; // NON-NLS
    return new String[] {"DERIVED", "SECONDARY", third, "SUBTRACTION"}; // NON-NLS
  }

  /**
   * Two presets of real values: the enhancement window from black up to the 99th percentile, and
   * the signed window from the 1st to the 99th percentile; the spec says which comes first. A DSA
   * run opens on the signed window, as the angiography consoles show it: the iodine lowers the
   * values of an X-ray image, so the vessels are dark on a mid-gray background.
   */
  private static void window(Attributes base, SubtractionSpec spec, Percentiles percentiles) {
    double high = percentiles.high();
    double low = percentiles.low();
    double black = high > 0 ? 0 : low;
    double[] enhancement = {(black + high) / 2, Math.max(high - black, 1)};
    double[] signedWindow = {(low + high) / 2, Math.max(high - low, 1)};
    boolean signedFirst = spec.signed() || spec.dsa() != null;
    double[] first = signedFirst ? signedWindow : enhancement;
    double[] second = signedFirst ? enhancement : signedWindow;
    String[] names = {"Subtraction", "Signed"}; // NON-NLS
    base.setDouble(Tag.WindowCenter, VR.DS, first[0], second[0]);
    base.setDouble(Tag.WindowWidth, VR.DS, first[1], second[1]);
    base.setString(
        Tag.WindowCenterWidthExplanation,
        VR.LO,
        signedFirst ? names[1] : names[0],
        signedFirst ? names[0] : names[1]);
    base.setString(Tag.VOILUTFunction, VR.CS, "LINEAR_EXACT"); // NON-NLS
  }

  /** The derived instance of one pair, with the two source instances it was computed from. */
  private static DicomImageElement element(
      SubtractionSpec spec,
      SlicePair pair,
      FileRawImage raw,
      Attributes base,
      double slope,
      String seriesUid,
      int index) {
    DicomImageElement image = pair.image();
    Attributes attributes = new Attributes(base);
    Sequence sources = attributes.newSequence(Tag.SourceImageSequence, 2);
    sources.add(sourceItem(image));
    sources.add(sourceItem(pair.mask()));

    RawImageIO rawIO = new RawImageIO(raw, null);
    rawIO.getFileCache().setOriginalTempFile(raw.path());
    rawIO.setBaseAttributes(attributes);
    DerivedStack.copyMandatoryTags(image, rawIO);
    rawIO.copyTags(
        TagD.getTagFromIDs(
            Tag.ImageOrientationPatient,
            Tag.ImagePositionPatient,
            Tag.PixelSpacing,
            Tag.ImagerPixelSpacing,
            Tag.PixelSpacingCalibrationDescription,
            Tag.PixelAspectRatio,
            Tag.SliceThickness,
            Tag.SpacingBetweenSlices,
            Tag.SliceLocation,
            Tag.Columns,
            Tag.Rows,
            Tag.CineRate,
            Tag.FrameTime),
        image,
        false);
    rawIO.setTagNoNull(TagW.SlicePosition, image.getTagValue(TagW.SlicePosition));
    // After the mandatory tags: the difference is signed whatever the sources were
    SeriesBuilder.writePixelDataAttributes(1, CvType.CV_16S, rawIO);
    rawIO.setTag(TagD.get(Tag.PhotometricInterpretation), "MONOCHROME2"); // NON-NLS
    rawIO.setTag(TagD.get(Tag.RescaleIntercept), 0.0);
    rawIO.setTag(TagD.get(Tag.RescaleSlope), slope);
    rawIO.setTag(TagD.get(Tag.SeriesInstanceUID), seriesUid);
    rawIO.setTag(TagD.get(Tag.SeriesDescription), base.getString(Tag.SeriesDescription));
    rawIO.setTag(
        TagD.get(Tag.SOPInstanceUID),
        spec.derivedInstanceUid(TagD.getTagValue(image, Tag.SOPInstanceUID, String.class)));
    rawIO.setTag(TagD.get(Tag.InstanceNumber), index + 1);
    return DerivedStack.buildDicomImageElement(rawIO);
  }

  private static Attributes sourceItem(DicomImageElement source) {
    Attributes item = new Attributes(3);
    item.setString(
        Tag.ReferencedSOPClassUID, VR.UI, TagD.getTagValue(source, Tag.SOPClassUID, String.class));
    item.setString(
        Tag.ReferencedSOPInstanceUID,
        VR.UI,
        TagD.getTagValue(source, Tag.SOPInstanceUID, String.class));
    item.newSequence(Tag.PurposeOfReferenceCodeSequence, 1)
        .add(
            new Code(
                    SOURCE_FOR_PROCESSING_CODE,
                    DCM,
                    null,
                    "Source image for image processing operation")
                .toItem());
    return item;
  }

  /**
   * The stored values keep the rescale slope of the series, so a difference of two stored values is
   * stored exactly; a slope of zero or none is one.
   */
  private static double rescaleSlope(Attributes source) {
    double slope = source.getDouble(Tag.RescaleSlope, 1.0);
    return slope == 0 || !Double.isFinite(slope) ? 1.0 : slope;
  }
}
