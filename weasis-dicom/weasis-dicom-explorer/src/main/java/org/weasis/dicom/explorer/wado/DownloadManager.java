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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagElement;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.GzipManager;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.dicom.codec.DicomInstance;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.DicomVideo;
import org.weasis.dicom.codec.wado.WadoParameters;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.Messages;
import org.xml.sax.InputSource;

public class DownloadManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadManager.class);

    public DownloadManager() {
    }

    public static ArrayList<LoadSeries> buildDicomSeriesFromXml(URI uri, final DicomModel model) {
        ArrayList<LoadSeries> seriesList = new ArrayList<LoadSeries>();
        XMLStreamReader xmler = null;
        InputStream stream = null;
        try {
            XMLInputFactory xmlif = XMLInputFactory.newInstance();
            URL url = uri.toURL();
            LOGGER.info("Downloading WADO references: {}", url); //$NON-NLS-1$

            String path = uri.getPath();
            if (path.endsWith(".gz")) { //$NON-NLS-1$
                stream = GzipManager.gzipUncompressToStream(url);
            } else if (path.endsWith(".xml")) { //$NON-NLS-1$
                stream = url.openStream();
            } else {
                // In case wado file has no extension
                File outFile = File.createTempFile("wadoqueries_", "", AbstractProperties.APP_TEMP_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                if (FileUtil.writeFile(url, outFile)) {
                    String mime = MimeInspector.getMimeType(outFile);
                    if (mime != null && mime.equals("application/x-gzip")) { //$NON-NLS-1$
                        stream = new BufferedInputStream((new GZIPInputStream(new FileInputStream((outFile)))));
                    } else {
                        stream = url.openStream();
                    }
                }
            }
            xmler = xmlif.createXMLStreamReader(stream);

            // TODO cannot reset stream after validating xml, try to write a temporary file
            // Source xmlFile = new StAXSource(xmler);
            // SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            // try {
            // Schema schema = schemaFactory.newSchema(DownloadManager.class.getResource("/config/wado_query.xsd"));
            // Validator validator = schema.newValidator();
            // validator.validate(xmlFile);
            // LOGGER.info("wado_query is valid");
            // } catch (SAXException e) {
            // LOGGER.error("wado_query is NOT valid");
            // LOGGER.error("Reason: {}", e.getLocalizedMessage());
            // }

            int eventType;
            if (xmler.hasNext()) {
                eventType = xmler.next();
                switch (eventType) {
                    case XMLStreamConstants.START_ELEMENT:
                        String key = xmler.getName().toString();
                        // First Tag must <wado_query>
                        if (WadoParameters.TAG_DOCUMENT_ROOT.equals(key)) {
                            String wadoURL = getTagAttribute(xmler, WadoParameters.TAG_WADO_URL, null);
                            boolean onlySopUID =
                                Boolean.valueOf(getTagAttribute(xmler, WadoParameters.TAG_WADO_ONLY_SOP_UID, "false")); //$NON-NLS-1$
                            String additionnalParameters =
                                getTagAttribute(xmler, WadoParameters.TAG_WADO_ADDITIONNAL_PARAMETERS, ""); //$NON-NLS-1$
                            String overrideList = getTagAttribute(xmler, WadoParameters.TAG_WADO_OVERRIDE_TAGS, null);
                            String webLogin = getTagAttribute(xmler, WadoParameters.TAG_WADO_WEB_LOGIN, null); //$NON-NLS-1$
                            final WadoParameters wadoParameters =
                                new WadoParameters(wadoURL, onlySopUID, additionnalParameters, overrideList, webLogin);
                            int pat = 0;
                            MediaSeriesGroup patient = null;
                            while (xmler.hasNext()) {
                                eventType = xmler.next();
                                switch (eventType) {
                                    case XMLStreamConstants.START_ELEMENT:
                                        // <Patient> Tag
                                        if (TagElement.DICOM_LEVEL.Patient.name().equals(xmler.getName().toString())) {
                                            patient = readPatient(model, seriesList, xmler, wadoParameters);
                                            pat++;
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

                        }
                        break;
                    default:
                        break;
                }
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (XMLStreamException e) {
            e.printStackTrace();
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
        String patientID = getTagAttribute(xmler, TagElement.PatientID.getTagName(), ""); //$NON-NLS-1$
        String patientBirthDate = getTagAttribute(xmler, TagElement.PatientBirthDate.getTagName(), ""); //$NON-NLS-1$
        String name =
            getTagAttribute(xmler, TagElement.PatientName.getTagName(), Messages.getString("DownloadManager.unknown")); //$NON-NLS-1$

        // TODO set preferences of building patientPseudoUID
        String patientPseudoUID = patientID + patientBirthDate;
        MediaSeriesGroup patient = model.getHierarchyNode(TreeModel.rootNode, patientPseudoUID);
        if (patient == null) {
            patient = new MediaSeriesGroupNode(TagElement.PatientPseudoUID, patientPseudoUID, TagElement.PatientName);
            patient.setTag(TagElement.PatientID, patientID);
            if (name.trim().equals("")) { //$NON-NLS-1$
                name = Messages.getString("DownloadManager.unknown"); //$NON-NLS-1$
            }
            name = name.replace("^", " "); //$NON-NLS-1$ //$NON-NLS-2$
            patient.setTag(TagElement.PatientName, name);

            patient.setTag(TagElement.PatientSex, getTagAttribute(xmler, TagElement.PatientSex.getTagName(), "O")); //$NON-NLS-1$
            patient.setTag(TagElement.PatientBirthDate, TagElement.getDicomDate(patientBirthDate));
            patient.setTag(TagElement.PatientBirthTime, TagElement.getDicomTime(getTagAttribute(xmler,
                TagElement.PatientBirthTime.getTagName(), null)));
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
                    if (TagElement.DICOM_LEVEL.Study.name().equals(xmler.getName().toString())) {
                        readStudy(model, seriesList, xmler, patient, wadoParameters);
                    }
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    if (TagElement.DICOM_LEVEL.Patient.name().equals(xmler.getName().toString())) {
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
        String studyUID = getTagAttribute(xmler, TagElement.StudyInstanceUID.getTagName(), ""); //$NON-NLS-1$
        MediaSeriesGroup study = model.getHierarchyNode(patient, studyUID);
        if (study == null) {
            study = new MediaSeriesGroupNode(TagElement.StudyInstanceUID, studyUID, TagElement.StudyDate);
            study.setTag(TagElement.StudyDate, TagElement.getDicomDate(getTagAttribute(xmler, TagElement.StudyDate
                .getTagName(), null)));
            study.setTag(TagElement.StudyTime, TagElement.getDicomTime(getTagAttribute(xmler, TagElement.StudyTime
                .getTagName(), null)));
            study.setTag(TagElement.StudyDescription, getTagAttribute(xmler, TagElement.StudyDescription.getTagName(),
                "")); //$NON-NLS-1$
            study.setTag(TagElement.AccessionNumber, getTagAttribute(xmler, TagElement.AccessionNumber.getTagName(),
                null));
            study.setTag(TagElement.StudyID, getTagAttribute(xmler, TagElement.StudyID.getTagName(), null));
            study.setTag(TagElement.ReferringPhysicianName, getTagAttribute(xmler, TagElement.ReferringPhysicianName
                .getTagName(), null));
            LOGGER.debug("Set date to study: {}", study.getTagValue(TagElement.StudyDate));
            model.addHierarchyNode(patient, study);
        }

        int eventType;
        boolean state = true;
        while (xmler.hasNext() && state) {
            eventType = xmler.next();
            switch (eventType) {
                case XMLStreamConstants.START_ELEMENT:
                    // <Series> Tag
                    if (TagElement.DICOM_LEVEL.Series.name().equals(xmler.getName().toString())) {
                        readSeries(model, seriesList, xmler, patient, study, wadoParameters);
                    }
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    if (TagElement.DICOM_LEVEL.Study.name().equals(xmler.getName().toString())) {
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

        String seriesUID = getTagAttribute(xmler, TagElement.SeriesInstanceUID.getTagName(), ""); //$NON-NLS-1$

        Series dicomSeries = (Series) model.getHierarchyNode(study, seriesUID);
        if (dicomSeries == null) {
            dicomSeries = new DicomSeries(seriesUID);
            dicomSeries.setTag(TagElement.ExplorerModel, model);
            dicomSeries.setTag(TagElement.SeriesInstanceUID, seriesUID);
            dicomSeries.setTag(TagElement.SeriesNumber, getIntegerTagAttribute(xmler, TagElement.SeriesNumber
                .getTagName(), null));
            dicomSeries.setTag(TagElement.Modality, getTagAttribute(xmler, TagElement.Modality.getTagName(), null));
            dicomSeries.setTag(TagElement.SeriesDescription, getTagAttribute(xmler, TagElement.SeriesDescription
                .getTagName(), "")); //$NON-NLS-1$
            dicomSeries.setTag(TagElement.WadoTransferSyntaxUID, getTagAttribute(xmler,
                TagElement.WadoTransferSyntaxUID.getTagName(), null));
            dicomSeries.setTag(TagElement.WadoCompressionRate, getIntegerTagAttribute(xmler,
                TagElement.WadoCompressionRate.getTagName(), 0));
            dicomSeries.setTag(TagElement.WadoParameters, wadoParameters);
            dicomSeries.setTag(TagElement.WadoInstanceReferenceList, new ArrayList<DicomInstance>());
            model.addHierarchyNode(study, dicomSeries);
        } else {
            WadoParameters wado = (WadoParameters) dicomSeries.getTagValue(TagElement.WadoParameters);
            if (wado == null) {
                // Should not happen
                dicomSeries.setTag(TagElement.WadoParameters, wadoParameters);
            } else if (!wado.getWadoURL().equals(wadoParameters.getWadoURL())) {
                LOGGER.error("Wado parameters must be unique for a DICOM Series: {}", dicomSeries); //$NON-NLS-1$
                // Cannot have multiple wado parameter for a Series
                return dicomSeries;
            }
        }

        List<DicomInstance> dicomInstances =
            (List<DicomInstance>) dicomSeries.getTagValue(TagElement.WadoInstanceReferenceList);
        boolean containsInstance = false;
        if (dicomInstances == null) {
            dicomSeries.setTag(TagElement.WadoInstanceReferenceList, new ArrayList<DicomInstance>());
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
                    if (TagElement.DICOM_LEVEL.Instance.name().equals(xmler.getName().toString())) {
                        String sopInstanceUID = getTagAttribute(xmler, TagElement.SOPInstanceUID.getTagName(), null);
                        if (sopInstanceUID != null) {
                            if (containsInstance && dicomInstances.contains(sopInstanceUID)) {
                                LOGGER.warn("DICOM instance {} already exists, abort downloading.", sopInstanceUID); //$NON-NLS-1$
                            } else {
                                String tsuid = getTagAttribute(xmler, TagElement.TransferSyntaxUID.getTagName(), null);
                                DicomInstance dcmInstance = new DicomInstance(sopInstanceUID, tsuid);
                                dcmInstance.setInstanceNumber(getIntegerTagAttribute(xmler, TagElement.InstanceNumber
                                    .getTagName(), -1));
                                dicomInstances.add(dcmInstance);
                            }
                        }

                    }
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    if (TagElement.DICOM_LEVEL.Series.name().equals(xmler.getName().toString())) {
                        state = false;
                    }
                    break;
                default:
                    break;
            }
        }

        if (dicomInstances.size() > 0) {
            // TODO Manage video if tsuid is not present
            if (dicomInstances.size() == 1
                && "1.2.840.10008.1.2.4.100".equals(dicomInstances.get(0).getTransferSyntaxUID())) { //$NON-NLS-1$
                model.removeHierarchyNode(study, dicomSeries);
                dicomSeries = new DicomVideo((DicomSeries) dicomSeries);
                model.addHierarchyNode(study, dicomSeries);
            }

            String modality = (String) dicomSeries.getTagValue(TagElement.Modality);
            boolean ps = modality != null && ("PR".equals(modality) || "KO".equals(modality)); //$NON-NLS-1$ //$NON-NLS-2$
            final LoadSeries loadSeries = new LoadSeries(dicomSeries, model);
            Integer sn = (Integer) (ps ? Integer.MAX_VALUE : dicomSeries.getTagValue(TagElement.SeriesNumber));
            DownloadPriority priority =
                new DownloadPriority((String) patient.getTagValue(TagElement.PatientName), (String) study
                    .getTagValue(TagElement.StudyInstanceUID), (Date) study.getTagValue(TagElement.StudyDate), sn);
            loadSeries.setPriority(priority);
            if (!ps) {
                loadSeries.startDownloadImageReference(wadoParameters);
            }
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

    private static Integer getIntegerTagAttribute(XMLStreamReader xmler, String attribute, Integer defaultValue) {
        if (attribute != null) {
            try {
                String val = xmler.getAttributeValue(null, attribute);
                if (val != null) {
                    return Integer.valueOf(val);
                }
            } catch (NumberFormatException e) {
            }
        }
        return defaultValue;
    }

    public static ArrayList<LoadSeries> buildDicomStructure(URI uri, final DicomModel model) {
        ArrayList<LoadSeries> tasks = new ArrayList<LoadSeries>();
        try {
            URL url = uri.toURL();
            LOGGER.info("Downloading WADO references: {}", url); //$NON-NLS-1$
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            Document doc = null;
            InputSource inputSource;
            if (uri.getPath().endsWith(".gz")) { //$NON-NLS-1$
                inputSource = GzipManager.gzipUncompressToInputSource(url);
            } else {
                inputSource = new InputSource(uri.toURL().openStream());
            }
            try {
                doc = docBuilder.parse(inputSource);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }

            // normalize text representation
            doc.getDocumentElement().normalize();
            Element root = doc.getDocumentElement();

            if (root == null || !WadoParameters.TAG_DOCUMENT_ROOT.equals(root.getNodeName())) {
                return null;
            }
            String wadoURL = getTagAttribute(root, WadoParameters.TAG_WADO_URL, null);
            boolean onlySopUID = Boolean.valueOf(getTagAttribute(root, WadoParameters.TAG_WADO_ONLY_SOP_UID, "false")); //$NON-NLS-1$
            String additionnalParameters = getTagAttribute(root, WadoParameters.TAG_WADO_ADDITIONNAL_PARAMETERS, ""); //$NON-NLS-1$
            String overrideList = getTagAttribute(root, WadoParameters.TAG_WADO_OVERRIDE_TAGS, null);
            String webLogin = getTagAttribute(root, WadoParameters.TAG_WADO_WEB_LOGIN, null); //$NON-NLS-1$
            final WadoParameters wadoParameters =
                new WadoParameters(wadoURL, onlySopUID, additionnalParameters, overrideList, webLogin);
            NodeList listOfPatient = doc.getElementsByTagName(TagElement.DICOM_LEVEL.Patient.name());

            for (int m = 0; m < listOfPatient.getLength(); m++) {
                Node patientNode = listOfPatient.item(m);
                if (patientNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element patientElement = (Element) patientNode;
                    // PatientID, PatientBirthDate, StudyInstanceUID, SeriesInstanceUID and SOPInstanceUID override
                    // the tags located in DICOM object (because original DICOM can contain different values after
                    // merging patient or study
                    String patientID = getTagAttribute(patientElement, TagElement.PatientID.getTagName(), ""); //$NON-NLS-1$
                    String patientBirthDate =
                        getTagAttribute(patientElement, TagElement.PatientBirthDate.getTagName(), ""); //$NON-NLS-1$
                    String name =
                        getTagAttribute(patientElement, TagElement.PatientName.getTagName(), Messages
                            .getString("DownloadManager.unknown")); //$NON-NLS-1$

                    // TODO set preferences of building patientPseudoUID
                    String patientPseudoUID = patientID + patientBirthDate;
                    MediaSeriesGroup patient = model.getHierarchyNode(TreeModel.rootNode, patientPseudoUID);
                    if (patient == null) {
                        patient =
                            new MediaSeriesGroupNode(TagElement.PatientPseudoUID, patientPseudoUID,
                                TagElement.PatientName);
                        patient.setTag(TagElement.PatientID, patientID);
                        if (name.trim().equals("")) { //$NON-NLS-1$
                            name = Messages.getString("DownloadManager.unknown"); //$NON-NLS-1$
                        }
                        name = name.replace("^", " "); //$NON-NLS-1$ //$NON-NLS-2$
                        patient.setTag(TagElement.PatientName, name);

                        patient.setTag(TagElement.PatientSex, getTagAttribute(patientElement, TagElement.PatientSex
                            .getTagName(), "O")); //$NON-NLS-1$
                        patient.setTag(TagElement.PatientBirthDate, TagElement.getDicomDate(patientBirthDate));
                        patient.setTag(TagElement.PatientBirthTime, TagElement.getDicomTime(getTagAttribute(
                            patientElement, TagElement.PatientBirthTime.getTagName(), null)));
                        model.addHierarchyNode(TreeModel.rootNode, patient);
                        LOGGER.info("Adding new patient: " + patient); //$NON-NLS-1$
                    } else {
                        if (listOfPatient.getLength() == 1) {
                            final MediaSeriesGroup uniquePatient = patient;
                            GuiExecutor.instance().execute(new Runnable() {

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
                    }

                    NodeList listOfStudy = patientElement.getElementsByTagName(TagElement.DICOM_LEVEL.Study.name());
                    for (int i = 0; i < listOfStudy.getLength(); i++) {
                        Node studyNode = listOfStudy.item(i);
                        if (studyNode.getNodeType() == Node.ELEMENT_NODE) {
                            Element studyElement = (Element) studyNode;
                            String studyUID =
                                getTagAttribute(studyElement, TagElement.StudyInstanceUID.getTagName(), ""); //$NON-NLS-1$
                            MediaSeriesGroup study = model.getHierarchyNode(patient, studyUID);
                            if (study == null) {
                                study =
                                    new MediaSeriesGroupNode(TagElement.StudyInstanceUID, studyUID,
                                        TagElement.StudyDate);
                                study.setTag(TagElement.StudyDate, TagElement.getDicomDate(getTagAttribute(
                                    studyElement, TagElement.StudyDate.getTagName(), null)));
                                study.setTag(TagElement.StudyTime, TagElement.getDicomTime(getTagAttribute(
                                    studyElement, TagElement.StudyTime.getTagName(), null)));
                                study.setTag(TagElement.StudyDescription, getTagAttribute(studyElement,
                                    TagElement.StudyDescription.getTagName(), "")); //$NON-NLS-1$
                                study.setTag(TagElement.AccessionNumber, getTagAttribute(studyElement,
                                    TagElement.AccessionNumber.getTagName(), null));
                                study.setTag(TagElement.StudyID, getTagAttribute(studyElement, TagElement.StudyID
                                    .getTagName(), null));
                                study.setTag(TagElement.ReferringPhysicianName, getTagAttribute(studyElement,
                                    TagElement.ReferringPhysicianName.getTagName(), null));
                                model.addHierarchyNode(patient, study);
                            }

                            NodeList seriesList =
                                studyElement.getElementsByTagName(TagElement.DICOM_LEVEL.Series.name());
                            for (int j = 0; j < seriesList.getLength(); j++) {
                                Node seriesNode = seriesList.item(j);
                                if (seriesNode.getNodeType() == Node.ELEMENT_NODE) {
                                    Element seriesElement = (Element) seriesNode;
                                    String seriesUID =
                                        getTagAttribute(seriesElement, TagElement.SeriesInstanceUID.getTagName(), ""); //$NON-NLS-1$

                                    boolean buildLoadSeries = false;
                                    Series dicomSeries = (Series) model.getHierarchyNode(study, seriesUID);
                                    if (dicomSeries == null) {
                                        dicomSeries = new DicomSeries(seriesUID);
                                        dicomSeries.setTag(TagElement.ExplorerModel, model);
                                        dicomSeries.setTag(TagElement.SeriesInstanceUID, seriesUID);
                                        dicomSeries.setTag(TagElement.SeriesNumber, getIntegerTagAttribute(
                                            seriesElement, TagElement.SeriesNumber.getTagName(), null));
                                        dicomSeries.setTag(TagElement.Modality, getTagAttribute(seriesElement,
                                            TagElement.Modality.getTagName(), null));
                                        dicomSeries.setTag(TagElement.SeriesDescription, getTagAttribute(seriesElement,
                                            TagElement.SeriesDescription.getTagName(), "")); //$NON-NLS-1$
                                        dicomSeries.setTag(TagElement.WadoTransferSyntaxUID, getTagAttribute(
                                            seriesElement, TagElement.WadoTransferSyntaxUID.getTagName(), null));
                                        dicomSeries.setTag(TagElement.WadoCompressionRate, getIntegerTagAttribute(
                                            seriesElement, TagElement.WadoCompressionRate.getTagName(), 0));
                                        dicomSeries.setTag(TagElement.WadoParameters, wadoParameters);
                                        dicomSeries.setTag(TagElement.WadoInstanceReferenceList,
                                            new ArrayList<DicomInstance>());
                                        model.addHierarchyNode(study, dicomSeries);
                                        buildLoadSeries = true;
                                    } else {
                                        LOGGER.warn("{} already exists, abort downloading.", dicomSeries); //$NON-NLS-1$
                                    }

                                    NodeList sopInstanceUIDList =
                                        seriesElement.getElementsByTagName(TagElement.DICOM_LEVEL.Instance.name());
                                    for (int k = 0; k < sopInstanceUIDList.getLength(); k++) {
                                        Node sop = sopInstanceUIDList.item(k);
                                        if (sop.getNodeType() == Node.ELEMENT_NODE) {
                                            Element sopInst = (Element) sop;
                                            String sopInstanceUID =
                                                getTagAttribute(sopInst, TagElement.SOPInstanceUID.getTagName(), null);
                                            if (sopInstanceUID != null) {
                                                String tsuid =
                                                    getTagAttribute(sopInst, TagElement.TransferSyntaxUID.getTagName(),
                                                        null);
                                                if ("1.2.840.10008.1.2.4.100".equals(tsuid) //$NON-NLS-1$
                                                    && sopInstanceUIDList.getLength() == 1) {
                                                    dicomSeries = new DicomVideo(seriesUID);
                                                    dicomSeries.setTag(TagElement.SeriesNumber, getIntegerTagAttribute(
                                                        seriesElement, TagElement.SeriesNumber.getTagName(), 0));
                                                    dicomSeries.setTag(TagElement.Modality, getTagAttribute(
                                                        seriesElement, TagElement.Modality.getTagName(), null));
                                                    dicomSeries.setTag(TagElement.SeriesDescription, getTagAttribute(
                                                        seriesElement, TagElement.SeriesDescription.getTagName(), "")); //$NON-NLS-1$
                                                    dicomSeries.setTag(TagElement.WadoTransferSyntaxUID,
                                                        getTagAttribute(seriesElement, TagElement.WadoTransferSyntaxUID
                                                            .getTagName(), null));
                                                    dicomSeries.setTag(TagElement.WadoCompressionRate,
                                                        getIntegerTagAttribute(seriesElement,
                                                            TagElement.WadoCompressionRate.getTagName(), 0));
                                                    dicomSeries.setTag(TagElement.WadoParameters, wadoParameters);
                                                    dicomSeries.setTag(TagElement.WadoInstanceReferenceList,
                                                        new ArrayList<DicomInstance>());
                                                    model.addHierarchyNode(study, dicomSeries);
                                                    buildLoadSeries = true;
                                                }
                                                List<DicomInstance> dicomInstances =
                                                    (List<DicomInstance>) dicomSeries
                                                        .getTagValue(TagElement.WadoInstanceReferenceList);
                                                DicomInstance dcmInstance = new DicomInstance(sopInstanceUID, tsuid);
                                                dcmInstance.setInstanceNumber(getIntegerTagAttribute(sopInst,
                                                    TagElement.InstanceNumber.getTagName(), -1));
                                                dicomInstances.add(dcmInstance);
                                            }
                                        }
                                    }
                                    if (buildLoadSeries) {
                                        String modality = (String) dicomSeries.getTagValue(TagElement.Modality);
                                        boolean ps =
                                            modality != null && ("PR".equals(modality) || "KO".equals(modality)); //$NON-NLS-1$ //$NON-NLS-2$
                                        final LoadSeries loadSeries = new LoadSeries(dicomSeries, model);
                                        Integer sn =
                                            (Integer) (ps ? Integer.MAX_VALUE : dicomSeries
                                                .getTagValue(TagElement.SeriesNumber));
                                        DownloadPriority priority =
                                            new DownloadPriority((String) patient.getTagValue(TagElement.PatientName),
                                                (String) study.getTagValue(TagElement.StudyInstanceUID), (Date) study
                                                    .getTagValue(TagElement.StudyDate), sn);
                                        loadSeries.setPriority(priority);
                                        if (!ps) {
                                            loadSeries.startDownloadImageReference(wadoParameters);
                                        }
                                        tasks.add(loadSeries);
                                    }
                                }
                            }
                        }
                    }

                }
            }
        }

        catch (Throwable t) {
            t.printStackTrace();
        }
        return tasks;
    }

    private static String getTagValue(NodeList nodeList, String defaultValue) {
        if (nodeList != null && nodeList.getLength() > 0) {
            Element lastNameElement = (Element) nodeList.item(0);
            NodeList textLNList = lastNameElement.getChildNodes();
            if (textLNList.getLength() > 0) {
                String val = (textLNList.item(0)).getNodeValue();
                return val == null ? defaultValue : val;
            }
        }
        return defaultValue;
    }

    private static String getTagAttribute(Element element, String attribute, String defaultValue) {
        if (element != null && attribute != null) {
            try {
                Attr attr = element.getAttributeNode(attribute);
                if (attr != null) {
                    return attr.getValue();
                }
            } catch (Exception e) {
            }
        }
        return defaultValue;
    }

    private static Integer getIntegerTagAttribute(Element element, String attribute, Integer defaultValue) {
        if (element != null && attribute != null) {
            try {
                Attr attr = element.getAttributeNode(attribute);
                if (attr != null) {
                    return Integer.valueOf(attr.getValue().trim());
                }
            } catch (Exception e) {
            }
        }
        return defaultValue;
    }

}
