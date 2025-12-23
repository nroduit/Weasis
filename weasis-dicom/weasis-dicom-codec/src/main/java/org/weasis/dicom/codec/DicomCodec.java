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

import java.net.URI;
import java.util.Map;
import org.pf4j.Extension;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.plugin.SingletonExtension;

@Extension
@SingletonExtension
public class DicomCodec implements Codec<DicomImageElement> {

  public static final String NAME = "dcm4che"; // NON-NLS
  public static final String[] FILE_EXTENSIONS = {"dcm", "dic", "dicm", "dicom"}; // NON-NLS

  private static final String LOGGER_KEY = "always.info.ItemParser";
  private static final String LOGGER_VAL = "org.dcm4che3.imageio.ItemParser";

  @Override
  public String[] getReaderMIMETypes() {
    return new String[] {
      DicomMediaIO.DICOM_MIMETYPE,
      DicomMediaIO.SERIES_XDSI,
      DicomMediaIO.IMAGE_MIMETYPE,
      DicomMediaIO.SERIES_VIDEO_MIMETYPE,
      DicomMediaIO.SERIES_ENCAP_DOC_MIMETYPE
    };
  }

  @Override
  public String[] getReaderExtensions() {
    return FILE_EXTENSIONS;
  }

  @Override
  public boolean isMimeTypeSupported(String mimeType) {
    if (mimeType != null) {
      for (String mime : getReaderMIMETypes()) {
        if (mimeType.equals(mime)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public MediaReader<DicomImageElement> getMediaIO(
      URI media, String mimeType, Map<String, Object> properties) {
    if (isMimeTypeSupported(mimeType)) {
      return new DicomMediaIO(media);
    }
    return null;
  }

  @Override
  public String getCodecName() {
    return NAME;
  }

  @Override
  public String[] getWriterExtensions() {
    return FILE_EXTENSIONS;
  }

  @Override
  public String[] getWriterMIMETypes() {
    return new String[] {DicomMediaIO.DICOM_MIMETYPE};
  }
}
