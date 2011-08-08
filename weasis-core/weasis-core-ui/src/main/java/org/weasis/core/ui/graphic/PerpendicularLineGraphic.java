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
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.util.MouseEventDouble;

public class PerpendicularLineGraphic extends AbstractDragGraphic {

    public static final Icon ICON = new ImageIcon(
        PerpendicularLineGraphic.class.getResource("/icon/22x22/draw-perpendicular.png")); //$NON-NLS-1$

    public static final Measurement LINE_LENGTH = new Measurement(Messages.getString("measure.line_length"), 1, true, true, true); //$NON-NLS-1$
    public static final Measurement ORIENTATION = new Measurement(Messages.getString("measure.orientation"), 2, true, true, false); //$NON-NLS-1$
    public static final Measurement AZIMUTH = new Measurement(Messages.getString("measure.azimuth"), 3, true, true, false); //$NON-NLS-1$

    // ///////////////////////////////////////////////////////////////////////////////////////////////////
    // Let AB & CD two perpendicular line segments with D being the projected point C on AB
    protected Point2D ptA, ptB, ptC, ptD;

    protected boolean lineABvalid, lineCDvalid; // estimate if line segments are valid or not

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
        return Messages.getString("measure.perpendicular"); //$NON-NLS-1$
    }

    @Override
    protected int moveAndResizeOnDrawing(int handlePointIndex, double deltaX, double deltaY, MouseEventDouble mouseEvent) {

        List<Point2D> prevHandlePointList = getHandlePointList();

        handlePointIndex = super.moveAndResizeOnDrawing(handlePointIndex, deltaX, deltaY, mouseEvent);

        if (handlePointIndex >= 0 && handlePointIndex < getHandlePointListSize()) {
            updateTool();

            if (handlePointIndex == 0 || handlePointIndex == 1) { // drag point is A or B

                Point2D prevPtA = (prevHandlePointList.size() > 0) ? prevHandlePointList.get(0) : null;
                Point2D prevPtB = (prevHandlePointList.size() > 1) ? prevHandlePointList.get(1) : null;

                if (lineABvalid && GeomUtil.isLineValid(prevPtA, prevPtB) && ptC != null && ptD != null) {

                    // compute rotation from previous to actual position
                    double theta = GeomUtil.getAngleRad(prevPtA, prevPtB) - GeomUtil.getAngleRad(ptA, ptB);

                    Point2D anchor = (handlePointIndex == 0) ? ptB : ptA; // anchor is opposite point of A or B
                    AffineTransform rotate = AffineTransform.getRotateInstance(theta, anchor.getX(), anchor.getY());

                    rotate.transform(ptC, ptC);
                    rotate.transform(ptD, ptD);

                    setHandlePoint(2, ptC);
                    setHandlePoint(3, ptD);
                }

            } else if (handlePointIndex == 2) { // drag point is C

                if (lineABvalid && ptC != null) {
                    ptD = GeomUtil.getPerpendicularPointToLine(ptA, ptB, ptC);

                    setHandlePoint(3, ptD);
                }

            } else if (handlePointIndex == 3) { // drag point is D

                Point2D prevPtD = (prevHandlePointList.size() > 3) ? prevHandlePointList.get(3) : null;

                if (lineABvalid && ptD != null && prevPtD != null && ptC != null) {
                    ptD = GeomUtil.getPerpendicularPointToLine(ptA, ptB, ptD);

                    AffineTransform translate =
                        AffineTransform.getTranslateInstance(ptD.getX() - prevPtD.getX(), ptD.getY() - prevPtD.getY());
                    translate.transform(ptC, ptC);

                    setHandlePoint(2, ptC);
                    setHandlePoint(3, ptD);
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

        if (lineABvalid) {
            path.append(new Line2D.Double(ptA, ptB), false);
        }

        if (lineCDvalid) {
            path.append(new Line2D.Double(ptC, ptD), false);
        }

        if (lineABvalid && lineCDvalid) {

            AdvancedShape aShape = (AdvancedShape) (newShape = new AdvancedShape(3));
            aShape.addShape(path);

            if (!ptD.equals(ptA) && !ptD.equals(ptB)) {
                // Check D is outside of AB segment
                if (Math.signum(GeomUtil.getAngleDeg(ptD, ptA)) == Math.signum(GeomUtil.getAngleDeg(ptD, ptB))) {
                    Point2D E = ptD.distance(ptA) < ptD.distance(ptB) ? ptA : ptB;
                    aShape.addShape(new Line2D.Double(ptD, E), getDashStroke(1.0f), true);
                }
            }

            double cornerLength = 10;
            double dMin = Math.min(ptD.distance(ptC), Math.max(ptD.distance(ptA), ptD.distance(ptB))) * 2 / 3;
            double scalingMin = cornerLength / dMin;

            Point2D F = GeomUtil.getMidPoint(ptA, ptB);
            Shape cornerShape = GeomUtil.getCornerShape(F, ptD, ptC, cornerLength);
            if (cornerShape != null) {
                aShape.addInvShape(cornerShape, ptD, scalingMin, getStroke(1.0f), true);
            }

        } else if (path.getCurrentPoint() != null) {
            newShape = path;
        }

        setShape(newShape, mouseEvent);
        updateLabel(mouseEvent, getDefaultView2d(mouseEvent));

    }

    @Override
    public List<MeasureItem> computeMeasurements(ImageElement imageElement, boolean releaseEvent) {

        if (imageElement != null && isShapeValid()) {
            MeasurementsAdapter adapter = imageElement.getMeasurementAdapter();

            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>(3);

                if (LINE_LENGTH.isComputed()) {
                    measVal.add(new MeasureItem(LINE_LENGTH, ptC.distance(ptD) * adapter.getCalibRatio(), adapter
                        .getUnit()));
                }
                if (ORIENTATION.isComputed()) {
                    measVal.add(new MeasureItem(ORIENTATION, MathUtil.getOrientation(ptC, ptD), Messages.getString("measure.deg"))); //$NON-NLS-1$
                }
                if (AZIMUTH.isComputed()) {
                    measVal.add(new MeasureItem(AZIMUTH, MathUtil.getAzimuth(ptC, ptD), Messages.getString("measure.deg"))); //$NON-NLS-1$
                }
                return measVal;
            }
        }
        return null;
    }

    @Override
    public boolean isShapeValid() {
        updateTool();
        return (lineABvalid && lineCDvalid);
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void updateTool() {
        ptA = getHandlePoint(0);
        ptB = getHandlePoint(1);
        ptC = getHandlePoint(2);
        ptD = getHandlePoint(3);

        lineABvalid = (ptA != null && ptB != null && !ptB.equals(ptA));
        lineCDvalid = (ptC != null && ptD != null && !ptC.equals(ptD));
    }

    @Override
    public List<Measurement> getMeasurementList() {
        List<Measurement> list = new ArrayList<Measurement>();
        list.add(LINE_LENGTH);
        list.add(ORIENTATION);
        list.add(AZIMUTH);
        return list;
    }
}
