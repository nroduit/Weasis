/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.base.ui.gui;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.base.ui.internal.MainWindowListener;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.DataExplorerViewFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.LangUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.util.Toolbar;

@org.osgi.service.component.annotations.Component(service = MainWindowListener.class, immediate = true)
public class WeasisWinListener implements MainWindowListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(WeasisWinListener.class);
    private WeasisWin mainWindow;

    @Override
    public void setMainWindow(WeasisWin mainWindow) {
        this.mainWindow = mainWindow;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (mainWindow == null) {
            return;
        }

        ViewerPlugin selectedPlugin = mainWindow.getSelectedPlugin();
        // Get only ObservableEvent
        if (evt instanceof ObservableEvent) {
            ObservableEvent event = (ObservableEvent) evt;
            ObservableEvent.BasicAction action = event.getActionCommand();
            Object source = event.getNewValue();
            if (evt.getSource() instanceof DataExplorerModel) {
                if (ObservableEvent.BasicAction.SELECT.equals(action)) {
                    if (source instanceof DataExplorerModel) {
                        DataExplorerModel model = (DataExplorerModel) source;
                        DataExplorerView view = null;
                        synchronized (UIManager.EXPLORER_PLUGINS) {
                            List<DataExplorerView> explorers = UIManager.EXPLORER_PLUGINS;
                            for (DataExplorerView dataExplorerView : explorers) {
                                if (dataExplorerView.getDataExplorerModel() == model) {
                                    view = dataExplorerView;
                                    break;
                                }
                            }
                            if (view instanceof PluginTool) {
                                PluginTool tool = (PluginTool) view;
                                tool.showDockable();
                            }
                        }
                    }
                    // Select a plugin from that as the same key as the MediaSeriesGroup
                    else if (source instanceof MediaSeriesGroup) {
                        MediaSeriesGroup group = (MediaSeriesGroup) source;
                        // If already selected do not reselect or select a second window
                        if (selectedPlugin == null || !group.equals(selectedPlugin.getGroupID())) {
                            synchronized (UIManager.VIEWER_PLUGINS) {
                                for (int i = UIManager.VIEWER_PLUGINS.size() - 1; i >= 0; i--) {
                                    ViewerPlugin p = UIManager.VIEWER_PLUGINS.get(i);
                                    if (group.equals(p.getGroupID())) {
                                        p.setSelectedAndGetFocus();
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } else if (ObservableEvent.BasicAction.REGISTER.equals(action)) {
                    if (source instanceof ViewerPlugin) {
                        mainWindow.registerPlugin((ViewerPlugin) source);
                    } else if (source instanceof ViewerPluginBuilder) {
                        ViewerPluginBuilder builder = (ViewerPluginBuilder) source;
                        DataExplorerModel model = builder.getModel();
                        List<MediaSeries<MediaElement>> series = builder.getSeries();
                        Map<String, Object> props = builder.getProperties();
                        if (series != null
                            && LangUtil
                                .getNULLtoTrue((Boolean) props.get(ViewerPluginBuilder.CMP_ENTRY_BUILD_NEW_VIEWER))
                            && model.getTreeModelNodeForNewPlugin() != null && model instanceof TreeModel) {
                            TreeModel treeModel = (TreeModel) model;
                            boolean inSelView =
                                LangUtil.getNULLtoFalse((Boolean) props.get(ViewerPluginBuilder.ADD_IN_SELECTED_VIEW))
                                    && builder.getFactory().isViewerCreatedByThisFactory(selectedPlugin);

                            if (series.size() == 1) {
                                MediaSeries<MediaElement> s = series.get(0);
                                MediaSeriesGroup group = treeModel.getParent(s, model.getTreeModelNodeForNewPlugin());
                                if (inSelView && s.getMimeType().indexOf("dicom") == -1) { //$NON-NLS-1$
                                    // Change the group attribution. DO NOT use it with DICOM.
                                    group = selectedPlugin.getGroupID();
                                }
                                mainWindow.openSeriesInViewerPlugin(builder, group);
                            } else if (series.size() > 1) {
                                HashMap<MediaSeriesGroup, List<MediaSeries<?>>> map = mainWindow
                                    .getSeriesByEntry(treeModel, series, model.getTreeModelNodeForNewPlugin());
                                for (Iterator<Map.Entry<MediaSeriesGroup, List<MediaSeries<?>>>> iterator =
                                    map.entrySet().iterator(); iterator.hasNext();) {
                                    Map.Entry<MediaSeriesGroup, List<MediaSeries<?>>> entry = iterator.next();
                                    MediaSeriesGroup group = entry.getKey();

                                    if (inSelView) {
                                        List<MediaSeries<?>> seriesList = entry.getValue();
                                        if (!seriesList.isEmpty()) {
                                            // Change the group attribution. DO NOT use it with DICOM.
                                            if (seriesList.get(0).getMimeType().indexOf("dicom") == -1) { //$NON-NLS-1$
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
                    if (source instanceof SeriesViewerFactory) {
                        SeriesViewerFactory viewerFactory = (SeriesViewerFactory) source;
                        final List<ViewerPlugin<?>> pluginsToRemove = new ArrayList<>();
                        String name = viewerFactory.getUIName();
                        synchronized (UIManager.VIEWER_PLUGINS) {
                            for (final ViewerPlugin<?> plugin : UIManager.VIEWER_PLUGINS) {
                                if (name.equals(plugin.getName())) {
                                    // Do not close Series directly, it can produce deadlock.
                                    pluginsToRemove.add(plugin);
                                }
                            }
                        }
                        UIManager.closeSeriesViewer(pluginsToRemove);
                    }
                }
            } else if (event.getSource() instanceof ViewerPlugin) {
                ViewerPlugin plugin = (ViewerPlugin) event.getSource();
                if (ObservableEvent.BasicAction.UPDTATE_TOOLBARS.equals(action)) {
                    List toolaBars = selectedPlugin == null ? null : selectedPlugin.getToolBar();
                    mainWindow.updateToolbars(toolaBars, toolaBars, true);
                } else if (ObservableEvent.BasicAction.NULL_SELECTION.equals(action)) {
                    mainWindow.setSelectedPlugin(null);
                }
            } else if (event.getSource() instanceof DataExplorerView
                && ObservableEvent.BasicAction.NULL_SELECTION.equals(action)) {
                if (mainWindow.getSelectedPlugin() == null) {
                    mainWindow.setSelectedPlugin(null);
                }
            }
        }
    }

    // ================================================================================
    // OSGI service implementation
    // ================================================================================

    @Activate
    protected void activate(ComponentContext context) {
        LOGGER.info("Activate the main window PropertyChangeListener"); //$NON-NLS-1$
        // Register default model
        ViewerPluginBuilder.DefaultDataModel.addPropertyChangeListener(this);
        mainWindow = BundlePreferences.getService(context.getBundleContext(), WeasisWin.class);
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        // UnRegister default model
        ViewerPluginBuilder.DefaultDataModel.removePropertyChangeListener(this);
        LOGGER.info("Deactivate the main window PropertyChangeListener"); //$NON-NLS-1$
    }

    @Reference(service = DataExplorerViewFactory.class, cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC, unbind = "removeDataExplorer")
    void addDataExplorer(DataExplorerViewFactory factory) {

        String className1 = BundleTools.SYSTEM_PREFERENCES.getProperty(factory.getClass().getName());
        if (!StringUtil.hasText(className1) || Boolean.valueOf(className1)) {
            GuiExecutor.instance().execute(() -> registerDataExplorer(factory.createDataExplorerView(null)));
        }
    }

    void removeDataExplorer(DataExplorerViewFactory factory) {
        GuiExecutor.instance().execute(() -> {
            final DataExplorerView explorer = factory.createDataExplorerView(null);
            if (UIManager.EXPLORER_PLUGINS.contains(explorer)) {
                Optional.ofNullable(explorer.getDataExplorerModel())
                    .ifPresent(e -> e.removePropertyChangeListener(this));
                UIManager.EXPLORER_PLUGINS.remove(explorer);

                // Update toolbar
                List<Toolbar> tb = mainWindow.getToolbarContainer().getRegisteredToolBars();
                tb.removeIf(b -> b.getComponent().getAttachedInsertable() == explorer);
                mainWindow.getToolbarContainer().registerToolBar(tb);
                UIManager.VIEWER_PLUGINS
                    .forEach(v -> v.getToolBar().removeIf(b -> b.getComponent().getAttachedInsertable() == explorer));

                explorer.dispose();
                LOGGER.info("Unregister data explorer Plug-in: {}", explorer.getUIName()); //$NON-NLS-1$
            }
        });
    }

    void registerDataExplorer(DataExplorerView explorer) {
        if (explorer != null && !UIManager.EXPLORER_PLUGINS.contains(explorer)) {
            UIManager.EXPLORER_PLUGINS.add(explorer);
            Optional.ofNullable(explorer.getDataExplorerModel()).ifPresent(e -> e.addPropertyChangeListener(this));
            if (explorer instanceof DockableTool) {
                final DockableTool dockable = (DockableTool) explorer;
                dockable.showDockable();
            }
            LOGGER.info("Register data explorer Plug-in: {}", explorer.getUIName()); //$NON-NLS-1$
        }
    }

}
