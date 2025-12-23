/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer;

import java.nio.file.Path;
import java.util.Map;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.internal.Activator;
import org.weasis.core.api.explorer.DataExplorerViewFactory;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.util.PropertiesUtil;
import org.weasis.plugin.SingletonExtension;

@Extension
@SingletonExtension
public class MediaImporterFactory implements DataExplorerViewFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(MediaImporterFactory.class);

  private AcquireExplorer explorer = null;

  @Override
  public AcquireExplorer createDataExplorerView(Map<String, Object> properties) {
    if (explorer == null) {
      explorer = new AcquireExplorer();
      explorer.initImageGroupPane();
      AcquireManager.getInstance().registerDataExplorerView(explorer);
    }
    return explorer;
  }

  // ================================================================================
  // PF4J plugin lifecycle
  // ================================================================================

  public void stop() {
    if (explorer != null) {
      explorer.saveLastPath();
      AcquireManager.getInstance().unRegisterDataExplorerView();
      String plugInId = GuiUtils.getUICore().getPluginManager().getPluginId(this.getClass());
      Path dataFolder = BundlePreferences.getDataFolder(plugInId);
      PropertiesUtil.storeProperties(
          dataFolder.resolve("publish.properties"), Activator.EXPORT_PERSISTENCE, null);
    }
    LOGGER.info("MediaImporterFactory stopped");
  }
}
