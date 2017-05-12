package org.weasis.dicom.qr.manisfest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.service.QueryRetrieveLevel;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.wado.WadoParameters;
import org.weasis.dicom.codec.wado.WadoParameters.HttpTag;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.op.CFind;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.qr.manisfest.xml.TagUtil;

public class ManifestBuilder {
    // Manifest 2.5
    public static final String TAG_DOCUMENT_ROOT = "manifest"; //$NON-NLS-1$
    public static final String SCHEMA =
        "xmlns=\"http://www.weasis.org/xsd/2.5\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""; //$NON-NLS-1$
    public static final String TAG_ARC_QUERY = "arcQuery"; //$NON-NLS-1$
    public static final String ARCHIVE_ID = "arcId"; //$NON-NLS-1$
    public static final String BASE_URL = "baseUrl"; //$NON-NLS-1$

    // Manifest 1
    public static final String TAG_HTTP_TAG = "httpTag"; //$NON-NLS-1$
    public static final String ADDITIONNAL_PARAMETERS = "additionnalParameters"; //$NON-NLS-1$
    public static final String OVERRIDE_TAGS = "overrideDicomTagsList"; //$NON-NLS-1$
    public static final String WEB_LOGIN = "webLogin"; //$NON-NLS-1$

    public static final String TAG_DOCUMENT_MSG = "Message"; //$NON-NLS-1$
    public static final String MSG_ATTRIBUTE_TITLE = "title"; //$NON-NLS-1$
    public static final String MSG_ATTRIBUTE_DESC = "description"; //$NON-NLS-1$
    public static final String MSG_ATTRIBUTE_LEVEL = "severity"; //$NON-NLS-1$

    private final StringBuilder manifest;
    private final List<Patient> patients;

    public ManifestBuilder() {
        this.manifest = new StringBuilder();
        this.patients = new ArrayList<>();
    }

    public String getCharsetEncoding() {
        return "UTF-8"; //$NON-NLS-1$
    }

    public String xmlManifest(WadoParameters wadoParameters, ViewerMessage message) {
        if (wadoParameters == null || (patients.isEmpty() && message == null)) {
            return null;
        }

        manifest.append("<?xml version=\"1.0\" encoding=\"" + getCharsetEncoding() + "\" ?>"); //$NON-NLS-1$ //$NON-NLS-2$
        manifest.append("\n<"); //$NON-NLS-1$
        manifest.append(TAG_DOCUMENT_ROOT);
        manifest.append(" "); //$NON-NLS-1$
        manifest.append(SCHEMA);
        manifest.append(">"); //$NON-NLS-1$

        manifest.append("\n<"); //$NON-NLS-1$
        manifest.append(TAG_ARC_QUERY);
        manifest.append(" "); //$NON-NLS-1$

        TagUtil.addXmlAttribute(ARCHIVE_ID, "localWadoRetrieve", manifest); //$NON-NLS-1$
        TagUtil.addXmlAttribute(BASE_URL, wadoParameters.getWadoURL(), manifest);
        TagUtil.addXmlAttribute(WEB_LOGIN, wadoParameters.getWebLogin(), manifest);
        TagUtil.addXmlAttribute(WadoParameters.TAG_WADO_ONLY_SOP_UID, wadoParameters.isRequireOnlySOPInstanceUID(),
            manifest);
        TagUtil.addXmlAttribute(ADDITIONNAL_PARAMETERS, wadoParameters.getAdditionnalParameters(), manifest);
        TagUtil.addXmlAttribute(OVERRIDE_TAGS, wadoParameters.getOverrideDicomTagsList(), manifest);
        manifest.append(">"); //$NON-NLS-1$

        buildHttpTags(wadoParameters.getHttpTaglist());
        buildViewerMessage(message);
        buildPatient();

        manifest.append("\n</"); //$NON-NLS-1$
        manifest.append(TAG_ARC_QUERY);
        manifest.append(">"); //$NON-NLS-1$

        manifest.append("\n</"); //$NON-NLS-1$
        manifest.append(TAG_DOCUMENT_ROOT);
        manifest.append(">\n"); // Requires end of line //$NON-NLS-1$

        return manifest.toString();
    }

    private void buildPatient() {
        Collections.sort(patients, (o1, o2) -> o1.getPatientName().compareTo(o2.getPatientName()));

        for (Patient patient : patients) {
            manifest.append(patient.toXml());
        }

    }

    private void buildHttpTags(List<HttpTag> list) {
        if (list != null) {
            for (WadoParameters.HttpTag tag : list) {
                manifest.append("\n<"); //$NON-NLS-1$
                manifest.append(TAG_HTTP_TAG);
                manifest.append(" key=\""); //$NON-NLS-1$
                manifest.append(tag.getKey());
                manifest.append("\" value=\""); //$NON-NLS-1$
                manifest.append(tag.getValue());
                manifest.append("\" />"); //$NON-NLS-1$
            }
        }
    }

    private void buildViewerMessage(ViewerMessage message) {
        if (message != null) {
            manifest.append("\n<"); //$NON-NLS-1$
            manifest.append(TAG_DOCUMENT_MSG);
            manifest.append(" "); //$NON-NLS-1$
            TagUtil.addXmlAttribute(MSG_ATTRIBUTE_TITLE, message.title, manifest);
            TagUtil.addXmlAttribute(MSG_ATTRIBUTE_DESC, message.message, manifest);
            TagUtil.addXmlAttribute(MSG_ATTRIBUTE_LEVEL, message.level.name(), manifest);
            manifest.append("/>"); //$NON-NLS-1$
        }
    }

    public static class ViewerMessage {
        public enum eLevel {
            INFO, WARN, ERROR;
        }

        private final String message;
        private final String title;
        private final eLevel level;

        public ViewerMessage(String title, String message, eLevel level) {
            this.title = title;
            this.message = message;
            this.level = level;
        }

        public String getMessage() {
            return message;
        }

        public String getTitle() {
            return title;
        }

        public eLevel getLevel() {
            return level;
        }
    }

    public void fillSeries(AdvancedParams advancedParams, DicomNode callingNode, DicomNode calledNode, DicomModel model,
        List<String> studies) {
        for (String studyUID : studies) {

            DicomParam[] keysSeries = {
                // Matching Keys
                new DicomParam(Tag.StudyInstanceUID, studyUID),
                // Return Keys
                CFind.SeriesInstanceUID, CFind.Modality, CFind.SeriesNumber, CFind.SeriesDescription };

            DicomState state =
                CFind.process(advancedParams, callingNode, calledNode, 0, QueryRetrieveLevel.SERIES, keysSeries);

            List<Attributes> seriesRSP = state.getDicomRSP();
            if (seriesRSP != null && !seriesRSP.isEmpty()) {
                MediaSeriesGroup studyGroup = model.getStudyNode(studyUID);
                MediaSeriesGroup patientGroup = model.getParent(studyGroup, DicomModel.patient);
                Patient patient = getPatient(patientGroup);
                Study study = getStudy(patient, studyGroup);
                for (Attributes seriesDataset : seriesRSP) {
                    fillInstance(advancedParams, callingNode, calledNode, seriesDataset, study);
                }
            }
        }
    }

    private Patient getPatient(MediaSeriesGroup patientGroup) {
        String id = TagD.getTagValue(patientGroup, Tag.PatientID, String.class);
        String ispid = TagD.getTagValue(patientGroup, Tag.IssuerOfPatientID, String.class);
        for (Patient p : patients) {
            if (p.hasSameUniqueID(id, ispid)) {
                return p;
            }
        }
        Patient p = new Patient(id, ispid);
        p.setPatientName(TagD.getTagValue(patientGroup, Tag.PatientName, String.class));
        // Only set birth date, birth time is often not consistent (00:00)
        p.setPatientBirthDate(
            TagD.formatDicomDate(TagD.getTagValue(patientGroup, Tag.PatientBirthDate, LocalDate.class)));
        p.setPatientSex(TagD.getTagValue(patientGroup, Tag.PatientSex, String.class));
        patients.add(p);
        return p;
    }

    private Study getStudy(Patient patient, MediaSeriesGroup studyGroup) {
        String uid = TagD.getTagValue(studyGroup, Tag.StudyInstanceUID, String.class);
        Study s = patient.getStudy(uid);
        if (s == null) {
            s = new Study(uid);
            s.setStudyDescription(TagD.getTagValue(studyGroup, Tag.StudyDescription, String.class));
            s.setStudyDate(TagD.formatDicomDate(TagD.getTagValue(studyGroup, Tag.StudyDate, LocalDate.class)));
            s.setStudyTime(TagD.formatDicomTime(TagD.getTagValue(studyGroup, Tag.StudyTime, LocalTime.class)));
            s.setAccessionNumber(TagD.getTagValue(studyGroup, Tag.AccessionNumber, String.class));
            s.setStudyID(TagD.getTagValue(studyGroup, Tag.StudyID, String.class));
            s.setReferringPhysicianName(TagD.getTagValue(studyGroup, Tag.ReferringPhysicianName, String.class));
            patient.addStudy(s);
        }
        return s;
    }

    private static Series getSeries(Study study, final Attributes seriesDataset) {
        String uid = seriesDataset.getString(Tag.SeriesInstanceUID);
        Series s = study.getSeries(uid);
        if (s == null) {
            s = new Series(uid);
            s.setModality(seriesDataset.getString(Tag.Modality));
            s.setSeriesNumber(seriesDataset.getString(Tag.SeriesNumber));
            s.setSeriesDescription(seriesDataset.getString(Tag.SeriesDescription));
            study.addSeries(s);
        }
        return s;
    }

    private void fillInstance(AdvancedParams advancedParams, DicomNode callingNode, DicomNode calledNode,
        Attributes seriesDataset, Study study) {
        String serieInstanceUID = seriesDataset.getString(Tag.SeriesInstanceUID);
        if (StringUtil.hasText(serieInstanceUID)) {
            DicomParam[] keysInstance = {
                // Matching Keys
                new DicomParam(Tag.StudyInstanceUID, study.getStudyInstanceUID()),
                new DicomParam(Tag.SeriesInstanceUID, serieInstanceUID),
                // Return Keys
                CFind.SOPInstanceUID, CFind.InstanceNumber };
            DicomState state =
                CFind.process(advancedParams, callingNode, calledNode, 0, QueryRetrieveLevel.IMAGE, keysInstance);

            List<Attributes> instances = state.getDicomRSP();
            if (instances != null && !instances.isEmpty()) {
                Series s = getSeries(study, seriesDataset);

                for (Attributes instanceDataSet : instances) {
                    String sopUID = instanceDataSet.getString(Tag.SOPInstanceUID);
                    if (sopUID != null) {
                        SOPInstance sop = new SOPInstance(sopUID);
                        sop.setInstanceNumber(instanceDataSet.getString(Tag.InstanceNumber));
                        s.addSOPInstance(sop);
                    }
                }
            }
        }
    }
}
