package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.util.List;

import javax.swing.Icon;

import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * @author Nicolas Roduit
 */
public class PointGraphic extends BasicGraphic {

    public PointGraphic(Point2D.Double point, float lineThickness, Color paintColor, boolean labelVisible,
        boolean filled, int pointSize) throws IllegalStateException {
        super(0, paintColor, lineThickness, labelVisible, filled);
        if (point == null) {
            point = new Point2D.Double();
        }
        this.handlePointList.add(point);
        Ellipse2D ellipse =
            new Ellipse2D.Double(point.getX() - pointSize / 2.0f, point.getY() - pointSize / 2.0f, pointSize, pointSize);
        setShape(ellipse, null);
        updateLabel(null, null);
    }

    @Override
    protected void buildShape() {
        updateLabel(null, null);
    }

    @Override
    public Icon getIcon() {
        return null;
    }

    @Override
    public String getUIName() {
        return "";
    }

    @Override
    public List<MeasureItem> computeMeasurements(ImageLayer layer, boolean releaseEvent) {
        return null;
    }

    @Override
    public List<Measurement> getMeasurementList() {
        return null;
    }

    @Override
    public boolean isOnGraphicLabel(MouseEventDouble mouseevent) {
        return false;
    }

    @Override
    public String getDescription() {
        return ""; //$NON-NLS-1$
    }

    @Override
    public Area getArea(AffineTransform transform) {
        return new Area();
    }

}
