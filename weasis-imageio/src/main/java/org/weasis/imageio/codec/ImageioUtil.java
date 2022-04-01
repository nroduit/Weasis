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

import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.IIOServiceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class ImageioUtil.
 *
 * @author Nicolas Roduit
 */
public final class ImageioUtil {
  private static final Logger LOGGER = LoggerFactory.getLogger(ImageioUtil.class);

  private ImageioUtil() {}

  public static void registerServiceProvider(IIOServiceProvider serviceProvider) {
    try {
      IIORegistry.getDefaultInstance().registerServiceProvider(serviceProvider);
    } catch (Exception e) {
      LOGGER.error("Cannot register IIOServiceProvider", e);
    }
  }

  public static void deregisterServiceProvider(IIOServiceProvider serviceProvider) {
    if (serviceProvider != null) {
      IIORegistry.getDefaultInstance().deregisterServiceProvider(serviceProvider);
    }
  }
}
