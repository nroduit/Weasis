package org.weasis.dicom.rt;

import java.util.ArrayList;
import java.util.List;

import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.dicom.codec.macro.SOPInstanceReference;



public class RTImageReference {
    private SOPInstanceReference imageSOPInstanceReference;
    private List<Graphic> graphics;
    private final String nodeLevel;

    public RTImageReference(String nodeLevel) {
        super();
        this.nodeLevel = nodeLevel;
    }

    public void addGraphic(Graphic g) {
        if (g != null) {
            if (graphics == null) {
                graphics = new ArrayList<>();
            }
            graphics.add(g);
        }
    }

    public SOPInstanceReference getImageSOPInstanceReference() {
        return imageSOPInstanceReference;
    }

    public void setImageSOPInstanceReference(SOPInstanceReference imageSOPInstanceReference) {
        this.imageSOPInstanceReference = imageSOPInstanceReference;
    }

    public List<Graphic> getGraphics() {
        return graphics;
    }

    public String getNodeLevel() {
        return nodeLevel;
    }

}

