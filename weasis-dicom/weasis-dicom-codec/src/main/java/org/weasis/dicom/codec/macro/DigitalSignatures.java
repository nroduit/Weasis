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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

public class DigitalSignatures extends Module {

    public DigitalSignatures(Attributes dcmItems) {
        super(dcmItems);
    }

    public DigitalSignatures() {
        super(new Attributes());
    }

    public static Collection<DigitalSignatures> toDigitalSignaturesMacros(Sequence seq) {
        if (seq == null || seq.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<DigitalSignatures> list = new ArrayList<>(seq.size());

        for (Attributes attr : seq) {
            list.add(new DigitalSignatures(attr));
        }

        return list;
    }

    public int getMACIDNumber() {
        return dcmItems.getInt(Tag.MACIDNumber, -1);
    }

    public void setMACIDNumber(int i) {
        dcmItems.setInt(Tag.MACIDNumber, VR.US, i);
    }

    public String getDigitalSignatureUID() {
        return dcmItems.getString(Tag.DigitalSignatureUID);
    }

    public void setDigitalSignatureUID(String s) {
        dcmItems.setString(Tag.DigitalSignatureUID, VR.UI, s);
    }

    public Date getDigitalSignatureDateTime() {
        return dcmItems.getDate(Tag.DigitalSignatureDateTime);
    }

    public void setDigitalSignatureDateTime(Date d) {
        dcmItems.setDate(Tag.DigitalSignatureDateTime, VR.DT, d);
    }

    public String getCertificateType() {
        return dcmItems.getString(Tag.CertificateType);
    }

    public void setCertificateType(String s) {
        dcmItems.setString(Tag.CertificateType, VR.CS, s);
    }

    public byte[] getCertificateOfSigner() throws IOException {
        return dcmItems.getBytes(Tag.CertificateOfSigner);
    }

    public void setCertificateOfSigner(byte[] b) {
        dcmItems.setBytes(Tag.CertificateOfSigner, VR.OB, b);
    }

    public byte[] getSignature() throws IOException {
        return dcmItems.getBytes(Tag.Signature);
    }

    public void setSignature(byte[] b) {
        dcmItems.setBytes(Tag.Signature, VR.OB, b);
    }

    public String getCertifiedTimestampType() {
        return dcmItems.getString(Tag.CertifiedTimestampType);
    }

    public void setCertifiedTimestampType(String s) {
        dcmItems.setString(Tag.CertifiedTimestampType, VR.CS, s);
    }

    public byte[] getCertifiedTimestamp() throws IOException {
        return dcmItems.getBytes(Tag.CertifiedTimestamp);
    }

    public void setCertifiedTimestamp(byte[] b) {
        dcmItems.setBytes(Tag.CertifiedTimestamp, VR.OB, b);
    }

    public Code getDigitalSignaturePurposeCode() {
        Attributes item = dcmItems.getNestedDataset(Tag.DigitalSignaturePurposeCodeSequence);
        return item != null ? new Code(item) : null;
    }

    public void setDigitalSignaturePurposeCode(Code code) {
        updateSequence(Tag.DigitalSignaturePurposeCodeSequence, code);
    }

}
