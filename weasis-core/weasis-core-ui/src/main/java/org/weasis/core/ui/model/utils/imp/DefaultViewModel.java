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
package org.weasis.core.ui.model.utils.imp;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

import org.weasis.core.api.gui.model.ViewModel;
import org.weasis.core.api.gui.model.ViewModelChangeListener;
import org.weasis.core.api.gui.util.MathUtil;

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
    private final ArrayList<ViewModelChangeListener> viewModelChangeListenerList;
    private boolean enableViewModelChangeListeners;

    public DefaultViewModel(double viewScaleMin, double viewScaleMax) {
        this.modelOffsetX = 0;
        this.modelOffsetY = 0;
        this.viewScale = 1.0;
        this.viewScaleMin = viewScaleMin;
        this.viewScaleMax = viewScaleMax;
        this.modelArea = new Rectangle2D.Double();
        this.viewModelChangeListenerList = new ArrayList<>();
        this.enableViewModelChangeListeners = true;
    }

    public DefaultViewModel() {
        this(SCALE_MIN, SCALE_MAX);
    }

    public boolean isEnableViewModelChangeListeners() {
        return enableViewModelChangeListeners;
    }

    public void setEnableViewModelChangeListeners(boolean enableViewModelChangeListeners) {
        this.enableViewModelChangeListeners = enableViewModelChangeListeners;
    }

    @Override
    public double getModelOffsetX() {
        return modelOffsetX;
    }

    @Override
    public double getModelOffsetY() {
        return modelOffsetY;
    }

    @Override
    public void setModelOffset(double modelOffsetX, double modelOffsetY) {
        if (MathUtil.isDifferent(this.modelOffsetX, modelOffsetX)
            || MathUtil.isDifferent(this.modelOffsetY, modelOffsetY)) {
            this.modelOffsetX = modelOffsetX;
            this.modelOffsetY = modelOffsetY;
            fireViewModelChanged();
        }
    }

    @Override
    public void setModelOffset(double modelOffsetX, double modelOffsetY, double viewScale) {
        double scale = maybeCropViewScale(viewScale);
        if (MathUtil.isDifferent(this.modelOffsetX, modelOffsetX)
            || MathUtil.isDifferent(this.modelOffsetY, modelOffsetY) || MathUtil.isDifferent(this.viewScale, scale)) {
            this.modelOffsetX = modelOffsetX;
            this.modelOffsetY = modelOffsetY;
            this.viewScale = scale;
            fireViewModelChanged();
        }
    }

    @Override
    public double getViewScale() {
        return viewScale;
    }

    @Override
    public void setViewScale(double viewScale) {
        double scale = maybeCropViewScale(viewScale);
        if (MathUtil.isDifferent(this.viewScale, scale)) {
            this.viewScale = scale;
            fireViewModelChanged();
        }
    }

    @Override
    public double getViewScaleMax() {
        return viewScaleMax;
    }

    @Override
    public void setViewScaleMax(double viewScaleMax) {
        this.viewScaleMax = viewScaleMax;
    }

    @Override
    public Rectangle2D getModelArea() {
        return new Rectangle2D.Double(modelArea.getX(), modelArea.getY(), modelArea.getWidth(), modelArea.getHeight());
    }

    @Override
    public double getViewScaleMin() {
        return viewScaleMin;
    }

    @Override
    public void setModelArea(Rectangle2D modelArea) {
        if (!this.modelArea.equals(modelArea)) {
            this.modelArea =
                new Rectangle2D.Double(modelArea.getX(), modelArea.getY(), modelArea.getWidth(), modelArea.getHeight());
            fireViewModelChanged();
        }
    }

    @Override
    public void setViewScaleMin(double viewScaleMin) {
        this.viewScaleMin = viewScaleMin;
    }

    @Override
    public ViewModelChangeListener[] getViewModelChangeListeners() {
        final ViewModelChangeListener[] viewModelChangeListeners =
            new ViewModelChangeListener[viewModelChangeListenerList.size()];
        return viewModelChangeListenerList.toArray(viewModelChangeListeners);
    }

    @Override
    public void addViewModelChangeListener(ViewModelChangeListener l) {
        if (l != null && !viewModelChangeListenerList.contains(l)) {
            viewModelChangeListenerList.add(l);
        }
    }

    @Override
    public void removeViewModelChangeListener(ViewModelChangeListener l) {
        if (l != null) {
            viewModelChangeListenerList.remove(l);
        }
    }

    protected void fireViewModelChanged() {
        if (enableViewModelChangeListeners) {
            for (int i = 0; i < viewModelChangeListenerList.size(); i++) {
                ViewModelChangeListener l = viewModelChangeListenerList.get(i);
                l.handleViewModelChanged(this);
            }
        }
    }

    public static double cropViewScale(double viewScale, final double viewScaleMin, final double viewScaleMax) {
        if (viewScaleMax > 1.0) {
            if (viewScale < viewScaleMin) {
                return viewScaleMin;
            } else if (viewScale > viewScaleMax) {
                return viewScaleMax;
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
