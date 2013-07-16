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
package org.weasis.dicom.codec;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferUShort;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.Raster;
import java.awt.image.RasterFormatException;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.awt.image.renderable.ParameterBlock;
import java.io.File;
import java.io.IOException;
import java.lang.ref.Reference;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.media.jai.JAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.NullDescriptor;

import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.UID;
import org.dcm4che2.data.VR;
import org.dcm4che2.image.OverlayUtils;
import org.dcm4che2.image.PartialComponentSampleModel;
import org.dcm4che2.imageio.ImageReaderFactory;
import org.dcm4che2.imageio.ItemParser;
import org.dcm4che2.imageio.plugins.dcm.DicomImageReadParam;
import org.dcm4che2.imageio.plugins.dcm.DicomStreamMetaData;
import org.dcm4che2.imageioimpl.plugins.dcm.DicomImageReader;
import org.dcm4che2.imageioimpl.plugins.dcm.SizeSkipInputHandler;
import org.dcm4che2.io.DicomInputHandler;
import org.dcm4che2.io.DicomInputStream;
import org.dcm4che2.io.StopTagInputHandler;
import org.dcm4che2.iod.module.pr.DisplayShutterModule;
import org.dcm4che2.util.ByteUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.image.op.RectifySignedShortDataDescriptor;
import org.weasis.core.api.image.op.RectifyUShortToShortDataDescriptor;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.image.util.LayoutUtil;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SoftHashMap;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.codec.utils.ColorModelFactory;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

import com.sun.media.imageio.stream.RawImageInputStream;
import com.sun.media.imageio.stream.SegmentedImageInputStream;
import com.sun.media.jai.util.ImageUtil;

public class DicomMediaIO extends ImageReader implements MediaReader<PlanarImage> {

    /**
     * Logger for this class
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomMediaIO.class);

    public static final String MIMETYPE = "application/dicom"; //$NON-NLS-1$
    public static final String IMAGE_MIMETYPE = "image/dicom"; //$NON-NLS-1$
    public static final String SERIES_VIDEO_MIMETYPE = "video/dicom"; //$NON-NLS-1$
    public static final String SERIES_MIMETYPE = "series/dicom"; //$NON-NLS-1$
    public static final String SERIES_PR_MIMETYPE = "pr/dicom"; //$NON-NLS-1$
    public static final String SERIES_KO_MIMETYPE = "ko/dicom"; //$NON-NLS-1$
    public static final String SERIES_SR_MIMETYPE = "sr/dicom"; //$NON-NLS-1$
    public static final String SERIES_ENCAP_DOC_MIMETYPE = "encap/dicom"; //$NON-NLS-1$
    public static final String UNREADABLE = "unreadable/dicom"; //$NON-NLS-1$
    public static final String SERIES_XDSI = "xds-i/dicom"; //$NON-NLS-1$
    public static final String NO_VALUE = org.weasis.dicom.codec.Messages.getString("DicomMediaIO.unknown");//$NON-NLS-1$

    private static final int[] OFFSETS_0 = { 0 };
    private static final int[] OFFSETS_0_0_0 = { 0, 0, 0 };
    private static final int[] OFFSETS_0_1_2 = { 0, 1, 2 };
    public static final File DICOM_CACHE_DIR = new File(AbstractProperties.APP_TEMP_DIR, "cache"); //$NON-NLS-1$
    static {
        try {
            DICOM_CACHE_DIR.mkdirs();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static final SoftHashMap<DicomMediaIO, DicomObject> HEADER_CACHE =
        new SoftHashMap<DicomMediaIO, DicomObject>() {

            public Reference<? extends DicomObject> getReference(DicomMediaIO key) {
                return hash.get(key);
            }

            @Override
            public void removeElement(Reference<? extends DicomObject> soft) {
                DicomMediaIO key = reverseLookup.remove(soft);
                if (key != null) {
                    hash.remove(key);
                    key.reset();
                }
            }
        };

    private URI uri;
    private int numberOfFrame;
    private final HashMap<TagW, Object> tags;
    private volatile MediaElement[] image = null;
    private volatile String mimeType;

    private volatile ImageInputStream iis;
    private DicomInputStream dis;
    private boolean compressed;
    private ImageReader reader;
    private ItemParser itemParser;
    private SegmentedImageInputStream siis;
    private int dataType = 0;
    private boolean bigEndian;
    private boolean swapByteOrder = false;
    private boolean hasPixel = false;
    private boolean clampPixelValues = false;
    private boolean banded = false;
    private long pixelDataPos = 0;
    private int pixelDataLen = 0;
    private ImageReader jpipReader;
    /**
     * Store the transfer syntax locally in case it gets modified to re-write the image
     */
    private String tsuid;
    /** Used to indicate whether or not to skip large private dicom elements. */
    private boolean skipLargePrivate = true;
    private volatile boolean readingHeader = false;
    private volatile boolean readingImage = false;

    public DicomMediaIO(URI uri) {
        super(DicomCodec.DicomImageReaderSpi);
        this.uri = uri;
        numberOfFrame = 0;
        this.tags = new HashMap<TagW, Object>();
        mimeType = MIMETYPE;
    }

    public DicomMediaIO(File source) {
        this(source.toURI());
    }

    public DicomMediaIO(URL url) throws URISyntaxException {
        this(url.toURI());
    }

    @Override
    public synchronized void replaceURI(URI uri) {
        if (uri != null && !uri.equals(this.uri)) {
            this.uri = uri;
            reset();
        }

    }

    public boolean isReadableDicom() {
        if (UNREADABLE.equals(mimeType)) {
            return true;
        }
        if (uri == null) {
            return false;
        }

        if (tags.size() == 0) {
            try {
                DicomObject header = getDicomObject();
                // Exclude DICOMDIR
                if (header == null || header.getString(Tag.MediaStorageSOPClassUID, "").equals("1.2.840.10008.1.3.10")) { //$NON-NLS-1$ //$NON-NLS-2$
                    mimeType = UNREADABLE;
                    close();
                    return false;
                }

                hasPixel = header.getInt(Tag.BitsStored, header.getInt(Tag.BitsAllocated, 0)) > 0;
                if (hasPixel) {
                    if (header.getString(Tag.TransferSyntaxUID, "").startsWith("1.2.840.10008.1.2.4.10")) { //$NON-NLS-1$ //$NON-NLS-2$ $NON-NLS-2$
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

                writeInstanceTags(header);

            } catch (Throwable t) {
                mimeType = UNREADABLE;
                LOGGER.warn("", t); //$NON-NLS-1$
                close();
                return false;
            }
        }
        return true;
    }

    /**
     * Sets the input for the image reader.
     * 
     * @param imageIndex
     *            The Dicom frame index, or overlay number
     * @throws IOException
     */

    protected void initImageReader(int imageIndex) throws IOException {
        readMetaData(true);
        if (reader == null) {
            if (compressed) {
                initCompressedImageReader(imageIndex);
            } else {
                initRawImageReader();
            }
        }
        // Reset the input stream location if required, and reset the reader if required
        if (compressed && itemParser != null) {
            itemParser.seekFrame(siis, imageIndex);
            reader.setInput(siis, false);
        }
        // TODO 1.2.840.10008.1.2.4.95 (DICOM JPIP Referenced Deflate Transfer Syntax)
        if ("1.2.840.10008.1.2.4.94".equals(tsuid)) { //$NON-NLS-1$
            MediaElement[] elements = getMediaElement();
            // TODO handle frame
            if (elements != null && elements.length > 0) {
                reader.setInput(elements[0]);
            }
        }
    }

    private void initCompressedImageReader(int imageIndex) throws IOException {
        ImageReaderFactory f = ImageReaderFactory.getInstance();
        this.reader = f.getReaderForTransferSyntax(tsuid);
        if (!"1.2.840.10008.1.2.4.94".equals(tsuid)) { //$NON-NLS-1$
            this.itemParser = new ItemParser(dis, iis, numberOfFrame, tsuid);
            this.siis = new SegmentedImageInputStream(iis, itemParser);
        }
    }

    private void initRawImageReader() {
        long[] frameOffsets = new long[numberOfFrame];
        int frameLen = calculateFrameLength();
        frameOffsets[0] = pixelDataPos;
        for (int i = 1; i < frameOffsets.length; i++) {
            frameOffsets[i] = frameOffsets[i - 1] + frameLen;
        }
        Dimension[] imageDimensions = new Dimension[numberOfFrame];
        int width = (Integer) getTagValue(TagW.Columns);
        int height = (Integer) getTagValue(TagW.Rows);
        Arrays.fill(imageDimensions, new Dimension(width, height));
        RawImageInputStream riis =
            new RawImageInputStream(iis, createImageTypeSpecifier(), frameOffsets, imageDimensions);
        riis.setByteOrder(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        reader = ImageIO.getImageReadersByFormatName("RAW").next(); //$NON-NLS-1$
        reader.setInput(riis);
    }

    private boolean setDicomSpecialType(DicomObject header) {
        String modality = header.getString(Tag.Modality);
        if (modality != null) {
            if ("PR".equals(modality)) {//$NON-NLS-1$
                mimeType = SERIES_PR_MIMETYPE;
                return true;
            } else if ("KO".equals(modality)) {//$NON-NLS-1$
                mimeType = SERIES_KO_MIMETYPE;
                return true;
            } else if ("SR".equals(modality)) {//$NON-NLS-1$
                mimeType = SERIES_SR_MIMETYPE;
                return true;
            } else {
                String encap = header.getString(Tag.MIMETypeOfEncapsulatedDocument);
                if (encap != null) {
                    mimeType = SERIES_ENCAP_DOC_MIMETYPE;
                    return true;
                }
            }
        }
        return false;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setTag(TagW tag, Object value) {
        if (tag != null) {
            tags.put(tag, value);
        }
    }

    public void setTagNoNull(TagW tag, Object value) {
        if (tag != null && value != null) {
            tags.put(tag, value);
        }
    }

    @Override
    public Object getTagValue(TagW tag) {
        return tags.get(tag);
    }

    private void writeTag(MediaSeriesGroup group, TagW tag) {
        group.setTag(tag, getTagValue(tag));
    }

    public void writeMetaData(MediaSeriesGroup group) {
        if (group == null) {
            return;
        }
        // Get the dicom header
        DicomObject header = getDicomObject();
        DicomMediaUtils.writeMetaData(group, header);

        // Series Group
        if (TagW.SubseriesInstanceUID.equals(group.getTagID())) {
            // Information for series ToolTips
            group.setTagNoNull(TagW.PatientName, getTagValue(TagW.PatientName));
            group.setTagNoNull(TagW.StudyDescription, header.getString(Tag.StudyDescription));

            if ("1.2.840.10008.1.2.4.94".equals(tsuid)) { //$NON-NLS-1$
                MediaElement[] elements = getMediaElement();
                if (elements != null) {
                    for (MediaElement m : elements) {
                        m.setTag(TagW.ExplorerModel, group.getTagValue(TagW.ExplorerModel));
                    }
                }
            }
        }
    }

    private void writeInstanceTags(DicomObject header) {
        if (tags.size() > 0 || header == null) {
            return;
        }
        // -------- Mandatory Tags --------
        // Tags for identifying group (Patient, Study, Series)

        String patientID = header.getString(Tag.PatientID, NO_VALUE);
        setTag(TagW.PatientID, patientID);
        String name = DicomMediaUtils.buildPatientName(header.getString(Tag.PatientName));
        setTag(TagW.PatientName, name);
        Date birthdate = DicomMediaUtils.getDateFromDicomElement(header, Tag.PatientBirthDate, null);
        setTagNoNull(TagW.PatientBirthDate, birthdate);
        // Global Identifier for the patient.
        setTag(TagW.PatientPseudoUID,
            DicomMediaUtils.buildPatientPseudoUID(patientID, header.getString(Tag.IssuerOfPatientID), name, birthdate));
        setTag(TagW.StudyInstanceUID, header.getString(Tag.StudyInstanceUID, NO_VALUE));
        setTag(TagW.SeriesInstanceUID, header.getString(Tag.SeriesInstanceUID, NO_VALUE));
        setTag(TagW.Modality, header.getString(Tag.Modality, NO_VALUE));
        setTag(TagW.InstanceNumber, header.getInt(Tag.InstanceNumber, TagW.AppID.incrementAndGet()));
        setTag(TagW.SOPInstanceUID, header.getString(Tag.SOPInstanceUID, getTagValue(TagW.InstanceNumber).toString()));
        // -------- End of Mandatory Tags --------

        writeOnlyinstance(header);
        writeSharedFunctionalGroupsSequence(header);
        DicomMediaUtils.writePerFrameFunctionalGroupsSequence(tags, header, 0);
        if (SERIES_PR_MIMETYPE.equals(mimeType)) {
            // Set the series list for applying the PR
            setTagNoNull(TagW.ReferencedSeriesSequence, header.get(Tag.ReferencedSeriesSequence));
            // Set the name of the PR
            setTagNoNull(TagW.SeriesDescription, header.getString(Tag.SeriesDescription));
        } else if (SERIES_KO_MIMETYPE.equals(mimeType)) {
            // Set the series list for applying the PR
            setTagNoNull(TagW.CurrentRequestedProcedureEvidenceSequence,
                header.get(Tag.CurrentRequestedProcedureEvidenceSequence));
            // Set the name of the PR
            setTagNoNull(TagW.SeriesDescription, header.getString(Tag.SeriesDescription));
        }
        DicomMediaUtils.validateDicomImageValues(tags);
        DicomMediaUtils.computeSlicePositionVector(tags);

        Area shape = DicomMediaUtils.buildShutterArea(header);
        if (shape != null) {
            setTagNoNull(TagW.ShutterFinalShape, shape);
            Integer psVal = DicomMediaUtils.getIntegerFromDicomElement(header, Tag.ShutterPresentationValue, null);
            setTagNoNull(TagW.ShutterPSValue, psVal);
            float[] rgb =
                DisplayShutterModule.convertToFloatLab(header.getInts(Tag.ShutterPresentationColorCIELabValue,
                    (int[]) null));
            Color color =
                rgb == null ? null : PresentationStateReader.getRGBColor(psVal == null ? 0 : psVal, rgb, (int[]) null);
            setTagNoNull(TagW.ShutterRGBColor, color);

        }
        DicomMediaUtils.computeSUVFactor(header, tags, 0);

    }

    private void writeSharedFunctionalGroupsSequence(DicomObject header) {

        if (header != null) {
            DicomElement seq = header.get(Tag.SharedFunctionalGroupsSequence);
            if (seq != null && seq.vr() == VR.SQ) {
                DicomObject dcm = null;
                try {
                    dcm = seq.getDicomObject(0);
                } catch (Exception e) {
                    LOGGER.warn("", e); //$NON-NLS-1$
                }
                if (dcm != null) {
                    DicomMediaUtils.writeFunctionalGroupsSequence(tags, dcm);
                }
            }
        }
    }

    private void writeOnlyinstance(DicomObject header) {
        if (header != null) {
            boolean signed = header.getInt(Tag.PixelRepresentation) != 0;
            // Instance tags
            setTagNoNull(TagW.ImageType, DicomMediaUtils.getStringFromDicomElement(header, Tag.ImageType, null));
            setTagNoNull(TagW.ImageComments, header.getString(Tag.ImageComments));
            setTagNoNull(TagW.ImageLaterality, header.getString(Tag.ImageLaterality, header.getString(Tag.Laterality)));
            setTagNoNull(TagW.ContrastBolusAgent, header.getString(Tag.ContrastBolusAgent));
            setTagNoNull(TagW.TransferSyntaxUID, header.getString(Tag.TransferSyntaxUID));
            setTagNoNull(TagW.SOPClassUID, header.getString(Tag.SOPClassUID));
            setTagNoNull(TagW.ScanningSequence, header.getString(Tag.ScanningSequence));
            setTagNoNull(TagW.SequenceVariant, header.getString(Tag.SequenceVariant));
            setTagNoNull(TagW.ScanOptions, header.getString(Tag.ScanOptions));
            setTagNoNull(TagW.RepetitionTime,
                DicomMediaUtils.getFloatFromDicomElement(header, Tag.RepetitionTime, null));
            setTagNoNull(TagW.EchoTime, DicomMediaUtils.getFloatFromDicomElement(header, Tag.EchoTime, null));
            setTagNoNull(TagW.InversionTime, DicomMediaUtils.getFloatFromDicomElement(header, Tag.InversionTime, null));
            setTagNoNull(TagW.EchoNumbers, DicomMediaUtils.getIntegerFromDicomElement(header, Tag.EchoNumbers, null));
            setTagNoNull(TagW.GantryDetectorTilt,
                DicomMediaUtils.getFloatFromDicomElement(header, Tag.GantryDetectorTilt, null));
            setTagNoNull(TagW.ConvolutionKernel, header.getString(Tag.ConvolutionKernel));
            setTagNoNull(TagW.FlipAngle, DicomMediaUtils.getFloatFromDicomElement(header, Tag.FlipAngle, null));
            setTagNoNull(TagW.PatientOrientation, header.getStrings(Tag.PatientOrientation, (String[]) null));
            setTagNoNull(TagW.SliceLocation, DicomMediaUtils.getFloatFromDicomElement(header, Tag.SliceLocation, null));
            setTagNoNull(TagW.SliceThickness,
                DicomMediaUtils.getDoubleFromDicomElement(header, Tag.SliceThickness, null));
            setTagNoNull(TagW.AcquisitionDate,
                DicomMediaUtils.getDateFromDicomElement(header, Tag.AcquisitionDate, null));
            setTagNoNull(TagW.AcquisitionTime,
                DicomMediaUtils.getDateFromDicomElement(header, Tag.AcquisitionTime, null));
            setTagNoNull(TagW.ContentTime, DicomMediaUtils.getDateFromDicomElement(header, Tag.ContentTime, null));

            setTagNoNull(TagW.ImagePositionPatient, header.getDoubles(Tag.ImagePositionPatient, (double[]) null));
            setTagNoNull(TagW.ImageOrientationPatient, header.getDoubles(Tag.ImageOrientationPatient, (double[]) null));
            setTagNoNull(
                TagW.ImageOrientationPlane,
                ImageOrientation
                    .makeImageOrientationLabelFromImageOrientationPatient((double[]) getTagValue(TagW.ImageOrientationPatient)));

            int bitsAllocated = DicomMediaUtils.getIntegerFromDicomElement(header, Tag.BitsAllocated, 8);
            bitsAllocated = (bitsAllocated <= 8) ? 8 : ((bitsAllocated <= 16) ? 16 : 32);
            int bitsStored = DicomMediaUtils.getIntegerFromDicomElement(header, Tag.BitsStored, bitsAllocated);
            setTagNoNull(TagW.BitsAllocated, bitsAllocated);
            setTagNoNull(TagW.BitsStored, bitsStored);
            setTagNoNull(TagW.PixelRepresentation,
                DicomMediaUtils.getIntegerFromDicomElement(header, Tag.PixelRepresentation, 0));

            setTagNoNull(TagW.ImagerPixelSpacing, header.getDoubles(Tag.ImagerPixelSpacing, (double[]) null));
            setTagNoNull(TagW.PixelSpacing, header.getDoubles(Tag.PixelSpacing, (double[]) null));
            setTagNoNull(TagW.PixelAspectRatio, header.getInts(Tag.PixelAspectRatio, (int[]) null));
            setTagNoNull(TagW.PixelSpacingCalibrationDescription,
                header.getString(Tag.PixelSpacingCalibrationDescription));

            setTagNoNull(TagW.ModalityLUTSequence, header.get(Tag.ModalityLUTSequence));
            setTagNoNull(TagW.RescaleSlope, DicomMediaUtils.getFloatFromDicomElement(header, Tag.RescaleSlope, null));
            setTagNoNull(TagW.RescaleIntercept,
                DicomMediaUtils.getFloatFromDicomElement(header, Tag.RescaleIntercept, null));
            setTagNoNull(TagW.RescaleType, DicomMediaUtils.getStringFromDicomElement(header, Tag.RescaleType, null));
            setTagNoNull(TagW.PixelIntensityRelationship,
                DicomMediaUtils.getStringFromDicomElement(header, Tag.PixelIntensityRelationship, null));

            setTagNoNull(TagW.VOILUTSequence, header.get(Tag.VOILUTSequence));
            setTagNoNull(TagW.WindowWidth, DicomMediaUtils.getFloatArrayFromDicomElement(header, Tag.WindowWidth, null));
            setTagNoNull(TagW.WindowCenter,
                DicomMediaUtils.getFloatArrayFromDicomElement(header, Tag.WindowCenter, null));
            setTagNoNull(TagW.WindowCenterWidthExplanation,
                DicomMediaUtils.getStringArrayFromDicomElement(header, Tag.WindowCenterWidthExplanation, null));
            setTagNoNull(TagW.VOILutFunction,
                DicomMediaUtils.getStringFromDicomElement(header, Tag.VOILUTFunction, null));

            setTagNoNull(TagW.Units, header.getString(Tag.Units));

            setTagNoNull(TagW.SmallestImagePixelValue,
                DicomMediaUtils.getIntPixelValue(header, Tag.SmallestImagePixelValue, signed, bitsStored));
            setTagNoNull(TagW.LargestImagePixelValue,
                DicomMediaUtils.getIntPixelValue(header, Tag.LargestImagePixelValue, signed, bitsStored));
            setTagNoNull(TagW.NumberOfFrames,
                DicomMediaUtils.getIntegerFromDicomElement(header, Tag.NumberOfFrames, null));
            setTagNoNull(TagW.OverlayRows, DicomMediaUtils.getIntegerFromDicomElement(header, Tag.OverlayRows, null));

            int samplesPerPixel = DicomMediaUtils.getIntegerFromDicomElement(header, Tag.SamplesPerPixel, 1);
            setTagNoNull(TagW.SamplesPerPixel, samplesPerPixel);
            String photometricInterpretation = header.getString(Tag.PhotometricInterpretation);
            setTagNoNull(TagW.PhotometricInterpretation, photometricInterpretation);
            setTag(TagW.MonoChrome,
                samplesPerPixel == 1 && !"PALETTE COLOR".equalsIgnoreCase(photometricInterpretation)); //$NON-NLS-1$

            setTagNoNull(TagW.Rows, DicomMediaUtils.getIntegerFromDicomElement(header, Tag.Rows, null));
            setTagNoNull(TagW.Columns, DicomMediaUtils.getIntegerFromDicomElement(header, Tag.Columns, null));

            setTagNoNull(TagW.PixelPaddingValue,
                DicomMediaUtils.getIntPixelValue(header, Tag.PixelPaddingValue, signed, bitsStored));
            setTagNoNull(TagW.PixelPaddingRangeLimit,
                DicomMediaUtils.getIntPixelValue(header, Tag.PixelPaddingRangeLimit, signed, bitsStored));

            setTagNoNull(TagW.MIMETypeOfEncapsulatedDocument, header.getString(Tag.MIMETypeOfEncapsulatedDocument));
            setTagNoNull(TagW.PixelDataProviderURL, header.getString(Tag.PixelDataProviderURL));
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
    public PlanarImage getMediaFragment(MediaElement<PlanarImage> media) throws Exception {
        if (media != null && media.getKey() instanceof Integer && isReadableDicom()) {
            int frame = (Integer) media.getKey();
            if (frame >= 0 && frame < numberOfFrame && hasPixel) {
                // read as tiled rendered image
                LOGGER.debug("read dicom image frame: {} sopUID: {}", frame, tags.get(TagW.SOPInstanceUID)); //$NON-NLS-1$
                RenderedImage buffer = null;
                if ("1.2.840.10008.1.2.4.94".equals(tsuid)) { //$NON-NLS-1$
                    if (jpipReader == null) {
                        ImageReaderFactory f = ImageReaderFactory.getInstance();
                        jpipReader = f.getReaderForTransferSyntax(tsuid);
                    }
                    MediaElement[] elements = getMediaElement();
                    if (elements != null && elements.length > frame) {
                        jpipReader.setInput(elements);
                        buffer = jpipReader.readAsRenderedImage(frame, null);
                    }
                } else {
                    buffer = readAsRenderedImage(frame, null);
                }
                PlanarImage img = null;
                if (buffer != null) {
                    // Bug fix: CLibImageReader and J2KImageReaderCodecLib (imageio libs) do not handle negative values
                    // for short data. They convert signed short to unsigned short.
                    if (dataType == DataBuffer.TYPE_SHORT
                        && buffer.getSampleModel().getDataType() == DataBuffer.TYPE_USHORT) {
                        img =
                            RectifyUShortToShortDataDescriptor
                                .create(buffer, LayoutUtil.createTiledLayoutHints(buffer));
                    } else if (ImageUtil.isBinary(buffer.getSampleModel())) {
                        ParameterBlock pb = new ParameterBlock();
                        pb.addSource(buffer);
                        // Tile size are set in this operation
                        img = JAI.create("formatbinary", pb, null); //$NON-NLS-1$
                    } else if (buffer.getTileWidth() != ImageFiler.TILESIZE
                        || buffer.getTileHeight() != ImageFiler.TILESIZE) {
                        img = ImageFiler.tileImage(buffer);
                    } else {
                        img = NullDescriptor.create(buffer, LayoutUtil.createTiledLayoutHints(buffer));
                    }
                }
                return img;
            }
        }
        return null;
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
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public MediaElement[] getMediaElement() {
        if (image == null) {
            if (isReadableDicom()) {
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
                                image[i].setTag(TagW.InstanceNumber, i + 1);
                            }
                        }
                    } else {
                        String modality = (String) getTagValue(TagW.Modality);
                        boolean ps =
                            modality != null
                                && ("PR".equals(modality) || "KO".equals(modality) || "SR".equals(modality)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        if (ps) {
                            image = new MediaElement[1];
                            image[0] = new DicomSpecialElement(this, null);
                        } else {
                            // Corrupted image => should have one frame
                            image = new MediaElement[0];
                        }
                    }
                }
            }
        }
        return image;
    }

    @Override
    public MediaSeries getMediaSeries() {
        Series series = null;
        if (isReadableDicom()) {
            String seriesUID = (String) getTagValue(TagW.SeriesInstanceUID);
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
    public String getMediaFragmentMimeType(Object key) {
        return mimeType;
    }

    @Override
    public HashMap<TagW, Object> getMediaFragmentTags(Object key) {
        if (key instanceof Integer) {
            if ((Integer) key > 0) {
                HashMap<TagW, Object> tagList = (HashMap<TagW, Object>) tags.clone();
                if (DicomMediaUtils.writePerFrameFunctionalGroupsSequence(tagList, getDicomObject(), (Integer) key)) {
                    DicomMediaUtils.validateDicomImageValues(tagList);
                    DicomMediaUtils.computeSlicePositionVector(tagList);
                }
                return tagList;
            }
        }
        return tags;
    }

    @Override
    public URI getMediaFragmentURI(Object key) {
        return uri;
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
        ImageReader imgReader = null;
        try {
            imgReader =
                ImageReaderFactory.getInstance().getReaderForTransferSyntax(
                    (String) getTagValue(TagW.TransferSyntaxUID));
        } catch (Exception e1) {
            // Do nothing
        }
        if (imgReader != null) {
            desc[1] = "Image Reader Class: " + imgReader.getClass().getName(); //$NON-NLS-1$
            try {
                desc[2] = "Image Format: " + imgReader.getFormatName(); //$NON-NLS-1$
            } catch (IOException e) {
                desc[2] = "Image Format: unknown"; //$NON-NLS-1$
            }

        }
        if (desc[1] == null) {
            desc[1] = Messages.getString("DicomMediaIO.msg_no_reader") + tsuid; //$NON-NLS-1$
        }
        return desc;
    }

    public Series buildSeries(String seriesUID) {
        if (IMAGE_MIMETYPE.equals(mimeType)) {
            return new DicomSeries(seriesUID);
        } else if (SERIES_VIDEO_MIMETYPE.equals(mimeType)) {
            return new DicomVideoSeries(seriesUID);
        } else if (SERIES_ENCAP_DOC_MIMETYPE.equals(mimeType)) {
            return new DicomEncapDocSeries(seriesUID);
        }
        return new DicomSeries(seriesUID);
    }

    @Override
    public int getHeight(int imageIndex) throws IOException {
        if (OverlayUtils.isOverlay(imageIndex)) {
            DicomObject ds = readMetaData(false);
            return OverlayUtils.getOverlayHeight(ds, imageIndex);
        }
        return (Integer) getTagValue(TagW.Rows);
    }

    @Override
    public int getWidth(int imageIndex) throws IOException {
        if (OverlayUtils.isOverlay(imageIndex)) {
            DicomObject ds = readMetaData(false);
            return OverlayUtils.getOverlayWidth(ds, imageIndex);
        }
        return (Integer) getTagValue(TagW.Columns);
    }

    @Override
    public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) throws IOException {
        // Index changes from 1 to 0 as the Dicom frames start to count at 1
        // ImageReader expects the first frame to be 0.
        initImageReader(0);
        return reader.getImageTypes(0);
    }

    @Override
    public boolean canReadRaster() {
        return true;
    }

    /**
     * Read the raw raster data from the image, without any LUTs being applied. Cannot read overlay data, as it isn't
     * clear what the raster format should be for those.
     */
    @Override
    public Raster readRaster(int imageIndex, ImageReadParam param) throws IOException {
        readingImage = true;
        try {
            initImageReader(imageIndex);
            if (param == null) {
                param = getDefaultReadParam();
            }
            if (compressed) {
                ImageReadParam param1 = reader.getDefaultReadParam();
                copyReadParam(param, param1);
                Raster raster = decompressRaster(imageIndex, param1);
                if (clampPixelValues) {
                    clampPixelValues(raster);
                }
                return raster;
            }
            String pmi = (String) tags.get(TagW.PhotometricInterpretation);
            if (pmi.endsWith("422") || pmi.endsWith("420")) { //$NON-NLS-1$ //$NON-NLS-2$
                LOGGER.debug("Using a 422/420 partial component image reader."); //$NON-NLS-1$
                if (param.getSourceXSubsampling() != 1 || param.getSourceYSubsampling() != 1
                    || param.getSourceRegion() != null) {
                    LOGGER.warn("YBR_*_422 and 420 reader does not support source sub-sampling or source region."); //$NON-NLS-1$
                    throw new UnsupportedOperationException("Implement sub-sampling/soure region."); //$NON-NLS-1$
                }
                SampleModel sm = createSampleModel();
                WritableRaster wr = Raster.createWritableRaster(sm, new Point());
                DataBufferByte dbb = (DataBufferByte) wr.getDataBuffer();
                byte[] data = dbb.getData();
                int frameLength = calculateFrameLength();
                LOGGER.debug("Seeking to " + (pixelDataPos + imageIndex * frameLength) + " and reading " + data.length //$NON-NLS-1$ //$NON-NLS-2$
                    + " bytes."); //$NON-NLS-1$
                iis.seek(pixelDataPos + imageIndex * frameLength);
                iis.read(data, 0, frameLength);
                if (swapByteOrder) {
                    ByteUtils.toggleShortEndian(data);
                }
                return wr;
            }
            Raster raster = reader.readRaster(imageIndex, param);
            if (swapByteOrder) {
                ByteUtils.toggleShortEndian(((DataBufferByte) raster.getDataBuffer()).getData());
            }
            return raster;
        } finally {
            readingImage = false;
        }
    }

    // TODO Can this function be removed?
    private void clampPixelValues(Raster raster) {
        int bitsStored = (Integer) getTagValue(TagW.BitsStored);
        int maxVal = -1 >>> (32 - bitsStored);
        short[] data = ((DataBufferUShort) raster.getDataBuffer()).getData();
        for (int i = 0; i < data.length; i++) {
            if (data[i] > maxVal) {
                data[i] = (short) maxVal;
            }
        }
    }

    /**
     * Reads the provided image as a buffered image. It is possible to read image overlays by providing the 0x60000000
     * number associated with the overlay. Otherwise, the imageIndex must be in the range 0..numberOfFrames-1, or 0 for
     * a single frame image. Overlays can be read from PR objects or other types of objects in addition to image
     * objects. param can be used to specify GSPS to apply to the image, or to override the default window level values,
     * or to return the raw image.
     */
    @Override
    public BufferedImage read(int imageIndex, ImageReadParam param) throws IOException {
        readingImage = true;
        try {
            if (OverlayUtils.isOverlay(imageIndex)) {
                DicomObject ds = readMetaData(false);
                String rgbs = (param != null) ? ((DicomImageReadParam) param).getOverlayRGB() : null;
                return OverlayUtils.extractOverlay(ds, imageIndex, this, rgbs);
            }
            initImageReader(imageIndex);
            if (param == null) {
                param = getDefaultReadParam();
            }
            String pmi = (String) tags.get(TagW.PhotometricInterpretation);
            BufferedImage bi;
            if (compressed) {
                ImageReadParam param1 = reader.getDefaultReadParam();
                copyReadParam(param, param1);
                bi = reader.read(0, param1);
                if (clampPixelValues) {
                    clampPixelValues(bi.getRaster());
                }
                postDecompress();
                if ("PALETTE COLOR".equalsIgnoreCase(pmi) && bi.getColorModel().getNumComponents() == 1) { //$NON-NLS-1$
                    bi =
                        new BufferedImage(ColorModelFactory.createColorModel(readMetaData(false)), bi.getRaster(),
                            false, null);
                }
            } else if (pmi.endsWith("422") || pmi.endsWith("420")) { //$NON-NLS-1$ //$NON-NLS-2$
                bi = readYbr400(imageIndex, param);
            } else {
                bi = reader.read(imageIndex, param);
                if (swapByteOrder) {
                    ByteUtils.toggleShortEndian(((DataBufferByte) bi.getRaster().getDataBuffer()).getData());
                }
            }
            RenderedImage img = validateSignedShortDataBuffer(bi);
            return (img instanceof BufferedImage ? (BufferedImage) img : ((RenderedOp) img).getAsBufferedImage());
        } finally {
            readingImage = false;
        }
    }

    @Override
    public RenderedImage readAsRenderedImage(int imageIndex, ImageReadParam param) throws IOException {
        readingImage = true;
        try {
            initImageReader(imageIndex);
            if (param == null) {
                param = getDefaultReadParam();
            }
            String pmi = (String) tags.get(TagW.PhotometricInterpretation);
            RenderedImage bi;
            if (compressed) {
                ImageReadParam param1 = reader.getDefaultReadParam();
                copyReadParam(param, param1);
                bi = reader.readAsRenderedImage(0, param1);
                postDecompress();

                // // TEST cache compressed images. Reading is slow with pure java raw reader! Issue W/L shifted.
                // if (reader.getClass().getName().equals("com.sun.media.imageioimpl.plugins.jpeg2000.J2KImageReader"))
                // {
                // Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName("RAW");
                // ImageWriter writer = null;
                // while (iter.hasNext()) {
                // writer = iter.next();
                // }
                // if (writer != null) {
                //                    File outFile = File.createTempFile("raw_", ".raw", DICOM_CACHE_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                // ImageOutputStream out = null;
                // out = ImageIO.createImageOutputStream(outFile);
                // writer.setOutput(out);
                //
                // ImageWriteParam iwp = writer.getDefaultWriteParam();
                // iwp.setTilingMode(ImageWriteParam.MODE_EXPLICIT);
                // iwp.setTiling(ImageFiler.TILESIZE, ImageFiler.TILESIZE, 0, 0);
                // writer.write(null, new IIOImage(bi, null, null), iwp);
                //
                // // Read image, no need to cache because image would accessible
                // long[] frameOffsets = new long[frames];
                // int frameLen = width * height * samples * (allocated >> 3);
                // frameOffsets[0] = 0;
                // for (int i = 1; i < frameOffsets.length; i++) {
                // frameOffsets[i] = frameOffsets[i - 1] + frameLen;
                // }
                // Dimension[] imageDimensions = new Dimension[frames];
                // Arrays.fill(imageDimensions, new Dimension(width, height));
                // ColorModel cm = ColorModelFactory.createColorModel(ds);
                // SampleModel sm = cm.createCompatibleSampleModel(width, height);
                // RawImageInputStream riis =
                // new RawImageInputStream(ImageIO.createImageInputStream(outFile),
                // new ImageTypeSpecifier(cm, sm), frameOffsets, imageDimensions);
                // riis.setByteOrder(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
                // // ImageReader readerRaw = ImageIO.getImageReadersByFormatName("RAW").next();
                // // readerRaw.setInput(riis);
                // // Tile image while reading to handle large images
                // ImageLayout layout = new ImageLayout();
                // layout.setTileWidth(ImageFiler.TILESIZE);
                // layout.setTileHeight(ImageFiler.TILESIZE);
                // RenderingHints hints = new RenderingHints(JAI.KEY_IMAGE_LAYOUT, layout);
                //                    ParameterBlockJAI pb = new ParameterBlockJAI("ImageRead"); //$NON-NLS-1$
                //                    pb.setParameter("Input", riis); //$NON-NLS-1$
                //                    bi = JAI.create("ImageRead", pb, hints); //$NON-NLS-1$
                // }
                // }
            } else if (pmi.endsWith("422") || pmi.endsWith("420")) { //$NON-NLS-1$ //$NON-NLS-2$
                bi = readYbr400(imageIndex, param);
                if (bi != null) {
                    File outFile = File.createTempFile("img_", ".jpg", DICOM_CACHE_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                    ImageOutputStream out = null;
                    try {
                        Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName("JPEG"); //$NON-NLS-1$
                        ImageWriter writer = null;
                        while (iter.hasNext()) {
                            ImageWriter w = iter.next();
                            // Other encoder do not write it correctly
                            // Workaround: overrides createSubsetSampleModel() in constructor of
                            // PartialComponentSampleModel
                            if (w.getClass().getName().equals("com.sun.imageio.plugins.jpeg.JPEGImageWriter")) { //$NON-NLS-1$
                                writer = w;
                                break;
                            }
                        }
                        if (writer != null) {
                            out = ImageIO.createImageOutputStream(outFile);
                            writer.setOutput(out);
                            JPEGImageWriteParam iwp = new JPEGImageWriteParam(null);
                            iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                            iwp.setCompressionQuality(1.0f);

                            writer.write(null, new IIOImage(bi, null, null), iwp);
                            // Read image. No need to cache the filename because it is contained in the rendered image.
                            bi = ImageIO.read(outFile);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        FileUtil.safeClose(out);
                    }
                }

            } else {
                bi = reader.readAsRenderedImage(imageIndex, param);
                if (swapByteOrder) {
                    // TODO handle this case for big image, find an example
                    ByteUtils.toggleShortEndian(((DataBufferByte) bi.getData().getDataBuffer()).getData());
                }
            }
            return validateSignedShortDataBuffer(bi);
        } finally {
            readingImage = false;
        }
    }

    private BufferedImage readYbr400(int imageIndex, ImageReadParam param) throws IOException {
        ImageReadParam useParam = param;
        Rectangle sourceRegion = param.getSourceRegion();
        if (param.getSourceXSubsampling() != 1 || param.getSourceYSubsampling() != 1 || sourceRegion != null) {
            useParam = getDefaultReadParam();
        }
        BufferedImage bi;
        WritableRaster wr = (WritableRaster) readRaster(imageIndex, useParam);
        bi = new BufferedImage(ColorModelFactory.createColorModel(getDicomObject()), wr, false, null);
        if (useParam == param) {
            return bi;
        }
        return DicomImageReader.subsampleRGB(bi, sourceRegion, param.getSourceXSubsampling(),
            param.getSourceYSubsampling());
    }

    protected void copyReadParam(ImageReadParam src, ImageReadParam dst) {
        dst.setDestination(src.getDestination());
        dst.setSourceRegion(src.getSourceRegion());
        dst.setSourceSubsampling(src.getSourceXSubsampling(), src.getSourceYSubsampling(), src.getSubsamplingXOffset(),
            src.getSubsamplingYOffset());
        dst.setDestinationOffset(src.getDestinationOffset());
        if (ImageReaderFactory.getInstance().needsImageTypeSpecifier(tsuid)) {
            dst.setDestinationType(createImageTypeSpecifier());
        }
    }

    private Raster decompressRaster(int imageIndex, ImageReadParam param) throws IOException {
        if (!reader.canReadRaster()) {
            BufferedImage bi = reader.read(0, param);
            postDecompress();
            return bi.getRaster();
        }
        Raster raster = reader.readRaster(0, param);
        postDecompress();
        return raster;
    }

    protected void postDecompress() {
        // workaround for Bug in J2KImageReader and
        // J2KImageReaderCodecLib.setInput()
        if (reader.getClass().getName().startsWith("com.sun.media.imageioimpl.plugins.jpeg2000.J2KImageReader")) { //$NON-NLS-1$
            reader.dispose();
            ImageReaderFactory f = ImageReaderFactory.getInstance();
            reader = f.getReaderForTransferSyntax(tsuid);
        } else {
            reader.reset();
        }
    }

    public RenderedImage validateSignedShortDataBuffer(RenderedImage source) {
        /*
         * Issue in ComponentColorModel when signed short DataBuffer, only 16 bits is supported see
         * http://java.sun.com/javase/6/docs/api/java/awt/image/ComponentColorModel.html Instances of
         * ComponentColorModel created with transfer types DataBuffer.TYPE_SHORT, DataBuffer.TYPE_FLOAT, and
         * DataBuffer.TYPE_DOUBLE use all the bits of all sample values. Thus all color/alpha components have 16 bits
         * when using DataBuffer.TYPE_SHORT, 32 bits when using DataBuffer.TYPE_FLOAT, and 64 bits when using
         * DataBuffer.TYPE_DOUBLE. When the ComponentColorModel(ColorSpace, int[], boolean, boolean, int, int) form of
         * constructor is used with one of these transfer types, the bits array argument is ignored.
         */

        // Bits Allocated = 16 (Bits alloués )
        // Bits Stored = 12 (Bits enregistrés )
        // High Bit = 11 (Bit le plus significatif)
        // |<------------------ pixel ----------------->|
        // ______________ ______________ ______________ ______________
        // |XXXXXXXXXXXXXX| | | |
        // |______________|______________|______________|______________|
        // 15 12 11 8 7 4 3 0
        //
        // ---------------------------
        //
        // Bits Allocated = 16
        // Bits Stored = 12
        // High Bit = 15
        // |<------------------ pixel ----------------->|
        // ______________ ______________ ______________ ______________
        // | | | |XXXXXXXXXXXXXX|
        // |______________|______________|______________|______________|
        // 15 12 11 8 7 4 3 0
        int allocated = (Integer) getTagValue(TagW.BitsAllocated);
        int highBit;
        // TODO test with all decoders (works with raw decoder)
        if (source != null && dataType == DataBuffer.TYPE_SHORT
            && source.getSampleModel().getDataType() == DataBuffer.TYPE_SHORT
            && (highBit = getDicomObject().getInt(Tag.HighBit, allocated - 1) + 1) < allocated) {
            source = RectifySignedShortDataDescriptor.create(source, new int[] { highBit }, null);
        }
        return source;
    }

    @Override
    public BufferedImage readTile(int imageIndex, int tileX, int tileY) throws IOException {
        return super.readTile(imageIndex, tileX, tileY);
    }

    @Override
    public Raster readTileRaster(int imageIndex, int tileX, int tileY) throws IOException {
        return super.readTileRaster(imageIndex, tileX, tileY);
    }

    public boolean isSkipLargePrivate() {
        return skipLargePrivate;
    }

    public void setSkipLargePrivate(boolean skipLargePrivate) {
        this.skipLargePrivate = skipLargePrivate;
    }

    // public void readPostPixeldata() throws IOException {
    // readMetaData(true);
    // long currentPosition = dis.getStreamPosition();
    //
    // DicomObject postPixelDs = new BasicDicomObject();
    //
    // if (pixelDataPos > 0) {
    // // There is pixeldata
    // if (pixelDataLen >= 0) {
    // // last call already read the tag, but we want to init a new InputStream from here
    // dis.reset();
    //
    // // there is uncompressed pixeldata
    // long startPosition = pixelDataPos + pixelDataLen;
    // iis.seek(startPosition);
    // } else {
    // // via siis
    // int imageIndex = numberOfFrame - 1;
    // imageIndex = (imageIndex < 0) ? 0 : imageIndex;
    // if (siis == null) {
    // initCompressedImageReader(imageIndex);
    // }
    // itemParser.seekFooter();
    // }
    // } else {
    // // last call already read the tag, but we want to init a new InputStream from here
    // dis.reset();
    // }
    //
    // // Not reusing the earlier dicom input stream as it has a stop tag handler and we want to go past the pixeldata.
    // DicomInputStream postDis = new DicomInputStream(iis, org.dcm4che2.data.TransferSyntax.valueOf(tsuid));
    // if (isSkipLargePrivate()) {
    // DicomInputHandler dih = new SizeSkipInputHandler(null);
    // postDis.setHandler(dih);
    // }
    //
    // postPixelDs = postDis.readDicomObject();
    // if (postPixelDs != null && !postPixelDs.isEmpty()) {
    // // Note the postPixelDs.copyTo(ds) does not work because the copy does not handle the
    // // SkippedDicomElements correctly
    // ds = new CombineDicomObject(ds, postPixelDs);
    // streamMetaData.setDicomObject(ds);
    // }
    //
    // // reset the stream
    // iis.seek(currentPosition);
    // }

    public DicomObject readPixelData() throws Exception {
        try {
            readMetaData(true);
            readingImage = true;
            if (dis.tag() == Tag.PixelData) {
                // Remove the StopHandler on the tag 'Pixel Data'
                dis.setHandler(dis);
                // Set the position at the beginning of the tag 'Pixel Data'
                dis.reset();
                // Read the Pixel Data (compressed is a sequence attribute)
                return dis.readDicomObject();

            } else {
                throw new Exception("Cannot read pixel data"); //$NON-NLS-1$
            }
        } finally {
            readingImage = false;
            // Close stream
            FileUtil.safeClose(iis);
            iis = null;
        }
    }

    public DicomObject getDicomObject() {
        try {
            return readMetaData(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void setInput(Object input, boolean seekForwardOnly, boolean ignoreMetadata) {
        super.setInput(input, seekForwardOnly, ignoreMetadata);
        resetLocal();
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
         * Prevent error when reading images from a large multiframe and the header is removed from the cache at the
         * same time
         */
        if (!readingHeader && !readingImage) {
            super.reset();
            resetLocal();
        }
    }

    private void resetLocal() {
        // System.err.println("Close stream: reset local");
        FileUtil.safeClose(iis);
        iis = null;
        dis = null;
        dataType = 0;
        banded = false;
        bigEndian = false;
        swapByteOrder = false;
        pixelDataPos = 0L;
        pixelDataLen = 0;
        tsuid = null;
        compressed = false;
        if (reader != null) {
            reader.dispose();
            reader = null;
        }
        itemParser = null;
        siis = null;
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
        DicomStreamMetaData streamMetaData = new DicomStreamMetaData();
        streamMetaData.setDicomObject(getDicomObject());
        return streamMetaData;
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
    private synchronized DicomObject readMetaData(boolean readImageAfter) throws IOException {
        DicomObject header = HEADER_CACHE.get(this);
        if (header != null && !readImageAfter) {
            return header;
        }

        try {
            readingHeader = true;
            if (iis == null && uri != null) {
                ImageInputStream imageStream;
                if (uri.toString().startsWith("file:/")) { //$NON-NLS-1$
                    imageStream = ImageIO.createImageInputStream(new File(uri));
                } else {
                    // TODO test if url stream is closed on reset !
                    imageStream = ImageIO.createImageInputStream(uri.toURL().openStream());
                }
                setInput(imageStream, false, false);
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
            dis = new DicomInputStream(iis);
            DicomInputHandler ih = new StopTagInputHandler(Tag.PixelData);
            if (isSkipLargePrivate()) {
                ih = new SizeSkipInputHandler(ih);
            }
            dis.setHandler(ih);
            DicomObject ds = dis.readDicomObject();
            while (dis.tag() == 0xFFFCFFFC) {
                dis.readBytes(dis.valueLength());
                dis.readDicomObject(ds, -1);
            }
            bigEndian = dis.getTransferSyntax().bigEndian();
            tsuid = ds.getString(Tag.TransferSyntaxUID);

            int allocated = ds.getInt(Tag.BitsAllocated, 8);
            int bitsStored = ds.getInt(Tag.BitsStored, allocated);
            int samples = ds.getInt(Tag.SamplesPerPixel, 1);
            numberOfFrame = ds.getInt(Tag.NumberOfFrames);
            banded = ds.getInt(Tag.PlanarConfiguration) != 0;
            dataType =
                allocated <= 8 ? DataBuffer.TYPE_BYTE : ds.getInt(Tag.PixelRepresentation) != 0 ? DataBuffer.TYPE_SHORT
                    : DataBuffer.TYPE_USHORT;
            if (allocated > 16 && samples == 1) {
                dataType = DataBuffer.TYPE_INT;
            }

            if (dis.tag() == Tag.PixelData) {
                if (numberOfFrame == 0) {
                    numberOfFrame = 1;
                }
                swapByteOrder = bigEndian && dis.vr() == VR.OW && dataType == DataBuffer.TYPE_BYTE;
                if (swapByteOrder && banded) {
                    throw new UnsupportedOperationException(
                        "Big Endian color-by-plane with Pixel Data VR=OW not implemented"); //$NON-NLS-1$
                }
                pixelDataPos = dis.getStreamPosition();
                pixelDataLen = dis.valueLength();

                compressed = pixelDataLen == -1;
                if (!compressed && tsuid.startsWith("1.2.840.10008.1.2.4")) { //$NON-NLS-1$
                    // Corrupted image where missing the encapsulated part for the identification the compressed dataset
                    compressed = true;
                }
                if (compressed) {
                    ImageReaderFactory f = ImageReaderFactory.getInstance();
                    LOGGER.debug("Transfer syntax for image is " + tsuid + " with image reader class " + f.getClass()); //$NON-NLS-1$ //$NON-NLS-2$
                    f.adjustDatasetForTransferSyntax(ds, tsuid);
                    clampPixelValues = allocated == 16 && bitsStored < 12 && UID.JPEGExtended24.equals(tsuid);
                }
            } else if (ds.getString(Tag.PixelDataProviderURL) != null) {
                if (numberOfFrame == 0) {
                    numberOfFrame = 1;
                    compressed = true;
                }
            }

            HEADER_CACHE.put(this, ds);
            return ds;
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

    private int calculateFrameLength() {
        String pmi = (String) tags.get(TagW.PhotometricInterpretation);
        int width = (Integer) getTagValue(TagW.Columns);
        int height = (Integer) getTagValue(TagW.Rows);
        int samples = (Integer) getTagValue(TagW.SamplesPerPixel);
        int allocated = (Integer) getTagValue(TagW.BitsAllocated);
        if (pmi.endsWith("422") || pmi.endsWith("420")) { //$NON-NLS-1$ //$NON-NLS-2$
            int calcWidth = width;
            int calcHeight = height;
            int extraRowSamples = 0;
            int extraColSamples = 0;
            if (pmi.endsWith("422")) { //$NON-NLS-1$

                if (width % 2 != 0) {
                    // odd number of columns
                    calcWidth--;
                    extraColSamples = 3;
                }
                return (calcWidth * calcHeight * 2 + calcHeight * extraColSamples) * (allocated >> 3);
            }

            if (pmi.endsWith("420")) { //$NON-NLS-1$
                calcHeight = calcHeight / 2;
                if (width % 2 != 0) {
                    // odd number of columns
                    calcWidth--;
                    extraColSamples = 4;
                }
                if (height % 2 != 0) {
                    // odd number of rows
                    extraRowSamples = 2;
                }

                int length =
                    (calcWidth * calcHeight * 3 + calcWidth * extraRowSamples + calcHeight * extraColSamples)
                        * (allocated >> 3);

                if (width % 2 != 0 && height % 2 != 0) {
                    length = length + 3;
                }

                return length;
            }
        }
        return width * height * samples * (allocated >> 3);
    }

    /** Create an image type specifier for the entire image */
    protected ImageTypeSpecifier createImageTypeSpecifier() {
        ColorModel cm = ColorModelFactory.createColorModel(getDicomObject());
        SampleModel sm = createSampleModel();
        return new ImageTypeSpecifier(cm, sm);
    }

    private SampleModel createSampleModel() {
        String pmi = (String) tags.get(TagW.PhotometricInterpretation);
        int width = (Integer) getTagValue(TagW.Columns);
        int height = (Integer) getTagValue(TagW.Rows);
        int samples = (Integer) getTagValue(TagW.SamplesPerPixel);
        if (samples == 1) {
            return new PixelInterleavedSampleModel(dataType, width, height, 1, width, OFFSETS_0);
        }

        // samples == 3
        if (banded) {
            return new BandedSampleModel(dataType, width, height, width, OFFSETS_0_1_2, OFFSETS_0_0_0);
        }

        if (pmi.endsWith("422")) { //$NON-NLS-1$
            return new PartialComponentSampleModel(width, height, 2, 1) {
                @Override
                public SampleModel createSubsetSampleModel(int[] bands) {
                    if (bands.length != 3) {
                        throw new RasterFormatException("Accept only 3 bands"); //$NON-NLS-1$
                    }
                    return this;
                }
            };
        }

        if ((!compressed) && pmi.endsWith("420")) { //$NON-NLS-1$
            return new PartialComponentSampleModel(width, height, 2, 2) {
                @Override
                public SampleModel createSubsetSampleModel(int[] bands) {
                    if (bands.length != 3) {
                        throw new RasterFormatException("Accept only 3 bands"); //$NON-NLS-1$
                    }
                    return this;
                }
            };
        }

        return new PixelInterleavedSampleModel(dataType, width, height, 3, width * 3, OFFSETS_0_1_2);
    }
}
