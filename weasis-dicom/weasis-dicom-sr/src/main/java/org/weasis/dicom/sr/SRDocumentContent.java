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