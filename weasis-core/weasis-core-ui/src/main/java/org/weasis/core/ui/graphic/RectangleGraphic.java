/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.graphic;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

import javax.media.jai.PlanarImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.Messages;

/**
 * The Class RectangleGraphic.
 * 
 * @author Nicolas Roduit
 */
public class RectangleGraphic extends AbstractDragGraphic {

    private static final long serialVersionUID = 5487817085724004342L;
    public static final Icon ICON = new ImageIcon(RectangleGraphic.class.getResource("/icon/22x22/draw-rectangle.png")); //$NON-NLS-1$
    protected int x;
    protected int y;
    protected int width;
    protected int height;

    public RectangleGraphic(float lineThickness, Color paint, boolean fill) {
        this.lineThickness = lineThickness;
        setPaint(paint);
        setFilled(fill);
        updateStroke();
    }

    @Override
    protected void updateStroke() {
        stroke = new BasicStroke(lineThickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
    }

    @Override
    public void move(int i, int j, int k, MouseEvent mouseevent) {
        Point p = needToMoveCanvas(j, k);
        if (p != null) {
            j = p.x;
            k = p.y;
            super.move(i, j, k, mouseevent);
        }
        x += j;
        y += k;
        updateShapeOnDrawing(mouseevent);
    }

    @Override
    protected void updateShapeOnDrawing(MouseEvent mouseevent) {
        setShape(new Rectangle(x, y, width < 1 ? 1 : width, height < 1 ? 1 : height), mouseevent);
        updateLabel(mouseevent, getGraphics2D(mouseevent));
    }

    @Override
    protected int resizeOnDrawing(int i, int j, int k, MouseEvent mouseevent) {
        Point p = needToMoveCanvas(j, k);
        if (p != null) {
            j = p.x;
            k = p.y;
            super.move(i, j, k, null);
        }
        Rectangle rectangle = new Rectangle(x, y, width, height);
        int l = super.adjustBoundsForResize(rectangle, i, j, k);
        x = rectangle.x;
        y = rectangle.y;
        width = rectangle.width;
        height = rectangle.height;
        updateShapeOnDrawing(mouseevent);
        return l;
    }

    protected double getGraphicArea(double scaleX, double scaleY) {
        return (width < 1 ? 1 : width) * scaleX * (height < 1 ? 1 : height) * scaleY;
    }

    @Override
    public void updateLabel(Object source, Graphics2D g2d) {
        if (showLabel) {
            ImageElement imageElement = null;
            if (source instanceof MouseEvent) {
                imageElement = getImageElement((MouseEvent) source);
            } else if (source instanceof ImageElement) {
                imageElement = (ImageElement) source;
            }
            if (imageElement != null) {
                ArrayList<String> list = new ArrayList<String>(5);
                Unit unit = imageElement.getPixelSpacingUnit();
                list.add(Messages.getString("RectangleGraphic.area") //$NON-NLS-1$
                    + DecFormater.oneDecimalUngroup(getGraphicArea(imageElement.getPixelSizeX(),
                        imageElement.getPixelSizeY())) + " " + unit.getAbbreviation() + "2"); //$NON-NLS-1$ //$NON-NLS-2$

                PlanarImage image = imageElement.getImage();
                try {
                    ArrayList<Integer> pList = getValueFromArea(image);
                    if (pList != null && pList.size() > 0) {
                        int band = image.getSampleModel().getNumBands();
                        if (band == 1) {
                            // Hounsfield = pixelValue * rescale slope + intercept value
                            Float slope = (Float) imageElement.getTagValue(TagW.RescaleSlope);
                            Float intercept = (Float) imageElement.getTagValue(TagW.RescaleIntercept);
                            double min = Double.MAX_VALUE;
                            double max = -Double.MAX_VALUE;
                            double sum = 0;
                            for (Integer val : pList) {
                                if (val < min) {
                                    min = val;
                                }
                                if (val > max) {
                                    max = val;
                                }
                                sum += val;
                            }

                            double mean = sum / pList.size();

                            double stdv = 0.0D;
                            for (Integer val : pList) {
                                if (val < min) {
                                    min = val;
                                }
                                if (val > max) {
                                    max = val;
                                }
                                stdv += (val - mean) * (val - mean);
                            }

                            stdv = Math.sqrt(stdv / (pList.size() - 1.0));

                            if (slope != null || intercept != null) {
                                slope = slope == null ? 1.0f : slope;
                                intercept = intercept == null ? 0.0f : intercept;
                                mean = mean * slope + intercept;
                                stdv = stdv * slope + intercept;
                                min = min * slope + intercept;
                                max = max * slope + intercept;
                            }
                            String hu =
                                imageElement.getPixelValueUnit() == null ? "" : " " + imageElement.getPixelValueUnit(); //$NON-NLS-1$ //$NON-NLS-2$
                            list.add(Messages.getString("RectangleGraphic.max") + DecFormater.oneDecimalUngroup(max) + hu); //$NON-NLS-1$
                            list.add(Messages.getString("RectangleGraphic.min") + DecFormater.oneDecimalUngroup(min) + hu); //$NON-NLS-1$
                            list.add(Messages.getString("RectangleGraphic.std") + DecFormater.oneDecimalUngroup(stdv) + hu); //$NON-NLS-1$
                            list.add(Messages.getString("RectangleGraphic.mean") + DecFormater.oneDecimalUngroup(mean) + hu); //$NON-NLS-1$
                        } else {
                            // message.append("R=" + c[0] + " G=" + c[1] + " B=" + c[2]);
                        }
                    }
                } catch (ArrayIndexOutOfBoundsException ex) {
                }

                setLabel(list.toArray(new String[list.size()]), g2d);
            }
        }
    }

    @Override
    public Graphic clone(int i, int j) {
        RectangleGraphic rectanglegraphic;
        try {
            rectanglegraphic = (RectangleGraphic) super.clone();
        } catch (CloneNotSupportedException clonenotsupportedexception) {
            return null;
        }
        rectanglegraphic.x = i;
        rectanglegraphic.y = j;
        rectanglegraphic.width = 1;
        rectanglegraphic.height = 1;
        rectanglegraphic.updateStroke();
        rectanglegraphic.updateShapeOnDrawing(null);
        return rectanglegraphic;
    }

    // public void setPoints(Rectangle rect) {
    // this.x = rect.x;
    // this.y = rect.y;
    // this.width = rect.width;
    // this.height = rect.height;
    // updateShapeOnDrawing(null);
    // }
    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("MeasureToolBar.rect"); //$NON-NLS-1$
    }

    @Override
    public String getDescription() {
        return null;
    }
}
