package org.weasis.core.ui.model.layer;

public enum LayerType {
    IMAGE(10, true, false), 
    CROSSLINES(20, true, true), 
    ANNOTATION(30, true, false),
    ANNOTATION_INFO(31, true, false),
    DRAW(40, true, false),
    MEASURE(50, true, false), 
    TEMPDRAGLAYER(60, true, false), 
    ACQUIRE(70, true, false),
    DICOM_PR(80, true, true);

    private LayerType(Integer level, Boolean visible, Boolean locked) {
        this.level = level;
        this.visible = visible;
        this.locked = locked;
    }

    private final Integer level;
    private final Boolean visible;
    private final Boolean locked;

    public Integer level() {
        return level;
    }

    public Boolean visible() {
        return visible;
    }

    public Boolean locked() {
        return locked;
    }
}
