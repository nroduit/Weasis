package org.weasis.dicom.codec.macro;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;

public class ImageSOPInstanceReference extends SOPInstanceReference {

    public ImageSOPInstanceReference(Attributes dcmItems) {
        super(dcmItems);
    }

    public int[] getReferencedFrameNumber() {
        return dcmItems.getInts(Tag.ReferencedFrameNumber);
    }

}
