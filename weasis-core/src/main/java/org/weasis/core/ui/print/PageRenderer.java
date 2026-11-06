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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.layout.MigCell;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.ui.editor.image.ExportImage;
import org.weasis.core.ui.util.ExportLayout;
import org.weasis.core.ui.util.PrintOptions;

/**
 * Renders the cells of an {@link ExportLayout} on a page at any resolution, given in pixels per
 * millimeter. The preview, the system printer and the DICOM printer differ only in that figure and
 * in the color model of the raster they receive.
 *
 * <p>The page is in millimeters; a cell is drawn in device pixels through the render scale of its
 * {@link ExportImage}: the overlay and the graphics are scaled by Java2D from logical pixels, the
 * image is rendered by the display pipeline at the device resolution and copied pixel for pixel.
 */
public final class PageRenderer {
  private static final Logger LOGGER = LoggerFactory.getLogger(PageRenderer.class);

  /** Logical pixels per millimeter: the views draw as on a 96 dpi screen. */
  public static final double LOGICAL_PX_PER_MM = 96.0 / Paper.MM_PER_INCH;

  /** Width of the cell border on paper. */
  public static final double BORDER_MM = 0.25;

  private PageRenderer() {}

  /** The device pixels of a cell at this resolution, from the top-left corner of the page. */
  public static Rectangle2D devicePixels(CellBox box, double pixelsPerMm) {
    Rectangle2D b = box.boundsMm();
    return new Rectangle2D.Double(
        b.getX() * pixelsPerMm,
        b.getY() * pixelsPerMm,
        b.getWidth() * pixelsPerMm,
        b.getHeight() * pixelsPerMm);
  }

  /** The text size of a cell: the one chosen, else the default for its width. */
  public static TextSize textSize(PrintOptions options, CellBox box) {
    TextSize size = options.getTextSize();
    return size == null ? TextSize.defaultFor(box.widthMm()) : size;
  }

  /** Device pixels per logical pixel of a cell rendered at this resolution and text size. */
  public static double renderScale(double pixelsPerMm, TextSize textSize) {
    return pixelsPerMm / LOGICAL_PX_PER_MM * textSize.scaleFor(baseFontSize());
  }

  private static String describe(org.weasis.opencv.data.PlanarImage image) {
    return image == null ? "none" : image.width() + "x" + image.height(); // NON-NLS
  }

  private static double baseFontSize() {
    return FontItem.DEFAULT_SEMIBOLD.getFont().getSize2D();
  }

  /**
   * Draws one cell. {@code g2d} is in device pixels with its origin at the top-left corner of the
   * page. A cell whose raster would exceed the pixel budget is rendered smaller and scaled up.
   */
  public static void drawCell(
      Graphics2D g2d,
      ExportImage<? extends ImageElement> view,
      CellBox box,
      double pixelsPerMm,
      PrintOptions options) {
    Rectangle2D device = devicePixels(box, pixelsPerMm);
    double budget =
        PrintResolution.budgetScale(
            device.getWidth(), device.getHeight(), PrintResolution.MAX_CELL_PIXELS);
    double scale = renderScale(pixelsPerMm, textSize(options, box)) * budget;
    double cellWidth = device.getWidth() * budget / scale;
    double cellHeight = device.getHeight() * budget / scale;
    CellFit fit = CellFit.fit(view.getSourceGeometry(), cellWidth, cellHeight, options.isCenter());

    view.showAnnotations(options.isShowingAnnotations());
    view.configureForPrint(fit, scale);
    LOGGER.debug(
        "Cell {}: {}x{} device px, scale {}, image {}",
        box.cell().position(),
        Math.round(device.getWidth()),
        Math.round(device.getHeight()),
        scale,
        describe(view.getImageLayer().getDisplayImage()));

    Graphics2D g = (Graphics2D) g2d.create();
    try {
      g.translate(device.getX(), device.getY());
      if (budget < 1.0) {
        g.scale(1.0 / budget, 1.0 / budget);
      }
      g.translate(fit.padX() * scale, fit.padY() * scale);
      double w = fit.viewportWidth() * scale;
      double h = fit.viewportHeight() * scale;
      g.clip(new Rectangle2D.Double(0, 0, w, h));
      boolean wasBuffered = ExportImage.disableDoubleBuffering(view);
      view.draw(g);
      ExportImage.restoreDoubleBuffering(view, wasBuffered);
      g.setClip(null);
      Color border = options.getBorder();
      if (border != null) {
        g.setColor(border);
        g.setStroke(new BasicStroke((float) Math.max(1.0, BORDER_MM * pixelsPerMm * budget)));
        g.draw(new Rectangle2D.Double(0, 0, w, h));
      }
    } finally {
      g.dispose();
    }
  }

  /**
   * Draws every cell of the layout on the page. {@code g2d} is in device pixels with its origin at
   * the top-left corner of the page.
   */
  public static void drawPage(
      Graphics2D g2d,
      ExportLayout<? extends ImageElement> layout,
      PageSpec spec,
      double pixelsPerMm,
      PrintOptions options) {
    drawPage(g2d, layout, spec, pixelsPerMm, options, 0, 1);
  }

  /** Draws the cells of page {@code pageIndex} of {@code pageCount}, and its header when asked. */
  public static void drawPage(
      Graphics2D g2d,
      ExportLayout<? extends ImageElement> layout,
      PageSpec spec,
      double pixelsPerMm,
      PrintOptions options,
      int pageIndex,
      int pageCount) {
    List<CellBox> boxes = PageLayout.layout(layout.getLayoutModel(), spec);
    for (MigCell cell : layout.getLayoutModel().getCells()) {
      Component component = layout.findComponentForCell(cell);
      CellBox box = PageLayout.find(boxes, cell);
      if (box == null) {
        continue;
      }
      if (component instanceof ExportImage<? extends ImageElement> view) {
        drawCell(g2d, view, box, pixelsPerMm, options);
      } else if (component instanceof ExportLayout.ExportTiles<? extends ImageElement> tiles) {
        List<CellBox> tileBoxes =
            PageLayout.tile(box, tiles.getCols(), tiles.getRows(), spec.gutterMm());
        List<? extends ExportImage<? extends ImageElement>> views = tiles.getTiles();
        for (int i = 0; i < views.size() && i < tileBoxes.size(); i++) {
          drawCell(g2d, views.get(i), tileBoxes.get(i), pixelsPerMm, options);
        }
      }
    }
    if (spec.hasHeader()) {
      PageHeader.draw(g2d, layout, spec, pixelsPerMm, pageIndex, pageCount);
    }
  }

  /** The size of the page in pixels at this resolution. */
  public static Dimension pageSize(PageSpec spec, double pixelsPerMm) {
    return new Dimension(
        Math.max(1, (int) Math.round(spec.widthMm() * pixelsPerMm)),
        Math.max(1, (int) Math.round(spec.heightMm() * pixelsPerMm)));
  }

  /**
   * Renders the page as a raster, in color or 8-bit gray, with its background. This is what the
   * preview, a file and the DICOM printer receive.
   */
  public static BufferedImage renderPage(
      ExportLayout<? extends ImageElement> layout,
      PageSpec spec,
      double pixelsPerMm,
      PrintOptions options,
      boolean gray) {
    return renderPage(layout, spec, pixelsPerMm, options, gray, 0, 1);
  }

  /** Renders page {@code pageIndex} of {@code pageCount} as a raster, in color or 8-bit gray. */
  public static BufferedImage renderPage(
      ExportLayout<? extends ImageElement> layout,
      PageSpec spec,
      double pixelsPerMm,
      PrintOptions options,
      boolean gray,
      int pageIndex,
      int pageCount) {
    Dimension size = pageSize(spec, pixelsPerMm);
    BufferedImage page =
        new BufferedImage(
            size.width,
            size.height,
            gray ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_3BYTE_BGR);
    return renderPage(layout, spec, pixelsPerMm, options, page, pageIndex, pageCount);
  }

  /**
   * Renders the page into {@code page}, a raster of {@link #pageSize}: the background, then the
   * cells.
   */
  public static BufferedImage renderPage(
      ExportLayout<? extends ImageElement> layout,
      PageSpec spec,
      double pixelsPerMm,
      PrintOptions options,
      BufferedImage page) {
    return renderPage(layout, spec, pixelsPerMm, options, page, 0, 1);
  }

  /** Renders page {@code pageIndex} of {@code pageCount} into {@code page}. */
  public static BufferedImage renderPage(
      ExportLayout<? extends ImageElement> layout,
      PageSpec spec,
      double pixelsPerMm,
      PrintOptions options,
      BufferedImage page,
      int pageIndex,
      int pageCount) {
    Graphics2D g2d = page.createGraphics();
    try {
      g2d.setColor(spec.background() == null ? Color.WHITE : spec.background());
      g2d.fillRect(0, 0, page.getWidth(), page.getHeight());
      drawPage(g2d, layout, spec, pixelsPerMm, options, pageIndex, pageCount);
    } finally {
      g2d.dispose();
    }
    return page;
  }
}
