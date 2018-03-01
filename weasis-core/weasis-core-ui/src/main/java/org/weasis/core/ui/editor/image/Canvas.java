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
package org.weasis.core.ui.editor.image;

import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
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
     * @param viewModel
     *            the view model, never null
     */
    void setViewModel(ViewModel viewModel);

    Object getActionValue(String action);

    Map<String, Object> getActionsInView();

    void zoom(Double viewScale);

    double getBestFitViewScale();

    double viewToModelX(Double viewX);

    double viewToModelY(Double viewY);

    double viewToModelLength(Double viewLength);

    double modelToViewX(Double modelX);

    double modelToViewY(Double modelY);

    double modelToViewLength(Double modelLength);

    Point2D getImageCoordinatesFromMouse(Integer x, Integer y);

    Point getMouseCoordinatesFromImage(Double x, Double y);

    void setGraphicManager(GraphicModel graphicManager);

    GraphicModel getGraphicManager();

    PropertyChangeListener getGraphicsChangeHandler();

}