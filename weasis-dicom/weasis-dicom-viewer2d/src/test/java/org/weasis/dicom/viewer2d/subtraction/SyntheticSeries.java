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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.UIDUtils;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;

/** Series without pixels, enough for the geometry checks of the compatibility. */
final class SyntheticSeries {

  static final String STUDY = "1.2.3"; // NON-NLS
  static final String FRAME = "1.2.3.4.5"; // NON-NLS

  private static final int[] IMAGE_TAGS = {
    Tag.PatientID,
    Tag.SOPInstanceUID,
    Tag.SOPClassUID,
    Tag.InstanceNumber,
    Tag.Modality,
    Tag.StudyInstanceUID,
    Tag.SeriesInstanceUID,
    Tag.ImageOrientationPatient,
    Tag.ImagePositionPatient,
    Tag.SliceThickness,
    Tag.FrameOfReferenceUID,
    Tag.PixelSpacing,
    Tag.Rows,
    Tag.Columns,
    Tag.ContrastBolusAgent,
    Tag.TemporalPositionIdentifier,
    Tag.TemporalPositionIndex,
    Tag.TriggerTime,
    Tag.AcquisitionTime
  };

  private static final int[] SERIES_TAGS = {
    Tag.SeriesInstanceUID, Tag.Modality, Tag.SeriesDescription, Tag.SeriesNumber
  };

  private SyntheticSeries() {}

  /** An MR series of {@code count} axial slices spaced 2 mm apart, the first one at {@code z0}. */
  static DicomSeries mr(String seriesUid, int count, double z0) {
    return mr(seriesUid, count, z0, 1.0, 256, FRAME);
  }

  static DicomSeries mr(
      String seriesUid, int count, double z0, double spacing, int matrix, String frame) {
    Attributes[] slices = new Attributes[count];
    for (int i = 0; i < count; i++) {
      slices[i] = slice(seriesUid, "MR", i + 1, z0 + 2.0 * i, spacing, matrix, frame); // NON-NLS
    }
    return series(slices);
  }

  static Attributes slice(
      String seriesUid,
      String modality,
      int instance,
      double z,
      double spacing,
      int matrix,
      String frame) {
    Attributes a = new Attributes();
    a.setString(Tag.SOPClassUID, VR.UI, UID.MRImageStorage);
    a.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
    a.setString(Tag.Modality, VR.CS, modality);
    a.setString(Tag.PatientID, VR.LO, "P1"); // NON-NLS
    a.setString(Tag.StudyInstanceUID, VR.UI, STUDY);
    a.setString(Tag.SeriesInstanceUID, VR.UI, seriesUid);
    a.setInt(Tag.InstanceNumber, VR.IS, instance);
    a.setDouble(Tag.ImageOrientationPatient, VR.DS, 1, 0, 0, 0, 1, 0);
    a.setDouble(Tag.ImagePositionPatient, VR.DS, -100, -100, z);
    a.setDouble(Tag.SliceThickness, VR.DS, 2.0);
    a.setDouble(Tag.PixelSpacing, VR.DS, spacing, spacing);
    a.setInt(Tag.Rows, VR.US, matrix);
    a.setInt(Tag.Columns, VR.US, matrix);
    if (frame != null) {
      a.setString(Tag.FrameOfReferenceUID, VR.UI, frame);
    }
    return a;
  }

  static DicomImageElement element(Attributes a) {
    Map<TagW, Object> tags = new HashMap<>();
    for (int tag : IMAGE_TAGS) {
      TagW key = TagD.get(tag);
      Object value = key.getValue(a);
      if (value != null) {
        tags.put(key, value);
      }
    }
    DcmMediaReader reader = mock(DcmMediaReader.class);
    when(reader.getMediaFragmentTags(any())).thenReturn(tags);
    when(reader.getTagValue(any())).thenAnswer(inv -> ((TagW) inv.getArgument(0)).getValue(a));
    when(reader.getDicomObject()).thenReturn(a);
    return new DicomImageElement(reader, 0);
  }

  /** A series tagged from its first image, holding one element per dataset. */
  static DicomSeries series(Attributes... images) {
    DicomSeries series = new DicomSeries(images[0].getString(Tag.SeriesInstanceUID));
    for (int tag : SERIES_TAGS) {
      TagW key = TagD.get(tag);
      Object value = key.getValue(images[0]);
      if (value != null) {
        series.setTag(key, value);
      }
    }
    for (Attributes image : images) {
      series.addMedia(element(image));
    }
    return series;
  }
}
