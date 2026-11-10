/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.UIDUtils;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.provenance.Provenance;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.export.ContributingEquipment;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.viewer2d.fusion.FusionVolumeResampler;
import org.weasis.dicom.viewer2d.mip.SeriesBuilder;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;
import org.weasis.opencv.data.FileRawImage;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

/**
 * Builds a multiplanar reformat of a series as a series of its own: the rectified volume of the
 * source resliced on an isotropic grid in another standard plane, each slice written as a raw image
 * with the DICOM attributes of a derived instance, so the result scrolls, hangs and is sent like an
 * acquired series.
 */
public final class ReformatBuilder {

  public static final Path CACHE_DIR =
      AppProperties.buildAccessibleTempDirectory(AppProperties.CACHE_NAME, "derived"); // NON-NLS

  /** Derivation label of the series and Image Type value 4 of its instances. */
  public static final String DERIVATION = "MPR"; // NON-NLS

  private static final String MPR_CODE = "113072"; // NON-NLS
  private static final String DCM = "DCM"; // NON-NLS

  /** The derived series and where its slices live. */
  public record Result(DicomSeries series, Path cacheDir, int slices, double spacing) {}

  private ReformatBuilder() {}

  /** Series Instance UID of the reformat of a series in a plane, the same every time. */
  public static String derivedSeriesUid(String sourceUid, Plane plane) {
    return UIDUtils.createNameBasedUID(key(sourceUid, plane).getBytes(StandardCharsets.UTF_8));
  }

  private static String key(String sourceUid, Plane plane) {
    return "weasis.mpr|" + sourceUid + "|" + plane.name(); // NON-NLS
  }

  /**
   * Reslices the series in the plane; the source must have a geometry and not lie in that plane
   * already.
   *
   * @throws IllegalStateException when the volume cannot be built or the plane is the source's
   * @throws IOException when a slice cannot be written
   */
  public static Result build(MediaSeries<DicomImageElement> series, Plane plane)
      throws IOException {
    DicomImageElement middle = series.getMedia(MEDIA_POSITION.MIDDLE, null, null);
    if (middle == null) {
      throw new IllegalStateException("The series has no image");
    }
    if (Plane.getAcquisitionPlane(middle) == plane && middle.getSliceGeometry() != null) {
      throw new IllegalStateException("The series already lies in the " + plane + " plane");
    }
    String sourceUid = TagD.getTagValue(series, Tag.SeriesInstanceUID, String.class);
    String seriesUid = derivedSeriesUid(sourceUid, plane);
    Path dir = CACHE_DIR.resolve(seriesUid);
    FileUtil.delete(dir);
    Files.createDirectories(dir);
    try (OwnedVolume owned = OwnedVolume.of(series)) {
      if (owned == null) {
        throw new IllegalStateException("The volume of the series cannot be built");
      }
      Volume<?, ?> volume = owned.volume();
      ReformatGrid grid;
      try {
        grid = ReformatGrid.of(corners(volume), volume.getMinPixelRatio(), plane);
      } catch (IllegalArgumentException e) {
        // too few slices or no spacing: the empty volume the request built must not stay
        FileUtil.delete(dir);
        VolumeService.getInstance().evict(series);
        throw new IllegalStateException(
            "The series makes no volume ("
                + series.size(null)
                + " slices, "
                + volume.getSize().x
                + "x"
                + volume.getSize().y
                + "x"
                + volume.getSize().z
                + " voxels): "
                + e.getMessage(),
            e);
      }
      Attributes base = baseAttributes(middle.getMediaReader().getDicomObject(), plane, grid);
      double fill = volume.getPhotometricMinValue().doubleValue();
      FileRawImage[] files = new FileRawImage[grid.slices()];
      try {
        IntStream.range(0, grid.slices())
            .parallel()
            .forEach(i -> files[i] = reslice(volume, grid, i, fill, dir));
      } catch (RuntimeException e) {
        FileUtil.delete(dir);
        throw e;
      }
      List<DicomImageElement> images =
          IntStream.range(0, grid.slices())
              .mapToObj(
                  i -> element(middle, grid, i, files[i], base, seriesUid, volume.getCvType()))
              .toList();
      DicomSeries derived = new DicomSeries(seriesUid);
      derived.addAll(images);
      images.getFirst().getMediaReader().writeMetaData(derived);
      derived.setTag(TagW.Derivation, DERIVATION);
      derived.setTag(
          TagW.Provenance,
          Provenance.computed(
              "mpr:build", // NON-NLS
              base.getString(Tag.DerivationDescription),
              List.of(
                  Provenance.header(
                      "(0020,000E)", // NON-NLS
                      TagD.getTagValue(middle, Tag.SeriesInstanceUID, String.class)))));
      return new Result(derived, dir, grid.slices(), grid.spacing());
    }
  }

  /** The eight corners of the volume in patient space. */
  private static Vector3d[] corners(Volume<?, ?> volume) {
    Vector3i size = volume.getSize();
    Vector3d[] corners = new Vector3d[8];
    int i = 0;
    for (int x : new int[] {0, size.x - 1}) {
      for (int y : new int[] {0, size.y - 1}) {
        for (int z : new int[] {0, size.z - 1}) {
          corners[i++] = volume.voxelToLps(x, y, z, new Vector3d());
        }
      }
    }
    return corners;
  }

  private static FileRawImage reslice(
      Volume<?, ?> volume, ReformatGrid grid, int index, double fill, Path dir) {
    PlanarImage values =
        FusionVolumeResampler.resampleToValue(
            volume, grid.geometry(index), grid.width(), grid.height());
    if (values == null) {
      throw new IllegalStateException("The volume cannot be resliced");
    }
    Mat floats = values.toMat();
    Core.patchNaNs(floats, fill);
    ImageCV stored = new ImageCV();
    floats.convertTo(stored, volume.getCvType());
    values.release();
    FileRawImage raw = new FileRawImage(dir.resolve("mpr_" + index + ".wcv")); // NON-NLS
    boolean written = raw.write(stored);
    stored.release();
    if (!written) {
      throw new IllegalStateException("Cannot write a slice of the reformat");
    }
    return raw;
  }

  private static Attributes baseAttributes(Attributes source, Plane plane, ReformatGrid grid) {
    Attributes base = SeriesBuilder.getBaseAttributes(source);
    String description = source.getString(Tag.SeriesDescription, "");
    base.setString(
        Tag.SeriesDescription,
        VR.LO,
        cut(description + " [" + DERIVATION + " " + plane.name() + "]")); // NON-NLS
    base.setString(Tag.ImageType, VR.CS, "DERIVED", "SECONDARY", "MPR"); // NON-NLS
    base.setInt(Tag.SeriesNumber, VR.IS, source.getInt(Tag.SeriesNumber, 0) + 2000);
    base.setString(
        Tag.DerivationDescription,
        VR.ST,
        String.format(
            Locale.ROOT,
            "Multiplanar reformat, %s plane, isotropic %.3f mm, nearest voxel",
            plane.name(),
            grid.spacing())); // NON-NLS
    Sequence derivation = base.newSequence(Tag.DerivationCodeSequence, 1);
    derivation.add(new Code(MPR_CODE, DCM, null, "Multiplanar reformatting").toItem());
    ContributingEquipment.weasis(base, base.getString(Tag.DerivationDescription));
    return base;
  }

  private static String cut(String text) {
    return text.length() <= 64 ? text : text.substring(0, 64).strip();
  }

  private static DicomImageElement element(
      DicomImageElement reference,
      ReformatGrid grid,
      int index,
      FileRawImage raw,
      Attributes base,
      String seriesUid,
      int cvType) {
    Attributes attributes = new Attributes(base);
    Sequence sources = attributes.newSequence(Tag.SourceImageSequence, 1);
    Attributes item = new Attributes(2);
    item.setString(
        Tag.ReferencedSOPClassUID,
        VR.UI,
        TagD.getTagValue(reference, Tag.SOPClassUID, String.class));
    item.setString(
        Tag.ReferencedSOPInstanceUID,
        VR.UI,
        TagD.getTagValue(reference, Tag.SOPInstanceUID, String.class));
    sources.add(item);

    RawImageIO rawIO = new RawImageIO(raw, null);
    rawIO.getFileCache().setOriginalTempFile(raw.path());
    rawIO.setBaseAttributes(attributes);
    DerivedStack.copyMandatoryTags(reference, rawIO);
    rawIO.copyTags(
        TagD.getTagFromIDs(
            Tag.FrameOfReferenceUID,
            Tag.RescaleSlope,
            Tag.RescaleIntercept,
            Tag.RescaleType,
            Tag.PixelSpacingCalibrationDescription,
            Tag.PhotometricInterpretation),
        reference,
        false);
    SeriesBuilder.writePixelDataAttributes(1, cvType, rawIO);
    Vector3d tlhc = grid.tlhc(index);
    rawIO.setTag(TagD.get(Tag.Rows), grid.height());
    rawIO.setTag(TagD.get(Tag.Columns), grid.width());
    rawIO.setTag(TagD.get(Tag.ImageOrientationPatient), grid.orientation());
    rawIO.setTag(TagD.get(Tag.ImagePositionPatient), new double[] {tlhc.x, tlhc.y, tlhc.z});
    rawIO.setTag(TagD.get(Tag.PixelSpacing), new double[] {grid.spacing(), grid.spacing()});
    rawIO.setTag(TagD.get(Tag.SliceThickness), grid.spacing());
    rawIO.setTag(TagD.get(Tag.SpacingBetweenSlices), grid.spacing());
    rawIO.setTag(TagD.get(Tag.SeriesInstanceUID), seriesUid);
    rawIO.setTag(TagD.get(Tag.SeriesDescription), base.getString(Tag.SeriesDescription));
    rawIO.setTag(
        TagD.get(Tag.SOPInstanceUID),
        UIDUtils.createNameBasedUID(
            (seriesUid + "|" + index).getBytes(StandardCharsets.UTF_8))); // NON-NLS
    rawIO.setTag(TagD.get(Tag.InstanceNumber), index + 1);
    rawIO.setTagNoNull(TagW.SlicePosition, DicomMediaUtils.computeSlicePosition(rawIO));
    return DerivedStack.buildDicomImageElement(rawIO);
  }
}
