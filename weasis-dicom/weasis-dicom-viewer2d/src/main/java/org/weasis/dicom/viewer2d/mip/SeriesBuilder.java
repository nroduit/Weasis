/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mip;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.dcm4che3.img.util.PixelDataUtils;
import org.dcm4che3.util.UIDUtils;
import org.opencv.core.CvType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.image.op.ImageStackOperations;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.Pair;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.viewer2d.View2d;
import org.weasis.dicom.viewer2d.mip.MipView.Type;
import org.weasis.dicom.viewer2d.mpr.DerivedStack;
import org.weasis.dicom.viewer2d.mpr.RawImageIO;
import org.weasis.opencv.data.FileRawImage;
import org.weasis.opencv.data.PlanarImage;

public class SeriesBuilder {
  private static final Logger LOGGER = LoggerFactory.getLogger(SeriesBuilder.class);
  public static final Path MIP_CACHE_DIR =
      AppProperties.buildAccessibleTempDirectory(AppProperties.CACHE_NAME, "mip"); // NON-NLS

  private static final int[] COPIED_ATTRS = {
    Tag.SpecificCharacterSet,
    Tag.TimezoneOffsetFromUTC,
    Tag.PatientID,
    Tag.PatientName,
    Tag.PatientBirthDate,
    Tag.PatientBirthTime,
    Tag.PatientSex,
    Tag.IssuerOfPatientID,
    Tag.IssuerOfAccessionNumberSequence,
    Tag.PatientWeight,
    Tag.PatientAge,
    Tag.PatientSize,
    Tag.PatientState,
    Tag.PatientComments,
    Tag.StudyID,
    Tag.StudyDate,
    Tag.StudyTime,
    Tag.StudyDescription,
    Tag.StudyComments,
    Tag.AccessionNumber,
    Tag.ModalitiesInStudy,
    Tag.Modality,
    Tag.SeriesDate,
    Tag.SeriesTime,
    Tag.RetrieveAETitle,
    Tag.ReferringPhysicianName,
    Tag.InstitutionName,
    Tag.InstitutionalDepartmentName,
    Tag.StationName,
    Tag.Manufacturer,
    Tag.ManufacturerModelName,
    Tag.AnatomicalOrientationType,
    Tag.SeriesNumber,
    Tag.KVP,
    Tag.Laterality,
    Tag.BodyPartExamined,
    Tag.AnatomicRegionSequence,
    Tag.FrameOfReferenceUID,
    Tag.WindowCenter,
    Tag.WindowWidth,
    Tag.VOILUTFunction,
    Tag.WindowCenterWidthExplanation,
    Tag.VOILUTSequence
  };

  static {
    Arrays.sort(COPIED_ATTRS);
  }

  private SeriesBuilder() {}

  /**
   * Computes MIP slices for the given series and appends the resulting {@link DicomImageElement}s
   * to {@code dicoms}.
   *
   * <p>The medias iterable is materialized into a {@link List} once so that the inner loop can use
   * O(1) random access instead of re-creating an iterator for every slice index.
   */
  @SuppressWarnings("unchecked")
  public static void applyMipParameters(
      MipView view,
      MediaSeries<DicomImageElement> series,
      List<DicomImageElement> dicoms,
      Type mipType,
      Integer extend,
      boolean fullSeries) {

    if (series == null) return;

    var sort = (SeriesComparator<DicomImageElement>) view.getActionValue(ActionW.SORT_STACK.cmd());
    var reverse = (Boolean) view.getActionValue(ActionW.INVERSE_STACK.cmd());
    var sortFilter = (reverse != null && reverse) ? sort.getReversOrderComparator() : sort;
    var filter = (Filter<DicomImageElement>) view.getActionValue(ActionW.FILTERED_SERIES.cmd());

    var allMedia = new ArrayList<>(series.copyOfMedias(filter, sortFilter));

    // For a single-slab build use the index that MipView already resolved from the received image
    // in setImage(), so the slab centre always matches the scrolled-to position even before the
    // EDT has had a chance to update the SCROLL_SERIES slider.
    int curImg = view.getFrameIndex();
    int minImg = fullSeries ? extend : curImg;
    int maxImg = fullSeries ? allMedia.size() - extend : curImg;

    var img = series.getMedia(MediaSeries.MEDIA_POSITION.MIDDLE, filter, sortFilter);
    var cpTags = getMipBaseAttributes(img.getMediaReader().getDicomObject());
    adaptWindowLevel(view, cpTags);
    String seriesUID = UIDUtils.createUID();

    for (int index = minImg; index <= maxImg; index++) {
      var sources = collectSources(allMedia, index, extend);
      if (sources.size() <= 1) continue;

      var curImage = addCollectionOperation(mipType, sources.stream().map(Pair::second).toList());
      if (curImage == null) continue;

      var imgRef = sources.get(sources.size() / 2).first();

      // For a single-slab build, tell the MipView which original-series image sits at the center
      if (!fullSeries) {
        view.setCenterImage(imgRef, mipType, extend);
      }

      var raw = writeMipImage(curImage, fullSeries, seriesUID, view.getCacheDir());
      if (raw == null) return;

      dicoms.add(
          buildMipDicomElement(raw, cpTags, curImage, sources, imgRef, img, seriesUID, index));
    }
  }

  /**
   * Collects the source (image, planar-image) pairs centred at {@code index} ± {@code extend},
   * clamped to the available range.
   */
  private static List<Pair<DicomImageElement, PlanarImage>> collectSources(
      List<DicomImageElement> allMedia, int index, int extend) {

    int start = Math.max(0, index - extend);
    int stop = Math.min(allMedia.size() - 1, index + extend);
    var sources = new ArrayList<Pair<DicomImageElement, PlanarImage>>(stop - start + 1);
    for (int k = start; k <= stop; k++) {
      var dcm = allMedia.get(k);
      sources.add(new Pair<>(dcm, dcm.getModalityLutImage(null, null)));
    }
    return sources;
  }

  private static FileRawImage writeMipImage(
      PlanarImage image, boolean fullSeries, String seriesUID, Path viewCacheDir) {
    FileRawImage raw = null;
    try {
      Path dir =
          fullSeries ? Files.createDirectories(viewCacheDir.resolve(seriesUID)) : viewCacheDir;
      raw = new FileRawImage(Files.createTempFile(dir, "mip_", ".wcv")); // NON-NLS
      if (raw.write(image)) return raw;
      FileUtil.delete(raw.path());
    } catch (Exception e) {
      if (raw != null) FileUtil.delete(raw.path());
      LOGGER.error("Writing MIP", e);
    }
    return null;
  }

  private static DicomImageElement buildMipDicomElement(
      FileRawImage raw,
      Attributes cpTags,
      PlanarImage image,
      List<Pair<DicomImageElement, PlanarImage>> sources,
      DicomImageElement imgRef,
      DicomImageElement img,
      String seriesUID,
      int index) {

    var rawIO = new RawImageIO(raw, null);
    rawIO.getFileCache().setOriginalTempFile(raw.path());
    rawIO.setBaseAttributes(cpTags);

    rawIO.setTag(TagD.get(Tag.Columns), image.width());
    rawIO.setTag(TagD.get(Tag.Rows), image.height());
    writePixelDataAttributes(CvType.channels(image.type()), image.type(), rawIO);

    double thickness =
        DicomMediaUtils.getThickness(sources.getFirst().first(), sources.getLast().first());
    rawIO.setTag(TagD.get(Tag.SliceThickness), thickness > 0.0 ? thickness : sources.size());

    double[] loc = (double[]) imgRef.getTagValue(TagW.SlicePosition);
    if (loc != null) {
      rawIO.setTag(TagW.SlicePosition, loc);
      rawIO.setTag(TagD.get(Tag.SliceLocation), DicomMediaUtils.getSlicePositionValue(imgRef));
    }

    rawIO.setTag(TagD.get(Tag.SeriesInstanceUID), seriesUID);
    DerivedStack.copyMandatoryTags(img, rawIO);

    var spatialTags =
        TagD.getTagFromIDs(
            Tag.ImageOrientationPatient,
            Tag.ImagePositionPatient,
            Tag.PixelSpacing,
            Tag.ImagerPixelSpacing,
            Tag.NominalScannedPixelSpacing,
            Tag.PixelSpacingCalibrationDescription,
            Tag.PixelAspectRatio);
    rawIO.copyTags(spatialTags, imgRef, false);

    rawIO.setTag(TagD.get(Tag.SOPInstanceUID), UIDUtils.createUID());
    rawIO.setTag(TagD.get(Tag.InstanceNumber), index + 1);
    return DerivedStack.buildDicomImageElement(rawIO);
  }

  public static void writePixelDataAttributes(int channels, int cvType, DcmMediaReader rawIO) {
    int bpc = PixelDataUtils.getBitsAllocatedFromCvType(cvType);
    rawIO.setTag(TagD.get(Tag.SamplesPerPixel), channels);
    rawIO.setTag(TagD.get(Tag.BitsAllocated), bpc);
    if (CvType.isInteger(cvType)) {
      rawIO.setTag(TagD.get(Tag.BitsStored), bpc);
      rawIO.setTag(TagD.get(Tag.HighBit), bpc - 1);
      rawIO.setTag(
          TagD.get(Tag.PixelRepresentation), PixelDataUtils.isSignedCvType(cvType) ? 1 : 0);
    }
  }

  public static Attributes getBaseAttributes(Attributes attributes) {
    return new Attributes(attributes, COPIED_ATTRS);
  }

  public static Attributes getMipBaseAttributes(Attributes attributes) {
    var cpTags = getBaseAttributes(attributes);
    cpTags.setString(
        Tag.SeriesDescription,
        VR.LO,
        attributes.getString(Tag.SeriesDescription, "") + " [MIP]"); // NON-NLS
    cpTags.setString(Tag.ImageType, VR.CS, "DERIVED", "SECONDARY", "PROJECTION IMAGE"); // NON-NLS
    return cpTags;
  }

  /**
   * Inserts the current window/level values from the event manager as the first entry of the {@code
   * WindowCenter} / {@code WindowWidth} arrays in {@code cpTags}, unless a named preset is already
   * active (in which case the preset stored in the tag is used as-is).
   */
  public static void adaptWindowLevel(View2d view2d, Attributes cpTags) {
    var manager = view2d.getEventManager();
    var windowAction = manager.getAction(ActionW.WINDOW);
    var levelAction = manager.getAction(ActionW.LEVEL);
    if (windowAction.isEmpty() || levelAction.isEmpty()) return;

    var presetAction = manager.getAction(ActionW.PRESET);
    var oldPreset = presetAction.map(a -> (PresetWindowLevel) a.getSelectedItem()).orElse(null);
    if (oldPreset != null) return; // named preset – keep tag values unchanged

    double center = levelAction.get().getRealValue();
    double width = windowAction.get().getRealValue();
    double[] wc = cpTags.getDoubles(Tag.WindowCenter);
    double[] ww = cpTags.getDoubles(Tag.WindowWidth);

    if (wc != null && ww != null && wc.length > 0 && ww.length > 0) {
      cpTags.setDouble(Tag.WindowCenter, VR.DS, insertAtFirst(wc, center));
      cpTags.setDouble(Tag.WindowWidth, VR.DS, insertAtFirst(ww, width));
    } else {
      cpTags.setDouble(Tag.WindowCenter, VR.DS, center);
      cpTags.setDouble(Tag.WindowWidth, VR.DS, width);
    }
  }

  /** Returns a new array with {@code newItem} prepended to {@code originalArray}. */
  public static double[] insertAtFirst(double[] originalArray, double newItem) {
    double[] result = new double[originalArray.length + 1];
    result[0] = newItem;
    System.arraycopy(originalArray, 0, result, 1, originalArray.length);
    return result;
  }

  /** Applies the projection operation matching {@code mipType} to the given image stack. */
  public static PlanarImage addCollectionOperation(Type mipType, List<PlanarImage> sources) {
    return switch (mipType) {
      case NONE -> null;
      case MIN -> ImageStackOperations.min(sources);
      case MEAN -> ImageStackOperations.mean(sources);
      case MAX -> ImageStackOperations.max(sources);
    };
  }
}
