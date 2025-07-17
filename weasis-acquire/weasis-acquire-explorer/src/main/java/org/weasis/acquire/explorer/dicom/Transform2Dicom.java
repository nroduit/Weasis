/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.dicom;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.io.File;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Objects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.UIDUtils;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.AcquireMediaInfo;
import org.weasis.acquire.explorer.core.bean.SeriesGroup.Type;
import org.weasis.core.api.image.CropOp;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Taggable;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.explorer.pr.DicomPrSerializer;
import org.weasis.dicom.ref.AnatomicRegion;
import org.weasis.dicom.tool.Dicomizer;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

public final class Transform2Dicom {

  private static final Logger LOGGER = LoggerFactory.getLogger(Transform2Dicom.class);

  private Transform2Dicom() {}

  /**
   * Do the encoding of the given image in a standard lossy JPEG format with optionally doing some
   * pre-processing operations (like resize, flip, crop, zoom, contrast ...) if any
   * postProcessOperation have been set in the AcquireImageInfo. The temporary image file is then
   * encapsulated in a standard DICOM format according to the proper Dicom attributes set in the
   * AcquireImageInfo. This Dicom is written in the exportDirDicom with its sopInstanceUID as
   * filename.
   *
   * @param mediaInfo value
   * @param exportDirDicom the folder to save DICOM files
   * @param exportDirImage the folder to save image files
   * @param seriesInstanceUID Global series for all PR
   * @return true when the operation is successful
   */
  public static boolean dicomize(
      AcquireMediaInfo mediaInfo,
      File exportDirDicom,
      File exportDirImage,
      String seriesInstanceUID) {
    if (mediaInfo instanceof AcquireImageInfo imageInfo) {
      return processImageElement(imageInfo, exportDirDicom, exportDirImage, seriesInstanceUID);
    } else {
      return processOtherMediaElement(mediaInfo, exportDirDicom);
    }
  }

  private static boolean processOtherMediaElement(AcquireMediaInfo mediaInfo, File exportDirDicom) {
    Attributes attrs = populateDicomAttributes(mediaInfo);

    MediaElement mediaElement = mediaInfo.getMedia();
    File mediaFile = mediaElement.getFileCache().getOriginalFile().orElse(null);
    if (mediaFile == null || !mediaFile.canRead()) {
      LOGGER.error("Cannot read media file: {}", mediaElement.getName());
      return false;
    }

    try {
      String sopInstanceUID =
          Objects.requireNonNull((String) mediaElement.getTagValue(TagD.getUID(Level.INSTANCE)));
      var type = mediaInfo.getSeries().getType();
      var file = new File(exportDirDicom, sopInstanceUID);
      if (type == Type.PDF) {
        Dicomizer.pdf(attrs, mediaFile, file);
      } else if (type == Type.STL) {
        Dicomizer.stl(attrs, mediaFile, file);
      } else if (type == Type.VIDEO_MP4) {
        Dicomizer.mpeg4(attrs, mediaFile, file);
      } else if (type == Type.VIDEO_MP2) {
        Dicomizer.mpeg2(attrs, mediaFile, file);
      }
      return true;
    } catch (Exception e) {
      LOGGER.error("Cannot Dicomize media: {}", mediaElement.getName(), e);
      return false;
    }
  }

  private static Attributes populateDicomAttributes(AcquireMediaInfo mediaInfo) {
    Attributes attrs = mediaInfo.getAttributes();
    DicomMediaUtils.fillAttributes(AcquireManager.GLOBAL.getTagEntrySetIterator(), attrs);
    DicomMediaUtils.fillAttributes(mediaInfo.getSeries().getTagEntrySetIterator(), attrs);
    DicomMediaUtils.fillAttributes(mediaInfo.getMedia().getTagEntrySetIterator(), attrs);

    AnatomicRegion.write(
        attrs, (AnatomicRegion) mediaInfo.getMedia().getTagValue(TagW.AnatomicRegion));
    return attrs;
  }

  public static boolean processImageElement(
      AcquireImageInfo imageInfo,
      File exportDirDicom,
      File exportDirImage,
      String seriesInstanceUID) {

    ImageElement imageElement = imageInfo.getImage();
    String sopInstanceUID =
        Objects.requireNonNull((String) imageElement.getTagValue(TagD.getUID(Level.INSTANCE)));

    // Transform the image if required
    File imgFile = imageElement.getFileCache().getOriginalFile().orElse(null);
    Integer orientation =
        StringUtil.getInteger((String) imageElement.getTagValue(TagW.ExifOrientation));
    if (imgFile == null
        || !imageElement.getMimeType().contains("jpeg")
        || !imageInfo.getCurrentValues().equals(imageInfo.getDefaultValues())
        || (orientation != null && orientation > 0)) {

      imgFile = new File(exportDirImage, sopInstanceUID + ".jpg");
      SimpleOpManager opManager = imageInfo.getPostProcessOpManager();
      PlanarImage transformedImage = imageElement.getImage(opManager, false);

      MatOfInt map = new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 80);
      if (!ImageProcessor.writeImage(transformedImage.toImageCV(), imgFile, map)) {
        // Out of memory or error
        FileUtil.delete(imgFile);
        LOGGER.error("Cannot Transform to JPEG: {}", imageElement.getName());
        return false;
      }
    }

    // Dicomize
    if (imgFile.canRead()) {
      Attributes attrs = populateDicomAttributes(imageInfo);

      // Spatial calibration
      if (Unit.PIXEL != imageElement.getPixelSpacingUnit()) {
        attrs.setString(Tag.PixelSpacingCalibrationDescription, VR.LO, "Used fiducial"); // NON-NLS
        double unitRatio =
            imageElement.getPixelSize()
                * Unit.MILLIMETER.getConversionRatio(
                    imageElement.getPixelSpacingUnit().getConvFactor());
        attrs.setDouble(Tag.PixelSpacing, VR.DS, unitRatio, unitRatio);
      }

      try {
        Dicomizer.jpeg(attrs, imgFile, new File(exportDirDicom, sopInstanceUID), false);
      } catch (Exception e) {
        LOGGER.error("Cannot Dicomize {}", imageElement.getName(), e);
        return false;
      }

      // Presentation State
      GraphicModel grModel = (GraphicModel) imageElement.getTagValue(TagW.PresentationModel);
      if (grModel != null && grModel.hasSerializableGraphics()) {
        processDicomPR(imageInfo, exportDirDicom, seriesInstanceUID, grModel, attrs);
      }
    } else {
      LOGGER.error("Cannot read JPEG image {}", imageElement.getName());
      return false;
    }

    return true;
  }

  private static void processDicomPR(
      AcquireImageInfo imageInfo,
      File exportDirDicom,
      String seriesInstanceUID,
      GraphicModel grModel,
      Attributes attrs) {
    Point2D offset = null;
    Rectangle crop =
        (Rectangle)
            imageInfo.getPostProcessOpManager().getParamValue(CropOp.OP_NAME, CropOp.P_AREA);
    if (crop != null) {
      offset = new Point2D.Double(crop.getX(), crop.getY());
    }
    String prUid = UIDUtils.createUID();

    // Set these attributes to determine the PR sopInstanceUID
    String photometricInterpretation =
        imageInfo.getAttributes().getString(Tag.PhotometricInterpretation, null);
    attrs.setString(Tag.PhotometricInterpretation, VR.CS, photometricInterpretation);
    int samplesPerPixel = imageInfo.getAttributes().getInt(Tag.SamplesPerPixel, 1);
    attrs.setInt(Tag.SamplesPerPixel, VR.US, samplesPerPixel);

    File outputFile = new File(exportDirDicom, prUid);
    DicomPrSerializer.writePresentation(
        grModel, attrs, outputFile, seriesInstanceUID, prUid, offset);
  }

  /**
   * Populates Date and Time for all Attributes in the imageInfo Collection with respect to the
   * youngest. That is : the first image content Date and Time would define the SeriesDate and
   * SeriesTime within the current Series, and so on within the current Study
   *
   * @param collection the AcquireImageInfo list
   * @param dicomTags the Taggable value
   */
  public static void buildStudySeriesDate(
      Collection<AcquireMediaInfo> collection, final Taggable dicomTags) {

    TagW seriesDate = TagD.get(Tag.SeriesDate);
    TagW seriesTime = TagD.get(Tag.SeriesTime);
    TagW studyDate = TagD.get(Tag.StudyDate);
    TagW studyTime = TagD.get(Tag.StudyTime);

    // Reset study and series values
    dicomTags.setTag(studyDate, null);
    dicomTags.setTag(studyTime, null);
    collection.forEach(
        i -> {
          i.getSeries().setTag(seriesDate, null);
          i.getSeries().setTag(seriesTime, null);
        });

    for (AcquireMediaInfo imageInfo : collection) {
      MediaElement imageElement = imageInfo.getMedia();
      LocalDateTime date = TagD.dateTime(Tag.ContentDate, Tag.ContentTime, imageElement);
      if (date == null) {
        continue;
      }

      LocalDateTime minSeries =
          TagD.dateTime(Tag.SeriesDate, Tag.SeriesTime, imageInfo.getSeries());
      if (minSeries == null || date.isBefore(minSeries)) {
        imageInfo.getSeries().setTag(seriesDate, date.toLocalDate());
        imageInfo.getSeries().setTag(seriesTime, date.toLocalTime());
      }

      LocalDateTime minStudy = TagD.dateTime(Tag.StudyDate, Tag.StudyTime, dicomTags);
      if (minStudy == null || date.isBefore(minStudy)) {
        dicomTags.setTag(studyDate, date.toLocalDate());
        dicomTags.setTag(studyTime, date.toLocalTime());
      }
    }
  }
}
