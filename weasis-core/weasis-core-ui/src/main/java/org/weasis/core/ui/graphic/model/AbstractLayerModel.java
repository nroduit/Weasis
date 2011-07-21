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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.media.jai.PlanarImage;
import javax.media.jai.TiledImage;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.util.LayoutUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.graphic.AbstractDragGraphic;
import org.weasis.core.ui.graphic.DragLayer;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.GraphicClipboard;
import org.weasis.core.ui.graphic.SelectGraphic;
import org.weasis.core.ui.graphic.TempLayer;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * @author Nicolas Roduit,Benoit Jacquemoud
 */

public class AbstractLayerModel implements LayerModel {
    public static final GraphicClipboard GraphicClipboard = new GraphicClipboard();

    public static final Cursor DEFAULT_CURSOR = new Cursor(Cursor.DEFAULT_CURSOR);
    public static final Cursor HAND_CURSOR = getCustomCursor("hand.gif", "hand", 16, 16); //$NON-NLS-1$ //$NON-NLS-2$
    // public static final Cursor HAND_CURSOR = new Cursor(Cursor.HAND_CURSOR); // ??? why not uses system cursor
    public static final Cursor EDIT_CURSOR = getCustomCursor("editpoint.png", "Edit Point", 16, 16); //$NON-NLS-1$ //$NON-NLS-2$
    public static final Cursor MOVE_CURSOR = new Cursor(Cursor.MOVE_CURSOR);
    public static final Cursor CROSS_CURSOR = new Cursor(Cursor.CROSSHAIR_CURSOR);
    public static final Cursor WAIT_CURSOR = new Cursor(Cursor.WAIT_CURSOR);

    protected Cursor cursor = DEFAULT_CURSOR;

    protected final GraphicsPane canvas;
    private final ArrayList<AbstractLayer> layers;

    private SelectGraphic selectGraphic;
    private final ArrayList<Graphic> selectedGraphicList;
    private final ArrayList<GraphicsListener> selectedGraphicsListener;
    private Graphic createGraphic;
    private final ArrayList<LayerModelChangeListener> listenerList;
    private boolean layerModelChangeFireingSuspended;

    private float alpha;

    public final Object antialiasingOn = RenderingHints.VALUE_ANTIALIAS_ON;
    public final Object antialiasingOff = RenderingHints.VALUE_ANTIALIAS_OFF;
    private Object antialiasing;

    private final boolean crossHairMode = false;

    public AbstractLayerModel(GraphicsPane canvas) {
        this.canvas = canvas;
        layers = new ArrayList<AbstractLayer>();
        selectedGraphicList = new ArrayList<Graphic>();

        listenerList = new ArrayList<LayerModelChangeListener>();
        selectedGraphicsListener = new ArrayList<GraphicsListener>();
        setAlpha(0f);
        setAntialiasing(false);
    }

    public AbstractDragGraphic createDragGraphic(MouseEventDouble mouseevent) {
        Graphic newGraphic = getCreateGraphic();
        Tools tool = null;

        if (newGraphic == null) {
            newGraphic = MeasureToolBar.selectionGraphic;
        }
        if (newGraphic instanceof SelectGraphic) {
            tool = Tools.TEMPDRAGLAYER;
        } else if (newGraphic instanceof AbstractDragGraphic) {
            tool = Tools.MEASURE;
        } else
            return null;

        newGraphic = ((AbstractDragGraphic) newGraphic).clone();

        AbstractLayer layer = getLayer(tool);
        if (layer != null) {
            if (!layer.isVisible() || !(Boolean) canvas.getActionValue(ActionW.DRAW.cmd())) {
                JOptionPane
                    .showMessageDialog(
                        canvas,
                        Messages.getString("AbstractLayerModel.msg_not_vis"), Messages.getString("AbstractLayerModel.draw"), //$NON-NLS-1$ //$NON-NLS-2$
                        JOptionPane.ERROR_MESSAGE);
                return null;
            }
            layer.addGraphic(newGraphic);
        }

        return (AbstractDragGraphic) newGraphic;

    }

    @Override
    public void repaint() {
        // repaint du composant ImageDisplay
        canvas.repaint();
    }

    @Override
    public void repaint(Rectangle rectangle) {
        if (rectangle != null) {
            // Add the offset of the canvas
            double viewScale = canvas.getViewModel().getViewScale();
            int x = (int) (rectangle.x - canvas.getViewModel().getModelOffsetX() * viewScale);
            int y = (int) (rectangle.y - canvas.getViewModel().getModelOffsetY() * viewScale);
            canvas.repaint(new Rectangle(x, y, rectangle.width, rectangle.height));
        }
    }

    public static Cursor getCustomCursor(String filename, String cursorName, int hotSpotX, int hotSpotY) {
        Toolkit defaultToolkit = Toolkit.getDefaultToolkit();
        ImageIcon icon = new ImageIcon(AbstractLayerModel.class.getResource("/icon/cursor/" + filename)); //$NON-NLS-1$
        Dimension bestCursorSize = defaultToolkit.getBestCursorSize(icon.getIconWidth(), icon.getIconHeight());
        Point hotSpot =
            new Point((hotSpotX * bestCursorSize.width) / icon.getIconWidth(), (hotSpotY * bestCursorSize.height)
                / icon.getIconHeight());
        return defaultToolkit.createCustomCursor(icon.getImage(), hotSpot, cursorName);
    }

    public void setCursor(Cursor cursor) {
        this.cursor = cursor;
        canvas.setCursor(this.cursor);
    }

    public void resetCursor() {
        setCursor(DEFAULT_CURSOR);
        // this.cursor = DEFAULT_CURSOR;
        // canvas.setCursor(cursor);
    }

    private void layerVisibilityChanged(AbstractLayer layer) {
        repaint();
    }

    public void addSelectedGraphic(Graphic graphic) {
        if (graphic != null) {
            if (!selectedGraphicList.contains(graphic)) {
                selectedGraphicList.add(graphic);
            }
            graphic.setSelected(true);
        }
    }

    public void setSelectedGraphic(Graphic graphic) {
        List<Graphic> singleList = new ArrayList<Graphic>(1);
        if (graphic != null) {
            singleList.add(graphic);
        }
        setSelectedGraphics(singleList);
    }

    @Override
    public void setSelectedGraphics(List<Graphic> list) {
        for (int i = selectedGraphicList.size() - 1; i >= 0; i--) {
            Graphic graphic = selectedGraphicList.get(i);
            if (list == null || !list.contains(graphic)) {
                graphic.setSelected(false);
            }
        }
        selectedGraphicList.clear();
        if (list != null) {
            selectedGraphicList.addAll(list);
            for (int j = selectedGraphicList.size() - 1; j >= 0; j--) {
                selectedGraphicList.get(j).setSelected(true);
            }
        }

    }

    @Override
    public ArrayList<Graphic> getSelectedGraphics() {
        return selectedGraphicList;
    }

    @Override
    public Rectangle getBounds() {
        return canvas.getBounds();
    }

    @Override
    public GraphicsPane getGraphicsPane() {
        return canvas;
    }

    public void repaintWithRelativeCoord(Rectangle rectangle) {
        canvas.repaint(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
    }

    @Override
    public void addLayer(AbstractLayer layer) {
        layers.add(layer);
        layer.setShowDrawing(this);
    }

    @Override
    public void removeLayer(AbstractLayer layer) {
        layers.remove(layer);
        repaint();
    }

    public void setLayers(List<AbstractLayer> list) {
        for (int i = layers.size() - 1; i >= 0; i--) {
            layers.remove(i);
        }
        if (list != null) {
            for (int j = list.size() - 1; j >= 0; j--) {
                addLayer(list.get(j));
            }
        }
    }

    public AbstractLayer[] getLayers() {
        return layers.toArray(new AbstractLayer[layers.size()]);
    }

    public void setSelectGraphic(SelectGraphic selectGraphic) {
        this.selectGraphic = selectGraphic;
    }

    public SelectGraphic getSelectGraphic() {
        return selectGraphic;
    }

    public List<AbstractDragGraphic> getSelectedDragableGraphics() {
        if (selectedGraphicList == null || selectedGraphicList.size() == 0)
            return null;

        List<AbstractDragGraphic> selectedDragGraphics = new ArrayList<AbstractDragGraphic>(selectedGraphicList.size());

        for (Graphic graphic : selectedGraphicList) {
            if (graphic instanceof AbstractDragGraphic) {
                selectedDragGraphics.add((AbstractDragGraphic) graphic);
            }
        }
        return selectedDragGraphics;
    }

    public List<Graphic> getSelectedAllGraphicsIntersecting(Rectangle rectangle, AffineTransform transform) {
        ArrayList<Graphic> selectedGraphicList = new ArrayList<Graphic>();
        for (int i = layers.size() - 1; i >= 0; i--) {
            AbstractLayer layer = layers.get(i);
            if (layer.isVisible()) {
                selectedGraphicList.addAll(layer.getGraphicsSurfaceInArea(rectangle, transform));
            }
        }
        return selectedGraphicList;
    }

    public List<Graphic> getSelectedAllGraphicsIntersecting(Rectangle rectangle, AffineTransform transform,
        boolean onlyFrontGraphic) {
        ArrayList<Graphic> selectedGraphicList = new ArrayList<Graphic>();
        for (int i = layers.size() - 1; i >= 0; i--) {
            AbstractLayer layer = layers.get(i);
            if (layer.isVisible()) {
                selectedGraphicList.addAll(layer.getGraphicsSurfaceInArea(rectangle, transform, onlyFrontGraphic));
            }
        }
        return selectedGraphicList;
    }

    public List<Graphic> getAllGraphics() {
        ArrayList<Graphic> arraylist = new ArrayList<Graphic>();
        for (int i = layers.size() - 1; i >= 0; i--) {
            AbstractLayer layer = layers.get(i);
            if (layer.isVisible()) {
                arraylist.addAll(layer.getGraphics());
            }
        }
        return arraylist;
    }

    public Graphic getFirstGraphicIntersecting(MouseEventDouble mouseevent) {
        for (int i = layers.size() - 1; i >= 0; i--) {
            AbstractLayer layer = layers.get(i);
            if (layer.isVisible()) {
                Graphic graph = layer.getGraphicContainPoint(mouseevent);
                if (graph != null)
                    return graph;
            }
        }
        return null;
    }

    public void deleteAllGraphics() {
        final AbstractLayer[] layerList = getLayers();
        final int n = layerList.length;
        for (int i = n - 1; i >= 0; i--) {
            layerList[i].deleteAllGraphic();
        }
    }

    public void deleteSelectedGraphics(boolean warningMessage) {
        java.util.List<Graphic> list = getSelectedGraphics();
        if (list != null && list.size() > 0) {
            int response = 0;
            if (warningMessage) {
                response =
                    JOptionPane
                        .showConfirmDialog(
                            canvas,
                            String.format(Messages.getString("AbstractLayerModel.del_conf"), list.size()), Messages.getString("AbstractLayerModel.del_graphs"), //$NON-NLS-1$ //$NON-NLS-2$
                            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            }
            if (response == 0) {
                java.util.List<Graphic> selectionList = new ArrayList<Graphic>(list);
                for (Graphic graphic : selectionList) {
                    graphic.fireRemoveAction();
                }
                repaint();
            }
        }
    }

    public static PlanarImage getGraphicAsImage(Shape shape) {
        Rectangle bound = shape.getBounds();
        TiledImage image =
            new TiledImage(0, 0, bound.width + 1, bound.height + 1, 0, 0, LayoutUtil.createBinarySampelModel(),
                LayoutUtil.createBinaryIndexColorModel());
        Graphics2D g2d = image.createGraphics();
        g2d.translate(-bound.x, -bound.y);
        g2d.setPaint(Color.white);
        g2d.setStroke(new BasicStroke(1.0f));
        g2d.fill(shape);
        g2d.draw(shape);
        return image;
    }

    @Override
    public int getLayerCount() {
        return layers.size();
    }

    // public Rectangle2D getVisibleBoundingBox(Rectangle2D r) {
    // return naewin.getSource().getBounds();
    // }

    @Override
    public void draw(Graphics2D g2d, AffineTransform transform, AffineTransform inverseTransform) {
        Rectangle2D bound = null;
        // Get the visible view in real coordinates
        Shape area = inverseTransform.createTransformedShape(g2d.getClipBounds());
        bound = area.getBounds2D();
        g2d.translate(0.5, 0.5);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasingOn);
        // g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        for (int i = 0; i < layers.size(); i++) {
            AbstractLayer layer = layers.get(i);
            if (layer.isVisible()) {
                layer.paint(g2d, transform, inverseTransform, bound);
            }
        }
        // g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasingOff);
        g2d.translate(-0.5, -0.5);
    }

    @Override
    public void dispose() {
        final AbstractLayer[] layerList = getLayers();
        layers.clear();
        listenerList.clear();
        selectedGraphicsListener.clear();
        final int n = layerList.length;
        for (int i = n - 1; i >= 0; i--) {
            layerList[i].deleteAllGraphic();
        }
    }

    @Override
    public boolean isLayerModelChangeFireingSuspended() {
        return layerModelChangeFireingSuspended;
    }

    @Override
    public void setLayerModelChangeFireingSuspended(boolean layerModelChangeFireingSuspended) {
        this.layerModelChangeFireingSuspended = layerModelChangeFireingSuspended;
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    public void setAntialiasing(boolean antialiasing) {
        this.antialiasing = antialiasing ? antialiasingOn : antialiasingOff;
    }

    /**
     * Gets all layer manager listeners of this layer.
     */
    @Override
    public LayerModelChangeListener[] getLayerModelChangeListeners() {
        return listenerList.toArray(new LayerModelChangeListener[listenerList.size()]);
    }

    /**
     * Adds a layer manager listener to this layer.
     */
    @Override
    public void addLayerModelChangeListener(LayerModelChangeListener listener) {
        if (listener != null && !listenerList.contains(listener)) {
            listenerList.add(listener);
        }
    }

    /**
     * Removes a layer manager listener from this layer.
     */
    @Override
    public void removeLayerModelChangeListener(LayerModelChangeListener listener) {
        if (listener != null) {
            listenerList.remove(listener);
        }
    }

    @Override
    public void fireLayerModelChanged() {
        if (!isLayerModelChangeFireingSuspended()) {
            for (int i = 0; i < listenerList.size(); i++) {
                (listenerList.get(i)).handleLayerModelChanged(this);
            }
        }
    }

    public GraphicsListener[] getGraphicSelectionListeners() {
        return selectedGraphicsListener.toArray(new GraphicsListener[selectedGraphicsListener.size()]);
    }

    public void addGraphicSelectionListener(GraphicsListener listener) {
        if (listener != null && !selectedGraphicsListener.contains(listener)) {
            selectedGraphicsListener.add(listener);
        }
    }

    public void removeGraphicSelectionListener(GraphicsListener listener) {
        if (listener != null) {
            selectedGraphicsListener.remove(listener);
        }
    }

    public void fireGraphicsSelectionChanged(ImageElement img) {
        for (int i = 0; i < selectedGraphicsListener.size(); i++) {
            (selectedGraphicsListener.get(i)).handle((List<Graphic>) selectedGraphicList.clone(), img);
        }

    }

    public static PlanarImage getGraphicsAsImage(Rectangle bound, List<Graphic> graphics2dlist) {
        TiledImage image =
            new TiledImage(0, 0, bound.width + 1, bound.height + 1, 0, 0, LayoutUtil.createBinarySampelModel(),
                LayoutUtil.createBinaryIndexColorModel());
        Graphics2D g2d = image.createGraphics();
        g2d.translate(-bound.x, -bound.y);
        g2d.setPaint(Color.white);
        g2d.setStroke(new BasicStroke(1.0f));
        for (int i = 0; i < graphics2dlist.size(); i++) {
            Graphic graph = graphics2dlist.get(i);
            g2d.fill(graph.getShape());
            g2d.draw(graph.getShape());
        }
        return image;
    }

    public void setActiveLayer(int drawType) {
        Tools.setLevelToLayers(layers);
        for (int j = layers.size() - 1; j >= 0; j--) {
            AbstractLayer layerTemp = layers.get(j);
            if (layerTemp.getDrawType() == drawType) {
                layerTemp.setLevel(9);
            }
        }
        Collections.sort(layers);
        repaint();
    }

    public AbstractLayer getLayer(Tools tool) {
        if (tool.isLayer())
            return getLayer(tool.getId());
        return getLayer(Tools.TEMPDRAGLAYER);
    }

    @Override
    public AbstractLayer getLayer(int drawType) {
        for (int j = layers.size() - 1; j >= 0; j--) {
            AbstractLayer layerTemp = layers.get(j);
            if (layerTemp.getDrawType() == drawType)
                return layerTemp;
        }
        return getLayer(Tools.TEMPDRAGLAYER);
    }

    public DragLayer getMeasureLayer() {
        return (DragLayer) getLayer(Tools.MEASURE);
    }

    public DragLayer getNoteLayer() {
        return (DragLayer) getLayer(Tools.NOTE);
    }

    public TempLayer getTemporyDragLayer() {
        return (TempLayer) getLayer(Tools.TEMPDRAGLAYER);
    }

    //
    // public ObjectsLayer getObjectExtractLayer() {
    // return (ObjectsLayer) getLayer(Tools.OBJECTEXTRACT);
    // }
    //
    // public TempClassLayer getTemporyClassifLayer() {
    // return (TempClassLayer) getLayer(Tools.TEMPCLASSIFLAYER);
    // }
    //
    // public RoiLayer getRoiLayer() {
    // return (RoiLayer) getLayer(Tools.DELIMIT);
    // }

    // public void paintSVG(SVGGraphics2D g2) {
    // for (int i = 0; i < layers.size(); i++) {
    // AbstractLayer layer = layers.get(i);
    // if (layer.isVisible() && layer.getDrawType() != Tools.TEMPCLASSIFLAYER.getId()) {
    // String name = Tools.getToolName(layer.getDrawType());
    // g2.writeStartGroup(name, false);
    // layer.paintSVG(g2);
    // g2.writeEndGroup(name);
    // }
    // }
    // }

    public void setCreateGraphic(Graphic graphic) {
        createGraphic = graphic;
    }

    public Graphic getCreateGraphic() {
        return createGraphic;
    }

}
