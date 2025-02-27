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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.TimeZone;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.DicomMetaData;
import org.dcm4che3.img.util.DateTimeUtils;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Taggable;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

public interface DcmMediaReader extends MediaReader<DicomImageElement> {

  @Override
  DicomSeries getMediaSeries();

  Attributes getDicomObject();

  default void writeMetaData(MediaSeriesGroup group) {
    if (group == null) {
      return;
    }
    // Get the dicom header
    Attributes header = getDicomObject();
    DicomMediaUtils.writeMetaData(group, header);

    if (TagD.get(Tag.StudyInstanceUID).equals(group.getTagID())) {
      addTimeZone(group, header);
    }

    // Series Group
    if (TagW.SubseriesInstanceUID.equals(group.getTagID())) {
      // Information for series ToolTips
      group.setTagNoNull(TagD.get(Tag.PatientName), getTagValue(TagD.get(Tag.PatientName)));
      group.setTagNoNull(TagD.get(Tag.StudyDescription), header.getString(Tag.StudyDescription));
      addTimeZone(group, header);
    }
  }

  static void addTimeZone(Taggable taggable, Attributes header) {
    if (taggable == null || header == null) {
      return;
    }
    TimeZone timeZone = header.getTimeZone();
    if (timeZone != null && !timeZone.equals(header.getDefaultTimeZone())) {
      taggable.setTag(TagW.Timezone, timeZone);
    }
  }

  static String buildDateTimeWithTimeZone(TagReadable readable, int dateTag, int timeTag) {
    LocalDate date = TagD.getTagValue(readable, dateTag, LocalDate.class);
    LocalTime time = TagD.getTagValue(readable, timeTag, LocalTime.class);
    LocalDateTime dateTime = DateTimeUtils.dateTime(date, time);
    if (dateTime != null) {
      TimeZone timeZone = TagW.getTagValue(readable, TagW.Timezone, TimeZone.class);
      ZonedDateTime zonedDateTime = TagUtil.getZonedDateTime(dateTime, timeZone);
      return TagUtil.formatDateTime(zonedDateTime);
    }
    return StringUtil.EMPTY_STRING;
  }

  DicomMetaData getDicomMetaData();

  boolean isEditableDicom();
}
