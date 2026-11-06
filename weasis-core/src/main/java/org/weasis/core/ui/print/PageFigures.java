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
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Optional;
import org.weasis.core.api.gui.layout.MigCell;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ExportImage;
import org.weasis.core.ui.util.ExportLayout;
import org.weasis.core.ui.util.PrintOptions;

/**
 * What the user needs to know about the result without a resolution setting: the size of a cell on
 * paper, how many pixels of the image land on an inch of it, and the text size used.
 *
 * @param magnified whether the image is drawn larger than its pixels, so interpolated up
 */
public record PageFigures(
    double cellWidthMm,
    double cellHeightMm,
    double imagePixelsPerInch,
    boolean magnified,
    TextSize textSize) {

  /** The figures of the first image cell of the layout at this resolution, if it has one. */
  public static Optional<PageFigures> of(
      ExportLayout<? extends ImageElement> layout, PageSpec spec, PrintOptions options, int dpi) {
    List<CellBox> boxes = PageLayout.layout(layout.getLayoutModel(), spec);
    for (MigCell cell : layout.getLayoutModel().getCells()) {
      CellBox box = PageLayout.find(boxes, cell);
      if (box == null) {
        continue;
      }
      Component component = layout.findComponentForCell(cell);
      if (component instanceof ExportImage<? extends ImageElement> view) {
        return Optional.of(of(view.getSourceGeometry(), box, options, dpi));
      }
      if (component instanceof ExportLayout.ExportTiles<? extends ImageElement> tiles
          && !tiles.getTiles().isEmpty()) {
        CellBox tile =
            PageLayout.tile(box, tiles.getCols(), tiles.getRows(), spec.gutterMm()).getFirst();
        return Optional.of(of(tiles.getTiles().getFirst().getSourceGeometry(), tile, options, dpi));
      }
    }
    return Optional.empty();
  }

  /** The figures of one cell holding this image at this resolution. */
  public static PageFigures of(
      CellFit.SourceGeometry source, CellBox box, PrintOptions options, int dpi) {
    double pixelsPerMm = PrintResolution.pixelsPerMm(dpi);
    TextSize textSize = PageRenderer.textSize(options, box);
    double scale = PageRenderer.renderScale(pixelsPerMm, textSize);
    Rectangle2D device = PageRenderer.devicePixels(box, pixelsPerMm);
    CellFit fit =
        CellFit.fit(
            source, device.getWidth() / scale, device.getHeight() / scale, options.isCenter());
    double magnification = fit.zoom() * scale;
    return new PageFigures(
        box.widthMm(), box.heightMm(), dpi / magnification, magnification > 1.0, textSize);
  }
}
