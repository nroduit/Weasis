/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.viewer2d.internal;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.base.viewer2d.View2dContainer;
import org.weasis.core.api.gui.InsertableFactory;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.BundleTools;
import org.weasis.plugin.PluginContext;
import org.weasis.plugin.WeasisPlugin;

public class Activator extends WeasisPlugin {
  private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

  public Activator(PluginContext context) {
    super(context);
  }

  @Override
  public void start() {
    try {
      // Register InsertableFactory extensions from this plugin
      List<InsertableFactory> insertableFactories =
          getPluginWrapper()
              .getPluginManager()
              .getExtensions(InsertableFactory.class, getPluginId());
      for (InsertableFactory factory : insertableFactories) {
        BundleTools.registerInsertableFactory(factory, View2dContainer.UI);
      }

      LOGGER.info("Weasis base-viewer2d plugin started");
    } catch (Exception e) {
      LOGGER.error("Error starting Weasis base-viewer2d plugin", e);
    }
  }

  @Override
  public void stop() {
    try {
      // Save preferences only if EventManager has been initialized
      if (EventManager.hasBeenInitialized()) {
        EventManager.getInstance().savePreferences();
      }

      // Unregister InsertableFactory extensions from this plugin
      List<InsertableFactory> insertableFactories =
          getPluginWrapper()
              .getPluginManager()
              .getExtensions(InsertableFactory.class, getPluginId());
      for (InsertableFactory factory : insertableFactories) {
        BundleTools.unregisterInsertableFactory(factory, View2dContainer.UI);
      }

      GuiUtils.getUICore().closeSeriesViewerType(View2dContainer.class);

      LOGGER.info("Weasis base-viewer2d plugin stopped");
    } catch (Exception e) {
      LOGGER.error("Error stopping Weasis base-viewer2d plugin", e);
    }
  }
}
