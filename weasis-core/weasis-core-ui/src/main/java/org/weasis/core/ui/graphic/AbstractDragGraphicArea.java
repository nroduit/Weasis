package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.util.ArrayList;

import javax.media.jai.PlanarImage;
import javax.media.jai.iterator.RectIter;
import javax.media.jai.iterator.RectIterFactory;

import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.Messages;

public abstract class AbstractDragGraphicArea extends AbstractDragGraphic {

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    public AbstractDragGraphicArea(int handlePointTotalNumber) {
        this(handlePointTotalNumber, Color.YELLOW);
    }

    public AbstractDragGraphicArea(int handlePointTotalNumber, Color paintColor) {
        this(handlePointTotalNumber, paintColor, 1f);
    }

    public AbstractDragGraphicArea(int handlePointTotalNumber, Color paintColor, float lineThickness) {
        this(handlePointTotalNumber, paintColor, lineThickness, true);
    }

    public AbstractDragGraphicArea(int handlePointTotalNumber, Color paintColor, float lineThickness,
        boolean labelVisible) {
        this(handlePointTotalNumber, paintColor, lineThickness, labelVisible, false);
    }

    public AbstractDragGraphicArea(int handlePointTotalNumber, Color paintColor, float lineThickness,
        boolean labelVisible, boolean filled) {
        super(handlePointTotalNumber, paintColor, lineThickness, labelVisible, filled);

    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void updateLabel(Object source, Graphics2D g2d) {
        if (labelVisible) {
            boolean releasedEvent = true;
            ImageElement imageElement = null;
            if (source instanceof MouseEvent) {
                imageElement = getImageElement((MouseEvent) source);
                releasedEvent = ((MouseEvent) source).getID() == MouseEvent.MOUSE_RELEASED;
            } else if (source instanceof ImageElement) {
                imageElement = (ImageElement) source;
            }
            if (imageElement != null) {
                ArrayList<String> list = new ArrayList<String>(5);
                Unit unit = imageElement.getPixelSpacingUnit();
                list.add(Messages.getString("RectangleGraphic.area") //$NON-NLS-1$
                    + DecFormater.oneDecimalUngroup(getGraphicArea(imageElement.getPixelSizeX(),
                        imageElement.getPixelSizeY())) + " " + unit.getAbbreviation() + "2"); //$NON-NLS-1$ //$NON-NLS-2$

                if (releasedEvent) {
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
                                    imageElement.getPixelValueUnit() == null
                                        ? "" : " " + imageElement.getPixelValueUnit(); //$NON-NLS-1$ //$NON-NLS-2$
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
                }
                setLabel(list.toArray(new String[list.size()]), g2d);
            }
        }
    }

    protected ArrayList<Integer> getValueFromArea(PlanarImage imageData) {
        if (imageData == null || shape == null)
            return null;
        Area area = new Area(shape);
        Rectangle bound = area.getBounds();
        bound = imageData.getBounds().intersection(bound);
        if (bound.width == 0 || bound.height == 0)
            return null;
        RectIter it;
        try {
            it = RectIterFactory.create(imageData, bound);
        } catch (Exception ex) {
            it = null;
        }
        ArrayList<Integer> list = null;

        if (it != null) {
            int band = imageData.getSampleModel().getNumBands();
            list = new ArrayList<Integer>();
            int[] c = { 0, 0, 0 };
            it.startBands();
            it.startLines();
            int y = bound.y;
            while (!it.finishedLines()) {
                it.startPixels();
                int x = bound.x;
                while (!it.finishedPixels()) {
                    if (shape.contains(x, y)) {
                        it.getPixel(c);
                        for (int i = 0; i < band; i++) {
                            list.add(c[i]);
                        }
                    }
                    it.nextPixel();
                    x++;
                }
                it.nextLine();
                y++;
            }
        }
        return list;
    }

    protected abstract double getGraphicArea(double scaleX, double scaleY);

}
