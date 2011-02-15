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
