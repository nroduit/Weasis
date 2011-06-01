package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.DefaultView2d;

@Deprecated
public class MedianLineGraphic extends AbstractDragGraphic {

    public static final Icon ICON = new ImageIcon(MedianLineGraphic.class.getResource("/icon/22x22/draw-parallel.png")); //$NON-NLS-1$

    public MedianLineGraphic(float lineThickness, Color paint, boolean fill) {
        super(4, paint);
        setLineThickness(lineThickness);
        setPaint(paint);
        setFilled(fill);
        setLabelVisible(false);
        updateStroke();
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("MeasureToolBar.median");
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
            handlePointList.get(handlePointIndex).setLocation(mouseEvent.getPoint());
        }

        return handlePointIndex;
    }

    @Override
    protected void updateShapeOnDrawing(MouseEvent mouseevent) {
        GeneralPath generalpath = new GeneralPath(Path2D.WIND_NON_ZERO, handlePointList.size());

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

                        double Ax = A.getX();
                        double Ay = A.getY();
                        double Bx = B.getX();
                        double By = B.getY();
                        double Cx = C.getX();
                        double Cy = C.getY();
                        double Dx = D.getX();
                        double Dy = D.getY();

                        double Mx = (Ax + Dx) / 2;
                        double My = (Ay + Dy) / 2;
                        double Nx = (Bx + Cx) / 2;
                        double Ny = (By + Cy) / 2;

                        Line2D medianLine = new Line2D.Double(Mx, My, Nx, Ny);
                        generalpath.append(medianLine, false);
                    }
                }
            }
        }
        setShape(generalpath, mouseevent);
        // updateLabel(mouseevent, getGraphics2D(mouseevent));
    }

    @Override
    public MedianLineGraphic clone() {
        return (MedianLineGraphic) super.clone();
    }

    @Override
    public Graphic clone(int xPos, int yPos) {
        MedianLineGraphic newGraphic = clone();
        newGraphic.updateStroke();
        newGraphic.updateShapeOnDrawing(null);
        return newGraphic;
    }

    @Override
    public List<MeasureItem> getMeasurements(ImageElement imageElement, boolean b) {
        // TODO Auto-generated method stub
        return null;
    }

}
