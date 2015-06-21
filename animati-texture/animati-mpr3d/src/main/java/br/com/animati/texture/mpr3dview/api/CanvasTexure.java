/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */
package br.com.animati.texture.mpr3dview.api;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.HashMap;

import javax.swing.JComponent;
import javax.vecmath.Point2i;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.model.ViewModel;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.core.ui.graphic.model.Canvas;
import org.weasis.core.ui.graphic.model.DefaultViewModel;
import org.weasis.core.ui.graphic.model.MainLayerModel;

import br.com.animati.texturedicom.ImageSeries;
import br.com.animati.texturedicom.TextureImageCanvas;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/**
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2015, 23 Apr.
 */
public class CanvasTexure extends TextureImageCanvas implements Canvas {
    
    /** Class logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(CanvasTexure.class);
    
    protected final GraphicsModel graphsLayer;
    protected final HashMap<String, Object> actionsInView = new HashMap<String, Object>();
    protected final DrawingsKeyListeners drawingsKeyListeners = new DrawingsKeyListeners();

    public CanvasTexure(ImageSeries parentImageSeries) {
        super(parentImageSeries);
        
        graphsLayer = new GraphicsModel(new MainLayerModel(this), new TextureViewModel());
    }
    
    @Override
    public JComponent getJComponent() {
        return this;
    }

    @Override
    public AffineTransform getAffineTransform() {
        return graphsLayer.getAffineTransform();
    }

    @Override
    public AffineTransform getInverseTransform() {
        return graphsLayer.getInverseTransform();
    }

    @Override
    public void disposeView() {
        graphsLayer.dispose();
        super.dispose();
    }

    @Override
    public ViewModel getViewModel() {
        return graphsLayer.getViewModel();
    }

    @Override
    public void setViewModel(ViewModel viewModel) {
        ViewModel viewModelOld = getViewModel();
        if (viewModelOld != getViewModel()) {
            graphsLayer.setViewModel(viewModel);
            firePropertyChange("viewModel", viewModelOld, getViewModel());
        }
    }

    @Override
    public AbstractLayerModel getLayerModel() {
        return graphsLayer.getLayerModel();
    }

    @Override
    public void setLayerModel(AbstractLayerModel layerModel) {
        AbstractLayerModel old = getLayerModel();
        if (old != getLayerModel()) {
            graphsLayer.setLayerModel(layerModel);
            firePropertyChange("layerModelModel", old, getLayerModel());
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
    public HashMap<String, Object> getActionsInView() {
        return actionsInView;
    }

    @Override
    public void zoom(double viewScale) {
        setZoom(viewScale, true); //true for repait texture.
    }

    @Override
    public double getBestFitViewScale() {
        return graphsLayer.getBestFitViewScale(new Dimension(getWidth(), getHeight()));
    }

    @Override
    public double viewToModelX(double viewX) {
        return graphsLayer.modelToViewX(viewX);
    }

    @Override
    public double viewToModelY(double viewY) {
        return graphsLayer.modelToViewY(viewY);
    }

    @Override
    public double viewToModelLength(double viewLength) {
        return graphsLayer.viewToModelLength(viewLength);
    }

    @Override
    public double modelToViewX(double modelX) {
        return graphsLayer.modelToViewX(modelX);
    }

    @Override
    public double modelToViewY(double modelY) {
        return graphsLayer.modelToViewY(modelY);
    }

    @Override
    public double modelToViewLength(double modelLength) {
        return graphsLayer.modelToViewLength(modelLength);
    }

    @Override
    public Point2D getImageCoordinatesFromMouse(int x, int y) {
        //not realy ImageCoordenates... see: Wikipage 'Coordinate Systems'
        return graphsLayer.getImageCoordinatesFromMouse(x, y);
    }
    
    public Point3d getOriginalSystemCoordinatesFromMouse(int x, int y) {
        Point2i position = new Point2i(x, y);
        Vector3d coordinatesTexture = getTextureCoordinatesForPosition(position);
        Vector3d imageSize = getParentImageSeries().getImageSize();
        return textureToOriginalSystemCoordinates(coordinatesTexture, imageSize);
    }

    @Override
    public Point getMouseCoordinatesFromImage(double x, double y) {
        //not realy ImageCoordenates... see: Wikipage 'Coordinate Systems'
        return graphsLayer.getMouseCoordinatesFromImage(x, y);
    }
    
    /**
     * Converts a position on texture`s coordinates system (0 to 1) to image`s coordinates system.
     * X e Y: 0 to n-1; (number of pixels) Z: 1 to n. (n = number of slices)
     * 
     * @param coordinatesTexture
     *            position on texture`s coord. system.
     * @param imageSize
     *            Size or images and stack (the "Original System).
     * @return Position on image`s coordinates System.
     */
    public Point3d textureToOriginalSystemCoordinates(final Vector3d coordinatesTexture, final Vector3d imageSize) {
        Point3d p3 =
            new Point3d((Math.round(coordinatesTexture.x * imageSize.x)), (Math.round(coordinatesTexture.y
                * imageSize.y)), (Math.round((coordinatesTexture.z * imageSize.z) + 1)));
        return p3;
    }
    
    public boolean hasContent() {
        return (getParentImageSeries() != null);
    }
    
    /**
     * Get ImageRectangle without rotation.
     * 
     * @return
     */
    public Rectangle getUnrotatedImageRect() {
        double currentRO = getRotationOffset();
        setRotationOffset(0);
        Rectangle imageRect = getImageRect(true);
        setRotationOffset(currentRO);

        return imageRect;
    }
    
    /**
     * @return Unrotated and "unzoomed" image rectangle.
     */
    public Rectangle getUntransformedImageRect() {
        double currentRO = getRotationOffset();
        double zoom = getActualDisplayZoom();
        
        setRotationOffset(0);
        setZoom(1);
        
        Rectangle imageRect = getImageRect(true);
        
        setRotationOffset(currentRO);
        setZoom(zoom);

        return imageRect;
    }
    
    /**
     * Uses DefaultViewModel to reuse the Listener system, but all the information came from the TextureImageCanvas
     * state.
     */
    private class TextureViewModel extends DefaultViewModel {

        @Override
        public double getModelOffsetX() {
            if (hasContent()) {
                return -(getUnrotatedImageRect().getX() / getActualDisplayZoom());
            }
            return 0;
        }

        @Override
        public double getModelOffsetY() {
            if (hasContent()) {
                return -(getUnrotatedImageRect().getY() / getActualDisplayZoom());
            }
            return 0;
        }

        @Override
        public double getViewScale() {
            if (hasContent()) {
                double displayZoom = CanvasTexure.this.getActualDisplayZoom();
                if (displayZoom <= 0) { // avoid error trying to generate inverseAffine
                    displayZoom = 1;
                }
                return displayZoom;
            }
            return 1;
        }
    }
    
    private class DrawingsKeyListeners implements KeyListener {

        @Override
        public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                getLayerModel().deleteSelectedGraphics(false);
            } else if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_D) {
                getLayerModel().setSelectedGraphics(null);
            } else if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_A) {
                getLayerModel().setSelectedGraphics(getLayerModel().getAllGraphics());
            }
        }

        @Override public void keyReleased(KeyEvent e) { }
        @Override public void keyTyped(KeyEvent e) { }
    }
    
}
