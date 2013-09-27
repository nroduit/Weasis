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
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.geom.Area;
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
import java.io.IOException;
import java.lang.ref.Reference;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageInputStreamImpl;
import javax.media.jai.JAI;
import javax.media.jai.LookupTableJAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.operator.AndConstDescriptor;
import javax.media.jai.operator.NullDescriptor;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.BulkData;
import org.dcm4che.data.Fragments;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.data.VR;
import org.dcm4che.image.Overlays;
import org.dcm4che.image.PaletteColorModel;
import org.dcm4che.image.PhotometricInterpretation;
import org.dcm4che.imageio.codec.ImageReaderFactory;
import org.dcm4che.imageio.codec.ImageReaderFactory.ImageReaderParam;
import org.dcm4che.imageio.codec.jpeg.PatchJPEGLS;
import org.dcm4che.imageio.codec.jpeg.PatchJPEGLSImageInputStream;
import org.dcm4che.imageio.plugins.dcm.DicomImageReadParam;
import org.dcm4che.imageio.plugins.dcm.DicomMetaData;
import org.dcm4che.imageio.stream.ImageInputStreamAdapter;
import org.dcm4che.imageio.stream.SegmentedInputImageStream;
import org.dcm4che.io.BulkDataDescriptor;
import org.dcm4che.io.DicomInputStream;
import org.dcm4che.io.DicomInputStream.IncludeBulkData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.image.op.RectifySignedShortDataDescriptor;
import org.weasis.core.api.image.op.RectifyUShortToShortDataDescriptor;
import org.weasis.core.api.image.util.CIELab;
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
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.codec.utils.DicomImageUtils;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.codec.utils.OverlayUtils;

import com.sun.media.imageio.stream.RawImageInputStream;
import com.sun.media.jai.util.ImageUtil;

public class DicomMediaIO extends ImageReader implements MediaReader<PlanarImage> {

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

    private static final SoftHashMap<DicomMediaIO, DicomMetaData> HEADER_CACHE =
        new SoftHashMap<DicomMediaIO, DicomMetaData>() {

            public Reference<? extends DicomMetaData> getReference(DicomMediaIO key) {
                return hash.get(key);
            }

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
    private PatchJPEGLS patchJpegLS;
    private int frameLength;
    private PhotometricInterpretation pmi;

    private URI uri;
    private int numberOfFrame;
    private final HashMap<TagW, Object> tags;
    private volatile MediaElement[] image = null;
    private volatile String mimeType;

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

    public DicomMediaIO(Attributes dcmItems) {
        this((URI) null);
        if (dcmItems == null) {
            throw new IllegalArgumentException();
        }
        this.dcmMetadata = new DicomMetaData(null, dcmItems);
    }

    @Override
    public synchronized void replaceURI(URI uri) {
        if (uri != null && !uri.equals(this.uri)) {
            this.uri = uri;
            reset();
        }

    }

    public boolean isWritableDicom() {
        return (dcmMetadata != null && uri == null);
    }

    public boolean isReadableDicom() {
        if (UNREADABLE.equals(mimeType)) {
            // Return true only to display the error message in the view
            return true;
        }
        if (uri == null && dcmMetadata == null) {
            return false;
        }

        if (tags.size() == 0) {
            try {
                DicomMetaData md = readMetaData(false);
                Attributes fmi = md.getFileMetaInformation();
                Attributes header = md.getAttributes();
                // Exclude DICOMDIR
                String mediaStorageSOPClassUID = fmi == null ? null : fmi.getString(Tag.MediaStorageSOPClassUID);
                if ("1.2.840.10008.1.3.10".equals(mediaStorageSOPClassUID)) { //$NON-NLS-1$ //$NON-NLS-2$
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

            } catch (Throwable t) {
                mimeType = UNREADABLE;
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.error("Cannot read DICOM:", t);
                } else {
                    LOGGER.error(t.getMessage());
                }
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
     * @return
     * @throws IOException
     */

    // protected void initImageReader(int imageIndex) throws IOException {
    // readMetaData(true);
    // if (reader == null) {
    // if (compressed) {
    // initCompressedImageReader(imageIndex);
    // } else {
    // initRawImageReader();
    // }
    // }
    // // Reset the input stream location if required, and reset the reader if required
    // if (compressed && itemParser != null) {
    // itemParser.seekFrame(siis, imageIndex);
    // reader.setInput(siis, false);
    // }
    // // TODO 1.2.840.10008.1.2.4.95 (DICOM JPIP Referenced Deflate Transfer Syntax)
    //        if ("1.2.840.10008.1.2.4.94".equals(tsuid)) { //$NON-NLS-1$
    // MediaElement[] elements = getMediaElement();
    // // TODO handle frame
    // if (elements != null && elements.length > 0) {
    // reader.setInput(elements[0]);
    // }
    // }
    // }

    // private void initCompressedImageReader(int imageIndex) throws IOException {
    // ImageReaderFactory f = ImageReaderFactory.getInstance();
    // this.reader = f.getReaderForTransferSyntax(tsuid);
    // if (!"1.2.840.10008.1.2.4.94".equals(tsuid)) {
    // this.itemParser = new ItemParser(dis, iis, numberOfFrame, tsuid);
    // this.siis = new SegmentedImageInputStream(iis, itemParser);
    // }
    // }

    private ImageReader initRawImageReader() {
        long[] frameOffsets = new long[numberOfFrame];
        frameOffsets[0] = pixeldata.offset;
        for (int i = 1; i < frameOffsets.length; i++) {
            frameOffsets[i] = frameOffsets[i - 1] + frameLength;
        }
        Dimension[] imageDimensions = new Dimension[numberOfFrame];
        int width = (Integer) getTagValue(TagW.Columns);
        int height = (Integer) getTagValue(TagW.Rows);
        Arrays.fill(imageDimensions, new Dimension(width, height));
        RawImageInputStream riis =
            new RawImageInputStream(iis, createImageType(bitsStored, dataType, banded), frameOffsets, imageDimensions);
        // endianess is already in iis?
        // riis.setByteOrder(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

        ImageReader reader = new RawImageReader(DicomCodec.RawImageReaderSpi);
        // ImageReader reader = ImageIO.getImageReadersByFormatName("RAW").next();
        // if (reader == null) {
        // FileUtil.safeClose(riis);
        // throw new UnsupportedOperationException("No RAW Reader available");
        // }
        reader.setInput(riis);
        return reader;
    }

    private boolean setDicomSpecialType(Attributes header) {
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
            group.setTagNoNull(TagW.PatientName, getTagValue(TagW.PatientName));
            group.setTagNoNull(TagW.StudyDescription, header.getString(Tag.StudyDescription));

            //            if ("1.2.840.10008.1.2.4.94".equals(tsuid)) { //$NON-NLS-1$
            // MediaElement[] elements = getMediaElement();
            // if (elements != null) {
            // for (MediaElement m : elements) {
            // m.setTag(TagW.ExplorerModel, group.getTagValue(TagW.ExplorerModel));
            // }
            // }
            // }
        }
    }

    private void writeInstanceTags(Attributes fmi, Attributes header) {
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
            DicomMediaUtils.buildPatientPseudoUID(patientID, header.getString(Tag.IssuerOfPatientID), name, null));
        setTag(TagW.StudyInstanceUID, header.getString(Tag.StudyInstanceUID, NO_VALUE));
        setTag(TagW.SeriesInstanceUID, header.getString(Tag.SeriesInstanceUID, NO_VALUE));
        setTag(TagW.Modality, header.getString(Tag.Modality, NO_VALUE));
        setTag(TagW.InstanceNumber,
            DicomMediaUtils.getIntegerFromDicomElement(header, Tag.InstanceNumber, TagW.AppID.incrementAndGet()));
        setTag(TagW.SOPInstanceUID, header.getString(Tag.SOPInstanceUID, getTagValue(TagW.InstanceNumber).toString()));
        if (fmi != null) {
            setTagNoNull(TagW.TransferSyntaxUID, fmi.getString(Tag.TransferSyntaxUID));
        }
        // -------- End of Mandatory Tags --------

        writeOnlyinstance(header);
        writeSharedFunctionalGroupsSequence(header);
        DicomMediaUtils.writePerFrameFunctionalGroupsSequence(tags, header, 0);

        boolean pr = SERIES_PR_MIMETYPE.equals(mimeType);
        boolean ko = SERIES_KO_MIMETYPE.equals(mimeType);
        if (pr) {
            // Set the series list for applying the PR
            setTagNoNull(TagW.ReferencedSeriesSequence, header.getSequence(Tag.ReferencedSeriesSequence));
            DicomMediaUtils.readPRLUTsModule(header, tags);
        }
        if (pr || ko) {
            // Set other required fields
            setTagNoNull(TagW.SeriesDescription, header.getString(Tag.SeriesDescription));
            setTagNoNull(
                TagW.SeriesDate,
                TagW.dateTime(DicomMediaUtils.getDateFromDicomElement(header, Tag.SeriesDate, null),
                    DicomMediaUtils.getDateFromDicomElement(header, Tag.SeriesTime, null)));
            setTagNoNull(TagW.SeriesNumber, DicomMediaUtils.getIntegerFromDicomElement(header, Tag.SeriesNumber, null));
        }

        DicomMediaUtils.buildLUTs(tags);
        DicomMediaUtils.computeSlicePositionVector(tags);

        Area shape = DicomMediaUtils.buildShutterArea(header);
        if (shape != null) {
            setTagNoNull(TagW.ShutterFinalShape, shape);
            Integer psVal = DicomMediaUtils.getIntegerFromDicomElement(header, Tag.ShutterPresentationValue, null);
            setTagNoNull(TagW.ShutterPSValue, psVal);
            float[] rgb =
                CIELab.convertToFloatLab(DicomMediaUtils.getIntAyrrayFromDicomElement(header,
                    Tag.ShutterPresentationColorCIELabValue, null));
            Color color =
                rgb == null ? null : PresentationStateReader.getRGBColor(psVal == null ? 0 : psVal, rgb, (int[]) null);
            setTagNoNull(TagW.ShutterRGBColor, color);

        }
        DicomMediaUtils.computeSUVFactor(header, tags, 0);

        // Remove sequence item
        tags.remove(TagW.ModalityLUTSequence);
        tags.remove(TagW.VOILUTSequence);
        if (pr) {
            tags.remove(TagW.PresentationLUTSequence);
        }
    }

    private void writeSharedFunctionalGroupsSequence(Attributes header) {
        if (header != null) {
            DicomMediaUtils.writeFunctionalGroupsSequence(tags,
                header.getNestedDataset(Tag.SharedFunctionalGroupsSequence));
        }
    }

    private void writeOnlyinstance(Attributes header) {
        if (header != null) {

            // Instance tags
            setTagNoNull(TagW.ImageType, DicomMediaUtils.getStringFromDicomElement(header, Tag.ImageType));
            setTagNoNull(TagW.ImageComments, header.getString(Tag.ImageComments));
            setTagNoNull(TagW.ImageLaterality, header.getString(Tag.ImageLaterality, header.getString(Tag.Laterality)));
            setTagNoNull(TagW.ContrastBolusAgent, header.getString(Tag.ContrastBolusAgent));
            setTagNoNull(TagW.SOPClassUID, header.getString(Tag.SOPClassUID));
            setTagNoNull(TagW.ScanningSequence, DicomMediaUtils.getStringFromDicomElement(header, Tag.ScanningSequence));
            setTagNoNull(TagW.SequenceVariant, DicomMediaUtils.getStringFromDicomElement(header, Tag.SequenceVariant));
            setTagNoNull(TagW.ScanOptions, DicomMediaUtils.getStringFromDicomElement(header, Tag.ScanOptions));
            setTagNoNull(TagW.RepetitionTime,
                DicomMediaUtils.getFloatFromDicomElement(header, Tag.RepetitionTime, null));
            setTagNoNull(TagW.EchoTime, DicomMediaUtils.getFloatFromDicomElement(header, Tag.EchoTime, null));
            setTagNoNull(TagW.InversionTime, DicomMediaUtils.getFloatFromDicomElement(header, Tag.InversionTime, null));
            setTagNoNull(TagW.EchoNumbers, DicomMediaUtils.getIntegerFromDicomElement(header, Tag.EchoNumbers, null));
            setTagNoNull(TagW.GantryDetectorTilt,
                DicomMediaUtils.getFloatFromDicomElement(header, Tag.GantryDetectorTilt, null));
            setTagNoNull(TagW.ConvolutionKernel,
                DicomMediaUtils.getStringFromDicomElement(header, Tag.ConvolutionKernel));
            setTagNoNull(TagW.FlipAngle, DicomMediaUtils.getFloatFromDicomElement(header, Tag.FlipAngle, null));
            setTagNoNull(TagW.PatientOrientation,
                DicomMediaUtils.getStringArrayFromDicomElement(header, Tag.PatientOrientation));
            setTagNoNull(TagW.SliceLocation, DicomMediaUtils.getFloatFromDicomElement(header, Tag.SliceLocation, null));
            setTagNoNull(TagW.SliceThickness,
                DicomMediaUtils.getDoubleFromDicomElement(header, Tag.SliceThickness, null));
            setTagNoNull(TagW.AcquisitionDate,
                DicomMediaUtils.getDateFromDicomElement(header, Tag.AcquisitionDate, null));
            setTagNoNull(TagW.AcquisitionTime,
                DicomMediaUtils.getDateFromDicomElement(header, Tag.AcquisitionTime, null));
            setTagNoNull(TagW.ContentTime, DicomMediaUtils.getDateFromDicomElement(header, Tag.ContentTime, null));

            if (tags.get(TagW.AcquisitionDate) == null) {
                // For Secondary Capture replace by DateOfSecondaryCapture
                Date date = DicomMediaUtils.getDateFromDicomElement(header, Tag.DateOfSecondaryCapture, null);
                if (date != null) {
                    setTagNoNull(TagW.AcquisitionDate, date);
                    setTagNoNull(TagW.AcquisitionTime,
                        DicomMediaUtils.getDateFromDicomElement(header, Tag.TimeOfSecondaryCapture, null));
                }
            }

            writeImageValues(header);
            setTagNoNull(TagW.MIMETypeOfEncapsulatedDocument, header.getString(Tag.MIMETypeOfEncapsulatedDocument));
            setTagNoNull(TagW.PixelDataProviderURL, header.getString(Tag.PixelDataProviderURL));
        }
    }

    private void writeImageValues(Attributes header) {
        if (hasPixel) {
            setTagNoNull(TagW.ImagePositionPatient,
                DicomMediaUtils.getDoubleArrayFromDicomElement(header, Tag.ImagePositionPatient, null));
            setTagNoNull(TagW.ImageOrientationPatient,
                DicomMediaUtils.getDoubleArrayFromDicomElement(header, Tag.ImageOrientationPatient, null));
            setTagNoNull(
                TagW.ImageOrientationPlane,
                ImageOrientation
                    .makeImageOrientationLabelFromImageOrientationPatient((double[]) getTagValue(TagW.ImageOrientationPatient)));

            bitsStored = DicomMediaUtils.getIntegerFromDicomElement(header, Tag.BitsStored, 8);
            bitsAllocated = DicomMediaUtils.getIntegerFromDicomElement(header, Tag.BitsAllocated, bitsStored);
            highBit = DicomMediaUtils.getIntegerFromDicomElement(header, Tag.HighBit, bitsStored - 1);
            if (highBit >= bitsAllocated) {
                highBit = bitsStored - 1;
            }
            int pixelRepresentation = DicomMediaUtils.getIntegerFromDicomElement(header, Tag.PixelRepresentation, 0);
            setTagNoNull(TagW.BitsAllocated, bitsAllocated);
            setTagNoNull(TagW.BitsStored, bitsStored);
            setTagNoNull(TagW.PixelRepresentation, pixelRepresentation);

            setTagNoNull(TagW.ImagerPixelSpacing,
                DicomMediaUtils.getDoubleArrayFromDicomElement(header, Tag.ImagerPixelSpacing, null));
            setTagNoNull(TagW.PixelSpacing,
                DicomMediaUtils.getDoubleArrayFromDicomElement(header, Tag.PixelSpacing, null));
            setTagNoNull(TagW.PixelAspectRatio,
                DicomMediaUtils.getIntAyrrayFromDicomElement(header, Tag.PixelAspectRatio, null));
            setTagNoNull(TagW.PixelSpacingCalibrationDescription,
                header.getString(Tag.PixelSpacingCalibrationDescription));

            DicomMediaUtils.applyModalityLutModule(header, tags, null);

            setTagNoNull(TagW.PixelIntensityRelationship,
                DicomMediaUtils.getStringFromDicomElement(header, Tag.PixelIntensityRelationship));

            DicomMediaUtils.applyVoiLutModule(header, tags, null);

            setTagNoNull(TagW.Units, header.getString(Tag.Units));

            setTagNoNull(TagW.SmallestImagePixelValue, DicomMediaUtils.getIntPixelValue(header,
                Tag.SmallestImagePixelValue, pixelRepresentation != 0, bitsStored));
            setTagNoNull(TagW.LargestImagePixelValue, DicomMediaUtils.getIntPixelValue(header,
                Tag.LargestImagePixelValue, pixelRepresentation != 0, bitsStored));
            setTagNoNull(TagW.NumberOfFrames,
                DicomMediaUtils.getIntegerFromDicomElement(header, Tag.NumberOfFrames, null));
            setTagNoNull(TagW.HasOverlay, DicomMediaUtils.hasOverlay(header));

            int samplesPerPixel = DicomMediaUtils.getIntegerFromDicomElement(header, Tag.SamplesPerPixel, 1);
            setTagNoNull(TagW.SamplesPerPixel, samplesPerPixel);
            banded =
                samplesPerPixel > 1
                    && DicomMediaUtils.getIntegerFromDicomElement(header, Tag.PlanarConfiguration, 0) != 0;
            dataType =
                bitsAllocated <= 8 ? DataBuffer.TYPE_BYTE : pixelRepresentation != 0 ? DataBuffer.TYPE_SHORT
                    : DataBuffer.TYPE_USHORT;
            if (bitsAllocated > 16 && samplesPerPixel == 1) {
                dataType = DataBuffer.TYPE_INT;
            }
            String photometricInterpretation = header.getString(Tag.PhotometricInterpretation, "MONOCHROME2");
            pmi = PhotometricInterpretation.fromString(photometricInterpretation);
            setTagNoNull(TagW.PresentationLUTShape, header.getString(Tag.PresentationLUTShape));
            setTagNoNull(TagW.PhotometricInterpretation, photometricInterpretation);
            setTag(TagW.MonoChrome,
                samplesPerPixel == 1 && !"PALETTE COLOR".equalsIgnoreCase(photometricInterpretation)); //$NON-NLS-1$

            setTagNoNull(TagW.Rows, DicomMediaUtils.getIntegerFromDicomElement(header, Tag.Rows, 0));
            setTagNoNull(TagW.Columns, DicomMediaUtils.getIntegerFromDicomElement(header, Tag.Columns, 0));

            setTagNoNull(TagW.PixelPaddingValue,
                DicomMediaUtils.getIntPixelValue(header, Tag.PixelPaddingValue, pixelRepresentation != 0, bitsStored));
            setTagNoNull(TagW.PixelPaddingRangeLimit, DicomMediaUtils.getIntPixelValue(header,
                Tag.PixelPaddingRangeLimit, pixelRepresentation != 0, bitsStored));

            /*
             * 
             * For overlays encoded in Overlay Data Element (60xx,3000), Overlay Bits Allocated (60xx,0100) is always 1
             * and Overlay Bit Position (60xx,0102) is always 0.
             * 
             * @see - Dicom Standard 2011 - PS 3.5 § 8.1.2 Overlay data encoding of related data elements
             */
            if (header.getInt(Tag.OverlayBitsAllocated, 0) > 1 && bitsStored < bitsAllocated
                && dataType >= DataBuffer.TYPE_BYTE && dataType < DataBuffer.TYPE_INT) {
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
                    Float slopeVal = (Float) tags.get(TagW.RescaleSlope);
                    if (slopeVal == null) {
                        slopeVal = 1.0f;
                        // Set valid modality LUT values
                        Float ri = (Float) tags.get(TagW.RescaleIntercept);
                        String rt = (String) tags.get(TagW.RescaleType);
                        tags.put(TagW.RescaleIntercept, ri == null ? 0.0f : ri);
                        tags.put(TagW.RescaleType, rt == null ? "US" : rt);
                    }
                    // Divide pixel value by (2 ^ rightBit) => remove right bits
                    slopeVal /= 1 << (high - bitsStored);
                    tags.put(TagW.RescaleSlope, slopeVal);
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
    public PlanarImage getMediaFragment(MediaElement<PlanarImage> media) throws Exception {
        if (media != null && media.getKey() instanceof Integer && isReadableDicom()) {
            int frame = (Integer) media.getKey();
            if (frame >= 0 && frame < numberOfFrame && hasPixel) {
                // read as tiled rendered image
                LOGGER.debug("read dicom image frame: {} sopUID: {}", frame, tags.get(TagW.SOPInstanceUID)); //$NON-NLS-1$
                RenderedImage buffer = null;
                //                if ("1.2.840.10008.1.2.4.94".equals(tsuid)) { //$NON-NLS-1$
                // if (jpipReader == null) {
                // // TODO change JPIP reader
                // // ImageReaderFactory f = ImageReaderFactory.getInstance();
                // // jpipReader = f.getReaderForTransferSyntax(tsuid);
                // }
                // MediaElement[] elements = getMediaElement();
                // if (elements != null && elements.length > frame) {
                // jpipReader.setInput(elements);
                // buffer = jpipReader.readAsRenderedImage(frame, null);
                // }
                // } else {
                String path = (String) media.getTagValue(TagW.TiledImagePath);
                if (StringUtil.hasText(path)) {
                    buffer = ImageFiler.readTiledCacheImage(new File(path));
                }
                if (buffer == null) {
                    buffer = readAsRenderedImage(frame, null);

                    // File file = ImageFiler.cacheTiledImage(getValidImage(buffer), media);
                    // if (file != null) {
                    // System.gc();
                    // buffer = ImageFiler.readTiledCacheImage(file);
                    // }
                }
                // }
                return getValidImage(buffer, media);
            }
        }
        return null;
    }

    private PlanarImage getValidImage(RenderedImage buffer, MediaElement<PlanarImage> media) {
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

            Integer overlayBitMask = (Integer) getTagValue(TagW.OverlayBitMask);
            if (overlayBitMask != null) {
                if (media.getTagValue(TagW.OverlayBurninData) == null) {
                    Attributes ds = getDicomObject();
                    int[] overlayGroupOffsets = Overlays.getActiveOverlayGroupOffsets(ds, 0xffff);
                    byte[][] overlayData = new byte[overlayGroupOffsets.length][];
                    Raster raster = buffer.getData();
                    for (int i = 0; i < overlayGroupOffsets.length; i++) {
                        overlayData[i] = OverlayUtils.extractOverlay(overlayGroupOffsets[i], raster, ds);
                    }
                    if (overlayGroupOffsets.length > 0) {
                        setTagNoNull(TagW.OverlayBurninData, overlayData);
                    }
                }
                // Set to 0 all bits outside bitStored
                img = AndConstDescriptor.create(img, new int[] { overlayBitMask }, null);
            }

            // Convert images with PaletteColorModel to RGB model
            if (img.getColorModel() instanceof PaletteColorModel) {
                Attributes ds = getDicomObject();
                if (ds != null) {
                    int[] rDesc = DicomImageUtils.lutDescriptor(ds, Tag.RedPaletteColorLookupTableDescriptor);
                    int[] gDesc = DicomImageUtils.lutDescriptor(ds, Tag.GreenPaletteColorLookupTableDescriptor);
                    int[] bDesc = DicomImageUtils.lutDescriptor(ds, Tag.BluePaletteColorLookupTableDescriptor);
                    byte[] r =
                        DicomImageUtils.lutData(ds, rDesc, Tag.RedPaletteColorLookupTableData,
                            Tag.SegmentedRedPaletteColorLookupTableData);
                    byte[] g =
                        DicomImageUtils.lutData(ds, gDesc, Tag.GreenPaletteColorLookupTableData,
                            Tag.SegmentedGreenPaletteColorLookupTableData);
                    byte[] b =
                        DicomImageUtils.lutData(ds, bDesc, Tag.BluePaletteColorLookupTableData,
                            Tag.SegmentedBluePaletteColorLookupTableData);
                    LookupTableJAI lut = new LookupTableJAI(new byte[][] { r, g, b });

                    // Replace the original image with the RGB image.
                    img = JAI.create("lookup", img, lut); //$NON-NLS-1$
                }
            }
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
                        if (modality != null) {
                            if ("KO".equals(modality)) {
                                image = new MediaElement[1];
                                image[0] = new KOSpecialElement(this);
                            } else if ("PR".equals(modality)) {
                                image = new MediaElement[1];
                                image[0] = new PRSpecialElement(this);
                            } else if ("SR".equals(modality)) {
                                image = new MediaElement[1];
                                image[0] = new DicomSpecialElement(this);
                            }
                        }
                        if (image == null) {
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
        Series<?> series = null;
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
                    DicomMediaUtils.buildLUTs(tagList);
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
                ts = NO_VALUE;
            }
            desc[1] = Messages.getString("DicomMediaIO.msg_no_reader") + ts; //$NON-NLS-1$
        }
        return desc;
    }

    public Series<?> buildSeries(String seriesUID) {
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
    public int getHeight(int frameIndex) throws IOException {
        checkIndex(frameIndex);
        return (Integer) getTagValue(TagW.Rows);
    }

    @Override
    public int getWidth(int frameIndex) throws IOException {
        checkIndex(frameIndex);
        return (Integer) getTagValue(TagW.Columns);
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
        // Index changes from 1 to 0 as the Dicom frames start to count at 1
        // ImageReader expects the first frame to be 0.
        // initImageReader(0);
        // return reader.getImageTypes(0);

        // TODO 1.2.840.10008.1.2.4.95 (DICOM JPIP Referenced Deflate Transfer Syntax)
        //        if ("1.2.840.10008.1.2.4.94".equals(tsuid)) { //$NON-NLS-1$
        // MediaElement[] elements = getMediaElement();
        // // TODO handle frame
        // if (elements != null && elements.length > 0) {
        // reader.setInput(elements[0]);
        // }
        // }

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

    private ImageInputStreamImpl iisOfFrame(int frameIndex) throws IOException {
        SegmentedInputImageStream siis = new SegmentedInputImageStream(iis, pixeldataFragments, frameIndex);
        return patchJpegLS != null ? new PatchJPEGLSImageInputStream(siis, patchJpegLS) : siis;
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
                decompressor.setInput(iisOfFrame(frameIndex));

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Start decompressing frame #" + (frameIndex + 1));
                }
                Raster wr =
                    pmi.decompress() == pmi && decompressor.canReadRaster() ? decompressor.readRaster(0,
                        decompressParam(param)) : decompressor.read(0, decompressParam(param)).getRaster();
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Finished decompressing frame #" + (frameIndex + 1));
                }
                return wr;
            }
            iis.seek(pixeldata.offset + frameIndex * frameLength);
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

    private ImageReadParam decompressParam(ImageReadParam param) {
        ImageReadParam decompressParam = decompressor.getDefaultReadParam();
        ImageTypeSpecifier imageType = param.getDestinationType();
        BufferedImage dest = param.getDestination();
        if (isRLELossless() && imageType == null && dest == null) {
            imageType = createImageType(bitsStored, dataType, true);
        }
        decompressParam.setDestinationType(imageType);
        decompressParam.setDestination(dest);
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
                decompressor.setInput(iisOfFrame(frameIndex));
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Start decompressing frame #" + (frameIndex + 1));
                }
                BufferedImage bi = decompressor.read(0, decompressParam(param));
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Finished decompressing frame #" + (frameIndex + 1));
                }
                return bi;
            } else {
                raster = (WritableRaster) readRaster(frameIndex, param);
            }

            // ColorModel cm;
            // if (pmi.isMonochrome()) {
            // int[] overlayGroupOffsets = getActiveOverlayGroupOffsets(param);
            // byte[][] overlayData = new byte[overlayGroupOffsets.length][];
            // for (int i = 0; i < overlayGroupOffsets.length; i++) {
            // overlayData[i] = extractOverlay(overlayGroupOffsets[i], raster);
            // }
            // cm = createColorModel(8, DataBuffer.TYPE_BYTE);
            // SampleModel sm = createSampleModel(DataBuffer.TYPE_BYTE, false);
            // raster = applyLUTs(raster, frameIndex, param, sm, 8);
            // for (int i = 0; i < overlayGroupOffsets.length; i++) {
            // applyOverlay(overlayGroupOffsets[i],
            // raster, frameIndex, param, 8, overlayData[i]);
            // }
            // } else {
            // cm = createColorModel(bitsStored, dataType);
            // }

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

            RenderedImage bi = null;
            if (decompressor != null) {
                decompressor.setInput(iisOfFrame(frameIndex));
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Start decompressing frame #" + (frameIndex + 1));
                }
                bi = decompressor.readAsRenderedImage(0, decompressParam(param));
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Finished decompressing frame #" + (frameIndex + 1));
                }
            } else {
                // Rewrite image with subsample model (cannot not be display as Renderedimage)
                if (pmi.isSubSambled()) {
                    // TODO improve this
                    WritableRaster raster = (WritableRaster) readRaster(frameIndex, param);
                    ColorModel cm = createColorModel(bitsStored, dataType);
                    ColorModel cmodel =
                        new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), new int[] { 8, 8, 8 },
                            false, // has alpha
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
                            float[] fba =
                                new float[] { (ba[0] & 0xFF) / 255f, (ba[1] & 0xFF) / 255f, (ba[2] & 0xFF) / 255f };
                            float[] rgb = cs.toRGB(fba);
                            ba[0] = (byte) (rgb[0] * 255);
                            ba[1] = (byte) (rgb[1] * 255);
                            ba[2] = (byte) (rgb[2] * 255);
                            rasterDst.setDataElements(j, i, ba);
                        }
                    }
                    bi = new BufferedImage(cmodel, rasterDst, false, null);
                    //                    File imgCacheFile = File.createTempFile("tiled_", ".tif", AbstractProperties.FILE_CACHE_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                    // ImageFiler.writeTIFF(imgCacheFile, bi, false, false, false);
                    // ImageFiler.writeJPG(File.createTempFile("raw", "jpg", AbstractProperties.APP_TEMP_DIR), bi,
                    // 1.0f);
                } else {
                    ImageReader reader = initRawImageReader();
                    bi = reader.readAsRenderedImage(frameIndex, param);
                }
            }
            return validateSignedShortDataBuffer(bi);
        } finally {
            readingImage = false;
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

        // TODO test with all decoders (works with raw decoder)
        if (source != null && dataType == DataBuffer.TYPE_SHORT
            && source.getSampleModel().getDataType() == DataBuffer.TYPE_SHORT && (highBit + 1) < bitsAllocated) {
            source = RectifySignedShortDataDescriptor.create(source, new int[] { highBit + 1 }, null);
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

    public Attributes getDicomObject() {
        try {
            DicomMetaData md = readMetaData(false);
            return md.getAttributes();
        } catch (Exception e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.error("Cannot read DICOM:", e);
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
                throw new IllegalArgumentException("Input not an ImageInputStream!");
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
        patchJpegLS = null;
    }

    private void checkIndex(int frameIndex) {
        if (frameIndex < 0 || frameIndex >= numberOfFrame) {
            throw new IndexOutOfBoundsException("imageIndex: " + frameIndex);
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
                throw new IllegalStateException("Input not set!");
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
            dis.setBulkDataDescriptor(BulkDataDescriptor.DEFAULT);
            dis.setURI(uri.toString());
            // dis.setURI("java:iis"); // avoid copy of pixeldata to temporary file
            Attributes fmi = dis.readFileMetaInformation();
            Attributes ds = dis.readDataset(-1, -1);
            if (fmi == null) {
                fmi = ds.createFileMetaInformation(dis.getTransferSyntax());
            }
            DicomMetaData metadata = new DicomMetaData(fmi, ds);
            Object pixdata = ds.getValue(Tag.PixelData, pixeldataVR);

            // TODO skip private tags in dcm4che3?
            // if (isSkipLargePrivate()) {
            // ih = new SizeSkipInputHandler(ih);
            // }

            // TODO fix non conformed compressed image
            // while (dis.tag() == 0xFFFCFFFC) {
            // dis.readBytes(dis.valueLength());
            // dis.readDicomObject(ds, -1);
            // }

            if (pixdata != null) {
                tsuid = dis.getTransferSyntax();
                numberOfFrame = ds.getInt(Tag.NumberOfFrames, 1);
                hasPixel = ds.getInt(Tag.BitsStored, ds.getInt(Tag.BitsAllocated, 0)) > 0;

                if (readImageAfter && !tsuid.startsWith("1.2.840.10008.1.2.4.10") && hasPixel) {

                    if (pixdata instanceof BulkData) {
                        int width = (Integer) getTagValue(TagW.Columns);
                        int height = (Integer) getTagValue(TagW.Rows);
                        int samples = (Integer) getTagValue(TagW.SamplesPerPixel);
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
                        ImageReaderParam param = ImageReaderFactory.getImageReaderParam(tsuid);
                        if (param == null) {
                            throw new IOException("Unsupported Transfer Syntax: " + tsuid);
                        }
                        this.decompressor = ImageReaderFactory.getImageReader(param);
                        // this.patchJpegLS = param.patchJPEGLS;
                        this.pixeldataFragments = (Fragments) pixdata;
                    }
                }
            }

            // if (dis.tag() == Tag.PixelData) {
            // if (numberOfFrame == 0) {
            // numberOfFrame = 1;
            // }
            // swapByteOrder = bigEndian && dis.vr() == VR.OW && dataType == DataBuffer.TYPE_BYTE;
            // if (swapByteOrder && banded) {
            // throw new UnsupportedOperationException(
            // "Big Endian color-by-plane with Pixel Data VR=OW not implemented");
            // }
            // pixelDataPos = dis.getStreamPosition();
            // pixelDataLen = dis.valueLength();
            //
            // compressed = pixelDataLen == -1;
            // if (!compressed && tsuid.startsWith("1.2.840.10008.1.2.4")) {
            // // Corrupted image where missing the encapsulated part for the identification the compressed dataset
            // compressed = true;
            // }
            // if (compressed) {
            // ImageReaderFactory f = ImageReaderFactory.getInstance();
            // LOGGER.debug("Transfer syntax for image is " + tsuid + " with image reader class " + f.getClass());
            // f.adjustDatasetForTransferSyntax(ds, tsuid);
            // clampPixelValues = allocated == 16 && bitsStored < 12 && UID.JPEGExtended24.equals(tsuid);
            // }
            // } else if (ds.getString(Tag.PixelDataProviderURL) != null) {
            // if (numberOfFrame == 0) {
            // numberOfFrame = 1;
            // compressed = true;
            // }
            // }

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
        return pmi.createSampleModel(dataType, (Integer) getTagValue(TagW.Columns), (Integer) getTagValue(TagW.Rows),
            (Integer) getTagValue(TagW.SamplesPerPixel), banded);
    }

    private ImageTypeSpecifier createImageType(int bits, int dataType, boolean banded) {
        return new ImageTypeSpecifier(createColorModel(bits, dataType), createSampleModel(dataType, banded));
    }

    private ColorModel createColorModel(int bits, int dataType) {
        return pmi.createColorModel(bits, dataType, getDicomObject());
    }

}
