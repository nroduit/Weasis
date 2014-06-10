/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.explorer.wado;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker.StateValue;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.transform.stax.StAXSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.dcm4che3.util.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.GzipManager;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.dicom.codec.DicomInstance;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.DicomVideoSeries;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.codec.wado.WadoParameters;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.xml.sax.SAXException;

public class DownloadManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadManager.class);

    public static final String CONCURRENT_SERIES = "download.concurrent.series"; //$NON-NLS-1$
    public static final ArrayList<LoadSeries> TASKS = new ArrayList<LoadSeries>();

    // Executor without concurrency (only one task is executed at the same time)
    private static final BlockingQueue<Runnable> UNIQUE_QUEUE = new PriorityBlockingQueue<Runnable>(10,
        new PriorityTaskComparator());
    public static final ThreadPoolExecutor UNIQUE_EXECUTOR = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        UNIQUE_QUEUE);

    // Executor with simultaneous tasks
    private static final BlockingQueue<Runnable> PRIORITY_QUEUE = new PriorityBlockingQueue<Runnable>(10,
        new PriorityTaskComparator());
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
                rep = DicomModel.PATIENT_COMPARATOR.compare(val1.getPatient(), val2.getPatient());
            }
            if (rep != 0) {
                return rep;
            }

            if (val1.getStudy() != val2.getStudy()) {
                rep = DicomModel.STUDY_COMPARATOR.compare(val1.getStudy(), val2.getStudy());
            }
            if (rep != 0) {
                return rep;
            }
            return DicomModel.SERIES_COMPARATOR.compare(val1.getSeries(), val2.getSeries());
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

    public static synchronized void addLoadSeries(final LoadSeries series, DicomModel dicomModel, boolean startLoading) {
        if (series != null) {
            if (startLoading) {
                offerSeriesInQueue(series);
            } else {
                GuiExecutor.instance().execute(new Runnable() {
                    @Override
                    public void run() {
                        series.getProgressBar().setValue(0);
                        series.stop();
                    }
                });
            }
            if (dicomModel != null) {
                dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.LoadingStart, dicomModel,
                    null, series));
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
                dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.LoadingStop, dicomModel,
                    null, series));
            }
            if (DownloadManager.TASKS.size() == 0) {
                // When all loadseries are ended, reset to default the number of simultaneous download (series)
                DownloadManager.CONCURRENT_EXECUTOR.setCorePoolSize(BundleTools.SYSTEM_PREFERENCES.getIntProperty(
                    DownloadManager.CONCURRENT_SERIES, 3));
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
        handleAllSeries(new LoadSeriesHandler() {
            @Override
            public void handle(LoadSeries loadSeries) {
                loadSeries.resume();
            }
        });
    }

    public static void stop() {
        handleAllSeries(new LoadSeriesHandler() {
            @Override
            public void handle(LoadSeries loadSeries) {
                loadSeries.stop();
            }
        });
    }

    private static void handleAllSeries(LoadSeriesHandler handler) {
        for (LoadSeries loadSeries : new ArrayList<LoadSeries>(DownloadManager.TASKS)) {
            handler.handle(loadSeries);
            Thumbnail thumbnail = (Thumbnail) loadSeries.getDicomSeries().getTagValue(TagW.Thumbnail);
            if (thumbnail != null) {
                thumbnail.repaint();
            }
        }
    }

    private static interface LoadSeriesHandler {
        void handle(LoadSeries loadSeries);
    }

    public static ArrayList<LoadSeries> buildDicomSeriesFromXml(URI uri, final DicomModel model) {
        ArrayList<LoadSeries> seriesList = new ArrayList<LoadSeries>();
        XMLStreamReader xmler = null;
        InputStream stream = null;
        try {
            XMLInputFactory xmlif = XMLInputFactory.newInstance();

            String path = uri.getPath();

            URL url = uri.toURL();
            URLConnection urlConnection = url.openConnection();

            if (BundleTools.SESSION_TAGS_MANIFEST.size() > 0) {
                for (Iterator<Entry<String, String>> iter = BundleTools.SESSION_TAGS_MANIFEST.entrySet().iterator(); iter
                    .hasNext();) {
                    Entry<String, String> element = iter.next();
                    urlConnection.setRequestProperty(element.getKey(), element.getValue());
                }
            }

            LOGGER.info("Downloading WADO references: {}", url); //$NON-NLS-1$

            if (urlConnection instanceof HttpURLConnection) {
                HttpURLConnection httpURLConnection = (HttpURLConnection) urlConnection;

                LOGGER.debug("HttpURLConnection previous ConnectTimeout : {} sec", //$NON-NLS-1$
                    TimeUnit.MILLISECONDS.toSeconds(httpURLConnection.getConnectTimeout()));
                httpURLConnection.setConnectTimeout(5000);
                LOGGER.debug("HttpURLConnection new ConnectTimeout : {} sec", //$NON-NLS-1$
                    TimeUnit.MILLISECONDS.toSeconds(httpURLConnection.getConnectTimeout()));

                int responseCode = httpURLConnection.getResponseCode();
                if (responseCode < HttpURLConnection.HTTP_OK || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                    // Following is only intended LOG more info about Http Server Error

                    InputStream errorStream = httpURLConnection.getErrorStream();
                    if (errorStream != null) {
                        BufferedReader reader = null;
                        try {
                            reader = new BufferedReader(new InputStreamReader(errorStream, "UTF-8")); //$NON-NLS-1$
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
                        } finally {
                            if (reader != null) {
                                reader.close();
                            }
                        }
                    }
                }
            }

            if (path.endsWith(".gz")) { //$NON-NLS-1$
                stream = GzipManager.gzipUncompressToStream(urlConnection.getInputStream());
            } else if (path.endsWith(".xml")) { //$NON-NLS-1$
                stream = urlConnection.getInputStream();
            } else {
                // In case wado file has no extension
                File outFile = File.createTempFile("wado_", "", AppProperties.APP_TEMP_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                if (FileUtil.writeStream(urlConnection.getInputStream(), new FileOutputStream(outFile)) == -1) {
                    if (MimeInspector.isMatchingMimeTypeFromMagicNumber(outFile, "application/x-gzip")) { //$NON-NLS-1$
                        stream = new BufferedInputStream((new GZIPInputStream(new FileInputStream((outFile)))));
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
            xmler = xmlif.createXMLStreamReader(new FileReader(tempFile));

            Source xmlFile = new StAXSource(xmler);
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            try {
                Schema schema = schemaFactory.newSchema(DownloadManager.class.getResource("/config/wado_query.xsd"));//$NON-NLS-1$ 
                Validator validator = schema.newValidator();
                validator.validate(xmlFile);
                LOGGER.info("[Validate with XSD schema] wado_query is valid"); //$NON-NLS-1$
            } catch (SAXException e) {
                LOGGER.error("[Validate with XSD schema] wado_query is NOT valid"); //$NON-NLS-1$
                LOGGER.error("Reason: {}", e.getLocalizedMessage()); //$NON-NLS-1$
            } catch (Exception e) {
                LOGGER.error("Error when validate XSD schema. Try to update JRE"); //$NON-NLS-1$
                e.printStackTrace();
            }
            // Try to read the xml even it is not valid.
            xmler = xmlif.createXMLStreamReader(new FileReader(tempFile));
            int eventType;
            if (xmler.hasNext()) {
                eventType = xmler.next();
                switch (eventType) {
                    case XMLStreamConstants.START_ELEMENT:
                        String key = xmler.getName().getLocalPart();
                        // First Tag must <wado_query>
                        if (WadoParameters.TAG_DOCUMENT_ROOT.equals(key)) {
                            String wadoURL = getTagAttribute(xmler, WadoParameters.TAG_WADO_URL, null);
                            boolean onlySopUID =
                                Boolean.valueOf(getTagAttribute(xmler, WadoParameters.TAG_WADO_ONLY_SOP_UID, "false")); //$NON-NLS-1$
                            String additionnalParameters =
                                getTagAttribute(xmler, WadoParameters.TAG_WADO_ADDITIONNAL_PARAMETERS, ""); //$NON-NLS-1$
                            String overrideList = getTagAttribute(xmler, WadoParameters.TAG_WADO_OVERRIDE_TAGS, null);
                            String webLogin = getTagAttribute(xmler, WadoParameters.TAG_WADO_WEB_LOGIN, null);
                            final WadoParameters wadoParameters =
                                new WadoParameters(wadoURL, onlySopUID, additionnalParameters, overrideList, webLogin);
                            int pat = 0;
                            MediaSeriesGroup patient = null;
                            while (xmler.hasNext()) {
                                eventType = xmler.next();
                                switch (eventType) {
                                    case XMLStreamConstants.START_ELEMENT:
                                        // <Patient> Tag
                                        if (TagW.DICOM_LEVEL.Patient.name().equals(xmler.getName().getLocalPart())) {
                                            patient = readPatient(model, seriesList, xmler, wadoParameters);
                                            pat++;
                                        } else if (WadoParameters.TAG_HTTP_TAG.equals(xmler.getName().getLocalPart())) {
                                            String httpkey = getTagAttribute(xmler, "key", null); //$NON-NLS-1$
                                            String httpvalue = getTagAttribute(xmler, "value", null); //$NON-NLS-1$
                                            wadoParameters.addHttpTag(httpkey, httpvalue);
                                            // <Message> tag
                                        } else if ("Message".equals(xmler.getName().getLocalPart())) { //$NON-NLS-1$
                                            final String title = getTagAttribute(xmler, "title", null); //$NON-NLS-1$
                                            final String message = getTagAttribute(xmler, "description", null); //$NON-NLS-1$
                                            if (StringUtil.hasText(title) && StringUtil.hasText(message)) {
                                                String severity = getTagAttribute(xmler, "severity", "WARN"); //$NON-NLS-1$ //$NON-NLS-2$
                                                final int messageType =
                                                    "ERROR".equals(severity) ? JOptionPane.ERROR_MESSAGE : "INFO" //$NON-NLS-1$ //$NON-NLS-2$
                                                        .equals(severity) ? JOptionPane.INFORMATION_MESSAGE
                                                            : JOptionPane.WARNING_MESSAGE;

                                                GuiExecutor.instance().execute(new Runnable() {

                                                    @Override
                                                    public void run() {

                                                        JFrame rootFrame = null;
                                                        DataExplorerView dicomExplorer =
                                                            UIManager.getExplorerplugin(DicomExplorer.NAME);
                                                        if (dicomExplorer instanceof PluginTool) {
                                                            rootFrame =
                                                                WinUtil.getParentJFrame((PluginTool) dicomExplorer);
                                                        }
                                                        ColorLayerUI layer =
                                                            ColorLayerUI.createTransparentLayerUI(rootFrame);
                                                        JOptionPane.showOptionDialog(rootFrame, message, title,
                                                            JOptionPane.DEFAULT_OPTION, messageType, null, null, null);
                                                        if (layer != null) {
                                                            layer.hideUI();
                                                        }
                                                    }
                                                });

                                            }
                                        }
                                        break;
                                    default:
                                        break;
                                }
                            }
                            if (pat == 1) {
                                // In case of the patient already exists, select it
                                final MediaSeriesGroup uniquePatient = patient;
                                GuiExecutor.instance().execute(new Runnable() {

                                    @Override
                                    public void run() {
                                        synchronized (UIManager.VIEWER_PLUGINS) {
                                            for (final ViewerPlugin p : UIManager.VIEWER_PLUGINS) {
                                                if (uniquePatient.equals(p.getGroupID())) {
                                                    p.setSelectedAndGetFocus();
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                });
                            }
                            for (LoadSeries loadSeries : seriesList) {
                                String modality = (String) loadSeries.getDicomSeries().getTagValue(TagW.Modality);
                                boolean ps = modality != null && ("PR".equals(modality) || "KO".equals(modality)); //$NON-NLS-1$ //$NON-NLS-2$
                                if (!ps) {
                                    loadSeries.startDownloadImageReference(wadoParameters);
                                }
                            }
                        }
                        break;
                    default:
                        break;
                }
            }

        } catch (Throwable t) {
            final String message = "Error on loading wadoXML from : " + uri.toString(); //$NON-NLS-1$
            LOGGER.error(message);

            if (LOGGER.isDebugEnabled()) {
                t.printStackTrace();
            } else {
                LOGGER.error(t.toString());
            }

            final String title = "LOADING ERROR"; //$NON-NLS-1$
            final int messageType = JOptionPane.ERROR_MESSAGE;

            GuiExecutor.instance().execute(new Runnable() {

                @Override
                public void run() {

                    JFrame rootFrame = null;
                    DataExplorerView dicomExplorer = UIManager.getExplorerplugin(DicomExplorer.NAME);
                    if (dicomExplorer instanceof PluginTool) {
                        rootFrame = WinUtil.getParentJFrame((PluginTool) dicomExplorer);
                    }
                    ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(rootFrame);
                    JOptionPane.showOptionDialog(rootFrame, message, title, JOptionPane.DEFAULT_OPTION, messageType,
                        null, null, null);
                    if (layer != null) {
                        layer.hideUI();
                    }
                }
            });

        } finally {
            FileUtil.safeClose(xmler);
            FileUtil.safeClose(stream);
        }
        return seriesList;
    }

    private static MediaSeriesGroup readPatient(DicomModel model, ArrayList<LoadSeries> seriesList,
        XMLStreamReader xmler, WadoParameters wadoParameters) throws XMLStreamException {
        // PatientID, PatientBirthDate, StudyInstanceUID, SeriesInstanceUID and SOPInstanceUID override
        // the tags located in DICOM object (because original DICOM can contain different values after merging
        // patient or study

        String patientID = getTagAttribute(xmler, TagW.PatientID.getTagName(), DicomMediaIO.NO_VALUE);
        String issuerOfPatientID = getTagAttribute(xmler, TagW.IssuerOfPatientID.getTagName(), null);
        Date birthdate = null;
        String date = getTagAttribute(xmler, TagW.PatientBirthDate.getTagName(), null);
        if (date != null) {
            birthdate = DateUtils.parseDA(TimeZone.getDefault(), date, false);
        }
        String name =
            DicomMediaUtils.buildPatientName(getTagAttribute(xmler, TagW.PatientName.getTagName(),
                DicomMediaIO.NO_VALUE));

        String patientPseudoUID = DicomMediaUtils.buildPatientPseudoUID(patientID, issuerOfPatientID, name, null);

        MediaSeriesGroup patient = model.getHierarchyNode(TreeModel.rootNode, patientPseudoUID);
        if (patient == null) {
            patient = new MediaSeriesGroupNode(TagW.PatientPseudoUID, patientPseudoUID, TagW.PatientName);
            patient.setTag(TagW.PatientID, patientID);
            patient.setTag(TagW.PatientName, name);

            patient.setTag(TagW.PatientSex, getTagAttribute(xmler, TagW.PatientSex.getTagName(), "O")); //$NON-NLS-1$
            patient.setTag(TagW.PatientBirthDate, birthdate);
            patient.setTagNoNull(TagW.PatientBirthTime,
                TagW.getDicomTime(getTagAttribute(xmler, TagW.PatientBirthTime.getTagName(), null)));
            model.addHierarchyNode(TreeModel.rootNode, patient);
            LOGGER.info("Adding new patient: " + patient); //$NON-NLS-1$
        }

        int eventType;
        boolean state = true;
        while (xmler.hasNext() && state) {
            eventType = xmler.next();
            switch (eventType) {
                case XMLStreamConstants.START_ELEMENT:
                    // <Study> Tag
                    if (TagW.DICOM_LEVEL.Study.name().equals(xmler.getName().getLocalPart())) {
                        readStudy(model, seriesList, xmler, patient, wadoParameters);
                    }
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    if (TagW.DICOM_LEVEL.Patient.name().equals(xmler.getName().getLocalPart())) {
                        state = false;
                    }
                    break;
                default:
                    break;
            }
        }
        return patient;
    }

    private static MediaSeriesGroup readStudy(DicomModel model, ArrayList<LoadSeries> seriesList,
        XMLStreamReader xmler, MediaSeriesGroup patient, WadoParameters wadoParameters) throws XMLStreamException {
        String studyUID = getTagAttribute(xmler, TagW.StudyInstanceUID.getTagName(), ""); //$NON-NLS-1$
        MediaSeriesGroup study = model.getHierarchyNode(patient, studyUID);
        if (study == null) {
            study = new MediaSeriesGroupNode(TagW.StudyInstanceUID, studyUID, TagW.StudyDate);
            study.setTagNoNull(TagW.StudyTime,
                TagW.getDicomTime(getTagAttribute(xmler, TagW.StudyTime.getTagName(), null)));
            // Merge date and time, used in display
            study.setTagNoNull(TagW.StudyDate, TagW.dateTime(
                TagW.getDicomTime(getTagAttribute(xmler, TagW.StudyDate.getTagName(), null)),
                (Date) study.getTagValue(TagW.StudyTime)));

            study.setTagNoNull(TagW.StudyDescription, getTagAttribute(xmler, TagW.StudyDescription.getTagName(), null));
            study.setTagNoNull(TagW.AccessionNumber, getTagAttribute(xmler, TagW.AccessionNumber.getTagName(), null));
            study.setTagNoNull(TagW.StudyID, getTagAttribute(xmler, TagW.StudyID.getTagName(), null));

            model.addHierarchyNode(patient, study);
        }

        int eventType;
        boolean state = true;
        while (xmler.hasNext() && state) {
            eventType = xmler.next();
            switch (eventType) {
                case XMLStreamConstants.START_ELEMENT:
                    // <Series> Tag
                    if (TagW.DICOM_LEVEL.Series.name().equals(xmler.getName().getLocalPart())) {
                        readSeries(model, seriesList, xmler, patient, study, wadoParameters);
                    }
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    if (TagW.DICOM_LEVEL.Study.name().equals(xmler.getName().getLocalPart())) {
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

        String seriesUID = getTagAttribute(xmler, TagW.SeriesInstanceUID.getTagName(), ""); //$NON-NLS-1$

        Series dicomSeries = (Series) model.getHierarchyNode(study, seriesUID);
        if (dicomSeries == null) {
            dicomSeries = new DicomSeries(seriesUID);
            dicomSeries.setTag(TagW.ExplorerModel, model);
            dicomSeries.setTag(TagW.SeriesInstanceUID, seriesUID);
            dicomSeries.setTag(TagW.Modality, getTagAttribute(xmler, TagW.Modality.getTagName(), null));
            dicomSeries.setTag(TagW.WadoParameters, wadoParameters);
            dicomSeries.setTag(TagW.WadoInstanceReferenceList, new ArrayList<DicomInstance>());

            dicomSeries.setTagNoNull(TagW.SeriesNumber,
                FileUtil.getIntegerTagAttribute(xmler, TagW.SeriesNumber.getTagName(), null));
            dicomSeries.setTagNoNull(TagW.SeriesDescription,
                getTagAttribute(xmler, TagW.SeriesDescription.getTagName(), null));
            dicomSeries
                .setTagNoNull(TagW.ReferringPhysicianName, DicomMediaUtils.buildPersonName(getTagAttribute(xmler,
                    TagW.ReferringPhysicianName.getTagName(), null)));
            dicomSeries.setTagNoNull(TagW.WadoTransferSyntaxUID,
                getTagAttribute(xmler, TagW.WadoTransferSyntaxUID.getTagName(), null));
            dicomSeries.setTagNoNull(TagW.WadoCompressionRate,
                FileUtil.getIntegerTagAttribute(xmler, TagW.WadoCompressionRate.getTagName(), null));
            dicomSeries.setTagNoNull(TagW.DirectDownloadThumbnail,
                getTagAttribute(xmler, TagW.DirectDownloadThumbnail.getTagName(), null));
            model.addHierarchyNode(study, dicomSeries);
        } else {
            WadoParameters wado = (WadoParameters) dicomSeries.getTagValue(TagW.WadoParameters);
            if (wado == null) {
                // Should not happen
                dicomSeries.setTag(TagW.WadoParameters, wadoParameters);
            } else if (!wado.getWadoURL().equals(wadoParameters.getWadoURL())) {
                LOGGER.error("Wado parameters must be unique for a DICOM Series: {}", dicomSeries); //$NON-NLS-1$
                // Cannot have multiple wado parameters for a Series
                return dicomSeries;
            }
        }

        List<DicomInstance> dicomInstances =
            (List<DicomInstance>) dicomSeries.getTagValue(TagW.WadoInstanceReferenceList);
        boolean containsInstance = false;
        if (dicomInstances == null) {
            dicomSeries.setTag(TagW.WadoInstanceReferenceList, new ArrayList<DicomInstance>());
        } else if (dicomInstances.size() > 0) {
            containsInstance = true;
        }

        int eventType;
        boolean state = true;
        while (xmler.hasNext() && state) {
            eventType = xmler.next();
            switch (eventType) {
                case XMLStreamConstants.START_ELEMENT:
                    // <Instance> Tag
                    if (TagW.DICOM_LEVEL.Instance.name().equals(xmler.getName().getLocalPart())) {
                        String sopInstanceUID = getTagAttribute(xmler, TagW.SOPInstanceUID.getTagName(), null);
                        if (sopInstanceUID != null) {
                            String tsuid = getTagAttribute(xmler, TagW.TransferSyntaxUID.getTagName(), null);
                            DicomInstance dcmInstance = new DicomInstance(sopInstanceUID, tsuid);
                            if (containsInstance && dicomInstances.contains(dcmInstance)) {
                                LOGGER.warn("DICOM instance {} already exists, abort downloading.", sopInstanceUID); //$NON-NLS-1$
                            } else {
                                dcmInstance.setInstanceNumber(FileUtil.getIntegerTagAttribute(xmler,
                                    TagW.InstanceNumber.getTagName(), -1));
                                dcmInstance.setDirectDownloadFile(getTagAttribute(xmler,
                                    TagW.DirectDownloadFile.getTagName(), null));
                                dicomInstances.add(dcmInstance);
                            }
                        }
                    }
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    if (TagW.DICOM_LEVEL.Series.name().equals(xmler.getName().getLocalPart())) {
                        state = false;
                    }
                    break;
                default:
                    break;
            }
        }

        if (dicomInstances.size() > 0) {
            if (dicomInstances.size() == 1
                && "1.2.840.10008.1.2.4.100".equals(dicomInstances.get(0).getTransferSyntaxUID())) { //$NON-NLS-1$
                model.removeHierarchyNode(study, dicomSeries);
                dicomSeries = new DicomVideoSeries((DicomSeries) dicomSeries);
                model.addHierarchyNode(study, dicomSeries);
            }

            final LoadSeries loadSeries =
                new LoadSeries(dicomSeries, model, BundleTools.SYSTEM_PREFERENCES.getIntProperty(
                    LoadSeries.CONCURRENT_DOWNLOADS_IN_SERIES, 4), true);
            loadSeries.setPriority(new DownloadPriority(patient, study, dicomSeries, true));
            seriesList.add(loadSeries);
        }
        return dicomSeries;
    }

    private static String getTagAttribute(XMLStreamReader xmler, String attribute, String defaultValue) {
        if (attribute != null) {
            String val = xmler.getAttributeValue(null, attribute);
            if (val != null) {
                return val;
            }
        }
        return defaultValue;
    }

}
