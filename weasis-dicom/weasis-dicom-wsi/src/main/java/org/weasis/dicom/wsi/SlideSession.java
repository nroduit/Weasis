/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.main.DicomExplorer;
import org.weasis.dicom.wsi.model.Slide;
import org.weasis.dicom.wsi.source.DicomTileSource;
import org.weasis.dicom.wsi.source.LocalFrameStore;
import org.weasis.dicom.wsi.tile.TileScheduler;
import org.weasis.dicom.wsi.view.TiledImageLayer;

/**
 * The slide of a whole slide series shown by a view: its files opened in the background, the tile
 * layer that paints it, and a new reading when more instances of the series arrive. To use on the
 * event dispatch thread.
 */
public final class SlideSession implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(SlideSession.class);

  /** Quiet time after the last instance arrived before the slide is read again. */
  private static final int REFRESH_DELAY_MS = 400;

  private final Runnable repaint;
  private final Consumer<Slide> opened;
  private final Timer refreshTimer = new Timer(REFRESH_DELAY_MS, _ -> reload());

  private MediaSeries<? extends MediaElement> series;
  private List<Path> files = List.of();
  private LocalFrameStore store;
  private TiledImageLayer layer;
  private String error;

  /**
   * @param repaint asks the view to repaint, from any thread
   * @param opened receives the slide each time it is read, on the event dispatch thread
   */
  public SlideSession(Runnable repaint, Consumer<Slide> opened) {
    this.repaint = Objects.requireNonNull(repaint);
    this.opened = Objects.requireNonNull(opened);
    refreshTimer.setRepeats(false);
  }

  public MediaSeries<? extends MediaElement> series() {
    return series;
  }

  public Optional<TiledImageLayer> layer() {
    return Optional.ofNullable(layer);
  }

  public Optional<Slide> slide() {
    return layer().map(TiledImageLayer::slide);
  }

  /** The reason the slide could not be shown, or null. */
  public String error() {
    return error;
  }

  /** The tile source of the slide, for its thumbnail, label or overview images. */
  public Optional<DicomTileSource> source() {
    return slide().map(s -> new DicomTileSource(s, store));
  }

  /** Shows the series, reading its headers in the background; nothing when it is already shown. */
  public void open(MediaSeries<? extends MediaElement> newSeries) {
    if (newSeries == series) {
      return;
    }
    close();
    series = newSeries;
    if (newSeries != null) {
      read(filesOf(newSeries));
    }
  }

  /** Reads the slide again once instances stop arriving, when the series got new files. */
  public void refresh() {
    if (series != null) {
      refreshTimer.restart();
    }
  }

  /** Whether the element is an instance of the series shown. */
  public boolean isOfSeries(MediaElement element) {
    Object uid = series == null ? null : series.getTagValue(TagD.get(Tag.SeriesInstanceUID));
    return uid != null && uid.equals(element.getTagValue(TagD.get(Tag.SeriesInstanceUID)));
  }

  @Override
  public void close() {
    refreshTimer.stop();
    closeSlide();
    series = null;
    files = List.of();
    error = null;
  }

  private void reload() {
    if (series == null) {
      return;
    }
    List<Path> now = filesOf(series);
    if (!Set.copyOf(now).equals(Set.copyOf(files))) {
      read(now);
    }
  }

  private void read(List<Path> paths) {
    files = paths;
    MediaSeries<? extends MediaElement> forSeries = series;
    CompletableFuture.supplyAsync(() -> LocalFrameStore.open(paths))
        .whenComplete(
            (store, failure) -> SwingUtilities.invokeLater(() -> show(forSeries, store, failure)));
  }

  private void show(
      MediaSeries<? extends MediaElement> forSeries, LocalFrameStore newStore, Throwable failure) {
    if (forSeries != series) {
      if (newStore != null) {
        newStore.close(); // replaced while reading
      }
      return;
    }
    if (failure != null) {
      LOGGER.error("Cannot open the slide", failure);
      error = Messages.format("WsiView.error", failure.getMessage());
    } else if (newStore.slides().isEmpty()) {
      newStore.close();
      error = Messages.getString("WsiView.no_slide");
    } else {
      closeSlide();
      store = newStore;
      error = null;
      Slide slide = newStore.slides().getFirst();
      TileScheduler scheduler =
          new TileScheduler(new DicomTileSource(slide, newStore), t -> repaint.run());
      layer = new TiledImageLayer(scheduler, repaint, ForkJoinPool.commonPool());
      LOGGER.info("Slide opened: {}, {}", slide, slide.levels());
      opened.accept(slide);
    }
    repaint.run();
  }

  private void closeSlide() {
    if (layer != null) {
      layer.close();
      layer = null;
    }
    if (store != null) {
      store.close();
      store = null;
    }
  }

  private static List<Path> filesOf(MediaSeries<? extends MediaElement> series) {
    return slideSeries(series).stream()
        .flatMap(SlideSession::elementsOf)
        .map(MediaElement::getFileCache)
        .map(cache -> cache.getOriginalFile().orElseGet(cache::getFinalFile))
        .filter(Objects::nonNull)
        .distinct()
        .toList();
  }

  // The thumbnail is the image of a slide series; its levels, label and overview are special
  // elements (see DicomMediaIO)
  private static Stream<MediaElement> elementsOf(MediaSeries<?> series) {
    Stream<MediaElement> images =
        series.copyOfMedias(null, null).stream().map(MediaElement.class::cast);
    Stream<MediaElement> others =
        series.getTagValue(TagW.DicomSpecialElementList) instanceof List<?> list
            ? list.stream().filter(MediaElement.class::isInstance).map(MediaElement.class::cast)
            : Stream.empty();
    return Stream.concat(images, others);
  }

  /**
   * The series and the series split from the same Series Instance UID, for a slide loaded by a path
   * that still splits by image size (one series per level).
   */
  private static List<MediaSeries<?>> slideSeries(MediaSeries<? extends MediaElement> series) {
    DataExplorerView explorer = GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME);
    Object uid = series.getTagValue(TagD.get(Tag.SeriesInstanceUID));
    if (uid == null
        || explorer == null
        || !(explorer.getDataExplorerModel() instanceof DicomModel model)) {
      return List.of(series);
    }
    MediaSeriesGroup study = model.getParent(series, DicomModel.study);
    if (study == null) {
      return List.of(series);
    }
    List<MediaSeries<?>> split = new ArrayList<>();
    for (MediaSeriesGroup child : model.getChildren(study)) {
      if (child instanceof MediaSeries<?> other
          && uid.equals(other.getTagValue(TagD.get(Tag.SeriesInstanceUID)))) {
        split.add(other);
      }
    }
    return split.isEmpty() ? List.of(series) : split;
  }
}
