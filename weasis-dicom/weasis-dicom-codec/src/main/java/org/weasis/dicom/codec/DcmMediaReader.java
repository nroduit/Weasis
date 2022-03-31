/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.DicomMetaData;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

public interface DcmMediaReader extends MediaReader {

  Attributes getDicomObject();

  default void writeMetaData(MediaSeriesGroup group) {
    if (group == null) {
      return;
    }
    // Get the dicom header
    Attributes header = getDicomObject();
    DicomMediaUtils.writeMetaData(group, header);

    // Series Group
    if (TagW.SubseriesInstanceUID.equals(group.getTagID())) {
      // Information for series ToolTips
      group.setTagNoNull(TagD.get(Tag.PatientName), getTagValue(TagD.get(Tag.PatientName)));
      group.setTagNoNull(TagD.get(Tag.StudyDescription), header.getString(Tag.StudyDescription));
    }
  }

  DicomMetaData getDicomMetaData();

  boolean isEditableDicom();
}
