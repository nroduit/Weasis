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

public class SOPInstanceReferenceAndPurpose extends SOPInstanceReference {

    public SOPInstanceReferenceAndPurpose(Attributes dcmItems) {
        super(dcmItems);
    }

    public SOPInstanceReferenceAndPurpose() {
        this(new Attributes());
    }

    public static Collection<SOPInstanceReferenceAndPurpose> toSOPInstanceReferenceAndPurposesMacros(Sequence seq) {

        if (seq == null || seq.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<SOPInstanceReferenceAndPurpose> list = new ArrayList<>(seq.size());

        for (Attributes attr : seq) {
            list.add(new SOPInstanceReferenceAndPurpose(attr));
        }

        return list;
    }

    public Code getPurposeOfReferenceCode() {
        Attributes item = dcmItems.getNestedDataset(Tag.PurposeOfReferenceCodeSequence);
        return item != null ? new Code(item) : null;
    }

    public void setPurposeOfReferenceCode(Code code) {
        updateSequence(Tag.PurposeOfReferenceCodeSequence, code);
    }

}
