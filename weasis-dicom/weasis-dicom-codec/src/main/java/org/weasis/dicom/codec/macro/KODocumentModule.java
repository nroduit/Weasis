package org.weasis.dicom.codec.macro;

import java.util.Collection;
import java.util.Date;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

public class KODocumentModule extends Module {

    public KODocumentModule(Attributes dcmItems) {
        super(dcmItems);
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////

    public String getInstanceNumber() {
        return dcmItems.getString(Tag.InstanceNumber);
    }

    public void setInstanceNumber(String s) {
        dcmItems.setString(Tag.InstanceNumber, VR.IS, s);
    }

    public Date getContentDateTime() {
        return dcmItems.getDate(Tag.ContentDate, Tag.ContentTime);
    }

    public void setContentDateTime(Date d) {
        dcmItems.setDate(Tag.ContentDate, VR.DA, d);
        dcmItems.setDate(Tag.ContentTime, VR.TM, d);
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////

    public Collection<ReferencedRequest> getReferencedRequests() {
        return ReferencedRequest.toReferencedRequestMacros(dcmItems.getSequence(Tag.ReferencedRequestSequence));
    }

    public void setReferencedRequest(Collection<ReferencedRequest> refrqs) {
        updateSequence(Tag.ReferencedRequestSequence, refrqs);
    }

    public Collection<HierachicalSOPInstanceReference> getCurrentRequestedProcedureEvidences() {
        return HierachicalSOPInstanceReference.toHierachicalSOPInstanceReferenceMacros(dcmItems
            .getSequence(Tag.CurrentRequestedProcedureEvidenceSequence));
    }

    public void setCurrentRequestedProcedureEvidences(Collection<HierachicalSOPInstanceReference> refs) {
        updateSequence(Tag.CurrentRequestedProcedureEvidenceSequence, refs);
    }

    public Collection<HierachicalSOPInstanceReference> getIdenticalDocuments() {
        return HierachicalSOPInstanceReference.toHierachicalSOPInstanceReferenceMacros(dcmItems
            .getSequence(Tag.IdenticalDocumentsSequence));
    }

    public void setIdenticalDocuments(Collection<HierachicalSOPInstanceReference> refs) {
        updateSequence(Tag.IdenticalDocumentsSequence, refs);
    }

}
