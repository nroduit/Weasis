/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.send;

import java.io.File;
import java.util.Hashtable;
import java.util.Properties;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.explorer.CheckTreeModel;
import org.weasis.dicom.explorer.DicomExportFactory;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExportDicom;

@org.osgi.service.component.annotations.Component(service = DicomExportFactory.class)
public class SendDicomFactory implements DicomExportFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(SendDicomFactory.class);

  static final Properties EXPORT_PERSISTENCE = new Properties();

  @Override
  public ExportDicom createDicomExportPage(Hashtable<String, Object> properties) {
    if (properties != null
        && GuiUtils.getUICore()
            .getSystemPreferences()
            .getBooleanProperty("weasis.export.dicom.send", true)) {
      DicomModel dicomModel = (DicomModel) properties.get(DicomModel.class.getName());
      CheckTreeModel treeModel = (CheckTreeModel) properties.get(CheckTreeModel.class.getName());
      if (dicomModel != null && treeModel != null) {
        return new SendDicomView(dicomModel, treeModel).initGUI();
      }
    }
    return null;
  }

  // ================================================================================
  // OSGI service implementation
  // ================================================================================

  @Activate
  protected void activate(ComponentContext context) {
    LOGGER.info("DICOM Send is activated");
    FileUtil.readProperties(
        new File(BundlePreferences.getDataFolder(context.getBundleContext()), "export.properties"),
        EXPORT_PERSISTENCE);
  }

  @Deactivate
  protected void deactivate(ComponentContext context) {
    LOGGER.info("DICOM Send is deactivated");
    FileUtil.storeProperties(
        new File(BundlePreferences.getDataFolder(context.getBundleContext()), "export.properties"),
        EXPORT_PERSISTENCE,
        null);
  }
}
