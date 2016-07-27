package org.weasis.core.ui.model.utils.imp;

import java.io.Serializable;
import java.util.Optional;
import java.util.UUID;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlID;

import org.weasis.core.ui.model.utils.UUIDable;

public class DefaultUUID implements UUIDable, Serializable {
    private static final long serialVersionUID = -3178169761934642523L;

    private String uuid;

    public DefaultUUID() {
        this.uuid = UUID.randomUUID().toString();
    }

    public DefaultUUID(String uuid) {
        setUuid(uuid);
    }

    @Override
    @XmlID
    @XmlAttribute
    public String getUuid() {
        return uuid;
    }

    @Override
    public void setUuid(String uuid) {
        this.uuid = Optional.ofNullable(uuid).orElse(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[" + this.uuid + "]";
    }
}
