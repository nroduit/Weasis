/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.codec.macro;

import java.util.ArrayList;
import java.util.Collection;

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
            return null;
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

    public void setReferencedFrameNumber(int... frameNumber) {
        dcmItems.setInt(Tag.ReferencedFrameNumber, VR.IS, frameNumber);
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
