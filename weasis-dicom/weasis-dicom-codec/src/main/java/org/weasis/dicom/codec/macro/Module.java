package org.weasis.dicom.codec.macro;

import java.util.Collection;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;

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

        Sequence oldSequence = dcmItems.getSequence(tag);

        if (module != null) {
            if (oldSequence != null) {
                oldSequence.clear(); // Allows to remove parents of Attributes
            }
            dcmItems.newSequence(tag, 1).add(module.getAttributes());
        }
    }

    protected void updateSequence(int tag, Collection<? extends Module> modules) {

        Sequence oldSequence = dcmItems.getSequence(tag);

        if (modules != null && modules.size() > 0) {
            if (oldSequence != null) {
                oldSequence.clear(); // Allows to remove parents of Attributes
            }
            Sequence newSequence = dcmItems.newSequence(tag, modules.size());
            for (Module module : modules) {
                newSequence.add(module.getAttributes());
            }
        }
    }
}
