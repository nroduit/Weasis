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

import java.util.Map;
import org.pf4j.Extension;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.TransferKind;
import org.weasis.dicom.explorer.exp.CheckTreeModel;
import org.weasis.dicom.explorer.exp.DicomExportFactory;
import org.weasis.dicom.explorer.exp.ExportDicom;
import org.weasis.plugin.SingletonExtension;

@Extension
@SingletonExtension
public class SendDicomFactory implements DicomExportFactory {

  private static final String FEATURE = "weasis.export.dicom.send"; // NON-NLS

  @Override
  public boolean isAvailable() {
    return GuiUtils.getUICore().getSystemPreferences().getBooleanProperty(FEATURE, true);
  }

  @Override
  public TransferKind getKind() {
    return TransferKind.NETWORK;
  }

  @Override
  public ExportDicom createDicomExportPage(Map<String, Object> properties) {
    if (properties != null && isAvailable()) {
      DicomModel dicomModel = (DicomModel) properties.get(DicomModel.class.getName());
      CheckTreeModel treeModel = (CheckTreeModel) properties.get(CheckTreeModel.class.getName());
      if (dicomModel != null && treeModel != null) {
        return new SendDicomView(dicomModel, treeModel).initGUI();
      }
    }
    return null;
  }
}
