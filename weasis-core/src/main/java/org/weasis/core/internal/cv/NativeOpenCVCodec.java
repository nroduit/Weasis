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
import java.util.Hashtable;
import org.opencv.osgi.OpenCVNativeLoader;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.image.cv.ImageCVIO;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaReader;

@org.osgi.service.component.annotations.Component(service = Codec.class)
public class NativeOpenCVCodec implements Codec {
  private static final Logger LOGGER = LoggerFactory.getLogger(NativeOpenCVCodec.class);

  private static final String[] readerMIMETypes = {
    "image/bmp",
    "image/x-bmp", // NON-NLS
    "image/x-windows-bmp", // NON-NLS
    "image/jpeg",
    "image/pjpeg", // NON-NLS
    "image/png",
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
    "image/j2c" // NON-NLS
  };
  private static final String[] readerFileSuffixes = {
    "bm",
    "bmp",
    "dib",
    "jpeg",
    "jpg",
    "jpe",
    "png",
    "x-png",
    "pbm",
    "pgm",
    "ppm",
    "pxm",
    "pnm",
    "ras",
    "rast",
    "tiff",
    "tif",
    "hdr",
    "jp2",
    "jp2k", // NON-NLS
    "j2k",
    "j2c" // NON-NLS
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
  public MediaReader getMediaIO(URI media, String mimeType, Hashtable<String, Object> properties) {
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

  // ================================================================================
  // OSGI service implementation
  // ================================================================================

  @Activate
  protected void activate(ComponentContext context) {
    // Load the native OpenCV library
    OpenCVNativeLoader loader = new OpenCVNativeLoader();
    loader.init();
    LOGGER.info("Native OpenCV is activated");
  }

  @Deactivate
  protected void deactivate(ComponentContext context) {}
}
