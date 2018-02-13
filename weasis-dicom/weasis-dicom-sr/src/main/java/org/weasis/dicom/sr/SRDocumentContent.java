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