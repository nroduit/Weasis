package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.Graphics2D;
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
import org.weasis.core.ui.Messages;

public class PerpendicularLineGraphic extends AbstractDragGraphic {

    public static final Icon ICON = new ImageIcon(
        PerpendicularLineGraphic.class.getResource("/icon/22x22/draw-perpendicular.png")); //$NON-NLS-1$

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
            if (!graphicComplete) {
                handlePointList.get(handlePointIndex).setLocation(mouseEvent.getPoint());

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
                    handlePointList.get(handlePointIndex).setLocation(mouseEvent.getPoint());
                    theta -= GeomUtil.getAngleRad(A, B);

                    Point2D anchor = handlePointIndex == 0 ? B : A;
                    AffineTransform transform = AffineTransform.getRotateInstance(theta, anchor.getX(), anchor.getY());

                    transform.transform(C, C);
                    transform.transform(D, D);
                } else if (handlePointIndex == 2) {
                    handlePointList.get(handlePointIndex).setLocation(mouseEvent.getPoint());
                    D.setLocation(GeomUtil.getPerpendicularPointToLine(A, B, C));
                } else if (handlePointIndex == 3) {
                    double tx = D.getX();
                    double ty = D.getY();
                    D.setLocation(GeomUtil.getPerpendicularPointToLine(A, B, mouseEvent.getPoint()));
                    tx -= D.getX();
                    ty -= D.getY();
                    AffineTransform.getTranslateInstance(-tx, -ty).transform(C, C);
                }
            }

        }
        return handlePointIndex;
    }

    @Override
    protected void updateShapeOnDrawing(MouseEvent mouseEvent) {
        GeneralPath generalpath = new GeneralPath(Path2D.WIND_EVEN_ODD, handlePointList.size());
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

                        label = getRealDistanceLabel(getImageElement(mouseEvent), C, D);
                    }
                }
            }
        }

        setShape(generalpath, mouseEvent);
        setLabel(new String[] { label }, getGraphics2D(mouseEvent));
        // updateLabel(mouseevent, getGraphics2D(mouseevent));
    }

    protected String getRealDistanceLabel(ImageElement image, Point2D A, Point2D B) {
        String label = "";
        if (image != null) {
            AffineTransform rescale = AffineTransform.getScaleInstance(image.getPixelSizeX(), image.getPixelSizeY());

            Point2D At = rescale.transform(A, null);
            Point2D Bt = rescale.transform(B, null);

            Unit unit = image.getPixelSpacingUnit();
            label = "Dist : " + DecFormater.twoDecimal(At.distance(Bt)) + " " + unit.getAbbreviation();
        }
        return label;
    }

    @Override
    public PerpendicularLineGraphic clone() {
        return (PerpendicularLineGraphic) super.clone();
    }

    @Override
    public Graphic clone(int xPos, int yPos) {
        PerpendicularLineGraphic newGraphic = clone();
        newGraphic.updateStroke();
        newGraphic.updateShapeOnDrawing(null);
        return newGraphic;
    }

}
