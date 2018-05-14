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
package org.weasis.dicom.codec;

import java.awt.Dimension;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferUShort;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.awt.image.renderable.ParameterBlock;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
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
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import javax.media.jai.JAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.operator.AndConstDescriptor;
import javax.media.jai.operator.NullDescriptor;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.image.Overlays;
import org.dcm4che3.image.PhotometricInterpretation;
import org.dcm4che3.imageio.codec.ImageReaderFactory;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReadParam;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReaderSpi;
import org.dcm4che3.imageio.plugins.dcm.DicomMetaData;
import org.dcm4che3.imageio.stream.ImageInputStreamAdapter;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.io.DicomOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.image.op.RectifySignedShortDataDescriptor;
import org.weasis.core.api.image.op.RectifyUShortToShortDataDescriptor;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.image.util.LayoutUtil;
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
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.StringUtil;
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

import com.sun.media.imageio.stream.RawImageInputStream;
import com.sun.media.imageioimpl.common.ExtendImageParam;
import com.sun.media.jai.util.ImageUtil;

public class DicomMediaIO extends ImageReader implements DcmMediaReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(DicomMediaIO.class);

    public static final File DICOM_EXPORT_DIR = AppProperties.buildAccessibleTempDirectory("dicom"); //$NON-NLS-1$

    public static final String MIMETYPE = "application/dicom"; //$NON-NLS-1$
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
    private ImageReader decompressor;
    private int frameLength;
    private PhotometricInterpretation pmi;

    private URI uri;
    private int numberOfFrame;
    private final Map<TagW, Object> tags;
    private volatile MediaElement[] image = null;
    private volatile String mimeType;
    private final ArrayList<Integer> fragmentsPositions = new ArrayList<>();

    private volatile ImageInputStream iis;
    private DicomInputStream dis;
    private int dataType = 0;
    private boolean hasPixel = false;
    private boolean banded = false;

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
        this.mimeType = MIMETYPE;
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

    public boolean isReadableDicom() {
        if (UNREADABLE.equals(mimeType)) {
            // Return true only to display the error message in the view
            return true;
        }
        if ("data".equals(uri.getScheme()) && dcmMetadata == null) { //$NON-NLS-1$
            return false;
        }

        if (tags.size() == 0) {
            try {
                DicomMetaData md = readMetaData(false);
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

    private ImageReader initRawImageReader() {
        long[] frameOffsets = new long[numberOfFrame];
        frameOffsets[0] = pixeldata.offset();
        for (int i = 1; i < frameOffsets.length; i++) {
            frameOffsets[i] = frameOffsets[i - 1] + frameLength;
        }
        Dimension[] imageDimensions = new Dimension[numberOfFrame];
        int width = TagD.getTagValue(this, Tag.Columns, Integer.class);
        int height = TagD.getTagValue(this, Tag.Rows, Integer.class);
        Arrays.fill(imageDimensions, new Dimension(width, height));

        ColorModel cmodel = createColorModel(bitsStored, dataType);

        SampleModel smodel;
        if (pmi.isSubSambled()) {
            // Cannot handle tiles with subsampled model
            smodel = createSampleModel(dataType, banded);
        } else {
            if (width >= 1024 || height >= 1024) {
                width = Math.min(width, ImageFiler.TILESIZE);
                height = Math.min(height, ImageFiler.TILESIZE);
            }
            smodel = pmi.createSampleModel(dataType, width, height,
                TagD.getTagValue(this, Tag.SamplesPerPixel, Integer.class), banded);
        }

        Iterator<ImageReader> iter = ImageIO.getImageReadersByFormatName("RAW");//$NON-NLS-1$
        ImageReader reader = iter.hasNext() ? iter.next() : null;
        if (reader == null) {
            throw new IllegalStateException("Cannot get RAW image reader"); //$NON-NLS-1$
        }
        RawImageInputStream riis =
            new RawImageInputStream(iis, new ImageTypeSpecifier(cmodel, smodel), frameOffsets, imageDimensions);
        // endianness is already in iis?
        // riis.setByteOrder(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        reader.setInput(riis);
        return reader;
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
        if (media != null && media.getKey() instanceof Integer && isReadableDicom()) {
            int frame = (Integer) media.getKey();
            if (frame >= 0 && frame < numberOfFrame && hasPixel) {
                // read as tiled rendered image
                LOGGER.debug("Start reading dicom image frame: {} sopUID: {}", //$NON-NLS-1$
                    frame, TagD.getTagValue(this, Tag.SOPInstanceUID));
                return getValidImage(readAsRenderedImage(frame, null), media);
            }
        }
        return null;
    }

    private PlanarImage getValidImage(RenderedImage buffer, MediaElement media) {
        PlanarImage img = null;
        if (buffer != null) {
            // Bug fix: CLibImageReader and J2KImageReaderCodecLib (imageio libs) do not handle negative values
            // for short data. They convert signed short to unsigned short.
            if (dataType == DataBuffer.TYPE_SHORT && buffer.getSampleModel().getDataType() == DataBuffer.TYPE_USHORT) {
                img = RectifyUShortToShortDataDescriptor.create(buffer, LayoutUtil.createTiledLayoutHints(buffer));
            } else if (ImageUtil.isBinary(buffer.getSampleModel())) {
                ParameterBlock pb = new ParameterBlock();
                pb.addSource(buffer);
                // Tile size are set in this operation
                img = JAI.create("formatbinary", pb, null); //$NON-NLS-1$
            } else if (buffer.getTileWidth() != ImageFiler.TILESIZE || buffer.getTileHeight() != ImageFiler.TILESIZE) {
                img = ImageFiler.tileImage(buffer);
            } else {
                img = NullDescriptor.create(buffer, LayoutUtil.createTiledLayoutHints(buffer));
            }

            /*
             * Handle overlay in pixel data: extract the overlay, serialize it in a file and set all values to O in the
             * pixel data.
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
                            Raster raster = buffer.getData();
                            for (int i = 0; i < embeddedOverlayGroupOffsets.length; i++) {
                                overlayData[i] =
                                    OverlayUtils.extractOverlay(embeddedOverlayGroupOffsets[i], raster, ds);
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
                img = AndConstDescriptor.create(img, new int[] { overlayBitMask }, null);
            }
            img = DicomImageUtils.getRGBImageFromPaletteColorModel(img, getDicomObject());
        }
        return img;
    }

    private MediaElement getSingleImage() {
        MediaElement[] elements = getMediaElement();
        if (elements != null && elements.length > 0) {
            return elements[0];
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
    public MediaElement[] getMediaElement() {
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
        return BundleTools.getCodec(DicomMediaIO.MIMETYPE, DicomCodec.NAME);
    }

    @Override
    public String[] getReaderDescription() {
        String[] desc = new String[3];
        desc[0] = "DICOM Codec: " + DicomCodec.NAME; //$NON-NLS-1$
        if (decompressor != null) {
            desc[1] = "Image Reader Class: " + decompressor.getClass().getName(); //$NON-NLS-1$
            try {
                desc[2] = "Image Format: " + decompressor.getFormatName(); //$NON-NLS-1$
            } catch (IOException e) {
                desc[2] = "Image Format: unknown"; //$NON-NLS-1$
            }
        }
        if (desc[1] == null) {
            String ts = tsuid;
            if (ts == null) {
                ts = "unknown"; //$NON-NLS-1$
            }
            desc[1] = Messages.getString("DicomMediaIO.msg_no_reader") + StringUtil.COLON_AND_SPACE + ts; //$NON-NLS-1$
        }
        return desc;
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
        readMetaData(false);
        checkIndex(frameIndex);

        if (decompressor == null) {
            createImageType(bitsStored, dataType, banded);
        }

        if (isRLELossless()) {
            createImageType(bitsStored, dataType, true);
        }

        decompressor.setInput(iisOfFrame(0));
        return decompressor.getRawImageType(0);
    }

    @Override
    public Iterator<ImageTypeSpecifier> getImageTypes(int frameIndex) throws IOException {
        readMetaData(true);
        checkIndex(frameIndex);

        ImageTypeSpecifier imageType;
        if (pmi.isMonochrome()) {
            imageType = createImageType(8, DataBuffer.TYPE_BYTE, false);
        } else if (decompressor == null) {
            imageType = createImageType(bitsStored, dataType, banded);
        } else if (isRLELossless()) {
            imageType = createImageType(bitsStored, dataType, true);
        } else {
            decompressor.setInput(iisOfFrame(0));
            return decompressor.getImageTypes(0);
        }

        return Collections.singletonList(imageType).iterator();
    }

    private boolean isRLELossless() {
        return dis == null ? false : dis.getTransferSyntax().equals(UID.RLELossless);
    }

    private ExtendSegmentedInputImageStream iisOfFrame(int frameIndex) throws IOException {
        // // Extract compressed file
        // if (!fileCache.isElementInMemory()) {
        // String extension = "." + Optional.ofNullable(decompressor)
        // .map(d -> d.getOriginatingProvider().getFileSuffixes()[0]).orElse("raw");
        // FileUtil.writeFile(buildSegmentedImageInputStream(frameIndex), new File(AppProperties.FILE_CACHE_DIR,
        // fileCache.getFinalFile().getName() + "-" + frameIndex + extension));
        // }
        return buildSegmentedImageInputStream(frameIndex);
    }

    private ExtendSegmentedInputImageStream buildSegmentedImageInputStream(int frameIndex) throws IOException {
        int nbFragments = pixeldataFragments.size();
        long[] offsets;
        int[] length;
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
                    if (decompressor == null) {
                        throw new IOException("no decompressor!"); //$NON-NLS-1$
                    }

                    for (int i = 1; i < nbFragments; i++) {
                        BulkData bulkData = (BulkData) pixeldataFragments.get(i);
                        ImageReaderSpi provider = decompressor.getOriginatingProvider();
                        if (provider.canDecodeInput(new org.dcm4che3.imageio.stream.SegmentedInputImageStream(iis,
                            new long[] { bulkData.offset() }, new int[] { bulkData.length() }))) {
                            fragmentsPositions.add(i);
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

        return new ExtendSegmentedInputImageStream(iis, fileCache.getOriginalFile().orElse(null), offsets, length);
    }

    @Override
    public boolean canReadRaster() {
        return true;
    }

    @Override
    public Raster readRaster(int frameIndex, ImageReadParam param) throws IOException {
        readingImage = true;
        try {
            readMetaData(true);
            checkIndex(frameIndex);

            if (decompressor != null) {
                ExtendSegmentedInputImageStream siis = iisOfFrame(frameIndex);
                decompressor.setInput(siis);

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Start decompressing frame #" + (frameIndex + 1)); //$NON-NLS-1$
                }
                Raster wr = pmi.decompress() == pmi && decompressor.canReadRaster()
                    ? decompressor.readRaster(0, decompressParam(param, siis))
                    : decompressor.read(0, decompressParam(param, siis)).getRaster();
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Finished decompressing frame #" + (frameIndex + 1)); //$NON-NLS-1$
                }
                return wr;
            }
            iis.seek(pixeldata.offset() + frameIndex * frameLength);
            WritableRaster wr = Raster.createWritableRaster(createSampleModel(dataType, banded), null);
            DataBuffer buf = wr.getDataBuffer();
            if (buf instanceof DataBufferByte) {
                byte[][] data = ((DataBufferByte) buf).getBankData();
                for (byte[] bs : data) {
                    iis.readFully(bs);
                }
            } else {
                short[] data = ((DataBufferUShort) buf).getData();
                iis.readFully(data, 0, data.length);
            }
            return wr;
        } finally {
            readingImage = false;
        }
    }

    private ImageReadParam decompressParam(ImageReadParam param, ExtendSegmentedInputImageStream siis) {
        ImageReadParam decompressParam = decompressor.getDefaultReadParam();
        ImageTypeSpecifier imageType = param.getDestinationType();
        BufferedImage dest = param.getDestination();
        if (isRLELossless() && imageType == null && dest == null) {
            imageType = createImageType(bitsStored, dataType, true);
        }
        decompressParam.setDestinationType(imageType);
        decompressParam.setDestination(dest);
        if (decompressParam instanceof ExtendImageParam) {
            ExtendImageParam p = (ExtendImageParam) decompressParam;
            p.setSignedData(dataType == DataBuffer.TYPE_SHORT);
            p.setYbrColorModel(pmi.name());
            if (siis != null) {
                p.setSegmentPositions(siis.getSegmentPositions());
                p.setSegmentLengths(siis.getSegmentLengths());
                p.setFile(siis.getFile());
            }
        }
        return decompressParam;
    }

    @Override
    public BufferedImage read(int frameIndex, ImageReadParam param) throws IOException {
        readingImage = true;
        try {
            checkIndex(frameIndex);
            if (param == null) {
                param = getDefaultReadParam();
            }

            WritableRaster raster;
            if (decompressor != null) {
                ExtendSegmentedInputImageStream siis = iisOfFrame(frameIndex);
                decompressor.setInput(siis);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Start decompressing frame #" + (frameIndex + 1)); //$NON-NLS-1$
                }
                BufferedImage bi = decompressor.read(0, decompressParam(param, siis));
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Finished decompressing frame #" + (frameIndex + 1)); //$NON-NLS-1$
                }
                return bi;
            } else {
                raster = (WritableRaster) readRaster(frameIndex, param);
            }

            ColorModel cm = createColorModel(bitsStored, dataType);
            return new BufferedImage(cm, raster, false, null);
        } finally {
            readingImage = false;
        }
    }

    @Override
    public RenderedImage readAsRenderedImage(int frameIndex, ImageReadParam param) throws IOException {
        readingImage = true;
        try {
            readMetaData(true);
            checkIndex(frameIndex);
            if (param == null) {
                param = getDefaultReadParam();
            }

            RenderedImage bi;
            if (decompressor != null) {
                ExtendSegmentedInputImageStream siis = iisOfFrame(frameIndex);
                decompressor.setInput(siis);
                if (isRLELossless() && (pmi.isSubSambled() || pmi.name().startsWith("YBR"))) { //$NON-NLS-1$
                    bi = convertSubSambledAndYBR(frameIndex, param);
                } else {
                    bi = decompressor.readAsRenderedImage(0, decompressParam(param, siis));
                }
            } else {
                // Rewrite image with subsampled model (otherwise cannot not be displayed as RenderedImage)
                // Convert YBR_FULL into RBG as the ybr model is not well supported.
                if (pmi.isSubSambled() || pmi.name().startsWith("YBR")) { //$NON-NLS-1$
                    bi = convertSubSambledAndYBR(frameIndex, param);
                } else {
                    ImageReader reader = initRawImageReader();
                    bi = reader.readAsRenderedImage(frameIndex, param);
                }
            }
            return validateSignedShortDataBuffer(bi);
        } finally {
            /*
             * "readingImage = false" will close the stream of the tiled image. The problem is that
             * readAsRenderedImage() do not read data immediately: RenderedImage delays the image reading
             */
        }
    }

    private BufferedImage convertSubSambledAndYBR(int frameIndex, ImageReadParam param) throws IOException {
        // TODO improve this
        WritableRaster raster = (WritableRaster) readRaster(frameIndex, param);
        ColorModel cm = createColorModel(bitsStored, dataType);
        ColorModel cmodel =
            new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), new int[] { 8, 8, 8 }, false, // has
                                                                                                              // alpha
                false, // alpha premultipled
                Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
        int width = raster.getWidth();
        int height = raster.getHeight();
        SampleModel sampleModel = cmodel.createCompatibleSampleModel(width, height);
        DataBuffer dataBuffer = sampleModel.createDataBuffer();
        WritableRaster rasterDst = Raster.createWritableRaster(sampleModel, dataBuffer, null);

        ColorSpace cs = cm.getColorSpace();
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                byte[] ba = (byte[]) raster.getDataElements(j, i, null);
                float[] fba = new float[] { (ba[0] & 0xFF) / 255f, (ba[1] & 0xFF) / 255f, (ba[2] & 0xFF) / 255f };
                float[] rgb = cs.toRGB(fba);
                ba[0] = (byte) (rgb[0] * 255);
                ba[1] = (byte) (rgb[1] * 255);
                ba[2] = (byte) (rgb[2] * 255);
                rasterDst.setDataElements(j, i, ba);
            }
        }
        BufferedImage bi = new BufferedImage(cmodel, rasterDst, false, null);
        readingImage = true;

        return bi;
    }

    public RenderedImage validateSignedShortDataBuffer(RenderedImage source) {
        /*
         * Issue in ComponentColorModel when signed short DataBuffer, only 16 bits is supported see
         * http://java.sun.com/javase/6/docs/api/java/awt/image/ ComponentColorModel.html Instances of
         * ComponentColorModel created with transfer types DataBuffer.TYPE_SHORT, DataBuffer.TYPE_FLOAT, and
         * DataBuffer.TYPE_DOUBLE use all the bits of all sample values. Thus all color/alpha components have 16 bits
         * when using DataBuffer.TYPE_SHORT, 32 bits when using DataBuffer.TYPE_FLOAT, and 64 bits when using
         * DataBuffer.TYPE_DOUBLE. When the ComponentColorModel(ColorSpace, int[], boolean, boolean, int, int) form of
         * constructor is used with one of these transfer types, the bits array argument is ignored.
         */

        // TODO test with all decoders (works with raw decoder)
        if (source != null && dataType == DataBuffer.TYPE_SHORT
            && source.getSampleModel().getDataType() == DataBuffer.TYPE_SHORT && (highBit + 1) < bitsAllocated) {
            return RectifySignedShortDataDescriptor.create(source, new int[] { highBit + 1 }, null);
        }
        return source;
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
            DicomMetaData md = readMetaData(false);
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

        pixeldata = null;
        pixeldataFragments = null;
        if (decompressor != null) {
            decompressor.dispose();
            decompressor = null;
        }
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
        return readMetaData(false);
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
    private synchronized DicomMetaData readMetaData(boolean readImageAfter) throws IOException {
        DicomMetaData header = HEADER_CACHE.get(this);
        if (header != null) {
            if (!readImageAfter) {
                return header;
            }
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

            /*
             * When readImageAfter is true, do not read again the header if it is in cache and the variables has been
             * initialized
             */
            if (header != null && tsuid != null) {
                return header;
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

                if (readImageAfter && !tsuid.startsWith("1.2.840.10008.1.2.4.10") && hasPixel) { //$NON-NLS-1$

                    if (pixdata instanceof BulkData) {
                        int width = TagD.getTagValue(this, Tag.Columns, Integer.class);
                        int height = TagD.getTagValue(this, Tag.Rows, Integer.class);
                        int samples = TagD.getTagValue(this, Tag.SamplesPerPixel, Integer.class);
                        iis.setByteOrder(ds.bigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
                        this.frameLength = pmi.frameLength(width, height, samples, bitsAllocated);
                        this.pixeldata = (BulkData) pixdata;
                        // Handle JPIP
                    } else if (ds.getString(Tag.PixelDataProviderURL) != null) {
                        if (numberOfFrame == 0) {
                            numberOfFrame = 1;
                            // compressed = true;
                        }
                    } else if (pixdata instanceof Fragments) {
                        ImageReaderFactory.ImageReaderItem readerItem = ImageReaderFactory.getImageReader(tsuid);
                        if (readerItem == null) {
                            throw new IOException("Unsupported Transfer Syntax: " + tsuid); //$NON-NLS-1$
                        }
                        this.decompressor = readerItem.getImageReader();
                        this.pixeldataFragments = (Fragments) pixdata;
                    }
                }
            }

            HEADER_CACHE.put(this, metadata);
            return metadata;
        } finally {
            readingHeader = false;
            if (!readImageAfter) {
                // Reset must be called only after reading the header, because closing imageStream does not let through
                // getTile(x,y) read image data.
                // unlock file to be deleted on exit
                // System.err.println("Close stream: reading header");
                FileUtil.safeClose(iis);
                iis = null;
            }
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

}
