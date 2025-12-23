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
import java.util.Map;
import javax.imageio.ImageIO;
import org.pf4j.Extension;
import org.weasis.core.api.image.cv.ImageCVIO;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.plugin.SingletonExtension;

@Extension
@SingletonExtension
public class ImageioCodec implements Codec<ImageElement> {

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
  public MediaReader<ImageElement> getMediaIO(
      URI media, String mimeType, Map<String, Object> properties) {
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
}
