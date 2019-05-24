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

public class MACParameters extends Module {

    public MACParameters(Attributes dcmItems) {
        super(dcmItems);
    }

    public MACParameters() {
        super(new Attributes());
    }

    public static Collection<MACParameters> toMACParametersMacros(Sequence seq) {
        if (seq == null || seq.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<MACParameters> list = new ArrayList<>(seq.size());

        for (Attributes attr : seq) {
            list.add(new MACParameters(attr));
        }

        return list;
    }

    public int getMACIDNumber() {
        return dcmItems.getInt(Tag.MACIDNumber, -1);
    }

    public void setMACIDNumber(int i) {
        dcmItems.setInt(Tag.MACIDNumber, VR.US, i);
    }

    public String getMACCalculationTransferSyntaxUID() {
        return dcmItems.getString(Tag.MACCalculationTransferSyntaxUID);
    }

    public void setMACCalculationTransferSyntaxUID(String s) {
        dcmItems.setString(Tag.MACCalculationTransferSyntaxUID, VR.UI, s);
    }

    public String getMACAlgorithm() {
        return dcmItems.getString(Tag.MACAlgorithm);
    }

    public void setMACAlgorithm(String s) {
        dcmItems.setString(Tag.MACAlgorithm, VR.CS, s);
    }

    public int[] getDataElementsSigned() {
        return dcmItems.getInts(Tag.DataElementsSigned);
    }

    public void setDataElementsSigned(int[] ints) {
        dcmItems.setInt(Tag.DataElementsSigned, VR.AT, ints);
    }
}
