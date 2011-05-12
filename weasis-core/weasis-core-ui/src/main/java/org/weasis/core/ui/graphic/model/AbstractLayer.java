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
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.util.ArrayList;

import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.GraphicLabel;

/**
 * The Class AbstractLayer.
 * 
 * @author Nicolas Roduit
 */
public abstract class AbstractLayer implements Comparable, Serializable, Layer {

    private static final long serialVersionUID = -6113490831569841167L;

    protected final PropertyChangeListener pcl;
    protected final transient ArrayList<LayerModel> canvas = new ArrayList<LayerModel>();
    private boolean masked;
    private int level;
    private final int drawType;
    protected volatile GraphicList graphics;

    /**
     * The Class PropertyChangeHandler.
     * 
     * @author Nicolas Roduit
     */
    class PropertyChangeHandler implements PropertyChangeListener, Serializable {

        // This method gets called when a bound property is changed, inherite by PropertyChangeListener
        public void propertyChange(PropertyChangeEvent propertychangeevent) {
            Object obj = propertychangeevent.getSource();
            String s = propertychangeevent.getPropertyName();
            if (obj instanceof Graphic) {
                Graphic graph = (Graphic) obj;
                if ("bounds".equals(s)) { //$NON-NLS-1$
                    graphicBoundsChanged(graph, (Shape) propertychangeevent.getOldValue(),
                        (Shape) propertychangeevent.getNewValue(), getAffineTransform());
                } else if ("graphicLabel".equals(s)) { //$NON-NLS-1$
                    labelBoundsChanged(graph, (Rectangle) propertychangeevent.getOldValue(),
                        (GraphicLabel) propertychangeevent.getNewValue(), getAffineTransform());
                } else if ("remove".equals(s)) { //$NON-NLS-1$
                    removeGraphic(graph);
                } else if ("remove.repaint".equals(s)) { //$NON-NLS-1$
                    removeGraphicAndRepaint(graph);
                }
                // pour toutes les autres propriétés des graphic : "selected", "shape", "intersectshape"
                else {
                    // if (obj instanceof AbstractDragGraphic) {
                    // repaint(((AbstractDragGraphic) obj).getRepaintBounds());
                    // }
                    // else {
                    // repaint(((Graphic) obj).getRepaintBounds());
                    // }
                }
            }
        }

        // version 1.0 n'avait pas de UID
        // protected static final long serialVersionUID = -9094820911680205527L;
        private static final long serialVersionUID = -9094820911680205527L;

        private PropertyChangeHandler() {
        }
    }

    public AbstractLayer(LayerModel canvas1, int drawMode) {
        this.drawType = drawMode;
        this.canvas.add(canvas1);
        graphics = new GraphicList();
        pcl = new PropertyChangeHandler();
    }

    public void addGraphic(Graphic graphic) {
        if (graphics != null && !graphics.contains(graphic)) {
            // graphic.setSelected(false);
            graphics.add(graphic);
            graphic.addPropertyChangeListener(pcl);
            ArrayList<AbstractLayer> layers = graphics.getLayers();
            if (layers != null) {
                for (AbstractLayer layer : layers) {
                    graphic.addPropertyChangeListener(layer.pcl);
                    layer.repaint(graphic.getRepaintBounds());
                }
            }
            // repaint(graphic.getRepaintBounds());
        }
    }

    public void toFront(Graphic graphic) {
        if (graphics != null) {
            graphics.remove(graphic);
            graphics.add(graphic);
        }
        // repaint(graphic.getv);
    }

    public synchronized void setGraphics(GraphicList graphics) {
        if (this.graphics != graphics) {
            if (this.graphics != null) {
                this.graphics.removeLayer(this);
                for (Graphic graphic : this.graphics) {
                    graphic.removePropertyChangeListener(pcl);
                }
                getShowDrawing().setSelectedGraphics(null);
            }
            if (graphics == null) {
                this.graphics = new GraphicList();
            } else {
                this.graphics = graphics;
                this.graphics.addLayer(this);
                for (Graphic graphic : this.graphics) {
                    graphic.addPropertyChangeListener(pcl);
                }
            }
        }
    }

    public void toBack(Graphic graphic) {
        if (graphics != null) {
            graphics.remove(graphic);
            graphics.add(0, graphic);
        }
        // repaint(graphic.getRepaintBounds());
    }

    public void setShowDrawing(LayerModel canvas1) {
        if (canvas != null) {
            if (!canvas.contains(canvas1)) {
                this.canvas.add(canvas1);
            }
        }
    }

    public LayerModel getShowDrawing() {
        return canvas.get(0);
    }

    public void setVisible(boolean flag) {
        this.masked = !flag;
    }

    public boolean isVisible() {
        return !masked;
    }

    public void setLevel(int i) {
        level = i;
    }

    public int getLevel() {
        return level;
    }

    private AffineTransform getAffineTransform() {
        LayerModel layerModel = getShowDrawing();
        if (layerModel != null) {
            GraphicsPane graphicsPane = layerModel.getGraphicsPane();
            if (graphicsPane != null) {
                return graphicsPane.getAffineTransform();
            }
        }
        return null;
    }

    public void removeGraphicAndRepaint(Graphic graphic) {
        if (graphics != null) {
            graphics.remove(graphic);
        }
        graphic.removePropertyChangeListener(pcl);
        repaint(graphic.getTransformedBounds(graphic.getShape(), getAffineTransform()));

        if (graphic.isSelected()) {
            getShowDrawing().getSelectedGraphics().remove(graphic);
        }
    }

    public void removeGraphic(Graphic graphic) {
        if (graphics != null) {
            graphics.remove(graphic);
        }
        graphic.removePropertyChangeListener(pcl);
        if (graphic.isSelected()) {
            getShowDrawing().getSelectedGraphics().remove(graphic);
        }
    }

    public java.util.List<Graphic> getGraphics() {
        return graphics;
    }

    public abstract java.util.List<Graphic> getGraphicsSurfaceInArea(Rectangle rect, AffineTransform transform);

    public abstract java.util.List<Graphic> getGraphicsBoundsInArea(Rectangle rect);

    public abstract Graphic getGraphicContainPoint(MouseEvent mouseevent);

    public abstract void paint(Graphics2D g2, AffineTransform transform, AffineTransform inverseTransform,
        Rectangle2D bound);

    // public abstract void paintSVG(SVGGraphics2D g2);

    // interface comparable, permet de trier par ordre croissant les layers
    public int compareTo(Object obj) {
        int thisVal = this.getLevel();
        int anotherVal = ((AbstractLayer) obj).getLevel();
        return (thisVal < anotherVal ? -1 : (thisVal == anotherVal ? 0 : 1));
    }

    public void repaint(Rectangle rectangle) {
        for (int i = 0; i < canvas.size(); i++) {
            canvas.get(i).repaint(rectangle);
        }
    }

    public void repaint() {
        for (int i = 0; i < canvas.size(); i++) {
            canvas.get(i).repaint();
        }
    }

    protected Rectangle rectangleUnion(Rectangle rectangle, Rectangle rectangle1) {
        if (rectangle == null) {
            return rectangle1;
        }
        return rectangle1 == null ? rectangle : rectangle.union(rectangle1);
    }

    protected void graphicBoundsChanged(Graphic graphic, Shape oldShape, Shape shape, AffineTransform affineTransform) {
        if (oldShape == null) {
            if (shape != null) {
                Rectangle rect = graphic.getTransformedBounds(shape, affineTransform);
                if (rect != null) {
                    repaint(rect);
                }
            }
        } else if (shape != null) {
            Rectangle rect =
                rectangleUnion(graphic.getTransformedBounds(oldShape, affineTransform),
                    graphic.getTransformedBounds(shape, affineTransform));
            if (rect != null) {
                repaint(rect);
            }
        }
    }

    private void transformLabelBound(Rectangle shape, AffineTransform affineTransform, GraphicLabel label) {
        if (affineTransform != null) {
            Point2D.Double p = new Point2D.Double(shape.getX(), shape.getY());
            affineTransform.transform(p, p);
            shape.x = (int) (p.x + label.getOffsetX());
            shape.y = (int) (p.y + label.getOffsetY());
        }
    }

    protected void labelBoundsChanged(Graphic graphic, Rectangle oldShape, GraphicLabel label,
        AffineTransform affineTransform) {
        if (label != null) {
            Rectangle bound = label.getBound();
            if (oldShape == null && bound != null) {
                transformLabelBound(bound, affineTransform, label);
                bound.grow(2, 2);
                repaint(bound);

            } else if (bound != null) {
                // Get new instance to avoid changing the oldShape for other for other layers
                oldShape = oldShape.getBounds();
                transformLabelBound(oldShape, affineTransform, label);
                transformLabelBound(bound, affineTransform, label);
                Rectangle rect = rectangleUnion(oldShape, bound);
                if (rect != null) {
                    rect.grow(2, 2);
                    repaint(rect);
                }
            }

        }
    }

    public int getDrawType() {
        return drawType;
    }

    public void deleteAllGraphic() {
        if (graphics != null) {
            if (graphics.getLayerSize() >= 0) {
                setGraphics(null);
            } else {
                for (int i = graphics.size() - 1; i >= 0; i--) {
                    removeGraphic(graphics.get(i));
                }
            }
            repaint();
        }
    }

}
