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

import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeListener;
import java.util.Map;
import javax.swing.JComponent;
import org.weasis.core.api.gui.model.ViewModel;
import org.weasis.core.ui.model.GraphicModel;

public interface Canvas {

  JComponent getJComponent();

  AffineTransform getAffineTransform();

  AffineTransform getInverseTransform();

  void disposeView();

  /**
   * Gets the view model.
   *
   * @return the view model, never null
   */
  ViewModel getViewModel();

  /**
   * Sets the view model.
   *
   * @param viewModel the view model, never null
   */
  void setViewModel(ViewModel viewModel);

  Object getActionValue(String action);

  Map<String, Object> getActionsInView();

  /**
   * Zoom from the center of the canvas
   *
   * @param viewScale the scale factor (1.0 is the actual pixel size)
   */
  void zoom(Double viewScale);

  /**
   * Get the image scale factor witch matches to the dimension of the view
   *
   * @return the best fit ratio
   */
  double getBestFitViewScale();

  Point2D viewToModel(Double viewX, Double viewY);

  double viewToModelLength(Double viewLength);

  Point2D modelToView(Double modelX, Double modelY);

  double modelToViewLength(Double modelLength);

  Point2D getImageCoordinatesFromMouse(Integer x, Integer y);

  Point getMouseCoordinatesFromImage(Double x, Double y);

  void setGraphicManager(GraphicModel graphicManager);

  GraphicModel getGraphicManager();

  PropertyChangeListener getGraphicsChangeHandler();

  Point2D getClipViewCoordinatesOffset();

  Point2D getViewCoordinatesOffset();

  Rectangle2D getImageViewBounds();

  Rectangle2D getImageViewBounds(double viewportWidth, double viewportHeight);
}
