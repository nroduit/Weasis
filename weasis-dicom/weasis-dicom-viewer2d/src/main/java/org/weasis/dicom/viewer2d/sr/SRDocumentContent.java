package org.weasis.dicom.viewer2d.sr;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;

public class SRDocumentContent extends SRDocumentContentModule {

    public SRDocumentContent(Attributes dcmobj) {
        super(dcmobj);
    }

    public String getRelationshipType() {
        return dcmItems.getString(Tag.RelationshipType);
    }

    public int[] getReferencedContentItemIdentifier() {
        return dcmItems.getInts(Tag.ReferencedContentItemIdentifier);
    }

}