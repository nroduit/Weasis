package org.weasis.core.ui.model;

import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Optional;

import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.Canvas;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.DragGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicSelectionListener;
import org.weasis.core.ui.model.graphic.imp.area.SelectGraphic;
import org.weasis.core.ui.model.layer.GraphicLayer;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.layer.GraphicModelChangeListener;
import org.weasis.core.ui.model.utils.GraphicUtil;
import org.weasis.core.ui.model.utils.UUIDable;
import org.weasis.core.ui.model.utils.bean.GraphicClipboard;
import org.weasis.core.ui.util.MouseEventDouble;

public interface GraphicModel extends UUIDable {
    public static final GraphicClipboard GRAPHIC_CLIPBOARD = new GraphicClipboard();

    public static final Cursor DEFAULT_CURSOR = GraphicUtil.getNewCursor(Cursor.DEFAULT_CURSOR);
    public static final Cursor MOVE_CURSOR = GraphicUtil.getNewCursor(Cursor.MOVE_CURSOR);
    public static final Cursor CROSS_CURSOR = GraphicUtil.getNewCursor(Cursor.CROSSHAIR_CURSOR);
    public static final Cursor WAIT_CURSOR = GraphicUtil.getNewCursor(Cursor.WAIT_CURSOR);

    public static final Cursor HAND_CURSOR = GraphicUtil.getCustomCursor("hand.gif", "hand", 16, 16); //$NON-NLS-1$ //$NON-NLS-2$
    public static final Cursor EDIT_CURSOR = GraphicUtil.getCustomCursor("editpoint.png", "Edit Point", 16, 16); //$NON-NLS-1$ //$NON-NLS-2$

    public static final Object antialiasingOn = RenderingHints.VALUE_ANTIALIAS_ON;
    public static final Object antialiasingOff = RenderingHints.VALUE_ANTIALIAS_OFF;

    List<Graphic> getModels();

    void setModels(List<Graphic> models);

    void updateLabels(Object source, ViewCanvas<? extends ImageElement> view);

    void addGraphic(Graphic graphic);

    Optional<GraphicLayer> findLayerByType(LayerType tempdraglayer);

    void deleteByLayerType(LayerType type);

    List<Graphic> getAllGraphics();

    List<Graphic> getSelectedAllGraphicsIntersecting(Rectangle rectangle, AffineTransform transform);

    List<Graphic> getSelectedAllGraphicsIntersecting(Rectangle rectangle, AffineTransform transform,
        boolean onlyFrontGraphic);

    Optional<Graphic> getFirstGraphicIntersecting(MouseEventDouble mouseevent);

    List<DragGraphic> getSelectedDragableGraphics();

    List<Graphic> getSelectedGraphics();

    Optional<SelectGraphic> getSelectGraphic();

    void deleteSelectedGraphics(Canvas canvas, Boolean warningMessage);

    void setSelectedGraphic(List<Graphic> graphics);

    void clear();

    void setSelectedAllGraphics();

    void fireGraphicsSelectionChanged(MeasurableLayer layer);

    /**
     * Draws all visible layers of this model.
     *
     * @param g2d
     *            the 2D graphics context
     * @param transform
     * @param clip
     */
    void draw(Graphics2D g2d, AffineTransform transform, AffineTransform inverseTransform, Rectangle2D viewClip);

    List<GraphicSelectionListener> getGraphicSelectionListeners();

    void addGraphicSelectionListener(GraphicSelectionListener listener);

    void removeGraphicSelectionListener(GraphicSelectionListener listener);

    /**
     * Releases all resources held by this model. Method calls to this model after <code>dispose</code> has been called,
     * are undefined.
     */
    void dispose();

    /**
     * Gets all layer manager listeners of this layer.
     */
    GraphicModelChangeListener[] getChangeListeners();

    /**
     * Adds a layer manager listener to this layer.
     */
    void addChangeListener(GraphicModelChangeListener listener);

    /**
     * Removes a layer manager listener from this layer.
     */
    void removeChangeListener(GraphicModelChangeListener listener);

    void fireChanged();

    Boolean isChangeFireingSuspended();

    void setChangeFireingSuspended(Boolean change);

    int getLayerCount();

    void setCreateGraphic(Graphic graphic);

    Graphic getCreateGraphic();

    void removeGraphic(Graphic graphic);

    void addGraphicChangeHandler(PropertyChangeListener graphicsChangeHandler);

    void removeGraphicChangeHandler(PropertyChangeListener graphicsChangeHandler);

    List<GraphicLayer> getLayers();

    void setReferencedSeries(List<ReferencedSeries> referencedSeries);

    List<ReferencedSeries> getReferencedSeries();

    List<GraphicLayer> groupLayerByType();
}
