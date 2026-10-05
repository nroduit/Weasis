/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.qr.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.util.LegacyConverters;
import org.weasis.core.util.PropertiesUtil;
import org.weasis.dicom.qr.DicomQrView;
import org.weasis.dicom.qr.SearchParameters;
import org.weasis.plugin.PluginContext;
import org.weasis.plugin.WeasisPlugin;

public class Activator extends WeasisPlugin {
  private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

  public Activator(PluginContext context) {
    super(context);
  }

  @Override
  public void start() {
    LOGGER.info("DICOM Q/R is activated");
    LegacyConverters.register(SearchParameters.CONVERSION);
    PropertiesUtil.loadProperties(
        BundlePreferences.getFileInDataFolder(getPluginId(), "import.properties"),
        DicomQrView.getPersistence());
  }

  @Override
  public void stop() {
    LOGGER.info("DICOM Q/R is deactivated");
    PropertiesUtil.storeProperties(
        BundlePreferences.getFileInDataFolder(getPluginId(), "import.properties"),
        DicomQrView.getPersistence(),
        null);
  }
}
