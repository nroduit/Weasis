package org.weasis.dicom.explorer;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.VR;
import org.dcm4che2.media.DicomDirReader;
import org.dcm4che2.media.DirectoryRecordType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.dicom.codec.ColorModelFactory;
import org.weasis.dicom.codec.DicomInstance;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.DicomVideoSeries;
import org.weasis.dicom.codec.wado.WadoParameters;
import org.weasis.dicom.explorer.wado.DicomManager;
import org.weasis.dicom.explorer.wado.DownloadPriority;
import org.weasis.dicom.explorer.wado.LoadSeries;

public class DicomDirLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomDirLoader.class);

    private DicomDirReader reader;
    private final DicomModel dicomModel;
    private final ArrayList<LoadSeries> seriesList;
    private final WadoParameters wadoParameters;

    public DicomDirLoader(File dcmDirFile, DataExplorerModel explorerModel) {
        if (dcmDirFile == null || !dcmDirFile.canRead() || !(explorerModel instanceof DicomModel)) {
            throw new IllegalArgumentException("invalid parameters"); //$NON-NLS-1$
        }
        this.dicomModel = (DicomModel) explorerModel;
        wadoParameters = new WadoParameters("", true, "", null, null);
        seriesList = new ArrayList<LoadSeries>();
        try {
            reader = new DicomDirReader(dcmDirFile);
            reader.setShowInactiveRecords(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ArrayList<LoadSeries> readDicomDir() {
        DicomObject dcmPatient = null;
        try {
            dcmPatient = reader.findFirstRootRecord();
        } catch (IOException e1) {
            LOGGER.error("Cannot find Patient in DICOMDIR !");
        }

        MediaSeriesGroup patient = null;
        int pat = 0;
        while (dcmPatient != null) {
            if (DirectoryRecordType.PATIENT.equals(dcmPatient.getString(Tag.DirectoryRecordType))) {
                try {
                    String name = DicomMediaIO.buildPatientName(dcmPatient.getString(Tag.PatientName));
                    String patientPseudoUID =
                        DicomMediaIO.buildPatientPseudoUID(dcmPatient.getString(Tag.PatientID), name,
                            DicomMediaIO.getDateFromDicomElement(dcmPatient, Tag.PatientBirthDate, null));

                    patient = dicomModel.getHierarchyNode(TreeModel.rootNode, patientPseudoUID);
                    if (patient == null) {
                        patient = new MediaSeriesGroupNode(TagW.PatientPseudoUID, patientPseudoUID, TagW.PatientName);
                        DicomMediaIO.writeMetaData(patient, dcmPatient);
                        dicomModel.addHierarchyNode(TreeModel.rootNode, patient);
                        pat++;
                    }
                    parseStudy(patient, dcmPatient);
                } catch (Exception e) {
                    System.out.println(e.toString());
                }
            }
            dcmPatient = findNextSiblingRecord(dcmPatient);
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
        return seriesList;
    }

    private void parseStudy(MediaSeriesGroup patient, DicomObject dcmPatient) {
        DicomObject dcmStudy = findFirstChildRecord(dcmPatient);
        while (dcmStudy != null) {
            if (DirectoryRecordType.STUDY.equals(dcmStudy.getString(Tag.DirectoryRecordType))) {
                String studyUID = dcmStudy.getString(Tag.StudyInstanceUID, DicomMediaIO.NO_VALUE);
                MediaSeriesGroup study = dicomModel.getHierarchyNode(patient, studyUID);
                if (study == null) {
                    study = new MediaSeriesGroupNode(TagW.StudyInstanceUID, studyUID, TagW.StudyDate);
                    DicomMediaIO.writeMetaData(study, dcmStudy);
                    dicomModel.addHierarchyNode(patient, study);
                }
                parseSeries(patient, study, dcmStudy);
            }
            dcmStudy = findNextSiblingRecord(dcmStudy);
        }
    }

    private void parseSeries(MediaSeriesGroup patient, MediaSeriesGroup study, DicomObject dcmStudy) {
        DicomObject series = findFirstChildRecord(dcmStudy);
        while (series != null) {
            if (DirectoryRecordType.SERIES.equals(series.getString(Tag.DirectoryRecordType))) {
                String seriesUID = series.getString(Tag.SeriesInstanceUID, DicomMediaIO.NO_VALUE);
                Series dicomSeries = (Series) dicomModel.getHierarchyNode(study, seriesUID);
                if (dicomSeries == null) {
                    dicomSeries = new DicomSeries(seriesUID);
                    dicomSeries.setTag(TagW.ExplorerModel, dicomModel);
                    dicomSeries.setTag(TagW.WadoParameters, wadoParameters);
                    dicomSeries.setTag(TagW.WadoInstanceReferenceList, new ArrayList<DicomInstance>());
                    DicomMediaIO.writeMetaData(dicomSeries, series);
                    dicomModel.addHierarchyNode(study, dicomSeries);
                } else {
                    WadoParameters wado = (WadoParameters) dicomSeries.getTagValue(TagW.WadoParameters);
                    if (wado == null) {
                        // Should not happen
                        dicomSeries.setTag(TagW.WadoParameters, wadoParameters);
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
                DicomObject iconInstance = null;
                // Icon Image Sequence (0088,0200).This Icon Image is representative of the Series. It may or may not
                // correspond to one of the images of the Series.
                DicomElement seq = series.get(Tag.IconImageSequence);
                if (seq != null && seq.vr() == VR.SQ && seq.countItems() > 0) {
                    iconInstance = seq.getDicomObject(0);
                }
                DicomObject instance = findFirstChildRecord(series);
                while (instance != null) {
                    if (DirectoryRecordType.IMAGE.equals(instance.getString(Tag.DirectoryRecordType))) {
                        // update instance level tag values

                        String sopInstanceUID = instance.getString(Tag.ReferencedSOPInstanceUIDInFile);
                        // updateTag(dataset, Tag.InstanceNumber, instance.getString(Tag.InstanceNumber));

                        if (sopInstanceUID != null) {
                            DicomInstance dcmInstance =
                                new DicomInstance(sopInstanceUID, instance.getString(Tag.TransferSyntaxUID));
                            if (containsInstance && dicomInstances.contains(dcmInstance)) {
                                LOGGER.warn("DICOM instance {} already exists, abort downloading.", sopInstanceUID); //$NON-NLS-1$
                            } else {
                                String filename = toFileName(instance);
                                if (filename != null) {
                                    dcmInstance.setInstanceNumber(DicomMediaIO.getIntegerFromDicomElement(instance,
                                        Tag.InstanceNumber, -1));
                                    dcmInstance.setDirectDownloadFile(filename);
                                    dicomInstances.add(dcmInstance);
                                    if (iconInstance == null) {
                                        // Icon Image Sequence (0088,0200). This Icon Image is representative of the
                                        // Image. Only a single Item is permitted in this Sequence.
                                        seq = instance.get(Tag.IconImageSequence);
                                        if (seq != null && seq.vr() == VR.SQ && seq.countItems() > 0) {
                                            iconInstance = seq.getDicomObject(0);
                                        }
                                    }
                                }
                            }
                        }

                    }
                    instance = findNextSiblingRecord(instance);
                }

                if (dicomInstances.size() > 0) {
                    if (dicomInstances.size() == 1
                        && "1.2.840.10008.1.2.4.100".equals(dicomInstances.get(0).getTransferSyntaxUID())) { //$NON-NLS-1$
                        dicomModel.removeHierarchyNode(study, dicomSeries);
                        dicomSeries = new DicomVideoSeries((DicomSeries) dicomSeries);
                        dicomModel.addHierarchyNode(study, dicomSeries);
                    }

                    dicomSeries.setTag(TagW.DirectDownloadThumbnail, readDicomDirIcon(iconInstance));
                    final LoadSeries loadSeries =
                        new LoadSeries(dicomSeries, dicomModel, 1, DicomManager.getInstance().isPortableDirCache());

                    DownloadPriority priority =
                        new DownloadPriority((String) patient.getTagValue(TagW.PatientName),
                            (String) study.getTagValue(TagW.StudyInstanceUID),
                            (Date) study.getTagValue(TagW.StudyDate),
                            (Integer) dicomSeries.getTagValue(TagW.SeriesNumber));
                    loadSeries.setPriority(priority);
                    seriesList.add(loadSeries);
                }
            }
            series = findNextSiblingRecord(series);
        }
    }

    private String readDicomDirIcon(DicomObject iconInstance) {
        if (iconInstance != null) {
            byte[] pixelData = iconInstance.getBytes(Tag.PixelData);
            if (pixelData != null) {
                File thumbnailPath = null;
                try {
                    thumbnailPath = File.createTempFile("tumb_", ".jpg", AbstractProperties.APP_TEMP_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (thumbnailPath != null) {
                    /*
                     * DICOM 11_03pu.pdf - F7 Only monochrome and palette color images shall be used. Samples per Pixel
                     * (0028,0002) shall have a Value of 1, Photometric Interpretation (0028,0004) shall have a Value of
                     * either MONOCHROME 1, MONOCHROME 2 or PALETTE COLOR, Planar Configuration (0028,0006) shall not be
                     * present
                     */
                    int width = iconInstance.getInt(Tag.Columns);
                    int height = iconInstance.getInt(Tag.Rows);
                    WritableRaster raster =
                        WritableRaster.createInterleavedRaster(DataBuffer.TYPE_BYTE, width, height, 1, new Point(0, 0));
                    raster.setDataElements(0, 0, width, height, pixelData);
                    BufferedImage thumbnail =
                        new BufferedImage(ColorModelFactory.createColorModel(iconInstance), raster, false, null);
                    if (ImageFiler.writeJPG(thumbnailPath, thumbnail, 0.75f)) {
                        return thumbnailPath.getPath();
                    }
                }
            }
        }
        return null;
    }

    private DicomObject findFirstChildRecord(DicomObject dcmObject) {
        try {
            return reader.findFirstChildRecord(dcmObject);
        } catch (IOException e) {
            LOGGER.error("Cannot read first DICOMDIR entry!", e.getMessage());
        }
        return null;
    }

    private DicomObject findNextSiblingRecord(DicomObject dcmObject) {
        try {
            return reader.findNextSiblingRecord(dcmObject);
        } catch (IOException e) {
            LOGGER.error("Cannot read next DICOMDIR entry!", e.getMessage());
        }
        return null;
    }

    private String toFileName(DicomObject dcmObject) {
        String[] fileID = dcmObject.getStrings(Tag.ReferencedFileID);
        if (fileID == null || fileID.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder(fileID[0]);
        for (int i = 1; i < fileID.length; i++) {
            sb.append(File.separatorChar).append(fileID[i]);
        }
        return new File(reader.getFile().getParent(), sb.toString()).toURI().toString();
    }
}
