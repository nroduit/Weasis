/*******************************************************************************
 * Copyright (c) 2011 Weasis Team.
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
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementArray;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.PixelInfo;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.graphic.AdvancedShape.ScaleInvariantShape;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.core.ui.graphic.model.GraphicsListener;
import org.weasis.core.ui.util.MouseEventDouble;

@Root(name = "pixelInfo")
public class PixelInfoGraphic extends AnnotationGraphic {

    public static final Icon ICON = new ImageIcon(PixelInfoGraphic.class.getResource("/icon/22x22/draw-pixelinfo.png")); //$NON-NLS-1$

    public static final Measurement ANCHOR_POINT_X = new Measurement(
        Messages.getString("PixelInfoGraphic.x"), 1, true, true, false); //$NON-NLS-1$
    public static final Measurement ANCHOR_POINT_Y = new Measurement(
        Messages.getString("PixelInfoGraphic.y"), 2, true, true, false); //$NON-NLS-1$

    private PixelInfo pixelInfo;

    public PixelInfoGraphic(Point2D.Double ptAnchoir, Point2D.Double ptBox, float lineThickness, Color paintColor,
        boolean labelVisible) throws InvalidShapeException {
        super(ptAnchoir, ptBox, lineThickness, paintColor, labelVisible);
    }

    public PixelInfoGraphic(float lineThickness, Color paintColor, boolean labelVisible) {
        super(lineThickness, paintColor, labelVisible);
    }

    protected PixelInfoGraphic(
        @ElementList(name = "pts", entry = "pt", type = Point2D.Double.class) List<Point2D.Double> handlePointList,
        @Attribute(name = "handle_pts_nb") int handlePointTotalNumber,
        @Element(name = "paint", required = false) Paint paintColor,
        @Attribute(name = "thickness") float lineThickness, @Attribute(name = "label_visible") boolean labelVisible,
        @Attribute(name = "fill") boolean filled, @ElementArray(name = "text") String[] labelStringArray)
        throws InvalidShapeException {
        super(handlePointList, handlePointTotalNumber, paintColor, lineThickness, labelVisible, filled,
            labelStringArray);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("Tools.pixelInfo"); //$NON-NLS-1$
    }

    @Override
    protected void buildShape(MouseEventDouble mouseEvent) {
        updateTool();
        AdvancedShape newShape = null;
        ViewCanvas view2d = getDefaultView2d(mouseEvent);

        if (ptBox != null) {
            if (labelStringArray == null) {
                if (view2d != null) {
                    setLabel(null, view2d, ptBox);
                    // call buildShape
                    return;
                }
                if (labelStringArray == null || labelHeight == 0 || labelWidth == 0) {
                    // This graphic cannot be displayed, remove it.
                    fireRemoveAction();
                    return;
                }
            }
            if (view2d != null && ptAnchor != null) {
                setLabel(labelStringArray, view2d, ptBox);
                // call buildShape
                return;
            }
            newShape = new AdvancedShape(this, 2);
            Line2D line = null;
            if (lineABvalid) {
                line = new Line2D.Double(ptBox, ptAnchor);
            }
            labelBounds = new Rectangle.Double();
            labelBounds.setFrameFromCenter(ptBox.getX(), ptBox.getY(), ptBox.getX() + labelWidth / 2
                + GraphicLabel.GROWING_BOUND, ptBox.getY() + labelHeight * labelStringArray.length / 2
                + GraphicLabel.GROWING_BOUND);
            GeomUtil.growRectangle(labelBounds, GraphicLabel.GROWING_BOUND);
            if (line != null) {
                newShape.addLinkSegmentToInvariantShape(line, ptBox, labelBounds, getDashStroke(lineThickness), true);

                ScaleInvariantShape arrow =
                    newShape.addScaleInvShape(GeomUtil.getArrowShape(ptAnchor, ptBox, 15, 8), ptAnchor,
                        getStroke(lineThickness), true);
                arrow.setFilled(true);
            }
            newShape.addAllInvShape(labelBounds, ptBox, getStroke(lineThickness), true);

        }

        setShape(newShape, mouseEvent);
    }

    @Override
    public void setLabel(String[] labels, ViewCanvas view2d, Point2D pos) {
        String[] lbs = null;
        if (view2d != null && ptAnchor != null) {
            pixelInfo =
                view2d.getPixelInfo(new Point((int) Math.floor(ptAnchor.getX()), (int) Math.floor(ptAnchor.getY())));
            if (pixelInfo != null) {
                lbs = new String[] { pixelInfo.getPixelValueText() };
            }
        } else {
            lbs = labels;
        }
        if (lbs == null) {
            lbs = new String[] { "No info" };
        }
        super.setLabel(lbs, view2d, pos);
        MeasurableLayer layer = view2d == null ? null : view2d.getMeasurableLayer();
        AbstractLayerModel model = (view2d != null) ? view2d.getLayerModel() : null;
        if (model != null) {
            ArrayList<Graphic> selectedGraphics = model.getSelectedGraphics();
            boolean isMultiSelection = selectedGraphics.size() > 1;
            MeasureTool measureToolListener = null;

            if (selectedGraphics.size() == 1 && selectedGraphics.get(0) == this) {
                GraphicsListener[] gfxListeners = model.getGraphicSelectionListeners();
                if (gfxListeners != null) {
                    for (GraphicsListener listener : gfxListeners) {
                        if (listener instanceof MeasureTool) {
                            measureToolListener = (MeasureTool) listener;
                            break;
                        }
                    }
                }
            }

            if (measureToolListener != null && !isMultiSelection) {
                Unit displayUnit = view2d == null ? null : (Unit) view2d.getActionValue(ActionW.SPATIAL_UNIT.cmd());
                List<MeasureItem> measList = computeMeasurements(layer, true, displayUnit);
                measureToolListener.updateMeasuredItems(measList);
            }
        }
    }

    @Override
    public List<MeasureItem> computeMeasurements(MeasurableLayer layer, boolean releaseEvent, Unit displayUnit) {

        if (layer != null && layer.hasContent() && isShapeValid()) {
            MeasurementsAdapter adapter = layer.getMeasurementAdapter(displayUnit);

            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>();

                if (ANCHOR_POINT_X.isComputed()) {
                    measVal.add(new MeasureItem(ANCHOR_POINT_X, adapter.getXCalibratedValue(ptAnchor.getX()), adapter
                        .getUnit()));
                }
                if (ANCHOR_POINT_Y.isComputed()) {
                    measVal.add(new MeasureItem(ANCHOR_POINT_Y, adapter.getYCalibratedValue(ptAnchor.getY()), adapter
                        .getUnit()));
                }
                String unit = Unit.PIXEL.getAbbreviation();
                if (!unit.equals(adapter.getUnit())) {
                    if (ANCHOR_POINT_X.isComputed()) {
                        measVal.add(new MeasureItem(ANCHOR_POINT_X, ptAnchor.getX() + adapter.getOffsetX(), unit));
                    }
                    if (ANCHOR_POINT_Y.isComputed()) {
                        measVal.add(new MeasureItem(ANCHOR_POINT_Y, ptAnchor.getY() + adapter.getOffsetY(), unit));
                    }
                }

                if (pixelInfo != null) {
                    double[] values = pixelInfo.getValues();
                    String[] channelNames = pixelInfo.getChannelNames();
                    if (values != null) {
                        for (int i = 0; i < values.length; i++) {
                            Measurement m =
                                new Measurement((channelNames == null || i >= channelNames.length)
                                    ? Messages.getString("PixelInfoGraphic.unknown") //$NON-NLS-1$
                                    : channelNames[i], i + 2, true, true, false);
                            measVal.add(new MeasureItem(m, values[i], pixelInfo.getPixelValueUnit()));
                        }
                    }
                }
                return measVal;
            }
        }
        return null;
    }
}
