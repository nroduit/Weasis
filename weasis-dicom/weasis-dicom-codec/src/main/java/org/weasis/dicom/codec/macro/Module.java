package org.weasis.dicom.codec.macro;

import java.util.Collection;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Sequence;

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

    protected void updateSequence(int tag, Module module) {

        if (module == null) {
            Sequence oldSequence = dcmItems.getSequence(tag);
            if (oldSequence != null) {
                oldSequence.clear();
            }
        } else {
            dcmItems.newSequence(tag, 1).add(module.getAttributes());
        }
    }

    protected void updateSequence(int tag, Collection<? extends Module> moduleList) {

        Sequence oldSequence = dcmItems.getSequence(tag);
        if (oldSequence != null) {
            // Allows to remove parents of Attributes
            oldSequence.clear();
        }

        Sequence newSequence = dcmItems.newSequence(tag, moduleList.size());
        for (Module module : moduleList) {
            newSequence.add(module.getAttributes());
        }

    }
}
