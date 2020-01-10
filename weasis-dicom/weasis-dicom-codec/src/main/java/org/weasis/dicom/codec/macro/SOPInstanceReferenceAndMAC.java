/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.codec.macro;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;

public class SOPInstanceReferenceAndMAC extends SOPInstanceReferenceAndPurpose {

    public SOPInstanceReferenceAndMAC(Attributes dcmItems) {
        super(dcmItems);
    }

    public SOPInstanceReferenceAndMAC() {
        super(new Attributes());
    }

    public static Collection<SOPInstanceReferenceAndMAC> toSOPInstanceReferenceAndMacMacros(Sequence seq) {

        if (seq == null || seq.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<SOPInstanceReferenceAndMAC> list = new ArrayList<>(seq.size());

        for (Attributes attr : seq) {
            list.add(new SOPInstanceReferenceAndMAC(attr));
        }

        return list;
    }

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
