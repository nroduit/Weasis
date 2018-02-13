/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.api.gui.model;

import java.awt.geom.Rectangle2D;

/**
 * The Interface ViewModel.
 *
 */
public interface ViewModel {

    /**
     * Gets the X-offset in model coordinates of the upper left view pixel
     *
     * @return the X-offset in model coordinates of the upper left view pixel
     */
    double getModelOffsetX();

    /**
     * Gets the Y-offset in model coordinates of the upper left view pixel
     *
     * @return the y-offset in model coordinates of the upper left view pixel
     */
    double getModelOffsetY();

    /**
     * Sets the offset in model coordinates of the upper left view pixel
     *
     * @param modelOffsetX
     *            the x-offset in model coordinates of the upper left view pixel
     * @param modelOffsetY
     *            the y-offset in model coordinates of the upper left view pixel
     */
    void setModelOffset(double modelOffsetX, double modelOffsetY);

    /**
     * Gets the view scale.
     *
     * @return the current view scale
     */
    double getViewScale();

    /**
     * Sets the view scale.
     *
     * @param viewScale
     *            the new view scale
     */
    void setViewScale(double viewScale);

    /**
     * Gets the maximum view scale. Minimum view scale is defined by <code>1.0 / getViewScaleMax().doubleValue()</code>.
     *
     * @return the maximum view scale, if the maximum view scale is not specified, <code>null</code> is returned
     */
    double getViewScaleMin();

    /**
     * Sets the maximum view scale. Minimum view scale is defined by <code>1.0 / getViewScaleMax().doubleValue()</code>.
     *
     * @param viewScaleMax
     *            the maximum view scale, or <code>null</code> if a maximum view scale shall not be specified
     */
    void setViewScaleMin(double viewScaleMin);

    /**
     * Gets the maximum view scale. Minimum view scale is defined by <code>1.0 / getViewScaleMax().doubleValue()</code>.
     *
     * @return the maximum view scale, if the maximum view scale is not specified, <code>null</code> is returned
     */
    double getViewScaleMax();

    /**
     * Sets the maximum view scale. Minimum view scale is defined by <code>1.0 / getViewScaleMax().doubleValue()</code>.
     *
     * @param viewScaleMax
     *            the maximum view scale, or <code>null</code> if a maximum view scale shall not be specified
     */
    void setViewScaleMax(double viewScaleMax);

    /**
     * This method sets all view properties of this model with a single method call. The method results in a single
     * change event being generated. This is convenient when you need to adjust all the model data simultaneously and do
     * not want individual change events to occur.
     *
     * @param modelOffsetX
     *            the x-offset in model coordinates of the upper left view pixel
     * @param modelOffsetY
     *            the y-offset in model coordinates of the upper left view pixel
     * @param viewScale
     *            the new view scale
     * @see #setModelOffset
     * @see #setViewScale
     */
    void setModelOffset(double modelOffsetX, double modelOffsetY, double viewScale);

    /**
     * Gets the model area of this view model. The model area enables a viewport to specify scrolling limits and to
     * perform a "zoom all" operation.
     *
     * @return the model area rectangle, must not be null
     */
    Rectangle2D getModelArea();

    /**
     * Sets the model area of this view model. The model area enables a viewport to specify scrolling limits and to
     * perform a "zoom all" operation.
     *
     * @param r
     *            the model area rectangle, must not be null
     */
    void setModelArea(Rectangle2D r);

    /**
     * Gets the array of all view model change listeners. An empty array is returned for the case that no listeners have
     * been added so far.
     *
     * @return the array of all view model change listeners, never null
     */
    ViewModelChangeListener[] getViewModelChangeListeners();

    /**
     * Adds a new view model change listener to this view model.
     *
     * @param l
     *            the listener, ignored if it already exists or if it is null
     */
    void addViewModelChangeListener(ViewModelChangeListener l);

    /**
     * Removes an existing view model change listener from this view model.
     *
     * @param l
     *            the listener, ignored if it does not exists or if it is null
     */
    void removeViewModelChangeListener(ViewModelChangeListener l);
}
