package org.weasis.dicom.explorer.mf;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.wado.WadoParameters;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.mf.ArcQuery;
import org.weasis.dicom.mf.ArcQuery.ViewerMessage;
import org.weasis.dicom.mf.Patient;
import org.weasis.dicom.mf.QueryResult;
import org.weasis.dicom.mf.SOPInstance;
import org.weasis.dicom.mf.Series;
import org.weasis.dicom.mf.Study;

public class ManifestBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManifestBuilder.class);

    public static String getManifest(DicomModel model) {

        WadoParameters wado = null;
        ViewerMessage message = null;
        int imgNotAdd = 0;
        final List<Patient> patientList = new ArrayList<>();

        for (MediaSeriesGroup patient : model.getChildren(MediaSeriesGroupNode.rootNode)) {
            Patient p = getPatient(patient, patientList);
            for (MediaSeriesGroup study : model.getChildren(patient)) {
                Study st = getStudy(study, p);
                for (MediaSeriesGroup series : model.getChildren(study)) {
                    Series s = getSeries(series, st);
                    if (series instanceof MediaSeries) {
                        WadoParameters wadoParams = (WadoParameters) series.getTagValue(TagW.WadoParameters);
                        if (wadoParams == null) {
                            imgNotAdd += ((MediaSeries) series).size(null);
                            continue;
                        }
                        if (wado == null) {
                            wado = wadoParams;
                        }
                        for (DicomImageElement media : ((MediaSeries<DicomImageElement>) series)
                            .getSortedMedias(null)) {
                            String sopUID = (String) media.getTagValue(TagD.get(Tag.SOPInstanceUID));
                            SOPInstance sop = new SOPInstance(sopUID);
                            sop.setInstanceNumber(
                                ((Integer) media.getTagValue(TagD.get(Tag.InstanceNumber))).toString().toUpperCase());
                            s.addSOPInstance(sop);
                        }
                    }
                }
            }
        }

        if (wado != null) {
            ArcQuery arquery;
            try {
                List<QueryResult> list = new ArrayList<>();
                ModelResult result = new ModelResult(patientList,
                    new org.weasis.dicom.mf.WadoParameters(wado.getArchiveID(), wado.getWadoURL(),
                        wado.isRequireOnlySOPInstanceUID(), wado.getAdditionnalParameters(),
                        wado.getOverrideDicomTagsList(), wado.getWebLogin()));
                result.setViewerMessage(message);
                list.add(result);
                arquery = new ArcQuery(list);
                return arquery.xmlManifest(null);
            } catch (Exception e1) {
                AuditLog.logError(LOGGER, e1, "Building wado query error"); //$NON-NLS-1$
            }
        }
        if (imgNotAdd > 0) {
            // JOptionPane.showMessageDialog(ExportClipBoardKOToolbar.this,
            // imgNotAdd + " images cannot exported because they have be loaded locally!", //$NON-NLS-1$
            // Messages.getString("ExportClipBoardKOToolbar.warning"), //$NON-NLS-1$
            // JOptionPane.WARNING_MESSAGE);
            // return;
        }

        return null;

    }

    private static Patient getPatient(MediaSeriesGroup patient, List<Patient> patientList) {
        String id = (String) patient.getTagValue(TagD.get(Tag.PatientID));
        String ispid = (String) patient.getTagValue(TagD.get(Tag.IssuerOfPatientID));
        for (Patient p : patientList) {
            if (p.hasSameUniqueID(id, ispid)) {
                return p;
            }
        }
        Patient p = new Patient(id, ispid);
        p.setPatientName((String) patient.getTagValue(TagD.get(Tag.PatientName)));
        LocalDate date = (LocalDate) patient.getTagValue(TagD.get(Tag.PatientBirthDate));
        p.setPatientBirthDate(date == null ? null : TagD.formatDicomDate(date));
        p.setPatientSex((String) patient.getTagValue(TagD.get(Tag.PatientSex)));
        patientList.add(p);
        return p;
    }

    private static Study getStudy(MediaSeriesGroup study, Patient patient) {
        String uid = (String) study.getTagValue(TagD.get(Tag.StudyInstanceUID));
        Study s = patient.getStudy(uid);
        if (s == null) {
            s = new Study(uid);
            s.setStudyDescription((String) study.getTagValue(TagD.get(Tag.StudyDescription)));
            LocalDate date = TagD.getTagValue(study, Tag.StudyDate, LocalDate.class);
            s.setStudyDate(date == null ? null : TagD.formatDicomDate(date));
            LocalTime time = TagD.getTagValue(study, Tag.StudyTime, LocalTime.class);
            s.setStudyTime(time == null ? null : TagD.formatDicomTime(time));
            s.setAccessionNumber((String) study.getTagValue(TagD.get(Tag.AccessionNumber)));
            s.setStudyID((String) study.getTagValue(TagD.get(Tag.StudyID)));
            s.setReferringPhysicianName((String) study.getTagValue(TagD.get(Tag.ReferringPhysicianName)));
            patient.addStudy(s);
        }
        return s;
    }

    private static Series getSeries(MediaSeriesGroup series, Study study) {
        String uid = TagD.getTagValue(series, Tag.SeriesInstanceUID, String.class);
        Series s = study.getSeries(uid);
        if (s == null) {
            s = new Series(uid);
            s.setSeriesDescription(TagD.getTagValue(series, Tag.SeriesDescription, String.class));
            s.setSeriesNumber(StringUtil.getNonNullObject(TagD.getTagValue(series, Tag.SeriesNumber, Integer.class)));
            s.setModality(TagD.getTagValue(series, Tag.Modality, String.class));
            // s.setDirectDownloadThumbnail(TagW.getTagValue(series, TagW.DirectDownloadThumbnail, String.class));
            s.setWadoTransferSyntaxUID(TagW.getTagValue(series, TagW.WadoTransferSyntaxUID, String.class));
            // s.setWadoCompressionRate(
            // StringUtil.getNonNullObject(TagW.getTagValue(series, TagW.WadoCompressionRate, Integer.class)));
            study.addSeries(s);
        }
        return s;
    }
}
