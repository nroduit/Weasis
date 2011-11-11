package org.weasis.dicom.codec.display;

import org.weasis.core.api.media.data.ImageElement;

public class LutShape {

    public static final LutShape LINEAR_SHAPE = new LutShape();

    protected enum eType {
        SEQUENCE, LINEAR, SIGMOID, LOG, LOG_INV
    }

    protected final eType functionType;
    protected final String explanation;

    public LutShape() {
        this.functionType = eType.LINEAR;
        this.explanation = "DEFAULT";
    }

    public static LutShape[] getShapeCollection() {
        return null;
    }

    public static LutShape[] getShapeCollection(ImageElement image) {
        return null;
    }

    public boolean isSequence() {
        return false;
    };

}
