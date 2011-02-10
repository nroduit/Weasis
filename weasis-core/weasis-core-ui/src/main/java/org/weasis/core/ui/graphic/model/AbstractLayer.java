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
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.weasis.core.ui.graphic.AbstractDragGraphic;
import org.weasis.core.ui.graphic.Graphic;

/**
 * The Class AbstractLayer.
 * 
 * @author Nicolas Roduit
 */
public abstract class AbstractLayer implements Comparable, Serializable, Layer {

    private static final long serialVersionUID = -6113490831569841167L;

    private static final List<ShareGraphics> shareGraphicsManager = new ArrayList<ShareGraphics>();
    protected final PropertyChangeListener pcl;
    protected final transient ArrayList<LayerModel> canvas = new ArrayList<LayerModel>();
    private boolean masked;
    private int level;
    private final int drawType;
    private boolean originalExternalGraphics = false;
    protected volatile List<Graphic> graphics;

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
            if ("bounds".equals(s)) { //$NON-NLS-1$
                graphicBoundsChanged((Rectangle) propertychangeevent.getOldValue(),
                    (Rectangle) propertychangeevent.getNewValue());
            } else if ("add.graphic".equals(s)) { //$NON-NLS-1$
                addGraphic((Graphic) propertychangeevent.getNewValue());
            } else if ("move.graphic".equals(s)) { //$NON-NLS-1$
            } else if ("remove.graphic".equals(s)) { //$NON-NLS-1$
                removeGraphic((Graphic) propertychangeevent.getNewValue());
            } else if ("remove.repaint.graphic".equals(s)) { //$NON-NLS-1$
                removeGraphicAndRepaint((Graphic) propertychangeevent.getNewValue());
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

        private static final long serialVersionUID = -9094820911680205527L;

        private PropertyChangeHandler() {
        }
    }

    public AbstractLayer(LayerModel canvas1, int drawMode) {
        this.drawType = drawMode;
        this.canvas.add(canvas1);
        graphics = new ArrayList<Graphic>();
        pcl = new PropertyChangeHandler();
    }

    private ShareGraphics getShareGraphics(List<Graphic> graphics) {
        for (ShareGraphics g : shareGraphicsManager) {
            if (g.getGraphics() == graphics) {
                return g;
            }
            for (AbstractLayer layer : g.getLayers()) {
                if (layer.getGraphics() == graphics) {
                    return g;
                }
            }
        }
        return null;
    }

    private List<AbstractLayer> getShareLayers(List<Graphic> graphics) {
        ShareGraphics shareGraphics = getShareGraphics(graphics);
        if (shareGraphics != null) {
            return shareGraphics.getLayers();
        }
        return null;
    }

    public void addGraphic(Graphic graphic) {
        if (graphics != null && !graphics.contains(graphic)) {
            graphic.addPropertyChangeListener(pcl);
            graphics.add(graphic);

            List<AbstractLayer> layers = getShareLayers(graphics);
            if (layers != null) {
                for (AbstractLayer layer : layers) {
                    if (layer != this) {
                        try {
                            Graphic g = (Graphic) graphic.clone();
                            g.addPropertyChangeListener(layer.pcl);
                            layer.graphics.add(g);
                            layer.repaint(g.getRepaintBounds());
                        } catch (CloneNotSupportedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    public void toFront(Graphic graphic) {
        if (graphics != null) {
            graphics.remove(graphic);
            graphics.add(graphic);
        }
    }

    public synchronized void setGraphics(List<Graphic> graphics) {
        if (this.graphics != graphics) {
            unRegisterGraphics();
            if (graphics == null) {
                this.graphics = new ArrayList<Graphic>();
                originalExternalGraphics = false;
            } else {
                ShareGraphics shareGraphics = getShareGraphics(graphics);
                if (shareGraphics == null) {
                    shareGraphics = new ShareGraphics(graphics, null);
                    shareGraphicsManager.add(shareGraphics);
                }
                List<AbstractLayer> layers = shareGraphics.getLayers();
                if (layers.isEmpty()) {
                    for (Graphic graphic : graphics) {
                        graphic.addPropertyChangeListener(pcl);
                    }
                    this.graphics = graphics;
                    originalExternalGraphics = true;
                } else {
                    for (AbstractLayer layer : layers) {
                        if (layer.originalExternalGraphics) {
                            if (layer == this) {
                                return;
                            }
                            graphics = layer.getGraphics();
                        }
                    }

                    this.graphics = new ArrayList<Graphic>();
                    for (Graphic graphic : graphics) {
                        if (graphic instanceof AbstractDragGraphic) {
                            try {
                                Graphic g = (Graphic) ((AbstractDragGraphic) graphic).clone();
                                g.addPropertyChangeListener(pcl);
                                this.graphics.add(g);
                            } catch (CloneNotSupportedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    originalExternalGraphics = false;
                }
                layers.add(this);
            }
        }
    }

    private void unRegisterGraphics() {
        if (this.graphics != null) {
            ShareGraphics shareGraphics = getShareGraphics(graphics);
            if (shareGraphics != null) {
                List<AbstractLayer> layers = shareGraphics.getLayers();
                layers.remove(this);
                if (this.originalExternalGraphics && layers.size() > 0) {
                    AbstractLayer[] tempLayers = layers.toArray(new AbstractLayer[layers.size()]);
                    // Clear to set new graphics as it has no shared layers.
                    layers.clear();
                    tempLayers[0].setGraphics(this.graphics);
                    tempLayers[0].originalExternalGraphics = true;
                    for (AbstractLayer layer : tempLayers) {
                        layers.add(layer);
                    }
                } else {
                    for (Graphic g : graphics) {
                        g.removePropertyChangeListener(pcl);
                    }
                }
                if (layers.size() == 0) {
                    shareGraphicsManager.remove(shareGraphics);
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

    public void removeGraphicAndRepaint(Graphic graphic) {
        if (graphics != null) {
            graphics.remove(graphic);
        }
        graphic.removePropertyChangeListener(pcl);
        if (graphic instanceof AbstractDragGraphic) {
            repaint(((AbstractDragGraphic) graphic).getTransformedBounds());
        } else {
            repaint(graphic.getRepaintBounds());
        }
        // graphic.setLayer(null);
        if (graphic.isSelected()) {
            getShowDrawing().getSelectedGraphics().remove(graphic);
        }
    }

    public void removeGraphic(Graphic graphic) {
        List<AbstractLayer> layers = getShareLayers(graphics);
        if (layers != null) {
            for (AbstractLayer layer : layers) {
                removeGraphic(layer, graphic, layer == this);
            }
        } else {
            removeGraphic(this, graphic, true);
        }
    }

    private void removeGraphic(AbstractLayer layer, Graphic graphic, boolean deselect) {
        List<Graphic> graphics = layer.getGraphics();
        if (graphics != null) {
            graphics.remove(graphic);
        }
        graphic.removePropertyChangeListener(layer.pcl);
        if (deselect && graphic.isSelected()) {
            getShowDrawing().getSelectedGraphics().remove(graphic);
        }
    }

    public java.util.List<Graphic> getGraphics() {
        return graphics;
    }

    public abstract java.util.List<Graphic> getGraphicsSurfaceInArea(Rectangle rect);

    public abstract java.util.List<Graphic> getGraphicsBoundsInArea(Rectangle rect);

    public abstract Graphic getGraphicContainPoint(Point pos);

    public abstract void paint(Graphics2D g2, AffineTransform transform, AffineTransform inverseTransform,
        Rectangle bound);

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

    protected void graphicBoundsChanged(Rectangle rectangle, Rectangle rectangle1) {
        if (rectangle == null) {
            if (rectangle1 != null) {
                repaint(rectangle1);
            }
        } else if (rectangle1 != null) {
            repaint(rectangle.union(rectangle1));
        }
    }

    public int getDrawType() {
        return drawType;
    }

    public void deleteAllGraphic() {
        if (graphics != null) {
            for (int i = graphics.size() - 1; i >= 0; i--) {
                removeGraphic(graphics.get(i));
            }
        }
        repaint();
    }

    static class ShareGraphics {
        private final List<AbstractLayer> layers;
        private final List<Graphic> graphics;

        public ShareGraphics(List<Graphic> graphics, List<AbstractLayer> layers) {
            if (graphics == null) {
                throw new IllegalArgumentException("graphics parameter cannot be null");
            }
            this.graphics = graphics;
            this.layers = layers == null ? new ArrayList<AbstractLayer>() : layers;
        }

        public List<AbstractLayer> getLayers() {
            return layers;
        }

        public List<Graphic> getGraphics() {
            return graphics;
        }

    }

}
