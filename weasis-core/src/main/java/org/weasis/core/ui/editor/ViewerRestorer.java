/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

import jakarta.json.JsonArray;
import jakarta.json.JsonString;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.ui.docking.ws.WorkspaceView;
import org.weasis.core.ui.docking.ws.snapshot.ViewRestorer;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.ViewDescriptor;
import org.weasis.core.ui.editor.image.ViewerPlugin;

/**
 * Resolves snapshot descriptors to viewers: a view that is still open is reused by id, otherwise
 * the viewer is created again by the factory of its type and given back its content and
 * presentation from the descriptor state. A descriptor whose type has no factory, or whose series
 * are no longer loaded, is skipped.
 */
public final class ViewerRestorer implements ViewRestorer {

  private static final Logger LOGGER = LoggerFactory.getLogger(ViewerRestorer.class);

  private final List<WorkspaceView> pool;
  private final Supplier<List<SeriesViewerFactory>> factories;
  private final Supplier<List<DataExplorerModel>> models;
  private final Consumer<ViewerPlugin<?>> register;

  /** Restores against the viewers, factories and explorer models of the application. */
  public ViewerRestorer(List<WorkspaceView> pool) {
    this(
        pool,
        () -> GuiUtils.getUICore().getSeriesViewerFactories(),
        ViewerRestorer::applicationModels,
        ViewerRestorer::registerInApplication);
  }

  ViewerRestorer(
      List<WorkspaceView> pool,
      Supplier<List<SeriesViewerFactory>> factories,
      Supplier<List<DataExplorerModel>> models,
      Consumer<ViewerPlugin<?>> register) {
    this.pool = pool;
    this.factories = factories;
    this.models = models;
    this.register = register;
  }

  @Override
  public Optional<WorkspaceView> recreate(ViewDescriptor descriptor) {
    Optional<WorkspaceView> live =
        pool.stream().filter(v -> v.viewId().equals(descriptor.viewId())).findFirst();
    if (live.isPresent()) {
      return live;
    }
    SeriesViewerFactory factory = factoryOf(descriptor.typeId()).orElse(null);
    if (factory == null) {
      return Optional.empty();
    }
    List<DataExplorerModel> explorers = models.get();
    SeriesResolver resolver =
        id ->
            explorers.stream()
                .map(model -> model.findSeries(id))
                .flatMap(Optional::stream)
                .filter(factory::canReadSeries)
                .findFirst();
    Loaded loaded = null;
    for (String id : seriesIds(descriptor)) {
      loaded = loadedIn(explorers, id, factory).orElse(null);
      if (loaded != null) {
        break;
      }
    }
    if (loaded == null) {
      return Optional.empty();
    }
    DataExplorerModel model = loaded.model();
    SeriesViewer<?> viewer =
        factory.createSeriesViewer(
            ViewerOpenOptions.builder().uid(descriptor.viewId()).build(), model);
    if (!(viewer instanceof ViewerPlugin<?> plugin)) {
      return Optional.empty();
    }
    // the group of the viewer (the patient for DICOM) names its tab and links it to its siblings
    if (model instanceof TreeModel tree && model.getTreeModelNodeForNewPlugin() != null) {
      MediaSeriesGroup group =
          tree.getParent(loaded.series(), model.getTreeModelNodeForNewPlugin());
      if (group != null) {
        plugin.setGroupID(group);
        plugin.setPluginName(group::toString);
      }
    }
    register.accept(plugin);
    boolean restored;
    try {
      restored = plugin.restoreState(descriptor.state(), resolver);
    } catch (RuntimeException e) {
      LOGGER.error("Cannot restore view {}", descriptor.viewId(), e);
      restored = false;
    }
    if (!restored) {
      plugin.close();
      return Optional.empty();
    }
    return Optional.of(plugin);
  }

  private Optional<SeriesViewerFactory> factoryOf(String typeId) {
    return factories.get().stream().filter(f -> typeId.equals(f.getViewerTypeId())).findFirst();
  }

  /** A series the factory can show, and the explorer model holding it. */
  private record Loaded(DataExplorerModel model, MediaSeries<? extends MediaElement> series) {}

  private static Optional<Loaded> loadedIn(
      List<DataExplorerModel> explorers, String seriesId, SeriesViewerFactory factory) {
    for (DataExplorerModel model : explorers) {
      Optional<MediaSeries<? extends MediaElement>> series =
          model.findSeries(seriesId).filter(factory::canReadSeries);
      if (series.isPresent()) {
        return Optional.of(new Loaded(model, series.get()));
      }
    }
    return Optional.empty();
  }

  private static List<String> seriesIds(ViewDescriptor descriptor) {
    List<String> ids = new ArrayList<>();
    if (descriptor.state() != null && descriptor.state().containsKey("series")) { // NON-NLS
      JsonArray array = descriptor.state().getJsonArray("series"); // NON-NLS
      for (JsonString id : array.getValuesAs(JsonString.class)) {
        ids.add(id.getString());
      }
    }
    return ids;
  }

  private static List<DataExplorerModel> applicationModels() {
    List<DataExplorerModel> models = new ArrayList<>();
    for (DataExplorerView explorer : GuiUtils.getUICore().getExplorerPlugins()) {
      DataExplorerModel model = explorer.getDataExplorerModel();
      if (model != null && !models.contains(model)) {
        models.add(model);
      }
    }
    if (!models.contains(ViewerPluginBuilder.DefaultDataModel)) {
      models.add(ViewerPluginBuilder.DefaultDataModel);
    }
    return models;
  }

  private static void registerInApplication(ViewerPlugin<?> plugin) {
    List<ViewerPlugin<?>> plugins = GuiUtils.getUICore().getViewerPlugins();
    synchronized (plugins) {
      if (!plugins.contains(plugin)) {
        plugins.add(plugin);
      }
    }
  }
}
