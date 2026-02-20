/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.opencv;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.opencv.natives.NativeLibrary;
import org.weasis.plugin.PluginContext;
import org.weasis.plugin.WeasisPlugin;

public class NativeLoaderPlugin extends WeasisPlugin {
  private static final Logger LOGGER = LoggerFactory.getLogger(NativeLoaderPlugin.class);

  public NativeLoaderPlugin(PluginContext context) {
    super(context);
  }

  @Override
  public void start() {
    try {
      extractAndLoadNatives(this::loadingNativeLibrary);
      LOGGER.info("Successfully loaded native libraries for {}", getPluginId());
    } catch (Exception e) {
      LOGGER.error("Failed to load native libraries", e);
    }
  }

  private void loadingNativeLibrary(Path targetPath) {
    NativeLibrary.loadLibraryFromAbsolutePath(targetPath.toAbsolutePath());
  }
}
