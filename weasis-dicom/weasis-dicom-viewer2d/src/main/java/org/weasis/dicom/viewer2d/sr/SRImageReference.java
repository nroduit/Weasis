package org.weasis.dicom.viewer2d.sr;

import java.util.ArrayList;
import java.util.List;

import org.dcm4che2.iod.module.macro.ImageSOPInstanceReference;
import org.weasis.core.ui.graphic.Graphic;

public class SRImageReference {
    private ImageSOPInstanceReference imageSOPInstanceReference;
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

    public ImageSOPInstanceReference getImageSOPInstanceReference() {
        return imageSOPInstanceReference;
    }

    public void setImageSOPInstanceReference(ImageSOPInstanceReference imageSOPInstanceReference) {
        this.imageSOPInstanceReference = imageSOPInstanceReference;
    }

    public List<Graphic> getGraphics() {
        return graphics;
    }

    public String getNodeLevel() {
        return nodeLevel;
    }

}
