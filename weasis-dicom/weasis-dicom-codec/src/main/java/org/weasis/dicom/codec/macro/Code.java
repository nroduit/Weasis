/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.codec.macro;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.weasis.core.util.StringUtil;

public class Code extends Module {

    public Code(Attributes dcmItems) {
        super(dcmItems);
    }

    public Code() {
        this(new Attributes());
    }

    public static Collection<Code> toCodeMacros(Sequence seq) {
        if (seq == null || seq.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<Code> list = new ArrayList<>(seq.size());

        for (Attributes attr : seq) {
            list.add(new Code(attr));
        }

        return list;
    }

    public String getExistingCodeValue() {
        String val = getCodeValue();
        if (!StringUtil.hasText(val)) {
            val = getLongCodeValue();
        }
        if (!StringUtil.hasText(val)) {
            val = getURNCodeValue();
        }
        return val;
    }

    public String getCodeValue() {
        return dcmItems.getString(Tag.CodeValue);
    }

    public void setCodeValue(String s) {
        dcmItems.setString(Tag.CodeValue, VR.SH, s);
    }

    public String getLongCodeValue() {
        return dcmItems.getString(Tag.LongCodeValue);
    }

    public void setLongCodeValue(String s) {
        dcmItems.setString(Tag.LongCodeValue, VR.UC, s);
    }

    public String getURNCodeValue() {
        return dcmItems.getString(Tag.URNCodeValue);
    }

    public void setURNCodeValue(String s) {
        dcmItems.setString(Tag.URNCodeValue, VR.UR, s);
    }

    public String getCodingSchemeDesignator() {
        return dcmItems.getString(Tag.CodingSchemeDesignator);
    }

    public void setCodingSchemeDesignator(String s) {
        dcmItems.setString(Tag.CodingSchemeDesignator, VR.SH, s);
    }

    public String getCodingSchemeVersion() {
        return dcmItems.getString(Tag.CodingSchemeVersion);
    }

    public void setCodingSchemeVersion(String s) {
        dcmItems.setString(Tag.CodingSchemeVersion, VR.SH, s);
    }

    public String getCodeMeaning() {
        return dcmItems.getString(Tag.CodeMeaning);
    }

    public void setCodeMeaning(String s) {
        dcmItems.setString(Tag.CodeMeaning, VR.LO, s);
    }

    public String getContextIdentifier() {
        return dcmItems.getString(Tag.ContextIdentifier);
    }

    public void setContextIdentifier(String s) {
        dcmItems.setString(Tag.ContextIdentifier, VR.CS, s);
    }

    public String getMappingResource() {
        return dcmItems.getString(Tag.MappingResource);
    }

    public void setMappingResource(String s) {
        dcmItems.setString(Tag.MappingResource, VR.CS, s);
    }

    public Date getContextGroupVersion() {
        return dcmItems.getDate(Tag.ContextGroupVersion);
    }

    public void setContextGroupVersion(Date d) {
        dcmItems.setDate(Tag.ContextGroupVersion, VR.DT, d);
    }

    public String getContextGroupExtensionFlag() {
        return dcmItems.getString(Tag.ContextGroupExtensionFlag);
    }

    public void setContextGroupExtensionFlag(String s) {
        dcmItems.setString(Tag.ContextGroupExtensionFlag, VR.CS, s);
    }

    public Date getContextGroupLocalVersion() {
        return dcmItems.getDate(Tag.ContextGroupLocalVersion);
    }

    public void setContextGroupLocalVersion(Date d) {
        dcmItems.setDate(Tag.ContextGroupLocalVersion, VR.DT, d);
    }

    public String getContextGroupExtensionCreatorUID() {
        return dcmItems.getString(Tag.ContextGroupExtensionCreatorUID);
    }

    public void setContextGroupExtensionCreatorUID(String s) {
        dcmItems.setString(Tag.ContextGroupExtensionCreatorUID, VR.UI, s);
    }
}
