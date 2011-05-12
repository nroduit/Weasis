package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.media.data.ImageElement;

public class OpenAngleToolGraphic extends AbstractDragGraphic {

    public static final Icon ICON = new ImageIcon(
        OpenAngleToolGraphic.class.getResource("/icon/22x22/draw-open-angle.png")); //$NON-NLS-1$

    public OpenAngleToolGraphic(float lineThickness, Color paint, boolean fill) {
        super(4);
        setLineThickness(lineThickness);
        setPaint(paint);
        setFilled(fill);
        setLabelVisible(true);
        updateStroke();
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return "Open Angle";
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public void updateLabel(Object source, Graphics2D g2d) {
    }

    @Override
    protected int moveAndResizeOnDrawing(int handlePointIndex, int deltaX, int deltaY, MouseEvent mouseEvent) {
        if (handlePointIndex == -1) {
            for (Point2D point : handlePointList) {
                point.setLocation(point.getX() + deltaX, point.getY() + deltaY);
            }
        } else {
            handlePointList.get(handlePointIndex).setLocation(mouseEvent.getPoint());
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

                    if (handlePointList.size() == 4) {
                        Point2D D = handlePointList.get(3);
                        generalpath.lineTo(D.getX(), D.getY());

                        double Ax = A.getX(), Ay = A.getY();
                        double Bx = B.getX(), By = B.getY();
                        double Cx = C.getX(), Cy = C.getY();
                        double Dx = D.getX(), Dy = D.getY();

                        double denominator = (Bx - Ax) * (Dy - Cy) - (By - Ay) * (Dx - Cx);
                        // If denominator is zero, AB & CD are parallel

                        if (denominator != 0) {

                            double r = ((Ay - Cy) * (Dx - Cx) - (Ax - Cx) * (Dy - Cy)) / denominator; // equ1
                            double s = ((Ay - Cy) * (Bx - Ax) - (Ax - Cx) * (By - Ay)) / denominator; // equ2
                            Point2D P = new Point2D.Double(Ax + r * (Bx - Ax), Ay + r * (By - Ay));

                            // If 0<=r<=1 & 0<=s<=1, segment intersection exists
                            // If r<0 or r>1 or s<0 or s>1, lines intersect but not segments

                            // If r>1, P is located on extension of AB
                            // If r<0, P is located on extension of BA
                            // If s>1, P is located on extension of CD
                            // If s<0, P is located on extension of DC

                            // Let ptArrayX be an ordered array of points along line segments.
                            Point2D[] ptArray1 = new Point2D[3]; // order can be ABP (r>1) or BAP (r<0) or APB (0<=r<=1)
                            Point2D[] ptArray2 = new Point2D[3]; // order can be CDP (s>1) or DCP (s<0) or CPD (0<=s<=1)

                            ptArray1[0] = r >= 0 ? A : B;
                            ptArray1[1] = r < 0 ? A : r > 1 ? B : P;
                            ptArray1[2] = r < 0 ? P : r > 1 ? P : B;

                            ptArray2[0] = s >= 0 ? C : D;
                            ptArray2[1] = s < 0 ? C : s > 1 ? D : P;
                            ptArray2[2] = s < 0 ? P : s > 1 ? P : D;

                            Point2D I = GeomUtil.getColinearPointWithRatio(ptArray1[1], ptArray1[0], 0.25);
                            Point2D J = GeomUtil.getColinearPointWithRatio(ptArray2[1], ptArray2[0], 0.25);

                            // AffineTransform rotate90 = AffineTransform.getQuadrantRotateInstance(0, P.getX(),
                            // P.getY());
                            // AffineTransform rotate180 =
                            // AffineTransform.getQuadrantRotateInstance(2, P.getX(), P.getY());
                            // Point2D K = rotate90.transform(GeomUtil.getColinearPointWithLength(P, A, 3), null);
                            // Point2D L = rotate180.transform(K, null);
                            // Point2D M = rotate90.transform(GeomUtil.getColinearPointWithLength(P, C, 3), null);
                            // Point2D N = rotate180.transform(M, null);

                            Point2D K = new Point2D.Double(P.getX() - 3, P.getY());
                            Point2D L = new Point2D.Double(P.getX() + 3, P.getY());

                            Point2D M = new Point2D.Double(P.getX(), P.getY() - 3);
                            Point2D N = new Point2D.Double(P.getX(), P.getY() + 3);

                            generalpath.append(new Line2D.Double(K, L), false);
                            generalpath.append(new Line2D.Double(M, N), false);

                            Point2D intersecPt = GeomUtil.getIntersectPoint(K, L, M, N);
                            Rectangle2D intersecPtCircleBound =
                                new Rectangle2D.Double(intersecPt.getX() - 1.5, intersecPt.getY() - 1.5, 3, 3);
                            generalpath.append(new Arc2D.Double(intersecPtCircleBound, 0, 360, Arc2D.OPEN), false);

                            // Let arcAngle be the partial section of the ellipse that represents the measured angle
                            double radius = (P.distance(I) + P.distance(J)) / 2.0;
                            double startingAngle = GeomUtil.getAngleDeg(P, ptArray1[0]);
                            double angularExtent = GeomUtil.getAngleDeg(ptArray1[0], P, ptArray2[0]);
                            angularExtent = GeomUtil.getSmallestRotationAngleDeg(angularExtent);

                            label = getRealAngleLabel(getImageElement(mouseEvent), ptArray1[0], P, ptArray2[0]);

                            Rectangle2D ellipseBounds =
                                new Rectangle2D.Double(P.getX() - radius, P.getY() - radius, 2 * radius, 2 * radius);
                            Arc2D arcAngle = new Arc2D.Double(ellipseBounds, startingAngle, angularExtent, Arc2D.OPEN);

                            generalpath.append(arcAngle, false);
                        }
                    }
                }
            }
        }
        setShape(generalpath, mouseEvent);
        setLabel(new String[] { label }, getGraphics2D(mouseEvent));
        // updateLabel(mouseevent, getGraphics2D(mouseevent));
    }

    protected String getRealAngleLabel(ImageElement image, Point2D A, Point2D O, Point2D B) {
        String label = "";
        if (image != null) {
            AffineTransform rescale = AffineTransform.getScaleInstance(image.getPixelSizeX(), image.getPixelSizeY());

            Point2D At = rescale.transform(A, null);
            Point2D Ot = rescale.transform(O, null);
            Point2D Bt = rescale.transform(B, null);

            double realAngle = GeomUtil.getSmallestRotationAngleDeg(GeomUtil.getAngleDeg(At, Ot, Bt));
            label = "Angle : " + DecFormater.twoDecimal(Math.abs(realAngle)) + "°";
            label += " / " + DecFormater.twoDecimal(180 - Math.abs(realAngle)) + "°";
        }
        return label;
    }

    @Override
    public OpenAngleToolGraphic clone() {
        return (OpenAngleToolGraphic) super.clone();
    }

    @Override
    public Graphic clone(int xPos, int yPos) {
        OpenAngleToolGraphic newGraphic = clone();
        newGraphic.updateStroke();
        newGraphic.updateShapeOnDrawing(null);
        return newGraphic;
    }

}
