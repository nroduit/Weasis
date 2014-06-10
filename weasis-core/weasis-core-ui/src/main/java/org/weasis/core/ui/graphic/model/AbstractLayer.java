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
package org.weasis.core.ui.graphic.model;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.GraphicLabel;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * The Class AbstractLayer.
 * 
 * @author Nicolas Roduit
 */
public abstract class AbstractLayer implements Comparable, Serializable, Layer {

    private static final long serialVersionUID = -6113490831569841167L;

    public static final Identifier IMAGE = new Identifier(20, Messages.getString("AbstractLayer.image")); //$NON-NLS-1$
    public static final Identifier CROSSLINES = new Identifier(100, Messages.getString("Tools.cross"));//$NON-NLS-1$ 
    public static final Identifier ANNOTATION = new Identifier(200, Messages.getString("Tools.Anno"));//$NON-NLS-1$ 
    // public static final Identifier DRAW = new Identifier(400, "Graphic Annotation");
    public static final Identifier MEASURE = new Identifier(500, Messages.getString("Tools.meas"));//$NON-NLS-1$ 
    //  public static final Identifier OBJECTEXTRACT = new Identifier(600, Messages.getString("Tools.seg"));//$NON-NLS-1$ 
    public static final Identifier TEMPDRAGLAYER = new Identifier(1000, Messages.getString("Tools.deco"));//$NON-NLS-1$ 

    protected final PropertyChangeListener pcl;
    protected final transient LayerModel layerModel;

    private boolean visible;
    private boolean locked;
    // Layers are sorted by level number (ascending order)
    private int level;
    private final Identifier identifier;
    protected volatile GraphicList graphics;

    /**
     * The Class PropertyChangeHandler.
     * 
     * @author Nicolas Roduit
     */
    class PropertyChangeHandler implements PropertyChangeListener, Serializable {

        // This method is called when a property is changed (fired from a graphic)
        @Override
        public void propertyChange(PropertyChangeEvent propertychangeevent) {
            Object obj = propertychangeevent.getSource();
            String s = propertychangeevent.getPropertyName();
            if (obj instanceof Graphic) {
                Graphic graph = (Graphic) obj;
                if ("bounds".equals(s)) { //$NON-NLS-1$
                    graphicBoundsChanged(graph, (Shape) propertychangeevent.getOldValue(),
                        (Shape) propertychangeevent.getNewValue(), getAffineTransform());
                } else if ("graphicLabel".equals(s)) { //$NON-NLS-1$
                    labelBoundsChanged(graph, (GraphicLabel) propertychangeevent.getOldValue(),
                        (GraphicLabel) propertychangeevent.getNewValue(), getAffineTransform());
                } else if ("remove".equals(s)) { //$NON-NLS-1$
                    removeGraphic(graph);
                } else if ("remove.repaint".equals(s)) { //$NON-NLS-1$
                    removeGraphicAndRepaint(graph);
                } else if ("toFront".equals(s)) { //$NON-NLS-1$
                    toFront(graph);
                } else if ("toBack".equals(s)) { //$NON-NLS-1$
                    toBack(graph);
                }
            }

        }

        private static final long serialVersionUID = -9094820911680205527L;

        private PropertyChangeHandler() {
        }
    }

    public AbstractLayer(LayerModel layerModel, Identifier identifier) {
        if (layerModel == null || identifier == null) {
            throw new IllegalArgumentException();
        }
        this.layerModel = layerModel;
        this.identifier = identifier;
        this.level = identifier.getDefaultLevel();
        this.graphics = new GraphicList();
        this.pcl = new PropertyChangeHandler();
        this.visible = true;
        this.locked = false;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public void addGraphic(Graphic graphic) {
        if (graphics != null && !graphics.list.contains(graphic)) {
            graphics.list.add(graphic);
            graphic.setLayerID(identifier);
            graphic.addPropertyChangeListener(pcl);
            ArrayList<AbstractLayer> layers = graphics.getLayers();
            if (layers != null) {
                for (AbstractLayer layer : layers) {
                    graphic.addPropertyChangeListener(layer.pcl);
                    layer.repaint(graphic.getRepaintBounds(getAffineTransform()));
                }
            }
        }
    }

    public void toFront(Graphic graphic) {
        if (graphics != null) {
            graphics.list.remove(graphic);
            graphics.list.add(graphic);
            repaint(graphic.getRepaintBounds(getAffineTransform()));
        }
    }

    public synchronized void setGraphics(GraphicList graphics) {
        if (this.graphics != graphics) {
            if (this.graphics != null) {
                this.graphics.removeLayer(this);
                for (Graphic graphic : this.graphics.list) {
                    graphic.removePropertyChangeListener(pcl);
                }
                layerModel.setSelectedGraphics(null);
            }
            if (graphics == null) {
                this.graphics = new GraphicList();
            } else {
                this.graphics = graphics;
                this.graphics.addLayer(this);
                synchronized (this.graphics.list) {
                    for (Graphic graphic : this.graphics.list) {
                        graphic.addPropertyChangeListener(pcl);
                    }
                }
            }
        }
    }

    public void toBack(Graphic graphic) {
        if (graphics != null) {
            graphics.list.remove(graphic);
            graphics.list.add(0, graphic);
            repaint(graphic.getRepaintBounds(getAffineTransform()));
        }
    }

    public LayerModel getLayerModel() {
        return layerModel;
    }

    @Override
    public void setVisible(boolean flag) {
        this.visible = flag;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setLevel(int i) {
        level = i;
    }

    @Override
    public int getLevel() {
        return level;
    }

    protected AffineTransform getAffineTransform() {
        GraphicsPane graphicsPane = layerModel.getGraphicsPane();
        if (graphicsPane != null) {
            return graphicsPane.getAffineTransform();
        }
        return null;
    }

    public void removeGraphicAndRepaint(Graphic graphic) {
        if (graphics != null) {
            graphics.list.remove(graphic);
        }
        graphic.removePropertyChangeListener(pcl);
        repaint(graphic.getTransformedBounds(graphic.getShape(), getAffineTransform()));

        if (graphic.isSelected()) {
            getLayerModel().getSelectedGraphics().remove(graphic);
        }
    }

    public void removeGraphic(Graphic graphic) {
        if (graphics != null) {
            graphics.list.remove(graphic);
        }
        graphic.removePropertyChangeListener(pcl);
        if (graphic.isSelected()) {
            getLayerModel().getSelectedGraphics().remove(graphic);
        }
    }

    public java.util.List<Graphic> getGraphics() {
        return graphics.list;
    }

    public abstract List<Graphic> getGraphicsSurfaceInArea(Rectangle rect, AffineTransform transform);

    public abstract List<Graphic> getGraphicsSurfaceInArea(Rectangle rect, AffineTransform transform,
        boolean onlyFrontGraphic);

    public abstract List<Graphic> getGraphicsBoundsInArea(Rectangle rect);

    public abstract Graphic getGraphicContainPoint(MouseEventDouble mouseevent);

    public abstract List<Graphic> getGraphicListContainPoint(MouseEventDouble mouseevent);

    public abstract void paint(Graphics2D g2, AffineTransform transform, AffineTransform inverseTransform,
        Rectangle2D bound);

    // public abstract void paintSVG(SVGGraphics2D g2);

    @Override
    public int compareTo(Object obj) {
        int thisVal = this.getLevel();
        int anotherVal = ((AbstractLayer) obj).getLevel();
        return (thisVal < anotherVal ? -1 : (thisVal == anotherVal ? 0 : 1));
    }

    public void repaint(Rectangle rectangle) {
        if (rectangle != null) {
            layerModel.repaint(rectangle);
        }
    }

    public void repaint() {
        layerModel.repaint();
    }

    protected Rectangle rectangleUnion(Rectangle rectangle, Rectangle rectangle1) {
        if (rectangle == null) {
            return rectangle1;
        }
        return rectangle1 == null ? rectangle : rectangle.union(rectangle1);
    }

    protected void graphicBoundsChanged(Graphic graphic, Shape oldShape, Shape shape, AffineTransform transform) {
        if (graphic != null) {
            if (oldShape == null) {
                if (shape != null) {
                    Rectangle rect = graphic.getTransformedBounds(shape, transform);
                    if (rect != null) {
                        repaint(rect);
                    }
                }
            } else {
                if (shape == null) {
                    Rectangle rect = graphic.getTransformedBounds(oldShape, transform);
                    if (rect != null) {
                        repaint(rect);
                    }
                } else {
                    Rectangle rect =
                        rectangleUnion(graphic.getTransformedBounds(oldShape, transform),
                            graphic.getTransformedBounds(shape, transform));
                    if (rect != null) {
                        repaint(rect);
                    }
                }
            }
        }
    }

    protected void labelBoundsChanged(Graphic graphic, GraphicLabel oldLabel, GraphicLabel newLabel,
        AffineTransform transform) {

        if (graphic != null) {
            boolean oldNull = oldLabel == null || oldLabel.getLabels() == null;
            boolean newNull = newLabel == null || newLabel.getLabels() == null;
            if (oldNull) {
                if (!newNull) {
                    Rectangle2D rect = graphic.getTransformedBounds(newLabel, transform);
                    GeomUtil.growRectangle(rect, 2);
                    if (rect != null) {
                        repaint(rect.getBounds());
                    }
                }
            } else {
                if (newNull) {
                    Rectangle2D rect = graphic.getTransformedBounds(oldLabel, transform);
                    GeomUtil.growRectangle(rect, 2);
                    if (rect != null) {
                        repaint(rect.getBounds());
                    }
                } else {
                    Rectangle2D newRect = graphic.getTransformedBounds(newLabel, transform);
                    GeomUtil.growRectangle(newRect, 2);

                    Rectangle2D oldRect = graphic.getTransformedBounds(oldLabel, transform);
                    GeomUtil.growRectangle(oldRect, 2);

                    Rectangle rect = rectangleUnion(oldRect.getBounds(), newRect.getBounds());
                    if (rect != null) {
                        repaint(rect);
                    }
                }
            }
        }
    }

    @Override
    public Identifier getIdentifier() {
        return identifier;
    }

    public void deleteAllGraphic() {
        if (graphics != null) {
            if (graphics.getLayerSize() >= 0) {
                setGraphics(null);
            } else {
                for (int i = graphics.list.size() - 1; i >= 0; i--) {
                    removeGraphic(graphics.list.get(i));
                }
            }
            repaint();
        }
    }

    @Root
    public static class Identifier {
        @Attribute(name = "id")
        private final int defaultLevel;
        private final String title;

        public Identifier(int defaultLevel, String title) {
            this.defaultLevel = defaultLevel;
            this.title = title;
        }

        public int getDefaultLevel() {
            return defaultLevel;
        }

        public String getTitle() {
            return title;
        }

        @Override
        public String toString() {
            return title;
        }
    }
}
