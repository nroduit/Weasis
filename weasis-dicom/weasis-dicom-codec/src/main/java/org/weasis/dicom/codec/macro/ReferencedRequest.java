package org.weasis.dicom.codec.macro;

import java.util.ArrayList;
import java.util.Collection;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;
import org.dcm4che.data.VR;

public class ReferencedRequest extends Module {
    public ReferencedRequest(Attributes dcmItems) {
        super(dcmItems);
    }

    public ReferencedRequest() {
        super(new Attributes());
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////

    public static Collection<ReferencedRequest> toReferencedRequestMacros(Sequence seq) {
        if (seq == null || seq.isEmpty()) {
            return null;
        }

        ArrayList<ReferencedRequest> list = new ArrayList<ReferencedRequest>(seq.size());

        for (Attributes attr : seq) {
            list.add(new ReferencedRequest(attr));
        }

        return list;
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////

    public String getStudyInstanceUID() {
        return dcmItems.getString(Tag.StudyInstanceUID);
    }

    public void setStudyInstanceUID(String s) {
        dcmItems.setString(Tag.StudyInstanceUID, VR.UI, s);
    }

    public SOPInstanceReference getReferencedStudySOPInstance() {
        Attributes item = dcmItems.getNestedDataset(Tag.ReferencedStudySequence);
        return item != null ? new SOPInstanceReference(item) : null;
    }

    public void setReferencedStudySOPInstance(SOPInstanceReference refSOP) {
        updateSequence(Tag.ReferencedStudySequence, refSOP);
    }

    public String getAccessionNumber() {
        return dcmItems.getString(Tag.AccessionNumber);
    }

    public void setAccessionNumber(String s) {
        dcmItems.setString(Tag.AccessionNumber, VR.SH, s);
    }

    public String getPlacerOrderNumberImagingServiceRequest() {
        return dcmItems.getString(Tag.PlacerOrderNumberImagingServiceRequest);
    }

    public void setPlacerOrderNumberImagingServiceRequest(String s) {
        dcmItems.setString(Tag.PlacerOrderNumberImagingServiceRequest, VR.LO, s);
    }

    public String getFillerOrderNumberImagingServiceRequest() {
        return dcmItems.getString(Tag.FillerOrderNumberImagingServiceRequest);
    }

    public void setFillerOrderNumberImagingServiceRequest(String s) {
        dcmItems.setString(Tag.FillerOrderNumberImagingServiceRequest, VR.LO, s);
    }

    public String getRequestedProcedureID() {
        return dcmItems.getString(Tag.RequestedProcedureID);
    }

    public void setRequestedProcedureID(String s) {
        dcmItems.setString(Tag.RequestedProcedureID, VR.SH, s);
    }

    public String getRequestedProcedureDescription() {
        return dcmItems.getString(Tag.RequestedProcedureDescription);
    }

    public void setRequestedProcedureDescription(String s) {
        dcmItems.setString(Tag.RequestedProcedureDescription, VR.LO, s);
    }

    public Code getRequestedProcedureCode() {
        Attributes item = dcmItems.getNestedDataset(Tag.RequestedProcedureCodeSequence);
        return item != null ? new Code(item) : null;
    }

    public void setRequestedProcedureCode(Code code) {
        updateSequence(Tag.RequestedProcedureCodeSequence, code);
    }

    public String getReasonForTheRequestedProcedure() {
        return dcmItems.getString(Tag.ReasonForTheRequestedProcedure);
    }

    public void setReasonForTheRequestedProcedure(String s) {
        dcmItems.setString(Tag.ReasonForTheRequestedProcedure, VR.LO, s);
    }

    public Code getReasonForRequestedProcedureCode() {
        Attributes item = dcmItems.getNestedDataset(Tag.ReasonForRequestedProcedureCodeSequence);
        return item != null ? new Code(item) : null;
    }

    public void setReasonForRequestedProcedureCode(Code code) {
        updateSequence(Tag.ReasonForRequestedProcedureCodeSequence, code);
    }
}
