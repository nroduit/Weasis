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

public class SeriesAndInstanceReference extends Module {

    public SeriesAndInstanceReference(Attributes dcmItems) {
        super(dcmItems);
    }

    public SeriesAndInstanceReference() {
        super(new Attributes());
    }

    public static Collection<SeriesAndInstanceReference> toSeriesAndInstanceReferenceMacros(Sequence seq) {
        if (seq == null || seq.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<SeriesAndInstanceReference> list = new ArrayList<>(seq.size());

        for (Attributes attr : seq) {
            list.add(new SeriesAndInstanceReference(attr));
        }

        return list;
    }

    public String getSeriesInstanceUID() {
        return dcmItems.getString(Tag.SeriesInstanceUID);
    }

    public void setSeriesInstanceUID(String uid) {
        dcmItems.setString(Tag.SeriesInstanceUID, VR.UI, uid);
    }

    public String getRetrieveAETitle() {
        return dcmItems.getString(Tag.RetrieveAETitle);
    }

    public void setRetrieveAETitle(String ae) {
        dcmItems.setString(Tag.RetrieveAETitle, VR.AE, ae);
    }

    public String getStorageMediaFileSetID() {
        return dcmItems.getString(Tag.StorageMediaFileSetID);
    }

    public void setStorageMediaFileSetID(String sh) {
        dcmItems.setString(Tag.StorageMediaFileSetID, VR.SH, sh);
    }

    public String getStorageMediaFileSetUID() {
        return dcmItems.getString(Tag.StorageMediaFileSetUID);
    }

    public void setStorageMediaFileSetUID(String uid) {
        dcmItems.setString(Tag.StorageMediaFileSetUID, VR.UI, uid);
    }

    public Collection<SOPInstanceReferenceAndMAC> getReferencedSOPInstances() {
        return SOPInstanceReferenceAndMAC
            .toSOPInstanceReferenceAndMacMacros(dcmItems.getSequence(Tag.ReferencedSOPSequence));
    }

    public void setReferencedSOPInstances(Collection<SOPInstanceReferenceAndMAC> referencedSOPInstances) {
        updateSequence(Tag.ReferencedSOPSequence, referencedSOPInstances);
    }

}
