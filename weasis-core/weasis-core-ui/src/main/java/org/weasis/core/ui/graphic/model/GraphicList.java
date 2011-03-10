/*******************************************************************************
 * Copyright (c) 2011 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.graphic.model;

import java.util.ArrayList;

import org.weasis.core.ui.graphic.Graphic;

public class GraphicList extends ArrayList<Graphic> {
    private ArrayList<AbstractLayer> layers = null;

    public ArrayList<AbstractLayer> getLayers() {
        return layers;
    }

    public int getLayerSize() {
        if (layers == null) {
            return -1;
        }
        return layers.size();
    }

    public void addLayer(AbstractLayer layer) {
        if (layer != null) {
            if (layers == null) {
                layers = new ArrayList<AbstractLayer>();
            }
            if (!layers.contains(layer)) {
                layers.add(layer);
            }
        }
    }

    public void removeLayer(AbstractLayer layer) {
        if (layers != null && layer != null) {
            layers.remove(layer);
        }
    }
}
