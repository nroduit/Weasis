/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import java.io.File;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Taggable;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.viewer2d.mpr.MprView.SliceOrientation;

public class DerivedStack extends AbstractStack {

  static final TagW[] mandatoryTags =
      TagD.getTagFromIDs(
          Tag.PatientID,
          Tag.PatientName,
          Tag.PatientBirthDate,
          Tag.StudyInstanceUID,
          Tag.StudyID,
          Tag.SOPClassUID,
          Tag.StudyDate,
          Tag.StudyTime,
          Tag.AccessionNumber,
          Tag.PhotometricInterpretation,
          Tag.PixelRepresentation,
          Tag.Units,
          Tag.SamplesPerPixel,
          Tag.Modality);

  public DerivedStack(SliceOrientation sliceOrientation, MediaSeries<DicomImageElement> series) {
    super(sliceOrientation, series);
  }

  public static DicomImageElement buildDicomImageElement(DcmMediaReader rawIO) {
    return new DicomImageElement(rawIO, 0) {
      @Override
      public Attributes saveToFile(File output, DicomExportParameters params) {
        RawImageIO reader = (RawImageIO) getMediaReader();
        boolean hasTransformation =
            params.dicomEditors() != null && !params.dicomEditors().isEmpty();
        if (!hasTransformation && params.syntax() == null) {
          FileUtil.nioCopyFile(reader.getDicomFile(), output);
          return new Attributes();
        }
        return super.saveToFile(output, params);
      }
    };
  }

  public static void copyMandatoryTags(DicomImageElement img, Taggable taggable) {
    taggable.copyTags(mandatoryTags, img, true);
    taggable.setTag(TagW.PatientPseudoUID, img.getTagValue(TagW.PatientPseudoUID));
    taggable.setTag(TagW.MonoChrome, img.getTagValue(TagW.MonoChrome));
  }
}
