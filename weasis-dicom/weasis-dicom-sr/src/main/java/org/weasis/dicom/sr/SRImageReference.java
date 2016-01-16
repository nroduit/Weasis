package org.weasis.dicom.sr;

import java.util.ArrayList;
import java.util.List;

import org.weasis.core.ui.graphic.Graphic;
import org.weasis.dicom.codec.macro.SOPInstanceReference;

public class SRImageReference {
    private SOPInstanceReference sopInstanceReference;
    private List<Graphic> graphics;
    private final String nodeLevel;

    public SRImageReference(String nodeLevel) {
        super();
        this.nodeLevel = nodeLevel;
    }

    public void addGraphic(Graphic g) {
        if (g != null) {
            if (graphics == null) {
                graphics = new ArrayList<Graphic>();
            }
            graphics.add(g);
        }
    }

    public SOPInstanceReference getSopInstanceReference() {
        return sopInstanceReference;
    }

    public void setSopInstanceReference(SOPInstanceReference sopInstanceReference) {
        this.sopInstanceReference = sopInstanceReference;
    }

    public List<Graphic> getGraphics() {
        return graphics;
    }

    public String getNodeLevel() {
        return nodeLevel;
    }

}
