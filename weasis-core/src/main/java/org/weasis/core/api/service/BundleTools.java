/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.service;

import java.util.List;
import org.osgi.framework.Constants;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.Codec;

public class BundleTools {

  private BundleTools() {}

  public static Codec getCodec(String mimeType, String preferredCodec) {
    Codec codec = null;
    List<Codec> codecs = GuiUtils.getUICore().getCodecPlugins();
    synchronized (codecs) {
      for (Codec c : codecs) {
        if (c.isMimeTypeSupported(mimeType)) {
          if (c.getCodecName().equals(preferredCodec)) {
            codec = c;
            break;
          }
          // If the preferred codec cannot be found, the first-found codec is retained
          if (codec == null) {
            codec = c;
          }
        }
      }
      return codec;
    }
  }

  public static String createServiceFilter(Class<?>... interfaces) {
    StringBuilder builder = new StringBuilder();

    builder.append("( |");
    for (Class<?> clazz : interfaces) {
      builder.append(String.format("(%s=%s) ", Constants.OBJECTCLASS, clazz.getName())); // NON-NLS
    }

    builder.append(" ) ");
    return builder.toString();
  }
}
