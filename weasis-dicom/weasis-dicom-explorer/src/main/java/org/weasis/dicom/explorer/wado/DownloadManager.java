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
package org.weasis.dicom.explorer.wado;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.weasis.core.api.util.BiConsumerWithException;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.NetworkUtil;
import org.weasis.core.api.util.StreamIOException;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.api.util.StringUtil.Suffix;
import org.weasis.core.api.util.ThreadUtil;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.ReferencedImage;
import org.weasis.core.ui.model.ReferencedSeries;
import org.weasis.core.ui.serialize.XmlSerializer;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.macro.HierachicalSOPInstanceReference;
import org.weasis.dicom.codec.macro.KODocumentModule;
import org.weasis.dicom.codec.macro.SOPInstanceReferenceAndMAC;
import org.weasis.dicom.codec.macro.SeriesAndInstanceReference;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.codec.utils.PatientComparator;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.DicomSorter;
import org.weasis.dicom.explorer.LoadDicomObjects;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.mf.ArcParameters;
import org.weasis.dicom.mf.SopInstance;
import org.weasis.dicom.mf.WadoParameters;
import org.weasis.dicom.mf.Xml;
import org.xml.sax.SAXException;

public class DownloadManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadManager.class);

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
    public static final ThreadPoolExecutor CONCURRENT_EXECUTOR =
        new ThreadPoolExecutor(BundleTools.SYSTEM_PREFERENCES.getIntProperty(CONCURRENT_SERIES, 3),
            BundleTools.SYSTEM_PREFERENCES.getIntProperty(CONCURRENT_SERIES, 3), 0L, TimeUnit.MILLISECONDS,
            PRIORITY_QUEUE, ThreadUtil.getThreadFactory("Series Downloader")); //$NON-NLS-1$

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
                if (series.isCancelled()) {
                    dicomModel.firePropertyChange(
                        new ObservableEvent(ObservableEvent.BasicAction.LOADING_CANCEL, dicomModel, null, series));
                } else {
                    dicomModel.firePropertyChange(
                        new ObservableEvent(ObservableEvent.BasicAction.LOADING_STOP, dicomModel, null, series));
                }

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
                            loading.cancel();
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
        handleAllSeries(LoadSeries::resume);
    }

    public static void stop() {
        handleAllSeries(LoadSeries::stop);
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

    public static Collection<LoadSeries> buildDicomSeriesFromXml(URI uri, final DicomModel model)
        throws DownloadException {
        Map<String, LoadSeries> seriesMap = new HashMap<>();
        XMLStreamReader xmler = null;
        InputStream stream = null;
        try {
            XMLInputFactory xmlif = XMLInputFactory.newInstance();

            String path = uri.getPath();
            
            URLConnection urlConnection = uri.toURL().openConnection();
            urlConnection.setUseCaches(false);

            LOGGER.info("Downloading XML manifest: {}", path); //$NON-NLS-1$
            InputStream urlInputStream = NetworkUtil.getUrlInputStream(urlConnection, BundleTools.SESSION_TAGS_MANIFEST,  StringUtil.getInt(System.getProperty("UrlConnectionTimeout"), 7000)  , StringUtil.getInt(System.getProperty("UrlReadTimeout"), 15000) * 2); //$NON-NLS-1$ //$NON-NLS-2$

            if (path.endsWith(".gz")) { //$NON-NLS-1$
                stream = new BufferedInputStream(new GZIPInputStream(urlInputStream));
            } else if (path.endsWith(".xml")) { //$NON-NLS-1$
                stream = urlInputStream;
            } else {
                // In case wado file has no extension
                File outFile = File.createTempFile("wado_", "", AppProperties.APP_TEMP_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                FileUtil.writeStreamWithIOException(urlInputStream, outFile);
                if (MimeInspector.isMatchingMimeTypeFromMagicNumber(outFile, "application/x-gzip")) { //$NON-NLS-1$
                    stream = new BufferedInputStream(new GZIPInputStream(new FileInputStream(outFile)));
                } else {
                    stream = new FileInputStream(outFile);
                }
            }

            File tempFile = null;
            if (uri.toString().startsWith("file:") && path.endsWith(".xml")) { //$NON-NLS-1$ //$NON-NLS-2$
                tempFile = new File(path);
            } else {
                tempFile = File.createTempFile("wado_", ".xml", AppProperties.APP_TEMP_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                FileUtil.writeStreamWithIOException(stream, tempFile);
            }
            xmler = xmlif.createXMLStreamReader(new FileInputStream(tempFile));

            Source xmlFile = new StAXSource(xmler);
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            try {
                Schema schema = schemaFactory.newSchema(new Source[] {
                    new StreamSource(DownloadManager.class.getResource("/config/wado_query.xsd").toExternalForm()), //$NON-NLS-1$
                    new StreamSource(DownloadManager.class.getResource("/config/manifest.xsd").toExternalForm()) }); //$NON-NLS-1$
                Validator validator = schema.newValidator();
                validator.validate(xmlFile);
                LOGGER.info("[Validate with XSD schema] wado_query is valid"); //$NON-NLS-1$
            } catch (SAXException e) {
                LOGGER.error("[Validate with XSD schema] wado_query is NOT valid", e); //$NON-NLS-1$
            } catch (Exception e) {
                LOGGER.error("Error when validate XSD schema. Try to update JRE", e); //$NON-NLS-1$
            }

            ReaderParams params = new ReaderParams(model, seriesMap);
            // Try to read the xml even it is not valid.
            xmler = xmlif.createXMLStreamReader(new FileInputStream(tempFile));

            BiConsumerWithException<XMLStreamReader, ReaderParams, XMLStreamException> method = (x, r) -> {
                String key = x.getName().getLocalPart();
                // xmlns="http://www.weasis.org/xsd/2.5"
                if (ArcParameters.TAG_DOCUMENT_ROOT.equals(key)) {
                    BiConsumerWithException<XMLStreamReader, ReaderParams, XMLStreamException> method2 = (x2, r2) -> {
                        String key2 = x2.getName().getLocalPart();
                        if (ArcParameters.TAG_ARC_QUERY.equals(key2)) {
                            readArcQuery(x2, r2);
                        } else if (ArcParameters.TAG_PR_ROOT.equals(key2)) {
                            BiConsumerWithException<XMLStreamReader, ReaderParams, XMLStreamException> method3 =
                                DownloadManager::readPresentation;
                            readElement(x2, ArcParameters.TAG_PR, ArcParameters.TAG_PR_ROOT, method3, r2);
                        } else if (ArcParameters.TAG_SEL_ROOT.equals(key2)) {
                            BiConsumerWithException<XMLStreamReader, ReaderParams, XMLStreamException> method3 =
                                DownloadManager::readSelection;
                            readElement(x2, ArcParameters.TAG_SEL, ArcParameters.TAG_SEL_ROOT, method3, r2);
                        }
                    };
                    readElement(x, ArcParameters.TAG_DOCUMENT_ROOT, method2, r);
                } else {
                    // Read old manifest: xmlns="http://www.weasis.org/xsd"
                    if (WadoParameters.TAG_WADO_QUERY.equals(key)) {
                        readWadoQuery(x, r);
                    }
                }
            };
            readElement(xmler, ArcParameters.TAG_DOCUMENT_ROOT, method, params);

        } catch (StreamIOException e) {
            throw new DownloadException(getErrorMessage(uri), e); // rethrow network issue
        } catch (Exception e) {
            String message = getErrorMessage(uri);
            LOGGER.error("{}", message, e); //$NON-NLS-1$
            final int messageType = JOptionPane.ERROR_MESSAGE;

            GuiExecutor.instance().execute(() -> {
                ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(UIManager.BASE_AREA);
                JOptionPane.showOptionDialog(ColorLayerUI.getContentPane(layer),
                    StringUtil.getTruncatedString(message, 130, Suffix.THREE_PTS), null, JOptionPane.DEFAULT_OPTION,
                    messageType, null, null, null);
                if (layer != null) {
                    layer.hideUI();
                }
            });
        } finally {
            FileUtil.safeClose(xmler);
            FileUtil.safeClose(stream);
        }
        return seriesMap.values();
    }

    private static String getErrorMessage(URI uri) {
        StringBuilder buf = new StringBuilder(Messages.getString("DownloadManager.error_load_xml")); //$NON-NLS-1$
        buf.append(StringUtil.COLON_AND_SPACE);
        buf.append(uri.toString());
        return buf.toString();
    }

    private static void readArcQuery(XMLStreamReader xmler, ReaderParams params) throws XMLStreamException {
        String arcID = TagUtil.getTagAttribute(xmler, ArcParameters.ARCHIVE_ID, ""); //$NON-NLS-1$
        String wadoURL = TagUtil.getTagAttribute(xmler, ArcParameters.BASE_URL, null);
        boolean onlySopUID =
            Boolean.parseBoolean(TagUtil.getTagAttribute(xmler, WadoParameters.WADO_ONLY_SOP_UID, "false")); //$NON-NLS-1$
        String additionnalParameters = TagUtil.getTagAttribute(xmler, ArcParameters.ADDITIONNAL_PARAMETERS, ""); //$NON-NLS-1$
        String overrideList = TagUtil.getTagAttribute(xmler, ArcParameters.OVERRIDE_TAGS, null);
        String webLogin = TagUtil.getTagAttribute(xmler, ArcParameters.WEB_LOGIN, null);
        final WadoParameters wadoParameters =
            new WadoParameters(arcID, wadoURL, onlySopUID, additionnalParameters, overrideList, webLogin);
        readQuery(xmler, params, wadoParameters, ArcParameters.TAG_ARC_QUERY);
    }

    private static void readWadoQuery(XMLStreamReader xmler, ReaderParams params) throws XMLStreamException {
        String wadoURL = TagUtil.getTagAttribute(xmler, WadoParameters.WADO_URL, null);
        boolean onlySopUID =
            Boolean.parseBoolean(TagUtil.getTagAttribute(xmler, WadoParameters.WADO_ONLY_SOP_UID, "false")); //$NON-NLS-1$
        String additionnalParameters = TagUtil.getTagAttribute(xmler, ArcParameters.ADDITIONNAL_PARAMETERS, ""); //$NON-NLS-1$
        String overrideList = TagUtil.getTagAttribute(xmler, ArcParameters.OVERRIDE_TAGS, null);
        String webLogin = TagUtil.getTagAttribute(xmler, ArcParameters.WEB_LOGIN, null);
        final WadoParameters wadoParameters =
            new WadoParameters(wadoURL, onlySopUID, additionnalParameters, overrideList, webLogin);
        readQuery(xmler, params, wadoParameters, WadoParameters.TAG_WADO_QUERY);
    }

    private static void readQuery(XMLStreamReader xmler, ReaderParams params, final WadoParameters wadoParameters,
        String endElement) throws XMLStreamException {
        Set<MediaSeriesGroup> patients = new LinkedHashSet<>();

        BiConsumerWithException<XMLStreamReader, ReaderParams, XMLStreamException> method = (x, r) -> {
            String key = xmler.getName().getLocalPart();
            // <Patient> Tag
            if (TagD.Level.PATIENT.getTagName().equals(key)) {
                MediaSeriesGroup patient = readPatient(xmler, params, wadoParameters);
                patients.add(patient);
            } else if (ArcParameters.TAG_HTTP_TAG.equals(key)) {
                String httpkey = TagUtil.getTagAttribute(xmler, "key", null); //$NON-NLS-1$
                String httpvalue = TagUtil.getTagAttribute(xmler, "value", null); //$NON-NLS-1$
                wadoParameters.addHttpTag(httpkey, httpvalue);
            // <Message> tag
            } else if ("Message".equals(key)) { //$NON-NLS-1$
                final String title = TagUtil.getTagAttribute(xmler, "title", null); //$NON-NLS-1$
                final String message = TagUtil.getTagAttribute(xmler, "description", null); //$NON-NLS-1$
                if (StringUtil.hasText(title) && StringUtil.hasText(message)) {
                    String severity = TagUtil.getTagAttribute(xmler, "severity", "WARN"); //$NON-NLS-1$ //$NON-NLS-2$
                    final int messageType = "ERROR".equals(severity) ? JOptionPane.ERROR_MESSAGE //$NON-NLS-1$
                        : "INFO" //$NON-NLS-1$
                            .equals(severity) ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE;

                    GuiExecutor.instance().execute(() -> {
                        ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(UIManager.BASE_AREA);
                        JOptionPane.showMessageDialog(ColorLayerUI.getContentPane(layer), message, title, messageType);
                        if (layer != null) {
                            layer.hideUI();
                        }
                    });
                }
            }
        };

        readElement(xmler, endElement, method, params);

        if (patients.size() == 1) {
            // In case of the patient already exists, select it
            final MediaSeriesGroup uniquePatient = patients.iterator().next();
            GuiExecutor.instance().execute(() -> {
                synchronized (UIManager.VIEWER_PLUGINS) {
                    for (final ViewerPlugin<?> p : UIManager.VIEWER_PLUGINS) {
                        if (uniquePatient.equals(p.getGroupID())) {
                            p.setSelectedAndGetFocus();
                            break;
                        }
                    }
                }
            });
        }
        for (LoadSeries loadSeries : params.getSeriesMap().values()) {
            String modality = TagD.getTagValue(loadSeries.getDicomSeries(), Tag.Modality, String.class);
            boolean ps = modality != null && ("PR".equals(modality) || "KO".equals(modality)); //$NON-NLS-1$ //$NON-NLS-2$
            if (!ps) {
                loadSeries.startDownloadImageReference(wadoParameters);
            }
        }
    }

    private static MediaSeriesGroup readPatient(XMLStreamReader xmler, ReaderParams params,
        WadoParameters wadoParameters) throws XMLStreamException {
        // PatientID, PatientBirthDate, StudyInstanceUID, SeriesInstanceUID and SOPInstanceUID override
        // the tags located in DICOM object (because original DICOM can contain different values after merging
        // patient or study
        TagW idTag = TagD.get(Tag.PatientID);
        TagW issuerIdTag = TagD.get(Tag.IssuerOfPatientID);
        TagW nameTag = TagD.get(Tag.PatientName);

        PatientComparator patientComparator = new PatientComparator(xmler);
        String patientPseudoUID = patientComparator.buildPatientPseudoUID();

        DicomModel model = params.getModel();
        MediaSeriesGroup patient = model.getHierarchyNode(MediaSeriesGroupNode.rootNode, patientPseudoUID);
        if (patient == null) {
            patient =
                new MediaSeriesGroupNode(TagD.getUID(Level.PATIENT), patientPseudoUID, DicomModel.patient.getTagView());
            patient.setTag(idTag, TagUtil.getTagAttribute(xmler, idTag.getKeyword(), TagW.NO_VALUE));
            patient.setTag(nameTag, TagUtil.getTagAttribute(xmler, nameTag.getKeyword(), TagW.NO_VALUE));
            patient.setTagNoNull(issuerIdTag, TagUtil.getTagAttribute(xmler, issuerIdTag.getKeyword(), null));

            TagW[] tags = TagD.getTagFromIDs(Tag.PatientSex, Tag.PatientBirthDate, Tag.PatientBirthTime);
            for (TagW tag : tags) {
                tag.readValue(xmler, patient);
            }

            model.addHierarchyNode(MediaSeriesGroupNode.rootNode, patient);
            LOGGER.info("Adding new patient: {}", patient); //$NON-NLS-1$
        }

        final MediaSeriesGroup patient2 = patient;
        BiConsumerWithException<XMLStreamReader, ReaderParams, XMLStreamException> method =
            (x, r) -> readStudy(xmler, params, patient2, wadoParameters);

        readElement(xmler, TagD.Level.STUDY.getTagName(), TagD.Level.PATIENT.getTagName(), method, params);

        return patient;
    }

    private static MediaSeriesGroup readStudy(XMLStreamReader xmler, ReaderParams params, MediaSeriesGroup patient,
        WadoParameters wadoParameters) throws XMLStreamException {
        DicomModel model = params.getModel();
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

        final MediaSeriesGroup study2 = study;
        BiConsumerWithException<XMLStreamReader, ReaderParams, XMLStreamException> method =
            (x, r) -> readSeries(x, r, patient, study2, wadoParameters);

        readElement(xmler, TagD.Level.SERIES.getTagName(), TagD.Level.STUDY.getTagName(), method, params);

        return study;
    }

    private static Series readSeries(XMLStreamReader xmler, ReaderParams params, MediaSeriesGroup patient,
        MediaSeriesGroup study, WadoParameters wadoParameters) throws XMLStreamException {

        DicomModel model = params.getModel();
        TagW seriesTag = TagD.get(Tag.SeriesInstanceUID);
        String seriesUID = (String) seriesTag.getValue(xmler);
        Series dicomSeries = (Series) model.getHierarchyNode(study, seriesUID);

        if (dicomSeries == null) {
            dicomSeries = new DicomSeries(seriesUID);
            dicomSeries.setTag(seriesTag, seriesUID);
            dicomSeries.setTag(TagW.ExplorerModel, model);
            dicomSeries.setTag(TagW.WadoParameters, wadoParameters);

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
            } else if (!wado.getBaseURL().equals(wadoParameters.getBaseURL())) {
                LOGGER.error("Wado parameters must be unique within a DICOM Series: {}", dicomSeries); //$NON-NLS-1$
                return dicomSeries;
            }
        }

        SeriesInstanceList seriesInstanceList =
            Optional.ofNullable((SeriesInstanceList) dicomSeries.getTagValue(TagW.WadoInstanceReferenceList))
                .orElseGet(SeriesInstanceList::new);

        BiConsumerWithException<XMLStreamReader, ReaderParams, XMLStreamException> method = (x, r) -> {
            String sopInstanceUID =
                TagUtil.getTagAttribute(xmler, TagD.getKeywordFromTag(Tag.SOPInstanceUID, null), null);
            if (sopInstanceUID != null) {
                Integer frame =
                    TagUtil.getIntegerTagAttribute(xmler, TagD.getKeywordFromTag(Tag.InstanceNumber, null), null);
                SopInstance sop = seriesInstanceList.getSopInstance(sopInstanceUID, frame);
                if (sop == null) {
                    sop = new SopInstance(sopInstanceUID, frame);
                    sop.setDirectDownloadFile(
                        TagUtil.getTagAttribute(xmler, TagW.DirectDownloadFile.getKeyword(), null));
                    seriesInstanceList.addSopInstance(sop);
                }
            }
        };
        readElement(xmler, TagD.Level.INSTANCE.getTagName(), TagD.Level.SERIES.getTagName(), method, params);
        dicomSeries.setTag(TagW.WadoInstanceReferenceList, seriesInstanceList);

        if (!seriesInstanceList.isEmpty()) {
            final LoadSeries loadSeries = new LoadSeries(dicomSeries, model,
                BundleTools.SYSTEM_PREFERENCES.getIntProperty(LoadSeries.CONCURRENT_DOWNLOADS_IN_SERIES, 4), true);
            loadSeries.setPriority(new DownloadPriority(patient, study, dicomSeries, true));
            params.getSeriesMap().put(seriesUID, loadSeries);
        }
        return dicomSeries;
    }

    private static void readPresentation(XMLStreamReader xmler, ReaderParams params) throws XMLStreamException {
        GraphicModel model = XmlSerializer.readPresentation(xmler);
        if (model != null) {
            model.getLayers().forEach(l -> l.setSerializable(Boolean.TRUE));
            for (ReferencedSeries refSeries : model.getReferencedSeries()) {
                LoadSeries series = params.getSeriesMap().get(refSeries.getUuid());
                if (series != null) {
                    SeriesInstanceList dicomInstanceMap =
                        (SeriesInstanceList) series.getDicomSeries().getTagValue(TagW.WadoInstanceReferenceList);

                    if (dicomInstanceMap != null) {
                        for (ReferencedImage refImg : refSeries.getImages()) {
                            List<Integer> frames = refImg.getFrames();
                            if (frames == null || frames.isEmpty()) {
                                SopInstance sop = dicomInstanceMap.getSopInstance(refImg.getUuid());
                                if (sop != null) {
                                    sop.setGraphicModel(model);
                                }
                            } else {
                                for (Integer f : refImg.getFrames()) {
                                    // Convert MediaElement InstanceNumber to Dicom InstanceNumber
                                    SopInstance sop = dicomInstanceMap.getSopInstance(refImg.getUuid(), f + 1);
                                    if (sop != null) {
                                        sop.setGraphicModel(model);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void readSelection(XMLStreamReader xmler, ReaderParams params) throws XMLStreamException {

        String sereiesUIDKey = TagD.get(Tag.SeriesInstanceUID).getKeyword();
        String name = TagUtil.getTagAttribute(xmler, KOSpecialElement.SEL_NAME, null);
        String koSeriesUID = TagUtil.getTagAttribute(xmler, sereiesUIDKey, null);
        List<HierachicalSOPInstanceReference> referencedStudies = new ArrayList<>();
        List<SeriesAndInstanceReference> referencedSeries = new ArrayList<>();
        HierachicalSOPInstanceReference hierachicalDicom = new HierachicalSOPInstanceReference();
        referencedStudies.add(hierachicalDicom);

        DicomModel model = params.getModel();
        BiConsumerWithException<XMLStreamReader, ReaderParams, XMLStreamException> method = (x, r) -> {
            String seriesUID = TagUtil.getTagAttribute(xmler, sereiesUIDKey, null);
            SeriesAndInstanceReference refSerInst = new SeriesAndInstanceReference();
            refSerInst.setSeriesInstanceUID(seriesUID);
            referencedSeries.add(refSerInst);

            readImages(refSerInst, xmler, r);
        };
        readElement(xmler, Xml.Level.SERIES.getTagName(), ArcParameters.TAG_SEL, method, params);

        if (!referencedSeries.isEmpty()) {
            MediaSeriesGroup s = model.getSeriesNode(referencedSeries.get(0).getSeriesInstanceUID());
            MediaSeriesGroup study = model.getParent(s, DicomModel.study);
            if (study == null) {
                return; // When the related series has not be loaded
            }
            Attributes srcAttribute = new Attributes(15);
            DicomMediaUtils.fillAttributes(study.getTagEntrySetIterator(), srcAttribute);
            MediaSeriesGroup patient = model.getParent(study, DicomModel.patient);
            DicomMediaUtils.fillAttributes(patient.getTagEntrySetIterator(), srcAttribute);
            Attributes attributes = DicomMediaUtils.createDicomKeyObject(srcAttribute, name, koSeriesUID);
            hierachicalDicom.setStudyInstanceUID(TagD.getTagValue(study, Tag.StudyInstanceUID, String.class));
            hierachicalDicom.setReferencedSeries(referencedSeries);

            new KODocumentModule(attributes).setCurrentRequestedProcedureEvidences(referencedStudies);
            new LoadDicomObjects(model, attributes).addSelectionAndnotify(); // must be executed in the EDT
        }

    }

    private static void readImages(SeriesAndInstanceReference rfSeries, XMLStreamReader xmler, ReaderParams params)
        throws XMLStreamException {
        List<SOPInstanceReferenceAndMAC> instances = new ArrayList<>();

        BiConsumerWithException<XMLStreamReader, ReaderParams, XMLStreamException> method = (x, r) -> {
            String sopUID = TagUtil.getTagAttribute(xmler, TagD.get(Tag.ReferencedSOPInstanceUID).getKeyword(), null);
            String sopClassUID = TagUtil.getTagAttribute(xmler, TagD.get(Tag.ReferencedSOPClassUID).getKeyword(), null);
            int[] seqFrame = (int[]) TagD.get(Tag.ReferencedFrameNumber).getValue(xmler);

            SOPInstanceReferenceAndMAC referencedSOP = new SOPInstanceReferenceAndMAC();
            referencedSOP.setReferencedSOPInstanceUID(sopUID);
            referencedSOP.setReferencedSOPClassUID(sopClassUID);
            referencedSOP.setReferencedFrameNumber(seqFrame);
            instances.add(referencedSOP);
        };

        readElement(xmler, Xml.Level.INSTANCE.getTagName(), Xml.Level.SERIES.getTagName(), method, params);
        rfSeries.setReferencedSOPInstances(instances);
    }

    private static void readElement(XMLStreamReader xmler, String endElement,
        BiConsumerWithException<XMLStreamReader, ReaderParams, XMLStreamException> method, ReaderParams params)
        throws XMLStreamException {
        boolean state = true;
        while (xmler.hasNext() && state) {
            int eventType = xmler.next();
            switch (eventType) {
                case XMLStreamConstants.START_ELEMENT:
                    method.accept(xmler, params);
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
    }

    private static void readElement(XMLStreamReader xmler, String startElement, String endElement,
        BiConsumerWithException<XMLStreamReader, ReaderParams, XMLStreamException> method, ReaderParams params)
        throws XMLStreamException {
        boolean state = true;
        while (xmler.hasNext() && state) {
            int eventType = xmler.next();
            switch (eventType) {
                case XMLStreamConstants.START_ELEMENT:
                    String key = xmler.getName().getLocalPart();
                    if (startElement.equals(key)) {
                        method.accept(xmler, params);
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
    }

    static class ReaderParams {
        private final DicomModel model;
        private final Map<String, LoadSeries> seriesMap;

        public ReaderParams(DicomModel model, Map<String, LoadSeries> seriesMap) {
            this.model = model;
            this.seriesMap = seriesMap;
        }

        public DicomModel getModel() {
            return model;
        }

        public Map<String, LoadSeries> getSeriesMap() {
            return seriesMap;
        }

    }
}
