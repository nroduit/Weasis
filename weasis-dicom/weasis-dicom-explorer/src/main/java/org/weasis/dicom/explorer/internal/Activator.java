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

import org.osgi.annotation.bundle.Header;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.util.PropertiesUtil;
import org.weasis.dicom.codec.seg.SegSpecialElement;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.LocalPersistence;
import org.weasis.dicom.explorer.UISegmentationVolumeBuildExecutor;
import org.weasis.dicom.explorer.main.DicomExplorer;
import org.weasis.dicom.explorer.wado.DicomManager;

@Header(name = Constants.BUNDLE_ACTIVATOR, value = "${@class}") // NON-NLS
public class Activator implements BundleActivator {

  @Override
  public void start(final BundleContext context) {
    String cache = context.getProperty("weasis.portable.dicom.cache");
    DicomManager.getInstance()
        .setPortableDirCache(
            !((cache != null) && cache.equalsIgnoreCase(Boolean.FALSE.toString())));
    PropertiesUtil.loadProperties(
        BundlePreferences.getFileInDataFolder(context, "import-export.properties"),
        LocalPersistence.getProperties());

    // Surface canonical segmentation volume builds in the explorer's bottom loading panel,
    // so the user can monitor and cancel long-running SEG volume builds.
    SegSpecialElement.setVolumeBuildExecutor(new UISegmentationVolumeBuildExecutor());
  }

  @Override
  public void stop(BundleContext context) {
    SegSpecialElement.setVolumeBuildExecutor(null);
    PropertiesUtil.storeProperties(
        BundlePreferences.getFileInDataFolder(context, "import-export.properties"),
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
