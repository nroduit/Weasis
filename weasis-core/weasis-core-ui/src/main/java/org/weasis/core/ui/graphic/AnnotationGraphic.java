package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.util.MouseEventDouble;

public class AnnotationGraphic extends AbstractDragGraphic {

    // TODO annotation icon
    public static final Icon ICON = new ImageIcon(AnnotationGraphic.class.getResource("/icon/22x22/draw-line.png")); //$NON-NLS-1$

    public static final Measurement FIRST_POINT_X = new Measurement("Anchor x", 1, true, true, false);
    public static final Measurement FIRST_POINT_Y = new Measurement("Anchor y", 2, true, true, false);

    // ///////////////////////////////////////////////////////////////////////////////////////////////////
    protected Point2D ptAnchoir, ptFoot; // Let AB be a simple a line segment
    protected boolean lineABvalid; // estimate if line segment is valid or not
    protected String[] labelStringArray;
    protected Rectangle2D labelBounds;
    protected double labelWidth;
    protected double labelHeight = 0.0;

    // ///////////////////////////////////////////////////////////////////////////////////////////////////

    public AnnotationGraphic(float lineThickness, Color paintColor, boolean labelVisible) {
        super(2, paintColor, lineThickness, labelVisible);
    }

    public AnnotationGraphic(Point2D ptFoot, Point2D ptAnchoir, String[] labelStringArray, float lineThickness,
        Color paintColor, boolean labelVisible) {
        super(2, paintColor, lineThickness, labelVisible);
        this.labelStringArray = labelStringArray;
        setHandlePointList(ptFoot, ptAnchoir);
    }

    public AnnotationGraphic(Point2D ptAnchoir, Rectangle2D labelBounds, String[] labelStringArray,
        float lineThickness, Color paintColor, boolean labelVisible) {
        super(1, paintColor, lineThickness, labelVisible);
        this.labelStringArray = labelStringArray;
        this.labelBounds = labelBounds;
        if (labelBounds != null) {
            ptFoot = new Point2D.Double(labelBounds.getCenterX(), labelBounds.getCenterY());
            setHandlePointList(ptFoot, ptAnchoir);
        }
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return "Annotation";
    }

    @Override
    protected void updateShapeOnDrawing(MouseEventDouble mouseEvent) {
        updateTool();
        Shape newShape = null;

        if (lineABvalid) {
            Area bound = labelBounds == null ? null : new Area(labelBounds);
            if (bound == null) {
                Rectangle2D lb = new Rectangle2D.Double();
                // TODO calculate the bound of the label
                lb.setFrameFromCenter(ptFoot, new Point2D.Double(100, 15));
                bound = new Area(lb);

            }
            if (ptAnchoir != null) {
                Area line = new Area(new Line2D.Double(ptAnchoir, ptFoot));
                bound.add(line);
            }
            newShape = bound;

        }

        setShape(newShape, mouseEvent);
        updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
    }

    protected void setHandlePointList(Point2D ptFoot, Point2D ptAnchoir) {
        if (ptFoot != null) {
            setHandlePoint(0, (Point2D) ptFoot.clone());
        }
        if (ptAnchoir != null) {
            setHandlePoint(1, (Point2D) ptAnchoir.clone());
        }
        updateShapeOnDrawing(null);
    }

    @Override
    public void paint(Graphics2D g2d, AffineTransform transform) {

        Paint oldPaint = g2d.getPaint();
        Stroke oldStroke = g2d.getStroke();

        Point2D pt = (Point2D) ptFoot.clone();
        if (transform != null) {
            transform.transform(pt, pt);
        }

        float px = (float) pt.getX();
        float py = (float) pt.getY();
        if (labelStringArray != null) {
            if (labelHeight == 0) {
                Font defaultFont = MeasureTool.viewSetting.getFont();
                FontRenderContext fontRenderContext = g2d.getFontRenderContext();
                double maxWidth = 0;
                for (String label : labelStringArray) {
                    if (label.length() > 0) {
                        TextLayout layout = new TextLayout(label, defaultFont, fontRenderContext);
                        maxWidth = Math.max(layout.getBounds().getWidth(), maxWidth);
                    }
                }
                labelHeight = new TextLayout("Tg", defaultFont, fontRenderContext).getBounds().getHeight() + 2; //$NON-NLS-1$
                labelWidth = maxWidth;

            }
            if (labelBounds != null) {
                // Shape drawingShape = (transform == null) ? shape : transform.createTransformedShape(shape);
                g2d.setPaint(colorPaint);
                g2d.setStroke(getStroke(lineThickness));
                g2d.draw(shape);
                Rectangle2D b = shape.getBounds2D();
                px -= (b.getWidth() / 2.0f);
                py -= (b.getHeight() / 2.0f);
            }
            // AffineTransform oldTransform = g2d.getTransform();
            // g2d.transform(AffineTransform.getRotateInstance(Math.PI
            // * (ratio - 1.0f)));

            for (String label : labelStringArray) {
                if (label.length() > 0) {
                    py += labelHeight;
                    GraphicLabel.paintFontOutline(g2d, label, px, py);
                }
            }
            // g2d.setTransform(oldTransform);
        }

        g2d.setStroke(oldStroke);

        // // Graphics DEBUG
        // if (transform != null) {
        // g2d.setPaint(Color.CYAN);
        // g2d.draw(transform.createTransformedShape(getBounds(transform)));
        // }
        // if (transform != null) {
        // g2d.setPaint(Color.RED);
        // g2d.draw(transform.createTransformedShape(getArea(transform)));
        // }
        // if (transform != null) {
        // g2d.setPaint(Color.BLUE);
        // g2d.draw(transform.createTransformedShape(getRepaintBounds(transform)));
        // }
        // // Graphics DEBUG

        g2d.setPaint(oldPaint);

        if (isSelected()) {
            paintHandles(g2d, transform);
        }

        paintLabel(g2d, transform);
    }

    @Override
    public List<MeasureItem> computeMeasurements(ImageLayer layer, boolean releaseEvent) {
        if (ptAnchoir != null && layer != null && layer.getSourceImage() != null && isShapeValid()) {
            MeasurementsAdapter adapter = layer.getSourceImage().getMeasurementAdapter();

            if (adapter != null) {
                ArrayList<MeasureItem> measVal = new ArrayList<MeasureItem>();

                if (FIRST_POINT_X.isComputed()) {
                    measVal.add(new MeasureItem(FIRST_POINT_X, adapter.getXCalibratedValue(ptAnchoir.getX()), adapter
                        .getUnit()));
                }
                if (FIRST_POINT_Y.isComputed()) {
                    measVal.add(new MeasureItem(FIRST_POINT_Y, adapter.getXCalibratedValue(ptAnchoir.getY()), adapter
                        .getUnit()));
                }

                return measVal;
            }
        }
        return null;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void updateTool() {
        ptFoot = getHandlePoint(0);
        ptAnchoir = getHandlePoint(1);

        lineABvalid = ptFoot != null && !ptFoot.equals(ptAnchoir);
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////

    public Point2D getAnchorPoint() {
        updateTool();
        return ptAnchoir;
    }

    public Point2D getFootPoint() {
        updateTool();
        return ptFoot;
    }

    @Override
    public List<Measurement> getMeasurementList() {
        List<Measurement> list = new ArrayList<Measurement>();
        list.add(FIRST_POINT_X);
        list.add(FIRST_POINT_Y);
        return list;
    }
}
