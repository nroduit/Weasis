/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.mpr3dview.api.graghics;

import br.com.animati.texture.mpr3dview.EventPublisher;
import br.com.animati.texture.mpr3dview.api.ViewCore;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.GraphicLabel;
import org.weasis.core.ui.graphic.LineGraphic;
import org.weasis.core.ui.graphic.MeasureItem;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2013, 29 Nov.
 */
public class DVLineGraphic extends LineGraphic {
    
    public DVLineGraphic(float lineThickness, Color paintColor, boolean labelVisible) {
        super(lineThickness, paintColor, labelVisible);
    }
    
    @Override
    protected void buildShape(MouseEventDouble mouseEvent) {

        updateTool();
        Shape newShape = null;

        if (lineABvalid) {
            newShape = new Line2D.Double(ptA, ptB);
        }

        setShape(newShape, mouseEvent);
        updateLabelDV(mouseEvent, null);
    }
    
    /** Needed for GraphicsMode.updateAllLabels. */
    @Override
    public void updateLabel(Object source, DefaultView2d view2d, Point2D pos) {
        if (source instanceof MouseEvent) {
            updateLabelDV((MouseEvent) source, null);
        }
    }
    
    public void updateLabelDV(MouseEvent source, Point2D pos) {
        
        List<MeasureItem> measList = null;
        String[] labels = null;

        // If isMultiSelection is false, it should return all enable 
        // computed measurements when
        // quickComputing is enable or when releasedEvent is true
        if (labelVisible || !isMultiSelection(source)) {
            measList = computeMeasurements(getAdapter(source));  
        }

        if (labelVisible && measList != null && measList.size() > 0) {
            labels = DVGraphicLabel.buildLabelsList(measList);
        }

        setLabel(labels, (Graphics2D) getGraphics((MouseEvent) source), pos);

        EventPublisher.getInstance().publish(new PropertyChangeEvent(
                    this, "graphics.data", null, measList));
        
    }
    
    public void setLabel(String[] labels, Graphics2D g2d, Point2D pos) {
        GraphicLabel oldLabel = (graphicLabel != null)
                ? ((DVGraphicLabel) graphicLabel).clone() : null;

        if (labels == null || labels.length == 0) {
            graphicLabel = null;
            fireLabelChanged(oldLabel);
        } else {
            if (pos == null) {
                pos = getPositionForLabel(shape);
            }
            if (graphicLabel == null) {
                graphicLabel = new DVGraphicLabel();
            }
            ((DVGraphicLabel) graphicLabel).setLabel(g2d, pos.getX(), pos.getY(), labels);
            fireLabelChanged(oldLabel);
        }
    }
    
    public static Point2D getPositionForLabel(Shape shape) {
        if (shape != null) {
            Rectangle2D rect = shape.getBounds2D();

            double xPos = rect.getX() + rect.getWidth() + 3;
            double yPos = rect.getY() + rect.getHeight() * 0.5;

            return new Point2D.Double(xPos, yPos);
        }
        return null;
    }
    
    public List<MeasureItem> computeMeasurements(MeasurementsAdapter adapter) {
        if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>();

                if (FIRST_POINT_X.isComputed()) {
                    measVal.add(new MeasureItem(FIRST_POINT_X, adapter.getXCalibratedValue(ptA.getX()), adapter
                        .getUnit()));
                }
                if (FIRST_POINT_Y.isComputed()) {
                    measVal.add(new MeasureItem(FIRST_POINT_Y, adapter.getXCalibratedValue(ptA.getY()), adapter
                        .getUnit()));
                }
                if (LAST_POINT_X.isComputed()) {
                    measVal.add(new MeasureItem(LAST_POINT_X, adapter.getXCalibratedValue(ptB.getX()), adapter
                        .getUnit()));
                }
                if (LAST_POINT_Y.isComputed()) {
                    measVal.add(new MeasureItem(LAST_POINT_Y, adapter.getXCalibratedValue(ptB.getY()), adapter
                        .getUnit()));
                }
                if (LINE_LENGTH.isComputed()) {
                    measVal.add(new MeasureItem(LINE_LENGTH, ptA.distance(ptB) * adapter.getCalibRatio(), adapter
                        .getUnit()));
                }
                if (ORIENTATION.isComputed()) {
                    measVal.add(new MeasureItem(ORIENTATION, MathUtil.getOrientation(ptA, ptB), Messages
                        .getString("measure.deg")));
                }
                if (AZIMUTH.isComputed()) {
                    measVal.add(new MeasureItem(AZIMUTH, MathUtil.getAzimuth(ptA, ptB), Messages
                        .getString("measure.deg")));
                }
                return measVal;
            }
        return null;
    }
    
    @Override
    public List<MeasureItem> computeMeasurements(ImageLayer layer, boolean releaseEvent,
            Unit displayUnit) {
        if (layer != null && layer.getSourceImage() != null && isShapeValid()) {
            MeasurementsAdapter adapter = layer.getSourceImage().getMeasurementAdapter(displayUnit);

            return computeMeasurements(adapter);
        }
        return null;
    }
    
    public static AbstractLayerModel getLayerModel(MouseEvent mouseevent) {
        if (mouseevent != null && mouseevent.getSource() instanceof ViewCore) {
            return ((ViewCore) mouseevent.getSource()).getLayerModel();
        }
        return null;
    }
    
    public static boolean isMultiSelection(MouseEvent source) {
        boolean isMultiSelection = false; // default is single selection
        AbstractLayerModel model = getLayerModel(source);
        if (model != null) {
            ArrayList<Graphic> selectedGraphics = model.getSelectedGraphics();
            isMultiSelection = selectedGraphics.size() > 1;
        }
        return isMultiSelection;
    }
    
    public static Graphics getGraphics(MouseEvent mouseevent) {
        if (mouseevent != null && mouseevent.getSource() instanceof Component) {
            return ((Component) mouseevent.getSource()).getGraphics();
        }
        return null;
    }

    public static MeasurementsAdapter getAdapter(MouseEvent mouseEvent) {
        if (mouseEvent != null) {
            if (mouseEvent.getSource() instanceof ViewCore) {
                return ((ViewCore) mouseEvent.getSource()).getMeasurementsAdapter();
            }
        }
        return null;
    }
    
    /**
     * Overriden so isOnGraphicLabel works with all ViewCore
     * @param mouseevent
     * @return 
     */
    @Override
    protected AffineTransform getAffineTransform(MouseEvent mouseevent) {
        if (mouseevent != null && mouseevent.getSource() instanceof ViewCore) {
            return ((ViewCore) mouseevent.getSource()).getAffineTransform();
        }
        return null;
    }

}
