/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.model.layer;

import java.util.Objects;
import java.util.Optional;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.adapters.XmlAdapter;

import org.weasis.core.ui.model.utils.imp.DefaultUUID;

@XmlAccessorType(XmlAccessType.NONE)
public abstract class AbstractGraphicLayer extends DefaultUUID implements GraphicLayer {
    private static final long serialVersionUID = 845033167886327915L;

    private String name;
    private LayerType type;
    private Boolean visible;
    private Boolean locked;
    private Integer level; // Layers are sorted by level number (ascending order)
    private Boolean serializable;
    private Boolean selectable;

    public AbstractGraphicLayer(LayerType type) {
        setType(type);
        this.level = type.getLevel();
        this.visible = type.getVisible();
        this.locked = type.getLocked();
        this.serializable = type.getSerializable();
        this.selectable = type.getSelectable();
    }

    @XmlAttribute(name = "locked")
    @Override
    public Boolean getLocked() {
        return locked;
    }

    @Override
    public void setLocked(Boolean locked) {
        this.locked = Optional.ofNullable(locked).orElse(getType().getLocked());
    }

    @Override
    public void setVisible(Boolean visible) {
        this.visible = Optional.ofNullable(visible).orElse(getType().getVisible());
    }

    @XmlAttribute(name = "visible")
    @Override
    public Boolean getVisible() {
        return visible;
    }

    @Override
    public void setLevel(Integer level) {
        this.level = Optional.ofNullable(level).orElse(getType().getLevel());
    }

    @XmlAttribute(name = "level")
    @Override
    public Integer getLevel() {
        return level;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @XmlAttribute(name = "name")
    @Override
    public String getName() {
        return name;
    }

    @XmlAttribute(name = "type")
    @Override
    public LayerType getType() {
        return type;
    }

    @Override
    public void setType(LayerType type) {
        this.type = Objects.requireNonNull(type);
    }

    @XmlAttribute(name = "selectable")
    @Override
    public Boolean getSelectable() {
        return selectable;
    }

    @Override
    public void setSelectable(Boolean selectable) {
        this.selectable = Optional.ofNullable(selectable).orElse(getType().getSelectable());
    }

    @Override
    public Boolean getSerializable() {
        return serializable;
    }

    @Override
    public void setSerializable(Boolean serializable) {
        this.serializable = serializable;
    }

    @Override
    public String toString() {
        return Optional.ofNullable(getName()).orElse(getType().getDefaultName());
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
