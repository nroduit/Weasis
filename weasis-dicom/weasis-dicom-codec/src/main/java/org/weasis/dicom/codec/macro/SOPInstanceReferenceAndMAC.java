package org.weasis.dicom.codec.macro;

import java.util.ArrayList;
import java.util.Collection;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;

public class SOPInstanceReferenceAndMAC extends SOPInstanceReferenceAndPurpose {

    public SOPInstanceReferenceAndMAC(Attributes dcmItems) {
        super(dcmItems);
    }

    public SOPInstanceReferenceAndMAC() {
        super(new Attributes());
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////

    public static Collection<SOPInstanceReferenceAndMAC> toSOPInstanceReferenceAndMacMacros(Sequence seq) {

        if (seq == null || seq.isEmpty()) {
            return null;
        }

        ArrayList<SOPInstanceReferenceAndMAC> list = new ArrayList<SOPInstanceReferenceAndMAC>(seq.size());

        for (Attributes attr : seq) {
            list.add(new SOPInstanceReferenceAndMAC(attr));
        }

        return list;
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////

    public Collection<MACParameters> getMACParameters() {
        return MACParameters.toMACParametersMacros(dcmItems.getSequence(Tag.MACParametersSequence));
    }

    public void setMACParameters(Collection<MACParameters> mac) {
        updateSequence(Tag.MACParametersSequence, mac);
    }

    public Collection<DigitalSignatures> getDigitalSignatures() {
        return DigitalSignatures.toDigitalSignaturesMacros(dcmItems.getSequence(Tag.DigitalSignaturesSequence));
    }

    public void setDigitalSignatures(Collection<DigitalSignatures> signatures) {
        updateSequence(Tag.DigitalSignaturesSequence, signatures);
    }
}
