/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.qr;

import java.io.File;
import java.util.Hashtable;
import java.util.Properties;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.explorer.DicomImportFactory;
import org.weasis.dicom.explorer.ImportDicom;

@org.osgi.service.component.annotations.Component(service = DicomImportFactory.class)
public class DicomQrFactory implements DicomImportFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(DicomQrFactory.class);

  public static final Properties IMPORT_PERSISTENCE = new Properties();

  @Override
  public ImportDicom createDicomImportPage(Hashtable<String, Object> properties) {
    if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.import.dicom.qr", true)) {
      return new DicomQrView();
    }
    return null;
  }

  // ================================================================================
  // OSGI service implementation
  // ================================================================================

  @Activate
  protected void activate(ComponentContext context) throws Exception {
    LOGGER.info("DICOM Q/R is activated");
    FileUtil.readProperties(
        new File(BundlePreferences.getDataFolder(context.getBundleContext()), "import.properties"),
        IMPORT_PERSISTENCE);
  }

  @Deactivate
  protected void deactivate(ComponentContext context) {
    LOGGER.info("DICOM Q/R is deactivated");
    FileUtil.storeProperties(
        new File(BundlePreferences.getDataFolder(context.getBundleContext()), "import.properties"),
        IMPORT_PERSISTENCE,
        null);
  }
}
