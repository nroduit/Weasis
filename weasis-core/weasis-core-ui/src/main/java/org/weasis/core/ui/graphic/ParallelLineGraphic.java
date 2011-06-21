package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.util.MouseEventDouble;

public class ParallelLineGraphic extends AbstractDragGraphic {

    public static final Icon ICON = new ImageIcon(
        ParallelLineGraphic.class.getResource("/icon/22x22/draw-parallel.png")); //$NON-NLS-1$

    public final static Measurement Distance = new Measurement("Distance", true, true, true);
    public final static Measurement Orientation = new Measurement("Orientation", true, true, false);
    public final static Measurement Azimuth = new Measurement("Azimuth", true, true, false);

    // ///////////////////////////////////////////////////////////////////////////////////////////////////
    protected Point2D A, B, C, D; // Let AB & CD two parallel line segments
    protected Point2D E, F; // Let E,F middle points of AB & CD

    protected boolean ABvalid, CDvalid; // estimate if line segments are valid or not

    // ///////////////////////////////////////////////////////////////////////////////////////////////////
    public ParallelLineGraphic(float lineThickness, Color paintColor, boolean labelVisible) {
        super(6, paintColor, lineThickness, labelVisible);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return "Parallel";
    }

    @Override
    protected int moveAndResizeOnDrawing(int handlePointIndex, double deltaX, double deltaY, MouseEventDouble mouseEvent) {

        if (handlePointIndex == -1) { // move shape
            for (Point2D point : handlePointList) {
                if (point != null)
                    point.setLocation(point.getX() + deltaX, point.getY() + deltaY);
            }
        } else { // move dragging point

            if (!isGraphicComplete()) {
                Point2D dragPoint = handlePointList.get(handlePointIndex);
                if (dragPoint != null)
                    dragPoint.setLocation(mouseEvent.getImageCoordinates());

                updateTool();

                if (ABvalid && CDvalid) {
                    if (handlePointList.size() < 5)
                        handlePointList.add(null); // increment list index reference for point E to complete graphic
                    if (handlePointList.size() < 6)
                        handlePointList.add(null);// increment list index reference for point F to complete graphic
                }
            }

            updateTool();

            if (ABvalid && CDvalid) {
                Point2D dragPoint = handlePointList.get(handlePointIndex);

                if (dragPoint != null) {
                    if (handlePointIndex == 0 || handlePointIndex == 1) { // drag point is A or B

                        // need to compute start angle with old position before setting to the new one
                        double theta = GeomUtil.getAngleRad(A, B);
                        dragPoint.setLocation(mouseEvent.getImageCoordinates());
                        theta -= GeomUtil.getAngleRad(A, B);

                        Point2D anchor = (handlePointIndex == 0) ? B : A; // anchor is opposite point to A or B
                        AffineTransform transform =
                            AffineTransform.getRotateInstance(theta, anchor.getX(), anchor.getY());

                        transform.transform(C, C);
                        transform.transform(D, D);

                    } else {

                        dragPoint.setLocation(mouseEvent.getImageCoordinates());

                        if (handlePointIndex == 2) { // drag point is C
                            Point2D J1 = GeomUtil.getPerpendicularPointToLine(A, B, D);
                            Point2D J2 = GeomUtil.getPerpendicularPointFromLine(A, B, J1, 10);

                            D.setLocation(GeomUtil.getPerpendicularPointToLine(J1, J2, C));

                        } else if (handlePointIndex == 3) { // drag point is D
                            Point2D I1 = GeomUtil.getPerpendicularPointToLine(A, B, C);
                            Point2D I2 = GeomUtil.getPerpendicularPointFromLine(A, B, I1, 10);

                            C.setLocation(GeomUtil.getPerpendicularPointToLine(I1, I2, D));

                        } else if (handlePointIndex == 4) {// drag point is E middle of AB
                            Point2D I1 = GeomUtil.getPerpendicularPointToLine(C, D, A);
                            Point2D I2 = GeomUtil.getPerpendicularPointFromLine(C, D, I1, 10);

                            A.setLocation(GeomUtil.getPerpendicularPointToLine(I1, I2, E));

                            Point2D J1 = GeomUtil.getPerpendicularPointToLine(C, D, B);
                            Point2D J2 = GeomUtil.getPerpendicularPointFromLine(C, D, J1, 10);

                            B.setLocation(GeomUtil.getPerpendicularPointToLine(J1, J2, E));

                        } else if (handlePointIndex == 5) {// drag point is F middle of CD
                            Point2D I1 = GeomUtil.getPerpendicularPointToLine(A, B, C);
                            Point2D I2 = GeomUtil.getPerpendicularPointFromLine(A, B, I1, 10);

                            C.setLocation(GeomUtil.getPerpendicularPointToLine(I1, I2, F));

                            Point2D J1 = GeomUtil.getPerpendicularPointToLine(A, B, D);
                            Point2D J2 = GeomUtil.getPerpendicularPointFromLine(A, B, J1, 10);

                            D.setLocation(GeomUtil.getPerpendicularPointToLine(J1, J2, F));
                        }
                    }
                    updateTool();
                }
            }

            if (handlePointList.size() >= 5) {// reference to E point index exist in handlePointList
                E = ABvalid ? GeomUtil.getMidPoint(A, B) : null;
                handlePointList.set(4, E);
            }

            if (handlePointList.size() >= 6) { // reference to F point index exist in handlePointList
                F = CDvalid ? GeomUtil.getMidPoint(C, D) : null;
                handlePointList.set(5, F);
            }
        }

        return handlePointIndex;
    }

    @Override
    protected void updateShapeOnDrawing(MouseEventDouble mouseEvent) {

        updateTool();

        Shape newShape = null;
        Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, 2);

        if (ABvalid)
            path.append(new Line2D.Double(A, B), false);

        if (CDvalid)
            path.append(new Line2D.Double(C, D), false);

        if (path.getCurrentPoint() != null)
            newShape = path;

        setShape(newShape, mouseEvent);
        updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
    }

    @Override
    public List<MeasureItem> getMeasurements(ImageElement imageElement, boolean releaseEvent, boolean drawOnLabel) {

        if (imageElement != null && isShapeValid()) {
            MeasurementsAdapter adapter = imageElement.getMeasurementAdapter();

            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>(3);

                if (Distance.isComputed() && (!drawOnLabel || Distance.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || Distance.isQuickComputing())
                        val = C.distance(GeomUtil.getPerpendicularPointToLine(A, B, C)) * adapter.getCalibRatio();
                    measVal.add(new MeasureItem(Distance, val, adapter.getUnit()));
                }
                if (Orientation.isComputed() && (!drawOnLabel || Orientation.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || Orientation.isQuickComputing())
                        val = MathUtil.getOrientation(A, B);
                    measVal.add(new MeasureItem(Orientation, val, "deg"));
                }
                if (Azimuth.isComputed() && (!drawOnLabel || Azimuth.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || Azimuth.isQuickComputing())
                        val = MathUtil.getAzimuth(A, B);
                    measVal.add(new MeasureItem(Azimuth, val, "deg"));
                }
                return measVal;
            }
        }
        return null;
    }

    @Override
    protected boolean isShapeValid() {
        updateTool();
        return (ABvalid && CDvalid);
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void updateTool() {
        A = handlePointList.size() >= 1 ? handlePointList.get(0) : null;
        B = handlePointList.size() >= 2 ? handlePointList.get(1) : null;
        C = handlePointList.size() >= 3 ? handlePointList.get(2) : null;
        D = handlePointList.size() >= 4 ? handlePointList.get(3) : null;
        E = handlePointList.size() >= 5 ? handlePointList.get(4) : null;
        F = handlePointList.size() >= 6 ? handlePointList.get(5) : null;

        ABvalid = A != null && B != null && !B.equals(A);
        CDvalid = C != null && D != null && !C.equals(D);
    }
}
