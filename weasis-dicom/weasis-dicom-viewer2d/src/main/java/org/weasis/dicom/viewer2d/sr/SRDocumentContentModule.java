package org.weasis.dicom.viewer2d.sr;

import java.util.Date;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Code;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;
import org.weasis.dicom.codec.macro.Module;
import org.weasis.dicom.codec.macro.SOPInstanceReference;

public class SRDocumentContentModule extends Module {

    public SRDocumentContentModule(Attributes dcmItems) {
        super(dcmItems);
    }

    public Code getNestedCode(int tag) {
        Attributes item = dcmItems.getNestedDataset(tag);
        return item != null ? new Code(item) : null;
    }

    public String getValueType() {
        return dcmItems.getString(Tag.ValueType);
    }

    public Code getConceptNameCode() {
        return getNestedCode(Tag.ConceptNameCodeSequence);
    }

    public Date getDateTime() {
        return dcmItems.getDate(Tag.DateTime);
    }

    public Date getDate() {
        return dcmItems.getDate(Tag.Date);
    }

    public Date getTime() {
        return dcmItems.getDate(Tag.Time);
    }

    public String getPersonName() {
        return dcmItems.getString(Tag.PersonName);
    }

    public String getUID() {
        return dcmItems.getString(Tag.UID);
    }

    public String getTextValue() {
        return dcmItems.getString(Tag.TextValue);
    }

    public Attributes getMeasuredValue() {
        return dcmItems.getNestedDataset(Tag.MeasuredValueSequence);
    }

    public Code getNumericValueQualifierCode() {
        return getNestedCode(Tag.NumericValueQualifierCodeSequence);
    }

    public Code getConceptCode() {
        return getNestedCode(Tag.ConceptCodeSequence);
    }

    public SOPInstanceReference getReferencedSOPInstance() {
        Attributes item = dcmItems.getNestedDataset(Tag.ConceptCodeSequence);
        return item != null ? new SOPInstanceReference(item) : null;
    }

    public Sequence getContent() {
        return dcmItems.getSequence(Tag.ContentSequence);
    }

    public String getContinuityOfContent() {
        return dcmItems.getString(Tag.ContinuityOfContent);
    }

    public Date getObservationDateTime() {
        return dcmItems.getDate(Tag.ObservationDateTime);
    }

}
