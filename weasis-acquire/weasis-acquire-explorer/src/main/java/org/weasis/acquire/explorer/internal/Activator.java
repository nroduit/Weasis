/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.internal;

import java.nio.file.Path;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.MediaImporterFactory;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.util.PropertiesUtil;
import org.weasis.plugin.WeasisPlugin;

public class Activator extends WeasisPlugin {
  private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

  public static final Properties EXPORT_PERSISTENCE = new Properties();

  public Activator(org.weasis.plugin.PluginContext context) {
    super(context);
  }

  @Override
  public void start() {
    Path dataFolder = BundlePreferences.getDataFolder(getPluginId());
    PropertiesUtil.loadProperties(dataFolder.resolve("publish.properties"), EXPORT_PERSISTENCE);
    LOGGER.info("MediaImporterFactory started");
  }

  @Override
  public void stop() {
    getPluginWrapper().getPluginManager().getExtensions(MediaImporterFactory.class).stream()
        .findFirst()
        .ifPresent(MediaImporterFactory::stop);
  }
}
