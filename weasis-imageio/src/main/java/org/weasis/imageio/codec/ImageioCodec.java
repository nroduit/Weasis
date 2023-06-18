/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.imageio.codec;

import java.net.URI;
import java.util.Hashtable;
import javax.imageio.ImageIO;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.weasis.core.api.image.cv.ImageCVIO;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaReader;

@org.osgi.service.component.annotations.Component(service = Codec.class)
public class ImageioCodec implements Codec {

  public static final String NAME = "JDK ImageIO"; // NON-NLS

  @Override
  public String[] getReaderMIMETypes() {
    return ImageIO.getReaderMIMETypes();
  }

  @Override
  public String[] getReaderExtensions() {
    return ImageIO.getReaderFileSuffixes();
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
    return NAME;
  }

  @Override
  public String[] getWriterExtensions() {
    return ImageIO.getWriterFileSuffixes();
  }

  @Override
  public String[] getWriterMIMETypes() {
    return ImageIO.getWriterMIMETypes();
  }

  // ================================================================================
  // OSGI service implementation
  // ================================================================================

  @Activate
  protected void activate(ComponentContext context) {
    // Do not use cache. Images must be downloaded locally before reading them.
    ImageIO.setUseCache(false);
  }

  @Deactivate
  protected void deactivate(ComponentContext context) {}
}
