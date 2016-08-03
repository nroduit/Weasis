package org.weasis.core.ui.model.layer;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.weasis.core.ui.model.utils.UUIDable;

@XmlJavaTypeAdapter(AbstractGraphicLayer.Adapter.class)
public interface Layer extends Comparable<Layer>, UUIDable {
    
    void setVisible(Boolean visible);

    Boolean getVisible();

    void setLevel(Integer level);

    Integer getLevel();

    LayerType getType();

    void setType(LayerType type);

    void setName(String layerName);

    String getName();

    
    @Override
    default int compareTo(Layer obj) {
        if (obj == null) {
            return 1;
        }
        int thisVal = this.getLevel();
        int anotherVal = obj.getLevel();
        return thisVal < anotherVal ? -1 : (thisVal == anotherVal ? 0 : 1);
    }
}
