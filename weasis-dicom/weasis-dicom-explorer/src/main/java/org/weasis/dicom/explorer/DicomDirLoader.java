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
package org.weasis.dicom.explorer;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.image.PhotometricInterpretation;
import org.dcm4che3.media.DicomDirReader;
import org.dcm4che3.media.DicomDirWriter;
import org.dcm4che3.media.RecordFactory;
import org.dcm4che3.media.RecordType;
import org.dcm4che3.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.codec.utils.PatientComparator;
import org.weasis.dicom.explorer.wado.DownloadPriority;
import org.weasis.dicom.explorer.wado.LoadSeries;
import org.weasis.dicom.explorer.wado.SeriesInstanceList;
import org.weasis.dicom.mf.SopInstance;
import org.weasis.dicom.mf.WadoParameters;
import org.weasis.opencv.op.ImageProcessor;

public class DicomDirLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomDirLoader.class);

    public static final RecordFactory RecordFactory = new RecordFactory();

    private final DicomModel dicomModel;
    private final ArrayList<LoadSeries> seriesList;
    private final WadoParameters wadoParameters;
    private final boolean writeInCache;
    private final File dcmDirFile;

    public DicomDirLoader(File dcmDirFile, DataExplorerModel explorerModel, boolean writeInCache) {
        if (dcmDirFile == null || !dcmDirFile.canRead() || !(explorerModel instanceof DicomModel)) {
            throw new IllegalArgumentException("invalid parameters"); //$NON-NLS-1$
        }
        this.dicomModel = (DicomModel) explorerModel;
        this.writeInCache = writeInCache;
        this.dcmDirFile = dcmDirFile;
        wadoParameters = new WadoParameters("", true); //$NON-NLS-1$
        seriesList = new ArrayList<>();

    }

    public List<LoadSeries> readDicomDir() {
        Attributes dcmPatient = null;
        MediaSeriesGroup patient = null;

        try (DicomDirReader reader = new DicomDirReader(dcmDirFile)) {
            dcmPatient = findFirstRootDirectoryRecordInUse(reader);

            while (dcmPatient != null) {
                if (RecordType.PATIENT.name().equals(dcmPatient.getString(Tag.DirectoryRecordType))) {
                    patient = parsePatient(dcmPatient, reader);
                }
                dcmPatient = findNextSiblingRecord(dcmPatient, reader);
            }
        } catch (IOException e) {
            LOGGER.error("Cannot read DICOMDIR !", e); //$NON-NLS-1$
        }

        if (patient != null) {
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
        return seriesList;
    }

    private MediaSeriesGroup parsePatient(Attributes dcmPatient, DicomDirReader reader) {
        MediaSeriesGroup patient = null;
        try {
            PatientComparator patientComparator = new PatientComparator(dcmPatient);
            String patientPseudoUID = patientComparator.buildPatientPseudoUID();

            patient = dicomModel.getHierarchyNode(MediaSeriesGroupNode.rootNode, patientPseudoUID);
            if (patient == null) {
                patient = new MediaSeriesGroupNode(TagD.getUID(Level.PATIENT), patientPseudoUID,
                    DicomModel.patient.getTagView());
                DicomMediaUtils.writeMetaData(patient, dcmPatient);
                dicomModel.addHierarchyNode(MediaSeriesGroupNode.rootNode, patient);
            }
            parseStudy(patient, dcmPatient, reader);
        } catch (Exception e) {
            LOGGER.error("Cannot read DICOMDIR !", e); //$NON-NLS-1$
        }
        return patient;
    }

    private void parseStudy(MediaSeriesGroup patient, Attributes dcmPatient, DicomDirReader reader) {
        Attributes dcmStudy = findFirstChildRecord(dcmPatient, reader);
        while (dcmStudy != null) {
            if (RecordType.STUDY.name().equals(dcmStudy.getString(Tag.DirectoryRecordType))) {
                String studyUID = (String) TagD.getUID(Level.STUDY).getValue(dcmStudy);
                MediaSeriesGroup study = dicomModel.getHierarchyNode(patient, studyUID);
                if (study == null) {
                    study = new MediaSeriesGroupNode(TagD.getUID(Level.STUDY), studyUID, DicomModel.study.getTagView());
                    DicomMediaUtils.writeMetaData(study, dcmStudy);
                    dicomModel.addHierarchyNode(patient, study);
                }
                parseSeries(patient, study, dcmStudy, reader);
            }
            dcmStudy = findNextSiblingRecord(dcmStudy, reader);
        }
    }

    private void parseSeries(MediaSeriesGroup patient, MediaSeriesGroup study, Attributes dcmStudy,
        DicomDirReader reader) {
        Attributes series = findFirstChildRecord(dcmStudy, reader);
        while (series != null) {
            if (RecordType.SERIES.name().equals(series.getString(Tag.DirectoryRecordType))) {
                String seriesUID = series.getString(Tag.SeriesInstanceUID, TagW.NO_VALUE);
                Series dicomSeries = (Series) dicomModel.getHierarchyNode(study, seriesUID);
                if (dicomSeries == null) {
                    dicomSeries = new DicomSeries(seriesUID);
                    dicomSeries.setTag(TagW.ExplorerModel, dicomModel);
                    dicomSeries.setTag(TagW.WadoParameters, wadoParameters);
                    DicomMediaUtils.writeMetaData(dicomSeries, series);
                    dicomModel.addHierarchyNode(study, dicomSeries);
                } else {
                    WadoParameters wado = (WadoParameters) dicomSeries.getTagValue(TagW.WadoParameters);
                    if (wado == null) {
                        // Should never happen
                        dicomSeries.setTag(TagW.WadoParameters, wadoParameters);
                    }
                }

                SeriesInstanceList seriesInstanceList =
                    Optional.ofNullable((SeriesInstanceList) dicomSeries.getTagValue(TagW.WadoInstanceReferenceList))
                        .orElseGet(SeriesInstanceList::new);
                dicomSeries.setTag(TagW.WadoInstanceReferenceList, seriesInstanceList);

                // Icon Image Sequence (0088,0200).This Icon Image is representative of the Series. It may or may not
                // correspond to one of the images of the Series.
                Attributes iconInstance = series.getNestedDataset(Tag.IconImageSequence);

                Attributes instance = findFirstChildRecord(series, reader);
                while (instance != null) {
                    // Try to read all the file types of the Series.

                    String sopInstanceUID = instance.getString(Tag.ReferencedSOPInstanceUIDInFile);
                    if (sopInstanceUID != null) {
                        Integer frame = DicomMediaUtils.getIntegerFromDicomElement(instance, Tag.InstanceNumber, null);
                        SopInstance sop = seriesInstanceList.getSopInstance(sopInstanceUID, frame);
                        if (sop == null) {
                            File file = toFileName(instance, reader);
                            if (file != null) {
                                if (file.exists()) {
                                    sop = new SopInstance(sopInstanceUID, frame);
                                    sop.setDirectDownloadFile(file.toURI().toString());
                                    seriesInstanceList.addSopInstance(sop);
                                    if (iconInstance == null) {
                                        // Icon Image Sequence (0088,0200). This Icon Image is representative of the
                                        // Image. Only a single Item is permitted in this Sequence.
                                        iconInstance = instance.getNestedDataset(Tag.IconImageSequence);
                                    }
                                } else {
                                    LOGGER.error("Missing DICOMDIR entry: {}", file.getPath()); //$NON-NLS-1$
                                }
                            }
                        }
                    }
                    instance = findNextSiblingRecord(instance, reader);
                }

                if (!seriesInstanceList.isEmpty()) {
                    dicomSeries.setTag(TagW.DirectDownloadThumbnail, readDicomDirIcon(iconInstance));
                    dicomSeries.setTag(TagW.ReadFromDicomdir, true);
                    final LoadSeries loadSeries = new LoadSeries(dicomSeries, dicomModel, 1, writeInCache);
                    loadSeries.setPriority(new DownloadPriority(patient, study, dicomSeries, false));
                    seriesList.add(loadSeries);
                }
            }
            series = findNextSiblingRecord(series, reader);
        }
    }

    /**
     * Reads DICOMDIR icon. Only monochrome and palette color images shall be used. Samples per Pixel (0028,0002) shall
     * have a Value of 1, Photometric Interpretation (0028,0004) shall have a Value of either MONOCHROME 1, MONOCHROME 2
     * or PALETTE COLOR, Planar Configuration (0028,0006) shall not be present.
     *
     * @see <a href="http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_F.7.html">F.7 Icon Image Key
     *      Definition</a>
     *
     * @param iconInstance
     *            Attributes
     * @return the thumbnail path
     */
    private String readDicomDirIcon(Attributes iconInstance) {
        if (iconInstance != null) {
            byte[] pixelData = null;
            try {
                pixelData = iconInstance.getBytes(Tag.PixelData);
                if (pixelData != null) {
                    File thumbnailPath = File.createTempFile("tumb_", ".jpg", Thumbnail.THUMBNAIL_CACHE_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                    if (thumbnailPath != null) {
                        int width = iconInstance.getInt(Tag.Columns, 0);
                        int height = iconInstance.getInt(Tag.Rows, 0);
                        if (width != 0 && height != 0) {          
                            WritableRaster raster =
                                Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, width, height, 1, new Point(0, 0));
                            raster.setDataElements(0, 0, width, height, pixelData);
                            PhotometricInterpretation pmi = PhotometricInterpretation
                                .fromString(iconInstance.getString(Tag.PhotometricInterpretation, "MONOCHROME2")); //$NON-NLS-1$
                            BufferedImage thumbnail = new BufferedImage(
                                pmi.createColorModel(8, DataBuffer.TYPE_BYTE, iconInstance), raster, false, null);
                            if (ImageProcessor.writeImage(thumbnail, thumbnailPath)) {
                                return thumbnailPath.getPath();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Cannot read Icon in DICOMDIR!", e); //$NON-NLS-1$
            }
        }
        return null;
    }

    private Attributes findFirstChildRecord(Attributes dcmObject, DicomDirReader reader) {
        try {
            return reader.findLowerDirectoryRecordInUse(dcmObject, true);
        } catch (IOException e) {
            LOGGER.error("Cannot read first DICOMDIR entry!", e); //$NON-NLS-1$
        }
        return null;
    }

    private Attributes findNextSiblingRecord(Attributes dcmObject, DicomDirReader reader) {
        try {
            return reader.findNextDirectoryRecordInUse(dcmObject, true);
        } catch (IOException e) {
            LOGGER.error("Cannot read next DICOMDIR entry!", e); //$NON-NLS-1$
        }
        return null;
    }

    private Attributes findFirstRootDirectoryRecordInUse(DicomDirReader reader) {
        try {
            return reader.findFirstRootDirectoryRecordInUse(true);
        } catch (IOException e) {
            LOGGER.error("Cannot find Patient in DICOMDIR !", e); //$NON-NLS-1$
        }
        return null;
    }

    private File toFileName(Attributes dcmObject, DicomDirReader reader) {
        String[] fileID = dcmObject.getStrings(Tag.ReferencedFileID);
        if (fileID == null || fileID.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder(fileID[0]);
        for (int i = 1; i < fileID.length; i++) {
            sb.append(File.separatorChar).append(fileID[i]);
        }
        File file = new File(reader.getFile().getParent(), sb.toString());
        if (!file.exists()) {
            // Try to find lower case relative path, it happens sometimes when mounting cdrom on Linux
            File fileLowerCase = new File(reader.getFile().getParent(), sb.toString().toLowerCase());
            if (fileLowerCase.exists()) {
                file = fileLowerCase;
            }
        }

        return file;
    }

    public static DicomDirWriter open(File file) throws IOException {
        if (file.createNewFile()) {
            DicomDirWriter.createEmptyDirectory(file, UIDUtils.createUID(), null, null, null);
        }
        return DicomDirWriter.open(file);
    }
}
