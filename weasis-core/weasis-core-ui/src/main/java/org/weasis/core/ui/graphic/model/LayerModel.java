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
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

import org.weasis.core.ui.graphic.Graphic;

/**
 * The Interface LayerModel.
 * 
 * @author Nicolas Roduit
 */
public interface LayerModel {

    /**
     * Gets the total number of layers in this layer model.
     * 
     * @return the number of layers
     */
    int getLayerCount();

    /**
     * Gets the layer at the specified index.
     * 
     * @param index
     *            the zero-based layer index
     * @return the layer at the given index, never null
     */
    AbstractLayer getLayer(int index);

    /**
     * Adds a new layer to this model.
     * 
     * @param layer
     *            the new layer, must not be null
     */
    void addLayer(AbstractLayer layer);

    /**
     * Removes an existing layer from this model.
     * 
     * @param layer
     *            the existing layer, must not be null
     */
    void removeLayer(AbstractLayer layer);

    /**
     * Draws all visible layers of this model.
     * 
     * @param g2d
     *            the 2D graphics context
     * @param transform
     * 
     */
    void draw(Graphics2D g2d, AffineTransform transform, AffineTransform inverseTransform);

    /**
     * Releases all resources held by this model. Method calls to this model after <code>dispose</code> has been called,
     * are undefined.
     */
    void dispose();

    /**
     * Tests if fireing layer model change events is suspended.
     * 
     * @return true, if so
     */
    boolean isLayerModelChangeFireingSuspended();

    /**
     * Suspends the fireing of layer model change events.
     * 
     * @param layerModelChangeFireingSuspended
     *            true, if suspended
     */
    void setLayerModelChangeFireingSuspended(boolean layerModelChangeFireingSuspended);

    /**
     * Gets all layer manager listeners of this layer.
     */
    LayerModelChangeListener[] getLayerModelChangeListeners();

    /**
     * Adds a layer manager listener to this layer.
     */
    void addLayerModelChangeListener(LayerModelChangeListener listener);

    /**
     * Removes a layer manager listener from this layer.
     */
    void removeLayerModelChangeListener(LayerModelChangeListener listener);

    /**
     * Notifies all listeners about a layer model change.
     */
    void fireLayerModelChanged();

    void repaint(Rectangle rectangle);

    void repaint();

    ArrayList<Graphic> getSelectedGraphics();

    void setSelectedGraphics(List<Graphic> list);

    Rectangle getBounds();

    GraphicsPane getGraphicsPane();
}
