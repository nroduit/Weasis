/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.hp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;

/** Image series built from datasets, without files, for the explorer tests of the engine. */
final class ExplorerFixtures {

  private static final int[] IMAGE_TAGS = {
    Tag.SOPInstanceUID,
    Tag.SOPClassUID,
    Tag.InstanceNumber,
    Tag.Modality,
    Tag.ImageOrientationPatient,
    Tag.ImagePositionPatient,
    Tag.SliceThickness,
    Tag.ConvolutionKernel,
    Tag.PixelSpacing
  };

  private static final int[] SERIES_TAGS = {
    Tag.SeriesInstanceUID, Tag.Modality, Tag.SOPClassUID, Tag.SeriesDescription, Tag.Manufacturer
  };

  private ExplorerFixtures() {}

  /** An axial CT series of one image with the given kernel. */
  static DicomSeries ctSeries(String uid, String kernel) {
    Attributes a = new Attributes();
    a.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2"); // NON-NLS
    a.setString(Tag.SOPInstanceUID, VR.UI, uid + ".1"); // NON-NLS
    a.setString(Tag.SeriesInstanceUID, VR.UI, uid);
    a.setString(Tag.Modality, VR.CS, "CT"); // NON-NLS
    a.setString(Tag.Manufacturer, VR.LO, "SIEMENS"); // NON-NLS
    a.setString(Tag.ImageType, VR.CS, "ORIGINAL", "PRIMARY", "AXIAL"); // NON-NLS
    a.setInt(Tag.InstanceNumber, VR.IS, 1);
    a.setDouble(Tag.ImageOrientationPatient, VR.DS, 1, 0, 0, 0, 1, 0);
    a.setDouble(Tag.ImagePositionPatient, VR.DS, -100, -100, 0);
    a.setDouble(Tag.SliceThickness, VR.DS, 1.25);
    a.setDouble(Tag.PixelSpacing, VR.DS, 0.7, 0.7);
    a.setString(Tag.ConvolutionKernel, VR.SH, kernel);
    a.setString(Tag.SeriesDescription, VR.LO, kernel);
    DicomSeries series = new DicomSeries(uid);
    for (int tag : SERIES_TAGS) {
      TagW key = TagD.get(tag);
      Object value = key.getValue(a);
      if (value != null) {
        series.setTag(key, value);
      }
    }
    series.addMedia(element(a));
    return series;
  }

  private static DicomImageElement element(Attributes a) {
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
}
