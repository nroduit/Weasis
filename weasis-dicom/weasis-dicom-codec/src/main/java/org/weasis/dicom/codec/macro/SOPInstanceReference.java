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
import org.weasis.dicom.codec.utils.DicomMediaUtils;

public class SOPInstanceReference extends Module {

    public SOPInstanceReference(Attributes dcmItems) {
        super(dcmItems);
    }

    public SOPInstanceReference() {
        super(new Attributes());
    }

    public static Collection<SOPInstanceReference> toSOPInstanceReferenceMacros(Sequence seq) {
        if (seq == null || seq.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<SOPInstanceReference> list = new ArrayList<>(seq.size());

        for (Attributes attr : seq) {
            list.add(new SOPInstanceReference(attr));
        }

        return list;
    }

    public int[] getReferencedFrameNumber() {
        return DicomMediaUtils.getIntAyrrayFromDicomElement(dcmItems, Tag.ReferencedFrameNumber, null);
    }

    /**
     * Add frame number references (1 to n). Note: no frame means the entire series.
     *
     * @param dicomFrameNumber
     */
    public void setReferencedFrameNumber(int... dicomFrameNumber) {
        dcmItems.setInt(Tag.ReferencedFrameNumber, VR.IS, dicomFrameNumber);
    }

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
