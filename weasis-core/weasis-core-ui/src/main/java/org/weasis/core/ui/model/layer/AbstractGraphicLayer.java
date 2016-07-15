package org.weasis.core.ui.model.layer;

import java.util.Objects;
import java.util.Optional;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.adapters.XmlAdapter;

import org.weasis.core.ui.model.utils.imp.DefaultUUID;

public abstract class AbstractGraphicLayer extends DefaultUUID implements GraphicLayer {
    private static final long serialVersionUID = 845033167886327915L;

    private String name;
    private LayerType type;
    private Boolean visible;
    private Boolean locked;
    private Integer level; // Layers are sorted by level number (ascending order)

    public AbstractGraphicLayer(LayerType type) {
        this.type = Objects.requireNonNull(type);

        this.level = type.level();
        this.visible = type.visible();
        this.locked = type.locked();
    }

    @XmlAttribute
    @Override
    public Boolean getLocked() {
        return locked;
    }

    @Override
    public void setLocked(Boolean locked) {
        this.locked = locked;
    }

    @Override
    public void setVisible(Boolean flag) {
        this.visible = flag;
    }

    @XmlAttribute
    @Override
    public Boolean getVisible() {
        return visible;
    }

    @Override
    public void setLevel(Integer level) {
        this.level = level;
    }

    @XmlAttribute
    @Override
    public Integer getLevel() {
        return level;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @XmlAttribute
    @Override
    public String getName() {
        return name;
    }

    @Override
    public int compareTo(Layer obj) {
        if (obj == null) {
            return 1;
        }
        int thisVal = this.getLevel();
        int anotherVal = obj.getLevel();
        return thisVal < anotherVal ? -1 : (thisVal == anotherVal ? 0 : 1);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        AbstractGraphicLayer other = (AbstractGraphicLayer) obj;
        if (type == null) {
            if (other.type != null) {
                return false;
            }
        } else if (!type.equals(other.type)) {
            return false;
        }
        return true;
    }

    @XmlAttribute
    @Override
    public LayerType getType() {
        return type;
    }

    @Override
    public void setType(LayerType type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return Optional.ofNullable(name).orElse(type.name());
    }

    static class Adapter extends XmlAdapter<AbstractGraphicLayer, Layer> {

        @Override
        public Layer unmarshal(AbstractGraphicLayer v) throws Exception {
            return v;
        }

        @Override
        public AbstractGraphicLayer marshal(Layer v) throws Exception {
            return (AbstractGraphicLayer) v;
        }
    }
}
