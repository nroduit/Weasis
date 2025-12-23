/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.internal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableFactory;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.View2dContainer;
import org.weasis.dicom.viewer2d.mpr.MprContainer;
import org.weasis.plugin.PluginContext;
import org.weasis.plugin.WeasisPlugin;

public class Activator extends WeasisPlugin {

  private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

  private final Map<String, List<InsertableFactory>> extensionCache = new HashMap<>();

  public Activator(PluginContext context) {
    super(context);
  }

  @Override
  public void start() {

    // Register tools and toolbars
    BundleTools.registerExistingComponents(View2dContainer.UI);

    // Start listening for InsertableFactory extensions from other plugins
    GuiUtils.getUICore()
        .getPluginManager()
        .addPluginStateListener(
            event -> {
              if (event.getPluginState() == PluginState.STARTED) {
                onExtensionsAdded(event.getPlugin());
              } else if (event.getPluginState() == PluginState.STOPPED) {
                onExtensionsRemoved(event.getPlugin());
              }
            });
  }

  private void onExtensionsAdded(PluginWrapper plugin) {
    List<InsertableFactory> extensions =
        plugin.getPluginManager().getExtensions(InsertableFactory.class, plugin.getPluginId());

    List<InsertableFactory> filteredExtensions =
        extensions.stream()
            .filter(
                f ->
                    Insertable.Type.TOOLBAR.equals(f.getType())
                        || Insertable.Type.TOOL.equals(f.getType())
                        || Type.TOOL_EXT.equals(f.getType()))
            .toList();

    extensionCache.put(plugin.getPluginId(), filteredExtensions);
    for (InsertableFactory factory : filteredExtensions) {
      GuiExecutor.execute(() -> BundleTools.registerInsertableFactory(factory, View2dContainer.UI));
    }
  }

  private void onExtensionsRemoved(PluginWrapper plugin) {
    List<InsertableFactory> removed = extensionCache.remove(plugin.getPluginId());
    if (removed != null) {
      for (InsertableFactory factory : removed) {
        GuiExecutor.execute(
            () -> BundleTools.unregisterInsertableFactory(factory, View2dContainer.UI));
      }
    }
  }

  @Override
  public void stop() {
    extensionCache.clear();

    // Save preferences
    ImageViewerPlugin<DicomImageElement> container =
        EventManager.getInstance().getSelectedView2dContainer();
    if (container instanceof MprContainer) {
      // Remove crosshair tool
      container.setSelected(false);
    }
    EventManager.getInstance().savePreferences();

    GuiUtils.getUICore().closeSeriesViewerType(MprContainer.class);
    GuiUtils.getUICore().closeSeriesViewerType(View2dContainer.class);
  }
}
