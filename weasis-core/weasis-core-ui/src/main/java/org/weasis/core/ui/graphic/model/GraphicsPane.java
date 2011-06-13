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

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;

import javax.swing.JComponent;

import org.weasis.core.api.gui.model.ViewModel;
import org.weasis.core.api.gui.model.ViewModelChangeListener;

/**
 * The Class GraphicsPane.
 * 
 * @author Nicolas Roduit
 */
public class GraphicsPane extends JComponent {

    protected AbstractLayerModel layerModel;
    private final LayerModelHandler layerModelHandler;
    private ViewModel viewModel;
    private final ViewModelHandler viewModelHandler;
    protected final DrawingsKeyListeners drawingsKeyListeners = new DrawingsKeyListeners();
    protected final HashMap<String, Object> actionsInView = new HashMap<String, Object>();
    protected final AffineTransform affineTransform = new AffineTransform();
    protected final AffineTransform inverseTransform = new AffineTransform();

    public GraphicsPane() {
        this(null, null);
    }

    public GraphicsPane(AbstractLayerModel layerModel, ViewModel viewModel) {
        setOpaque(false);
        this.viewModel = viewModel == null ? new DefaultViewModel() : viewModel;
        viewModelHandler = new ViewModelHandler();
        this.viewModel.addViewModelChangeListener(this.viewModelHandler);

        this.layerModel = layerModel == null ? new MainLayerModel(this) : layerModel;
        layerModelHandler = new LayerModelHandler();
        this.layerModel.addLayerModelChangeListener(layerModelHandler);
    }

    public AffineTransform getAffineTransform() {
        return affineTransform;
    }

    public AffineTransform getInverseTransform() {
        return inverseTransform;
    }

    public void dispose() {
        if (viewModel != null) {
            viewModel.removeViewModelChangeListener(viewModelHandler);
            viewModel = null;
        }
        if (layerModel != null) {
            layerModel.removeLayerModelChangeListener(layerModelHandler);
            layerModel.dispose();
            layerModel = null;
        }
    }

    /**
     * Gets the view model.
     * 
     * @return the view model, never null
     */
    public ViewModel getViewModel() {
        return viewModel;
    }

    /**
     * Sets the view model.
     * 
     * @param viewModel
     *            the view model, never null
     */
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
            firePropertyChange("viewModel", viewModelOld, this.viewModel); //$NON-NLS-1$
        }
    }

    public AbstractLayerModel getLayerModel() {
        return layerModel;
    }

    public void setLayerModel(AbstractLayerModel layerModel) {
        LayerModel layerModelOld = this.layerModel;
        if (layerModelOld != layerModel) {
            if (layerModelOld != null) {
                layerModelOld.removeLayerModelChangeListener(layerModelHandler);
            }
            this.layerModel = layerModel;
            if (this.layerModel != null) {
                this.layerModel.addLayerModelChangeListener(layerModelHandler);
            }
            firePropertyChange("layerModel", layerModelOld, layerModel); //$NON-NLS-1$
        }
    }

    public Object getActionValue(String action) {
        if (action == null)
            return null;
        return actionsInView.get(action);
    }

    // /////////////////////////////////////////////////////////////////////////////////////
    // Utilities
    // public void setModelAreaFromLayerModel() {
    // final Rectangle2D visibleBoundingBox = getLayerModel().getVisibleBoundingBox(null);
    // getViewModel().setModelArea(visibleBoundingBox);
    // }

    public void zoom(double viewScale) {
        double modelOffsetXOld = viewModel.getModelOffsetX();
        double modelOffsetYOld = viewModel.getModelOffsetY();
        double viewScaleOld = viewModel.getViewScale();
        double viewportWidth = getWidth() - 1;
        double viewportHeight = getHeight() - 1;
        double centerX = modelOffsetXOld + 0.5 * viewportWidth / viewScaleOld;
        double centerY = modelOffsetYOld + 0.5 * viewportHeight / viewScaleOld;
        zoom(centerX, centerY, viewScale);
    }

    public void zoom(double centerX, double centerY, double viewScale) {
        viewScale = cropViewScale(viewScale);
        final double viewportWidth = getWidth() - 1;
        final double viewportHeight = getHeight() - 1;
        final double modelOffsetX = centerX - 0.5 * viewportWidth / viewScale;
        final double modelOffsetY = centerY - 0.5 * viewportHeight / viewScale;
        getViewModel().setModelOffset(modelOffsetX, modelOffsetY, viewScale);
    }

    public void zoom(Rectangle2D zoomRect) {
        final double viewportWidth = getWidth() - 1;
        final double viewportHeight = getHeight() - 1;
        zoom(zoomRect.getCenterX(), zoomRect.getCenterY(),
            Math.min(viewportWidth / zoomRect.getWidth(), viewportHeight / zoomRect.getHeight()));
    }

    public double getBestFitViewScale() {
        final double viewportWidth = getWidth() - 1;
        final double viewportHeight = getHeight() - 1;
        final Rectangle2D modelArea = viewModel.getModelArea();
        return cropViewScale(Math.min(viewportWidth / modelArea.getWidth(), viewportHeight / modelArea.getHeight()));
    }

    public double viewToModelX(double viewX) {
        return viewModel.getModelOffsetX() + viewToModelLength(viewX);
    }

    public double viewToModelY(double viewY) {
        return viewModel.getModelOffsetY() + viewToModelLength(viewY);
    }

    public double viewToModelLength(double viewLength) {
        return viewLength / viewModel.getViewScale();
    }

    public double modelToViewX(double modelX) {
        return modelToViewLength(modelX - viewModel.getModelOffsetX());
    }

    public double modelToViewY(double modelY) {
        return modelToViewLength(modelY - viewModel.getModelOffsetY());
    }

    public double modelToViewLength(double modelLength) {
        return modelLength * viewModel.getViewScale();
    }

    public Point2D getImageCoordinatesFromMouse(int x, int y) {
        double viewScale = getViewModel().getViewScale();
        Point2D p2 =
            new Point2D.Double(x + getViewModel().getModelOffsetX() * viewScale, y + getViewModel().getModelOffsetY()
                * viewScale);
        inverseTransform.transform(p2, p2);
        return p2;
    }

    public Point getMouseCoordinatesFromImage(double x, double y) {
        Point2D p2 = new Point2D.Double(x, y);
        affineTransform.transform(p2, p2);
        double viewScale = getViewModel().getViewScale();
        return new Point((int) Math.floor(p2.getX() - getViewModel().getModelOffsetX() * viewScale + 0.5),
            (int) Math.floor(p2.getY() - getViewModel().getModelOffsetY() * viewScale + 0.5));
    }

    // JComponent Overrides
    /**
     * If you override this in a subclass you should not make permanent changes to the passed in <code>Graphics</code>.
     * For example, you should not alter the clip <code>Rectangle</code> or modify the transform. If you need to do
     * these operations you may find it easier to create a new <code>Graphics</code> from the passed in
     * <code>Graphics</code> and manipulate it. Further, if you do not invoker super's implementation you must honor the
     * opaque property, that is if this component is opaque, you must completely fill in the background in a non-opaque
     * color. If you do not honor the opaque property you will likely see visual artifacts.
     * 
     * @param g
     *            the <code>Graphics</code> object to protect
     * 
     * @see #paint
     * @see javax.swing.plaf.ComponentUI
     */
    @Override
    protected void paintComponent(Graphics g) {
        // honor the opaque property
        if (isOpaque()) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        if (g instanceof Graphics2D) {
            drawLayers((Graphics2D) g, null, null);
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////
    // Drawing
    public void drawLayers(Graphics2D g2d, AffineTransform transform, AffineTransform inverseTransform) {
        // create a new Graphics because we shall not alter the Graphics passed in
        final Graphics2D g2dClone = (Graphics2D) g2d.create();
        transformGraphics(g2dClone, true);
        getLayerModel().draw(g2dClone, transform, inverseTransform);
        g2dClone.dispose();
    }

    public void transformGraphics(final Graphics2D g2d, boolean forward) {
        if (forward) {
            // forward transform
            g2d.scale(viewModel.getViewScale(), viewModel.getViewScale());
            g2d.translate(-viewModel.getModelOffsetX(), -viewModel.getModelOffsetY());
        } else {
            // inverse transform
            g2d.translate(viewModel.getModelOffsetX(), viewModel.getModelOffsetY());
            g2d.scale(1.0 / viewModel.getViewScale(), 1.0 / viewModel.getViewScale());
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////
    // Helpers
    private double cropViewScale(double viewScale) {
        return DefaultViewModel.cropViewScale(viewScale, viewModel.getViewScaleMin(), viewModel.getViewScaleMax());
    }

    // /////////////////////////////////////////////////////////////////////////////////////
    // Inner Classes
    /**
     * The Class LayerModelHandler.
     * 
     * @author Nicolas Roduit
     */
    private class LayerModelHandler extends LayerModelChangeAdapter {

        @Override
        public void handleLayerModelChanged(LayerModel layerModel) {
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

    /**
     * The Class DrawingsKeyListeners.
     * 
     * @author Nicolas Roduit
     */
    private class DrawingsKeyListeners implements KeyListener {

        @Override
        public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                layerModel.deleteSelectedGraphics();
            }
            // FIXME arrows is already used with pan!
            // else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
            // layerModel.moveSelectedGraphics(-1, 0);
            // }
            // else if (e.getKeyCode() == KeyEvent.VK_UP) {
            // layerModel.moveSelectedGraphics(0, -1);
            // }
            // else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
            // layerModel.moveSelectedGraphics(1, 0);
            // }
            // else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
            // layerModel.moveSelectedGraphics(0, 1);
            // }
            else if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_D) {
                layerModel.setSelectedGraphics(null);

            } else if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_A) {
                layerModel.setSelectedGraphics(layerModel.getdAllGraphics());
            }
        }

        @Override
        public void keyReleased(KeyEvent e) {
        }

        @Override
        public void keyTyped(KeyEvent e) {
        }
    }
}
