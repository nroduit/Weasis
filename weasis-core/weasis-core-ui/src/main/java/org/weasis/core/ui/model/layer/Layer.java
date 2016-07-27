package org.weasis.core.ui.model.layer;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.weasis.core.ui.model.utils.UUIDable;

@XmlJavaTypeAdapter(AbstractGraphicLayer.Adapter.class)
public interface Layer extends Comparable<Layer>, UUIDable {
    void setVisible(Boolean flag);

    Boolean getVisible();

    void setLevel(Integer i);

    Integer getLevel();

    LayerType getType();

    void setType(LayerType type);

    void setName(String graphicLayerName);

    String getName();

}
