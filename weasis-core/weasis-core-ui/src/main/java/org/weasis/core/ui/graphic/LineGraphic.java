/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *     Benoit Jacquemoud
 ******************************************************************************/
package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.Messages;

public class LineGraphic extends AbstractDragGraphic {

    public static final Icon ICON = new ImageIcon(LineGraphic.class.getResource("/icon/22x22/draw-line.png")); //$NON-NLS-1$

    public LineGraphic(float lineThickness, Color paint, boolean fill) {
        super(2);
        setLineThickness(lineThickness);
        setPaint(paint);
        setFilled(fill);
        setLabelVisible(true);
        updateStroke();
        updateShapeOnDrawing(null);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("MeasureToolBar.line"); //$NON-NLS-1$
    }

    @Override
    public void updateLabel(Object source, Graphics2D g2d) {
        if (isLabelVisible) {
            ImageElement image = null;

            if (source instanceof MouseEvent) {
                image = getImageElement((MouseEvent) source);
            } else if (source instanceof ImageElement) {
                image = (ImageElement) source;
            }
            if (image != null) {
                AffineTransform rescale =
                    AffineTransform.getScaleInstance(image.getPixelSizeX(), image.getPixelSizeY());

                Point2D At = rescale.transform(handlePointList.get(0), null);
                Point2D Bt = rescale.transform(handlePointList.get(1), null);

                Unit unit = image.getPixelSpacingUnit();
                String label = "Dist : " + DecFormater.twoDecimal(At.distance(Bt)) + " " + unit.getAbbreviation();
                setLabel(new String[] { label }, g2d);
            }
        }
    }

    @Override
    protected void updateShapeOnDrawing(MouseEvent mouseEvent) {
        GeneralPath generalpath = new GeneralPath(Path2D.WIND_NON_ZERO, handlePointList.size());

        if (handlePointList.size() >= 1) {
            Point2D A = handlePointList.get(0);
            generalpath.moveTo(A.getX(), A.getY());

            if (handlePointList.size() >= 2) {
                Point2D B = handlePointList.get(1);
                generalpath.lineTo(B.getX(), B.getY());
            }
        }

        setShape(generalpath, mouseEvent);
        updateLabel(mouseEvent, getGraphics2D(mouseEvent));
    }

    @Override
    public LineGraphic clone() {
        return (LineGraphic) super.clone();
    }

    @Override
    public Graphic clone(int xPos, int yPos) {
        LineGraphic newGraphic = clone();
        newGraphic.updateStroke();
        newGraphic.updateShapeOnDrawing(null);
        return newGraphic;
    }

    // temporary here for inherited classes
    public static Area createAreaForLine(float x1, float y1, float x2, float y2, int width) {
        int i = width;
        int j = 0;
        int or = (int) MathUtil.getOrientation(x1, y1, x2, y2);
        if (or < 45 || or > 135) {
            j = i;
            i = 0;
        }
        GeneralPath generalpath = new GeneralPath();
        generalpath.moveTo(x1 - i, y1 - j);
        generalpath.lineTo(x1 + i, y1 + j);
        generalpath.lineTo(x2 + i, y2 + j);
        generalpath.lineTo(x2 - i, y2 - j);
        generalpath.lineTo(x1 - i, y1 - j);
        generalpath.closePath();
        return new Area(generalpath);
    }

    public double getSegmentLength() {
        if (handlePointList.size() >= 1) {
            Point2D A = handlePointList.get(0);
            if (handlePointList.size() >= 2) {
                Point2D B = handlePointList.get(1);
                return A.distance(B);
            }
        }
        return -1;
    }

    public double getSegmentLength(double scalex, double scaley) {
        if (handlePointList.size() >= 1) {
            Point2D A = handlePointList.get(0);
            if (handlePointList.size() >= 2) {
                Point2D B = handlePointList.get(1);
                return Point2D.distance(scalex * A.getX(), scaley * A.getY(), scalex * B.getX(), scaley * B.getY());
            }
        }
        return -1;
    }

    public Point2D getStartPoint() {
        if (handlePointList.size() >= 1)
            return (Point2D) handlePointList.get(0).clone();
        return null;
    }

    public Point2D getEndPoint() {
        if (handlePointList.size() > 1)
            return (Point2D) handlePointList.get(1).clone();
        return null;
    }
}
