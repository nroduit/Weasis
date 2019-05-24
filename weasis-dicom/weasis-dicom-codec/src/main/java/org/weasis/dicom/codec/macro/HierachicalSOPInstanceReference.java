/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.codec.macro;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

public class HierachicalSOPInstanceReference extends Module {

    public HierachicalSOPInstanceReference(Attributes dcmItems) {
        super(dcmItems);
    }

    public HierachicalSOPInstanceReference() {
        super(new Attributes());
    }

    public static Collection<HierachicalSOPInstanceReference> toHierachicalSOPInstanceReferenceMacros(Sequence seq) {
        if (seq == null || seq.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<HierachicalSOPInstanceReference> list = new ArrayList<>(seq.size());

        for (Attributes attr : seq) {
            list.add(new HierachicalSOPInstanceReference(attr));
        }

        return list;
    }

    public String getStudyInstanceUID() {
        return dcmItems.getString(Tag.StudyInstanceUID);
    }

    public void setStudyInstanceUID(String uid) {
        dcmItems.setString(Tag.StudyInstanceUID, VR.UI, uid);
    }

    public Collection<SeriesAndInstanceReference> getReferencedSeries() {
        return SeriesAndInstanceReference
            .toSeriesAndInstanceReferenceMacros(dcmItems.getSequence(Tag.ReferencedSeriesSequence));
    }

    public void setReferencedSeries(Collection<SeriesAndInstanceReference> referencedSeries) {
        updateSequence(Tag.ReferencedSeriesSequence, referencedSeries);
    }
}
