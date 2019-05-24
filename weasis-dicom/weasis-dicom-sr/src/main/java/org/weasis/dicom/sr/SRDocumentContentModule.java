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

import java.util.Date;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.weasis.dicom.codec.macro.Code;
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
