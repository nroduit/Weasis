package org.weasis.dicom.codec.macro;

import java.util.ArrayList;
import java.util.Collection;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;
import org.dcm4che.data.VR;

public class SOPInstanceReference extends Module {

    public SOPInstanceReference(Attributes dcmItems) {
        super(dcmItems);
    }

    public SOPInstanceReference() {
        super(new Attributes());
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////

    public static Collection<SOPInstanceReference> toSOPInstanceReferenceMacros(Sequence seq) {
        if (seq == null || seq.isEmpty()) {
            return null;
        }

        ArrayList<SOPInstanceReference> list = new ArrayList<SOPInstanceReference>(seq.size());

        for (Attributes attr : seq) {
            list.add(new SOPInstanceReference(attr));
        }

        return list;
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////

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
