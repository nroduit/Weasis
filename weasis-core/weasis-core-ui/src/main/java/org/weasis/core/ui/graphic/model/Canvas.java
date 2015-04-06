package org.weasis.core.ui.graphic.model;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;

import javax.swing.JComponent;

import org.weasis.core.api.gui.model.ViewModel;

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

    AbstractLayerModel getLayerModel();

    void setLayerModel(AbstractLayerModel layerModel);

    Object getActionValue(String action);

    HashMap<String, Object> getActionsInView();

    void zoom(double viewScale);

    void zoom(double centerX, double centerY, double viewScale);

    void zoom(Rectangle2D zoomRect);

    double getBestFitViewScale();

    double viewToModelX(double viewX);

    double viewToModelY(double viewY);

    double viewToModelLength(double viewLength);

    double modelToViewX(double modelX);

    double modelToViewY(double modelY);

    double modelToViewLength(double modelLength);

    Point2D getImageCoordinatesFromMouse(int x, int y);

    Point getMouseCoordinatesFromImage(double x, double y);

    void transformGraphics(Graphics2D g2d, boolean forward);

}