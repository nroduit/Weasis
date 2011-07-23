/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.graphic.model;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

import org.weasis.core.api.gui.model.ViewModel;
import org.weasis.core.api.gui.model.ViewModelChangeListener;

/**
 * The Class DefaultViewModel.
 * 
 * @author Nicolas Roduit
 */
public class DefaultViewModel implements ViewModel {

    /**
     * The x-offset in model coordinates of the upper left view pixel
     */
    private double modelOffsetX;
    /**
     * The y-offset in model coordinates of the upper left view pixel
     */
    private double modelOffsetY;
    /**
     * The current view scale
     */
    private double viewScale;
    /**
     * The maximum view scale. Minimum is given as 1.0 / viewScaleMax.
     */
    private double viewScaleMax;
    private double viewScaleMin;
    public static final double SCALE_MIN = 1.0 / 12.0;
    public static final double SCALE_MAX = 12.0;
    /**
     * This view model's area. Enables scrolling with scroll bars.
     */
    private Rectangle2D modelArea;
    /**
     * The list of change listeners
     */
    private final ArrayList viewModelChangeListenerList;

    public DefaultViewModel(double viewScaleMin, double viewScaleMax) {
        this.modelOffsetX = 0;
        this.modelOffsetY = 0;
        this.viewScale = 1.0;
        this.viewScaleMin = viewScaleMin;
        this.viewScaleMax = viewScaleMax;
        this.modelArea = new Rectangle2D.Double();
        this.viewModelChangeListenerList = new ArrayList();
    }

    public DefaultViewModel() {
        this(SCALE_MIN, SCALE_MAX);
    }

    public double getModelOffsetX() {
        return modelOffsetX;
    }

    public double getModelOffsetY() {
        return modelOffsetY;
    }

    public void setModelOffset(double modelOffsetX, double modelOffsetY) {
        if (this.modelOffsetX != modelOffsetX || this.modelOffsetY != modelOffsetY) {
            this.modelOffsetX = modelOffsetX;
            this.modelOffsetY = modelOffsetY;
            fireViewModelChanged();
        }
    }

    public void setModelOffset(double modelOffsetX, double modelOffsetY, double viewScale) {
        viewScale = maybeCropViewScale(viewScale);
        if (this.modelOffsetX != modelOffsetX || this.modelOffsetY != modelOffsetY || this.viewScale != viewScale) {
            this.modelOffsetX = modelOffsetX;
            this.modelOffsetY = modelOffsetY;
            this.viewScale = viewScale;
            fireViewModelChanged();
        }
    }

    public double getViewScale() {
        return viewScale;
    }

    public void setViewScale(double viewScale) {
        viewScale = maybeCropViewScale(viewScale);
        if (this.viewScale != viewScale) {
            this.viewScale = viewScale;
            fireViewModelChanged();
        }
    }

    public double getViewScaleMax() {
        return viewScaleMax;
    }

    public void setViewScaleMax(double viewScaleMax) {
        this.viewScaleMax = viewScaleMax;
    }

    public Rectangle2D getModelArea() {
        return new Rectangle2D.Double(modelArea.getX(), modelArea.getY(), modelArea.getWidth(), modelArea.getHeight());
    }

    public double getViewScaleMin() {
        return viewScaleMin;
    }

    public void setModelArea(Rectangle2D modelArea) {
        if (!this.modelArea.equals(modelArea)) {
            this.modelArea =
                new Rectangle2D.Double(modelArea.getX(), modelArea.getY(), modelArea.getWidth(), modelArea.getHeight());
            fireViewModelChanged();
        }
    }

    public void setViewScaleMin(double viewScaleMin) {
        this.viewScaleMin = viewScaleMin;
    }

    public ViewModelChangeListener[] getViewModelChangeListeners() {
        final ViewModelChangeListener[] viewModelChangeListeners =
            new ViewModelChangeListener[viewModelChangeListenerList.size()];
        return (ViewModelChangeListener[]) viewModelChangeListenerList.toArray(viewModelChangeListeners);
    }

    public void addViewModelChangeListener(ViewModelChangeListener l) {
        if (l != null && !viewModelChangeListenerList.contains(l)) {
            viewModelChangeListenerList.add(l);
        }
    }

    public void removeViewModelChangeListener(ViewModelChangeListener l) {
        if (l != null) {
            viewModelChangeListenerList.remove(l);
        }
    }

    protected void fireViewModelChanged() {
        for (int i = 0; i < viewModelChangeListenerList.size(); i++) {
            ViewModelChangeListener l = (ViewModelChangeListener) viewModelChangeListenerList.get(i);
            l.handleViewModelChanged(this);
        }
    }

    public static double cropViewScale(double viewScale, final double viewScaleMin, final double viewScaleMax) {
        if (viewScaleMax > 1.0) {
            if (viewScale < viewScaleMin) {
                viewScale = viewScaleMin;
            } else if (viewScale > viewScaleMax) {
                viewScale = viewScaleMax;
            }
        }
        return viewScale;
    }

    private double maybeCropViewScale(double viewScale) {
        return cropViewScale(viewScale, getViewScaleMin(), getViewScaleMax());
    }

    public void adjustMinViewScaleFromImage(int width, int height) {
        double ratio = 250.0 / (width > height ? width : height);
        if (ratio < viewScaleMin) {
            this.viewScaleMin = ratio;
        }
    }
}
