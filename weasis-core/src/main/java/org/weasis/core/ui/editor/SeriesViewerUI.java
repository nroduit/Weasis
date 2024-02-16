/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

import java.util.Hashtable;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.InsertableFactory;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.service.UICore;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.ExtToolFactory;
import org.weasis.core.ui.util.Toolbar;

public class SeriesViewerUI {

  public final AtomicBoolean init;
  public final Class<?> clazz;
  public final List<Toolbar> toolBars;
  public final List<DockableTool> tools;
  public final List<InsertableFactory> dynamicTools;

  public SeriesViewerUI(Class<?> clazz) {
    this(clazz, null, null, null);
  }

  public SeriesViewerUI(
      Class<?> clazz,
      List<Toolbar> toolBars,
      List<DockableTool> tools,
      List<InsertableFactory> dynamicTools) {
    this.init = new AtomicBoolean(false);
    this.clazz = Objects.requireNonNull(clazz);
    this.toolBars = toolBars == null ? new CopyOnWriteArrayList<>() : toolBars;
    this.tools = tools == null ? new CopyOnWriteArrayList<>() : tools;
    this.dynamicTools = dynamicTools == null ? new CopyOnWriteArrayList<>() : dynamicTools;
  }

  public List<Toolbar> getToolBars() {
    return toolBars;
  }

  public List<DockableTool> getTools() {
    return tools;
  }

  public List<InsertableFactory> getDynamicTools() {
    return dynamicTools;
  }

  public static void updateTools(SeriesViewer<?> oldPlugin, SeriesViewer<?> plugin, boolean force) {
    List<DockableTool> oldTool = oldPlugin == null ? null : oldPlugin.getSeriesViewerUI().tools;
    List<DockableTool> tool = plugin == null ? null : plugin.getSeriesViewerUI().tools;
    if (force || !Objects.equals(tool, oldTool)) {
      if (oldTool != null) {
        for (DockableTool p : oldTool) {
          p.closeDockable();
        }
      }
      if (tool != null) {
        for (DockableTool p : tool) {
          if (p.isComponentEnabled()) {
            p.showDockable();
          }
        }
      }
    }
    if (oldPlugin != null) {
      for (InsertableFactory factory : oldPlugin.getSeriesViewerUI().dynamicTools) {
        if (factory instanceof ExtToolFactory<?> extToolFactory) {
          extToolFactory.hideTool();
        }
      }
    }
  }

  public static void updateToolbars(
      SeriesViewer<?> oldPlugin, SeriesViewer<?> plugin, boolean force) {
    List<Toolbar> oldToolBars = oldPlugin == null ? null : oldPlugin.getSeriesViewerUI().toolBars;
    List<Toolbar> toolBars = plugin == null ? null : plugin.getSeriesViewerUI().toolBars;
    if (force || !Objects.equals(toolBars, oldToolBars)) {
      if (toolBars == null) {
        toolBars = UICore.getInstance().getExplorerPluginToolbars();
      }
      UICore.getInstance().getToolbarContainer().registerToolBar(toolBars);
    }
  }

  public void updateDynamicTools(MediaSeries<?> series) {
    if (dynamicTools.isEmpty()) {
      return;
    }

    Hashtable<String, Object> properties = new Hashtable<>();
    if (series != null) {
      properties.put(MediaSeries.class.getName(), series);
      properties.put("class.container", clazz.getName());
    }

    for (InsertableFactory factory : dynamicTools) {
      Insertable instance = factory.createInstance(properties);
      if (instance instanceof DockableTool dockableTool) {
        dockableTool.showDockable();
      } else if (factory instanceof ExtToolFactory<?> extToolFactory) {
        extToolFactory.hideTool();
      }
    }
  }
}
