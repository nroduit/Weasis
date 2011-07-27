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

import java.awt.Color;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * @author Nicolas Roduit
 */
public class EllipseGraphic extends RectangleGraphic {

    public static final Icon ICON = new ImageIcon(EllipseGraphic.class.getResource("/icon/22x22/draw-eclipse.png")); //$NON-NLS-1$

    public static final Measurement AREA = new Measurement("Area", true, true, true);
    public static final Measurement PERIMETER = new Measurement("Perimeter", true, true, false);
    public static final Measurement CENTER_X = new Measurement("Center X", true, true, false);
    public static final Measurement CENTER_Y = new Measurement("Center Y", true, true, false);
    public static final Measurement WIDTH = new Measurement("Width", true, true, false);
    public static final Measurement HEIGHT = new Measurement("Height", true, true, false);

    public EllipseGraphic(float lineThickness, Color paint, boolean labelVisible) {
        super(lineThickness, paint, labelVisible);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("MeasureToolBar.ellipse"); //$NON-NLS-1$
    }

    @Override
    protected void updateShapeOnDrawing(MouseEventDouble mouseevent) {
        Rectangle2D rectangle = new Rectangle2D.Double();
        rectangle.setFrameFromDiagonal(getHandlePoint(eHandlePoint.NW.index), getHandlePoint(eHandlePoint.SE.index));

        setShape(new Ellipse2D.Double(rectangle.getX(), rectangle.getY(), rectangle.getWidth(), rectangle.getHeight()),
            mouseevent);
        updateLabel(mouseevent, getDefaultView2d(mouseevent));
    }

    @Override
    public List<MeasureItem> getMeasurements(ImageElement imageElement, boolean releaseEvent, boolean drawOnLabel) {

        if (imageElement != null && isShapeValid()) {
            MeasurementsAdapter adapter = imageElement.getMeasurementAdapter();

            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>();
                Rectangle2D rect = new Rectangle2D.Double();

                rect.setFrameFromDiagonal(getHandlePoint(eHandlePoint.NW.index), getHandlePoint(eHandlePoint.SE.index));

                double ratio = adapter.getCalibRatio();

                if (CENTER_X.isComputed() && (!drawOnLabel || CENTER_X.isGraphicLabel())) {
                    Double val =
                        releaseEvent || CENTER_X.isQuickComputing() ? adapter.getXCalibratedValue(rect.getCenterX())
                            : null;
                    measVal.add(new MeasureItem(CENTER_X, val, adapter.getUnit()));
                }
                if (CENTER_Y.isComputed() && (!drawOnLabel || CENTER_Y.isGraphicLabel())) {
                    Double val =
                        releaseEvent || CENTER_Y.isQuickComputing() ? adapter.getYCalibratedValue(rect.getCenterY())
                            : null;
                    measVal.add(new MeasureItem(CENTER_Y, val, adapter.getUnit()));
                }

                if (WIDTH.isComputed() && (!drawOnLabel || WIDTH.isGraphicLabel())) {
                    Double val = releaseEvent || WIDTH.isQuickComputing() ? ratio * rect.getWidth() : null;
                    measVal.add(new MeasureItem(WIDTH, val, adapter.getUnit()));
                }
                if (HEIGHT.isComputed() && (!drawOnLabel || HEIGHT.isGraphicLabel())) {
                    Double val = releaseEvent || HEIGHT.isQuickComputing() ? ratio * rect.getHeight() : null;
                    measVal.add(new MeasureItem(HEIGHT, val, adapter.getUnit()));
                }

                if (AREA.isComputed() && (!drawOnLabel || AREA.isGraphicLabel())) {
                    Double val =
                        releaseEvent || AREA.isQuickComputing() ? Math.PI * rect.getWidth() * ratio * rect.getHeight()
                            * ratio / 4.0 : null;
                    String unit = "pix".equals(adapter.getUnit()) ? adapter.getUnit() : adapter.getUnit() + "2";
                    measVal.add(new MeasureItem(AREA, val, unit));
                }

                List<MeasureItem> stats = getImageStatistics(imageElement, releaseEvent);
                if (stats != null) {
                    measVal.addAll(stats);
                }
                return measVal;
            }
        }
        return null;
    }

}
