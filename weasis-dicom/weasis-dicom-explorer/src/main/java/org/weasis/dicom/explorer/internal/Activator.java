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

import java.io.File;
import org.osgi.annotation.bundle.Header;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.LocalPersistence;
import org.weasis.dicom.explorer.wado.DicomManager;

@Header(name = Constants.BUNDLE_ACTIVATOR, value = "${@class}") // NON-NLS
public class Activator implements BundleActivator {

  @Override
  public void start(final BundleContext context) throws Exception {
    String cache = context.getProperty("weasis.portable.dicom.cache");
    DicomManager.getInstance()
        .setPortableDirCache(
            !((cache != null) && cache.equalsIgnoreCase(Boolean.FALSE.toString())));
    FileUtil.readProperties(
        new File(BundlePreferences.getDataFolder(context), "import-export.properties"),
        LocalPersistence.getProperties());
  }

  @Override
  public void stop(BundleContext context) throws Exception {
    FileUtil.storeProperties(
        new File(BundlePreferences.getDataFolder(context), "import-export.properties"),
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
