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
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.StreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;

public class DicomEncapDocElement extends DicomImageElement implements FileExtractor {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomEncapDocElement.class);
  private File document = null;

  public DicomEncapDocElement(DicomMediaIO mediaIO, Object key) {
    super(mediaIO, key);
  }

  @Override
  public String getMimeType() {
    String val = TagD.getTagValue(this, Tag.MIMETypeOfEncapsulatedDocument, String.class);
    return val == null ? super.getMimeType() : val;
  }

  @Override
  public File getExtractFile() {
    synchronized (this) {
      if ((document == null || !document.exists()) && getMediaReader() != null) {
        String extension = ".tmp";
        Attributes ds = getMediaReader().getDicomObject();
        String mime = ds.getString(Tag.MIMETypeOfEncapsulatedDocument);
        String ext = MimeInspector.getExtensions(mime);
        if (StringUtil.hasText(extension)) {
          extension = "." + ext;
        }
        // see http://dicom.nema.org/MEDICAL/Dicom/current/output/chtml/part03/sect_C.24.2.html
        Object data = ds.getValue(Tag.EncapsulatedDocument);
        readEncapsulatedDocument(data, extension);
      }
    }
    return document;
  }

  private void readEncapsulatedDocument(Object data, String extension) {
    if (data instanceof BulkData bulkData) {
      BufferedInputStream in = null;
      FileOutputStream out = null;
      try {
        File file = File.createTempFile("encap_", extension, AppProperties.FILE_CACHE_DIR);
        in = new BufferedInputStream(bulkData.openStream());
        out = new FileOutputStream(file);
        StreamUtils.copy(in, out, bulkData.length());
        document = file;
      } catch (Exception e) {
        LOGGER.error("Cannot extract encapsulated document", e);
      } finally {
        FileUtil.safeClose(out);
        FileUtil.safeClose(in);
      }
    }
  }

  @Override
  public Attributes saveToFile(File output, DicomExportParameters params) {
    return DicomSpecialElement.saveToFile(this, output, params.dicomEditors());
  }
}
