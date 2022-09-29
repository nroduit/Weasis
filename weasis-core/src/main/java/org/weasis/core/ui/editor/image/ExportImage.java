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
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import javax.swing.ToolTipManager;
import org.weasis.core.api.gui.model.ViewModel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.ui.model.layer.LayerAnnotation;

public class ExportImage<E extends ImageElement> extends DefaultView2d<E> {

  private final ViewCanvas<E> view2d;
  private Graphics2D currentG2d;
  private double imagePrintingResolution = 1.0;

  public ExportImage(ViewCanvas<E> view2d) {
    super(view2d.getEventManager(), null);
    this.view2d = view2d;
    // Remove OpEventListener to avoid reseting some parameters when setting the series
    this.imageLayer.removeEventListener(imageLayer.getDisplayOpManager());
    setFont(FontItem.MINI.getFont());
    this.infoLayer = view2d.getInfoLayer().getLayerCopy(this, false);
    infoLayer.setVisible(view2d.getInfoLayer().getVisible());
    infoLayer.setShowBottomScale(false);
    // For exporting view, remove Pixel value, Preloading bar, Key Object
    infoLayer.setDisplayPreferencesValue(LayerAnnotation.PIXEL, false);
    infoLayer.setDisplayPreferencesValue(LayerAnnotation.PRELOADING_BAR, false);

    // Copy image operations from view2d
    SimpleOpManager operations = imageLayer.getDisplayOpManager();
    for (ImageOpNode op : view2d.getImageLayer().getDisplayOpManager().getOperations()) {
      operations.addImageOperationAction(op.copy());
    }
    // Copy the current values of image operations
    view2d.copyActionWState(actionsInView);

    setPreferredSize(new Dimension(1024, 1024));
    ViewModel model = view2d.getViewModel();

    Rectangle2D canvas =
        new Rectangle2D.Double(
            0, 0, view2d.getJComponent().getWidth(), view2d.getJComponent().getHeight());
    actionsInView.put("origin.image.bound", canvas);
    actionsInView.put("origin.zoom", view2d.getActionValue(ActionW.ZOOM.cmd()));
    actionsInView.put(
        "origin.center.offset",
        new Point2D.Double(model.getModelOffsetX(), model.getModelOffsetY()));
    // Do not use setSeries() because the view will be reset
    this.series = view2d.getSeries();
    setImage(view2d.getImage());
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
    graphicManager.removeChangeListener(layerModelHandler);
    graphicManager.removeGraphicChangeHandler(graphicsChangeHandler);
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
    if (g instanceof Graphics2D graphics2D) {
      draw(graphics2D);
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
    // Set label box size and spaces between items
    graphicManager.updateLabels(Boolean.TRUE, this);

    Point2D p = getClipViewCoordinatesOffset();
    g2d.translate(p.getX(), p.getY());

    // TODO fix rotation issue
    Integer rotationAngle = (Integer) actionsInView.get(ActionW.ROTATION.cmd());
    if ((rotationAngle == null || rotationAngle == 0)
        && g2d.getClass().getName().contains("print")) {
      imageLayer.drawImageForPrinter(g2d, imagePrintingResolution, this);
    } else {
      imageLayer.drawImage(g2d);
    }

    drawLayers(g2d, affineTransform, inverseTransform);
    g2d.translate(-p.getX(), -p.getY());

    if (infoLayer != null) {
      infoLayer.paint(g2d);
    }
    GuiUtils.resetRenderingHints(g2d, oldRenderingHints);
    g2d.setPaint(oldColor);
    g2d.setStroke(oldStroke);

    // Reset label box size and spaces between items
    graphicManager.updateLabels(Boolean.TRUE, view2d);

    currentG2d = null;
  }

  @Override
  public void handleLayerChanged(ImageLayer<E> layer) {
    // Do nothing
  }

  @Override
  public void enableMouseAndKeyListener(MouseActions mouseActions) {
    // Do nothing
  }
}
