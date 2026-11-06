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
import org.weasis.core.ui.model.layer.Layer;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.layer.imp.RenderedImageLayer;
import org.weasis.core.ui.model.utils.imp.DefaultViewModel;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.MathUtil;

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
  private ZoomGesture zoomGesture;

  protected final PropertyChangeListener graphicsChangeHandler;

  public GraphicsPane(ViewModel viewModel) {
    setOpaque(true);

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
      graphicManager.getLayers().forEach(this::applyLayerTypeVisibility);
      firePropertyChange("graphicManager", graphicManagerOld, this.graphicManager);
    }
  }

  @Override
  public void setLayerTypeVisibility(LayerType type, boolean visible) {
    if (isLayerTypeVisible(type) != visible) {
      Canvas.super.setLayerTypeVisibility(type, visible);
      repaint();
    }
  }

  /** Applies the per-view visibility of the layer's type (see {@link #isLayerTypeVisible}). */
  private void applyLayerTypeVisibility(Layer layer) {
    Boolean visible = (Boolean) actionsInView.get(layer.getType().name());
    if (visible != null && !visible.equals(layer.getVisible())) {
      layer.setVisible(visible);
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

  /**
   * Zooms keeping in place the model point shown at {@code anchor}. The model offset is in view
   * axes (after rotation and flip) and in model units: a point {@code q} of these axes is shown at
   * the anchor when the offset is {@code q − anchor / s}, whatever the rotation, the flip or the
   * crop.
   *
   * <p>The point kept is the one displayed under the anchor, with the image origin on a whole pixel
   * (see {@link #getImageViewBounds(double, double)}), so that the rounding is not multiplied by
   * the zoom ratio. It is kept for the whole gesture (successive zooms at the same anchor with no
   * other change of the view): it then stays within half a pixel of the anchor at every step,
   * without drifting.
   *
   * @param viewScale the new scale, cropped to the limits of the view model
   * @param anchor the anchor as an offset from the center of the view, in view pixels; null zooms
   *     around the center
   */
  public void zoomAt(double viewScale, Point2D anchor) {
    double scale = cropViewScale(viewScale);
    ViewModel m = getViewModel();
    double oldScale = m.getViewScale();
    if (anchor == null || oldScale <= 0.0 || MathUtil.isEqual(oldScale, scale)) {
      m.setModelOffset(m.getModelOffsetX(), m.getModelOffsetY(), scale);
      return;
    }
    Point2D offset = new Point2D.Double(m.getModelOffsetX(), m.getModelOffsetY());
    ZoomGesture gesture = zoomGesture;
    Point2D point;
    if (gesture != null && gesture.continues(anchor, offset, oldScale, affineTransform)) {
      point = gesture.point();
    } else {
      point = anchorPoint(offset, oldScale, anchor, getOriginRounding());
    }
    Point2D newOffset = offsetShowing(point, scale, anchor);
    m.setModelOffset(newOffset.getX(), newOffset.getY(), scale);
    zoomGesture = new ZoomGesture(anchor, point, newOffset, scale, linearPart(affineTransform));
  }

  /**
   * The point of the view axes displayed at {@code anchor}.
   *
   * @param rounding how far the image origin is displayed from its exact position, in view pixels
   */
  static Point2D anchorPoint(Point2D offset, double scale, Point2D anchor, Point2D rounding) {
    return new Point2D.Double(
        offset.getX() + (anchor.getX() - rounding.getX()) / scale,
        offset.getY() + (anchor.getY() - rounding.getY()) / scale);
  }

  /** The model offset showing the point of the view axes {@code point} at {@code anchor}. */
  static Point2D offsetShowing(Point2D point, double scale, Point2D anchor) {
    return new Point2D.Double(
        point.getX() - anchor.getX() / scale, point.getY() - anchor.getY() / scale);
  }

  /** How far the image origin is displayed from its exact position, in view pixels. */
  private Point2D getOriginRounding() {
    Rectangle2D b = affineTransform.createTransformedShape(viewModel.getModelArea()).getBounds2D();
    ViewModel m = getViewModel();
    double x = (getWidth() - b.getWidth()) * 0.5 - m.getModelOffsetX() * m.getViewScale();
    double y = (getHeight() - b.getHeight()) * 0.5 - m.getModelOffsetY() * m.getViewScale();
    return new Point2D.Double(Math.floor(x + 0.5) - x, Math.floor(y + 0.5) - y);
  }

  /** The rotation and flip of a transform: its linear part divided by its scale. */
  private static double[] linearPart(AffineTransform t) {
    double scale = Math.hypot(t.getScaleX(), t.getShearY());
    if (scale <= 0.0) {
      return new double[4];
    }
    return new double[] {
      t.getScaleX() / scale, t.getShearX() / scale, t.getShearY() / scale, t.getScaleY() / scale
    };
  }

  /**
   * The point kept by an anchored zoom, valid while the next zoom has the same anchor and finds the
   * offset, the scale and the orientation it left.
   */
  private record ZoomGesture(
      Point2D anchor, Point2D point, Point2D offset, double scale, double[] orientation) {
    boolean continues(Point2D anchor, Point2D offset, double scale, AffineTransform transform) {
      return this.anchor.equals(anchor)
          && !MathUtil.isDifferent(this.offset.getX(), offset.getX())
          && !MathUtil.isDifferent(this.offset.getY(), offset.getY())
          && !MathUtil.isDifferent(this.scale, scale)
          && sameOrientation(this.orientation, linearPart(transform));
    }

    private static boolean sameOrientation(double[] a, double[] b) {
      for (int i = 0; i < a.length; i++) {
        if (Math.abs(a[i] - b[i]) > 1e-9) {
          return false;
        }
      }
      return true;
    }
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
    // The affine transform already holds the part of the image outside the view
    Point2D p = getClipViewCoordinatesOffset();
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

    Point2D p = getClipViewCoordinatesOffset();
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
    // Whole pixels: Java2D draws the image raster at the nearest pixel, so the graphics and the
    // mouse mapping must use the same origin. A fraction left here shifts them by up to 0.5 pixel.
    double offsetX = Math.floor(viewOffsetX - m.getModelOffsetX() * m.getViewScale() + 0.5);
    double offsetY = Math.floor(viewOffsetY - m.getModelOffsetY() * m.getViewScale() + 0.5);
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

  /**
   * Device pixels per view pixel at which the image is rendered: 1 on screen, more for a printer
   * whose graphics are scaled by the same factor when the view is drawn.
   */
  protected double getRenderScale() {
    return 1.0;
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

    boolean flip = LangUtil.nullToFalse((Boolean) view2d.getActionValue((ActionW.FLIP.cmd())));
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

      // Convert to openCV affine matrix, at the resolution the image is rendered at
      double rs = getRenderScale();
      List<Double> m =
          List.of(fmx[0] * rs, fmx[2] * rs, fmx[4] * rs, fmx[1] * rs, fmx[3] * rs, fmx[5] * rs);
      node.setParam(AffineTransformOp.P_AFFINE_MATRIX, m);
      node.setParam(
          AffineTransformOp.P_DST_BOUNDS,
          rs == 1.0
              ? dstBounds
              : new Rectangle2D.Double(
                  dstBounds.getX() * rs,
                  dstBounds.getY() * rs,
                  dstBounds.getWidth() * rs,
                  dstBounds.getHeight() * rs));
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

    @Override
    public void handleLayerAdded(GraphicModel modelList, Layer layer) {
      applyLayerTypeVisibility(layer);
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
