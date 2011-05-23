package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.media.data.ImageElement;

public class FourPointsAngleToolGraphic extends AbstractDragGraphic {

    public static final Icon ICON = new ImageIcon(
        FourPointsAngleToolGraphic.class.getResource("/icon/22x22/draw-4p-angle.png")); //$NON-NLS-1$
    public final static double ARC_RADIUS = 24.0;

    public FourPointsAngleToolGraphic(float lineThickness, Color paint, boolean fill) {
        super(8);
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
        return "Four Points Angle Tool";
    }

    @Override
    public void updateLabel(Object source, Graphics2D g2d) {
    }

    @Override
    protected void updateShapeOnDrawing(MouseEvent mouseEvent) {
        GeneralPath generalpath = new GeneralPath(Path2D.WIND_NON_ZERO, handlePointList.size());

        AdvancedShape newShape = new AdvancedShape(2);
        newShape.addShape(generalpath);

        String label = "";

        if (handlePointList.size() >= 1) {
            Point2D A = handlePointList.get(0);
            generalpath.moveTo(A.getX(), A.getY());

            if (handlePointList.size() >= 2) {
                Point2D B = handlePointList.get(1);
                generalpath.lineTo(B.getX(), B.getY());

                Point2D I = GeomUtil.getMidPoint(A, B);

                if (handlePointList.size() >= 3) {
                    Point2D C = handlePointList.get(2);
                    generalpath.moveTo(C.getX(), C.getY());

                    Point2D J;

                    if (handlePointList.size() >= 4) {
                        Point2D D = handlePointList.get(3);
                        generalpath.lineTo(D.getX(), D.getY());

                        J = GeomUtil.getMidPoint(C, D);

                        if (handlePointList.size() >= 5) {
                            Point2D E = handlePointList.get(4);
                            generalpath.moveTo(E.getX(), E.getY());

                            if (handlePointList.size() >= 6) {
                                Point2D F = handlePointList.get(5);
                                generalpath.lineTo(F.getX(), F.getY());

                                Point2D K = GeomUtil.getMidPoint(E, F);
                                Point2D L;

                                if (handlePointList.size() >= 7) {
                                    Point2D G = handlePointList.get(6);
                                    generalpath.moveTo(G.getX(), G.getY());

                                    if (handlePointList.size() == 8) {
                                        Point2D H = handlePointList.get(7);
                                        generalpath.lineTo(H.getX(), H.getY());
                                        L = GeomUtil.getMidPoint(G, H);
                                    } else {
                                        L = G;
                                    }

                                    generalpath.append(new Line2D.Double(K, L), false);
                                } else {
                                    L = GeomUtil.getPerpendicularPointFromLine(E, F, K, 1.0);
                                }

                                Point2D P = GeomUtil.getIntersectPoint(I, J, K, L);

                                if (P != null) {
                                    generalpath.append(new Line2D.Double(J, P), false);
                                    generalpath.append(new Line2D.Double(K, P), false);
                                    double startingAngle = GeomUtil.getAngleDeg(P, J);
                                    double angularExtent = GeomUtil.getAngleDeg(J, P, K);
                                    angularExtent = GeomUtil.getSmallestRotationAngleDeg(angularExtent);

                                    label = getRealAngleLabel(getImageElement(mouseEvent), J, P, K);

                                    Rectangle2D ellipseBounds =
                                        new Rectangle2D.Double(P.getX() - ARC_RADIUS, P.getY() - ARC_RADIUS,
                                            2 * ARC_RADIUS, 2 * ARC_RADIUS);

                                    Shape unTransformedShape =
                                        new Arc2D.Double(ellipseBounds, startingAngle, angularExtent, Arc2D.OPEN);

                                    newShape.addInvShape(unTransformedShape, (Point2D) P.clone());
                                }

                                // generalpath.append(unTransformedShape, false);
                            }
                        }
                    } else {
                        J = C;
                    }

                    generalpath.append(new Line2D.Double(I, J), false);
                }
            }
        }
        // setShape(generalpath, mouseEvent);
        setShape(newShape, mouseEvent);
        setLabel(new String[] { label }, getGraphics2D(mouseEvent));
        // updateLabel(mouseevent, getGraphics2D(mouseevent));
    }

    protected Shape computeUnTransformedDrawingShape(double radius, AffineTransform transform) {

        if (handlePointList.size() >= 1) {
            Point2D A = handlePointList.get(0);

            if (handlePointList.size() >= 2) {
                Point2D B = handlePointList.get(1);

                Point2D I = GeomUtil.getMidPoint(A, B);

                if (handlePointList.size() >= 3) {
                    Point2D C = handlePointList.get(2);
                    Point2D J;

                    if (handlePointList.size() >= 4) {
                        Point2D D = handlePointList.get(3);
                        J = GeomUtil.getMidPoint(C, D);

                        if (handlePointList.size() >= 5) {
                            Point2D E = handlePointList.get(4);

                            if (handlePointList.size() >= 6) {
                                Point2D F = handlePointList.get(5);

                                Point2D K = GeomUtil.getMidPoint(E, F);
                                Point2D L;

                                if (handlePointList.size() >= 7) {
                                    Point2D G = handlePointList.get(6);

                                    if (handlePointList.size() == 8) {
                                        Point2D H = handlePointList.get(7);
                                        L = GeomUtil.getMidPoint(G, H);
                                    } else {
                                        L = G;
                                    }
                                } else {
                                    L = GeomUtil.getPerpendicularPointFromLine(E, F, K, 1.0);
                                }

                                Point2D P = GeomUtil.getIntersectPoint(I, J, K, L);
                                if (P != null) {
                                    double startingAngle = GeomUtil.getAngleDeg(P, J);
                                    double angularExtent = GeomUtil.getAngleDeg(J, P, K);
                                    angularExtent = GeomUtil.getSmallestRotationAngleDeg(angularExtent);

                                    return computeUnTransformedDrawingShape(P, ARC_RADIUS, startingAngle,
                                        angularExtent, transform);
                                }
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    protected Shape computeUnTransformedDrawingShape(Point2D P, double radius, double startingAngle,
        double angularExtent, AffineTransform transform) {

        Point2D newP = transform == null ? P : transform.transform(P, null);

        Rectangle2D ellipseBounds =
            new Rectangle2D.Double(newP.getX() - radius, newP.getY() - radius, 2 * radius, 2 * radius);
        // can be simplified !!! by just rescale radius

        if (transform != null) {
            try {
                AffineTransform inverseT = transform.createInverse();
                Point2D upLeftCornerPt = new Point2D.Double(ellipseBounds.getX(), ellipseBounds.getY());
                Point2D newUpLeftCornerPt = inverseT.transform(upLeftCornerPt, null);
                double newWidthHeight = ellipseBounds.getWidth() * inverseT.getScaleX();

                ellipseBounds =
                    new Rectangle2D.Double(newUpLeftCornerPt.getX(), newUpLeftCornerPt.getY(), newWidthHeight,
                        newWidthHeight);

            } catch (NoninvertibleTransformException e) {
                e.printStackTrace();
            }
        }

        return new Arc2D.Double(ellipseBounds, startingAngle, angularExtent, Arc2D.OPEN);
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
    public FourPointsAngleToolGraphic clone() {
        return (FourPointsAngleToolGraphic) super.clone();
    }

    @Override
    public Graphic clone(int xPos, int yPos) {
        FourPointsAngleToolGraphic newGraphic = clone();
        newGraphic.updateStroke();
        newGraphic.updateShapeOnDrawing(null);
        return newGraphic;
    }

}
