/*
 * Contributors:
 *    Felipe Fetzer (felipe.fetzer@animati.com.br) - initial API and implementation
 */
package org.weasis.dicom.codec.utils;

import javax.xml.stream.XMLStreamReader;
import org.dcm4che3.data.Attributes;
import org.weasis.core.api.media.data.TagReadable;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.TagD;

public class PatientComparator {

    private String birthdate;
    private String sex;
    private String patientId;
    private String name;
    private String issuerOfPatientID;
    private Long id;

    public PatientComparator(final Attributes item) {
        patientId = item.getString(Tag.PatientID, TagW.NO_VALUE);
        name = item.getString(Tag.PatientName, TagW.NO_VALUE);
        sex = item.getString(Tag.PatientSex, "");
        birthdate = item.getString(Tag.PatientBirthDate, "");
        issuerOfPatientID = item.getString(Tag.IssuerOfPatientID, "");
        id = null;
    }

    public PatientComparator(final XMLStreamReader xmler) {
        patientId = TagUtil.getTagAttribute(xmler, TagD.get(Tag.PatientID).getKeyword(), TagW.NO_VALUE);
        issuerOfPatientID = TagUtil.getTagAttribute(xmler, TagD.get(Tag.IssuerOfPatientID).getKeyword(), "");
        name = TagUtil.getTagAttribute(xmler, TagD.get(Tag.PatientName).getKeyword(), TagW.NO_VALUE);
        sex = TagUtil.getTagAttribute(xmler, TagD.get(Tag.PatientSex).getKeyword(), "");
        birthdate = TagUtil.getTagAttribute(xmler, TagD.get(Tag.PatientBirthDate).getKeyword(), "");
        id = null;
    }

    public PatientComparator() {

    }

    public String buildPatientPseudoUID(TagReadable tagable) {
        patientId = TagD.getTagValue(tagable, Tag.PatientID, String.class);
        issuerOfPatientID = TagD.getTagValue(tagable, Tag.IssuerOfPatientID, String.class);
        name = TagD.getTagValue(tagable, Tag.PatientName, String.class);
        sex = "";
        birthdate = "";
        id = null;
        return buildPatientPseudoUID();
    }

    public String buildPatientPseudoUID() {

        String property = BundleTools.SYSTEM_PREFERENCES.getProperty("patientComparator.buildPatientPseudoUID", null);

        if (StringUtil.hasText(property)) {

            StringBuilder buffer = new StringBuilder();
            String[] split = property.split(",");
            for (String string : split) {
                switch (string) {
                    case "patientId":
                        buffer.append(StringUtil.hasLength(patientId) ? patientId.trim() : TagW.NO_VALUE);
                        break;
                    case "patientName":
                        buffer.append(StringUtil.hasLength(name) ? name.replace("^", " ").trim() : "");
                        break;
                    case "id":
                        buffer.append(id == null ? "" : id);
                        break;
                    case "patientBirthdate":
                        buffer.append(StringUtil.hasLength(birthdate) ? birthdate.trim() : "");
                        break;
                    case "patientSex":
                        buffer.append(StringUtil.hasLength(sex) ? sex : "");
                        break;
                    case "issuerOfPatientID":
                        buffer.append(StringUtil.hasLength(issuerOfPatientID) ? issuerOfPatientID.trim() : "");
                        break;
                }
            }
            return buffer.toString();

        } else {

            /*
         * IHE RAD TF-­‐2: 4.16.4.2.2.5.3
         *
         * The Image Display shall not display FrameSets for multiple patients simultaneously. Only images with exactly
         * the same value for Patient’s ID (0010,0020) and Patient’s Name (0010,0010) shall be displayed at the same
         * time (other Patient-level attributes may be different, empty or absent). Though it is possible that the same
         * patient may have slightly different identifying attributes in different DICOM images performed at different
         * sites or on different occasions, it is expected that such differences will have been reconciled prior to the
         * images being provided to the Image Display (e.g., in the Image Manager/Archive or by the Portable Media
         * Creator).
             */
            // Build a global identifier for the patient.
            StringBuilder buffer = new StringBuilder(patientId == null ? TagW.NO_VALUE : patientId);
            if (StringUtil.hasText(issuerOfPatientID)) {
                // patientID + issuerOfPatientID => should be unique globally
                buffer.append(issuerOfPatientID);
            }
            if (name != null) {
                buffer.append(name.toUpperCase());
            }

            return buffer.toString();
        }

    }


    public String getBirthdate() {
        return birthdate;
    }

    public void setBirthdate(String birthdate) {
        this.birthdate = birthdate;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIssuerOfPatientID() {
        return issuerOfPatientID;
    }

    public void setIssuerOfPatientID(String issuerOfPatientID) {
        this.issuerOfPatientID = issuerOfPatientID;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }


}
