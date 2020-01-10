/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.model;

import java.awt.Graphics2D;
import java.awt.Rectangle;
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
import org.weasis.core.ui.model.layer.GraphicModelChangeListener;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.UUIDable;
import org.weasis.core.ui.util.MouseEventDouble;

public interface GraphicModel extends UUIDable {
    List<Graphic> getModels();

    void setModels(List<Graphic> models);

    void updateLabels(Object source, ViewCanvas<? extends ImageElement> view);

    void addGraphic(Graphic graphic);

    Optional<GraphicLayer> findLayerByType(LayerType layer);

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
    List<GraphicModelChangeListener> getChangeListeners();

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

    void removeGraphic(Graphic graphic);

    void addGraphicChangeHandler(PropertyChangeListener graphicsChangeHandler);

    void removeGraphicChangeHandler(PropertyChangeListener graphicsChangeHandler);

    List<GraphicLayer> getLayers();

    void setReferencedSeries(List<ReferencedSeries> referencedSeries);

    List<ReferencedSeries> getReferencedSeries();

    List<GraphicLayer> groupLayerByType();

    void deleteByLayer(GraphicLayer layer);

    void deleteNonSerializableGraphics();

    boolean hasSerializableGraphics();

    List<PropertyChangeListener> getGraphicsListeners();
}
