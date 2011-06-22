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

import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.util.MouseEventDouble;

public class PerpendicularLineGraphic extends AbstractDragGraphic {

    public static final Icon ICON = new ImageIcon(
        PerpendicularLineGraphic.class.getResource("/icon/22x22/draw-perpendicular.png")); //$NON-NLS-1$

    public final static Measurement LineLength = new Measurement("Line length", true, true, true);
    public final static Measurement Orientation = new Measurement("Orientation", true, true, false);
    public final static Measurement Azimuth = new Measurement("Azimuth", true, true, false);

    // ///////////////////////////////////////////////////////////////////////////////////////////////////
    // Let AB & CD two perpendicular line segments with D being the projected point C on AB
    protected Point2D A, B, C, D;

    protected boolean ABvalid, CDvalid; // estimate if line segments are valid or not

    // ///////////////////////////////////////////////////////////////////////////////////////////////////

    public PerpendicularLineGraphic(float lineThickness, Color paintColor, boolean labelVisible) {
        super(4, paintColor, lineThickness, labelVisible);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("MeasureToolBar.perpendicular"); //$NON-NLS-1$
    }

    @Override
    protected int moveAndResizeOnDrawing(int handlePointIndex, double deltaX, double deltaY, MouseEventDouble mouseEvent) {

        if (handlePointIndex == -1) { // move shape
            for (Point2D point : handlePointList) {
                if (point != null) {
                    point.setLocation(point.getX() + deltaX, point.getY() + deltaY);
                }
            }
        } else { // move dragging point

            if (!isGraphicComplete()) {
                Point2D dragPoint = handlePointList.get(handlePointIndex);
                if (dragPoint != null) {
                    dragPoint.setLocation(mouseEvent.getImageCoordinates());
                }

                updateTool();

                if (ABvalid && C != null) {
                    D = GeomUtil.getPerpendicularPointToLine(A, B, C);
                    handlePointList.add(D); // increment list index reference for point D to complete graphics
                }
            }

            updateTool();

            if (ABvalid && CDvalid) {

                if (handlePointIndex == 0 || handlePointIndex == 1) { // drag point is A or B
                    Point2D dragPoint = handlePointList.get(handlePointIndex);

                    // need to compute start angle with old position before setting to the new one
                    double theta = GeomUtil.getAngleRad(A, B);
                    dragPoint.setLocation(mouseEvent.getImageCoordinates());
                    theta -= GeomUtil.getAngleRad(A, B);

                    Point2D anchor = (handlePointIndex == 0) ? B : A; // anchor is opposite point to A or B
                    AffineTransform transform = AffineTransform.getRotateInstance(theta, anchor.getX(), anchor.getY());

                    transform.transform(C, C);
                    transform.transform(D, D);

                } else if (handlePointIndex == 2) { // drag point is C
                    C.setLocation(mouseEvent.getImageCoordinates());
                    D.setLocation(GeomUtil.getPerpendicularPointToLine(A, B, C));

                } else if (handlePointIndex == 3) { // drag point is D
                    double x = D.getX(), y = D.getY();
                    D.setLocation(GeomUtil.getPerpendicularPointToLine(A, B, mouseEvent.getImageCoordinates()));

                    AffineTransform transform = AffineTransform.getTranslateInstance(D.getX() - x, D.getY() - y);
                    transform.transform(C, C);
                }
            } else {
                Point2D dragPoint = handlePointList.get(handlePointIndex);
                if (dragPoint != null) {
                    dragPoint.setLocation(mouseEvent.getImageCoordinates());
                }
            }
        }

        return handlePointIndex;
    }

    @Override
    protected void updateShapeOnDrawing(MouseEventDouble mouseEvent) {

        updateTool();

        Shape newShape = null;
        Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, 2);

        if (ABvalid) {
            path.append(new Line2D.Double(A, B), false);
        }

        if (CDvalid) {
            path.append(new Line2D.Double(C, D), false);
        }

        if (ABvalid && CDvalid) {

            AdvancedShape aShape = (AdvancedShape) (newShape = new AdvancedShape(3));
            aShape.addShape(path);

            if (!D.equals(A) && !D.equals(B)) {
                // Check D is outside of AB segment
                if (Math.signum(GeomUtil.getAngleDeg(D, A)) == Math.signum(GeomUtil.getAngleDeg(D, B))) {
                    Point2D E = D.distance(A) < D.distance(B) ? A : B;
                    aShape.addShape(new Line2D.Double(D, E), getDashStroke(1.0f), true);
                }
            }

            double cornerLength = 10;
            double dMin = Math.min(D.distance(C), Math.max(D.distance(A), D.distance(B))) * 2 / 3;
            double scalingMin = cornerLength / dMin;

            Point2D F = GeomUtil.getMidPoint(A, B);
            Shape cornerShape = GeomUtil.getCornerShape(F, D, C, cornerLength);
            if (cornerShape != null) {
                aShape.addInvShape(cornerShape, D, scalingMin, getStroke(1.0f), true);
            }

        } else if (path.getCurrentPoint() != null) {
            newShape = path;
        }

        setShape(newShape, mouseEvent);
        updateLabel(mouseEvent, getDefaultView2d(mouseEvent));

    }

    @Override
    public List<MeasureItem> getMeasurements(ImageElement imageElement, boolean releaseEvent, boolean drawOnLabel) {

        if (imageElement != null && isShapeValid()) {
            MeasurementsAdapter adapter = imageElement.getMeasurementAdapter();

            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>(3);

                if (LineLength.isComputed() && (!drawOnLabel || LineLength.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || LineLength.isQuickComputing()) {
                        val = C.distance(D) * adapter.getCalibRatio();
                    }
                    measVal.add(new MeasureItem(LineLength, val, adapter.getUnit()));
                }
                if (Orientation.isComputed() && (!drawOnLabel || Orientation.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || Orientation.isQuickComputing()) {
                        val = MathUtil.getOrientation(C, D);
                    }
                    measVal.add(new MeasureItem(Orientation, val, "deg"));
                }
                if (Azimuth.isComputed() && (!drawOnLabel || Azimuth.isGraphicLabel())) {
                    Double val = null;
                    if (releaseEvent || Azimuth.isQuickComputing()) {
                        val = MathUtil.getAzimuth(C, D);
                    }
                    measVal.add(new MeasureItem(Azimuth, val, "deg"));
                }
                return measVal;
            }
        }
        return null;
    }

    @Override
    public boolean isShapeValid() {
        updateTool();
        return (ABvalid && CDvalid);
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void updateTool() {
        A = Pt.A.get(this);
        B = Pt.B.get(this);
        C = Pt.C.get(this);
        D = Pt.D.get(this);

        ABvalid = isLineValid(Pt.A, Pt.B, this);
        CDvalid = isLineValid(Pt.C, Pt.D, this);
    }

    protected enum Pt implements eHandlePoint {
        A(0), B(1), C(2), D(3);
        int index;

        private Pt(int index) {
            this.index = index;
        }

        @Override
        public Point2D get(AbstractDragGraphic g) {
            return (g != null) ? (g.handlePointList.size() >= index + 1 ? g.handlePointList.get(index) : null) : null;
        }
    }
}
