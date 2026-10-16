/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.hp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.UIDUtils;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.ref.AnatomicRegion;

/** Series and images built from datasets, on readers that answer the tags the codec registers. */
final class TestSeries {

  /** The instance-level tags the codec keeps on an image, among those the extractor reads. */
  private static final int[] IMAGE_TAGS = {
    Tag.SOPInstanceUID,
    Tag.SOPClassUID,
    Tag.InstanceNumber,
    Tag.Modality,
    Tag.ImageOrientationPatient,
    Tag.ImagePositionPatient,
    Tag.SliceLocation,
    Tag.SliceThickness,
    Tag.DiffusionBValue,
    Tag.NumberOfFrames,
    Tag.FrameOfReferenceUID,
    Tag.PixelSpacing
  };

  private static final int[] SERIES_TAGS = {
    Tag.SeriesInstanceUID,
    Tag.Modality,
    Tag.SOPClassUID,
    Tag.SeriesDescription,
    Tag.Manufacturer,
    Tag.FrameOfReferenceUID,
    Tag.Laterality,
    Tag.BodyPartExamined
  };

  private TestSeries() {}

  static Attributes image(String modality, String sopClass, int instance) {
    Attributes a = new Attributes();
    a.setString(Tag.SOPClassUID, VR.UI, sopClass);
    a.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
    a.setString(Tag.Modality, VR.CS, modality);
    a.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3"); // NON-NLS
    a.setString(Tag.SeriesInstanceUID, VR.UI, "1.2.3.4"); // NON-NLS
    a.setInt(Tag.InstanceNumber, VR.IS, instance);
    return a;
  }

  static Attributes ctSlice(int instance, double z) {
    Attributes a = image("CT", UID.CTImageStorage, instance); // NON-NLS
    a.setString(Tag.ImageType, VR.CS, "ORIGINAL", "PRIMARY", "AXIAL"); // NON-NLS
    a.setDouble(Tag.ImageOrientationPatient, VR.DS, 1, 0, 0, 0, 1, 0);
    a.setDouble(Tag.ImagePositionPatient, VR.DS, -100, -100, z);
    a.setDouble(Tag.SliceLocation, VR.DS, z);
    a.setDouble(Tag.SliceThickness, VR.DS, 2.5);
    a.setDouble(Tag.PixelSpacing, VR.DS, 0.7, 0.7);
    a.setString(Tag.FrameOfReferenceUID, VR.UI, "1.2.3.4.5"); // NON-NLS
    a.setString(Tag.Manufacturer, VR.LO, "SIEMENS"); // NON-NLS
    a.setString(Tag.ConvolutionKernel, VR.SH, "B30f"); // NON-NLS
    a.setString(Tag.BodyPartExamined, VR.CS, "CHEST"); // NON-NLS
    return a;
  }

  static Attributes item(Attributes parent, int tag) {
    Sequence sequence = parent.newSequence(tag, 1);
    Attributes item = new Attributes();
    sequence.add(item);
    return item;
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
    AnatomicRegion region = AnatomicRegion.read(a);
    if (region != null) {
      tags.put(TagW.AnatomicRegion, region);
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
