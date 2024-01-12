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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.TagView;
import org.weasis.core.util.StringUtil;

public class DicomVideoSeries extends DicomSeries implements FilesExtractor {

  public DicomVideoSeries(String subseriesInstanceUID) {
    super(subseriesInstanceUID);
  }

  public DicomVideoSeries(
      String subseriesInstanceUID, List<DicomImageElement> c, TagView displayTag) {
    super(subseriesInstanceUID, c, displayTag);
  }

  @Override
  public void addMedia(DicomImageElement media) {
    if (media instanceof FileExtractor fileExtractor) {
      fileExtractor.getExtractFile();
    }
    super.addMedia(media);
  }

  @Override
  public String getToolTips() {
    StringBuilder toolTips = DicomSeries.getToolTips(this);
    toolTips.append(Messages.getString("DicomVideo.video_l"));
    toolTips.append(StringUtil.COLON_AND_SPACE);
    Integer frames = TagD.getTagValue(this, Tag.NumberOfFrames, Integer.class);
    if (frames != null) {
      DicomImageElement video = getMedia(MEDIA_POSITION.FIRST, null, null);
      if (video != null) {
        Double frameTime = TagD.getTagValue(video, Tag.FrameTime, Double.class);
        if (frameTime != null) {
          toolTips.append(convertSecondsInTime((int) (frames * frameTime / 1000)));
        }
      }
    }
    toolTips.append(GuiUtils.HTML_BR);
    toolTips.append(GuiUtils.HTML_END);
    return toolTips.toString();
  }

  private static String convertSecondsInTime(int totalSecs) {
    int hours = totalSecs / 3600;
    int minutes = (totalSecs % 3600) / 60;
    int seconds = totalSecs % 60;
    return String.format("%02d:%02d:%02d", hours, minutes, seconds); // NON-NLS
  }

  @Override
  public String getMimeType() {
    return DicomMediaIO.SERIES_VIDEO_MIMETYPE;
  }

  @Override
  public List<File> getExtractFiles() {
    // Should have only one file as all the DicomVideoElement items are split in subseries
    List<File> files = new ArrayList<>();
    getMedias(null, null)
        .forEach(
            dcm -> {
              if (dcm instanceof FileExtractor fileExtractor) {
                files.add(fileExtractor.getExtractFile());
              }
            }); // Synchronized iteration with forEach
    return files;
  }
}
