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
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementArray;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.graphic.AdvancedShape.BasicShape;
import org.weasis.core.ui.graphic.AdvancedShape.ScaleInvariantShape;
import org.weasis.core.ui.util.MouseEventDouble;

@Root(name = "annotation")
public class AnnotationGraphic extends AbstractDragGraphic {

    public static final Icon ICON = new ImageIcon(AnnotationGraphic.class.getResource("/icon/22x22/draw-text.png")); //$NON-NLS-1$

    // ///////////////////////////////////////////////////////////////////////////////////////////////////
    protected Point2D ptBox, ptAnchor; // Let AB be a simple a line segment
    protected boolean lineABvalid; // estimate if line segment is valid or not

    @ElementArray(name = "text")
    protected String[] labelStringArray;
    protected Rectangle2D labelBounds;
    protected double labelWidth;
    protected double labelHeight;

    // ///////////////////////////////////////////////////////////////////////////////////////////////////

    public AnnotationGraphic(Point2D.Double ptAnchoir, Point2D.Double ptBox, float lineThickness, Color paintColor,
        boolean labelVisible) throws InvalidShapeException {
        super(2, paintColor, lineThickness, labelVisible, false);
        if (ptBox == null) {
            throw new InvalidShapeException("ptBox cannot be null!"); //$NON-NLS-1$
        }
        setHandlePointList(ptAnchoir, ptBox);
        if (!isShapeValid()) {
            throw new InvalidShapeException("This shape cannot be drawn"); //$NON-NLS-1$
        }
        buildShape(null);
    }

    public AnnotationGraphic(float lineThickness, Color paintColor, boolean labelVisible) {
        super(2, paintColor, lineThickness, labelVisible);
    }

    protected AnnotationGraphic(
        @ElementList(name = "pts", entry = "pt", type = Point2D.Double.class) List<Point2D.Double> handlePointList,
        @Attribute(name = "handle_pts_nb") int handlePointTotalNumber,
        @Element(name = "paint", required = false) Paint paintColor,
        @Attribute(name = "thickness") float lineThickness, @Attribute(name = "label_visible") boolean labelVisible,
        @Attribute(name = "fill") boolean filled, @ElementArray(name = "text") String[] labelStringArray)
        throws InvalidShapeException {
        super(handlePointList, handlePointTotalNumber, paintColor, lineThickness, labelVisible, filled);
        if (handlePointTotalNumber != 2) {
            throw new InvalidShapeException("Not a valid AnnotationGraphic!"); //$NON-NLS-1$
        }
        this.labelStringArray = labelStringArray;
        buildShape(null);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("Tools.Anno"); //$NON-NLS-1$
    }

    public String[] getLabelStringArray() {
        return labelStringArray;
    }

    @Override
    public void updateLabel(Object source, ViewCanvas view2d) {
        setLabel(labelStringArray, view2d);
    }

    @Override
    public void updateLabel(Object source, ViewCanvas view2d, Point2D pos) {
        setLabel(labelStringArray, view2d, pos);
    }

    @Override
    protected void buildShape(MouseEventDouble mouseEvent) {
        updateTool();
        AdvancedShape newShape = null;

        if (ptBox != null) {
            ViewCanvas view = getDefaultView2d(mouseEvent);
            if (labelStringArray == null) {
                if (view != null) {
                    setLabel(new String[] { getInitialText(view) }, view, ptBox);
                    // call buildShape
                    return;
                }
                if (labelStringArray == null || labelHeight == 0 || labelWidth == 0) {
                    // This graphic cannot be displayed, remove it.
                    fireRemoveAction();
                    return;
                }
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
                newShape.addLinkSegmentToInvariantShape(line, ptBox, labelBounds, getDashStroke(lineThickness), false);

                ScaleInvariantShape arrow =
                    newShape.addScaleInvShape(GeomUtil.getArrowShape(ptAnchor, ptBox, 15, 8), ptAnchor,
                        getStroke(lineThickness), false);
                arrow.setFilled(true);
            }
            newShape.addAllInvShape(labelBounds, ptBox, getStroke(lineThickness), false);

        }

        setShape(newShape, mouseEvent);
    }

    protected String getInitialText(ViewCanvas view) {
        return Messages.getString("AnnotationGraphic.text_box"); //$NON-NLS-1$
    }

    @Override
    public void paintLabel(Graphics2D g2d, AffineTransform transform) {
        if (labelVisible && labelStringArray != null && labelBounds != null) {
            Paint oldPaint = g2d.getPaint();

            Rectangle2D rect = labelBounds;
            Point2D pt = new Point2D.Double(rect.getCenterX(), rect.getCenterY());
            if (transform != null) {
                transform.transform(pt, pt);
            }

            float px = (float) (pt.getX() - rect.getWidth() / 2 + GraphicLabel.GROWING_BOUND);
            float py = (float) (pt.getY() - rect.getHeight() / 2 + GraphicLabel.GROWING_BOUND);

            for (String label : labelStringArray) {
                if (StringUtil.hasText(label)) {
                    py += labelHeight;
                    GraphicLabel.paintColorFontOutline(g2d, label, px, py, Color.WHITE);
                }
            }
            g2d.setPaint(oldPaint);
        }
    }

    protected void setHandlePointList(Point2D.Double ptAnchor, Point2D.Double ptBox) {
        if (ptBox == null && ptAnchor != null) {
            ptBox = ptAnchor;
        }
        if (ptBox != null && ptBox.equals(ptAnchor)) {
            ptAnchor = null;
        }
        setHandlePoint(0, ptAnchor == null ? null : (Point2D.Double) ptAnchor.clone());
        setHandlePoint(1, ptBox == null ? null : (Point2D.Double) ptBox.clone());
        buildShape(null);
    }

    @Override
    public Area getArea(AffineTransform transform) {
        if (shape == null) {
            return new Area();
        }
        if (shape instanceof AdvancedShape) {
            AdvancedShape s = ((AdvancedShape) shape);
            Area area = s.getArea(transform);
            List<BasicShape> list = s.getShapeList();
            if (list.size() > 0) {
                BasicShape b = list.get(list.size() - 1);
                // Allow to move inside the box, not only around stroke.
                area.add(new Area(b.getRealShape()));
            }

            return area;
        } else {
            return super.getArea(transform);
        }
    }

    @Override
    public List<MeasureItem> computeMeasurements(MeasurableLayer layer, boolean releaseEvent, Unit displayUnit) {
        return null;
    }

    protected void updateTool() {
        ptAnchor = getHandlePoint(0);
        ptBox = getHandlePoint(1);

        lineABvalid = ptAnchor != null && !ptAnchor.equals(ptBox);
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    public Point2D getAnchorPoint() {
        updateTool();
        return ptAnchor == null ? null : (Point2D) ptAnchor.clone();
    }

    public Point2D getBoxPoint() {
        updateTool();
        return ptBox == null ? null : (Point2D) ptBox.clone();
    }

    @Override
    public List<Measurement> getMeasurementList() {
        return null;
    }

    protected void reset() {
        labelStringArray = null;
        labelBounds = null;
        labelHeight = labelWidth = 0;
    }

    @Override
    public void setLabel(String[] labels, ViewCanvas view2d) {
        Point2D pt = getBoxPoint();
        if (pt == null) {
            pt = getAnchorPoint();
        }
        if (pt != null) {
            this.setLabel(labels, view2d, pt);
        }
    }

    @Override
    public void setLabel(String[] labels, ViewCanvas view2d, Point2D pos) {
        if (view2d == null || labels == null || labels.length == 0 || pos == null) {
            reset();
        } else {
            Graphics2D g2d = (Graphics2D) view2d.getJComponent().getGraphics();
            if (g2d == null) {
                return;
            }
            labelStringArray = labels;
            Font defaultFont = g2d.getFont();
            FontRenderContext fontRenderContext =
                ((Graphics2D) view2d.getJComponent().getGraphics()).getFontRenderContext();

            updateBoundsSize(defaultFont, fontRenderContext);

            labelBounds = new Rectangle.Double();
            labelBounds.setFrameFromCenter(pos.getX(), pos.getY(), (labelWidth + GraphicLabel.GROWING_BOUND) / 2,
                ((labelHeight * labels.length) + GraphicLabel.GROWING_BOUND) * 2);
            labelBounds.setFrameFromCenter(pos.getX(), pos.getY(), ptBox.getX() + labelWidth / 2
                + GraphicLabel.GROWING_BOUND, ptBox.getY() + labelHeight * labelStringArray.length / 2
                + GraphicLabel.GROWING_BOUND);
            GeomUtil.growRectangle(labelBounds, GraphicLabel.GROWING_BOUND);
        }
        buildShape(null);
    }

    protected void updateBoundsSize(Font defaultFont, FontRenderContext fontRenderContext) {
        if (defaultFont == null) {
            throw new RuntimeException("Font should not be null"); //$NON-NLS-1$
        }
        if (fontRenderContext == null) {
            throw new RuntimeException("FontRenderContext should not be null"); //$NON-NLS-1$
        }

        if (labelStringArray == null || labelStringArray.length == 0) {
            reset();
        } else {
            double maxWidth = 0;
            for (String label : labelStringArray) {
                if (StringUtil.hasText(label)) {
                    TextLayout layout = new TextLayout(label, defaultFont, fontRenderContext);
                    maxWidth = Math.max(layout.getBounds().getWidth(), maxWidth);
                }
            }
            labelHeight = new TextLayout("Tg", defaultFont, fontRenderContext).getBounds().getHeight() + 2; //$NON-NLS-1$
            labelWidth = maxWidth;
        }
    }

    @Override
    public BasicGraphic clone() {
        AnnotationGraphic newGraphic = (AnnotationGraphic) super.clone();
        newGraphic.labelBounds = labelBounds == null ? null : labelBounds.getBounds2D();
        newGraphic.labelWidth = labelWidth;
        newGraphic.labelHeight = labelHeight;
        newGraphic.labelStringArray = labelStringArray;
        return newGraphic;
    }
}
