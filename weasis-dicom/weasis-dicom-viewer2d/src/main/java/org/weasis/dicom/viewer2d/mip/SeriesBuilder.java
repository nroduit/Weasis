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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.image.op.MaxCollectionZprojection;
import org.weasis.core.api.image.op.MeanCollectionZprojection;
import org.weasis.core.api.image.op.MinCollectionZprojection;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.viewer2d.View2d;
import org.weasis.dicom.viewer2d.mip.MipView.Type;
import org.weasis.dicom.viewer2d.mpr.RawImageIO;
import org.weasis.opencv.data.FileRawImage;
import org.weasis.opencv.data.PlanarImage;

public class SeriesBuilder {
  private static final Logger LOGGER = LoggerFactory.getLogger(SeriesBuilder.class);

  private SeriesBuilder() {}

  public static void applyMipParameters(
      final View2d view,
      final MediaSeries<DicomImageElement> series,
      List<DicomImageElement> dicoms,
      Type mipType,
      Integer extend,
      boolean fullSeries) {

    PlanarImage curImage;
    if (series != null) {

      SeriesComparator sort = (SeriesComparator) view.getActionValue(ActionW.SORT_STACK.cmd());
      Boolean reverse = (Boolean) view.getActionValue(ActionW.INVERSE_STACK.cmd());
      Comparator sortFilter = (reverse != null && reverse) ? sort.getReversOrderComparator() : sort;
      Filter filter = (Filter) view.getActionValue(ActionW.FILTERED_SERIES.cmd());
      Iterable<DicomImageElement> medias = series.copyOfMedias(filter, sortFilter);

      int curImg = extend - 1;
      ActionState sequence = view.getEventManager().getAction(ActionW.SCROLL_SERIES);
      if (sequence instanceof SliderCineListener cineAction) {
        curImg = cineAction.getSliderValue() - 1;
      }

      int minImg = fullSeries ? extend : curImg;
      int maxImg = fullSeries ? series.size(filter) - extend : curImg;

      DicomImageElement img =
          series.getMedia(MediaSeries.MEDIA_POSITION.MIDDLE, filter, sortFilter);
      final Attributes attributes = img.getMediaReader().getDicomObject();
      final int[] COPIED_ATTRS = {
        Tag.SpecificCharacterSet,
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
        Tag.SeriesNumber,
        Tag.KVP,
        Tag.Laterality,
        Tag.BodyPartExamined,
        Tag.FrameOfReferenceUID,
        Tag.RescaleSlope,
        Tag.RescaleIntercept,
        Tag.RescaleType,
        Tag.ModalityLUTSequence,
        Tag.WindowCenter,
        Tag.WindowWidth,
        Tag.VOILUTFunction,
        Tag.WindowCenterWidthExplanation,
        Tag.VOILUTSequence
      };

      Arrays.sort(COPIED_ATTRS);
      final Attributes cpTags = new Attributes(attributes, COPIED_ATTRS);
      cpTags.setString(
          Tag.SeriesDescription,
          VR.LO,
          attributes.getString(Tag.SeriesDescription, "") + " [MIP]"); // NON-NLS
      cpTags.setString(Tag.ImageType, VR.CS, "DERIVED", "SECONDARY", "PROJECTION IMAGE"); // NON-NLS
      String seriesUID = UIDUtils.createUID();

      for (int index = minImg; index <= maxImg; index++) {
        Iterator<DicomImageElement> iter = medias.iterator();
        final List<ImageElement> sources = new ArrayList<>();
        int startIndex = index - extend;
        if (startIndex < 0) {
          startIndex = 0;
        }
        int stopIndex = index + extend;
        int k = 0;
        while (iter.hasNext()) {
          DicomImageElement dcm = iter.next();
          if (k >= startIndex) {
            sources.add(dcm);
          }

          if (k >= stopIndex) {
            break;
          }
          k++;
        }

        if (sources.size() > 1) {
          curImage = addCollectionOperation(mipType, sources);
        } else {
          curImage = null;
        }

        if (curImage != null) {

          DicomImageElement imgRef = (DicomImageElement) sources.get(sources.size() / 2);
          FileRawImage raw = null;
          try {
            File mipDir =
                AppProperties.buildAccessibleTempDirectory(
                    AppProperties.FILE_CACHE_DIR.getName(), "mip"); // NON-NLS
            raw = new FileRawImage(File.createTempFile("mip_", ".wcv", mipDir)); // NON-NLS
            if (!raw.write(curImage)) {
              raw = null;
            }
          } catch (Exception e) {
            if (raw != null) {
              FileUtil.delete(raw.getFile());
              raw = null;
            }
            LOGGER.error("Writing MIP", e);
          }
          if (raw == null) {
            return;
          }
          RawImageIO rawIO = new RawImageIO(raw, null);
          rawIO.setBaseAttributes(cpTags);

          // Tags with same values for all the Series
          rawIO.setTag(TagD.get(Tag.Columns), curImage.width());
          rawIO.setTag(TagD.get(Tag.Rows), curImage.height());
          rawIO.setTag(TagD.get(Tag.BitsAllocated), imgRef.getBitsAllocated());
          rawIO.setTag(TagD.get(Tag.BitsStored), imgRef.getBitsStored());

          int lastIndex = sources.size() - 1;
          rawIO.setTag(
              TagD.get(Tag.SliceThickness),
              getThickness(sources.get(0), sources.get(lastIndex), lastIndex));
          double[] loc = (double[]) imgRef.getTagValue(TagW.SlicePosition);
          if (loc != null) {
            rawIO.setTag(TagW.SlicePosition, loc);
            rawIO.setTag(TagD.get(Tag.SliceLocation), loc[0] + loc[1] + loc[2]);
          }

          rawIO.setTag(TagD.get(Tag.SeriesInstanceUID), seriesUID);

          // Mandatory tags
          org.weasis.dicom.viewer2d.mpr.SeriesBuilder.copyMandatoryTags(img, rawIO);
          TagW[] tagList2;

          tagList2 =
              TagD.getTagFromIDs(
                  Tag.ImageOrientationPatient,
                  Tag.ImagePositionPatient,
                  Tag.PixelPaddingValue,
                  Tag.PixelPaddingRangeLimit,
                  Tag.PixelSpacing,
                  Tag.ImagerPixelSpacing,
                  Tag.NominalScannedPixelSpacing,
                  Tag.PixelSpacingCalibrationDescription,
                  Tag.PixelAspectRatio);
          rawIO.copyTags(tagList2, imgRef, false);

          // Image specific tags
          rawIO.setTag(TagD.get(Tag.SOPInstanceUID), UIDUtils.createUID());
          rawIO.setTag(TagD.get(Tag.InstanceNumber), index + 1);
          dicoms.add(org.weasis.dicom.viewer2d.mpr.SeriesBuilder.buildDicomImageElement(rawIO));
        }
      }
    }
  }

  static double getThickness(ImageElement firstDcm, ImageElement lastDcm, int range) {
    double[] p1 = (double[]) firstDcm.getTagValue(TagW.SlicePosition);
    double[] p2 = (double[]) lastDcm.getTagValue(TagW.SlicePosition);
    if (p1 != null && p2 != null) {
      double diff = Math.abs((p2[0] + p2[1] + p2[2]) - (p1[0] + p1[1] + p1[2]));

      Double t1 = TagD.getTagValue(firstDcm, Tag.SliceThickness, Double.class);
      if (t1 != null) {
        diff += t1 / 2;
      }

      t1 = TagD.getTagValue(lastDcm, Tag.SliceThickness, Double.class);
      if (t1 != null) {
        diff += t1 / 2;
      }

      return diff;
    }
    return range;
  }

  public static PlanarImage addCollectionOperation(Type mipType, List<ImageElement> sources) {
    if (Type.MIN.equals(mipType)) {
      MinCollectionZprojection op = new MinCollectionZprojection(sources);
      return op.computeMinCollectionOpImage();
    }
    if (Type.MEAN.equals(mipType)) {
      MeanCollectionZprojection op = new MeanCollectionZprojection(sources);
      return op.computeMeanCollectionOpImage();
    }
    MaxCollectionZprojection op = new MaxCollectionZprojection(sources);
    return op.computeMaxCollectionOpImage();
  }
}
