/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import javax.swing.JPopupMenu;
import javax.swing.ToolTipManager;
import org.weasis.core.api.gui.model.ViewModel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.layer.LayerItem;

/**
 * A non-interactive view used for printing and exporting images.
 *
 * <p>Captures the full visual state of a source {@link ViewCanvas} (image operations, zoom, center,
 * rotation, annotations) and renders it off-screen for printing or image export.
 *
 * <p>When rendering to a printer context, the image pipeline automatically uses the printer's
 * native device scale to produce the sharpest possible output.
 *
 * @param <E> the type of ImageElement
 */
public class ExportImage<E extends ImageElement> extends DefaultView2d<E> {

  /** The source view whose state is captured for export. */
  private final ViewCanvas<E> sourceView;

  /**
   * Holds the current {@link Graphics2D} context during {@link #draw(Graphics2D)} so that {@link
   * #getGraphics()} returns it correctly for sub-components.
   */
  private Graphics2D currentG2d;

  private double imagePrintingResolution = 1.0;

  /**
   * Creates an export view that captures the visual state of the given {@code sourceView}.
   *
   * @param sourceView the canvas whose image, operations, zoom, and annotations are copied
   */
  public ExportImage(ViewCanvas<E> sourceView) {
    super(sourceView.getEventManager(), null);
    this.sourceView = sourceView;

    // Remove the OpEventListener so that resizing/repaints do not reset operation parameters
    imageLayer.removeEventListener(imageLayer.getDisplayOpManager());

    setFont(FontItem.MINI.getFont());

    // Copy info layer settings (without global preferences so export is independent)
    this.infoLayer = sourceView.getInfoLayer().getLayerCopy(this, false);
    infoLayer.setVisible(sourceView.getInfoLayer().getVisible());
    infoLayer.setShowBottomScale(false);
    // Suppress interactive-only items in the export overlay
    infoLayer.setDisplayPreferencesValue(LayerItem.PIXEL, false);
    infoLayer.setDisplayPreferencesValue(LayerItem.PRELOADING_BAR, false);

    // Copy image pipeline operations from the source view
    SimpleOpManager operations = imageLayer.getDisplayOpManager();
    for (ImageOpNode op : sourceView.getImageLayer().getDisplayOpManager().getOperations()) {
      operations.addImageOperationAction(op.copy());
    }

    // Copy all action states (zoom, window/level, rotation, …)
    sourceView.copyActionWState(actionsInView);

    setPreferredSize(new Dimension(1024, 1024));
    ViewModel model = sourceView.getViewModel();
    Rectangle2D canvas =
        new Rectangle2D.Double(
            0, 0, sourceView.getJComponent().getWidth(), sourceView.getJComponent().getHeight());
    actionsInView.put("origin.image.bound", canvas);
    actionsInView.put("origin.zoom", sourceView.getActionValue(ActionW.ZOOM.cmd()));
    actionsInView.put(
        "origin.center.offset",
        new Point2D.Double(model.getModelOffsetX(), model.getModelOffsetY()));

    // Do not use setSeries() because the view will be reset
    this.series = sourceView.getSeries();
    setImage(sourceView.getImage());
  }

  public double getImagePrintingResolution() {
    return imagePrintingResolution;
  }

  public void setImagePrintingResolution(double imagePrintingResolution) {
    this.imagePrintingResolution = imagePrintingResolution;
  }

  @Override
  public void disposeView() {
    disableMouseAndKeyListener();
    removeFocusListener(this);
    ToolTipManager.sharedInstance().unregisterComponent(this);
    imageLayer.removeLayerChangeListener(this);
    // Unregister listener in GraphicsPane
    removeGraphicManager(graphicManager, layerModelHandler);
    setViewModel(null);
  }

  @Override
  public Graphics getGraphics() {
    if (currentG2d != null) {
      return currentG2d;
    }
    return super.getGraphics();
  }

  @Override
  public void paintComponent(Graphics g) {
    if (g instanceof Graphics2D g2d) {
      draw(g2d);
    }
  }

  @Override
  public void draw(Graphics2D g2d) {
    currentG2d = g2d;
    Stroke oldStroke = g2d.getStroke();
    Paint oldColor = g2d.getPaint();

    // Set font size according to the view size
    g2d.setFont(getLayerFont());
    Object[] oldRenderingHints = GuiUtils.setRenderingHints(g2d, true, true, true);

    // Update graphic label positions to match this view's bounds
    graphicManager.updateLabels(Boolean.TRUE, this);

    Point2D offset = getClipViewCoordinatesOffset();
    g2d.translate(offset.getX(), offset.getY());

    // TODO fix rotation issue
    Integer rotationAngle = (Integer) actionsInView.get(ActionW.ROTATION.cmd());
    if ((rotationAngle == null || rotationAngle == 0)
        && g2d.getClass().getName().contains("print")) {
      imageLayer.drawImageForPrinter(g2d, imagePrintingResolution, this);
    } else {
      imageLayer.drawImage(g2d);
    }

    drawLayers(g2d, affineTransform, inverseTransform);
    g2d.translate(-offset.getX(), -offset.getY());

    if (infoLayer != null) {
      infoLayer.paint(g2d);
    }
    GuiUtils.resetRenderingHints(g2d, oldRenderingHints);
    g2d.setPaint(oldColor);
    g2d.setStroke(oldStroke);

    // Restore graphic label positions relative to the original source view
    graphicManager.updateLabels(Boolean.TRUE, sourceView);

    currentG2d = null;
  }

  @Override
  public void handleLayerChanged(ImageLayer<E> layer) {
    // No-op: export views do not react to live layer changes
  }

  @Override
  public void enableMouseAndKeyListener(MouseActions mouseActions) {
    // No-op: export views are not interactive
  }

  @Override
  public MouseActionAdapter getMouseAdapter(String command) {
    return null;
  }

  @Override
  public JPopupMenu buildGraphicContextMenu(MouseEvent evt, List<Graphic> selected) {
    return null;
  }

  @Override
  public JPopupMenu buildContextMenu(MouseEvent evt) {
    return null;
  }

  @Override
  public boolean hasValidContent() {
    return getSourceImage() != null;
  }
}
