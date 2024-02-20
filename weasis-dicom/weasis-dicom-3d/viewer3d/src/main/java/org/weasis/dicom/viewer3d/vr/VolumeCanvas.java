/*
 * Copyright (c) 2012 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr;

import com.jogamp.opengl.awt.GLJPanel;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.Window;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.model.ViewModel;
import org.weasis.core.api.gui.model.ViewModelChangeListener;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.editor.image.Canvas;
import org.weasis.core.ui.editor.image.DrawingsKeyListeners;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.PropertyChangeHandler;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.imp.XmlGraphicModel;
import org.weasis.core.ui.model.layer.GraphicModelChangeListener;
import org.weasis.core.ui.model.utils.imp.DefaultViewModel;
import org.weasis.core.ui.pref.Monitor;
import org.weasis.core.util.LangUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer3d.EventManager;
import org.weasis.dicom.viewer3d.geometry.Camera;

public class VolumeCanvas extends GLJPanel implements Canvas {

  private static final Logger LOGGER = LoggerFactory.getLogger(VolumeCanvas.class);

  private static final double ROUNDING_FACTOR = 0.5;
  protected final Camera camera;
  protected DicomVolTexture volTexture;
  protected GraphicModel graphicManager;
  protected VolumeViewModel viewModel;

  protected final EventManager eventManager;
  protected final LayerModelHandler layerModelHandler;
  protected final ViewModelHandler viewModelHandler;
  protected final HashMap<String, Object> actionsInView = new HashMap<>();
  protected final AffineTransform affineTransform = new AffineTransform();
  protected final AffineTransform inverseTransform = new AffineTransform();
  protected final DrawingsKeyListeners drawingsKeyListeners;
  protected final PropertyChangeListener graphicsChangeHandler;

  public VolumeCanvas(
      ImageViewerEventManager<DicomImageElement> eventManager,
      DicomVolTexture volTexture,
      VolumeViewModel viewModel) {
    this.volTexture = volTexture;
    this.eventManager = (EventManager) eventManager;
    this.camera = new Camera(this);
    this.layerModelHandler = new LayerModelHandler();

    this.graphicManager = new XmlGraphicModel();
    this.graphicManager.addChangeListener(layerModelHandler);

    this.viewModelHandler = new ViewModelHandler();

    this.viewModel = Optional.ofNullable(viewModel).orElseGet(VolumeViewModel::new);
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

  @Override
  public VolumeViewModel getViewModel() {
    return viewModel;
  }

  @Override
  public void setViewModel(ViewModel viewModel) {
    ViewModel viewModelOld = this.viewModel;
    if (viewModelOld != viewModel) {
      if (viewModelOld != null) {
        viewModelOld.removeViewModelChangeListener(viewModelHandler);
      }
      this.viewModel = (VolumeViewModel) viewModel;
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

  public void setActionsInView(String action, Object value) {
    setActionsInView(action, value, false);
  }

  public void setActionsInView(String action, Object value, Boolean repaint) {
    if (action != null) {
      actionsInView.put(action, value);
      if (LangUtil.getNULLtoFalse(repaint)) {
        repaint();
      }
    }
  }

  public DicomVolTexture getVolTexture() {
    return volTexture;
  }

  public boolean isReadyForRendering() {
    return volTexture != null && volTexture.isReadyForDisplay();
  }

  public double getAspectRatio() {
    return getSurfaceWidth() / (double) getSurfaceHeight();
  }

  public void setRotation(Integer val) {
    int rotate = val;
    //    if (ViewTexture.ViewType.SAGITTAL.equals(getViewType())) {
    //      rotate = val - 90;
    //    }

    camera.setRotation(rotate);
  }

  @Override
  public void zoom(Double viewScale) {
    double ratio = getZoomRatioFromType(viewScale);
    camera.setZoomFactor(ratio);
  }

  @Override
  public double getRealWorldViewScale() {
    double viewScale = 0.0;
    DicomVolTexture vol = getVolTexture();
    if (vol != null) {
      Window win = SwingUtilities.getWindowAncestor(this);
      if (win != null) {
        GraphicsConfiguration config = win.getGraphicsConfiguration();
        Monitor monitor = MeasureTool.viewSetting.getMonitor(config.getDevice());
        if (monitor != null) {
          double realFactor = monitor.getRealScaleFactor();
          if (realFactor > 0.0) {
            Unit imgUnit = vol.getPixelSpacingUnit();
            if (!Unit.PIXEL.equals(imgUnit)) {
              viewScale = imgUnit.getConvFactor() * vol.getTexelSize().x / realFactor;
              viewScale = -adjustViewScale(viewScale);
            }
          }
        }
      }
    }
    return viewScale;
  }

  public double adjustViewScale(double viewScale) {
    double ratio = viewScale;
    if (ratio < Camera.SCALE_MIN) {
      ratio = Camera.SCALE_MIN;
    } else if (ratio > Camera.SCALE_MAX) {
      ratio = Camera.SCALE_MAX;
    }
    return adjustViewScale(eventManager, ratio);
  }

  @Override
  public double getBestFitViewScale() {
    final double viewportWidth = getWidth() - 1.0;
    final double viewportHeight = getHeight() - 1.0;
    Vector3d d = getDisplayDimensions();
    double min = Math.min(Math.min(viewportWidth / d.x, viewportWidth / d.y), viewportHeight / d.z);
    return adjustViewScale(Math.sqrt(min * 1.3));
  }

  public Vector3d getDisplayDimensions() {
    DicomVolTexture vol = getVolTexture();
    if (vol != null) {
      Vector3d v = new Vector3d(volTexture.width, volTexture.height, volTexture.depth);
      v.mul(vol.getTexelSize());
      return v;
    }
    return new Vector3d(512, 512, 512);
  }

  private double cropViewScale(double viewScale) {
    return DefaultViewModel.cropViewScale(viewScale, Camera.SCALE_MIN, Camera.SCALE_MAX);
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
  public Point2D getImageCoordinatesFromMouse(Integer x, Integer y) {
    Point2D p = getClipViewCoordinatesOffset();
    p.setLocation(x - p.getX(), y - p.getY());

    getInverseTransform().transform(p, p);
    return p;
  }

  @Override
  public Point getMouseCoordinatesFromImage(Double x, Double y) {
    Point2D p2 = new Point2D.Double(x, y);
    getAffineTransform().transform(p2, p2);

    Point2D p = getClipViewCoordinatesOffset();
    return new Point(
        (int) Math.floor(p2.getX() + p.getX() + ROUNDING_FACTOR),
        (int) Math.floor(p2.getY() + p.getY() + ROUNDING_FACTOR));
  }

  @Override
  public Point2D getClipViewCoordinatesOffset() {
    Point2D p = getViewCoordinatesOffset();
    p.setLocation(Math.max(p.getX(), 0.0), Math.max(p.getY(), 0.0));
    return p;
  }

  @Override
  public Point2D getViewCoordinatesOffset() {
    Rectangle2D b = getImageViewBounds(getWidth(), getHeight());
    return new Point2D.Double(b.getX(), b.getY());
  }

  @Override
  public Rectangle2D getImageViewBounds() {
    return getImageViewBounds(getWidth(), getHeight());
  }

  @Override
  public Rectangle2D getImageViewBounds(double viewportWidth, double viewportHeight) {
    return new Rectangle2D.Double(0, 0, viewportWidth, viewportHeight);
  }

  @Override
  public DrawingsKeyListeners getDrawingsKeyListeners() {
    return drawingsKeyListeners;
  }

  private class LayerModelHandler implements GraphicModelChangeListener {
    @Override
    public void handleModelChanged(GraphicModel modelList) {
      repaint();
    }
  }

  private class ViewModelHandler implements ViewModelChangeListener {
    @Override
    public void handleViewModelChanged(ViewModel viewModel) {
      repaint();
    }
  }
}
