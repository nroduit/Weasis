/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.codec;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.lang.ref.Reference;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Implementation;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.image.Overlays;
import org.dcm4che3.image.PhotometricInterpretation;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReadParam;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReaderSpi;
import org.dcm4che3.imageio.plugins.dcm.DicomMetaData;
import org.dcm4che3.imageio.stream.ImageInputStreamAdapter;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.io.DicomOutputStream;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.FileCache;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SimpleTagable;
import org.weasis.core.api.media.data.SoftHashMap;
import org.weasis.core.api.media.data.TagView;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.display.CornerDisplay;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.codec.display.ModalityInfoData;
import org.weasis.dicom.codec.display.ModalityView;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.codec.utils.DicomImageUtils;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.codec.utils.OverlayUtils;
import org.weasis.dicom.codec.utils.PatientComparator;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.op.ImageProcessor;

public class DicomMediaIO extends ImageReader implements DcmMediaReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(DicomMediaIO.class);

    public static final File DICOM_EXPORT_DIR = AppProperties.buildAccessibleTempDirectory("dicom"); //$NON-NLS-1$
    public static final File CACHE_UNCOMPRESSED_DIR =
        AppProperties.buildAccessibleTempDirectory(AppProperties.FILE_CACHE_DIR.getName(), "dcm-rawcv"); //$NON-NLS-1$

    public static final String DICOM_MIMETYPE = "application/dicom"; //$NON-NLS-1$
    public static final String IMAGE_MIMETYPE = "image/dicom"; //$NON-NLS-1$
    public static final String SERIES_VIDEO_MIMETYPE = "video/dicom"; //$NON-NLS-1$
    public static final String SERIES_MIMETYPE = "series/dicom"; //$NON-NLS-1$
    public static final String SERIES_PR_MIMETYPE = "pr/dicom"; //$NON-NLS-1$
    public static final String SERIES_KO_MIMETYPE = "ko/dicom"; //$NON-NLS-1$

    public static final String SERIES_ENCAP_DOC_MIMETYPE = "encap/dicom"; //$NON-NLS-1$
    public static final String UNREADABLE = "unreadable/dicom"; //$NON-NLS-1$
    public static final String SERIES_XDSI = "xds-i/dicom"; //$NON-NLS-1$

    private static final AtomicInteger instanceID = new AtomicInteger(1);
    public static final TagManager tagManager = new TagManager();

    static {
        // PatientPseudoUID is the unique identifying tag for this patient group
        // -------- Mandatory Tags --------
        tagManager.addTag(Tag.PatientID, Level.PATIENT);
        tagManager.addTag(Tag.PatientName, Level.PATIENT);
        // -------- End of Mandatory Tags --------
        tagManager.addTag(Tag.PatientBirthDate, Level.PATIENT);
        tagManager.addTag(Tag.PatientBirthTime, Level.PATIENT);
        tagManager.addTag(Tag.PatientAge, Level.SERIES); // needs to be updated for each series if computed
        tagManager.addTag(Tag.PatientSex, Level.PATIENT);
        tagManager.addTag(Tag.IssuerOfPatientID, Level.PATIENT);
        tagManager.addTag(Tag.PatientWeight, Level.PATIENT);
        tagManager.addTag(Tag.PatientComments, Level.PATIENT);

        // StudyInstanceUID is the unique identifying tag for this study group
        tagManager.addTag(Tag.StudyID, Level.STUDY);
        tagManager.addTag(Tag.StudyDate, Level.STUDY);
        tagManager.addTag(Tag.StudyTime, Level.STUDY);
        tagManager.addTag(Tag.StudyDescription, Level.STUDY);
        tagManager.addTag(Tag.StudyComments, Level.STUDY);
        tagManager.addTag(Tag.AccessionNumber, Level.STUDY);
        tagManager.addTag(Tag.ModalitiesInStudy, Level.STUDY); // not required
        tagManager.addTag(Tag.NumberOfStudyRelatedInstances, Level.STUDY); // not required
        tagManager.addTag(Tag.NumberOfStudyRelatedSeries, Level.STUDY); // not required

        // SubseriesInstanceUID is the unique identifying tag for this series group
        // -------- Mandatory Tags --------
        tagManager.addTag(Tag.SeriesInstanceUID, Level.SERIES);
        tagManager.addTag(Tag.Modality, Level.SERIES);
        // -------- End of Mandatory Tags --------
        tagManager.addTag(Tag.SeriesDescription, Level.SERIES);
        tagManager.addTag(Tag.SOPClassUID, Level.SERIES);
        tagManager.addTag(Tag.RetrieveAETitle, Level.SERIES); // not required
        tagManager.addTag(Tag.ReferringPhysicianName, Level.SERIES);
        tagManager.addTag(Tag.InstitutionName, Level.SERIES);
        tagManager.addTag(Tag.InstitutionalDepartmentName, Level.SERIES);
        tagManager.addTag(Tag.StationName, Level.SERIES);
        tagManager.addTag(Tag.Manufacturer, Level.SERIES);
        tagManager.addTag(Tag.ManufacturerModelName, Level.SERIES);
        tagManager.addTag(Tag.SeriesNumber, Level.SERIES);
        tagManager.addTag(Tag.NumberOfFrames, Level.SERIES);
        tagManager.addTag(Tag.SeriesDate, Level.SERIES);
        tagManager.addTag(Tag.SeriesTime, Level.SERIES);
        tagManager.addTag(Tag.PerformedProcedureStepStartDate, Level.SERIES); // not
                                                                              // required
        tagManager.addTag(Tag.PerformedProcedureStepStartTime, Level.SERIES); // not
                                                                              // required
        // Should be in image
        // C.7.6.5 Cine Module
        // http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.7.6.5.html
        tagManager.addTag(Tag.PreferredPlaybackSequencing, Level.SERIES);
        tagManager.addTag(Tag.CineRate, Level.SERIES);
        tagManager.addTag(Tag.RecommendedDisplayFrameRate, Level.SERIES);
        tagManager.addTag(Tag.KVP, Level.SERIES);
        tagManager.addTag(Tag.BodyPartExamined, Level.SERIES);
        tagManager.addTag(Tag.FrameOfReferenceUID, Level.SERIES);
        tagManager.addTag(Tag.NumberOfSeriesRelatedInstances, Level.SERIES);
        tagManager.addTag(Tag.Laterality, Level.SERIES);

        // SOPInstanceUID is the unique identifying tag of a DICOM object
        // -------- Mandatory Tags --------
        // Tags for identifying group (Patient, Study, Series)
        tagManager.addTag(Tag.PatientID, Level.INSTANCE);
        tagManager.addTag(Tag.PatientName, Level.INSTANCE);
        tagManager.addTag(Tag.PatientBirthDate, Level.INSTANCE);
        tagManager.addTag(Tag.IssuerOfPatientID, Level.INSTANCE);
        tagManager.addTag(Tag.StudyInstanceUID, Level.INSTANCE);
        tagManager.addTag(Tag.SeriesInstanceUID, Level.INSTANCE);
        tagManager.addTag(Tag.Modality, Level.INSTANCE);
        // -------- End of Mandatory Tags --------

        tagManager.addTag(Tag.GantryDetectorTilt, Level.INSTANCE);
        tagManager.addTag(Tag.PatientOrientation, Level.INSTANCE);
        tagManager.addTag(Tag.SliceLocation, Level.INSTANCE);
        tagManager.addTag(Tag.SliceThickness, Level.INSTANCE);
        tagManager.addTag(Tag.AcquisitionDate, Level.INSTANCE);
        tagManager.addTag(Tag.AcquisitionTime, Level.INSTANCE);
        tagManager.addTag(Tag.ContentDate, Level.INSTANCE);
        tagManager.addTag(Tag.ContentTime, Level.INSTANCE);
        tagManager.addTag(Tag.DiffusionBValue, Level.INSTANCE);
        tagManager.addTag(Tag.MIMETypeOfEncapsulatedDocument, Level.INSTANCE);
        tagManager.addTag(Tag.PixelDataProviderURL, Level.INSTANCE);

        for (Entry<Modality, ModalityInfoData> entry : ModalityView.getModalityViewEntries()) {
            readTagsInModalityView(entry.getValue().getCornerInfo(CornerDisplay.TOP_LEFT).getInfos());
            readTagsInModalityView(entry.getValue().getCornerInfo(CornerDisplay.TOP_RIGHT).getInfos());
            readTagsInModalityView(entry.getValue().getCornerInfo(CornerDisplay.BOTTOM_RIGHT).getInfos());
        }

        // TODO init with a profile
        DicomMediaUtils.enableAnonymizationProfile(true);
    }

    public static final Map<String, DicomSpecialElementFactory> DCM_ELEMENT_FACTORIES = new HashMap<>();

    static {
        /*
         * DICOM PR and KO are not displayed with a special viewer but are transversally managed objects. So they are
         * not registered from a viewer.
         */
        DCM_ELEMENT_FACTORIES.put("PR", new DicomSpecialElementFactory() { //$NON-NLS-1$

            @Override
            public String getSeriesMimeType() {
                return SERIES_PR_MIMETYPE;
            }

            @Override
            public String[] getModalities() {
                return new String[] { "PR" }; //$NON-NLS-1$
            }

            @Override
            public DicomSpecialElement buildDicomSpecialElement(DicomMediaIO mediaIO) {
                return new PRSpecialElement(mediaIO);
            }
        });
        DCM_ELEMENT_FACTORIES.put("KO", new DicomSpecialElementFactory() { //$NON-NLS-1$

            @Override
            public String getSeriesMimeType() {
                return SERIES_KO_MIMETYPE;
            }

            @Override
            public String[] getModalities() {
                return new String[] { "KO" }; //$NON-NLS-1$
            }

            @Override
            public DicomSpecialElement buildDicomSpecialElement(DicomMediaIO mediaIO) {
                if (RejectedKOSpecialElement.isRejectionKOS(mediaIO)) {
                    return new RejectedKOSpecialElement(mediaIO);
                }
                return new KOSpecialElement(mediaIO);
            }
        });
    }

    static final DicomImageReaderSpi dicomImageReaderSpi = new DicomImageReaderSpi();

    private static final SoftHashMap<DicomMediaIO, DicomMetaData> HEADER_CACHE =
        new SoftHashMap<DicomMediaIO, DicomMetaData>() {

            @Override
            public void removeElement(Reference<? extends DicomMetaData> soft) {
                DicomMediaIO key = reverseLookup.remove(soft);
                if (key != null) {
                    hash.remove(key);
                    key.reset();
                }
            }
        };

    // The above softReference HEADER_CACHE shall be used instead of the following dcmMetadata variable to get access to
    // the current DicomObject unless it's virtual and then URI doesn't exit. This case appends when the dcmMetadata is
    // created within the application and is given to the ImageReader constructor
    private DicomMetaData dcmMetadata = null;

    private BulkData pixeldata;
    private final VR.Holder pixeldataVR = new VR.Holder();
    private Fragments pixeldataFragments;
    private PhotometricInterpretation pmi;

    private URI uri;
    private int numberOfFrame;
    private final Map<TagW, Object> tags;
    private MediaElement[] image = null;
    private String mimeType;
    private final ArrayList<Integer> fragmentsPositions = new ArrayList<>();

    private ImageInputStream iis;
    private DicomInputStream dis;
    private int dataType = 0;
    private boolean hasPixel = false;
    private boolean banded = false;
    private boolean bigendian = false;
    private boolean compressedData = false;

    private int bitsStored;
    private int bitsAllocated;
    private int highBit;
    /**
     * Store the transfer syntax locally in case it gets modified to re-write the image
     */
    private String tsuid;
    /** Used to indicate whether or not to skip large private dicom elements. */
    private boolean skipLargePrivate = true;
    private volatile boolean readingHeader = false;
    private volatile boolean readingImage = false;

    private final FileCache fileCache;

    public DicomMediaIO(URI uri) {
        super(dicomImageReaderSpi);
        this.uri = Objects.requireNonNull(uri);
        this.numberOfFrame = 0;
        this.tags = new HashMap<>();
        this.mimeType = DICOM_MIMETYPE;
        this.fileCache = new FileCache(this);
    }

    public DicomMediaIO(File source) {
        this(Objects.requireNonNull(source).toURI());
    }

    public DicomMediaIO(Path path) throws URISyntaxException {
        this(Objects.requireNonNull(path).toUri());
    }

    public DicomMediaIO(Attributes dcmItems) throws URISyntaxException {
        this(new URI("data:" + Objects.requireNonNull(dcmItems).getString(Tag.SOPInstanceUID))); //$NON-NLS-1$
        this.dcmMetadata = new DicomMetaData(null, Objects.requireNonNull(dcmItems));
    }

    private static void readTagsInModalityView(TagView[] views) {
        for (TagView tagView : views) {
            if (tagView != null) {
                for (TagW tag : tagView.getTag()) {
                    if (tag != null) {
                        if (!DicomMediaIO.tagManager.contains(tag, Level.PATIENT)
                            && !DicomMediaIO.tagManager.contains(tag, Level.STUDY)
                            && !DicomMediaIO.tagManager.contains(tag, Level.SERIES)) {
                            DicomMediaIO.tagManager.addTag(tag, Level.INSTANCE);
                        }
                    }
                }
            }
        }
    }

    @Override
    public synchronized void replaceURI(URI uri) {
        if (!Objects.equals(this.uri, Objects.requireNonNull(uri))) {
            this.uri = uri;
            reset();
        }
    }

    /**
     *
     * @return true when the DICOM Object has no source file (only in memory)
     */
    public boolean isEditableDicom() {
        return dcmMetadata != null && "data".equals(uri.getScheme()); //$NON-NLS-1$
    }

    public synchronized boolean isReadableDicom() {
        if (UNREADABLE.equals(mimeType)) {
            // Return true only to display the error message in the view
            return true;
        }
        if ("data".equals(uri.getScheme()) && dcmMetadata == null) { //$NON-NLS-1$
            return false;
        }

        if (tags.size() == 0) {
            try {
                DicomMetaData md = readMetaData();
                Attributes fmi = md.getFileMetaInformation();
                Attributes header = md.getAttributes();
                // Exclude DICOMDIR
                String mediaStorageSOPClassUID = fmi == null ? null : fmi.getString(Tag.MediaStorageSOPClassUID);
                if ("1.2.840.10008.1.3.10".equals(mediaStorageSOPClassUID)) { //$NON-NLS-1$
                    mimeType = UNREADABLE;
                    close();
                    return false;
                }
                if (hasPixel) {
                    String ts = fmi == null ? null : fmi.getString(Tag.TransferSyntaxUID);
                    if (ts != null && ts.startsWith("1.2.840.10008.1.2.4.10")) { //$NON-NLS-1$ $NON-NLS-2$
                        // MPEG2 MP@ML 1.2.840.10008.1.2.4.100
                        // MEPG2 MP@HL 1.2.840.10008.1.2.4.101
                        // MPEG4 AVC/H.264 1.2.840.10008.1.2.4.102
                        // MPEG4 AVC/H.264 BD 1.2.840.10008.1.2.4.103
                        mimeType = SERIES_VIDEO_MIMETYPE;
                    } else {
                        mimeType = IMAGE_MIMETYPE;
                    }
                } else {
                    boolean special = setDicomSpecialType(header);
                    if (!special) {
                        // Not supported DICOM file
                        mimeType = UNREADABLE;
                        close();
                        return false;
                    }
                }

                writeInstanceTags(fmi, header);

            } catch (Exception | OutOfMemoryError e) {
                mimeType = UNREADABLE;
                LOGGER.error("Cannot read DICOM:", e); //$NON-NLS-1$
                close();
                return false;
            }
        }
        return true;
    }

    private boolean setDicomSpecialType(Attributes header) {
        String modality = header.getString(Tag.Modality);
        if (modality != null) {
            String encap = header.getString(Tag.MIMETypeOfEncapsulatedDocument);
            DicomSpecialElementFactory factory = DCM_ELEMENT_FACTORIES.get(modality);
            if (factory != null) {
                mimeType = factory.getSeriesMimeType();
                // Can be not null for instance by ECG with encapsulated pdf
                if (encap == null) {
                    return true;
                }
            }
            if (encap != null) {
                mimeType = SERIES_ENCAP_DOC_MIMETYPE;
                return true;
            }

        }
        return false;
    }

    public String getMimeType() {
        return mimeType;
    }

    @Override
    public Object getTagValue(TagW tag) {
        return tag == null ? null : tags.get(tag);
    }

    @Override
    public void setTag(TagW tag, Object value) {
        DicomMediaUtils.setTag(tags, tag, value);
    }

    @Override
    public void setTagNoNull(TagW tag, Object value) {
        if (value != null) {
            setTag(tag, value);
        }
    }

    @Override
    public boolean containTagKey(TagW tag) {
        return tags.containsKey(tag);
    }

    @Override
    public Iterator<Entry<TagW, Object>> getTagEntrySetIterator() {
        return tags.entrySet().iterator();
    }

    @Override
    public void writeMetaData(MediaSeriesGroup group) {
        if (group == null) {
            return;
        }
        // Get the dicom header
        Attributes header = getDicomObject();
        DicomMediaUtils.writeMetaData(group, header);

        // Series Group
        if (TagW.SubseriesInstanceUID.equals(group.getTagID())) {
            // Information for series ToolTips
            group.setTagNoNull(TagD.get(Tag.PatientName), getTagValue(TagD.get(Tag.PatientName)));
            group.setTagNoNull(TagD.get(Tag.StudyDescription), header.getString(Tag.StudyDescription));
        }
    }

    private void writeInstanceTags(Attributes fmi, Attributes header) {
        if (tags.size() > 0 || header == null) {
            return;
        }

        tagManager.readTags(Level.INSTANCE, header, this);

        // -------- Mandatory Tags --------
        // Tags for identifying group (Patient, Study, Series)
        // Global Identifier for the patient.
        PatientComparator patientComparator = new PatientComparator(this);
        setTag(TagW.PatientPseudoUID, patientComparator.buildPatientPseudoUID());

        Integer instNb =
            DicomMediaUtils.getIntegerFromDicomElement(header, Tag.InstanceNumber, instanceID.incrementAndGet());
        setTag(TagD.get(Tag.InstanceNumber), instNb);
        setTag(TagD.get(Tag.SOPInstanceUID), header.getString(Tag.SOPInstanceUID, String.valueOf(instNb)));
        if (fmi != null) {
            setTagNoNull(TagD.get(Tag.TransferSyntaxUID), fmi.getString(Tag.TransferSyntaxUID));
        }
        // -------- End of Mandatory Tags --------

        writeImageValues(header);
        writeSharedFunctionalGroupsSequence(header);
        DicomMediaUtils.writePerFrameFunctionalGroupsSequence(this, header, 0);

        boolean pr = SERIES_PR_MIMETYPE.equals(mimeType);
        boolean ko = SERIES_KO_MIMETYPE.equals(mimeType);
        if (pr) {
            // Set the series list for applying the PR
            DicomMediaUtils.buildSeriesReferences(this, header);
            DicomMediaUtils.readPRLUTsModule(header, this);
            setTagNoNull(TagW.HasOverlay, DicomMediaUtils.hasOverlay(header));
        }
        if (pr || ko) {
            // Set other required fields
            TagW[] tagIDs = TagD.getTagFromIDs(Tag.SeriesDescription, Tag.SeriesDate, Tag.SeriesTime, Tag.SeriesNumber);
            for (TagW tag : tagIDs) {
                tag.readValue(header, this);
            }
        }

        DicomMediaUtils.computeSlicePositionVector(this);
        DicomMediaUtils.setShutter(this, header);
        DicomMediaUtils.computeSUVFactor(header, this, 0);
    }

    private void writeSharedFunctionalGroupsSequence(Attributes header) {
        if (header != null) {
            DicomMediaUtils.writeFunctionalGroupsSequence(this,
                header.getNestedDataset(Tag.SharedFunctionalGroupsSequence));
        }
    }

    private void writeImageValues(Attributes header) {
        if (header != null && hasPixel) {
            TagD.get(Tag.ImagePositionPatient).readValue(header, this);
            TagD.get(Tag.ImageOrientationPatient).readValue(header, this);
            setTagNoNull(TagW.ImageOrientationPlane,
                ImageOrientation.makeImageOrientationLabelFromImageOrientationPatient(
                    TagD.getTagValue(this, Tag.ImageOrientationPatient, double[].class)));

            bitsAllocated = DicomMediaUtils.getIntegerFromDicomElement(header, Tag.BitsAllocated, 8);
            bitsStored = DicomMediaUtils.getIntegerFromDicomElement(header, Tag.BitsStored, bitsAllocated);
            highBit = DicomMediaUtils.getIntegerFromDicomElement(header, Tag.HighBit, bitsStored - 1);
            if (highBit >= bitsAllocated) {
                highBit = bitsStored - 1;
            }
            int pixelRepresentation = DicomMediaUtils.getIntegerFromDicomElement(header, Tag.PixelRepresentation, 0);
            setTagNoNull(TagD.get(Tag.BitsAllocated), bitsAllocated);
            setTagNoNull(TagD.get(Tag.BitsStored), bitsStored);
            setTagNoNull(TagD.get(Tag.PixelRepresentation), pixelRepresentation);

            TagD.get(Tag.PixelSpacing).readValue(header, this);
            TagD.get(Tag.PixelAspectRatio).readValue(header, this);
            TagD.get(Tag.PixelSpacingCalibrationDescription).readValue(header, this);
            TagD.get(Tag.ImagerPixelSpacing).readValue(header, this);
            TagD.get(Tag.NominalScannedPixelSpacing).readValue(header, this);

            DicomMediaUtils.applyModalityLutModule(header, this, null);

            TagD.get(Tag.PixelIntensityRelationship).readValue(header, this);

            DicomMediaUtils.applyVoiLutModule(header, header, this, null);

            TagD.get(Tag.Units).readValue(header, this);
            TagD.get(Tag.NumberOfFrames).readValue(header, this);
            setTagNoNull(TagW.HasOverlay, DicomMediaUtils.hasOverlay(header));

            int samplesPerPixel = DicomMediaUtils.getIntegerFromDicomElement(header, Tag.SamplesPerPixel, 1);
            setTag(TagD.get(Tag.SamplesPerPixel), samplesPerPixel);
            banded = samplesPerPixel > 1
                && DicomMediaUtils.getIntegerFromDicomElement(header, Tag.PlanarConfiguration, 0) != 0;
            dataType = bitsAllocated <= 8 ? DataBuffer.TYPE_BYTE
                : pixelRepresentation != 0 ? DataBuffer.TYPE_SHORT : DataBuffer.TYPE_USHORT;
            if (bitsAllocated == 32 && samplesPerPixel == 1) {
                dataType = header.getValue(Tag.FloatPixelData, pixeldataVR) == null ? DataBuffer.TYPE_INT
                    : DataBuffer.TYPE_FLOAT;
            } else if (bitsAllocated == 64 && samplesPerPixel == 1) {
                dataType = DataBuffer.TYPE_DOUBLE;
            }
            String photometricInterpretation = header.getString(Tag.PhotometricInterpretation, "MONOCHROME2"); //$NON-NLS-1$
            pmi = PhotometricInterpretation.fromString(photometricInterpretation);
            TagD.get(Tag.PresentationLUTShape).readValue(header, this);
            setTag(TagD.get(Tag.PhotometricInterpretation), photometricInterpretation);
            setTag(TagW.MonoChrome,
                samplesPerPixel == 1 && !"PALETTE COLOR".equalsIgnoreCase(photometricInterpretation)); //$NON-NLS-1$

            setTag(TagD.get(Tag.Rows), DicomMediaUtils.getIntegerFromDicomElement(header, Tag.Rows, 0));
            setTag(TagD.get(Tag.Columns), DicomMediaUtils.getIntegerFromDicomElement(header, Tag.Columns, 0));

            setTagNoNull(TagD.get(Tag.PixelPaddingValue),
                DicomMediaUtils.getIntPixelValue(header, Tag.PixelPaddingValue, pixelRepresentation != 0, bitsStored));
            setTagNoNull(TagD.get(Tag.PixelPaddingRangeLimit), DicomMediaUtils.getIntPixelValue(header,
                Tag.PixelPaddingRangeLimit, pixelRepresentation != 0, bitsStored));

            /*
             * * @see <a href=
             * "http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.7.6.html#sect_C.7.6.1.1.5" >C
             * .7.6.1.1.5 Lossy Image Compression</a>
             */
            setTagNoNull(TagD.get(Tag.LossyImageCompression),
                header.getString(Tag.LossyImageCompression, header.getString(Tag.LossyImageCompressionRetired)));
            TagD.get(Tag.LossyImageCompressionRatio).readValue(header, this);
            TagD.get(Tag.LossyImageCompressionMethod).readValue(header, this);
            TagD.get(Tag.DerivationDescription).readValue(header, this);

            /*
             *
             * For overlays encoded in Overlay Data Element (60xx,3000), Overlay Bits Allocated (60xx,0100) is always 1
             * and Overlay Bit Position (60xx,0102) is always 0.
             *
             * @see - Dicom Standard 2011 - PS 3.5 § 8.1.2 Overlay data encoding of related data elements
             */
            if (bitsStored < bitsAllocated && dataType >= DataBuffer.TYPE_BYTE && dataType < DataBuffer.TYPE_INT
                && Overlays.getEmbeddedOverlayGroupOffsets(header).length > 0) {
                int high = highBit + 1;
                int val = (1 << high) - 1;
                if (high > bitsStored) {
                    val -= (1 << (high - bitsStored)) - 1;
                }
                /*
                 * Set to 0 all bits upper than highBit and if lower than high-bitsStored (=> all bits outside
                 * bitStored)
                 */
                setTagNoNull(TagW.OverlayBitMask, val);

                if (high > bitsStored) {
                    // Combine to the slope value
                    Double slopeVal = TagD.getTagValue(this, Tag.RescaleSlope, Double.class);
                    if (slopeVal == null) {
                        slopeVal = 1.0;
                        // Set valid modality LUT values
                        Double ri = TagD.getTagValue(this, Tag.RescaleIntercept, Double.class);
                        String rt = TagD.getTagValue(this, Tag.RescaleType, String.class);
                        setTag(TagD.get(Tag.RescaleIntercept), ri == null ? 0.0 : ri);
                        setTag(TagD.get(Tag.RescaleType), rt == null ? "US" : rt); //$NON-NLS-1$
                    }
                    // Divide pixel value by (2 ^ rightBit) => remove right bits
                    slopeVal /= 1 << (high - bitsStored);
                    setTag(TagD.get(Tag.RescaleSlope), slopeVal);
                }
            }
        }
    }

    public boolean containTag(int id) {
        for (Iterator<TagW> it = tags.keySet().iterator(); it.hasNext();) {
            if (it.next().getId() == id) {
                return true;
            }
        }
        return false;
    }

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public FileCache getFileCache() {
        return fileCache;
    }

    @Override
    public boolean buildFile(File output) {
        // When object is in memory, write it
        if (isEditableDicom()) {
            Attributes dcm = getDicomObject();
            if (dcm != null) {
                try (DicomOutputStream out = new DicomOutputStream(output)) {
                    out.writeDataset(dcm.createFileMetaInformation(UID.ImplicitVRLittleEndian), dcm);
                    return true;
                } catch (IOException e) {
                    LOGGER.error("Cannot write dicom file", e); //$NON-NLS-1$
                }
            }
        }
        return false;
    }

    @Override
    public PlanarImage getImageFragment(MediaElement media) throws Exception {
        if (Objects.requireNonNull(media).getKey() instanceof Integer) {
            return getImageFragment(media, (Integer) media.getKey());
        }
        return null;
    }

    protected PlanarImage getImageFragment(MediaElement media, int frame) throws Exception {
        if (isReadableDicom()) {
            if (frame >= 0 && frame < numberOfFrame && hasPixel) {
                LOGGER.debug("Start reading dicom image frame: {} sopUID: {}", //$NON-NLS-1$
                    frame, TagD.getTagValue(this, Tag.SOPInstanceUID));

                PlanarImage img = getUncacheImage(media, frame);
                if (pmi == PhotometricInterpretation.PALETTE_COLOR) {
                    img = DicomImageUtils.getRGBImageFromPaletteColorModel(img, getDicomObject());
                }

                /*
                 * Handle overlay in pixel data: extract the overlay, serialize it in a file and set all values to O in
                 * the pixel data.
                 */
                Integer overlayBitMask = (Integer) getTagValue(TagW.OverlayBitMask);
                if (overlayBitMask != null) {
                    if (media.getTagValue(TagW.OverlayBurninDataPath) == null) {
                        // Serialize overlay (from pixel data)
                        Attributes ds = getDicomObject();
                        int[] embeddedOverlayGroupOffsets = Overlays.getEmbeddedOverlayGroupOffsets(ds);
                        if (embeddedOverlayGroupOffsets.length > 0) {
                            FileOutputStream fileOut = null;
                            ObjectOutput objOut = null;
                            try {
                                byte[][] overlayData = new byte[embeddedOverlayGroupOffsets.length][];
                                for (int i = 0; i < embeddedOverlayGroupOffsets.length; i++) {
                                    overlayData[i] = OverlayUtils.extractOverlay(embeddedOverlayGroupOffsets[i],
                                        ImageConversion.toBufferedImage(img).getRaster(), ds);
                                }
                                File file = File.createTempFile("ovly_", "", AppProperties.FILE_CACHE_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                                fileOut = new FileOutputStream(file);
                                objOut = new ObjectOutputStream(fileOut);
                                objOut.writeObject(overlayData);
                                media.setTag(TagW.OverlayBurninDataPath, file.getPath());
                            } catch (Exception e) {
                                LOGGER.error("Cannot serialize overlay", e); //$NON-NLS-1$
                            } finally {
                                FileUtil.safeClose(objOut);
                                FileUtil.safeClose(fileOut);
                            }
                        }
                    }
                    // Set to 0 all bits outside bitStored
                    img = ImageProcessor.bitwiseAnd(img.toMat(), overlayBitMask);
                }

                return img;
            }
        }
        return null;
    }

    private static Mat getMatBuffer(ExtendSegmentedInputImageStream extParams) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(extParams.getFile(), "r")) { //$NON-NLS-1$

            Long cols = Arrays.stream(extParams.getSegmentLengths()).sum();
            Mat buf = new Mat(1, cols.intValue(), CvType.CV_8UC1);
            long[] pos = extParams.getSegmentPositions();
            int offset = 0;
            for (int i = 0; i < pos.length; i++) {
                int len = (int) extParams.getSegmentLengths()[i];
                byte[] b = new byte[len];
                raf.seek(pos[i]);
                raf.read(b);
                buf.put(0, offset, b);
                offset += len;
            }
            return buf;
        }
    }

    private PlanarImage getUncacheImage(MediaElement media, int frame) throws IOException {
        FileCache cache = media.getFileCache();
        Optional<File> orinigal = cache.getOriginalFile();
        if (orinigal.isPresent()) {
            readMetaData();
            String syntax = tsuid;
            boolean rawData = !compressedData || isRLELossless();
            ExtendSegmentedInputImageStream extParams = buildSegmentedImageInputStream(frame);

            if (extParams.getSegmentPositions() != null) {

                // FileInputStream in = new FileInputStream(extParams.getFile());
                // File outFile =
                // new File(AppProperties.FILE_CACHE_DIR, fileCache.getFinalFile().getName() + "-" + frame + ".jp2");
                // FileOutputStream out = new FileOutputStream(outFile);
                // StreamUtils.skipFully(in, extParams.getSegmentPositions()[frame]);
                // StreamUtils.copy(in, out, (int) extParams.getSegmentLengths()[frame]);

                int dcmFlags =
                    dataType == DataBuffer.TYPE_SHORT ? Imgcodecs.DICOM_FLAG_SIGNED : Imgcodecs.DICOM_FLAG_UNSIGNED;

                // Force JPEG Baseline (1.2.840.10008.1.2.4.50) to YBR_FULL_422 color model when RGB (error made by some
                // constructors). RGB color model doesn't make sense for lossy jpeg.
                // http://dicom.nema.org/medical/dicom/current/output/chtml/part05/sect_8.2.html#sect_8.2.1
                if (pmi.name().startsWith("YBR") || ("RGB".equalsIgnoreCase(pmi.name()) //$NON-NLS-1$ //$NON-NLS-2$
                    && TransferSyntax.JPEG_LOSSY_8.getTransferSyntaxUID().equals(syntax))) {
                    dcmFlags |= Imgcodecs.DICOM_FLAG_YBR;
                }
                if (bigendian) {
                    dcmFlags |= Imgcodecs.DICOM_FLAG_BIGENDIAN;
                }
                if (dataType == DataBuffer.TYPE_FLOAT || dataType == DataBuffer.TYPE_DOUBLE) {
                    dcmFlags |= Imgcodecs.DICOM_FLAG_FLOAT;
                }
                if (TransferSyntax.RLE.getTransferSyntaxUID().equals(syntax)) {
                    dcmFlags |= Imgcodecs.DICOM_FLAG_RLE;
                }

                MatOfDouble positions =
                    new MatOfDouble(Arrays.stream(extParams.getSegmentPositions()).asDoubleStream().toArray());
                MatOfDouble lengths =
                    new MatOfDouble(Arrays.stream(extParams.getSegmentLengths()).asDoubleStream().toArray());

                if (rawData) {
                    int bits = bitsStored <= 8 && bitsAllocated > 8 ? 9 : bitsStored; // Fix #94
                    MatOfInt dicomparams = new MatOfInt(Imgcodecs.IMREAD_UNCHANGED, dcmFlags,
                        TagD.getTagValue(this, Tag.Columns, Integer.class),
                        TagD.getTagValue(this, Tag.Rows, Integer.class), 0,
                        TagD.getTagValue(this, Tag.SamplesPerPixel, Integer.class), bits,
                        banded ? Imgcodecs.ILV_NONE : Imgcodecs.ILV_SAMPLE);
                    return ImageCV.toImageCV(Imgcodecs.dicomRawFileRead(orinigal.get().getAbsolutePath(), positions,
                        lengths, dicomparams, pmi.name()));
                }
                return ImageCV.toImageCV(Imgcodecs.dicomJpgFileRead(orinigal.get().getAbsolutePath(), positions,
                    lengths, dcmFlags, Imgcodecs.IMREAD_UNCHANGED));

                // Mat buf = getMatBuffer(extParams);
                // if (rawData) {
                // MatOfInt dicomparams = new MatOfInt(Imgcodecs.IMREAD_UNCHANGED, dcmFlags,
                // TagD.getTagValue(this, Tag.Columns, Integer.class),
                // TagD.getTagValue(this, Tag.Rows, Integer.class),
                // TagD.getTagValue(this, Tag.SamplesPerPixel, Integer.class), bitsStored,
                // banded ? Imgcodecs.ILV_NONE : Imgcodecs.ILV_SAMPLE);
                //
                // return ImageCV.toImageCV(Imgcodecs.dicomRawRead(buf, dicomparams, pmi.name()));
                // }
                // return ImageCV.toImageCV(Imgcodecs.dicomJpgRead(buf, dcmFlags, Imgcodecs.IMREAD_UNCHANGED));
            }
        }
        return null;
    }

    private MediaElement getSingleImage() {
        return getSingleImage(0);
    }

    private MediaElement getSingleImage(int frame) {
        MediaElement[] elements = getMediaElement();
        if (elements != null && elements.length > frame) {
            return elements[frame];
        }
        return null;
    }

    @Override
    public MediaElement getPreview() {
        return getSingleImage();
    }

    @Override
    public boolean delegate(DataExplorerModel explorerModel) {
        return false;
    }

    @Override
    public synchronized MediaElement[] getMediaElement() {
        if (image == null && isReadableDicom()) {
            if (SERIES_VIDEO_MIMETYPE.equals(mimeType)) {
                image = new MediaElement[] { new DicomVideoElement(this, null) };
            } else if (SERIES_ENCAP_DOC_MIMETYPE.equals(mimeType)) {
                image = new MediaElement[] { new DicomEncapDocElement(this, null) };
            } else {
                if (numberOfFrame > 0) {
                    image = new MediaElement[numberOfFrame];
                    for (int i = 0; i < image.length; i++) {
                        image[i] = new DicomImageElement(this, i);
                    }
                    if (numberOfFrame > 1) {
                        // IF enhanced DICOM, instance number can be overridden later
                        // IF simple Multiframe instance number is necessary
                        for (int i = 0; i < image.length; i++) {
                            image[i].setTag(TagD.get(Tag.InstanceNumber), i + 1);
                        }
                    }
                } else {
                    String modality = TagD.getTagValue(this, Tag.Modality, String.class);
                    if (modality != null) {
                        DicomSpecialElementFactory factory = DCM_ELEMENT_FACTORIES.get(modality);
                        if (factory != null) {
                            image = new MediaElement[1];
                            image[0] = factory.buildDicomSpecialElement(this);
                        }
                    }
                    if (image == null) {
                        // Corrupted image => should have one frame
                        image = new MediaElement[0];
                    }
                }
            }
        }
        return image;
    }

    @Override
    public MediaSeries<MediaElement> getMediaSeries() {
        Series<MediaElement> series = null;
        if (isReadableDicom()) {
            String seriesUID = TagD.getTagValue(this, Tag.SeriesInstanceUID, String.class);
            series = buildSeries(seriesUID);
            writeMetaData(series);
            // no need to apply splitting rules
            // also no model
            MediaElement[] elements = getMediaElement();
            if (elements != null) {
                for (MediaElement media : elements) {
                    series.addMedia(media);
                }
            }
        }
        return series;
    }

    @Override
    public int getMediaElementNumber() {
        return numberOfFrame;
    }

    @Override
    public String getMediaFragmentMimeType() {
        return mimeType;
    }

    @Override
    public Map<TagW, Object> getMediaFragmentTags(Object key) {
        if (key instanceof Integer) {
            if ((Integer) key > 0) {
                // Clone the shared tag
                Map<TagW, Object> tagList = new HashMap<>(tags);
                SimpleTagable tagable = new SimpleTagable(tagList);
                if (DicomMediaUtils.writePerFrameFunctionalGroupsSequence(tagable, getDicomObject(), (Integer) key)) {
                    DicomMediaUtils.computeSlicePositionVector(tagable);
                }
                return tagList;
            }
        }
        return tags;
    }

    @Override
    public void close() {
        dispose();
    }

    @Override
    public Codec getCodec() {
        return BundleTools.getCodec(DicomMediaIO.DICOM_MIMETYPE, DicomCodec.NAME);
    }

    @Override
    public String[] getReaderDescription() {
        return new String[] { "DICOM Codec: " + DicomCodec.NAME, //$NON-NLS-1$
            "Version: " + Implementation.getVersionName(), //$NON-NLS-1$
            "Image decompression: OpenCV imgcodecs", //$NON-NLS-1$
            "Version: " + Core.VERSION }; //$NON-NLS-1$
    }

    public Series<MediaElement> buildSeries(String seriesUID) {
        Series<? extends MediaElement> series;
        if (IMAGE_MIMETYPE.equals(mimeType)) {
            series = new DicomSeries(seriesUID);
        } else if (SERIES_VIDEO_MIMETYPE.equals(mimeType)) {
            series = new DicomVideoSeries(seriesUID);
        } else if (SERIES_ENCAP_DOC_MIMETYPE.equals(mimeType)) {
            series = new DicomEncapDocSeries(seriesUID);
        } else {
            series = new DicomSeries(seriesUID);
        }
        return (Series<MediaElement>) series;
    }

    @Override
    public int getHeight(int frameIndex) throws IOException {
        checkIndex(frameIndex);
        return TagD.getTagValue(this, Tag.Rows, Integer.class);
    }

    @Override
    public int getWidth(int frameIndex) throws IOException {
        checkIndex(frameIndex);
        return TagD.getTagValue(this, Tag.Columns, Integer.class);
    }

    @Override
    public ImageTypeSpecifier getRawImageType(int frameIndex) throws IOException {
        readMetaData();
        checkIndex(frameIndex);
        return createImageType(bitsStored, dataType, false);
    }

    @Override
    public Iterator<ImageTypeSpecifier> getImageTypes(int frameIndex) throws IOException {
        readMetaData();
        checkIndex(frameIndex);

        ImageTypeSpecifier imageType = createImageType(bitsStored, dataType, false);
        return Collections.singletonList(imageType).iterator();
    }

    private boolean isRLELossless() {
        return dis == null ? false : dis.getTransferSyntax().equals(UID.RLELossless);
    }

    private ExtendSegmentedInputImageStream buildSegmentedImageInputStream(int frameIndex) throws IOException {
        long[] offsets;
        int[] length;

        if (pixeldataFragments == null) {
            readMetaData();
            int width = TagD.getTagValue(this, Tag.Columns, Integer.class);
            int height = TagD.getTagValue(this, Tag.Rows, Integer.class);
            int samples = TagD.getTagValue(this, Tag.SamplesPerPixel, Integer.class);
            int frameLength = pmi.frameLength(width, height, samples, bitsAllocated);

            offsets = new long[1];
            length = new int[offsets.length];
            offsets[0] = pixeldata.offset() + frameIndex * frameLength;
            length[0] = frameLength;
        } else {
            int nbFragments = pixeldataFragments.size();

            if (numberOfFrame >= nbFragments - 1) {
                // nbFrames > nbFragments should never happen
                offsets = new long[1];
                length = new int[offsets.length];
                int index = frameIndex < nbFragments - 1 ? frameIndex + 1 : nbFragments - 1;
                BulkData bulkData = (BulkData) pixeldataFragments.get(index);
                offsets[0] = bulkData.offset();
                length[0] = bulkData.length();
            } else {
                if (numberOfFrame == 1) {
                    offsets = new long[nbFragments - 1];
                    length = new int[offsets.length];
                    for (int i = 0; i < length.length; i++) {
                        BulkData bulkData = (BulkData) pixeldataFragments.get(i + frameIndex + 1);
                        offsets[i] = bulkData.offset();
                        length[i] = bulkData.length();
                    }
                } else {
                    // Multi-frames where each frames can have multiple fragments.
                    if (fragmentsPositions.isEmpty()) {
                        boolean jpeg2000 = tsuid.startsWith("1.2.840.10008.1.2.4.9"); //$NON-NLS-1$
                        try (ImageInputStream srcStream = ImageIO.createImageInputStream(new File(uri))) {
                            for (int i = 1; i < nbFragments; i++) {
                                BulkData bulkData = (BulkData) pixeldataFragments.get(i);
                                ImageInputStream stream = new org.dcm4che3.imageio.stream.SegmentedInputImageStream(
                                    srcStream, bulkData.offset(), bulkData.length(), false);
                                if (jpeg2000 ? decodeJpeg2000(stream) : decodeJpeg(stream)) {
                                    fragmentsPositions.add(i);
                                }
                            }
                        }
                    }

                    if (fragmentsPositions.size() == numberOfFrame) {
                        int start = fragmentsPositions.get(frameIndex);
                        int end = (frameIndex + 1) >= fragmentsPositions.size() ? nbFragments
                            : fragmentsPositions.get(frameIndex + 1);

                        offsets = new long[end - start];
                        length = new int[offsets.length];
                        for (int i = 0; i < offsets.length; i++) {
                            BulkData bulkData = (BulkData) pixeldataFragments.get(start + i);
                            offsets[i] = bulkData.offset();
                            length[i] = bulkData.length();
                        }
                    } else {
                        throw new IOException("Cannot match all the fragments to all the frames!"); //$NON-NLS-1$
                    }
                }
            }
        }
        return new ExtendSegmentedInputImageStream(fileCache.getOriginalFile().orElse(null), offsets, length);
    }

    @Override
    public boolean canReadRaster() {
        return true;
    }

    @Override
    public Raster readRaster(int frameIndex, ImageReadParam param) throws IOException {
        readingImage = true;
        try {
            PlanarImage img = getImageFragment(getSingleImage(frameIndex), frameIndex);
            return ImageConversion.toBufferedImage(img).getRaster();
        } catch (Exception e) {
            LOGGER.error("Reading image", e); //$NON-NLS-1$
            return null;
        } finally {
            readingImage = false;
        }
    }

    @Override
    public BufferedImage read(int frameIndex, ImageReadParam param) throws IOException {
        readingImage = true;
        try {
            PlanarImage img = getImageFragment(getSingleImage(frameIndex), frameIndex);
            return ImageConversion.toBufferedImage(img);
        } catch (Exception e) {
            LOGGER.error("Reading image", e); //$NON-NLS-1$
            return null;
        } finally {
            readingImage = false;
        }
    }

    @Override
    public RenderedImage readAsRenderedImage(int frameIndex, ImageReadParam param) throws IOException {
        readingImage = true;
        try {
            PlanarImage img = getImageFragment(getSingleImage(frameIndex), frameIndex);
            return ImageConversion.toBufferedImage(img);
        } catch (Exception e) {
            LOGGER.error("Reading image", e); //$NON-NLS-1$
            return null;
        } finally {
            readingImage = false;
        }
    }

    public boolean isSkipLargePrivate() {
        return skipLargePrivate;
    }

    public void setSkipLargePrivate(boolean skipLargePrivate) {
        this.skipLargePrivate = skipLargePrivate;
    }

    @Override
    public Attributes getDicomObject() {
        try {
            DicomMetaData md = readMetaData();
            return md.getAttributes();
        } catch (Exception e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.error("Cannot read DICOM:", e); //$NON-NLS-1$
            } else {
                LOGGER.error(e.getMessage());
            }
        }
        return null;
    }

    @Override
    public void setInput(Object input, boolean seekForwardOnly, boolean ignoreMetadata) {
        super.setInput(input, seekForwardOnly, ignoreMetadata);
        resetInternalState();
        if (input != null) {
            if (!(input instanceof ImageInputStream)) {
                throw new IllegalArgumentException("Input not an ImageInputStream!"); //$NON-NLS-1$
            }
            this.iis = (ImageInputStream) input;
        }
    }

    @Override
    public void dispose() {
        HEADER_CACHE.remove(this);
        readingHeader = false;
        readingImage = false;
        reset();
        super.dispose();
    }

    @Override
    public void reset() {
        /*
         * readingHeader: prevent error when reading images from a large multiframe and the header is removed from the
         * cache at the same time.
         *
         * readingImage: prevent closing stream when reading an image or for the RenderedImage which delays the image
         * reading).
         */
        if (!readingHeader && !readingImage) {
            super.reset();
            resetInternalState();
        }
    }

    private void resetInternalState() {
        FileUtil.safeClose(iis);
        iis = null;
        dis = null;
        tsuid = null;
    }

    private void checkIndex(int frameIndex) {
        if (frameIndex < 0 || frameIndex >= numberOfFrame) {
            throw new IndexOutOfBoundsException("imageIndex: " + frameIndex); //$NON-NLS-1$
        }
    }

    @Override
    public ImageReadParam getDefaultReadParam() {
        return new DicomImageReadParam();
    }

    /**
     * Return a DicomStreamMetaData object that includes the DICOM header. <b>WARNING:</b> If this class is used to read
     * directly from a cache or other location that contains uncorrected data, the DICOM header will have the
     * uncorrected data as well. That is, assume the DB has some fixes to patient demographics. These will not usually
     * be applied to the DICOM files directly, so you can get the wrong information from the header. This is not an
     * issue if you know the DICOM is up to date, or if you use the DB information as authoritative.
     */
    @Override
    public IIOMetadata getStreamMetadata() throws IOException {
        return readMetaData();
    }

    /**
     * Gets any image specific meta data. This should return the image specific blocks for enhanced multi-frame, but
     * currently it merely returns null.
     */
    @Override
    public IIOMetadata getImageMetadata(int imageIndex) throws IOException {
        return null;
    }

    /**
     * Returns the number of regular images in the study. This excludes overlays.
     */
    @Override
    public int getNumImages(boolean allowSearch) throws IOException {
        return numberOfFrame;
    }

    /**
     * Reads the DICOM header meta-data, up to, but not including pixel data.
     *
     * @throws Exception
     */
    private synchronized DicomMetaData readMetaData() throws IOException {
        DicomMetaData header = HEADER_CACHE.get(this);
        if (header != null) {
            return header;
        } else if (dcmMetadata != null) {
            return dcmMetadata;
        }

        try {
            readingHeader = true;
            if (iis == null) {
                Optional<File> file = fileCache.getOriginalFile();
                if (file.isPresent()) {
                    setInput(ImageIO.createImageInputStream(new File(uri)), false, false);
                }
            }

            if (iis == null) {
                throw new IllegalStateException("Input not set!"); //$NON-NLS-1$
            }

            iis.seek(0L);
            dis = new DicomInputStream(new ImageInputStreamAdapter(iis));
            dis.setIncludeBulkData(IncludeBulkData.URI);
            dis.setBulkDataDescriptor(DicomCodec.BULKDATA_DESCRIPTOR);
            // avoid a copy of pixeldata into temporary file
            dis.setURI(uri.toString());
            Attributes fmi = dis.readFileMetaInformation();
            Attributes ds = dis.readDataset(-1, -1);
            if (fmi == null) {
                fmi = ds.createFileMetaInformation(dis.getTransferSyntax());
            }
            DicomMetaData metadata = new DicomMetaData(fmi, ds);
            Object pixdata = ds.getValue(Tag.PixelData, pixeldataVR);
            if (pixdata == null) {
                pixdata = ds.getValue(Tag.FloatPixelData, pixeldataVR);
            }
            if (pixdata == null) {
                pixdata = ds.getValue(Tag.DoubleFloatPixelData, pixeldataVR);
            }

            if (pixdata != null) {
                tsuid = dis.getTransferSyntax();
                numberOfFrame = ds.getInt(Tag.NumberOfFrames, 1);
                hasPixel = ds.getInt(Tag.BitsStored, ds.getInt(Tag.BitsAllocated, 0)) > 0;

                if (!tsuid.startsWith("1.2.840.10008.1.2.4.10") && hasPixel) { //$NON-NLS-1$

                    if (pixdata instanceof BulkData) {
                        bigendian = ds.bigEndian();
                        iis.setByteOrder(ds.bigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
                        this.pixeldata = (BulkData) pixdata;
                        // Handle JPIP
                    } else if (ds.getString(Tag.PixelDataProviderURL) != null) {
                        // always little endian:
                        // http://dicom.nema.org/medical/dicom/2017b/output/chtml/part05/sect_A.6.html
                        if (numberOfFrame == 0) {
                            numberOfFrame = 1;
                            // compressed = true;
                        }
                    } else if (pixdata instanceof Fragments) {
                        // ImageReaderFactory.ImageReaderItem readerItem = ImageReaderFactory.getImageReader(tsuid);
                        // if (readerItem == null) {
                        // throw new IOException("Unsupported Transfer Syntax: " + tsuid); //$NON-NLS-1$
                        // }
                        this.compressedData = true;
                        this.pixeldataFragments = (Fragments) pixdata;
                        bigendian = pixeldataFragments.bigEndian();
                        if (bigendian) {
                            LOGGER.error("Big endian fragments?"); //$NON-NLS-1$
                        }
                    }
                }
            }

            HEADER_CACHE.put(this, metadata);
            return metadata;
        } finally {
            readingHeader = false;
            FileUtil.safeClose(iis);
            iis = null;

        }
    }

    private SampleModel createSampleModel(int dataType, boolean banded) {
        return pmi.createSampleModel(dataType, TagD.getTagValue(this, Tag.Columns, Integer.class),
            TagD.getTagValue(this, Tag.Rows, Integer.class), TagD.getTagValue(this, Tag.SamplesPerPixel, Integer.class),
            banded);
    }

    private ImageTypeSpecifier createImageType(int bits, int dataType, boolean banded) {
        return new ImageTypeSpecifier(createColorModel(bits, dataType), createSampleModel(dataType, banded));
    }

    private ColorModel createColorModel(int bits, int dataType) {
        return pmi.createColorModel(bits, dataType, getDicomObject());
    }

    private boolean decodeJpeg2000(ImageInputStream iis) throws IOException {
        iis.mark();
        try {
            int marker = (iis.read() << 8) | iis.read();

            if (marker == 0xFF4F) {
                return true;
            }

            iis.reset();
            iis.mark();
            byte[] b = new byte[12];
            iis.readFully(b);

            // Verify the signature box
            // The length of the signature box is 12
            if (b[0] != 0 || b[1] != 0 || b[2] != 0 || b[3] != 12) {
                return false;
            }

            // The signature box type is "jP "
            if ((b[4] & 0xff) != 0x6A || (b[5] & 0xFF) != 0x50 || (b[6] & 0xFF) != 0x20 || (b[7] & 0xFF) != 0x20) {
                return false;
            }

            // The signature content is 0x0D0A870A
            if ((b[8] & 0xFF) != 0x0D || (b[9] & 0xFF) != 0x0A || (b[10] & 0xFF) != 0x87 || (b[11] & 0xFF) != 0x0A) {
                return false;
            }

            return true;
        } finally {
            iis.reset();
        }
    }

    private boolean decodeJpeg(ImageInputStream iis) throws IOException {
        // jpeg and jpeg-ls
        iis.mark();
        try {
            int byte1 = iis.read();
            int byte2 = iis.read();
            // Magic numbers for JPEG (general jpeg marker)
            if ((byte1 != 0xFF) || (byte2 != 0xD8)) {
                return false;
            }
            do {
                byte1 = iis.read();
                byte2 = iis.read();
                // Something wrong, but try to read it anyway
                if (byte1 != 0xFF) {
                    break;
                }
                // Start of scan
                if (byte2 == 0xDA) {
                    break;
                }
                // Start of Frame, also known as SOF55, indicates a JPEG-LS file.
                if (byte2 == 0xF7) {
                    return true;
                }
                // 0xffc0: // SOF_0: JPEG baseline
                // 0xffc1: // SOF_1: JPEG extended sequential DCT
                // 0xffc2: // SOF_2: JPEG progressive DCT
                // 0xffc3: // SOF_3: JPEG lossless sequential
                if ((byte2 >= 0xC0) && (byte2 <= 0xC3)) {
                    return true;
                }
                // 0xffc5: // SOF_5: differential (hierarchical) extended sequential, Huffman
                // 0xffc6: // SOF_6: differential (hierarchical) progressive, Huffman
                // 0xffc7: // SOF_7: differential (hierarchical) lossless, Huffman
                if ((byte2 >= 0xC5) && (byte2 <= 0xC7)) {
                    return true;
                }
                // 0xffc9: // SOF_9: extended sequential, arithmetic
                // 0xffca: // SOF_10: progressive, arithmetic
                // 0xffcb: // SOF_11: lossless, arithmetic
                if ((byte2 >= 0xC9) && (byte2 <= 0xCB)) {
                    return true;
                }
                // 0xffcd: // SOF_13: differential (hierarchical) extended sequential, arithmetic
                // 0xffce: // SOF_14: differential (hierarchical) progressive, arithmetic
                // 0xffcf: // SOF_15: differential (hierarchical) lossless, arithmetic
                if ((byte2 >= 0xCD) && (byte2 <= 0xCF)) {
                    return true;
                }
                int length = iis.read() << 8;
                length += iis.read();
                length -= 2;
                while (length > 0) {
                    length -= iis.skipBytes(length);
                }
            } while (true);
            return true;
        } finally {
            iis.reset();
        }
    }

}
