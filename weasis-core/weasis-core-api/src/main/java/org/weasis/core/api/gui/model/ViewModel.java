/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.model;

import java.awt.geom.Rectangle2D;

/** The Interface ViewModel. */
public interface ViewModel {

  /**
   * Gets the X-offset from the center model coordinates (image center) to the view center.
   *
   * @return the X-offset.
   */
  double getModelOffsetX();

  /**
   * Gets the Y-offset from the center model coordinates (image center) to the view center.
   *
   * @return the Y-offset
   */
  double getModelOffsetY();

  /**
   * Sets the offset from the center model coordinates (image center) to the view center.
   *
   * <p>With (0.0,0.0) the image will be in the center of the view. Negative values will shift the
   * image on the bottom right.
   *
   * @param modelOffsetX the X-offset
   * @param modelOffsetY the Y-offset
   */
  void setModelOffset(double modelOffsetX, double modelOffsetY);

  /**
   * Gets the view scale factor.
   *
   * @return the current view scale factor
   */
  double getViewScale();

  /**
   * Sets the view scale factor.
   *
   * @param viewScale the new view scale factor
   */
  void setViewScale(double viewScale);

  /**
   * Gets the minimum view scale factor. The min value is defined by <code>
   * 1.0 / DefaultViewModel.SCALE_MAX</code>.
   *
   * @return the minimum view scale factor
   */
  double getViewScaleMin();

  /**
   * Sets the minimum view scale factor. The min value is defined by <code>
   * 1.0 / DefaultViewModel.SCALE_MAX</code>.
   *
   * @param viewScaleMin the minimum view scale factor
   */
  void setViewScaleMin(double viewScaleMin);

  /**
   * Gets the maximum view scale factor. The max value is defined by <code>
   * DefaultViewModel.SCALE_MAX</code>.
   *
   * @return the maximum view scale factor
   */
  double getViewScaleMax();

  /**
   * Sets the maximum view scale factor. The max value is defined by <code>
   * DefaultViewModel.SCALE_MAX</code>.
   *
   * @param viewScaleMax the maximum view scale factor
   */
  void setViewScaleMax(double viewScaleMax);

  /**
   * Sets the offset from the center model coordinates (image center) to the view center. This
   * method is convenient when you need to adjust all the model data simultaneously and do not want
   * individual change events to occur.
   *
   * @param modelOffsetX the X-offset
   * @param modelOffsetY the Y-offset
   * @param viewScale the new view scale
   * @see #setModelOffset
   * @see #setViewScale
   */
  void setModelOffset(double modelOffsetX, double modelOffsetY, double viewScale);

  /**
   * Gets the model area of this view model (image size).
   *
   * @return the model area rectangle, must not be null
   */
  Rectangle2D getModelArea();

  /**
   * Sets the model area of this view model (image size).
   *
   * @param r the model area rectangle, must not be null
   */
  void setModelArea(Rectangle2D r);

  /**
   * Gets the array of all view model change listeners.
   *
   * @return the array of all view model change listeners, never null
   */
  ViewModelChangeListener[] getViewModelChangeListeners();

  /**
   * Adds a new view model change listener to this view model.
   *
   * @param l the listener, ignored if it already exists or if it is null
   */
  void addViewModelChangeListener(ViewModelChangeListener l);

  /**
   * Removes an existing view model change listener from this view model.
   *
   * @param l the listener, ignored if it does not exist or if it is null
   */
  void removeViewModelChangeListener(ViewModelChangeListener l);
}
