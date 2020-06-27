/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.explorer.rs;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.json.Json;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.json.JSONReader.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.ClosableURLConnection;
import org.weasis.core.api.util.NetworkUtil;
import org.weasis.core.api.util.URLParameters;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.codec.utils.PatientComparator;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.pref.download.SeriesDownloadPrefView;
import org.weasis.dicom.explorer.wado.DownloadPriority;
import org.weasis.dicom.explorer.wado.LoadSeries;
import org.weasis.dicom.explorer.wado.SeriesInstanceList;
import org.weasis.dicom.mf.AbstractQueryResult;
import org.weasis.dicom.mf.SopInstance;
import org.weasis.dicom.mf.WadoParameters;
import org.weasis.dicom.util.DateUtil;
import org.weasis.dicom.web.Multipart;

public class RsQueryResult extends AbstractQueryResult {
    private static final Logger LOGGER = LoggerFactory.getLogger(RsQueryResult.class);

    private static final boolean multipleParams =
        LangUtil.getEmptytoFalse(System.getProperty("dicom.qido.query.multi.params")); //$NON-NLS-1$
    private static final String STUDY_QUERY = multiParams(
        "&includefield=00080020,00080030,00080050,00080061,00080090,00081030,00100010,00100020,00100021,00100030,00100040,0020000D,00200010"); //$NON-NLS-1$
    private static final String SERIES_QUERY = multiParams("0008103E,00080060,0020000E,00200011,00081190"); //$NON-NLS-1$
    private static final String INSTANCE_QUERY = multiParams("00080018,00200013,00081190"); //$NON-NLS-1$
    private static final String QIDO_REQUEST = "QIDO-RS request: {}"; //$NON-NLS-1$

    private final RsQueryParams rsQueryParams;
    private final WadoParameters wadoParameters;
    private final boolean defaultStartDownloading;

    public RsQueryResult(RsQueryParams rsQueryParams) {
        this.rsQueryParams = rsQueryParams;
        this.wadoParameters = new WadoParameters("", true, true); //$NON-NLS-1$
        rsQueryParams.getRetrieveHeaders().forEach(wadoParameters::addHttpTag);
        // Accept only multipart/related and retrieve dicom at the stored syntax
        wadoParameters.addHttpTag("Accept", Multipart.MULTIPART_RELATED + ";type=\"" + Multipart.ContentType.DICOM //$NON-NLS-1$ //$NON-NLS-2$
            + "\";" + rsQueryParams.getProperties().getProperty(RsQueryParams.P_ACCEPT_EXT)); //$NON-NLS-1$
        defaultStartDownloading =
            BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(SeriesDownloadPrefView.DOWNLOAD_IMMEDIATELY, true);
    }

    private static String multiParams(String query) {
        return multipleParams ? query.replace(",", "&includefield=") : query; //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Override
    public WadoParameters getWadoParameters() {
        return null;
    }

    public void buildFromPatientID(List<String> patientIDs) {
        for (String patientID : LangUtil.emptyIfNull(patientIDs)) {
            if (!StringUtil.hasText(patientID)) {
                continue;
            }

            // IssuerOfPatientID filter ( syntax like in HL7 with extension^^^root)
            int beginIndex = patientID.indexOf("^^^"); //$NON-NLS-1$

            StringBuilder buf = new StringBuilder(rsQueryParams.getBaseUrl());
            buf.append("/studies?00100020="); //$NON-NLS-1$
            String patientVal = beginIndex <= 0 ? patientID : patientID.substring(0, beginIndex);
            try {
                buf.append(URLEncoder.encode(patientVal, StandardCharsets.UTF_8.toString()));
                if (beginIndex > 0) {
                    buf.append("&00100021="); //$NON-NLS-1$
                    buf.append(patientID.substring(beginIndex + 3));
                }
                buf.append(STUDY_QUERY);
                buf.append(rsQueryParams.getProperties().getProperty(RsQueryParams.P_QUERY_EXT, "")); //$NON-NLS-1$

                LOGGER.debug(QIDO_REQUEST, buf);
                List<Attributes> studies = parseJSON(buf.toString());
                if (!studies.isEmpty()) {
                    Collections.sort(studies, getStudyComparator());
                    applyAllFilters(studies);
                }
            } catch (Exception e) {
                LOGGER.error("QIDO-RS with PatientID {}", patientID, e); //$NON-NLS-1$
            }
        }
    }

    private List<Attributes> parseJSON(String url) throws IOException {
        List<Attributes> items = new ArrayList<>();
        try (ClosableURLConnection httpCon =
            NetworkUtil.getUrlConnection(new URL(url), new URLParameters(rsQueryParams.getQueryHeaders()));
                        InputStreamReader instream =
                            new InputStreamReader(httpCon.getInputStream(), StandardCharsets.UTF_8)) {
            JSONReader reader = new JSONReader(Json.createParser(instream));
            Callback callback = (fmi, dataset) -> items.add(dataset);
            reader.readDatasets(callback);
        }
        return items;
    }

    private void applyAllFilters(List<Attributes> studies) {
        if (StringUtil.hasText(rsQueryParams.getLowerDateTime())) {
            Date lowerDateTime = null;
            try {
                lowerDateTime = DateUtil.parseXmlDateTime(rsQueryParams.getLowerDateTime()).getTime();
            } catch (Exception e) {
                LOGGER.error("Cannot parse date: {}", rsQueryParams.getLowerDateTime(), e); //$NON-NLS-1$
            }
            if (lowerDateTime != null) {
                for (int i = studies.size() - 1; i >= 0; i--) {
                    Attributes s = studies.get(i);
                    Date date = s.getDate(Tag.StudyDateAndTime);
                    if (date != null) {
                        int rep = date.compareTo(lowerDateTime);
                        if (rep > 0) {
                            studies.remove(i);
                        }
                    }
                }
            }
        }

        if (StringUtil.hasText(rsQueryParams.getUpperDateTime())) {
            Date upperDateTime = null;
            try {
                upperDateTime = DateUtil.parseXmlDateTime(rsQueryParams.getUpperDateTime()).getTime();
            } catch (Exception e) {
                LOGGER.error("Cannot parse date: {}", rsQueryParams.getUpperDateTime(), e); //$NON-NLS-1$
            }
            if (upperDateTime != null) {
                for (int i = studies.size() - 1; i >= 0; i--) {
                    Attributes s = studies.get(i);
                    Date date = s.getDate(Tag.StudyDateAndTime);
                    if (date != null) {
                        int rep = date.compareTo(upperDateTime);
                        if (rep < 0) {
                            studies.remove(i);
                        }
                    }
                }
            }
        }

        if (StringUtil.hasText(rsQueryParams.getMostRecentResults())) {
            int recent = StringUtil.getInteger(rsQueryParams.getMostRecentResults());
            if (recent > 0) {
                for (int i = studies.size() - 1; i >= recent; i--) {
                    studies.remove(i);
                }
            }
        }

        if (StringUtil.hasText(rsQueryParams.getModalitiesInStudy())) {
            for (int i = studies.size() - 1; i >= 0; i--) {
                Attributes s = studies.get(i);
                String m = s.getString(Tag.ModalitiesInStudy);
                if (StringUtil.hasText(m)) {
                    boolean remove = true;
                    for (String mod : rsQueryParams.getModalitiesInStudy().split(",")) { //$NON-NLS-1$
                        if (m.indexOf(mod) != -1) {
                            remove = false;
                            break;
                        }
                    }

                    if (remove) {
                        studies.remove(i);
                    }
                }
            }

        }

        if (StringUtil.hasText(rsQueryParams.getKeywords())) {
            String[] keys = rsQueryParams.getKeywords().split(","); //$NON-NLS-1$
            for (int i = 0; i < keys.length; i++) {
                keys[i] = StringUtil.deAccent(keys[i].trim().toUpperCase());
            }

            studyLabel: for (int i = studies.size() - 1; i >= 0; i--) {
                Attributes s = studies.get(i);
                String desc = StringUtil.deAccent(s.getString(Tag.StudyDescription, "").toUpperCase()); //$NON-NLS-1$

                for (int j = 0; j < keys.length; j++) {
                    if (desc.contains(keys[j])) {
                        continue studyLabel;
                    }
                }
                studies.remove(i);
            }
        }

        for (Attributes studyDataSet : studies) {
            fillSeries(studyDataSet, defaultStartDownloading);
        }
    }

    private static Comparator<Attributes> getStudyComparator() {
        return (o1, o2) -> {
            Date date1 = o1.getDate(Tag.StudyDate);
            Date date2 = o2.getDate(Tag.StudyDate);
            if (date1 != null && date2 != null) {
                // inverse time
                int rep = date2.compareTo(date1);
                if (rep == 0) {
                    Date time1 = o1.getDate(Tag.StudyTime);
                    Date time2 = o2.getDate(Tag.StudyTime);
                    if (time1 != null && time2 != null) {
                        // inverse time
                        return time2.compareTo(time1);
                    }
                } else {
                    return rep;
                }
            }
            if (date1 == null && date2 == null) {
                return o1.getString(Tag.StudyInstanceUID, "").compareTo(o2.getString(Tag.StudyInstanceUID, "")); //$NON-NLS-1$ //$NON-NLS-2$
            } else {
                if (date1 == null) {
                    return 1;
                }
                if (date2 == null) {
                    return -1;
                }
            }
            return 0;
        };
    }

    public void buildFromStudyInstanceUID(List<String> studyInstanceUIDs) {
        buildFromStudyInstanceUID(studyInstanceUIDs, defaultStartDownloading);
    }

    public void buildFromStudyInstanceUID(List<String> studyInstanceUIDs, boolean startDownloading) {
        for (String studyInstanceUID : LangUtil.emptyIfNull(studyInstanceUIDs)) {
            if (!StringUtil.hasText(studyInstanceUID)) {
                continue;
            }
            StringBuilder buf = new StringBuilder(rsQueryParams.getBaseUrl());
            buf.append("/studies?0020000D="); //$NON-NLS-1$
            buf.append(studyInstanceUID);
            buf.append(STUDY_QUERY);
            buf.append(rsQueryParams.getProperties().getProperty(RsQueryParams.P_QUERY_EXT, "")); //$NON-NLS-1$

            try {
                LOGGER.debug(QIDO_REQUEST, buf);
                List<Attributes> studies = parseJSON(buf.toString());
                for (Attributes studyDataSet : studies) {
                    fillSeries(studyDataSet, startDownloading);
                }
            } catch (Exception e) {
                LOGGER.error("QIDO-RS with studyUID {}", studyInstanceUID, e); //$NON-NLS-1$
            }
        }
    }

    public void buildFromStudyAccessionNumber(List<String> accessionNumbers) {
        for (String accessionNumber : LangUtil.emptyIfNull(accessionNumbers)) {
            if (!StringUtil.hasText(accessionNumber)) {
                continue;
            }
            StringBuilder buf = new StringBuilder(rsQueryParams.getBaseUrl());
            buf.append("/studies?00080050="); //$NON-NLS-1$
            buf.append(accessionNumber);
            buf.append(STUDY_QUERY);
            buf.append(rsQueryParams.getProperties().getProperty(RsQueryParams.P_QUERY_EXT, "")); //$NON-NLS-1$

            try {
                LOGGER.debug(QIDO_REQUEST, buf);
                List<Attributes> studies = parseJSON(buf.toString());
                for (Attributes studyDataSet : studies) {
                    fillSeries(studyDataSet, defaultStartDownloading);
                }
            } catch (Exception e) {
                LOGGER.error("QIDO-RS with AccessionNumber {}", accessionNumber, e); //$NON-NLS-1$
            }
        }
    }

    public void buildFromSeriesInstanceUID(List<String> seriesInstanceUIDs) {
        boolean wholeStudy =
            LangUtil.getEmptytoFalse(rsQueryParams.getProperties().getProperty(RsQueryParams.P_SHOW_WHOLE_STUDY));
        Set<String> studyHashSet = new LinkedHashSet<>();

        for (String seriesInstanceUID : LangUtil.emptyIfNull(seriesInstanceUIDs)) {
            if (!StringUtil.hasText(seriesInstanceUID)) {
                continue;
            }

            StringBuilder buf = new StringBuilder(rsQueryParams.getBaseUrl());
            buf.append("/series?0020000E="); //$NON-NLS-1$
            buf.append(seriesInstanceUID);
            buf.append(STUDY_QUERY);
            buf.append(",0008103E,00080060,00081190,00200011"); //$NON-NLS-1$
            buf.append(rsQueryParams.getProperties().getProperty(RsQueryParams.P_QUERY_EXT, "")); //$NON-NLS-1$

            try {
                LOGGER.debug(QIDO_REQUEST, buf);
                List<Attributes> series = parseJSON(buf.toString());
                if (!series.isEmpty()) {
                    Attributes dataset = series.get(0);
                    MediaSeriesGroup patient = getPatient(dataset);
                    MediaSeriesGroup study = getStudy(patient, dataset);
                    for (Attributes seriesDataset : series) {
                        Series<?> dicomSeries = getSeries(study, seriesDataset, defaultStartDownloading);
                        fillInstance(seriesDataset, study, dicomSeries);
                    }
                    studyHashSet.add(dataset.getString(Tag.StudyInstanceUID));
                }
            } catch (Exception e) {
                LOGGER.error("QIDO-RS with seriesUID {}", seriesInstanceUID, e); //$NON-NLS-1$
            }
        }
        
        if (wholeStudy) {
            buildFromStudyInstanceUID(new ArrayList<>(studyHashSet), false);
        }
    }

    public void buildFromSopInstanceUID(List<String> sopInstanceUIDs) {
        for (String sopInstanceUID : LangUtil.emptyIfNull(sopInstanceUIDs)) {
            if (!StringUtil.hasText(sopInstanceUID)) {
                continue;
            }

            StringBuilder buf = new StringBuilder(rsQueryParams.getBaseUrl());
            buf.append("/instances?00080018="); //$NON-NLS-1$
            buf.append(sopInstanceUID);
            buf.append(STUDY_QUERY);
            buf.append(",0008103E,00080060,0020000E,00200011"); //$NON-NLS-1$
            buf.append(",00200013,00081190"); //$NON-NLS-1$
            buf.append(rsQueryParams.getProperties().getProperty(RsQueryParams.P_QUERY_EXT, "")); //$NON-NLS-1$

            try {
                LOGGER.debug(QIDO_REQUEST, buf);
                List<Attributes> instances = parseJSON(buf.toString());
                if (!instances.isEmpty()) {
                    Attributes dataset = instances.get(0);
                    MediaSeriesGroup patient = getPatient(dataset);
                    MediaSeriesGroup study = getStudy(patient, dataset);
                    Series<?> dicomSeries = getSeries(study, dataset, defaultStartDownloading);
                    String seriesRetrieveURL = TagD.getTagValue(dicomSeries, Tag.RetrieveURL, String.class);
                    SeriesInstanceList seriesInstanceList =
                        (SeriesInstanceList) dicomSeries.getTagValue(TagW.WadoInstanceReferenceList);
                    if (seriesInstanceList != null) {
                        for (Attributes instanceDataSet : instances) {
                            addSopInstance(instanceDataSet, seriesInstanceList, seriesRetrieveURL);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("QIDO-RS with sopInstanceUID {}", sopInstanceUID, e); //$NON-NLS-1$
            }
        }
    }

    private void fillSeries(Attributes studyDataSet, boolean startDownloading) {
        String studyInstanceUID = studyDataSet.getString(Tag.StudyInstanceUID);
        if (StringUtil.hasText(studyInstanceUID)) {
            StringBuilder buf = new StringBuilder(rsQueryParams.getBaseUrl());
            buf.append("/studies/"); //$NON-NLS-1$
            buf.append(studyInstanceUID);
            buf.append("/series?includefield="); //$NON-NLS-1$
            buf.append(SERIES_QUERY);
            buf.append(rsQueryParams.getProperties().getProperty(RsQueryParams.P_QUERY_EXT, "")); //$NON-NLS-1$

            try {
                LOGGER.debug(QIDO_REQUEST, buf);
                List<Attributes> series = parseJSON(buf.toString());
                if (!series.isEmpty()) {
                    // Get patient from each study in case IssuerOfPatientID is different
                    MediaSeriesGroup patient = getPatient(studyDataSet);
                    MediaSeriesGroup study = getStudy(patient, studyDataSet);
                    for (Attributes seriesDataset : series) {
                        Series<?> dicomSeries = getSeries(study, seriesDataset, startDownloading);
                        fillInstance(seriesDataset, study, dicomSeries);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("QIDO-RS all series with studyUID {}", studyInstanceUID, e); //$NON-NLS-1$
            }
        }
    }

    private void fillInstance(Attributes seriesDataset, MediaSeriesGroup study, Series<?> dicomSeries) {
        String serieInstanceUID = seriesDataset.getString(Tag.SeriesInstanceUID);
        if (StringUtil.hasText(serieInstanceUID)) {
            String seriesRetrieveURL = TagD.getTagValue(dicomSeries, Tag.RetrieveURL, String.class);
            StringBuilder buf = new StringBuilder(seriesRetrieveURL);
            buf.append("/instances?includefield="); //$NON-NLS-1$
            buf.append(INSTANCE_QUERY);
            buf.append(rsQueryParams.getProperties().getProperty(RsQueryParams.P_QUERY_EXT, "")); //$NON-NLS-1$

            try {
                LOGGER.debug(QIDO_REQUEST, buf);
                List<Attributes> instances = parseJSON(buf.toString());
                if (!instances.isEmpty()) {
                    SeriesInstanceList seriesInstanceList =
                        (SeriesInstanceList) dicomSeries.getTagValue(TagW.WadoInstanceReferenceList);
                    if (seriesInstanceList != null) {
                        for (Attributes instanceDataSet : instances) {
                            addSopInstance(instanceDataSet, seriesInstanceList, seriesRetrieveURL);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("QIDO-RS all instances with seriesUID {}", serieInstanceUID, e); //$NON-NLS-1$
            }
        }
    }

    private void addSopInstance(Attributes instanceDataSet, SeriesInstanceList seriesInstanceList,
        String seriesRetrieveURL) {
        String sopUID = instanceDataSet.getString(Tag.SOPInstanceUID);
        Integer frame = DicomMediaUtils.getIntegerFromDicomElement(instanceDataSet, Tag.InstanceNumber, null);

        SopInstance sop = seriesInstanceList.getSopInstance(sopUID, frame);
        if (sop == null) {
            sop = new SopInstance(sopUID, frame);
            String rurl = instanceDataSet.getString(Tag.RetrieveURL);
            if (!StringUtil.hasText(rurl)) {
                StringBuilder b = new StringBuilder(seriesRetrieveURL);
                b.append("/instances/"); //$NON-NLS-1$
                b.append(sopUID);
                rurl = b.toString();
            }
            sop.setDirectDownloadFile(rurl);
            seriesInstanceList.addSopInstance(sop);
        }
    }

    private MediaSeriesGroup getPatient(Attributes patientDataset) {
        if (patientDataset == null) {
            throw new IllegalArgumentException("patientDataset cannot be null"); //$NON-NLS-1$
        }

        PatientComparator patientComparator = new PatientComparator(patientDataset);
        String patientPseudoUID = patientComparator.buildPatientPseudoUID();

        DicomModel model = rsQueryParams.getDicomModel();
        MediaSeriesGroup patient = model.getHierarchyNode(MediaSeriesGroupNode.rootNode, patientPseudoUID);
        if (patient == null) {
            patient =
                new MediaSeriesGroupNode(TagD.getUID(Level.PATIENT), patientPseudoUID, DicomModel.patient.getTagView());
            patient.setTag(TagD.get(Tag.PatientID), patientComparator.getPatientId());
            patient.setTag(TagD.get(Tag.PatientName), patientComparator.getName());
            patient.setTagNoNull(TagD.get(Tag.IssuerOfPatientID), patientComparator.getIssuerOfPatientID());

            TagW[] tags = TagD.getTagFromIDs(Tag.PatientSex, Tag.PatientBirthDate, Tag.PatientBirthTime);
            for (TagW tag : tags) {
                tag.readValue(patientDataset, patient);
            }

            model.addHierarchyNode(MediaSeriesGroupNode.rootNode, patient);
            LOGGER.info("Adding new patient: {}", patient); //$NON-NLS-1$
        }
        return patient;
    }

    private MediaSeriesGroup getStudy(MediaSeriesGroup patient, final Attributes studyDataset) {
        if (studyDataset == null) {
            throw new IllegalArgumentException("studyDataset cannot be null"); //$NON-NLS-1$
        }
        String studyUID = studyDataset.getString(Tag.StudyInstanceUID);
        DicomModel model = rsQueryParams.getDicomModel();
        MediaSeriesGroup study = model.getHierarchyNode(patient, studyUID);
        if (study == null) {
            study = new MediaSeriesGroupNode(TagD.getUID(Level.STUDY), studyUID, DicomModel.study.getTagView());
            TagW[] tags = TagD.getTagFromIDs(Tag.StudyDate, Tag.StudyTime, Tag.StudyDescription, Tag.AccessionNumber,
                Tag.StudyID, Tag.ReferringPhysicianName);
            for (TagW tag : tags) {
                tag.readValue(studyDataset, study);
            }

            model.addHierarchyNode(patient, study);
        }
        return study;
    }

    private Series getSeries(MediaSeriesGroup study, final Attributes seriesDataset, boolean startDownloading) {
        if (seriesDataset == null) {
            throw new IllegalArgumentException("seriesDataset cannot be null"); //$NON-NLS-1$
        }
        String seriesUID = seriesDataset.getString(Tag.SeriesInstanceUID);
        DicomModel model = rsQueryParams.getDicomModel();
        Series dicomSeries = (Series) model.getHierarchyNode(study, seriesUID);
        if (dicomSeries == null) {
            dicomSeries = new DicomSeries(seriesUID);
            dicomSeries.setTag(TagD.get(Tag.SeriesInstanceUID), seriesUID);
            dicomSeries.setTag(TagW.ExplorerModel, model);
            dicomSeries.setTag(TagW.WadoParameters, wadoParameters);
            dicomSeries.setTag(TagW.WadoInstanceReferenceList, new SeriesInstanceList());

            TagW[] tags = TagD.getTagFromIDs(Tag.Modality, Tag.SeriesNumber, Tag.SeriesDescription, Tag.RetrieveURL);
            for (TagW tag : tags) {
                tag.readValue(seriesDataset, dicomSeries);
            }
            if (!StringUtil.hasText(TagD.getTagValue(dicomSeries, Tag.RetrieveURL, String.class))) {
                StringBuilder buf = new StringBuilder(rsQueryParams.getBaseUrl());
                buf.append("/studies/"); //$NON-NLS-1$
                buf.append(study.getTagValue(TagD.get(Tag.StudyInstanceUID)));
                buf.append("/series/"); //$NON-NLS-1$
                buf.append(seriesUID);
                dicomSeries.setTag(TagD.get(Tag.RetrieveURL), buf.toString());
            }

            model.addHierarchyNode(study, dicomSeries);

            final LoadSeries loadSeries = new LoadSeries(dicomSeries, rsQueryParams.getDicomModel(),
                BundleTools.SYSTEM_PREFERENCES.getIntProperty(LoadSeries.CONCURRENT_DOWNLOADS_IN_SERIES, 4), true,
                startDownloading);
            loadSeries.setPriority(
                new DownloadPriority(model.getParent(study, DicomModel.patient), study, dicomSeries, true));
            rsQueryParams.getSeriesMap().put(TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class),
                loadSeries);
        }
        return dicomSeries;
    }
}