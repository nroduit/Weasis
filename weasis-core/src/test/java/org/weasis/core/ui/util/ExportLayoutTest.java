/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.gui.layout.LayoutCellManager;
import org.weasis.core.api.gui.layout.MigCell;
import org.weasis.core.api.gui.layout.MigLayoutModel;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ExportImage;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewportPane;
import org.weasis.core.ui.util.ExportLayout.ExportTiles;
import org.weasis.core.ui.util.ExportLayout.TileBox;

@DisplayName("ExportLayout")
class ExportLayoutTest {

  /** A view without image: what {@link ExportImage} copies from it comes from deep stubs. */
  @SuppressWarnings("unchecked")
  private static ViewCanvas<ImageElement> canvas() {
    ViewCanvas<ImageElement> canvas = mock(ViewCanvas.class, RETURNS_DEEP_STUBS);
    when(canvas.getJComponent()).thenReturn(new JPanel());
    when(canvas.getImage()).thenReturn(null);
    when(canvas.getSeries()).thenReturn(null);
    when(canvas.getActionValue(anyString())).thenReturn(null);
    return canvas;
  }

  @SuppressWarnings("unchecked")
  private static ImageViewerPlugin<ImageElement> container(LayoutCellManager<ImageElement> cells) {
    ImageViewerPlugin<ImageElement> container = mock(ImageViewerPlugin.class);
    when(container.getLayoutModel()).thenReturn(cells.getLayoutModel());
    when(container.getCellManager()).thenReturn(cells);
    return container;
  }

  @Test
  @DisplayName("exports the panes of a layout and leaves them in the viewer")
  void keepsPanesInTheViewer() {
    MigLayoutModel model = ImageViewerPlugin.VIEWS_2x1;
    LayoutCellManager<ImageElement> cells = new LayoutCellManager<>(model);
    JPanel viewerGrid = new JPanel();
    for (MigCell cell : cells.getLayoutModel().getCells()) {
      ViewportPane<ImageElement> pane = new ViewportPane<>(canvas());
      cells.addComponent(cell.position(), pane);
      viewerGrid.add(pane);
    }

    ExportLayout<ImageElement> layout = new ExportLayout<>(container(cells));

    assertEquals(2, viewerGrid.getComponentCount());
    for (MigCell cell : layout.getLayoutModel().getCells()) {
      assertInstanceOf(ExportImage.class, layout.findComponentForCell(cell));
    }
  }

  @Test
  @DisplayName("leaves a histogram or a dump panel in the viewer")
  void keepsOtherComponentsInTheViewer() {
    LayoutCellManager<ImageElement> cells = new LayoutCellManager<>(ImageViewerPlugin.VIEWS_1x2);
    JPanel viewerGrid = new JPanel();
    JPanel histogram = new JPanel();
    MigCell first = cells.getLayoutModel().getCells().getFirst();
    MigCell second = cells.getLayoutModel().getCells().getLast();
    ViewportPane<ImageElement> pane = new ViewportPane<>(canvas());
    cells.addComponent(first.position(), pane);
    cells.addComponent(second.position(), histogram);
    viewerGrid.add(pane);
    viewerGrid.add(histogram);

    ExportLayout<ImageElement> layout = new ExportLayout<>(container(cells));

    assertAll(
        () -> assertSame(viewerGrid, histogram.getParent()),
        () -> assertSame(viewerGrid, pane.getParent()),
        () -> assertEquals(2, viewerGrid.getComponentCount()),
        () -> assertInstanceOf(ExportImage.class, layout.findComponentForCell(first)),
        () -> assertInstanceOf(JPanel.class, layout.findComponentForCell(second)));
  }

  @Test
  @DisplayName("exports every tile of a tiled pane")
  void exportsTiles() {
    LayoutCellManager<ImageElement> cells = new LayoutCellManager<>(ImageViewerPlugin.VIEWS_1x1);
    MigCell cell = cells.getLayoutModel().getCells().getFirst();
    ViewportPane<ImageElement> pane = new ViewportPane<>(canvas());
    pane.switchToTiled(2, 2, ExportLayoutTest::canvas);
    cells.addComponent(cell.position(), pane);

    ExportLayout<ImageElement> layout = new ExportLayout<>(container(cells));

    ExportTiles<?> tiles = assertInstanceOf(ExportTiles.class, layout.findComponentForCell(cell));
    assertEquals(4, tiles.getTiles().size());
  }

  @Test
  @DisplayName("divides a cell between the tiles in reading order, with the gap between them")
  void layoutTilesInReadingOrder() {
    List<ExportImage<ImageElement>> images = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      @SuppressWarnings("unchecked")
      ExportImage<ImageElement> image = mock(ExportImage.class);
      images.add(image);
    }

    List<TileBox> boxes = new ExportTiles<>(2, images).layoutTiles(105, 205, 5);

    assertAll(
        () -> assertSame(images.get(2), boxes.get(2).image()),
        () -> assertEquals(50, boxes.get(0).width()),
        () -> assertEquals(100, boxes.get(0).height()),
        () -> assertEquals(55, boxes.get(1).x()),
        () -> assertEquals(0, boxes.get(1).y()),
        () -> assertEquals(0, boxes.get(2).x()),
        () -> assertEquals(105, boxes.get(2).y()),
        () -> assertEquals(55, boxes.get(3).x()),
        () -> assertEquals(105, boxes.get(3).y()));
  }

  @Test
  @DisplayName("has no content when no view has an image")
  void hasNoContentWithoutImage() {
    LayoutCellManager<ImageElement> cells = new LayoutCellManager<>(ImageViewerPlugin.VIEWS_1x1);
    cells.addComponent(
        cells.getLayoutModel().getCells().getFirst().position(), new ViewportPane<>(canvas()));

    ExportLayout<ImageElement> empty = new ExportLayout<>(container(cells));
    assertFalse(empty.hasContent());
  }
}
