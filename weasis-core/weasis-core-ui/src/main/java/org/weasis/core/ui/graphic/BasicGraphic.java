package org.weasis.core.ui.graphic;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.convert.Convert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.Image2DViewer;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.graphic.AdvancedShape.BasicShape;
import org.weasis.core.ui.graphic.model.AbstractLayer;
import org.weasis.core.ui.graphic.model.AbstractLayer.Identifier;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.core.ui.graphic.model.GraphicsListener;
import org.weasis.core.ui.serialize.ColorConverter;
import org.weasis.core.ui.serialize.Point2DConverter;
import org.weasis.core.ui.util.MouseEventDouble;

@Root
public abstract class BasicGraphic implements Graphic {

    public static final int UNDEFINED = -1;

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractDragGraphic.class);
    protected static final int HANDLE_SIZE = 6;
    protected static final int SELECTION_SIZE = 10;

    protected final boolean variablePointsNumber;

    // TODO make it work in deserialization
    // @Element(name = "layer", required = false)
    private Identifier layerID = AbstractLayer.MEASURE;

    protected PropertyChangeSupport pcs;
    protected Shape shape;
    protected boolean selected = false;

    @ElementList(name = "pts", entry = "pt", type = Point2D.Double.class)
    @Convert(Point2DConverter.class)
    protected List<Point2D.Double> handlePointList;
    @Attribute(name = "handle_pts_nb")
    protected int handlePointTotalNumber;
    @Element(name = "paint", required = false)
    @Convert(ColorConverter.class)
    protected Paint colorPaint;
    @Attribute(name = "thickness", required = false)
    protected float lineThickness;
    @Attribute(name = "fill", required = false)
    protected boolean filled;
    @Attribute(name = "label_visible", required = false)
    protected boolean labelVisible;
    @Element(name = "label", required = false)
    protected GraphicLabel graphicLabel;
    @Attribute(name = "class_id")
    protected int classID;

    public BasicGraphic() {
        this(0);
    }

    public BasicGraphic(int handlePointTotalNumber) {
        this(handlePointTotalNumber, Color.YELLOW, 1f, true);
    }

    public BasicGraphic(int handlePointTotalNumber, Paint paintColor, float lineThickness, boolean labelVisible) {
        this(handlePointTotalNumber, paintColor, lineThickness, labelVisible, false);
    }

    public BasicGraphic(int handlePointTotalNumber, Paint paintColor, float lineThickness, boolean labelVisible,
        boolean filled) {
        this(null, handlePointTotalNumber, paintColor, lineThickness, labelVisible, filled, 0);
    }

    public BasicGraphic(List<Point2D.Double> handlePointList, int handlePointTotalNumber, Paint paintColor,
        float lineThickness, boolean labelVisible, boolean filled) {
        this(handlePointList, handlePointTotalNumber, paintColor, lineThickness, labelVisible, filled, 0);
    }

    public BasicGraphic(List<Point2D.Double> handlePointList, int handlePointTotalNumber, Paint paintColor,
        float lineThickness, boolean labelVisible, boolean filled, int classID) {
        if (paintColor == null) {
            paintColor = Color.YELLOW;
        }
        this.variablePointsNumber = handlePointTotalNumber == UNDEFINED;
        this.handlePointTotalNumber = handlePointTotalNumber;
        this.handlePointList =
            handlePointList == null ? new ArrayList<Point2D.Double>(handlePointTotalNumber == UNDEFINED ? 10
                : handlePointTotalNumber) : handlePointList;
        this.colorPaint = paintColor;
        this.lineThickness = lineThickness;
        this.labelVisible = labelVisible;
        this.filled = filled;
        this.classID = classID;
    }

    protected abstract void buildShape();

    @Override
    public Shape getShape() {
        return shape;
    }

    public int getHandlePointTotalNumber() {
        return handlePointTotalNumber;
    }

    public boolean isVariablePointsNumber() {
        return variablePointsNumber;
    }

    public Stroke getStroke(float lineThickness) {
        return new BasicStroke(lineThickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
    }

    public Stroke getDashStroke(float lineThickness) {
        return new BasicStroke(lineThickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 5.0f,
            5.0f }, 0f);
    }

    public int getClassID() {
        return classID;
    }

    public void setClassID(int classID) {
        this.classID = classID;
    }

    public Paint getColorPaint() {
        return colorPaint;
    }

    public float getLineThickness() {
        return lineThickness;
    }

    public int getHandleSize() {
        return HANDLE_SIZE;
    }

    public boolean isFilled() {
        return filled;
    }

    @Override
    public boolean isSelected() {
        return selected;
    }

    @Override
    public boolean isLabelVisible() {
        return labelVisible;
    }

    @Override
    public GraphicLabel getGraphicLabel() {
        return graphicLabel;
    }

    protected boolean isLabelDisplayable() {
        return labelVisible && graphicLabel != null && graphicLabel.labelBounds != null;
    }

    public boolean isGraphicComplete() {
        return handlePointList.size() == handlePointTotalNumber;
    }

    public Point2D.Double getHandlePoint(int index) {
        Point2D.Double handlePoint = null;
        if (index >= 0 && index < handlePointList.size()) {
            if ((handlePoint = handlePointList.get(index)) != null) {
                handlePoint = (Point2D.Double) handlePoint.clone();
            }
        }
        return handlePoint;
    }

    public List<Point2D> getHandlePointList() {
        List<Point2D> handlePointListcopy = new ArrayList<Point2D>(handlePointList.size());

        for (Point2D handlePt : handlePointList) {
            handlePointListcopy.add(handlePt != null ? (Point2D) handlePt.clone() : null);
        }

        return handlePointListcopy;

    }

    public void setHandlePoint(int index, Point2D.Double newPoint) {
        if (index >= 0 && index <= handlePointList.size()) {
            if (index == handlePointList.size()) {
                handlePointList.add(newPoint);
            } else {
                handlePointList.set(index, newPoint);
            }
        }
    }

    public int getHandlePointListSize() {
        return handlePointList.size();
    }

    @Override
    public String getDescription() {
        return ""; //$NON-NLS-1$
    }

    /**
     * @since v1.1.0 - new in Graphic interface
     */
    @Override
    public Area getArea(AffineTransform transform) {
        if (shape == null) {
            return new Area();
        }

        if (shape instanceof AdvancedShape) {
            return ((AdvancedShape) shape).getArea(transform);
        } else {
            double growingSize = Math.max(SELECTION_SIZE, HANDLE_SIZE);
            growingSize = Math.max(growingSize, lineThickness);
            growingSize /= GeomUtil.extractScalingFactor(transform);

            Stroke boundingStroke = new BasicStroke((float) growingSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

            return new Area(boundingStroke.createStrokedShape(shape));
        }
    }

    public Area getArea(MouseEvent mouseEvent) {
        AffineTransform transform = getAffineTransform(mouseEvent);
        return getArea(transform);
    }

    @Override
    public boolean intersects(Rectangle rectangle, AffineTransform transform) {
        return (rectangle != null) ? getArea(transform).intersects(rectangle) : false;
    }

    /**
     * @param affineTransform
     *            Current transform applied to the view. Should be used to compute invariantSizedShape bounding
     *            rectangle in union with drawing shape bounding rectangle.
     * @return Bounding rectangle of all the drawing shape. Handle points paintings not included.<br>
     *         Coordinates are given in RealCoordinates. <br>
     *         Null is return if shape is Null
     * 
     * @since v1.1.0 - new in Graphic interface
     */
    @Override
    public Rectangle getBounds(AffineTransform transform) {
        if (shape == null) {
            return null;
        }

        if (shape instanceof AdvancedShape) {
            ((AdvancedShape) shape).setAffineTransform(transform);
        }

        Rectangle2D bounds = shape.getBounds2D();

        double growingSize = lineThickness / 2.0;
        growingSize /= GeomUtil.extractScalingFactor(transform);
        GeomUtil.growRectangle(bounds, growingSize);

        return (bounds != null) ? bounds.getBounds() : null;
    }

    public Rectangle getBounds(MouseEvent mouseEvent) {
        AffineTransform transform = getAffineTransform(mouseEvent);
        return getBounds(transform);
    }

    /**
     * 
     * @return Bounding rectangle which size has to be modified according to the given transform with handle drawings
     *         and lineThikness taken in consideration<br>
     *         This assumes that handle drawing size do not change with different scaling of views. Hence, real
     *         coordinates of bounding rectangle are modified consequently<br>
     * 
     * @since v1.1.0 - new in Graphic interface
     */
    public Rectangle getRepaintBounds(Shape shape, AffineTransform transform) {
        if (shape == null) {
            return null;
        }

        if (shape instanceof AdvancedShape) {
            ((AdvancedShape) shape).setAffineTransform(transform);
        }

        Rectangle2D bounds = shape.getBounds2D();

        // Add pixel tolerance to ensure that the graphic is correctly repainted
        double growingSize = Math.max(HANDLE_SIZE * 1.5 / 2.0, lineThickness / 2.0) + 2;
        growingSize /= GeomUtil.extractScalingFactor(transform);
        GeomUtil.growRectangle(bounds, growingSize);

        return (bounds != null) ? bounds.getBounds() : null;
    }

    @Override
    public Rectangle getRepaintBounds(AffineTransform transform) {
        return getRepaintBounds(shape, transform);
    }

    public Rectangle getRepaintBounds(MouseEvent mouseEvent) {
        AffineTransform transform = getAffineTransform(mouseEvent);
        return getRepaintBounds(shape, transform);
    }

    /**
     * @return Shape bounding rectangle relative to affineTransform<br>
     *         Handle points bounding rectangles are also included, knowing they have invariant size according to
     *         current view.<br>
     *         Any other invariant sized shape bounding rectangles are included if shape is instanceof AdvancedShape
     */
    @Override
    public Rectangle getTransformedBounds(Shape shape, AffineTransform transform) {
        Rectangle rectangle = getRepaintBounds(shape, transform);

        if (transform != null && rectangle != null) {
            rectangle = transform.createTransformedShape(rectangle).getBounds();
        }

        return rectangle;
    }

    @Override
    public Rectangle getTransformedBounds(GraphicLabel label, AffineTransform transform) {
        return (label != null) ? label.getTransformedBounds(transform).getBounds() : null;
    }

    /**
     * @return selected handle point index if exist, otherwise -1
     */
    public int getHandlePointIndex(MouseEventDouble mouseEvent) {

        int nearestHandlePtIndex = -1;
        final Point2D mousePoint = (mouseEvent != null) ? mouseEvent.getImageCoordinates() : null;

        if (mousePoint != null && handlePointList.size() > 0) {
            double minHandleDistance = Double.MAX_VALUE;
            double maxHandleDistance =
                HANDLE_SIZE * 1.5 / GeomUtil.extractScalingFactor(getAffineTransform(mouseEvent));

            for (int index = 0; index < handlePointList.size(); index++) {
                Point2D handlePoint = handlePointList.get(index);
                double handleDistance = (handlePoint != null) ? mousePoint.distance(handlePoint) : Double.MAX_VALUE;

                if (handleDistance <= maxHandleDistance && handleDistance < minHandleDistance) {
                    minHandleDistance = handleDistance;
                    nearestHandlePtIndex = index;
                }
            }
        }
        return nearestHandlePtIndex;
    }

    public List<Integer> getHandlePointIndexList(MouseEventDouble mouseEvent) {

        Map<Double, Integer> indexByDistanceMap = null;
        final Point2D mousePoint = (mouseEvent != null) ? mouseEvent.getImageCoordinates() : null;

        if (mousePoint != null && handlePointList.size() > 0) {
            double maxHandleDistance =
                HANDLE_SIZE * 1.5 / GeomUtil.extractScalingFactor(getAffineTransform(mouseEvent));

            for (int index = 0; index < handlePointList.size(); index++) {
                Point2D handlePoint = handlePointList.get(index);
                double handleDistance = (handlePoint != null) ? mousePoint.distance(handlePoint) : Double.MAX_VALUE;

                if (handleDistance <= maxHandleDistance) {
                    if (indexByDistanceMap == null) {
                        indexByDistanceMap = new TreeMap<Double, Integer>();
                    }
                    indexByDistanceMap.put(handleDistance, index);
                }
            }
        }

        return (indexByDistanceMap != null) ? new ArrayList<Integer>(indexByDistanceMap.values()) : null;
    }

    public boolean isOnGraphicLabel(MouseEventDouble mouseevent) {
        if (mouseevent == null) {
            return false;
        }

        AffineTransform transform = getAffineTransform(mouseevent);
        if (transform != null && isLabelDisplayable()) {
            Area labelArea = graphicLabel.getArea(transform);
            if (labelArea != null && labelArea.contains(mouseevent.getImageCoordinates())) {
                return true;
            }
        }
        return false;
    }

    protected ViewCanvas getDefaultView2d(MouseEvent mouseevent) {
        if (mouseevent != null && mouseevent.getSource() instanceof ViewCanvas) {
            return (ViewCanvas) mouseevent.getSource();
        }
        return null;
    }

    protected AffineTransform getAffineTransform(MouseEvent mouseevent) {
        if (mouseevent != null && mouseevent.getSource() instanceof Image2DViewer) {
            return ((Image2DViewer) mouseevent.getSource()).getAffineTransform();
        }
        return null;
    }

    public void setShape(Shape newShape, MouseEvent mouseevent) {
        Shape oldShape = this.shape;
        this.shape = newShape;
        fireDrawingChanged(oldShape);
    }

    public void setLineThickness(float lineThickness) {
        if (this.lineThickness != lineThickness) {
            this.lineThickness = lineThickness;
            if (shape instanceof AdvancedShape) {
                for (BasicShape bs : ((AdvancedShape) shape).getShapeList()) {
                    bs.changelineThickness(lineThickness);
                }
            }
            fireDrawingChanged();
        }
    }

    public void setPaint(Color newPaintColor) {
        if (this.colorPaint == null || newPaintColor == null || !this.colorPaint.equals(newPaintColor)) {
            this.colorPaint = newPaintColor;
            fireDrawingChanged();
        }
    }

    public void setFilled(boolean newFilled) {
        if (this.filled != newFilled) {
            if (this instanceof AbstractDragGraphicArea) {
                this.filled = newFilled;
                fireDrawingChanged();
            }
        }
    }

    @Override
    public void setSelected(boolean newSelected) {
        if (this.selected != newSelected) {
            this.selected = newSelected;
            fireDrawingChanged();
            fireLabelChanged();
        }
    }

    public void setLabelVisible(boolean newLabelVisible) {
        if (this.labelVisible != newLabelVisible) {
            this.labelVisible = newLabelVisible;
            fireLabelChanged();
        }
    }

    @Override
    public void setLabel(String[] labels, ViewCanvas view2d) {
        if (shape != null) {
            Rectangle2D rect;

            if (shape instanceof AdvancedShape && ((AdvancedShape) shape).shapeList.size() > 0) {
                // Assuming first shape is the user drawing path, else stands for decoration
                Shape generalPath = ((AdvancedShape) shape).shapeList.get(0).shape;
                rect = generalPath.getBounds2D();
            } else {
                rect = shape.getBounds2D();
            }

            double xPos = rect.getX() + rect.getWidth() + 3;
            double yPos = rect.getY() + rect.getHeight() * 0.5;

            this.setLabel(labels, view2d, new Point2D.Double(xPos, yPos));
        }
    }

    public void setLabel(String[] labels, ViewCanvas view2d, Point2D pos) {
        GraphicLabel oldLabel = (graphicLabel != null) ? graphicLabel.clone() : null;

        if (labels == null || labels.length == 0) {
            graphicLabel = null;
            fireLabelChanged(oldLabel);
        } else if (pos == null) {
            setLabel(labels, view2d);
        } else {
            if (graphicLabel == null) {
                graphicLabel = new GraphicLabel();
            }
            graphicLabel.setLabel(view2d, pos.getX(), pos.getY(), labels);
            fireLabelChanged(oldLabel);
        }
    }

    public void moveLabel(double deltaX, double deltaY) {
        if (isLabelDisplayable() && (deltaX != 0 || deltaY != 0)) {
            GraphicLabel oldLabel = graphicLabel.clone();
            graphicLabel.move(deltaX, deltaY);
            fireLabelChanged(oldLabel);
        }
    }

    @Override
    public void updateLabel(Object source, ViewCanvas view2d) {
        this.updateLabel(source, view2d, null);
    }

    public void updateLabel(Object source, ViewCanvas view2d, Point2D pos) {

        boolean releasedEvent = false;
        MeasurableLayer layer = view2d == null ? null : view2d.getMeasurableLayer();

        if (source instanceof MouseEvent) {
            releasedEvent = ((MouseEvent) source).getID() == MouseEvent.MOUSE_RELEASED;
        } else if (source instanceof Boolean) {
            releasedEvent = (Boolean) source;
        }

        MeasureTool measureToolListener = null;
        boolean isMultiSelection = false; // default is single selection
        AbstractLayerModel model = (view2d != null) ? view2d.getLayerModel() : null;

        if (model != null) {
            ArrayList<Graphic> selectedGraphics = model.getSelectedGraphics();
            isMultiSelection = selectedGraphics.size() > 1;

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
        }

        List<MeasureItem> measList = null;
        String[] labels = null;

        // If isMultiSelection is false, it should return all enable computed measurements when
        // quickComputing is enable or when releasedEvent is true
        if (labelVisible || !isMultiSelection) {
            Unit displayUnit = view2d == null ? null : (Unit) view2d.getActionValue(ActionW.SPATIAL_UNIT.cmd());
            measList = computeMeasurements(layer, releasedEvent, displayUnit);
        }

        if (labelVisible && measList != null && measList.size() > 0) {
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
                            sb.append(" : "); //$NON-NLS-1$
                            if (value instanceof Number) {
                                sb.append(DecFormater.oneDecimal((Number) value));
                                if (unit != null) {
                                    sb.append(" ").append(unit); //$NON-NLS-1$
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
                labels = labelList.toArray(new String[labelList.size()]);
            }
        }

        setLabel(labels, view2d, pos);

        // update MeasureTool on the fly without calling again getMeasurements
        if (measureToolListener != null) {
            measureToolListener.updateMeasuredItems(isMultiSelection ? null : measList);
        }

    }

    @Override
    public void paint(Graphics2D g2d, AffineTransform transform) {

        Paint oldPaint = g2d.getPaint();
        Stroke oldStroke = g2d.getStroke();

        if (shape instanceof AdvancedShape) {
            ((AdvancedShape) shape).paint(g2d, transform);
        } else if (shape != null) {
            Shape drawingShape = (transform == null) ? shape : transform.createTransformedShape(shape);

            g2d.setPaint(colorPaint);
            g2d.setStroke(getStroke(lineThickness));
            g2d.draw(drawingShape);

            if (isFilled()) {
                g2d.fill(drawingShape);
            }
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
    public void paintLabel(Graphics2D g2d, AffineTransform transform) {
        if (isLabelDisplayable()) {
            graphicLabel.paint(g2d, transform, selected);
        }
    }

    protected void paintHandles(Graphics2D g2d, AffineTransform transform) {
        if (handlePointList.size() > 0) {
            double size = HANDLE_SIZE;
            double halfSize = size / 2;

            ArrayList<Point2D> handlePts = new ArrayList<Point2D>(handlePointList.size());
            for (Point2D pt : handlePointList) {
                if (pt != null) {
                    handlePts.add(new Point2D.Double(pt.getX(), pt.getY()));
                }
            }

            Point2D.Double[] handlePtArray = handlePts.toArray(new Point2D.Double[handlePts.size()]);
            transform.transform(handlePtArray, 0, handlePtArray, 0, handlePtArray.length);

            Paint oldPaint = g2d.getPaint();
            Stroke oldStroke = g2d.getStroke();

            g2d.setPaint(Color.black);
            for (Point2D point : handlePtArray) {
                g2d.fill(new Rectangle2D.Double(point.getX() - halfSize, point.getY() - halfSize, size, size));
            }

            g2d.setPaint(Color.white);
            g2d.setStroke(new BasicStroke(1.0f));
            for (Point2D point : handlePtArray) {
                g2d.draw(new Rectangle2D.Double(point.getX() - halfSize, point.getY() - halfSize, size, size));
            }

            g2d.setPaint(oldPaint);
            g2d.setStroke(oldStroke);
        }
    }

    /**
     * Can be overridden to estimate what is a valid shape that can be fully computed and drawn
     * 
     * @return True when not handle points equals each another. <br>
     */
    public boolean isShapeValid() {
        if (!isGraphicComplete()) {
            return false;
        }

        int lastPointIndex = handlePointList.size() - 1;

        while (lastPointIndex > 0) {
            Point2D checkPoint = handlePointList.get(lastPointIndex);

            ListIterator<Point2D.Double> listIt = handlePointList.listIterator(lastPointIndex--);

            while (listIt.hasPrevious()) {
                if (checkPoint != null && checkPoint.equals(listIt.previous())) {
                    return false;
                }
            }
        }
        return true;

    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener propertychangelistener) {
        if (pcs == null) {
            pcs = new PropertyChangeSupport(this);
        }

        for (PropertyChangeListener listener : pcs.getPropertyChangeListeners()) {
            if (listener == propertychangelistener) {
                return;
            }
        }

        pcs.addPropertyChangeListener(propertychangelistener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener propertychangelistener) {
        if (pcs != null) {
            pcs.removePropertyChangeListener(propertychangelistener);
        }
    }

    protected void firePropertyChange(String s, Object obj, Object obj1) {
        if (pcs != null) {
            pcs.firePropertyChange(s, obj, obj1);
        }
    }

    protected void firePropertyChange(String s, int i, int j) {
        if (pcs != null) {
            pcs.firePropertyChange(s, i, j);
        }
    }

    protected void firePropertyChange(String s, boolean flag, boolean flag1) {
        if (pcs != null) {
            pcs.firePropertyChange(s, flag, flag1);
        }
    }

    @Override
    public Identifier getLayerID() {
        return layerID;
    }

    @Override
    public void setLayerID(Identifier layerID) {
        this.layerID = layerID;
    }

    protected void fireDrawingChanged() {
        fireDrawingChanged(null);
    }

    protected void fireDrawingChanged(Shape oldShape) {
        firePropertyChange("bounds", oldShape, shape); //$NON-NLS-1$
    }

    protected void fireLabelChanged() {
        fireLabelChanged(null);
    }

    protected void fireLabelChanged(GraphicLabel oldLabel) {
        firePropertyChange("graphicLabel", oldLabel, graphicLabel); //$NON-NLS-1$
    }

    protected void fireMoveAction() {
        if (isGraphicComplete()) {
            firePropertyChange("move", null, this); //$NON-NLS-1$
        }
    }

    @Override
    public void toFront() {
        if (isGraphicComplete()) {
            firePropertyChange("toFront", null, this); //$NON-NLS-1$
        }
    }

    @Override
    public void toBack() {
        if (isGraphicComplete()) {
            firePropertyChange("toBack", null, this); //$NON-NLS-1$
        }
    }

    @Override
    public void fireRemoveAction() {
        if (isGraphicComplete()) {
            firePropertyChange("remove", null, this); //$NON-NLS-1$
        }

    }

    public void fireRemoveAndRepaintAction() {
        if (isGraphicComplete()) {
            firePropertyChange("remove.repaint", null, this); //$NON-NLS-1$
        }
    }

    @Override
    public String toString() {
        return getUIName();
    }

    @Override
    public int getKeyCode() {
        return 0;
    }

    @Override
    public int getModifier() {
        return 0;
    }

    @Override
    public BasicGraphic clone() {
        BasicGraphic newGraphic = null;
        try {
            newGraphic = (BasicGraphic) super.clone();
        } catch (CloneNotSupportedException clonenotsupportedexception) {
            return null;
        }
        newGraphic.pcs = null;
        newGraphic.shape = null;
        newGraphic.handlePointList =
            new ArrayList<Point2D.Double>(handlePointTotalNumber == UNDEFINED ? 10 : handlePointTotalNumber);
        newGraphic.graphicLabel = null;
        newGraphic.selected = false;
        return newGraphic;
    }

    @Override
    public Graphic deepCopy() {
        BasicGraphic newGraphic = this.clone();
        if (newGraphic == null) {
            return null;
        }
        for (Point2D p : handlePointList) {
            newGraphic.handlePointList.add(p != null ? (Point2D.Double) p.clone() : null);
        }
        newGraphic.buildShape();
        return newGraphic;
    }

}
