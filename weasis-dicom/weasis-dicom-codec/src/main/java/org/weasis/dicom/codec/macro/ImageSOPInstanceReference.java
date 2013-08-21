package org.weasis.dicom.codec.macro;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

public class ImageSOPInstanceReference extends SOPInstanceReference {

    public ImageSOPInstanceReference(Attributes dcmItems) {
        super(dcmItems);
    }

    public int[] getReferencedFrameNumber() {
        return DicomMediaUtils.getIntAyrrayFromDicomElement(dcmItems, Tag.ReferencedFrameNumber, null);
    }

}
