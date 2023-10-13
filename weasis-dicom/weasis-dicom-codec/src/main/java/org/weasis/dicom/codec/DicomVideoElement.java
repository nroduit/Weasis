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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.StreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.util.FileUtil;

public class DicomVideoElement extends DicomImageElement implements FileExtractor {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomVideoElement.class);
  private File videoFile = null;

  public DicomVideoElement(DicomMediaIO mediaIO, Object key) {
    super(mediaIO, key);
  }

  @Override
  public File getExtractFile() {
    synchronized (this) {
      if ((videoFile == null || !videoFile.exists()) && getMediaReader() != null) {
        Attributes dcm = getMediaReader().getDicomObject();
        if (dcm != null) {
          VR.Holder holder = new VR.Holder();
          Object pixelData = dcm.getValue(Tag.PixelData, holder);
          if (pixelData instanceof Fragments fragments) {
            readFragments(fragments);
          }
        }
      }
    }
    return videoFile;
  }

  private void readFragments(Fragments fragments) {
    // Should have only 2 fragments: 1) compression marker 2) video stream
    // One fragment shall contain the whole video stream.
    // see http://dicom.nema.org/medical/dicom/current/output/chtml/part05/sect_8.2.5.html
    for (Object data : fragments) {
      if (data instanceof BulkData bulkData) {
        InputStream in = null;
        FileOutputStream out = null;
        try {
          File file =
              File.createTempFile("video_", ".mpg", AppProperties.FILE_CACHE_DIR); // NON-NLS
          in = new BufferedInputStream(bulkData.openStream());
          out = new FileOutputStream(file);
          StreamUtils.copy(in, out, bulkData.length());
          videoFile = file;
        } catch (Exception e) {
          LOGGER.error("Cannot extract video stream", e);
        } finally {
          FileUtil.safeClose(out);
          FileUtil.safeClose(in);
        }
      }
    }
  }

  @Override
  public Attributes saveToFile(File output, DicomExportParameters params) {
    return DicomSpecialElement.saveToFile(this, output, params.dicomEditors());
  }
}
