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
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.TagView;

public class DicomEncapDocSeries extends DicomSeries implements FilesExtractor {

  public DicomEncapDocSeries(String subseriesInstanceUID) {
    super(subseriesInstanceUID);
  }

  public DicomEncapDocSeries(
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
    toolTips.append(GuiUtils.HTML_END);
    return toolTips.toString();
  }

  @Override
  public String getMimeType() {
    return DicomMediaIO.SERIES_ENCAP_DOC_MIMETYPE;
  }

  @Override
  public List<File> getExtractFiles() {
    // Should have only one file as all the DicomEncapDocElement items are split in subseries
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
