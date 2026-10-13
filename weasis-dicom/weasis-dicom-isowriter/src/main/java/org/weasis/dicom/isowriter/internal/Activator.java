/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.isowriter.internal;

import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.setting.SettingsFileStore;
import org.weasis.plugin.WeasisPlugin;

public class Activator extends WeasisPlugin {
  private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

  public static final Properties EXPORT_PERSISTENCE = new Properties();

  public Activator(org.weasis.plugin.PluginContext context) {
    super(context);
  }

  @Override
  public void start() {
    LOGGER.info("Export ISO image is activated");
    SettingsFileStore.machine().load(getPluginId(), "export", EXPORT_PERSISTENCE); // NON-NLS
  }

  @Override
  public void stop() {
    LOGGER.info("Export ISO image is deactivated");
    SettingsFileStore.machine().store(getPluginId(), "export", EXPORT_PERSISTENCE); // NON-NLS
  }
}
