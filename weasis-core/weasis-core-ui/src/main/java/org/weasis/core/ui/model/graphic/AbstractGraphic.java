/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.model.graphic;

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
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlIDREF;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.Image2DViewer;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.layer.GraphicLayer;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.bean.AdvancedShape;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.model.utils.imp.DefaultGraphicLabel;
import org.weasis.core.ui.model.utils.imp.DefaultUUID;
import org.weasis.core.ui.serialize.ColorModelAdapter;
import org.weasis.core.ui.serialize.PointAdapter;
import org.weasis.core.ui.util.MouseEventDouble;

@XmlAccessorType(XmlAccessType.NONE)
public abstract class AbstractGraphic extends DefaultUUID implements Graphic {
    private static final long serialVersionUID = -8152071576417041112L;
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractGraphic.class);

    protected static final String NULL_MSG = "Null is not allowed"; //$NON-NLS-1$

    protected Integer pointNumber;
    protected List<Point2D.Double> pts;
    protected Paint colorPaint = DEFAULT_COLOR;
    protected Float lineThickness = DEFAULT_LINE_THICKNESS;
    protected Boolean labelVisible = DEFAULT_LABEL_VISISIBLE;
    protected Boolean filled = DEFAULT_FILLED;
    protected Integer classID;
    protected GraphicLabel graphicLabel;
    protected LayerType layerType = LayerType.DRAW;

    protected Shape shape;
    protected Boolean selected = DEFAULT_SELECTED;
    protected Boolean variablePointsNumber = Boolean.FALSE;
    protected PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    private GraphicLayer layer;

    public AbstractGraphic(Integer pointNumber) {
        setPointNumber(pointNumber);
        this.variablePointsNumber = Objects.isNull(pointNumber) || pointNumber < 0;
        setPts(null);
    }

    public AbstractGraphic(AbstractGraphic graphic) {
        this.layerType = graphic.layerType;
        setPointNumber(graphic.pointNumber);
        setColorPaint(graphic.colorPaint);
        setLineThickness(graphic.lineThickness);
        setLabelVisible(graphic.labelVisible);
        setFilled(graphic.filled);
        setClassID(graphic.classID);
        this.graphicLabel = graphic.graphicLabel == null ? null : graphic.graphicLabel.copy();

        this.variablePointsNumber = Objects.isNull(graphic.pointNumber) || graphic.pointNumber < 0;
        List<Point2D.Double> ptsList = graphic.pts.stream().filter(Objects::nonNull)
            .map(g -> (Point2D.Double) g.clone()).collect(Collectors.toList());
        try {
            initCopy(graphic);
            buildGraphic(ptsList);
        } catch (InvalidShapeException e) {
            LOGGER.error("Building graphic", e); //$NON-NLS-1$
        }
    }

    @Override
    public String toString() {
        return getUIName();
    }

    /**
     * Returns the total number of points. If the value is null or negative then return 10 as the default value
     *
     * @return total number of points
     */
    @Override
    public Integer getPtsNumber() {
        return pointNumber;
    }

    @Override
    public void setPointNumber(Integer pointNumber) {
        Objects.requireNonNull(pointNumber, NULL_MSG);
        this.pointNumber = pointNumber;
    }

    @XmlElementWrapper(name = "pts", required = false)
    @XmlElement(name = "pt")
    @XmlJavaTypeAdapter(PointAdapter.Point2DAdapter.class)
    @Override
    public List<Point2D.Double> getPts() {
        return pts;
    }

    @Override
    public void setPts(List<Point2D.Double> pts) {
        this.pts = Optional.ofNullable(pts).orElseGet(
            () -> new ArrayList<>(Optional.ofNullable(getPtsNumber()).filter(v -> v >= 0).orElse(DEFAULT_PTS_SIZE)));
    }

    @Override
    public Graphic buildGraphic(List<Point2D.Double> pts) throws InvalidShapeException {
        setPts(pts);
        if (!pts.isEmpty()) {
            prepareShape();
        }
        return this;
    }

    protected abstract void prepareShape() throws InvalidShapeException;

    protected void initCopy(Graphic graphic) {
        // Do noting at this level. Final graphics with new serializable fields must implement this method
    }

    @Override
    public Boolean getVariablePointsNumber() {
        return variablePointsNumber;
    }

    @Override
    public void setVariablePointsNumber(Boolean variablePointsNumber) {
        Objects.requireNonNull(variablePointsNumber, NULL_MSG);
        this.variablePointsNumber = variablePointsNumber;
    }

    @XmlElement(name = "paint", required = false)
    @XmlJavaTypeAdapter(ColorModelAdapter.PaintAdapter.class)
    @Override
    public Paint getColorPaint() {
        return colorPaint;
    }

    public void setColorPaint(Paint colorPaint) {
        this.colorPaint = Optional.ofNullable(colorPaint).orElse(DEFAULT_COLOR);
    }

    @XmlAttribute(name = "thickness", required = false)
    @Override
    public Float getLineThickness() {
        return lineThickness;
    }

    @Override
    public void setLineThickness(Float lineThickness) {
        if (!Objects.equals(this.lineThickness, lineThickness)) {
            this.lineThickness = Optional.ofNullable(lineThickness).orElse(DEFAULT_LINE_THICKNESS);
            if (shape instanceof AdvancedShape) {
                ((AdvancedShape) shape).getShapeList().stream()
                    .forEachOrdered(bs -> bs.changelineThickness(lineThickness));
            }
            fireDrawingChanged();
        }
    }

    @XmlAttribute(name = "showLabel", required = false)
    @Override
    public Boolean getLabelVisible() {
        return labelVisible;
    }

    @Override
    public void setLabelVisible(Boolean labelVisible) {
        if (!Objects.equals(this.labelVisible, labelVisible)) {
            this.labelVisible = Optional.ofNullable(labelVisible).orElse(DEFAULT_LABEL_VISISIBLE);
            fireLabelChanged();
        }
    }

    @XmlAttribute(name = "fill", required = false)
    @Override
    public Boolean getFilled() {
        return filled;
    }

    @Override
    public void setFilled(Boolean filled) {
        if (!Objects.equals(this.filled, filled) && this instanceof GraphicArea) {
            this.filled = Optional.ofNullable(filled).orElse(DEFAULT_FILLED);
            fireDrawingChanged();
        }
    }

    @XmlAttribute(name = "classId", required = false)
    @Override
    public Integer getClassID() {
        return classID;
    }

    @Override
    public void setClassID(Integer classID) {
        this.classID = classID;
    }

    @Override
    public Shape getShape() {
        return shape;
    }

    @Override
    public Boolean getSelected() {
        return selected;
    }

    @Override
    public void setSelected(Boolean selected) {
        if (!Objects.equals(this.selected, selected)) {
            this.selected = Optional.ofNullable(selected).orElse(DEFAULT_SELECTED);
            fireDrawingChanged();
            fireLabelChanged();
        }
    }

    @XmlElement(name = "graphicLabel", required = false)
    @Override
    public GraphicLabel getGraphicLabel() {
        return graphicLabel;
    }

    public void setGraphicLabel(GraphicLabel graphicLabel) {
        this.graphicLabel = graphicLabel;
    }

    @Override
    public void setLayer(GraphicLayer layer) {
        Objects.requireNonNull(layer, NULL_MSG);
        this.layer = layer;
        // Adapt the default layerType
        setLayerType(layer.getType());
    }

    @XmlIDREF
    @XmlElement(name = "layer")
    @Override
    public GraphicLayer getLayer() {
        return layer;
    }

    @Override
    public String getDescription() {
        return ""; //$NON-NLS-1$
    }

    @Override
    public Area getArea(AffineTransform transform) {
        if (Objects.isNull(shape)) {
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

    @Override
    public Boolean intersects(Rectangle rectangle, AffineTransform transform) {
        return Optional.ofNullable(rectangle).map(getArea(transform)::intersects).orElse(false);
    }

    @Override
    public Rectangle getBounds(AffineTransform transform) {
        if (Objects.isNull(shape)) {
            return null;
        }

        if (shape instanceof AdvancedShape) {
            ((AdvancedShape) shape).setAffineTransform(transform);
        }

        Rectangle2D bounds = shape.getBounds2D();

        double growingSize = lineThickness / 2.0;
        growingSize /= GeomUtil.extractScalingFactor(transform);
        GeomUtil.growRectangle(bounds, growingSize);

        return Optional.ofNullable(bounds).map(Rectangle2D::getBounds).orElse(null);
    }

    @Override
    public Rectangle getRepaintBounds(AffineTransform transform) {
        return getRepaintBounds(shape, transform);
    }

    @Override
    public Rectangle getTransformedBounds(Shape shape, AffineTransform transform) {
        Rectangle rectangle = getRepaintBounds(shape, transform);

        if (Objects.nonNull(transform) && Objects.nonNull(rectangle)) {
            rectangle = transform.createTransformedShape(rectangle).getBounds();
        }

        return rectangle;
    }

    @Override
    public Rectangle getTransformedBounds(GraphicLabel label, AffineTransform transform) {
        return Optional.ofNullable(label).map(l -> l.getTransformedBounds(transform).getBounds()).orElse(null);
    }

    @Override
    public void setLabel(String[] labels, ViewCanvas<?> view2d) {
        Consumer<Shape> applyShape = s -> {
            Rectangle2D rect;

            if (s instanceof AdvancedShape && !((AdvancedShape) s).shapeList.isEmpty()) {
                // Assuming first shape is the user drawing path, else stands for decoration
                Shape generalPath = ((AdvancedShape) s).shapeList.get(0).getShape();
                rect = generalPath.getBounds2D();
            } else {
                rect = s.getBounds2D();
            }

            double xPos = rect.getX() + rect.getWidth() + 3;
            double yPos = rect.getY() + rect.getHeight() * 0.5;

            this.setLabel(labels, view2d, new Point2D.Double(xPos, yPos));
        };

        Optional.ofNullable(shape).ifPresent(applyShape);
    }

    @Override
    public void updateLabel(Object source, ViewCanvas<?> view2d) {
        boolean releasedEvent = false;

        if (source instanceof MouseEvent) {
            releasedEvent = ((MouseEvent) source).getID() == MouseEvent.MOUSE_RELEASED;
        } else if (source instanceof Boolean) {
            releasedEvent = (Boolean) source;
        }
        this.updateLabel(view2d, null, releasedEvent);
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

            if (getFilled()) {
                g2d.fill(drawingShape);
            }
        }

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

        g2d.setStroke(oldStroke);
        g2d.setPaint(oldPaint);

        if (getSelected()) {
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

    @Override
    public void addPropertyChangeListener(PropertyChangeListener propertychangelistener) {
        for (PropertyChangeListener listener : pcs.getPropertyChangeListeners()) {
            if (listener == propertychangelistener) {
                return;
            }
        }

        pcs.addPropertyChangeListener(propertychangelistener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener propertychangelistener) {
        pcs.removePropertyChangeListener(propertychangelistener);
    }

    @Override
    public void removeAllPropertyChangeListener() {
        for (PropertyChangeListener listener : pcs.getPropertyChangeListeners()) {
            pcs.removePropertyChangeListener(listener);
        }
    }

    @Override
    public void toFront() {
        if (isGraphicComplete()) {
            firePropertyChange(ACTION_TO_FRONT, null, this);
        }
    }

    @Override
    public void toBack() {
        if (isGraphicComplete()) {
            firePropertyChange(ACTION_TO_BACK, null, this);
        }
    }

    @Override
    public void fireRemoveAction() {
        if (isGraphicComplete()) {
            firePropertyChange(ACTION_REMOVE, null, this);
        }
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
    public Graphic deepCopy() {
        Graphic newGraphic = this.copy();
        if (newGraphic == null) {
            return null;
        }
        for (Point2D p : pts) {
            newGraphic.getPts().add(p != null ? (Point2D.Double) p.clone() : null);
        }
        newGraphic.buildShape();
        return newGraphic;
    }

    @Override
    public void fireRemoveAndRepaintAction() {
        if (isGraphicComplete()) {
            firePropertyChange(ACTION_REMOVE_REPAINT, null, this);
        }
    }

    @Override
    public Stroke getStroke(Float lineThickness) {
        return new BasicStroke(Optional.ofNullable(lineThickness).orElse(DEFAULT_LINE_THICKNESS), BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_ROUND);
    }

    public Stroke getDashStroke(Float lineThickness) {
        return new BasicStroke(Optional.ofNullable(lineThickness).orElse(DEFAULT_LINE_THICKNESS), BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER, 10f, new float[] { 5.0f, 5.0f }, 0f);
    }

    protected Boolean isLabelDisplayable() {
        return labelVisible && graphicLabel != null && graphicLabel.getLabelBounds() != null;
    }

    @Override
    public Boolean isGraphicComplete() {
        return Objects.equals(pts.size(), pointNumber);
    }

    public Point2D.Double getHandlePoint(int index) {
        Predicate<List<Point2D.Double>> validateIndex = list -> list.size() > index;
        Function<List<Point2D.Double>, Point2D.Double> getPoint = list -> list.get(index);
        Function<Point2D.Double, Point2D.Double> cloneValue = point -> (Point2D.Double) point.clone();

        return Optional.of(pts).filter(validateIndex).map(getPoint).map(cloneValue).orElse(null);
    }

    public List<Point2D> getHandlePointList() {
        return pts.stream().map(p -> (Point2D.Double) p.clone()).collect(Collectors.toList());
    }

    public void setHandlePoint(int index, Point2D.Double newPoint) {
        Optional.ofNullable(pts).ifPresent(list -> {
            if (index >= 0 && index <= list.size()) {
                if (index == list.size()) {
                    list.add(newPoint);
                } else {
                    list.set(index, newPoint);
                }
            }
        });
    }

    public Integer getHandlePointListSize() {
        return pts.size();
    }

    @Override
    public Integer getHandleSize() {
        return HANDLE_SIZE;
    }

    @Override
    public Area getArea(MouseEvent mouseEvent) {
        AffineTransform transform = getAffineTransform(mouseEvent);
        return getArea(transform);
    }

    protected AffineTransform getAffineTransform(MouseEvent mouseevent) {
        if (mouseevent != null && mouseevent.getSource() instanceof Image2DViewer) {
            return ((Image2DViewer<?>) mouseevent.getSource()).getAffineTransform();
        }
        return null;
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
        if (Objects.isNull(shape)) {
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
    public Rectangle getRepaintBounds(MouseEvent mouseEvent) {
        AffineTransform transform = getAffineTransform(mouseEvent);
        return getRepaintBounds(shape, transform);
    }

    /**
     * @return selected handle point index if exist, otherwise -1
     */
    @Override
    public int getHandlePointIndex(MouseEventDouble mouseEvent) {
        int nearestHandlePtIndex = -1;
        final Point2D mousePoint = Optional.ofNullable(mouseEvent).map(MouseEventDouble::getImageCoordinates).orElse(null);

        if (mousePoint != null && !pts.isEmpty() && !layer.getLocked()) {
            double minHandleDistance = Double.MAX_VALUE;
            double maxHandleDistance =
                HANDLE_SIZE * 1.5 / GeomUtil.extractScalingFactor(getAffineTransform(mouseEvent));

            for (int index = 0; index < pts.size(); index++) {
                Point2D handlePoint = pts.get(index);
                double handleDistance =
                    Optional.ofNullable(handlePoint).map(mousePoint::distance).orElse(Double.MAX_VALUE);

                if (handleDistance <= maxHandleDistance && handleDistance < minHandleDistance) {
                    minHandleDistance = handleDistance;
                    nearestHandlePtIndex = index;
                }
            }
        }
        return nearestHandlePtIndex;
    }

    public List<Integer> getHandlePointIndexList(MouseEventDouble mouseEvent) {
        Map<Double, Integer> indexByDistanceMap = new TreeMap<>();
        final Point2D mousePoint = Optional.ofNullable(mouseEvent).map(MouseEventDouble::getImageCoordinates).orElse(null);

        if (mousePoint != null && !pts.isEmpty() && !layer.getLocked()) {
            double maxHandleDistance =
                HANDLE_SIZE * 1.5 / GeomUtil.extractScalingFactor(getAffineTransform(mouseEvent));

            for (int index = 0; index < pts.size(); index++) {
                Point2D handlePoint = pts.get(index);
                double handleDistance = (handlePoint != null) ? mousePoint.distance(handlePoint) : Double.MAX_VALUE;

                if (handleDistance <= maxHandleDistance) {
                    indexByDistanceMap.put(handleDistance, index);
                }
            }
        }

        return (!indexByDistanceMap.isEmpty()) ? new ArrayList<>(indexByDistanceMap.values()) : null;
    }

    @Override
    public boolean isOnGraphicLabel(MouseEventDouble mouseevent) {
        if (Objects.isNull(mouseevent)) {
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

    @Override
    @SuppressWarnings("rawtypes")
    public ViewCanvas getDefaultView2d(MouseEvent mouseevent) {
        if (mouseevent != null && mouseevent.getSource() instanceof ViewCanvas) {
            return (ViewCanvas) mouseevent.getSource();
        }
        return null;
    }

    @Override
    public void setShape(Shape newShape, MouseEvent mouseevent) {
        Shape oldShape = this.shape;
        this.shape = newShape;
        fireDrawingChanged(oldShape);
    }

    @Override
    public void setPaint(Color newPaintColor) {
        if (this.colorPaint == null || newPaintColor == null || !this.colorPaint.equals(newPaintColor)) {
            this.colorPaint = newPaintColor;
            fireDrawingChanged();
        }
    }

    protected void fireDrawingChanged() {
        fireDrawingChanged(null);
    }

    protected void fireDrawingChanged(Shape oldShape) {
        firePropertyChange("bounds", oldShape, shape); //$NON-NLS-1$
    }

    protected void firePropertyChange(String s, Object obj, Object obj1) {
        pcs.firePropertyChange(s, obj, obj1);
    }

    protected void firePropertyChange(String s, int i, int j) {
        pcs.firePropertyChange(s, i, j);
    }

    protected void firePropertyChange(String s, boolean flag, boolean flag1) {
        pcs.firePropertyChange(s, flag, flag1);
    }

    protected void fireLabelChanged() {
        fireLabelChanged(null);
    }

    protected void fireLabelChanged(GraphicLabel oldLabel) {
        firePropertyChange("graphicLabel", oldLabel, graphicLabel); //$NON-NLS-1$
    }

    @Override
    public void setLabel(GraphicLabel label) {
        GraphicLabel oldLabel = Optional.ofNullable(graphicLabel).map(GraphicLabel::copy).orElse(null);
        graphicLabel = label;
        fireLabelChanged(oldLabel);
    }

    public void setLabel(String[] labels, ViewCanvas<?> view2d, Point2D pos) {
        GraphicLabel oldLabel = Optional.ofNullable(graphicLabel).map(GraphicLabel::copy).orElse(null);

        if (labels == null || labels.length == 0) {
            graphicLabel = null;
            fireLabelChanged(oldLabel);
        } else if (pos == null) {
            setLabel(labels, view2d);
        } else {
            if (graphicLabel == null) {
                graphicLabel = new DefaultGraphicLabel();
            }
            graphicLabel.setLabel(view2d, pos.getX(), pos.getY(), labels);
            fireLabelChanged(oldLabel);
        }
    }

    @Override
    public void moveLabel(Double deltaX, Double deltaY) {
        if (isLabelDisplayable() && (MathUtil.isDifferentFromZero(deltaX) || MathUtil.isDifferentFromZero(deltaY))) {
            GraphicLabel oldLabel = graphicLabel.copy();
            graphicLabel.move(deltaX, deltaY);
            fireLabelChanged(oldLabel);
        }
    }

    public void updateLabel(ViewCanvas<?> view2d, Point2D pos, boolean releasedEvent) {
        List<Graphic> selectedGraphics =
            view2d == null ? Collections.emptyList() : view2d.getGraphicManager().getSelectedGraphics();
        boolean isMultiSelection = selectedGraphics.size() > 1;

        List<MeasureItem> measList = null;
        String[] labels = null;

        // If isMultiSelection is false, it should return all enable computed measurements when
        // quickComputing is enable or when releasedEvent is true
        if ((labelVisible || !isMultiSelection) && getLayerType() == LayerType.MEASURE) {
            Unit displayUnit = view2d == null ? null : (Unit) view2d.getActionValue(ActionW.SPATIAL_UNIT.cmd());
            measList =
                computeMeasurements(view2d == null ? null : view2d.getMeasurableLayer(), releasedEvent, displayUnit);
        }

        if (labelVisible && measList != null && !measList.isEmpty()) {
            List<String> labelList = new ArrayList<>(measList.size());

            for (MeasureItem item : measList) {
                if (item != null) {
                    Measurement measurement = item.getMeasurement();

                    if (measurement != null && measurement.getGraphicLabel()) {
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
                                sb.append(DecFormater.allNumber((Number) value));
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
            if (!labelList.isEmpty()) {
                labels = labelList.toArray(new String[labelList.size()]);
            }
        }

        if (labels == null && view2d == null && graphicLabel != null) {
            labels = graphicLabel.getLabels();
        }

        setLabel(labels, view2d, pos);

        // update MeasureTool on the fly without calling again getMeasurements
        if (selectedGraphics.size() == 1 && this.equals(selectedGraphics.get(0)) && view2d != null) {
            for (GraphicSelectionListener gfxListener : view2d.getGraphicManager().getGraphicSelectionListeners()) {
                gfxListener.updateMeasuredItems(measList);
            }
        }
    }

    protected void paintHandles(Graphics2D g2d, AffineTransform transform) {
        if (!pts.isEmpty()) {
            double size = HANDLE_SIZE;
            double halfSize = size / 2;

            ArrayList<Point2D> handlePts = new ArrayList<>(pts.size());
            for (Point2D pt : pts) {
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
    @Override
    public boolean isShapeValid() {
        if (!isGraphicComplete()) {
            return false;
        }

        int lastPointIndex = pts.size() - 1;

        while (lastPointIndex > 0) {
            Point2D checkPoint = pts.get(lastPointIndex);

            ListIterator<Point2D.Double> listIt = pts.listIterator(lastPointIndex--);

            while (listIt.hasPrevious()) {
                if (checkPoint != null && checkPoint.equals(listIt.previous())) {
                    return false;
                }
            }
        }
        return true;
    }

    protected void fireMoveAction() {
        if (isGraphicComplete()) {
            firePropertyChange("move", null, this); //$NON-NLS-1$
        }
    }

    @Override
    public LayerType getLayerType() {
        return layerType;
    }

    @Override
    public void setLayerType(LayerType layerType) {
        this.layerType = Objects.requireNonNull(layerType, NULL_MSG);
    }

    static class Adapter extends XmlAdapter<AbstractGraphic, Graphic> {

        @Override
        public Graphic unmarshal(AbstractGraphic v) throws Exception {
            v.buildGraphic(v.getPts());
            return v;
        }

        @Override
        public AbstractGraphic marshal(Graphic v) throws Exception {
            return (AbstractGraphic) v;
        }
    }
}
