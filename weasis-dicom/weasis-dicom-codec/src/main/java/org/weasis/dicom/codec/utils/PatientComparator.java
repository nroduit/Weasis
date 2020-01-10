/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
/*
 * Contributors:
 *    Felipe Fetzer (felipe.fetzer@animati.com.br) - initial API and implementation
 */
package org.weasis.dicom.codec.utils;

import java.time.LocalDate;
import java.util.Optional;

import javax.xml.stream.XMLStreamReader;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.util.DateUtil;

public class PatientComparator {

    private String issuerOfPatientID;
    private String patientId;
    private String name;
    private String birthdate;
    private String sex;

    public PatientComparator(Attributes item) {
        setPatientId(item.getString(Tag.PatientID));
        setIssuerOfPatientID(item.getString(Tag.IssuerOfPatientID));
        setName(item.getString(Tag.PatientName));
        setSex(item.getString(Tag.PatientSex));
        setBirthdate(item.getString(Tag.PatientBirthDate));
    }

    public PatientComparator(XMLStreamReader xmler) {
        setPatientId(TagUtil.getTagAttribute(xmler, TagD.get(Tag.PatientID).getKeyword(), null));
        setIssuerOfPatientID(TagUtil.getTagAttribute(xmler, TagD.get(Tag.IssuerOfPatientID).getKeyword(), null));
        setName(TagUtil.getTagAttribute(xmler, TagD.get(Tag.PatientName).getKeyword(), null));
        setSex(TagUtil.getTagAttribute(xmler, TagD.get(Tag.PatientSex).getKeyword(), null));
        setBirthdate(TagUtil.getTagAttribute(xmler, TagD.get(Tag.PatientBirthDate).getKeyword(), null));
    }

    public PatientComparator(TagReadable tagable) {
        setPatientId(TagD.getTagValue(tagable, Tag.PatientID, String.class));
        setIssuerOfPatientID(TagD.getTagValue(tagable, Tag.IssuerOfPatientID, String.class));
        setName(TagD.getTagValue(tagable, Tag.PatientName, String.class));
        setSex(TagD.getTagValue(tagable, Tag.PatientSex, String.class));
        setBirthdate(DateUtil.formatDicomDate(TagD.getTagValue(tagable, Tag.PatientBirthDate, LocalDate.class)));
    }

    public String buildPatientPseudoUID() {

        String property = BundleTools.SYSTEM_PREFERENCES.getProperty("patientComparator.buildPatientPseudoUID", null); //$NON-NLS-1$

        if (StringUtil.hasText(property)) {

            StringBuilder buffer = new StringBuilder();
            String[] split = property.split(","); //$NON-NLS-1$
            for (String string : split) {
                switch (string) {
                    case "issuerOfPatientID": //$NON-NLS-1$
                        buffer.append(issuerOfPatientID);
                        break;
                    case "patientId": //$NON-NLS-1$
                        buffer.append(patientId);
                        break;
                    case "patientName": //$NON-NLS-1$
                        buffer.append(name);
                        break;
                    case "patientBirthdate": //$NON-NLS-1$
                        buffer.append(birthdate);
                        break;
                    case "patientSex": //$NON-NLS-1$
                        buffer.append(sex);
                        break;
                }
            }
            return buffer.toString();

        } else {
            /*
             * IHE RAD TF-­‐2: 4.16.4.2.2.5.3
             *
             * The Image Display shall not display FrameSets for multiple patients simultaneously. Only images with
             * exactly the same value for Patient’s ID (0010,0020) and Patient’s Name (0010,0010) shall be displayed at
             * the same time (other Patient-level attributes may be different, empty or absent). Though it is possible
             * that the same patient may have slightly different identifying attributes in different DICOM images
             * performed at different sites or on different occasions, it is expected that such differences will have
             * been reconciled prior to the images being provided to the Image Display (e.g., in the Image
             * Manager/Archive or by the Portable Media Creator).
             */
            // Build a global identifier for the patient.
            StringBuilder buffer = new StringBuilder(patientId);
            // patientID + issuerOfPatientID => should be unique globally
            buffer.append(issuerOfPatientID);
            buffer.append(name);

            return buffer.toString();
        }

    }

    public String getIssuerOfPatientID() {
        return issuerOfPatientID;
    }

    public void setIssuerOfPatientID(String issuerOfPatientID) {
        this.issuerOfPatientID = Optional.ofNullable(issuerOfPatientID).orElse(StringUtil.EMPTY_STRING).trim();
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = Optional.ofNullable(patientId).orElse(TagW.NO_VALUE).trim();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Optional.ofNullable(name).orElse(TagW.NO_VALUE).toUpperCase().trim();
    }

    public String getBirthdate() {
        return birthdate;
    }

    public void setBirthdate(String birthdate) {
        this.birthdate = Optional.ofNullable(birthdate).orElse(StringUtil.EMPTY_STRING).trim();
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = Optional.ofNullable(sex).orElse(StringUtil.EMPTY_STRING).trim();
    }

}
