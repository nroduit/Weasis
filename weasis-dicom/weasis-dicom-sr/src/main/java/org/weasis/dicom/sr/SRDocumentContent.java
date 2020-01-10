/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.sr;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

public class SRDocumentContent extends SRDocumentContentModule {

    public SRDocumentContent(Attributes dcmobj) {
        super(dcmobj);
    }

    public String getRelationshipType() {
        return dcmItems.getString(Tag.RelationshipType);
    }

    public int[] getReferencedContentItemIdentifier() {
        return DicomMediaUtils.getIntAyrrayFromDicomElement(dcmItems, Tag.ReferencedContentItemIdentifier, null);
    }

}