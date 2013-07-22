package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.Paint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.util.List;

import javax.swing.Icon;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * @author Nicolas Roduit
 */

// TODO should a draggable graphic

@Root(name = "point")
public class PointGraphic extends BasicGraphic {

    @Attribute(name = "pt_size")
    private int pointSize;

    public PointGraphic(Point2D.Double point, float lineThickness, Color paintColor, boolean labelVisible,
        boolean filled, int pointSize) throws IllegalStateException {
        super(0, paintColor, lineThickness, labelVisible, filled);
        if (point == null) {
            point = new Point2D.Double();
        }
        this.handlePointList.add(point);
        this.pointSize = pointSize;
        buildShape();
    }

    protected PointGraphic(
        @ElementList(name = "pts", entry = "pt", type = Point2D.Double.class) List<Point2D.Double> handlePointList,
        @Attribute(name = "handle_pts_nb") int handlePointTotalNumber,
        @Element(name = "paint", required = false) Paint paintColor,
        @Attribute(name = "thickness") float lineThickness, @Attribute(name = "label_visible") boolean labelVisible,
        @Attribute(name = "pt_size") int pointSize) throws InvalidShapeException {
        super(handlePointList, handlePointTotalNumber, paintColor, lineThickness, labelVisible, false);
        if (handlePointTotalNumber != 1) {
            throw new InvalidShapeException("Not a valid PointGraphic!");
        }
        buildShape();
    }

    @Override
    protected void buildShape() {
        if (this.handlePointList.size() == 1) {
            Point2D.Double point = this.handlePointList.get(0);
            Ellipse2D ellipse =
                new Ellipse2D.Double(point.getX() - pointSize / 2.0f, point.getY() - pointSize / 2.0f, pointSize,
                    pointSize);
            setShape(ellipse, null);
            updateLabel(null, null);
        }
    }

    public int getPointSize() {
        return pointSize;
    }

    public void setPointSize(int pointSize) {
        this.pointSize = pointSize;
    }

    @Override
    public Icon getIcon() {
        return null;
    }

    @Override
    public String getUIName() {
        return "Point";
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
