package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
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

    public final static Measurement Distance = new Measurement("Distance", true);
    public final static Measurement Orientation = new Measurement("Orientation", true);
    public final static Measurement Azimuth = new Measurement("Azimuth", true);
    public final static Measurement ColorRGB = new Measurement("Color (RGB)", true);

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
    public String getDescription() {
        return null;
    }

    @Override
    protected int moveAndResizeOnDrawing(int handlePointIndex, double deltaX, double deltaY, MouseEventDouble mouseEvent) {
        if (handlePointIndex == -1) {
            for (Point2D point : handlePointList) {
                point.setLocation(point.getX() + deltaX, point.getY() + deltaY);
            }
        } else {
            if (!isGraphicComplete()) {
                handlePointList.get(handlePointIndex).setLocation(mouseEvent.getImageCoordinates());

                if (handlePointList.size() >= 4) {
                    Point2D A = handlePointList.get(0);
                    Point2D B = handlePointList.get(1);
                    Point2D C = handlePointList.get(2);
                    Point2D D = handlePointList.get(3);

                    Point2D I = GeomUtil.getPerpendicularPointToLine(A, B, D);
                    D.setLocation(GeomUtil.getPerpendicularPointToLine(D, I, C));

                    while (handlePointList.size() < handlePointTotalNumber) {
                        handlePointList.add(new Point.Double());
                    }

                    Point2D E = handlePointList.get(4);
                    Point2D F = handlePointList.get(5);

                    E.setLocation(GeomUtil.getMidPoint(A, B));
                    F.setLocation(GeomUtil.getMidPoint(C, D));
                }
            } else {
                Point2D A = handlePointList.get(0);
                Point2D B = handlePointList.get(1);
                Point2D C = handlePointList.get(2);
                Point2D D = handlePointList.get(3);
                Point2D E = handlePointList.get(4);
                Point2D F = handlePointList.get(5);

                if (handlePointIndex == 0 || handlePointIndex == 1) {
                    Point2D anchor = (handlePointIndex == 0) ? B : A;

                    double theta = GeomUtil.getAngleRad(A, B);
                    handlePointList.get(handlePointIndex).setLocation(mouseEvent.getImageCoordinates());
                    theta -= GeomUtil.getAngleRad(A, B);

                    AffineTransform transform = AffineTransform.getRotateInstance(theta, anchor.getX(), anchor.getY());

                    transform.transform(C, C);
                    transform.transform(D, D);
                } else {
                    handlePointList.get(handlePointIndex).setLocation(mouseEvent.getImageCoordinates());

                    if (handlePointIndex == 2) {
                        Point2D J = GeomUtil.getPerpendicularPointToLine(A, B, D);
                        D.setLocation(GeomUtil.getPerpendicularPointToLine(D, J, C));
                    } else if (handlePointIndex == 3) {
                        Point2D I = GeomUtil.getPerpendicularPointToLine(A, B, C);
                        C.setLocation(GeomUtil.getPerpendicularPointToLine(C, I, D));
                    } else if (handlePointIndex == 4) {
                        Point2D I = GeomUtil.getPerpendicularPointToLine(C, D, A);
                        Point2D J = GeomUtil.getPerpendicularPointToLine(C, D, B);

                        A.setLocation(GeomUtil.getPerpendicularPointToLine(A, I, E));
                        B.setLocation(GeomUtil.getPerpendicularPointToLine(B, J, E));

                    } else if (handlePointIndex == 5) {
                        Point2D I = GeomUtil.getPerpendicularPointToLine(A, B, C);
                        Point2D J = GeomUtil.getPerpendicularPointToLine(A, B, D);

                        C.setLocation(GeomUtil.getPerpendicularPointToLine(C, I, F));
                        D.setLocation(GeomUtil.getPerpendicularPointToLine(D, J, F));
                    }
                }

                E.setLocation(GeomUtil.getMidPoint(A, B));
                F.setLocation(GeomUtil.getMidPoint(C, D));
            }
        }
        return handlePointIndex;
    }

    @Override
    protected void updateShapeOnDrawing(MouseEventDouble mouseEvent) {
        GeneralPath generalpath = new GeneralPath(Path2D.WIND_NON_ZERO, handlePointList.size());

        if (handlePointList.size() >= 1) {
            Point2D A = handlePointList.get(0);

            if (handlePointList.size() >= 2) {
                Point2D B = handlePointList.get(1);
                generalpath.moveTo(A.getX(), A.getY());
                generalpath.lineTo(B.getX(), B.getY());

                if (handlePointList.size() >= 3) {
                    Point2D C = handlePointList.get(2);

                    if (handlePointList.size() >= 4) {
                        Point2D D = handlePointList.get(3);
                        generalpath.moveTo(C.getX(), C.getY());
                        generalpath.lineTo(D.getX(), D.getY());
                    }
                }
            }
        }
        setShape(generalpath, mouseEvent);
        updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
    }

    @Override
    public List<MeasureItem> getMeasurements(ImageElement imageElement, boolean releaseEvent) {
        if (imageElement != null && handlePointList.size() >= 4) {
            MeasurementsAdapter adapter = imageElement.getMeasurementAdapter();
            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>();
                Point2D A = handlePointList.get(0);
                Point2D B = handlePointList.get(1);
                Point2D C = handlePointList.get(2);

                if (Distance.isComputed() && (releaseEvent || Distance.isGraphicLabel())) {
                    Double val =
                        releaseEvent || Distance.isQuickComputing() ? C.distance(GeomUtil
                            .getPerpendicularPointToLine(A, B, C)) * adapter.getCalibRatio() : null;
                    measVal.add(new MeasureItem(Distance, val, adapter.getUnit()));
                }
                if (Orientation.isComputed() && (releaseEvent || Orientation.isGraphicLabel())) {
                    Double val = releaseEvent || Orientation.isQuickComputing() ? getSegmentOrientation() : null;
                    measVal.add(new MeasureItem(Orientation, val, "deg"));
                }
                if (Azimuth.isComputed() && (releaseEvent || Azimuth.isGraphicLabel())) {
                    Double val = releaseEvent || Azimuth.isQuickComputing() ? getSegmentAzimuth() : null;
                    measVal.add(new MeasureItem(Azimuth, val, "deg"));
                }
                return measVal;
            }
        }
        return null;
    }

    public Double getSegmentOrientation() {
        if (handlePointList.size() >= 1) {
            Point2D p1 = handlePointList.get(0);
            if (handlePointList.size() >= 2) {
                Point2D p2 = handlePointList.get(1);
                return MathUtil.getOrientation(p1.getX(), p1.getY(), p2.getX(), p2.getY());
            }
        }
        return null;
    }

    public Double getSegmentAzimuth() {
        if (handlePointList.size() >= 1) {
            Point2D p1 = handlePointList.get(0);
            if (handlePointList.size() >= 2) {
                Point2D p2 = handlePointList.get(1);
                return MathUtil.getAzimuth(p1.getX(), p1.getY(), p2.getX(), p2.getY());
            }
        }
        return null;
    }

    @Override
    public ParallelLineGraphic clone() {
        return (ParallelLineGraphic) super.clone();
    }

    @Override
    public Graphic clone(double xPos, double yPos) {
        ParallelLineGraphic newGraphic = clone();
        newGraphic.updateShapeOnDrawing(null);
        return newGraphic;
    }

}
