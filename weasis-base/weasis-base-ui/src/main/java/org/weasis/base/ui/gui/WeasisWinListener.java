/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.ui.gui;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.pf4j.PluginManager;
import org.pf4j.PluginState;
import org.pf4j.PluginStateEvent;
import org.pf4j.PluginStateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.DataExplorerViewFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.ObservableEvent.BasicAction;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.SidePanel;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.SeriesViewerUI;
import org.weasis.core.ui.editor.ViewerOpenOptions;
import org.weasis.core.ui.editor.ViewerPlacement;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.core.util.StringUtil;

public class WeasisWinListener implements PropertyChangeListener, PluginStateListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(WeasisWinListener.class);
  private final WeasisWin mainWindow;

  /** Listens once {@link #activate()} is called (by the plugin start), never twice. */
  public WeasisWinListener(WeasisWin mainWindow) {
    this.mainWindow = mainWindow;
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (mainWindow == null) {
      return;
    }

    ViewerPlugin<?> selectedPlugin = mainWindow.getSelectedPlugin();
    // Get only ObservableEvent
    if (evt instanceof ObservableEvent event) {
      ObservableEvent.BasicAction action = event.getActionCommand();
      Object source = event.getNewValue();
      if (evt.getSource() instanceof DataExplorerModel) {
        if (ObservableEvent.BasicAction.SELECT.equals(action)) {
          if (source instanceof DataExplorerModel model) {
            // data arrives in a model from outside (a launch, a load or unload command)
            showExplorerOf(model);
          }
          // Select a plugin from that as the same key as the MediaSeriesGroup
          else if (source instanceof MediaSeriesGroup group) {
            // If already selected, do not reselect or select a second window
            if (selectedPlugin == null || !group.equals(selectedPlugin.getGroupID())) {
              List<ViewerPlugin<?>> viewerPlugins = GuiUtils.getUICore().getViewerPlugins();
              synchronized (viewerPlugins) {
                for (int i = viewerPlugins.size() - 1; i >= 0; i--) {
                  ViewerPlugin<?> p = viewerPlugins.get(i);
                  if (group.equals(p.getGroupID())) {
                    p.setSelectedAndGetFocus();
                    break;
                  }
                }
              }
            }
          }
        } else if (ObservableEvent.BasicAction.REGISTER.equals(action)) {
          if (source instanceof ViewerPlugin<?> viewerPlugin) {
            mainWindow.registerPlugin(viewerPlugin);
          } else if (source instanceof ViewerPluginBuilder builder) {
            DataExplorerModel model = builder.getModel();
            List<MediaSeries<MediaElement>> series = builder.getSeries();
            ViewerOpenOptions opts = builder.getViewerOpenOptions();
            if (series != null
                && opts.placement() instanceof ViewerPlacement.ReuseViewer reuse
                && model.getTreeModelNodeForNewPlugin() != null
                && model instanceof TreeModel treeModel) {
              boolean inSelView =
                  reuse.openInSelection()
                      && builder.getFactory().isViewerCreatedByThisFactory(selectedPlugin);

              if (series.size() == 1) {
                MediaSeries<MediaElement> s = series.getFirst();
                MediaSeriesGroup group =
                    treeModel.getParent(s, model.getTreeModelNodeForNewPlugin());
                if (inSelView && !s.getMimeType().contains("dicom")) { // NON-NLS
                  // Change the group attribution. DO NOT use it with DICOM.
                  group = selectedPlugin.getGroupID();
                }
                mainWindow.openSeriesInViewerPlugin(builder, group);
              } else if (series.size() > 1) {
                HashMap<MediaSeriesGroup, List<MediaSeries<?>>> map =
                    mainWindow.getSeriesByEntry(
                        treeModel, series, model.getTreeModelNodeForNewPlugin());
                for (Map.Entry<MediaSeriesGroup, List<MediaSeries<?>>> entry : map.entrySet()) {
                  MediaSeriesGroup group = entry.getKey();

                  if (inSelView) {
                    List<MediaSeries<?>> seriesList = entry.getValue();
                    if (!seriesList.isEmpty()) {
                      // Change the group attribution. DO NOT use it with DICOM.
                      if (!seriesList.getFirst().getMimeType().contains("dicom")) { // NON-NLS
                        group = selectedPlugin.getGroupID();
                      }
                    }
                  }
                  mainWindow.openSeriesInViewerPlugin(builder, group);
                }
              }

            } else {
              mainWindow.openSeriesInViewerPlugin(builder, null);
            }
          }
        } else if (ObservableEvent.BasicAction.UNREGISTER.equals(action)) {
          if (source instanceof SeriesViewerFactory viewerFactory) {
            final List<ViewerPlugin<?>> pluginsToRemove = new ArrayList<>();
            List<ViewerPlugin<?>> viewerPlugins = GuiUtils.getUICore().getViewerPlugins();
            synchronized (viewerPlugins) {
              for (final ViewerPlugin<?> plugin : viewerPlugins) {
                if (viewerFactory.isViewerCreatedByThisFactory(plugin)) {
                  // Do not close Series directly, it can produce deadlock.
                  pluginsToRemove.add(plugin);
                }
              }
            }
            GuiUtils.getUICore().closeSeriesViewer(pluginsToRemove);
          }
        } else if (BasicAction.UPDATE_TOOLS.equals(action)) {
          SeriesViewerUI.updateTools(selectedPlugin, selectedPlugin, true);
        }
      } else if (event.getSource() instanceof SeriesViewerUI) {
        if (ObservableEvent.BasicAction.UPDATE_TOOLBARS.equals(action)) {
          SeriesViewerUI.updateToolbars(selectedPlugin, selectedPlugin, true);
        } else if (BasicAction.UPDATE_TOOLS.equals(action)) {
          SeriesViewerUI.updateTools(selectedPlugin, selectedPlugin, true);
        } else if (ObservableEvent.BasicAction.NULL_SELECTION.equals(action)) {
          mainWindow.setSelectedPlugin(null);
        }
      } else if (event.getSource() instanceof DataExplorerView) {
        if (ObservableEvent.BasicAction.NULL_SELECTION.equals(action)) {
          if (mainWindow.getSelectedPlugin() == null) {
            mainWindow.setSelectedPlugin(null);
          }
        }
      }
    }
  }

  // ================================================================================
  // Lifecycle methods
  // ================================================================================

  public void activate() {
    LOGGER.info("Activate the main window PropertyChangeListener");
    // Register default model
    ViewerPluginBuilder.DefaultDataModel.addPropertyChangeListener(this);
    monitorDataExplorerFactories();
  }

  public void deactivate() {
    // UnRegister default model
    ViewerPluginBuilder.DefaultDataModel.removePropertyChangeListener(this);
    GuiUtils.getUICore().getPluginManager().removePluginStateListener(this);
    LOGGER.info("Deactivate the main window PropertyChangeListener");
  }

  /**
   * Shows the family of the explorer that owns the model, unless the explorer shown feeds that
   * model (the file explorer or a catalog loading into the DICOM model stays shown). Selecting a
   * viewer never comes here: its event carries the group of the viewer, not the model.
   */
  static void showExplorerOf(DataExplorerModel model) {
    DataExplorerView owner = null;
    DataExplorerView shown = null;
    SidePanel side = GuiUtils.getUICore().getSidePanel(SidePanel.Role.EXPLORER);
    String active = side == null ? null : side.activeFamily().orElse(null);
    List<DataExplorerView> explorerPlugins = GuiUtils.getUICore().getExplorerPlugins();
    synchronized (explorerPlugins) {
      for (DataExplorerView view : explorerPlugins) {
        if (owner == null && view.getDataExplorerModel() == model) {
          owner = view;
        }
        if (view instanceof DockableTool tool && tool.getToolId().equals(active)) {
          shown = view;
        }
      }
    }
    if (!(owner instanceof DockableTool tool)) {
      return;
    }
    tool.showDockable();
    if (side != null
        && owner != shown
        && tool.isFamilyHead()
        && (shown == null || !shown.isSourceFor(model))) {
      side.selectFamily(tool.getToolId());
    }
  }

  public void monitorDataExplorerFactories() {
    PluginManager pluginManager = GuiUtils.getUICore().getPluginManager();

    // Get all DataExplorerViewFactory extensions
    List<DataExplorerViewFactory> factories =
        pluginManager.getExtensions(DataExplorerViewFactory.class);

    pluginManager.addPluginStateListener(this);

    for (DataExplorerViewFactory factory : factories) {
      addDataExplorer(factory);
    }
  }

  @Override
  public void pluginStateChanged(PluginStateEvent event) {
    if (event.getPluginState() == PluginState.STARTED) {
      List<DataExplorerViewFactory> newFactories =
          GuiUtils.getUICore()
              .getPluginManager()
              .getExtensions(DataExplorerViewFactory.class, event.getPlugin().getPluginId());
      newFactories.forEach(this::addDataExplorer);
    } else if (event.getPluginState() == PluginState.STOPPED) {
      List<DataExplorerViewFactory> removedFactories =
          GuiUtils.getUICore()
              .getPluginManager()
              .getExtensions(DataExplorerViewFactory.class, event.getPlugin().getPluginId());
      removedFactories.forEach(this::removeDataExplorer);
    }
  }

  public void addDataExplorer(DataExplorerViewFactory factory) {
    String className1 =
        GuiUtils.getUICore().getSystemPreferences().getProperty(factory.getClass().getName());
    if (!StringUtil.hasText(className1) || Boolean.parseBoolean(className1)) {
      GuiExecutor.execute(() -> registerDataExplorer(factory.createDataExplorerView(null)));
    }
  }

  public void removeDataExplorer(DataExplorerViewFactory factory) {
    GuiExecutor.execute(
        () -> {
          final DataExplorerView explorer = factory.createDataExplorerView(null);
          List<DataExplorerView> explorerPlugins = GuiUtils.getUICore().getExplorerPlugins();
          if (explorerPlugins.contains(explorer)) {
            Optional.ofNullable(explorer.getDataExplorerModel())
                .ifPresent(e -> e.removePropertyChangeListener(this));
            explorerPlugins.remove(explorer);

            // Update toolbar
            List<Toolbar> tb = mainWindow.getToolbarContainer().getRegisteredToolBars();
            tb.removeIf(b -> b.getComponent().getAttachedInsertable() == explorer);
            mainWindow.getToolbarContainer().registerToolBar(tb);
            GuiUtils.getUICore()
                .getViewerPlugins()
                .forEach(
                    v ->
                        v.getSeriesViewerUI()
                            .toolBars
                            .removeIf(b -> b.getComponent().getAttachedInsertable() == explorer));

            explorer.dispose();
            LOGGER.info("Unregister data explorer Plug-in: {}", explorer.getUIName());
          }
        });
  }

  void registerDataExplorer(DataExplorerView explorer) {
    List<DataExplorerView> explorerPlugins = GuiUtils.getUICore().getExplorerPlugins();
    if (explorer != null && !explorerPlugins.contains(explorer)) {
      explorerPlugins.add(explorer);
      Optional.ofNullable(explorer.getDataExplorerModel())
          .ifPresent(e -> e.addPropertyChangeListener(this));
      if (explorer instanceof final DockableTool dockable) {
        dockable.showDockable();
      }
      LOGGER.info("Register data explorer Plug-in: {}", explorer.getUIName());
    }
  }
}
