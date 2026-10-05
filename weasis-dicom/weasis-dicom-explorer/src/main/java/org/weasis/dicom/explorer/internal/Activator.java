/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.internal;

import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.util.LegacyConverters;
import org.weasis.core.util.PropertiesUtil;
import org.weasis.dicom.codec.seg.SegSpecialElement;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.LocalPersistence;
import org.weasis.dicom.explorer.UISegmentationVolumeBuildExecutor;
import org.weasis.dicom.explorer.main.DicomExplorer;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode;
import org.weasis.dicom.explorer.pref.node.AuthenticationPersistence;
import org.weasis.dicom.explorer.wado.DicomManager;
import org.weasis.plugin.PluginContext;
import org.weasis.plugin.WeasisPlugin;

public class Activator extends WeasisPlugin {

  public Activator(PluginContext context) {
    super(context);
  }

  @Override
  public void start() {
    String cache =
        GuiUtils.getUICore().getConfigData().getSystemProps().get("weasis.portable.dicom.cache");
    DicomManager.getInstance()
        .setPortableDirCache(
            !((cache != null) && cache.equalsIgnoreCase(Boolean.FALSE.toString())));
    PropertiesUtil.loadProperties(
        BundlePreferences.getFileInDataFolder(getPluginId(), "import-export.properties"),
        LocalPersistence.getProperties());
    LegacyConverters.register(AuthenticationPersistence.CONVERSION);

    AbstractDicomNode.registerLegacyConversions();

    // Surface canonical segmentation volume builds in the explorer's bottom loading panel,
    // so the user can monitor and cancel long-running SEG volume builds.
    SegSpecialElement.setVolumeBuildExecutor(new UISegmentationVolumeBuildExecutor());
  }

  @Override
  public void stop() {
    SegSpecialElement.setVolumeBuildExecutor(null);
    PropertiesUtil.storeProperties(
        BundlePreferences.getFileInDataFolder(getPluginId(), "import-export.properties"),
        LocalPersistence.getProperties(),
        null);

    DicomModel.LOADING_EXECUTOR.shutdownNow();
    DataExplorerView explorer = GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME);
    if (explorer != null && explorer.getDataExplorerModel() instanceof DicomModel dicomModel) {
      // Remove image in viewers, in image cache and close the image stream
      dicomModel.dispose();
    }
  }
}
