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

import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.Image2DViewer;
import org.weasis.core.api.gui.model.ViewModel;
import org.weasis.core.api.gui.model.ViewModelChangeListener;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.AffineTransformOp;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.imp.XmlGraphicModel;
import org.weasis.core.ui.model.layer.GraphicModelChangeListener;
import org.weasis.core.ui.model.layer.imp.RenderedImageLayer;
import org.weasis.core.ui.model.utils.imp.DefaultViewModel;
import org.weasis.core.util.LangUtil;

/**
 * The Class GraphicsPane.
 *
 * @author Nicolas Roduit
 */
public abstract class GraphicsPane extends JComponent implements Canvas {

  private static final Logger LOGGER = LoggerFactory.getLogger(GraphicsPane.class);

  protected GraphicModel graphicManager;
  protected ViewModel viewModel;
  protected final LayerModelHandler layerModelHandler;
  protected final ViewModelHandler viewModelHandler;

  protected final DrawingsKeyListeners drawingsKeyListeners;
  protected final HashMap<String, Object> actionsInView = new HashMap<>();
  protected final AffineTransform affineTransform = new AffineTransform();
  protected final AffineTransform inverseTransform = new AffineTransform();

  protected final PropertyChangeListener graphicsChangeHandler;

  public GraphicsPane(ViewModel viewModel) {
    setOpaque(false);

    this.layerModelHandler = new LayerModelHandler();

    this.graphicManager = new XmlGraphicModel();
    this.graphicManager.addChangeListener(layerModelHandler);

    this.viewModelHandler = new ViewModelHandler();

    this.viewModel = Optional.ofNullable(viewModel).orElseGet(DefaultViewModel::new);
    this.viewModel.addViewModelChangeListener(viewModelHandler);
    this.drawingsKeyListeners = new DrawingsKeyListeners(this);
    this.graphicsChangeHandler = new PropertyChangeHandler(this);
  }

  @Override
  public void setGraphicManager(GraphicModel graphicManager) {
    Objects.requireNonNull(graphicManager);
    GraphicModel graphicManagerOld = this.graphicManager;
    if (!Objects.equals(graphicManager, graphicManagerOld)) {
      removeGraphicManager(graphicManagerOld, layerModelHandler);
      this.graphicManager = graphicManager;
      graphicManager.addGraphicChangeHandler(graphicsChangeHandler);
      if (this instanceof ViewCanvas<?> viewCanvas) {
        graphicManager.updateLabels(Boolean.TRUE, viewCanvas);
      }
      graphicManager.addChangeListener(layerModelHandler);
      firePropertyChange("graphicManager", graphicManagerOld, this.graphicManager);
    }
  }

  public DrawingsKeyListeners getDrawingsKeyListeners() {
    return drawingsKeyListeners;
  }

  @Override
  public GraphicModel getGraphicManager() {
    return graphicManager;
  }

  @Override
  public AffineTransform getAffineTransform() {
    return affineTransform;
  }

  @Override
  public AffineTransform getInverseTransform() {
    return inverseTransform;
  }

  @Override
  public PropertyChangeListener getGraphicsChangeHandler() {
    return graphicsChangeHandler;
  }

  @Override
  public void disposeView() {
    Optional.ofNullable(viewModel)
        .ifPresent(model -> model.removeViewModelChangeListener(viewModelHandler));
    // Unregister listener
    removeGraphicManager(graphicManager, layerModelHandler);
  }

  /**
   * Gets the view model.
   *
   * @return the view model, never null
   */
  @Override
  public ViewModel getViewModel() {
    return viewModel;
  }

  /**
   * Sets the view model.
   *
   * @param viewModel the view model, never null
   */
  @Override
  public void setViewModel(ViewModel viewModel) {
    ViewModel viewModelOld = this.viewModel;
    if (viewModelOld != viewModel) {
      if (viewModelOld != null) {
        viewModelOld.removeViewModelChangeListener(viewModelHandler);
      }
      this.viewModel = viewModel;
      if (this.viewModel != null) {
        this.viewModel.addViewModelChangeListener(viewModelHandler);
      }
      firePropertyChange("viewModel", viewModelOld, this.viewModel);
    }
  }

  @Override
  public Object getActionValue(String action) {
    if (action == null) {
      return null;
    }
    return actionsInView.get(action);
  }

  @Override
  public Map<String, Object> getActionsInView() {
    return actionsInView;
  }

  @Override
  public JComponent getJComponent() {
    return this;
  }

  @Override
  public void zoom(Double viewScale) {
    getViewModel()
        .setModelOffset(
            viewModel.getModelOffsetX(), viewModel.getModelOffsetY(), cropViewScale(viewScale));
  }

  public void zoom(Rectangle2D zoomRect) {
    final Rectangle2D modelArea = viewModel.getModelArea();
    getViewModel()
        .setModelOffset(
            modelArea.getCenterX() - zoomRect.getCenterX(),
            modelArea.getCenterY() - zoomRect.getCenterY(),
            Math.min(getWidth() / zoomRect.getWidth(), getHeight() / zoomRect.getHeight()));
  }

  @Override
  public double getBestFitViewScale() {
    final Rectangle2D modelArea = viewModel.getModelArea();
    return cropViewScale(
        Math.min(getWidth() / modelArea.getWidth(), getHeight() / modelArea.getHeight()));
  }

  @Override
  public Point2D viewToModel(Double viewX, Double viewY) {
    Point2D p = getViewCoordinatesOffset();
    p.setLocation(viewX - p.getX(), viewY - p.getY());
    inverseTransform.transform(p, p);
    return p;
  }

  @Override
  public double viewToModelLength(Double viewLength) {
    return viewLength / viewModel.getViewScale();
  }

  @Override
  public Point2D modelToView(Double modelX, Double modelY) {
    Point2D p2 = new Point2D.Double(modelX, modelY);
    affineTransform.transform(p2, p2);

    Point2D p = getViewCoordinatesOffset();
    p2.setLocation(p2.getX() + p.getX(), p2.getY() + p.getY());
    return p2;
  }

  @Override
  public double modelToViewLength(Double modelLength) {
    return modelLength * viewModel.getViewScale();
  }

  @Override
  public Point2D getViewCoordinatesOffset() {
    Rectangle2D b = getImageViewBounds(getWidth(), getHeight());
    return new Point2D.Double(b.getX(), b.getY());
  }

  public Path2D getVisibleImageViewBounds() {
    Point2D p = getClipViewCoordinatesOffset();
    Rectangle2D bounds = new Rectangle2D.Double(-p.getX(), -p.getY(), getWidth(), getHeight());
    Shape path = inverseTransform.createTransformedShape(bounds);
    if (path instanceof Path2D path2D) {
      return path2D;
    }
    Path2D path2D = new Path2D.Double();
    path2D.append(bounds, false);
    return path2D;
  }

  @Override
  public Rectangle2D getImageViewBounds() {
    return getImageViewBounds(getWidth(), getHeight());
  }

  @Override
  public Rectangle2D getImageViewBounds(double viewportWidth, double viewportHeight) {
    Rectangle2D b = affineTransform.createTransformedShape(viewModel.getModelArea()).getBounds2D();
    ViewModel m = getViewModel();
    double viewOffsetX = (viewportWidth - b.getWidth()) * 0.5;
    double viewOffsetY = (viewportHeight - b.getHeight()) * 0.5;
    double offsetX = viewOffsetX - m.getModelOffsetX() * m.getViewScale();
    double offsetY = viewOffsetY - m.getModelOffsetY() * m.getViewScale();
    b.setRect(offsetX, offsetY, b.getWidth(), b.getHeight());
    return b;
  }

  @Override
  public Point2D getClipViewCoordinatesOffset() {
    Point2D p = getViewCoordinatesOffset();
    p.setLocation(Math.max(p.getX(), 0.0), Math.max(p.getY(), 0.0));
    return p;
  }

  @Override
  public Point2D getImageCoordinatesFromMouse(Integer x, Integer y) {
    Point2D p = getClipViewCoordinatesOffset();
    p.setLocation(x - p.getX(), y - p.getY());

    inverseTransform.transform(p, p);
    return p;
  }

  @Override
  public Point getMouseCoordinatesFromImage(Double x, Double y) {
    Point2D p2 = new Point2D.Double(x, y);
    affineTransform.transform(p2, p2);

    Point2D p = getClipViewCoordinatesOffset();
    return new Point(
        (int) Math.floor(p2.getX() + p.getX() + 0.5), (int) Math.floor(p2.getY() + p.getY() + 0.5));
  }

  protected void updateAffineTransform(
      Image2DViewer<? extends ImageElement> view2d,
      ImageOpNode node,
      RenderedImageLayer<?> imageLayer,
      double viewSizeShift) {
    Rectangle2D modelArea = getViewModel().getModelArea();
    double viewScale = getViewModel().getViewScale();

    double rWidth = modelArea.getWidth();
    double rHeight = modelArea.getHeight();

    boolean flip = LangUtil.getNULLtoFalse((Boolean) view2d.getActionValue((ActionW.FLIP.cmd())));
    Integer rotationAngle = (Integer) view2d.getActionValue(ActionW.ROTATION.cmd());

    affineTransform.setToScale(flip ? -viewScale : viewScale, viewScale);
    if (rotationAngle != null && rotationAngle > 0) {
      affineTransform.rotate(Math.toRadians(rotationAngle), rWidth / 2.0, rHeight / 2.0);
    }
    if (flip) {
      affineTransform.translate(-rWidth, 0.0);
    }

    if (node != null) {
      Rectangle2D imgBounds = affineTransform.createTransformedShape(modelArea).getBounds2D();

      double diffX = 0.0;
      double diffY = 0.0;
      Rectangle2D viewBounds =
          new Rectangle2D.Double(0, 0, getWidth() + viewSizeShift, getHeight() + viewSizeShift);
      Rectangle2D srcBounds = getImageViewBounds(viewBounds.getWidth(), viewBounds.getHeight());

      Rectangle2D dstBounds;
      if (viewBounds.contains(srcBounds)) {
        dstBounds = srcBounds;
      } else {
        dstBounds = viewBounds.createIntersection(srcBounds);

        if (srcBounds.getX() < 0.0) {
          diffX += srcBounds.getX();
        }
        if (srcBounds.getY() < 0.0) {
          diffY += srcBounds.getY();
        }
      }

      double[] fmx = new double[6];
      affineTransform.getMatrix(fmx);
      // adjust transformation matrix => move the center to keep all the image
      fmx[4] -= imgBounds.getX() - diffX;
      fmx[5] -= imgBounds.getY() - diffY;
      affineTransform.setTransform(fmx[0], fmx[1], fmx[2], fmx[3], fmx[4], fmx[5]);

      // Convert to openCV affine matrix
      List<Double> m = List.of(fmx[0], fmx[2], fmx[4], fmx[1], fmx[3], fmx[5]);
      node.setParam(AffineTransformOp.P_AFFINE_MATRIX, m);

      node.setParam(AffineTransformOp.P_DST_BOUNDS, dstBounds);
      imageLayer.updateDisplayOperations();
    }

    // Keep the coordinates of the original image when cropping
    Point offset = view2d.getImageLayer().getOffset();
    if (offset != null) {
      affineTransform.translate(-offset.getX(), -offset.getY());
    }

    try {
      inverseTransform.setTransform(affineTransform.createInverse());
    } catch (NoninvertibleTransformException e) {
      LOGGER.error("Create inverse transform", e);
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    // honor the opaque property
    if (isOpaque()) {
      g.setColor(getBackground());
      g.fillRect(0, 0, getWidth(), getHeight());
    }
  }

  // /////////////////////////////////////////////////////////////////////////////////////
  // Helpers
  private double cropViewScale(double viewScale) {
    return DefaultViewModel.cropViewScale(
        viewScale, viewModel.getViewScaleMin(), viewModel.getViewScaleMax());
  }

  public static void repaint(Canvas canvas, Rectangle rectangle) {
    if (rectangle != null) {
      // Add the offset of the canvas
      Point2D p = canvas.getClipViewCoordinatesOffset();
      int x = (int) (rectangle.x + p.getX());
      int y = (int) (rectangle.y + p.getY());
      canvas.getJComponent().repaint(new Rectangle(x, y, rectangle.width, rectangle.height));
    }
  }

  // /////////////////////////////////////////////////////////////////////////////////////
  // Inner Classes
  /**
   * The Class LayerModelHandler.
   *
   * @author Nicolas Roduit
   */
  private class LayerModelHandler implements GraphicModelChangeListener {
    @Override
    public void handleModelChanged(GraphicModel modelList) {
      repaint();
    }
  }

  /**
   * The Class ViewModelHandler.
   *
   * @author Nicolas Roduit
   */
  private class ViewModelHandler implements ViewModelChangeListener {
    @Override
    public void handleViewModelChanged(ViewModel viewModel) {
      repaint();
    }
  }
}
