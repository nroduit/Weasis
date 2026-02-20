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
   * container's current layout.
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
      if (sourceEntry.isViewCanvas()) {
        ViewCanvas<E> viewCanvas = sourceEntry.getViewCanvas().orElse(null);
        if (viewCanvas != null) {
          ExportImage<E> export = new ExportImage<>(viewCanvas);
          export.getInfoLayer().setBorder(3);
          cellManager.addComponent(cell.position(), export);
          grid.add(export, constraints);
        }
      } else {
        Component comp = sourceEntry.getComponent();
        cellManager.addComponent(cell.position(), comp);
        grid.add(comp, constraints);
      }
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
    ExportImage<E> export = new ExportImage<>(viewCanvas);
    export.getInfoLayer().setBorder(3);
    cellManager.addComponent(cell.position(), export);
    grid.add(export, cell.getFullConstraints());
    grid.revalidate();
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

  /** Disposes all ExportImage views and clears the cell manager. */
  public void dispose() {
    for (var c : cellManager.getAllComponents()) {
      if (c instanceof ExportImage<?> exportImage) {
        exportImage.disposeView();
      }
    }
    cellManager.clear();
  }
}
