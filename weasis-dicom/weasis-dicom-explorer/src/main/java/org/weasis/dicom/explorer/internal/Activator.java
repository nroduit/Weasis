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
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.setting.SettingCatalog;
import org.weasis.core.api.setting.SettingsFileStore;
import org.weasis.core.api.util.LegacyConverters;
import org.weasis.core.ui.editor.ViewContexts;
import org.weasis.dicom.codec.seg.SegSpecialElement;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.LocalPersistence;
import org.weasis.dicom.explorer.UISegmentationVolumeBuildExecutor;
import org.weasis.dicom.explorer.hp.HangingPlanApplier;
import org.weasis.dicom.explorer.hp.HpSettings;
import org.weasis.dicom.explorer.hp.SessionActions;
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
    SettingsFileStore.machine()
        .load(getPluginId(), "import-export", LocalPersistence.getProperties()); // NON-NLS
    LegacyConverters.register(AuthenticationPersistence.CONVERSION);

    AbstractDicomNode.registerLegacyConversions();
    SettingCatalog.declare(HpSettings.class);
    GuiUtils.getUICore().addClosingHook(SessionActions::keepOnClose);
    // a hanging lands after the viewer was selected: the view context learns the protocol then
    HangingPlanApplier.addListener(
        applied ->
            GuiExecutor.execute(
                () ->
                    GuiUtils.getUICore()
                        .getSelectedPlugin()
                        .filter(applied.viewers()::contains)
                        .ifPresent(ViewContexts::publish)));

    // Surface canonical segmentation volume builds in the explorer's bottom loading panel,
    // so the user can monitor and cancel long-running SEG volume builds.
    SegSpecialElement.setVolumeBuildExecutor(new UISegmentationVolumeBuildExecutor());
  }

  @Override
  public void stop() {
    SegSpecialElement.setVolumeBuildExecutor(null);
    SettingsFileStore.machine()
        .store(getPluginId(), "import-export", LocalPersistence.getProperties()); // NON-NLS

    DicomModel.LOADING_EXECUTOR.shutdownNow();
    DataExplorerView explorer = GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME);
    if (explorer != null && explorer.getDataExplorerModel() instanceof DicomModel dicomModel) {
      // Remove image in viewers, in image cache and close the image stream
      dicomModel.dispose();
    }
  }
}
