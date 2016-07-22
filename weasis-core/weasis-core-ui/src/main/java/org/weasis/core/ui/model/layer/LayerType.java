package org.weasis.core.ui.model.layer;

public enum LayerType {
    IMAGE(10, Boolean.TRUE, Boolean.FALSE, Boolean.FALSE),

    CROSSLINES(20, Boolean.TRUE, Boolean.TRUE, Boolean.FALSE),

    IMAGE_ANNOTATION(30, Boolean.TRUE, Boolean.FALSE, Boolean.FALSE),

    ANNOTATION(31, Boolean.TRUE, Boolean.FALSE, Boolean.TRUE),

    DRAW(40, Boolean.TRUE, Boolean.FALSE, Boolean.TRUE),

    MEASURE(50, Boolean.TRUE, Boolean.FALSE, Boolean.TRUE),

    TEMP_DRAW(60, Boolean.TRUE, Boolean.FALSE, Boolean.FALSE),

    ACQUIRE(70, Boolean.TRUE, Boolean.FALSE, Boolean.FALSE),

    DICOM_PR(80, Boolean.TRUE, Boolean.TRUE, Boolean.FALSE);

    private final Integer level;
    private final Boolean visible;
    private final Boolean locked;
    private final Boolean serializable;

    private LayerType(Integer level, Boolean visible, Boolean locked, Boolean serializable) {
        this.level = level;
        this.visible = visible;
        this.locked = locked;
        this.serializable = serializable;
    }

    public Integer getLevel() {
        return level;
    }

    public Boolean getVisible() {
        return visible;
    }

    public Boolean getLocked() {
        return locked;
    }

    public Boolean getSerializable() {
        return serializable;
    }
}
