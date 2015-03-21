/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Inform?tica Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.mpr3dview.api.graghics;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import java.util.List;
import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.ui.graphic.GraphicLabel;
import org.weasis.core.ui.graphic.MeasureItem;
import org.weasis.core.ui.graphic.Measurement;

/**
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2013, 10 Dec
 */
public class DVGraphicLabel extends GraphicLabel {
    
    
    public static String[] buildLabelsList(List<MeasureItem> measList) {
        List<String> labelList = new ArrayList<String>(measList.size());

        for (MeasureItem item : measList) {
            if (item != null) {
                Measurement measurement = item.getMeasurement();

                if (measurement != null && measurement.isGraphicLabel()) {
                    StringBuilder sb = new StringBuilder();

                    String name = measurement.getName();
                    Object value = item.getValue();
                    String unit = item.getUnit();

                    if (name != null) {
                        sb.append(name);
                        if (item.getLabelExtension() != null) {
                            sb.append(item.getLabelExtension());
                        }
                        sb.append(" : ");
                        if (value instanceof Number) {
                            sb.append(DecFormater.oneDecimal((Number) value));
                            if (unit != null) {
                                sb.append(" ").append(unit);
                            }
                        } else if (value != null) {
                            sb.append(value.toString());
                        }
                    }
                    labelList.add(sb.toString());
                }
            }
        }
        if (labelList.size() > 0) {
            return labelList.toArray(new String[labelList.size()]);
        }
        return null;

    }

    public void setLabel(Graphics2D g2d, double xPos, double yPos, String... labels) {
        if (g2d == null || labels == null || labels.length == 0) {
            reset();
        } else {
            labelStringArray = labels.clone();
            Font defaultFont = g2d.getFont();
            FontRenderContext fontRenderContext = g2d.getFontRenderContext();
            updateBoundsSize(defaultFont, fontRenderContext);
            labelBounds = new Rectangle.Double(xPos + GROWING_BOUND, yPos
                    + GROWING_BOUND, labelWidth + GROWING_BOUND,
                    (labelHeight * labels.length) + GROWING_BOUND);
            GeomUtil.growRectangle(labelBounds, GROWING_BOUND);
        }
    }

    @Override
    public GraphicLabel clone() {
        return super.clone();
    }

}
