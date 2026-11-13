/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.internal.cv;

import java.net.URI;
import java.util.Map;
import org.pf4j.Extension;
import org.weasis.core.api.image.cv.ImageCVIO;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.plugin.SingletonExtension;

@Extension
@SingletonExtension
public class NativeOpenCVCodec implements Codec<ImageElement> {

  private static final String[] readerMIMETypes = {
    "image/bmp",
    "image/x-bmp", // NON-NLS
    "image/x-windows-bmp", // NON-NLS
    "image/jpeg",
    "image/pjpeg", // NON-NLS
    "image/png",
    "image/apng", // NON-NLS
    "image/gif",
    "image/x-portable-bitmap",
    "image/x-portable-graymap",
    "image/x-portable-greymap", // NON-NLS
    "image/x-portable-pixmap",
    "image/x-portable-anymap",
    "application/x-portable-anymap", // NON-NLS
    "image/cmu-raster", // NON-NLS
    "application/x-cmu-raster", // NON-NLS
    "image/x-cmu-raster", // NON-NLS
    "image/tiff",
    "image/x-tiff", // NON-NLS
    "image/hdr", // NON-NLS
    "image/jp2", // NON-NLS
    "image/jp2k", // NON-NLS
    "image/j2k", // NON-NLS
    "image/j2c", // NON-NLS
    "image/jxl" // NON-NLS
  };
  private static final String[] readerFileSuffixes = {
    "bm", "bmp", "dib", "jpeg", "jpg", "jpe", "png", "x-png", "apng", "gif", "pbm", "pgm", "ppm",
    "pxm", // NON-NLS
    "pnm", "ras", "rast", // NON-NLS
    "tiff", "tif", "hdr", "jp2", "jp2k", // NON-NLS
    "j2k", "j2c", // NON-NLS
    "jxl"
  };

  private static final String[] writerMIMETypes = {};
  private static final String[] writerFileSuffixes = {};

  @Override
  public String[] getReaderMIMETypes() {
    return readerMIMETypes;
  }

  @Override
  public String[] getReaderExtensions() {
    return readerFileSuffixes;
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
  public MediaReader<ImageElement> getMediaIO(
      URI media, String mimeType, Map<String, Object> properties) {
    if (isMimeTypeSupported(mimeType)) {
      return new ImageCVIO(media, mimeType, this);
    }
    return null;
  }

  @Override
  public String getCodecName() {
    return "OpenCV imgcodecs"; // NON-NLS
  }

  @Override
  public String[] getWriterExtensions() {
    return writerFileSuffixes;
  }

  @Override
  public String[] getWriterMIMETypes() {
    return writerMIMETypes;
  }
}
