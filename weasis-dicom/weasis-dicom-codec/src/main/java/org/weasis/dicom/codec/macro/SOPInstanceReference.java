package org.weasis.dicom.codec.macro;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.data.VR;

public class SOPInstanceReference extends Module {

    public SOPInstanceReference(Attributes dcmItems) {
        super(dcmItems);
    }

    public String getReferencedSOPInstanceUID() {
        return dcmItems.getString(Tag.ReferencedSOPInstanceUID);
    }

    public void setReferencedSOPInstanceUID(String uid) {
        dcmItems.setString(Tag.ReferencedSOPInstanceUID, VR.UI, uid);
    }

    public String getReferencedSOPClassUID() {
        return dcmItems.getString(Tag.ReferencedSOPClassUID);
    }

    public void setReferencedSOPClassUID(String uid) {
        dcmItems.setString(Tag.ReferencedSOPClassUID, VR.UI, uid);
    }

}
