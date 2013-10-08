package org.weasis.dicom.codec.macro;

import java.util.ArrayList;
import java.util.Collection;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;

public class SOPInstanceReferenceAndPurpose extends SOPInstanceReference {

    public SOPInstanceReferenceAndPurpose(Attributes dcmItems) {
        super(dcmItems);
    }

    public SOPInstanceReferenceAndPurpose() {
        this(new Attributes());
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////

    public static Collection<SOPInstanceReferenceAndPurpose> toSOPInstanceReferenceAndPurposesMacros(Sequence seq) {

        if (seq == null || seq.isEmpty()) {
            return null;
        }

        ArrayList<SOPInstanceReferenceAndPurpose> list = new ArrayList<SOPInstanceReferenceAndPurpose>(seq.size());

        for (Attributes attr : seq) {
            list.add(new SOPInstanceReferenceAndPurpose(attr));
        }

        return list;
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////

    public Code getPurposeOfReferenceCode() {
        Attributes item = dcmItems.getNestedDataset(Tag.PurposeOfReferenceCodeSequence);
        return item != null ? new Code(item) : null;
    }

    public void setPurposeOfReferenceCode(Code code) {
        updateSequence(Tag.PurposeOfReferenceCodeSequence, code);
    }

}
