/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import static org.weasis.core.ui.editor.image.ImageViewerPlugin.VIEWS_1x1;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JPanel;
import javax.swing.plaf.PanelUI;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.api.gui.layout.LayoutCellManager;
import org.weasis.core.api.gui.layout.MigCell;
import org.weasis.core.api.gui.layout.MigLayoutModel;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ExportImage;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewportPane;

/**
 * Provides a printable/exportable representation of a {@link MigLayoutModel} with its content.
 *
 * <p>Use {@link #ExportLayout(ImageViewerPlugin)} to export all views in the current layout, or
 * {@link #ExportLayout(ViewCanvas)} to export a single view.
 *
 * @param <E> the type of ImageElement
 */
public class ExportLayout<E extends ImageElement> extends JPanel {

  protected final JPanel grid = new JPanel();
  protected final LayoutCellManager<E> cellManager;

  /**
   * Creates an export layout from a full viewer container. All {@link ViewCanvas} instances in the
   * container are wrapped in {@link ExportImage} components and placed into a copy of the
   * container's current layout. The components of the container stay in the container.
   *
   * @param container the source viewer plugin
   */
  public ExportLayout(ImageViewerPlugin<E> container) {
    super(new BorderLayout());
    MigLayoutModel layoutModel = container.getLayoutModel().copy();
    this.cellManager = new LayoutCellManager<>(layoutModel);
    initGrid();

    grid.setLayout(
        new MigLayout(
            layoutModel.getLayoutConstraints(),
            layoutModel.getColumnConstraints(),
            layoutModel.getRowConstraints()));

    List<LayoutCellManager.CellEntry<E>> entries = container.getCellManager().getAllEntries();

    for (LayoutCellManager.CellEntry<E> sourceEntry : entries) {
      MigCell cell = sourceEntry.getCell();
      Optional<MigCell> modelCell = findCellInModel(layoutModel, cell.position());
      if (modelCell.isEmpty()) {
        continue;
      }
      String constraints = modelCell.get().getFullConstraints();
      Component export = exportOf(sourceEntry);
      cellManager.addComponent(cell.position(), export);
      grid.add(export, constraints);
    }

    layoutModel.applyConstraintsToLayout(grid);
    grid.revalidate();
  }

  /**
   * Creates a 1×1 export layout for a single view canvas.
   *
   * @param viewCanvas the canvas to export
   */
  public ExportLayout(ViewCanvas<E> viewCanvas) {
    super(new BorderLayout());
    MigLayoutModel layoutModel = VIEWS_1x1.copy();
    this.cellManager = new LayoutCellManager<>(layoutModel);
    initGrid();

    grid.setLayout(
        new MigLayout(
            layoutModel.getLayoutConstraints(),
            layoutModel.getColumnConstraints(),
            layoutModel.getRowConstraints()));

    MigCell cell = layoutModel.getCells().getFirst();
    ExportImage<E> export = exportImage(viewCanvas);
    cellManager.addComponent(cell.position(), export);
    grid.add(export, cell.getFullConstraints());
    grid.revalidate();
  }

  /**
   * The export of a cell: an {@link ExportImage} for a view canvas or a stack pane, an {@link
   * ExportTiles} for a tiled pane and an empty panel otherwise, as a live component such as a
   * histogram must remain in its viewer.
   */
  private Component exportOf(LayoutCellManager.CellEntry<E> sourceEntry) {
    ViewCanvas<E> viewCanvas = sourceEntry.getViewCanvas().orElse(null);
    if (viewCanvas != null) {
      return exportImage(viewCanvas);
    }
    if (sourceEntry.getComponent() instanceof ViewportPane<?> pane) {
      @SuppressWarnings("unchecked")
      ViewportPane<E> typedPane = (ViewportPane<E>) pane;
      if (!typedPane.isTiled()) {
        return exportImage(typedPane.getSelectedCanvas());
      }
      List<ExportImage<E>> tiles = new ArrayList<>();
      for (ViewCanvas<E> canvas : typedPane.getAllViewCanvases()) {
        tiles.add(exportImage(canvas));
      }
      return new ExportTiles<>(typedPane.getTiledCols(), tiles);
    }
    return new JPanel();
  }

  private static <E extends ImageElement> ExportImage<E> exportImage(ViewCanvas<E> viewCanvas) {
    ExportImage<E> export = new ExportImage<>(viewCanvas);
    export.getInfoLayer().setBorder(3);
    return export;
  }

  /** The location of a tile relative to its cell, and its size, in the unit of the cell size. */
  public record TileBox(
      ExportImage<? extends ImageElement> image, double x, double y, double width, double height) {}

  /** The export of a tiled pane: its canvases in reading order, drawn inside the pane's cell. */
  public static final class ExportTiles<E extends ImageElement> extends JPanel {
    private final int cols;
    private final List<ExportImage<E>> tiles;

    ExportTiles(int cols, List<ExportImage<E>> tiles) {
      this.cols = Math.max(1, cols);
      this.tiles = List.copyOf(tiles);
    }

    public List<ExportImage<E>> getTiles() {
      return tiles;
    }

    /**
     * Divides a cell into one box per tile, separated by {@code gap}.
     *
     * @param cellWidth the width of the cell
     * @param cellHeight the height of the cell
     * @param gap the space between two tiles
     * @return the boxes in reading order
     */
    public List<TileBox> layoutTiles(double cellWidth, double cellHeight, double gap) {
      int rows = (tiles.size() + cols - 1) / cols;
      double width = Math.max(1.0, (cellWidth - (cols - 1) * gap) / cols);
      double height = Math.max(1.0, (cellHeight - (rows - 1) * gap) / rows);
      List<TileBox> boxes = new ArrayList<>(tiles.size());
      for (int i = 0; i < tiles.size(); i++) {
        double x = (i % cols) * (width + gap);
        double y = (i / cols) * (height + gap);
        boxes.add(new TileBox(tiles.get(i), x, y, width, height));
      }
      return boxes;
    }
  }

  private void initGrid() {
    // For having a black background with any Look and Feel
    grid.setUI(new PanelUI() {});
    setGridBackground(Color.BLACK);
    add(grid, BorderLayout.CENTER);
  }

  public void setGridBackground(Color bg) {
    grid.setBackground(bg);
  }

  /** Get the layout model of this export panel. */
  public MigLayoutModel getLayoutModel() {
    return cellManager.getLayoutModel();
  }

  /**
   * Finds the {@link MigCell} matching a given position in the layout model.
   *
   * @param layoutModel the layout model to search
   * @param position the cell position
   * @return an Optional containing the matching cell, or empty if not found
   */
  private Optional<MigCell> findCellInModel(MigLayoutModel layoutModel, int position) {
    return layoutModel.getCells().stream().filter(c -> c.position() == position).findFirst();
  }

  /**
   * Returns the component registered at the given cell position, or null if none.
   *
   * @param cell the MigCell whose position is used
   * @return the component, or null
   */
  public Component findComponentForCell(MigCell cell) {
    return cellManager.getComponent(cell.position());
  }

  /** Returns whether a view of this layout has an image to draw. */
  public boolean hasContent() {
    for (Component c : cellManager.getAllComponents()) {
      boolean content =
          switch (c) {
            case ExportImage<?> image -> image.hasValidContent();
            case ExportTiles<?> tiles ->
                tiles.getTiles().stream().anyMatch(ExportImage::hasValidContent);
            default -> false;
          };
      if (content) {
        return true;
      }
    }
    return false;
  }

  /** Disposes all ExportImage views and clears the cell manager. */
  public void dispose() {
    for (var c : cellManager.getAllComponents()) {
      if (c instanceof ExportImage<?> exportImage) {
        exportImage.disposeView();
      } else if (c instanceof ExportTiles<?> tiles) {
        tiles.getTiles().forEach(ExportImage::disposeView);
      }
    }
    cellManager.clear();
  }
}
