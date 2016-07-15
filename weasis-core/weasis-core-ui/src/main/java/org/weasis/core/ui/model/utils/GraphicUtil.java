package org.weasis.core.ui.model.utils;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Toolkit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.media.jai.PlanarImage;
import javax.media.jai.TiledImage;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.util.LayoutUtil;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.Canvas;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.layer.GraphicLayer;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.layer.imp.DragLayer;

public class GraphicUtil {

    private GraphicUtil() {
    }

    public static GraphicLayer getOrBuildCrosslinesLayer(ViewCanvas canvas) {
        return canvas.getGraphicManager().findLayerByType(LayerType.CROSSLINES).orElseGet(() -> {
            GraphicLayer crossLayer = new DragLayer(LayerType.CROSSLINES);
            crossLayer.setLocked(true);
            return crossLayer;
        });
    }

    public static void addGraphicToModel(ViewCanvas canvas, Graphic graphic) {
        addGraphicToModel(canvas, null, graphic);
    }

    public static void addGraphicToModel(ViewCanvas canvas, GraphicLayer layer, Graphic graphic) {
        GraphicModel gm = canvas.getGraphicManager();
        graphic.updateLabel(Boolean.TRUE, canvas);
        graphic.addPropertyChangeListener(canvas.getGraphicsChangeHandler());
        if (layer == null) {
            layer = gm.findLayerByType(graphic.getLayerType()).orElse(new DragLayer(graphic.getLayerType()));
        }
        graphic.setLayer(layer);
        gm.addGraphic(graphic);
    }

    public static Graphic drawGraphic(ViewCanvas canvas) {
        Objects.requireNonNull(canvas);
        GraphicModel modelList = canvas.getGraphicManager();
        Objects.requireNonNull(modelList);

        Graphic newGraphic =
            Optional.ofNullable(modelList.getCreateGraphic()).orElse(MeasureToolBar.selectionGraphic);
        GraphicLayer layer = canvas.getGraphicManager().findLayerByType(newGraphic.getLayerType())
            .orElse(new DragLayer(newGraphic.getLayerType()));

        if (!layer.getVisible() || !(Boolean) canvas.getActionValue(ActionW.DRAW.cmd())) {
            JOptionPane.showMessageDialog(canvas.getJComponent(), Messages.getString("AbstractLayerModel.msg_not_vis"), //$NON-NLS-1$
                Messages.getString("AbstractLayerModel.draw"), //$NON-NLS-1$
                JOptionPane.ERROR_MESSAGE);
            return null;
        } else {
            Graphic graph = newGraphic.copy();
            graph.updateLabel(Boolean.TRUE, canvas);
            graph.addPropertyChangeListener(canvas.getGraphicsChangeHandler());
            graph.setLayer(layer);
            canvas.getGraphicManager().addGraphic(graph);
            return graph;
        }
    }

    public static Cursor getCustomCursor(String filename, String cursorName, int hotSpotX, int hotSpotY) {
        Toolkit defaultToolkit = Toolkit.getDefaultToolkit();
        ImageIcon icon = new ImageIcon(GraphicUtil.class.getResource("/icon/cursor/" + filename)); //$NON-NLS-1$
        Dimension bestCursorSize = defaultToolkit.getBestCursorSize(icon.getIconWidth(), icon.getIconHeight());
        Point hotSpot = new Point((hotSpotX * bestCursorSize.width) / icon.getIconWidth(),
            (hotSpotY * bestCursorSize.height) / icon.getIconHeight());
        return defaultToolkit.createCustomCursor(icon.getImage(), hotSpot, cursorName);
    }

    public static Cursor getNewCursor(int type) {
        return new Cursor(type);
    }

    public static PlanarImage getGraphicAsImage(Shape shape) {
        Rectangle bound = shape.getBounds();
        TiledImage image = new TiledImage(0, 0, bound.width + 1, bound.height + 1, 0, 0,
            LayoutUtil.createBinarySampelModel(), LayoutUtil.createBinaryIndexColorModel());
        Graphics2D g2d = image.createGraphics();
        g2d.translate(-bound.x, -bound.y);
        g2d.setPaint(Color.white);
        g2d.setStroke(new BasicStroke(1.0f));
        g2d.fill(shape);
        g2d.draw(shape);
        return image;
    }

    public static PlanarImage getGraphicsAsImage(Rectangle bound, List<Graphic> graphics2dlist) {
        TiledImage image = new TiledImage(0, 0, bound.width + 1, bound.height + 1, 0, 0,
            LayoutUtil.createBinarySampelModel(), LayoutUtil.createBinaryIndexColorModel());
        Graphics2D g2d = image.createGraphics();
        g2d.translate(-bound.x, -bound.y);
        g2d.setPaint(Color.white);
        g2d.setStroke(new BasicStroke(1.0f));
        for (Graphic graph : graphics2dlist) {
            g2d.fill(graph.getShape());
            g2d.draw(graph.getShape());
        }
        return image;
    }

    public static void repaint(Canvas canvas, Rectangle rectangle) {
        if (rectangle != null) {
            // Add the offset of the canvas
            double viewScale = canvas.getViewModel().getViewScale();
            int x = (int) (rectangle.x - canvas.getViewModel().getModelOffsetX() * viewScale);
            int y = (int) (rectangle.y - canvas.getViewModel().getModelOffsetY() * viewScale);
            canvas.getJComponent().repaint(new Rectangle(x, y, rectangle.width, rectangle.height));
        }
    }

    public static Rectangle getBounds(Canvas canvas) {
        return canvas.getJComponent().getBounds();
    }

}
