/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.print;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.weasis.core.api.gui.layout.MigLayoutModel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.image.ImageOpEvent;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.PixelReviewAdvisor;
import org.weasis.core.ui.editor.image.ExportImage;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.util.ExportLayout;

/**
 * The frames of the series of a view, every {@code step}-th one of a range, laid out {@code cols}
 * by {@code rows} per page in reading order, each cell showing a frame with the display state of
 * the view and fitted to its cell. Pages are built when asked for; one is kept at a time.
 */
public final class SeriesPages<E extends ImageElement> implements PageSource {

  /** The key under which the export view keeps the zoom of its source; 0 means best fit. */
  private static final String ORIGIN_ZOOM = "origin.zoom"; // NON-NLS

  private final ViewCanvas<E> view;
  private final List<E> frames;
  private final int cols;
  private final int rows;
  private final MigLayoutModel grid;
  private ExportLayout<E> current;
  private int currentIndex = -1;

  /**
   * @param first the first frame, counting from 1
   * @param last the last frame, counting from 1, or 0 for the end of the series
   * @param step one frame out of {@code step}
   */
  public SeriesPages(ViewCanvas<E> view, int cols, int rows, int first, int last, int step) {
    if (cols < 1 || rows < 1) {
      throw new IllegalArgumentException("The grid needs at least one cell");
    }
    this.view = view;
    this.cols = cols;
    this.rows = rows;
    this.frames = select(frames(view), first, last, step);
    this.grid = ImageViewerPlugin.buildMigLayoutModel(rows, cols, ViewCanvas.class.getName());
  }

  /** The frames of the series as the view sorts and filters them. */
  @SuppressWarnings("unchecked")
  public static <E extends ImageElement> List<E> frames(ViewCanvas<E> view) {
    MediaSeries<E> series = view.getSeries();
    if (series == null) {
      return List.of();
    }
    Comparator<E> sort = view.getCurrentSortComparator();
    Filter<E> filter = (Filter<E>) view.getActionValue(ActionW.FILTERED_SERIES.cmd());
    List<E> list = new ArrayList<>();
    series.getMedias(filter, sort).forEach(list::add);
    return list;
  }

  static <E> List<E> select(List<E> all, int first, int last, int step) {
    int from = Math.max(1, first);
    int to = last <= 0 ? all.size() : Math.min(last, all.size());
    int by = Math.max(1, step);
    List<E> selected = new ArrayList<>();
    for (int i = from; i <= to; i += by) {
      selected.add(all.get(i - 1));
    }
    return selected;
  }

  public int frameCount() {
    return frames.size();
  }

  public int cols() {
    return cols;
  }

  public int rows() {
    return rows;
  }

  @Override
  public int pageCount() {
    return Math.max(1, (frames.size() + cols * rows - 1) / (cols * rows));
  }

  @Override
  public synchronized ExportLayout<? extends ImageElement> page(int index) {
    if (index < 0 || index >= pageCount()) {
      throw new IndexOutOfBoundsException("Page " + index + " of " + pageCount());
    }
    if (currentIndex != index) {
      if (current != null) {
        current.dispose();
      }
      current = GuiExecutor.invokeAndRethrow(() -> build(index));
      currentIndex = index;
    }
    return current;
  }

  private ExportLayout<E> build(int index) {
    int perPage = cols * rows;
    List<Component> cells = new ArrayList<>();
    for (int k = index * perPage; k < (index + 1) * perPage; k++) {
      cells.add(k < frames.size() ? cellOf(frames.get(k)) : null);
    }
    return new ExportLayout<>(grid, cells);
  }

  private ExportImage<E> cellOf(E frame) {
    ExportImage<E> export = new ExportImage<>(view);
    export.getInfoLayer().setBorder(ExportLayout.INFO_BORDER);
    export.setActionsInView(ORIGIN_ZOOM, 0.0, false);
    export.setImage(frame);
    // The export view does not listen to its own image changes: the operations that depend on
    // the image (redaction, shutter, color profile) are told here, window/level is kept
    export
        .getDisplayOpManager()
        .handleImageOpEvent(
            new ImageOpEvent(ImageOpEvent.OpEvent.IMAGE_CHANGE, view.getSeries(), frame, null));
    return export;
  }

  @Override
  public boolean requiresPixelReview() {
    return PixelReviewAdvisor.review(view.getImage(), view.getSeries());
  }

  @Override
  public synchronized void dispose() {
    if (current != null) {
      current.dispose();
      current = null;
      currentIndex = -1;
    }
  }
}
