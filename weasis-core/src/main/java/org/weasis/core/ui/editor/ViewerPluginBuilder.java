/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;

public class ViewerPluginBuilder {
  public static final FileModel DefaultDataModel = new FileModel();

  private final SeriesViewerFactory factory;
  private final List<? extends MediaSeries<? extends MediaElement>> series;
  private final DataExplorerModel model;
  private final ViewerOpenOptions options;

  // ------------------------------------------------------------------
  //  Constructor
  // ------------------------------------------------------------------

  /**
   * Creates a builder with typed {@link ViewerOpenOptions}.
   *
   * @param factory the factory that will create the viewer
   * @param series the series to display
   * @param model the data explorer model
   * @param options typed open options (may be {@code null} for defaults)
   */
  public ViewerPluginBuilder(
      SeriesViewerFactory factory,
      List<? extends MediaSeries<? extends MediaElement>> series,
      DataExplorerModel model,
      ViewerOpenOptions options) {
    if (factory == null || series == null || model == null) {
      throw new IllegalArgumentException();
    }
    this.factory = factory;
    this.series = series;
    this.model = model;
    this.options = options == null ? ViewerOpenOptions.defaults() : options;
  }

  // ------------------------------------------------------------------
  //  Accessors
  // ------------------------------------------------------------------

  public SeriesViewerFactory getFactory() {
    return factory;
  }

  @SuppressWarnings("unchecked")
  public List<MediaSeries<MediaElement>> getSeries() {
    return (List<MediaSeries<MediaElement>>) series;
  }

  public DataExplorerModel getModel() {
    return model;
  }

  /** Returns the typed open options. */
  public ViewerOpenOptions getViewerOpenOptions() {
    return options;
  }

  // ------------------------------------------------------------------
  //  Open orchestration
  // ------------------------------------------------------------------

  /**
   * Terminal operation: fires a {@code REGISTER} event through the model, causing the main window
   * listener to create or reuse a viewer and display the series.
   */
  public void open() {
    model.firePropertyChange(
        new ObservableEvent(ObservableEvent.BasicAction.REGISTER, model, null, this));
  }

  // ------------------------------------------------------------------
  //  Default-viewer convenience openers
  // ------------------------------------------------------------------

  /**
   * Looks up the best factory for the series' MIME type and opens it.
   *
   * @param series the series to display
   * @param model the data explorer model (falls back to {@link #DefaultDataModel} if {@code null})
   * @param options typed open options
   */
  public static void openInDefaultViewer(
      MediaSeries<? extends MediaElement> series,
      DataExplorerModel model,
      ViewerOpenOptions options) {
    if (series == null) {
      return;
    }
    SeriesViewerFactory factory = GuiUtils.getUICore().getViewerFactory(series.getMimeType());
    if (factory != null) {
      new ViewerPluginBuilder(
              factory, List.of(series), model == null ? DefaultDataModel : model, options)
          .open();
    }
  }

  /**
   * Groups series by MIME type, looks up the best factory for each, and opens them.
   *
   * @param series the series to display
   * @param model the data explorer model
   * @param options typed open options
   */
  public static void openInDefaultViewer(
      List<? extends MediaSeries<? extends MediaElement>> series,
      DataExplorerModel model,
      ViewerOpenOptions options) {
    if (series == null || series.isEmpty()) {
      return;
    }
    ArrayList<String> mimes = new ArrayList<>();
    for (MediaSeries<?> s : series) {
      String mime = s.getMimeType();
      if (mime != null && !mimes.contains(mime)) {
        mimes.add(mime);
      }
    }
    for (String mime : mimes) {
      SeriesViewerFactory plugin = GuiUtils.getUICore().getViewerFactory(mime);
      if (plugin != null) {
        ArrayList<MediaSeries<? extends MediaElement>> seriesList = new ArrayList<>();
        for (MediaSeries<? extends MediaElement> s : series) {
          if (mime.equals(s.getMimeType())) {
            seriesList.add(s);
          }
        }
        new ViewerPluginBuilder(plugin, seriesList, model, options).open();
      }
    }
  }

  /**
   * Extracts the series from the media element's reader and opens it in the default viewer.
   *
   * @param media the media element
   * @param model the data explorer model
   * @param options typed open options
   */
  public static void openInDefaultViewer(
      MediaElement media, DataExplorerModel model, ViewerOpenOptions options) {
    if (media != null) {
      openInDefaultViewer(media.getMediaReader().getMediaSeries(), model, options);
    }
  }

  /**
   * Reads a file, builds a series with the default model, and opens it in the default viewer.
   *
   * @param file the file to open
   * @param options typed open options
   */
  public static void openInDefaultViewer(Path file, ViewerOpenOptions options) {
    MediaReader<MediaElement> reader = MediaFactory.getMedia(file);
    if (reader != null) {
      MediaSeries<MediaElement> s = MediaFactory.buildMediaSeriesWithDefaultModel(reader);
      openInDefaultViewer(s, DefaultDataModel, options);
    }
  }
}
