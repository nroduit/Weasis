/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.mpr3dview.api;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import javax.vecmath.Quat4d;
import org.weasis.core.api.gui.model.ViewModel;
import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.ui.graphic.model.AbstractLayer;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.core.ui.graphic.model.DefaultViewModel;
import org.weasis.core.ui.graphic.model.GraphicList;

/**
 * Deals with the ViewModel and the LayerModel data.
 * 
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2013, 29 Nov.
 */
public class GraphicsModel {
    
    protected DVLayerModel layerModel;
    private ViewModel viewModel;
    protected final AffineTransform affineTransform = new AffineTransform();
    protected final AffineTransform inverseTransform = new AffineTransform();
    
    
    public GraphicsModel(final DVLayerModel lModel, final ViewModel vModel) {
        layerModel = lModel;
        viewModel = vModel;
    }

    public AbstractLayerModel getLayerModel() {
        return layerModel;
    }
    
    public ViewModel getViewModel() {
        return viewModel;
    }

    public AffineTransform getAffineTransform() {
        return affineTransform;
    }
    
    public AffineTransform getInverseTransform() {
        return inverseTransform;
    }

    public void dispose() {
        if (viewModel != null) {
            viewModel = null;
        }
        if (layerModel != null) {
            layerModel.dispose();
            layerModel = null;
        }
    }

    public Point2D getImageCoordinatesFromMouse(int x, int y) {
        double viewScale = getViewModel().getViewScale();
        Point2D p2 =
            new Point2D.Double(x + getViewModel().getModelOffsetX() * viewScale,
                y + getViewModel().getModelOffsetY() * viewScale);
        inverseTransform.transform(p2, p2);
        return p2;
    }
    
    public double modelToViewLength(double modelLength) {
        return modelLength * viewModel.getViewScale();
    }
    
    public double viewToModelLength(double viewLength) {
        return viewLength / viewModel.getViewScale();
    }
    
    public void updateAffineTransform(Integer rotationAngle, Boolean flip) {
        double viewScale = getViewModel().getViewScale();
        affineTransform.setToScale(viewScale, viewScale);
  
        if (rotationAngle != null && rotationAngle > 0) {
            if (flip != null && flip) {
                rotationAngle = 360 - rotationAngle;
            }
            Rectangle2D imageCanvas = getViewModel().getModelArea();
            affineTransform.rotate(Math.toRadians(rotationAngle), imageCanvas.getWidth() / 2.0,
                imageCanvas.getHeight() / 2.0);
        }
        if (flip != null && flip) {
            affineTransform.scale(-1.0, 1.0);
            affineTransform.translate(-getViewModel().getModelArea().getWidth(), 0.0);
        }

        try {
            inverseTransform.setTransform(affineTransform.createInverse());
        } catch (NoninvertibleTransformException e) {
            e.printStackTrace();
        }
    }

    public void zoom(Dimension viewport, double viewScale) {
        double modelOffsetXOld = viewModel.getModelOffsetX();
        double modelOffsetYOld = viewModel.getModelOffsetY();
        double viewScaleOld = viewModel.getViewScale();
        double centerX = modelOffsetXOld + 0.5 * (viewport.getWidth() - 1) / viewScaleOld;
        double centerY = modelOffsetYOld + 0.5 * (viewport.getHeight() - 1) / viewScaleOld;
        zoom(viewport, centerX, centerY, viewScale);
    }
    
    public void zoom(Dimension viewport, double centerX, double centerY, double viewScale) {
        final double modelOffsetX = centerX - 0.5 * (viewport.getWidth() - 1) / viewScale;
        final double modelOffsetY = centerY - 0.5 * (viewport.getHeight() - 1) / viewScale;
        viewModel.setModelOffset(modelOffsetX, modelOffsetY, viewScale);
    }
    
    public double getBestFitViewScale(Dimension viewport) {
        final double viewportWidth = viewport.getWidth() - 1;
        final double viewportHeight = viewport.getHeight() - 1;
        final Rectangle2D modelArea = viewModel.getModelArea();
        double min = Math.min(viewportWidth / modelArea.getWidth(),
                            viewportHeight / modelArea.getHeight());
       return cropViewScale(min);
    }

    private double cropViewScale(double viewScale) {
        return DefaultViewModel.cropViewScale(viewScale,
                viewModel.getViewScaleMin(), viewModel.getViewScaleMax());
    }

    
    public static String getRotationDesc(Quat4d rotation) {
        StringBuilder desc = new StringBuilder();
        desc.append(DecFormater.twoDecimal(rotation.w));
        desc.append('_').append(DecFormater.twoDecimal(rotation.x));
        desc.append('_').append(DecFormater.twoDecimal(rotation.y));
        desc.append('_').append(DecFormater.twoDecimal(rotation.z));
        return desc.toString();
    }

    public void removeGraphics() {
        AbstractLayer layer = getLayerModel().getLayer(AbstractLayer.MEASURE);
        if (layer != null) {
            layer.setGraphics(new GraphicList());
        }
    }

    /**
     * Updates all labels on viewer.
     * @param source The viewer object.
     */
    public void updateAllLabels(final Component source) {
        //TODO
    }
    
}
