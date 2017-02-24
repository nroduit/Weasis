/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.explorer.wado;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker.StateValue;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.dicom.codec.DicomInstance;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.codec.wado.WadoParameters;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.DicomSorter;
import org.weasis.dicom.explorer.Messages;
import org.xml.sax.SAXException;

public class DownloadManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadManager.class);

    public static final String SCHEMA =
        "xmlns=\"http://manifest.service.weasis/v2.5\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""; //$NON-NLS-1$
    public static final String TAG_XML_ROOT = "manifest"; //$NON-NLS-1$
    public static final String TAG_ARC_QUERY = "arcQuery"; //$NON-NLS-1$
    public static final String TAG_ARCHIVE_ID = "arcId"; //$NON-NLS-1$
    public static final String TAG_BASE_URL = "baseUrl"; //$NON-NLS-1$
    public static final String TAG_PR_ROOT = "presentations"; //$NON-NLS-1$
    public static final String TAG_PR = "presentation"; //$NON-NLS-1$

    public static final String CONCURRENT_SERIES = "download.concurrent.series"; //$NON-NLS-1$
    public static final List<LoadSeries> TASKS = new ArrayList<>();

    // Executor without concurrency (only one task is executed at the same time)
    private static final BlockingQueue<Runnable> UNIQUE_QUEUE =
        new PriorityBlockingQueue<>(10, new PriorityTaskComparator());
    public static final ThreadPoolExecutor UNIQUE_EXECUTOR =
        new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, UNIQUE_QUEUE);

    // Executor with simultaneous tasks
    private static final BlockingQueue<Runnable> PRIORITY_QUEUE =
        new PriorityBlockingQueue<>(10, new PriorityTaskComparator());
    public static final ThreadPoolExecutor CONCURRENT_EXECUTOR = new ThreadPoolExecutor(
        BundleTools.SYSTEM_PREFERENCES.getIntProperty(CONCURRENT_SERIES, 3),
        BundleTools.SYSTEM_PREFERENCES.getIntProperty(CONCURRENT_SERIES, 3), 0L, TimeUnit.MILLISECONDS, PRIORITY_QUEUE);

    public static class PriorityTaskComparator implements Comparator<Runnable>, Serializable {

        private static final long serialVersionUID = 513213203958362767L;

        @Override
        public int compare(final Runnable r1, final Runnable r2) {
            LoadSeries o1 = (LoadSeries) r1;
            LoadSeries o2 = (LoadSeries) r2;
            DownloadPriority val1 = o1.getPriority();
            DownloadPriority val2 = o2.getPriority();

            int rep = val1.getPriority().compareTo(val2.getPriority());
            if (rep != 0) {
                return rep;
            }
            if (val1.getPatient() != val2.getPatient()) {
                rep = DicomSorter.PATIENT_COMPARATOR.compare(val1.getPatient(), val2.getPatient());
            }
            if (rep != 0) {
                return rep;
            }

            if (val1.getStudy() != val2.getStudy()) {
                rep = DicomSorter.STUDY_COMPARATOR.compare(val1.getStudy(), val2.getStudy());
            }
            if (rep != 0) {
                return rep;
            }
            return DicomSorter.SERIES_COMPARATOR.compare(val1.getSeries(), val2.getSeries());
        }
    }

    private DownloadManager() {
    }

    public static boolean removeSeriesInQueue(final LoadSeries series) {
        return series.getPriority().hasConcurrentDownload() ? DownloadManager.PRIORITY_QUEUE.remove(series)
            : DownloadManager.UNIQUE_QUEUE.remove(series);
    }

    public static void offerSeriesInQueue(final LoadSeries series) {
        if (series.getPriority().hasConcurrentDownload()) {
            DownloadManager.PRIORITY_QUEUE.offer(series);
        } else {
            DownloadManager.UNIQUE_QUEUE.offer(series);
        }
    }

    public static synchronized void addLoadSeries(final LoadSeries series, DicomModel dicomModel,
        boolean startLoading) {
        if (series != null) {
            if (startLoading) {
                offerSeriesInQueue(series);
            } else {
                GuiExecutor.instance().execute(() -> {
                    series.getProgressBar().setValue(0);
                    series.stop();
                });
            }
            if (dicomModel != null) {
                dicomModel.firePropertyChange(
                    new ObservableEvent(ObservableEvent.BasicAction.LOADING_START, dicomModel, null, series));
            }
            if (!DownloadManager.TASKS.contains(series)) {
                DownloadManager.TASKS.add(series);
            }
        }
    }

    public static synchronized void removeLoadSeries(LoadSeries series, DicomModel dicomModel) {
        if (series != null) {
            DownloadManager.TASKS.remove(series);
            if (dicomModel != null) {
                dicomModel.firePropertyChange(
                    new ObservableEvent(ObservableEvent.BasicAction.LOADING_STOP, dicomModel, null, series));
            }
            if (DownloadManager.TASKS.isEmpty()) {
                // When all loadseries are ended, reset to default the number of simultaneous download (series)
                DownloadManager.CONCURRENT_EXECUTOR.setCorePoolSize(
                    BundleTools.SYSTEM_PREFERENCES.getIntProperty(DownloadManager.CONCURRENT_SERIES, 3));
            }
        }
    }

    public static void stopDownloading(DicomSeries series, DicomModel dicomModel) {
        if (series != null) {
            synchronized (DownloadManager.TASKS) {
                for (final LoadSeries loading : DownloadManager.TASKS) {
                    if (loading.getDicomSeries() == series) {
                        removeLoadSeries(loading, dicomModel);
                        removeSeriesInQueue(loading);
                        if (StateValue.STARTED.equals(loading.getState())) {
                            loading.cancel(true);
                        }
                        // Ensure to stop downloading
                        series.setSeriesLoader(null);
                        break;
                    }
                }
            }
        }
    }

    public static void resume() {
        handleAllSeries(loadSeries -> loadSeries.resume());
    }

    public static void stop() {
        handleAllSeries(loadSeries -> loadSeries.stop());
    }

    private static void handleAllSeries(LoadSeriesHandler handler) {
        for (LoadSeries loadSeries : new ArrayList<>(DownloadManager.TASKS)) {
            handler.handle(loadSeries);
            Thumbnail thumbnail = (Thumbnail) loadSeries.getDicomSeries().getTagValue(TagW.Thumbnail);
            if (thumbnail != null) {
                thumbnail.repaint();
            }
        }
    }

    @FunctionalInterface
    private static interface LoadSeriesHandler {
        void handle(LoadSeries loadSeries);
    }

    public static List<LoadSeries> buildDicomSeriesFromXml(URI uri, final DicomModel model) throws WeasisDownloadException {
        ArrayList<LoadSeries> seriesList = new ArrayList<>();
        XMLStreamReader xmler = null;
        InputStream stream = null;
        try {
            XMLInputFactory xmlif = XMLInputFactory.newInstance();

            String path = uri.getPath();

            URL url = uri.toURL();
            URLConnection urlConnection = url.openConnection();

            if (BundleTools.SESSION_TAGS_MANIFEST.size() > 0) {
                for (Iterator<Entry<String, String>> iter =
                    BundleTools.SESSION_TAGS_MANIFEST.entrySet().iterator(); iter.hasNext();) {
                    Entry<String, String> element = iter.next();
                    urlConnection.setRequestProperty(element.getKey(), element.getValue());
                }
            }

            LOGGER.info("Downloading WADO references: {}", url); //$NON-NLS-1$
            logHttpError(urlConnection);

            if (path.endsWith(".gz")) { //$NON-NLS-1$
                stream = new BufferedInputStream(new GZIPInputStream(urlConnection.getInputStream()));
            } else if (path.endsWith(".xml")) { //$NON-NLS-1$
                stream = urlConnection.getInputStream();
            } else {
                // In case wado file has no extension
                File outFile = File.createTempFile("wado_", "", AppProperties.APP_TEMP_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                if (FileUtil.writeStream(urlConnection.getInputStream(), new FileOutputStream(outFile)) == -1) {
                    if (MimeInspector.isMatchingMimeTypeFromMagicNumber(outFile, "application/x-gzip")) { //$NON-NLS-1$
                        stream = new BufferedInputStream(new GZIPInputStream(new FileInputStream(outFile)));
                    } else {
                        stream = new FileInputStream(outFile);
                    }
                }
            }

            File tempFile = null;
            if (uri.toString().startsWith("file:") && path.endsWith(".xml")) { //$NON-NLS-1$ //$NON-NLS-2$
                tempFile = new File(path);
            } else {
                tempFile = File.createTempFile("wado_", ".xml", AppProperties.APP_TEMP_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                FileUtil.writeStream(stream, new FileOutputStream(tempFile));
            }
            xmler = xmlif.createXMLStreamReader(new FileInputStream(tempFile));

            Source xmlFile = new StAXSource(xmler);
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            try {
                Schema schema = schemaFactory.newSchema(new Source[] {
                    new StreamSource(DownloadManager.class.getResource("/config/wado_query.xsd").toExternalForm()), //$NON-NLS-1$
                    new StreamSource(DownloadManager.class.getResource("/config/wado_query25.xsd").toExternalForm()) }); //$NON-NLS-1$
                Validator validator = schema.newValidator();
                validator.validate(xmlFile);
                LOGGER.info("[Validate with XSD schema] wado_query is valid"); //$NON-NLS-1$
            } catch (SAXException e) {
                LOGGER.error("[Validate with XSD schema] wado_query is NOT valid", e); //$NON-NLS-1$
            } catch (Exception e) {
                LOGGER.error("Error when validate XSD schema. Try to update JRE", e); //$NON-NLS-1$
            }
            // Try to read the xml even it is not valid.
            xmler = xmlif.createXMLStreamReader(new FileInputStream(tempFile));
            int eventType;
            if (xmler.hasNext()) {
                eventType = xmler.next();
                switch (eventType) {
                    case XMLStreamConstants.START_ELEMENT:
                        String key = xmler.getName().getLocalPart();
                        // xmlns="http://www.weasis.org/xsd/2.5"
                        if (TAG_XML_ROOT.equals(key)) {
                            boolean state = true;
                            while (xmler.hasNext() && state) {
                                eventType = xmler.next();
                                switch (eventType) {
                                    case XMLStreamConstants.START_ELEMENT:
                                        key = xmler.getName().getLocalPart();
                                        if (TAG_ARC_QUERY.equals(key)) {
                                            readArcQuery(model, seriesList, xmler);
                                        } else if (TAG_PR_ROOT.equals(key)) {
                                            // TODO implement reader of presentation
                                            // GraphicList list = XmlSerializer.readMeasurementGraphics(gpxFile);
                                            // if (list != null) {
                                            // loader.setTag(TagW.MeasurementGraphics, list);
                                            // }
                                        }
                                        break;
                                    case XMLStreamConstants.END_ELEMENT:
                                        if (TAG_XML_ROOT.equals(xmler.getName().getLocalPart())) {
                                            state = false;
                                        }
                                        break;
                                    default:
                                        break;
                                }
                            }
                        } else {
                            // Read old manifest: xmlns="http://www.weasis.org/xsd"
                            if (WadoParameters.TAG_DOCUMENT_ROOT.equals(key)) {
                                readWadoQuery(model, seriesList, xmler);
                            }
                        }
                        break;
                    default:
                        break;
                }
            }

        } catch (IOException | XMLStreamException e) {
            final String message = Messages.getString("DownloadManager.error_load_xml") + "\n" + uri.toString(); //$NON-NLS-1$//$NON-NLS-2$
            LOGGER.error("{}", message, e); //$NON-NLS-1$
            throw new WeasisDownloadException(message, e);

        } finally {
            FileUtil.safeClose(xmler);
            FileUtil.safeClose(stream);
        }
        return seriesList;
    }

    private static void readArcQuery(DicomModel model, ArrayList<LoadSeries> seriesList, XMLStreamReader xmler)
        throws XMLStreamException {
        String wadoURL = TagUtil.getTagAttribute(xmler, TAG_BASE_URL, null);
        boolean onlySopUID =
            Boolean.parseBoolean(TagUtil.getTagAttribute(xmler, WadoParameters.TAG_WADO_ONLY_SOP_UID, "false")); //$NON-NLS-1$
        String additionnalParameters =
            TagUtil.getTagAttribute(xmler, WadoParameters.TAG_WADO_ADDITIONNAL_PARAMETERS, ""); //$NON-NLS-1$
        String overrideList = TagUtil.getTagAttribute(xmler, WadoParameters.TAG_WADO_OVERRIDE_TAGS, null);
        String webLogin = TagUtil.getTagAttribute(xmler, WadoParameters.TAG_WADO_WEB_LOGIN, null);
        final WadoParameters wadoParameters =
            new WadoParameters(wadoURL, onlySopUID, additionnalParameters, overrideList, webLogin);
        readQuery(model, seriesList, xmler, wadoParameters, TAG_ARC_QUERY);
    }

    private static void readWadoQuery(DicomModel model, ArrayList<LoadSeries> seriesList, XMLStreamReader xmler)
        throws XMLStreamException {
        String wadoURL = TagUtil.getTagAttribute(xmler, WadoParameters.TAG_WADO_URL, null);
        boolean onlySopUID =
            Boolean.parseBoolean(TagUtil.getTagAttribute(xmler, WadoParameters.TAG_WADO_ONLY_SOP_UID, "false")); //$NON-NLS-1$
        String additionnalParameters =
            TagUtil.getTagAttribute(xmler, WadoParameters.TAG_WADO_ADDITIONNAL_PARAMETERS, ""); //$NON-NLS-1$
        String overrideList = TagUtil.getTagAttribute(xmler, WadoParameters.TAG_WADO_OVERRIDE_TAGS, null);
        String webLogin = TagUtil.getTagAttribute(xmler, WadoParameters.TAG_WADO_WEB_LOGIN, null);
        final WadoParameters wadoParameters =
            new WadoParameters(wadoURL, onlySopUID, additionnalParameters, overrideList, webLogin);
        readQuery(model, seriesList, xmler, wadoParameters, WadoParameters.TAG_DOCUMENT_ROOT);
    }

    private static void readQuery(DicomModel model, ArrayList<LoadSeries> seriesList, XMLStreamReader xmler,
        final WadoParameters wadoParameters, String endElement) throws XMLStreamException {
        int pat = 0;
        MediaSeriesGroup patient = null;
        boolean state = true;
        while (xmler.hasNext() && state) {
            int eventType = xmler.next();
            switch (eventType) {
                case XMLStreamConstants.START_ELEMENT:
                    String key = xmler.getName().getLocalPart();
                    // <Patient> Tag
                    if (TagD.Level.PATIENT.getTagName().equals(key)) {
                        patient = readPatient(model, seriesList, xmler, wadoParameters);
                        pat++;
                    } else if (WadoParameters.TAG_HTTP_TAG.equals(key)) {
                        String httpkey = TagUtil.getTagAttribute(xmler, "key", null); //$NON-NLS-1$
                        String httpvalue = TagUtil.getTagAttribute(xmler, "value", null); //$NON-NLS-1$
                        wadoParameters.addHttpTag(httpkey, httpvalue);
                        // <Message> tag
                    } else if ("Message".equals(key)) { //$NON-NLS-1$
                        final String title = TagUtil.getTagAttribute(xmler, "title", null); //$NON-NLS-1$
                        final String message = TagUtil.getTagAttribute(xmler, "description", null); //$NON-NLS-1$
                        if (StringUtil.hasText(title) && StringUtil.hasText(message)) {
                            String severity = TagUtil.getTagAttribute(xmler, "severity", "WARN"); //$NON-NLS-1$ //$NON-NLS-2$
                            final int messageType =
                                "ERROR".equals(severity) ? JOptionPane.ERROR_MESSAGE //$NON-NLS-1$
                                    : "INFO" //$NON-NLS-1$
                                        .equals(severity) ? JOptionPane.INFORMATION_MESSAGE
                                            : JOptionPane.WARNING_MESSAGE;

                            GuiExecutor.instance().execute(() -> {
                                PluginTool explorer = null;
                                DataExplorerView dicomExplorer = UIManager.getExplorerplugin(DicomExplorer.NAME);
                                if (dicomExplorer instanceof PluginTool) {
                                    explorer = (PluginTool) dicomExplorer;
                                }
                                ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(explorer);
                                JOptionPane.showOptionDialog(ColorLayerUI.getContentPane(layer), message, title,
                                    JOptionPane.DEFAULT_OPTION, messageType, null, null, null);
                                if (layer != null) {
                                    layer.hideUI();
                                }
                            });
                        }
                    }
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    if (endElement.equals(xmler.getName().getLocalPart())) {
                        state = false;
                    }
                    break;
                default:
                    break;
            }
        }
        if (pat == 1) {
            // In case of the patient already exists, select it
            final MediaSeriesGroup uniquePatient = patient;
            GuiExecutor.instance().execute(() -> {
                synchronized (UIManager.VIEWER_PLUGINS) {
                    for (final ViewerPlugin p : UIManager.VIEWER_PLUGINS) {
                        if (uniquePatient.equals(p.getGroupID())) {
                            p.setSelectedAndGetFocus();
                            break;
                        }
                    }
                }
            });
        }
        for (LoadSeries loadSeries : seriesList) {
            String modality = TagD.getTagValue(loadSeries.getDicomSeries(), Tag.Modality, String.class);
            boolean ps = modality != null && ("PR".equals(modality) || "KO".equals(modality)); //$NON-NLS-1$ //$NON-NLS-2$
            if (!ps) {
                loadSeries.startDownloadImageReference(wadoParameters);
            }
        }

    }

    private static void logHttpError(URLConnection urlConnection) {
        if (urlConnection instanceof HttpURLConnection) {
            HttpURLConnection httpURLConnection = (HttpURLConnection) urlConnection;
            httpURLConnection.setConnectTimeout(5000);

            try {
                int responseCode = httpURLConnection.getResponseCode();
                if (responseCode < HttpURLConnection.HTTP_OK || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                    // Following is only intended LOG more info about Http Server Error

                    InputStream errorStream = httpURLConnection.getErrorStream();
                    if (errorStream != null) {
                        try (InputStreamReader inputStream = new InputStreamReader(errorStream, "UTF-8"); //$NON-NLS-1$
                                        BufferedReader reader = new BufferedReader(inputStream)) {
                            StringBuilder stringBuilder = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                stringBuilder.append(line);
                            }
                            String errorDescription = stringBuilder.toString();
                            if (StringUtil.hasText(errorDescription)) {
                                LOGGER.warn("HttpURLConnection - HTTP Status {} - {}", responseCode + " [" //$NON-NLS-1$ //$NON-NLS-2$
                                    + httpURLConnection.getResponseMessage() + "]", errorDescription); //$NON-NLS-1$
                            }
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.error("lOG http response message", e); //$NON-NLS-1$
            }
        }
    }

    private static MediaSeriesGroup readPatient(DicomModel model, ArrayList<LoadSeries> seriesList,
        XMLStreamReader xmler, WadoParameters wadoParameters) throws XMLStreamException {
        // PatientID, PatientBirthDate, StudyInstanceUID, SeriesInstanceUID and SOPInstanceUID override
        // the tags located in DICOM object (because original DICOM can contain different values after merging
        // patient or study
        TagW idTag = TagD.get(Tag.PatientID);
        TagW issuerIdTag = TagD.get(Tag.IssuerOfPatientID);
        TagW nameTag = TagD.get(Tag.PatientName);

        String patientID = TagUtil.getTagAttribute(xmler, idTag.getKeyword(), TagW.NO_VALUE);
        String issuerOfPatientID = TagUtil.getTagAttribute(xmler, issuerIdTag.getKeyword(), null);
        String name = TagUtil.getTagAttribute(xmler, nameTag.getKeyword(), TagW.NO_VALUE);

        String patientPseudoUID = DicomMediaUtils.buildPatientPseudoUID(patientID, issuerOfPatientID, name);

        MediaSeriesGroup patient = model.getHierarchyNode(MediaSeriesGroupNode.rootNode, patientPseudoUID);
        if (patient == null) {
            patient =
                new MediaSeriesGroupNode(TagD.getUID(Level.PATIENT), patientPseudoUID, DicomModel.patient.getTagView());
            patient.setTag(idTag, patientID);
            patient.setTag(nameTag, name);
            patient.setTagNoNull(issuerIdTag, issuerOfPatientID);

            TagW[] tags = TagD.getTagFromIDs(Tag.PatientSex, Tag.PatientBirthDate, Tag.PatientBirthTime);
            for (TagW tag : tags) {
                tag.readValue(xmler, patient);
            }

            model.addHierarchyNode(MediaSeriesGroupNode.rootNode, patient);
            LOGGER.info("Adding new patient: " + patient); //$NON-NLS-1$
        }

        int eventType;
        boolean state = true;
        while (xmler.hasNext() && state) {
            eventType = xmler.next();
            switch (eventType) {
                case XMLStreamConstants.START_ELEMENT:
                    // <Study> Tag
                    if (TagD.Level.STUDY.getTagName().equals(xmler.getName().getLocalPart())) {
                        readStudy(model, seriesList, xmler, patient, wadoParameters);
                    }
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    if (TagD.Level.PATIENT.getTagName().equals(xmler.getName().getLocalPart())) {
                        state = false;
                    }
                    break;
                default:
                    break;
            }
        }
        return patient;
    }

    private static MediaSeriesGroup readStudy(DicomModel model, ArrayList<LoadSeries> seriesList, XMLStreamReader xmler,
        MediaSeriesGroup patient, WadoParameters wadoParameters) throws XMLStreamException {
        String studyUID = (String) TagD.getUID(Level.STUDY).getValue(xmler);
        MediaSeriesGroup study = model.getHierarchyNode(patient, studyUID);
        if (study == null) {
            study = new MediaSeriesGroupNode(TagD.getUID(Level.STUDY), studyUID, DicomModel.study.getTagView());
            TagW[] tags = TagD.getTagFromIDs(Tag.StudyDate, Tag.StudyTime, Tag.StudyDescription, Tag.AccessionNumber,
                Tag.StudyID);
            for (TagW tag : tags) {
                tag.readValue(xmler, study);
            }

            model.addHierarchyNode(patient, study);
        }

        int eventType;
        boolean state = true;
        while (xmler.hasNext() && state) {
            eventType = xmler.next();
            switch (eventType) {
                case XMLStreamConstants.START_ELEMENT:
                    // <Series> Tag
                    if (TagD.Level.SERIES.getTagName().equals(xmler.getName().getLocalPart())) {
                        readSeries(model, seriesList, xmler, patient, study, wadoParameters);
                    }
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    if (TagD.Level.STUDY.getTagName().equals(xmler.getName().getLocalPart())) {
                        state = false;
                    }
                    break;
                default:
                    break;
            }
        }
        return study;
    }

    private static Series readSeries(DicomModel model, ArrayList<LoadSeries> seriesList, XMLStreamReader xmler,
        MediaSeriesGroup patient, MediaSeriesGroup study, WadoParameters wadoParameters) throws XMLStreamException {

        TagW seriesTag = TagD.get(Tag.SeriesInstanceUID);
        String seriesUID = (String) seriesTag.getValue(xmler);
        Series dicomSeries = (Series) model.getHierarchyNode(study, seriesUID);

        if (dicomSeries == null) {
            dicomSeries = new DicomSeries(seriesUID);
            dicomSeries.setTag(seriesTag, seriesUID);
            dicomSeries.setTag(TagW.ExplorerModel, model);
            dicomSeries.setTag(TagW.WadoParameters, wadoParameters);
            dicomSeries.setTag(TagW.WadoInstanceReferenceList, new ArrayList<DicomInstance>());

            TagW[] tags =
                TagD.getTagFromIDs(Tag.Modality, Tag.SeriesNumber, Tag.SeriesDescription, Tag.ReferringPhysicianName);
            for (TagW tag : tags) {
                tag.readValue(xmler, dicomSeries);
            }

            dicomSeries.setTagNoNull(TagW.WadoTransferSyntaxUID,
                TagUtil.getTagAttribute(xmler, TagW.WadoTransferSyntaxUID.getKeyword(), null));
            dicomSeries.setTagNoNull(TagW.WadoCompressionRate,
                TagUtil.getIntegerTagAttribute(xmler, TagW.WadoCompressionRate.getKeyword(), null));
            dicomSeries.setTagNoNull(TagW.DirectDownloadThumbnail,
                TagUtil.getTagAttribute(xmler, TagW.DirectDownloadThumbnail.getKeyword(), null));

            model.addHierarchyNode(study, dicomSeries);
        } else {
            WadoParameters wado = (WadoParameters) dicomSeries.getTagValue(TagW.WadoParameters);
            if (wado == null) {
                // Should not happen
                dicomSeries.setTag(TagW.WadoParameters, wadoParameters);
            } else if (!wado.getWadoURL().equals(wadoParameters.getWadoURL())) {
                LOGGER.error("Wado parameters must be unique within a DICOM Series: {}", dicomSeries); //$NON-NLS-1$
                return dicomSeries;
            }
        }

        List<DicomInstance> dicomInstances =
            (List<DicomInstance>) dicomSeries.getTagValue(TagW.WadoInstanceReferenceList);
        if (dicomInstances == null) {
            dicomInstances = new ArrayList<>();
            dicomSeries.setTag(TagW.WadoInstanceReferenceList, dicomInstances);
        }

        int eventType;
        boolean state = true;
        while (xmler.hasNext() && state) {
            eventType = xmler.next();
            switch (eventType) {
                case XMLStreamConstants.START_ELEMENT:
                    // <Instance> Tag
                    if (TagD.Level.INSTANCE.getTagName().equals(xmler.getName().getLocalPart())) {
                        String sopInstanceUID =
                            TagUtil.getTagAttribute(xmler, TagD.getKeywordFromTag(Tag.SOPInstanceUID, null), null);
                        if (sopInstanceUID != null) {
                            DicomInstance dcmInstance = new DicomInstance(sopInstanceUID);
                            if (dicomInstances.contains(dcmInstance)) {
                                LOGGER.warn("DICOM instance {} already exists, abort downloading.", sopInstanceUID); //$NON-NLS-1$
                            } else {
                                dcmInstance.setInstanceNumber(TagUtil.getIntegerTagAttribute(xmler,
                                    TagD.getKeywordFromTag(Tag.InstanceNumber, null), -1));
                                dcmInstance.setDirectDownloadFile(
                                    TagUtil.getTagAttribute(xmler, TagW.DirectDownloadFile.getKeyword(), null));
                                dicomInstances.add(dcmInstance);
                            }
                        }
                    }
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    if (TagD.Level.SERIES.getTagName().equals(xmler.getName().getLocalPart())) {
                        state = false;
                    }
                    break;
                default:
                    break;
            }
        }

        if (!dicomInstances.isEmpty()) {
            final LoadSeries loadSeries = new LoadSeries(dicomSeries, model,
                BundleTools.SYSTEM_PREFERENCES.getIntProperty(LoadSeries.CONCURRENT_DOWNLOADS_IN_SERIES, 4), true);
            loadSeries.setPriority(new DownloadPriority(patient, study, dicomSeries, true));
            seriesList.add(loadSeries);
        }
        return dicomSeries;
    }

}
