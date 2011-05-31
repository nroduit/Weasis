package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.DefaultView2d;

public class ParallelLineGraphic extends AbstractDragGraphic {

    public static final Icon ICON = new ImageIcon(
        ParallelLineGraphic.class.getResource("/icon/22x22/draw-parallel.png")); //$NON-NLS-1$

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
    public void updateLabel(Object source, DefaultView2d view2d) {
    }

    @Override
    protected int moveAndResizeOnDrawing(int handlePointIndex, int deltaX, int deltaY, MouseEvent mouseEvent) {
        if (handlePointIndex == -1) {
            for (Point2D point : handlePointList) {
                point.setLocation(point.getX() + deltaX, point.getY() + deltaY);
            }
        } else {
            if (!graphicComplete) {
                handlePointList.get(handlePointIndex).setLocation(mouseEvent.getPoint());

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
                    handlePointList.get(handlePointIndex).setLocation(mouseEvent.getPoint());
                    theta -= GeomUtil.getAngleRad(A, B);

                    AffineTransform transform = AffineTransform.getRotateInstance(theta, anchor.getX(), anchor.getY());

                    transform.transform(C, C);
                    transform.transform(D, D);
                } else {
                    handlePointList.get(handlePointIndex).setLocation(mouseEvent.getPoint());

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
    protected void updateShapeOnDrawing(MouseEvent mouseEvent) {
        GeneralPath generalpath = new GeneralPath(Path2D.WIND_NON_ZERO, handlePointList.size());
        String label = "";

        if (handlePointList.size() >= 1) {
            Point2D A = handlePointList.get(0);
            generalpath.moveTo(A.getX(), A.getY());

            if (handlePointList.size() >= 2) {
                Point2D B = handlePointList.get(1);
                generalpath.lineTo(B.getX(), B.getY());

                if (handlePointList.size() >= 3) {
                    Point2D C = handlePointList.get(2);
                    generalpath.moveTo(C.getX(), C.getY());

                    label =
                        getRealDistanceLabel(getImageElement(mouseEvent), C,
                            GeomUtil.getPerpendicularPointToLine(A, B, C));

                    if (handlePointList.size() >= 4) {
                        Point2D D = handlePointList.get(3);
                        generalpath.lineTo(D.getX(), D.getY());
                    }
                }
            }
        }
        setShape(generalpath, mouseEvent);
        setLabel(new String[] { label }, getDefaultView2d(mouseEvent));
        // updateLabel(mouseevent, getGraphics2D(mouseevent));
    }

    protected String getRealDistanceLabel(ImageElement image, Point2D A, Point2D B) {
        String label = "";
        if (image != null) {
            AffineTransform rescale = AffineTransform.getScaleInstance(image.getPixelSize(), image.getPixelSize());

            Point2D At = rescale.transform(A, null);
            Point2D Bt = rescale.transform(B, null);

            Unit unit = image.getPixelSpacingUnit();
            label = "Dist : " + DecFormater.twoDecimal(At.distance(Bt)) + " " + unit.getAbbreviation();
        }
        return label;
    }

    @Override
    public ParallelLineGraphic clone() {
        return (ParallelLineGraphic) super.clone();
    }

    @Override
    public Graphic clone(int xPos, int yPos) {
        ParallelLineGraphic newGraphic = clone();
        newGraphic.updateStroke();
        newGraphic.updateShapeOnDrawing(null);
        return newGraphic;
    }

}
