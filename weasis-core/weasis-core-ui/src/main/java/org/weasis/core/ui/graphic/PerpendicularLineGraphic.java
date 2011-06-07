package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
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

    public final static Measurement LineLength = new Measurement("Line length", true, true, true);
    public final static Measurement Orientation = new Measurement("Orientation", true, true, false);
    public final static Measurement Azimuth = new Measurement("Azimuth", true, true, false);
    public final static Measurement ColorRGB = new Measurement("Color (RGB)", true, true, false);

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
        if (handlePointIndex == -1) {
            handlePointIndex = super.moveAndResizeOnDrawing(handlePointIndex, deltaX, deltaY, mouseEvent);
        } else {
            if (!isGraphicComplete()) {
                handlePointList.get(handlePointIndex).setLocation(mouseEvent.getImageCoordinates());

                if (handlePointList.size() >= 3) {
                    Point2D A = handlePointList.get(0);
                    Point2D B = handlePointList.get(1);
                    Point2D C = handlePointList.get(2);

                    while (handlePointList.size() < handlePointTotalNumber) {
                        handlePointList.add(new Point.Double());
                    }

                    Point2D D = handlePointList.get(3);
                    D.setLocation(GeomUtil.getPerpendicularPointToLine(A, B, C));
                }
            } else {
                Point2D A = handlePointList.get(0);
                Point2D B = handlePointList.get(1);
                Point2D C = handlePointList.get(2);
                Point2D D = handlePointList.get(3);

                if (handlePointIndex == 0 || handlePointIndex == 1) {
                    double theta = GeomUtil.getAngleRad(A, B);
                    handlePointList.get(handlePointIndex).setLocation(mouseEvent.getImageCoordinates());
                    theta -= GeomUtil.getAngleRad(A, B);

                    Point2D anchor = (handlePointIndex == 0) ? B : A;
                    AffineTransform transform = AffineTransform.getRotateInstance(theta, anchor.getX(), anchor.getY());

                    transform.transform(C, C);
                    transform.transform(D, D);
                } else if (handlePointIndex == 2) {
                    handlePointList.get(handlePointIndex).setLocation(mouseEvent.getImageCoordinates());
                    D.setLocation(GeomUtil.getPerpendicularPointToLine(A, B, C));
                } else if (handlePointIndex == 3) {
                    double tx = D.getX();
                    double ty = D.getY();
                    D.setLocation(GeomUtil.getPerpendicularPointToLine(A, B, mouseEvent.getImageCoordinates()));
                    tx -= D.getX();
                    ty -= D.getY();
                    AffineTransform.getTranslateInstance(-tx, -ty).transform(C, C);
                }
            }

        }
        return handlePointIndex;
    }

    @Override
    protected void updateShapeOnDrawing(MouseEventDouble mouseEvent) {

        if (handlePointList.size() >= 1) {
            Point2D A = handlePointList.get(0);

            if (handlePointList.size() >= 2) {
                Point2D B = handlePointList.get(1);

                AdvancedShape newShape = new AdvancedShape(3);

                if (!A.equals(B)) {
                    GeneralPath generalpath = new GeneralPath(Path2D.WIND_NON_ZERO, handlePointList.size() / 2);
                    newShape.addShape(generalpath);
                    generalpath.moveTo(A.getX(), A.getY());
                    generalpath.lineTo(B.getX(), B.getY());

                    if (handlePointList.size() >= 3) {
                        Point2D C = handlePointList.get(2);

                        if (handlePointList.size() == 4) {
                            Point2D D = handlePointList.get(3);

                            if (!C.equals(D)) {
                                generalpath.moveTo(C.getX(), C.getY());
                                generalpath.lineTo(D.getX(), D.getY());

                                // Check if D is outside of AB segment
                                if (Math.signum(GeomUtil.getAngleDeg(D, A)) == Math.signum(GeomUtil.getAngleDeg(D, B))) {
                                    Point2D E = D.distance(A) < D.distance(B) ? A : B;
                                    if (!D.equals(E)) {
                                        newShape.addShape(new Line2D.Double(D, E), getDashStroke(1.0f), true);
                                    }
                                }

                                double cornerLength = 10;
                                double dMin =
                                    (2.0 / 3.0) * Math.min(D.distance(C), Math.max(D.distance(A), D.distance(B)));
                                double scalingMin = cornerLength / dMin;

                                Point2D F = GeomUtil.getMidPoint(A, B);
                                if (!D.equals(C) && !F.equals(D)) {
                                    newShape.addInvShape(GeomUtil.getCornerShape(F, D, C, cornerLength),
                                        (Point2D) D.clone(), scalingMin, getStroke(1.0f), true);
                                }
                            }
                        }
                    }
                }
                setShape(newShape, mouseEvent);
                updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
            }
        }
    }

    public Double getSegmentLength(double scalex, double scaley) {
        if (handlePointList.size() >= 3) {
            Point2D A = handlePointList.get(2);
            if (handlePointList.size() >= 4) {
                Point2D B = handlePointList.get(3);
                return Point2D.distance(scalex * A.getX(), scaley * A.getY(), scalex * B.getX(), scaley * B.getY());
            }
        }
        return null;
    }

    public Double getSegmentOrientation() {
        if (handlePointList.size() >= 3) {
            Point2D p1 = handlePointList.get(2);
            if (handlePointList.size() >= 4) {
                Point2D p2 = handlePointList.get(3);
                return MathUtil.getOrientation(p1.getX(), p1.getY(), p2.getX(), p2.getY());
            }
        }
        return null;
    }

    public Double getSegmentAzimuth() {
        if (handlePointList.size() >= 3) {
            Point2D p1 = handlePointList.get(2);
            if (handlePointList.size() >= 4) {
                Point2D p2 = handlePointList.get(3);
                return MathUtil.getAzimuth(p1.getX(), p1.getY(), p2.getX(), p2.getY());
            }
        }
        return null;
    }

    @Override
    public List<MeasureItem> getMeasurements(ImageElement imageElement, boolean releaseEvent, boolean drawOnLabel) {
        if (imageElement != null && handlePointList.size() >= 4) {
            MeasurementsAdapter adapter = imageElement.getMeasurementAdapter();
            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>();

                if (LineLength.isComputed() && (!drawOnLabel || LineLength.isGraphicLabel())) {
                    Double val =
                        releaseEvent || LineLength.isQuickComputing() ? getSegmentLength(adapter.getCalibRatio(),
                            adapter.getCalibRatio()) : null;
                    measVal.add(new MeasureItem(LineLength, val, adapter.getUnit()));
                }
                if (Orientation.isComputed() && (!drawOnLabel || Orientation.isGraphicLabel())) {
                    Double val = releaseEvent || Orientation.isQuickComputing() ? getSegmentOrientation() : null;
                    measVal.add(new MeasureItem(Orientation, val, "deg"));
                }
                if (Azimuth.isComputed() && (!drawOnLabel || Azimuth.isGraphicLabel())) {
                    Double val = releaseEvent || Azimuth.isQuickComputing() ? getSegmentAzimuth() : null;
                    measVal.add(new MeasureItem(Azimuth, val, "deg"));
                }
                return measVal;
            }
        }
        return null;
    }

}
