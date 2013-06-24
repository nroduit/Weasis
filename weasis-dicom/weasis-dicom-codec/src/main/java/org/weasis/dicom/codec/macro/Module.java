package org.weasis.dicom.codec.macro;

import org.dcm4che.data.Attributes;

public class Module {

    protected final Attributes dcmItems;

    public Module(Attributes dcmItems) {
        if (dcmItems == null) {
            throw new NullPointerException("dcmItems");
        }
        this.dcmItems = dcmItems;
    }

    public Attributes getAttributes() {
        return dcmItems;
    }

}
