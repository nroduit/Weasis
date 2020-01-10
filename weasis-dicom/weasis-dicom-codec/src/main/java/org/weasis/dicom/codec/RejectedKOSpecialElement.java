/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.codec;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;

/*
 * When an Imaging Document Consumer receives a Key Image Note with Key Object Selection (KOS) Document Title
 * valued (113001, DCM, "Rejected for Quality Reasons"), the Imaging Document Consumer shall support the three
 * behaviors listed below. The behavior shall be configurable as one of the following: • Suppress from
 * presentation the rejected instances referenced in this KOS and this KOS itself • Present the rejected
 * instances referenced in this KOS and this KOS itself • Ignore this KOS and present the rejected instances.
 *
 *
 * When an Imaging Document Consumer receives a Key Image Note with the Key Object Selection (KOS) Document
 * Title valued (113037, DCM, "Rejected for Patient Safety Reasons"), (113038, DCM, “Incorrect Modality Worklist
 * Entry”), or (113039, DCM, “Data Retention Policy Expired”), it shall suppress the KOS and its referenced
 * rejected instances from presentation.
 *
 * See  http://hl7.org/fhir/ValueSet/kos-title
 */
public class RejectedKOSpecialElement extends AbstractKOSpecialElement {

    public RejectedKOSpecialElement(DicomMediaIO mediaIO) {
        super(mediaIO);
    }

    public static boolean isRejectionKOS(DicomMediaIO mediaIO) {
        if (mediaIO != null) {
            Attributes dcmItems = mediaIO.getDicomObject();
            if (dcmItems != null) {
                Attributes item = dcmItems.getNestedDataset(Tag.ConceptNameCodeSequence);
                if (item != null) {
                    String cm = item.getString(Tag.CodeMeaning, null);
                    if (cm != null) {
                        return "Rejected for Quality Reasons".equalsIgnoreCase(cm) //$NON-NLS-1$
                            || "Rejected for Patient Safety Reasons".equalsIgnoreCase(cm) //$NON-NLS-1$
                            || "Incorrect Modality Worklist Entry".equalsIgnoreCase(cm) //$NON-NLS-1$
                            || "Data Retention Policy Expired".equalsIgnoreCase(cm); //$NON-NLS-1$
                    }
                }
            }
        }
        return false;
    }
}
