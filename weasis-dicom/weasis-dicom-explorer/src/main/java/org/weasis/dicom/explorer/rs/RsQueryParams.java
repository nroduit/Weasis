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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import javax.swing.JOptionPane;

import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.core.util.StringUtil.Suffix;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExplorerTask;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.wado.DownloadManager;
import org.weasis.dicom.explorer.wado.DownloadManager.PriorityTaskComparator;
import org.weasis.dicom.explorer.wado.LoadSeries;
import org.weasis.dicom.mf.WadoParameters;
import org.weasis.dicom.web.InvokeImageDisplay;

public class RsQueryParams extends ExplorerTask<Boolean, String> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RsQueryParams.class);

    public static final String P_DICOMWEB_URL = "dicomweb.url"; //$NON-NLS-1$
    public static final String P_QUERY_EXT = "query.ext"; //$NON-NLS-1$
    public static final String P_RETRIEVE_EXT = "retrieve.ext"; //$NON-NLS-1$
    public static final String P_SHOW_WHOLE_STUDY = "show.whole.study"; //$NON-NLS-1$
    public static final String P_ACCEPT_EXT = "accept.ext"; //$NON-NLS-1$

    private final DicomModel dicomModel;
    private final Map<String, LoadSeries> seriesMap;
    private final Properties properties;
    private final Map<String, String> queryHeaders;
    private final Map<String, String> retrieveHeaders;
    protected final Map<String, List<String>> requestMap;
    protected final RsQueryResult arcConfig;

    public RsQueryParams(DicomModel dicomModel, Properties properties, Map<String, List<String>> requestMap,
        Map<String, String> queryHeaders, Map<String, String> retrieveHeaders) {
        super(Messages.getString("DicomExplorer.loading"), true); //$NON-NLS-1$
        this.dicomModel = Objects.requireNonNull(dicomModel);
        this.properties = Objects.requireNonNull(properties);
        this.requestMap = Objects.requireNonNull(requestMap);
        String url = properties.getProperty(P_DICOMWEB_URL);
        if (StringUtil.hasText(url)) {
            if (url.endsWith("/")) { //$NON-NLS-1$
                properties.setProperty(P_DICOMWEB_URL, url.substring(0, url.length() - 1));
            }
        } else {
            throw new IllegalArgumentException("DICOMWeb URL cannot be null"); //$NON-NLS-1$
        }

        this.seriesMap = new HashMap<>();
        this.queryHeaders = queryHeaders == null ? Collections.emptyMap() : queryHeaders;
        this.retrieveHeaders = retrieveHeaders == null ? Collections.emptyMap() : retrieveHeaders;
        this.arcConfig = new RsQueryResult(this);
    }

    public static Map<String, String> getHeaders(List<String> urlHeaders) {
        Map<String, String> headers = new HashMap<>();
        for (String h : LangUtil.emptyIfNull(urlHeaders)) {
            String[] val = h.split(":", 2); //$NON-NLS-1$
            if (val.length == 1) {
                headers.put(val[0].trim().toLowerCase(), ""); //$NON-NLS-1$
            } else if (val.length == 2) {
                String name = val[0].trim().toLowerCase();
                String value = val[1].trim();
                // Hack for dcm4chee-arc integration
                if ("authorization".equals(name) && value.length() > 14 && value.startsWith("&access_token=")) { //$NON-NLS-1$ //$NON-NLS-2$
                    value = "Bearer " + value.substring(14); //$NON-NLS-1$
                }
                headers.put(name, value);
            }
        }
        return headers;
    }

    public static Map<String, List<String>> getQueryMap(String query) {
        String[] params = query.split("&"); //$NON-NLS-1$
        Map<String, List<String>> map = new HashMap<>();
        for (String param : params) {
            String[] val = param.split("=", 2); //$NON-NLS-1$
            String name = val[0];
            if (!name.isEmpty()) {
                List<String> v = map.get(name);
                if (v == null) {
                    v = new ArrayList<>();
                    map.put(val[0], v);
                }
                if (val.length == 1) {
                    v.add(""); //$NON-NLS-1$
                } else if (val.length == 2) {
                    v.add(val[1]);
                }
            }
        }
        return map;
    }

    @Override
    protected Boolean doInBackground() throws Exception {
        fillPatientList();
        WadoParameters wp = new WadoParameters("", true, true); //$NON-NLS-1$
        getRetrieveHeaders().forEach(wp::addHttpTag);
        wp.addHttpTag("Accept", "image/jpeg"); //$NON-NLS-1$ //$NON-NLS-2$
        
        for (final LoadSeries loadSeries : seriesMap.values()) {
            String modality = TagD.getTagValue(loadSeries.getDicomSeries(), Tag.Modality, String.class);
            boolean ps = modality != null && ("PR".equals(modality) || "KO".equals(modality)); //$NON-NLS-1$ //$NON-NLS-2$
            if (!ps) {
                loadSeries.startDownloadImageReference(wp);
            }
            DownloadManager.addLoadSeries(loadSeries, dicomModel, loadSeries.isStartDownloading());
        }

        // Sort tasks from the download priority order (low number has a higher priority), TASKS
        // is sorted from low to high priority).
        Collections.sort(DownloadManager.TASKS, Collections.reverseOrder(new PriorityTaskComparator()));

        DownloadManager.CONCURRENT_EXECUTOR.prestartAllCoreThreads();
        return true;
    }

    private void fillPatientList() {
        try {
            String requestType = getRequestType();

            if (InvokeImageDisplay.STUDY_LEVEL.equals(requestType)) {
                String stuID = getReqStudyUID();
                String anbID = getReqAccessionNumber();
                if (StringUtil.hasText(anbID)) {
                    arcConfig.buildFromStudyAccessionNumber(Arrays.asList(anbID));
                } else if (StringUtil.hasText(stuID)) {
                    arcConfig.buildFromStudyInstanceUID(Arrays.asList(stuID));

                } else {
                    LOGGER.error("Not ID found for STUDY request type: {}", requestType); //$NON-NLS-1$
                    showErrorMessage(Messages.getString("RsQueryParams.missing_study_uid"), Messages.getString("RsQueryParams.no_sudy_uid")); //$NON-NLS-1$ //$NON-NLS-2$
                }
            } else if (InvokeImageDisplay.PATIENT_LEVEL.equals(requestType)) {
                String patID = getReqPatientID();
                if (StringUtil.hasText(patID)) {
                    arcConfig.buildFromPatientID(Arrays.asList(patID));
                }
            } else if (requestType != null) {
                LOGGER.error("Not supported IID request type: {}", requestType); //$NON-NLS-1$
                showErrorMessage(Messages.getString("RsQueryParams.unexpect_req"), Messages.getString("RsQueryParams.idd_type") + requestType); //$NON-NLS-1$ //$NON-NLS-2$
            } else {
                arcConfig.buildFromSopInstanceUID(getReqObjectUIDs());
                arcConfig.buildFromSeriesInstanceUID(getReqSeriesUIDs());
                arcConfig.buildFromStudyAccessionNumber(getReqAccessionNumbers());
                arcConfig.buildFromStudyInstanceUID(getReqStudyUIDs());
                arcConfig.buildFromPatientID(getReqPatientIDs());
            }
        } catch (Exception e) {
            LOGGER.error("Error when building the patient list", e); //$NON-NLS-1$
            showErrorMessage(Messages.getString("RsQueryParams.unexpect_error"), Messages.getString("RsQueryParams.error_build_mf") + StringUtil.COLON + "\n" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + StringUtil.getTruncatedString(e.getMessage(), 130, Suffix.THREE_PTS));
        }
    }

    private static void showErrorMessage(String title, String msg) {
        GuiExecutor.instance()
            .execute(() -> JOptionPane.showMessageDialog(UIManager.BASE_AREA, msg, title, JOptionPane.ERROR_MESSAGE));
    }

    private static String getFirstParam(List<String> list) {
        if (list != null && !list.isEmpty()) {
            return list.get(0);
        }
        return null;
    }

    public boolean isAcceptNoImage() {
        return LangUtil.getEmptytoFalse(properties.getProperty("accept.noimage")); //$NON-NLS-1$
    }

    public DicomModel getDicomModel() {
        return dicomModel;
    }

    public Map<String, LoadSeries> getSeriesMap() {
        return seriesMap;
    }

    public Properties getProperties() {
        return properties;
    }

    public Map<String, String> getQueryHeaders() {
        return queryHeaders;
    }

    public Map<String, String> getRetrieveHeaders() {
        return retrieveHeaders;
    }

    public String getBaseUrl() {
        return properties.getProperty(P_DICOMWEB_URL);
    }

    public boolean hasPatients() {
        return !arcConfig.getPatients().isEmpty();
    }

    public void clearAllPatients() {
        arcConfig.getPatients().clear();
    }

    public void removePatientId(List<String> patientIdList, boolean containsIssuer) {
        arcConfig.removePatientId(patientIdList, containsIssuer);
    }

    public void removeStudyUid(List<String> studyUidList) {
        arcConfig.removeStudyUid(studyUidList);
    }

    public void removeAccessionNumber(List<String> accessionNumberList) {
        arcConfig.removeAccessionNumber(accessionNumberList);
    }

    public void removeSeriesUid(List<String> seriesUidList) {
        arcConfig.removeSeriesUid(seriesUidList);
    }

    public String getRequestType() {
        return getFirstParam(requestMap.get(InvokeImageDisplay.REQUEST_TYPE));
    }

    public String getReqPatientID() {
        return getFirstParam(requestMap.get(InvokeImageDisplay.PATIENT_ID));
    }

    public String getPatientName() {
        return getFirstParam(requestMap.get(InvokeImageDisplay.PATIENT_NAME));
    }

    public String getPatientBirthDate() {
        return getFirstParam(requestMap.get(InvokeImageDisplay.PATIENT_BIRTHDATE));
    }

    public String getLowerDateTime() {
        return getFirstParam(requestMap.get(InvokeImageDisplay.LOWER_DATETIME));
    }

    public String getUpperDateTime() {
        return getFirstParam(requestMap.get(InvokeImageDisplay.UPPER_DATETIME));
    }

    public String getMostRecentResults() {
        return getFirstParam(requestMap.get(InvokeImageDisplay.MOST_RECENT_RESULTS));
    }

    public String getKeywords() {
        return getFirstParam(requestMap.get(InvokeImageDisplay.KEYWORDS));
    }

    public String getModalitiesInStudy() {
        return getFirstParam(requestMap.get(InvokeImageDisplay.MODALITIES_IN_STUDY));
    }

    public String getReqStudyUID() {
        return getFirstParam(requestMap.get(InvokeImageDisplay.STUDY_UID));
    }

    public String getReqAccessionNumber() {
        return getFirstParam(requestMap.get(InvokeImageDisplay.ACCESSION_NUMBER));
    }

    public String getReqSeriesUID() {
        return getFirstParam(requestMap.get(InvokeImageDisplay.SERIES_UID));
    }

    public String getReqObjectUID() {
        return getFirstParam(requestMap.get(InvokeImageDisplay.OBJECT_UID));
    }

    public List<String> getReqPatientIDs() {
        return requestMap.get(InvokeImageDisplay.PATIENT_ID);
    }

    public List<String> getReqStudyUIDs() {
        return requestMap.get(InvokeImageDisplay.STUDY_UID);
    }

    public List<String> getReqAccessionNumbers() {
        return requestMap.get(InvokeImageDisplay.ACCESSION_NUMBER);
    }

    public List<String> getReqSeriesUIDs() {
        return requestMap.get(InvokeImageDisplay.SERIES_UID);
    }

    public List<String> getReqObjectUIDs() {
        return requestMap.get(InvokeImageDisplay.OBJECT_UID);
    }
}