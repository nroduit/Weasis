/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.UIDUtils;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;

public class AcquireMediaInfo {

  protected final MediaElement media;
  protected final Attributes attributes;
  private AcquireImageStatus status;
  private SeriesGroup seriesGroup;

  public AcquireMediaInfo(MediaElement media) {
    this.media = Objects.requireNonNull(media);

    // Create a SOPInstanceUID if not present
    TagW tagUid = TagD.getUID(Level.INSTANCE);
    String uuid = (String) media.getTagValue(tagUid);
    if (uuid == null) {
      uuid = UIDUtils.createUID();
      media.setTag(tagUid, uuid);
    }

    this.attributes = new Attributes();
    attributes.setString(Tag.SpecificCharacterSet, VR.CS, "ISO_IR 192"); // NON-NLS

    setContentDateTime(media, null);
    setStatus(AcquireImageStatus.TO_PUBLISH);
  }

  public Attributes getAttributes() {
    return attributes;
  }

  public AcquireImageStatus getStatus() {
    return status;
  }

  public void setStatus(AcquireImageStatus status) {
    this.status = Objects.requireNonNull(status);
  }

  public SeriesGroup getSeries() {
    return seriesGroup;
  }

  public void setSeries(SeriesGroup seriesGroup) {
    this.seriesGroup = seriesGroup;
    if (seriesGroup != null) {
      media.setTag(TagD.get(Tag.SeriesInstanceUID), seriesGroup.getUID());

      String seriesDescription = TagD.getTagValue(seriesGroup, Tag.SeriesDescription, String.class);
      if (!StringUtil.hasText(seriesDescription)
          && seriesGroup.getType() != SeriesGroup.Type.NONE) {
        seriesGroup.setTag(TagD.get(Tag.SeriesDescription), seriesGroup.getDisplayName());
      }
    }
  }

  public MediaElement getMedia() {
    return media;
  }

  public String getUID() {
    return TagD.getTagValue(media, Tag.SOPInstanceUID, String.class);
  }

  @Override
  public String toString() {
    return Optional.ofNullable(seriesGroup).map(SeriesGroup::getUID).orElse("Unknown Media");
  }

  public void setSpecificTags() {
    if (media instanceof ImageElement || seriesGroup == null) {
      return;
    }

    // Set specific tags for non-image media
    String mime = media.getMimeType();
    if (mime != null) {
      String modality;
      String title;
      if ("application/pdf".equals(mime)) {
        modality = "OT"; // NON-NLS
        title = "Encapsulated PDF"; // NON-NLS
      } else if (mime.startsWith("video/")) {
        modality = "XC"; // NON-NLS
        title = "Video"; // NON-NLS
      } else if (mime.startsWith("audio/")) {
        modality = "AU"; // NON-NLS
        title = "Audio"; // NON-NLS
      } else if ("application/SLA".equals(mime) || "model/stl".equals(mime)) {
        modality = "M3D"; // NON-NLS
        title = "STL 3D Model"; // NON-NLS
      } else {
        modality = "OT"; // NON-NLS
        title = "Encapsulated Media";
      }
      seriesGroup.setTag(TagD.get(Tag.Modality), modality);
      seriesGroup.setTag(TagD.get(Tag.SeriesDescription), title);
    }
  }

  protected static void setContentDateTime(MediaElement media, LocalDateTime dateTime) {
    if (dateTime == null) {
      dateTime =
          LocalDateTime.from(
              Instant.ofEpochMilli(media.getLastModified()).atZone(ZoneId.systemDefault()));
    }
    media.setTagNoNull(TagD.get(Tag.ContentDate), dateTime.toLocalDate());
    media.setTagNoNull(TagD.get(Tag.ContentTime), dateTime.toLocalTime());
  }
}
