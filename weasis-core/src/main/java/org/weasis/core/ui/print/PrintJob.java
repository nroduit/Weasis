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

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MaskingProfile;
import org.weasis.core.ui.editor.image.ExportImage;
import org.weasis.core.ui.print.PageSpec.Orientation;
import org.weasis.core.ui.util.ExportLayout;
import org.weasis.core.ui.util.PrintOptions;

/**
 * Pages to print, built once from the workspace: a source of pages holding detached copies of the
 * views, the paper and its orientation, the look of the cells and the masking profile. The same job
 * can be rendered to rasters or sent to a destination; rendering and sending are serialized on the
 * job.
 */
public final class PrintJob {

  private final String id;
  private final String viewId;
  private final PageSource pages;
  private final Paper paper;
  private final Orientation orientation;
  private final PrintOptions options;
  private final MaskingProfile masking;
  private boolean disposed;

  public PrintJob(
      String id,
      String viewId,
      PageSource pages,
      Paper paper,
      Orientation orientation,
      PrintOptions options,
      MaskingProfile masking) {
    this.id = Objects.requireNonNull(id);
    this.viewId = viewId;
    this.pages = Objects.requireNonNull(pages);
    this.paper = Objects.requireNonNull(paper);
    this.orientation = Objects.requireNonNull(orientation);
    this.options = Objects.requireNonNull(options);
    this.masking = masking;
  }

  /** Landscape when the grid, or the single image, is wider than tall. */
  public static Orientation defaultOrientation(ExportLayout<? extends ImageElement> layout) {
    var grid = layout.getLayoutModel().getGridSize();
    if (grid.width != grid.height) {
      return grid.width > grid.height ? Orientation.LANDSCAPE : Orientation.PORTRAIT;
    }
    for (var cell : layout.getLayoutModel().getCells()) {
      if (layout.findComponentForCell(cell) instanceof ExportImage<?> view) {
        CellFit.SourceGeometry source = view.getSourceGeometry();
        return source.shownWidth() > source.shownHeight()
            ? Orientation.LANDSCAPE
            : Orientation.PORTRAIT;
      }
    }
    return Orientation.PORTRAIT;
  }

  public String id() {
    return id;
  }

  /** The viewer the pages were taken from, when they came from one. */
  public Optional<String> viewId() {
    return Optional.ofNullable(viewId);
  }

  public PageSource pages() {
    return pages;
  }

  public int pageCount() {
    return pages.pageCount();
  }

  /** The first page, the one the figures describe. */
  public ExportLayout<? extends ImageElement> layout() {
    return pages.page(0);
  }

  public Paper paper() {
    return paper;
  }

  public Orientation orientation() {
    return orientation;
  }

  public PrintOptions options() {
    return options;
  }

  public Optional<MaskingProfile> masking() {
    return Optional.ofNullable(masking);
  }

  public int cellCount() {
    return layout().getLayoutModel().getCellCount();
  }

  /** Whether an image of the pages may carry identity burned into its pixels that nothing hides. */
  public boolean requiresPixelReview() {
    return pages.requiresPixelReview();
  }

  /** The page as {@code destination} prints it, or with the default margins when none is named. */
  public PageSpec spec(PrintDestination destination) {
    Color background = options.getBackground();
    PageSpec spec =
        destination == null
            ? PageSpec.of(paper, orientation, background)
            : destination.pageSpec(paper, orientation, background, options.getBorder());
    return spec.withHeader(options.isHeader());
  }

  /** Renders page {@code pageIndex} at this resolution under the masking profile of the job. */
  public synchronized BufferedImage render(PageSpec spec, int dpi, boolean gray, int pageIndex) {
    checkOpen();
    double pixelsPerMm = PrintResolution.pixelsPerMm(dpi);
    return masked(
        () ->
            PageRenderer.renderPage(
                pages.page(pageIndex), spec, pixelsPerMm, options, gray, pageIndex, pageCount()));
  }

  /** Prints the pages on {@code destination} under the masking profile of the job. */
  public synchronized void send(PrintDestination destination, int copies) throws Exception {
    checkOpen();
    PageSpec spec = spec(destination);
    try {
      masked(
          () -> {
            try {
              destination.print(pages, spec, options, copies);
              return null;
            } catch (Exception e) {
              throw new IllegalStateException(e);
            }
          });
    } catch (IllegalStateException e) {
      throw e.getCause() instanceof Exception cause ? cause : e;
    }
  }

  private <T> T masked(Supplier<T> task) {
    return masking == null ? task.get() : IdentityMask.forProfile(masking).callMasked(task::get);
  }

  private void checkOpen() {
    if (disposed) {
      throw new IllegalStateException("Print job " + id + " is disposed");
    }
  }

  public synchronized void dispose() {
    if (!disposed) {
      disposed = true;
      pages.dispose();
    }
  }
}
