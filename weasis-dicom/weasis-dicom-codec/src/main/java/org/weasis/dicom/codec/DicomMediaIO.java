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

import java.awt.image.DataBuffer;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.media.jai.JAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.operator.NullDescriptor;

import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.VR;
import org.dcm4che2.imageio.ImageReaderFactory;
import org.dcm4che2.imageio.plugins.dcm.DicomStreamMetaData;
import org.dcm4che2.imageioimpl.plugins.dcm.DicomImageReader;
import org.dcm4che2.imageioimpl.plugins.dcm.DicomImageReaderSpi;
import org.dcm4che2.io.DicomInputStream;
import org.dcm4che2.io.DicomOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.image.op.RectifyUShortToShortDataDescriptor;
import org.weasis.core.api.image.util.LayoutUtil;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.codec.geometry.ImageOrientation;

import com.sun.media.jai.util.ImageUtil;

public class DicomMediaIO extends DicomImageReader implements MediaReader<PlanarImage> {

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(DicomMediaIO.class);

    public static final String MIMETYPE = "application/dicom"; //$NON-NLS-1$
    public static final String IMAGE_MIMETYPE = "image/dicom"; //$NON-NLS-1$
    public static final String SERIES_VIDEO_MIMETYPE = "video/dicom"; //$NON-NLS-1$
    public static final String SERIES_MIMETYPE = "series/dicom"; //$NON-NLS-1$
    public static final String SERIES_PR_MIMETYPE = "pr/dicom"; //$NON-NLS-1$
    public static final String SERIES_KO_MIMETYPE = "ko/dicom"; //$NON-NLS-1$
    public static final String SERIES_SR_MIMETYPE = "sr/dicom"; //$NON-NLS-1$
    public static final String SERIES_ENCAP_DOC_MIMETYPE = "encap/dicom"; //$NON-NLS-1$
    public static final String SERIES_XDSI = "xds-i/dicom"; //$NON-NLS-1$
    public static final Codec CODEC = BundleTools.getCodec(DicomMediaIO.MIMETYPE, DicomCodec.NAME);
    private final static DicomImageReaderSpi readerSpi = new DicomImageReaderSpi();
    private final URI uri;
    private DicomObject dicomObject = null;
    private int numberOfFrame;
    private int stored;
    private final HashMap<TagW, Object> tags;
    private MediaElement[] image = null;
    private ImageInputStream imageStream = null;
    private volatile String mimeType;

    private ImageReader jpipReader;

    public DicomMediaIO(URI uri) {
        super(readerSpi);
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

    public boolean readMediaTags() {
        if (dicomObject == null && uri != null) {
            try {
                if (uri.toString().startsWith("file:/")) { //$NON-NLS-1$
                    imageStream = ImageIO.createImageInputStream(new File(uri));
                } else {
                    // TODO test if url stream is closed on reset !
                    imageStream = ImageIO.createImageInputStream(uri.toURL().openStream());
                }

                setInput(imageStream, false, false);
                if (getStreamMetadata() instanceof DicomStreamMetaData) {
                    dicomObject = ((DicomStreamMetaData) getStreamMetadata()).getDicomObject();
                }
                // Exclude DICOMDIR
                if (dicomObject == null
                    || dicomObject.getString(Tag.MediaStorageSOPClassUID, "").equals("1.2.840.10008.1.3.10")) { //$NON-NLS-1$ //$NON-NLS-2$
                    close();
                    return false;
                }

                stored = dicomObject.getInt(Tag.BitsStored, dicomObject.getInt(Tag.BitsAllocated, 0));
                if (stored > 0) {
                    numberOfFrame = getNumImages(false);
                    if ("1.2.840.10008.1.2.4.100".equals(dicomObject.getString(Tag.TransferSyntaxUID))) { //$NON-NLS-1$
                        mimeType = SERIES_VIDEO_MIMETYPE;
                    } else {
                        mimeType = IMAGE_MIMETYPE;
                    }
                } else {
                    boolean special = setDicomSpecialType(dicomObject);
                    if (!special) {
                        close();
                        return false;
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                close();
                return false;
            }
            writeInstanceTags();
        }
        return true;
    }

    @Override
    protected void initImageReader(int imageIndex) throws IOException {
        super.initImageReader(imageIndex);
        if ("1.2.840.10008.1.2.4.94".equals(tsuid)) {
            setTagNoNull(TagW.PixelDataProviderURL, dicomObject.getString(Tag.PixelDataProviderURL));
            MediaElement[] elements = getMediaElement();
            // TODO handle frame
            if (elements != null && elements.length > 0) {
                reader.setInput(elements[0]);
            }
        }
    }

    private boolean setDicomSpecialType(DicomObject dicom) {
        String modality = dicom.getString(Tag.Modality);
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
                String encap = dicomObject.getString(Tag.MIMETypeOfEncapsulatedDocument);
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

    public static void setTag(HashMap<TagW, Object> tags, TagW tag, Object value) {
        if (tag != null) {
            tags.put(tag, value);
        }
    }

    public static void setTagNoNull(HashMap<TagW, Object> tags, TagW tag, Object value) {
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

        // Patient Group
        if (TagW.PatientPseudoUID.equals(group.getTagID())) {
            // -------- Mandatory Tags --------
            group.setTag(TagW.PatientID, getTagValue(TagW.PatientID));
            group.setTag(TagW.PatientName, getTagValue(TagW.PatientName));
            // -------- End of Mandatory Tags --------

            group.setTagNoNull(TagW.PatientBirthDate, getTagValue(TagW.PatientBirthDate));
            group.setTagNoNull(TagW.PatientBirthTime, getDateFromDicomElement(header, Tag.PatientBirthTime, null));
            // Sex attribute can have the following values: M(male), F(female), or O(other)
            String val = header.getString(Tag.PatientSex, "O"); //$NON-NLS-1$
            group
                .setTag(
                    TagW.PatientSex,
                    val.startsWith("F") ? Messages.getString("DicomMediaIO.female") : val.startsWith("M") ? Messages.getString("DicomMediaIO.Male") : Messages.getString("DicomMediaIO.other")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            group.setTagNoNull(TagW.IssuerOfPatientID, header.getString(Tag.IssuerOfPatientID));
            group.setTagNoNull(TagW.PatientComments, header.getString(Tag.PatientComments));
        }
        // Study Group
        else if (TagW.StudyInstanceUID.equals(group.getTagID())) {
            // -------- Mandatory Tags --------
            // StudyInstanceUID is the unique identifying tag for this study group
            // -------- End of Mandatory Tags --------

            group.setTagNoNull(TagW.StudyID, header.getString(Tag.StudyID));
            group.setTagNoNull(TagW.StudyDate, getDateFromDicomElement(header, Tag.StudyDate, null));
            group.setTagNoNull(TagW.StudyTime, getDateFromDicomElement(header, Tag.StudyTime, null));
            group.setTagNoNull(TagW.StudyDescription, header.getString(Tag.StudyDescription));

            group.setTagNoNull(TagW.AccessionNumber, header.getString(Tag.AccessionNumber));
            group.setTagNoNull(TagW.ModalitiesInStudy, header.getString(Tag.ModalitiesInStudy));
            group.setTagNoNull(TagW.NumberOfStudyRelatedInstances,
                getIntegerFromDicomElement(header, Tag.NumberOfStudyRelatedInstances, null));
            group.setTagNoNull(TagW.NumberOfStudyRelatedSeries,
                getIntegerFromDicomElement(header, Tag.NumberOfStudyRelatedSeries, null));

            group.setTagNoNull(TagW.StudyStatusID, header.getString(Tag.StudyStatusID));
            // TODO sequence: define data structure
            group.setTagNoNull(TagW.ProcedureCodeSequence, header.get(Tag.ProcedureCodeSequence));
        }
        // Series Group
        else if (TagW.SubseriesInstanceUID.equals(group.getTagID())) {
            // -------- Mandatory Tags --------
            // SubseriesInstanceUID is the unique identifying tag for this series group
            group.setTag(TagW.SeriesInstanceUID, getTagValue(TagW.SeriesInstanceUID));
            group.setTag(TagW.Modality, getTagValue(TagW.Modality));
            // -------- End of Mandatory Tags --------

            group.setTagNoNull(TagW.SeriesDate,
                header.getDate(Tag.SeriesDate, getDateFromDicomElement(header, Tag.StudyDate, null)));

            group.setTagNoNull(TagW.SeriesDescription, header.getString(Tag.SeriesDescription));
            group.setTagNoNull(TagW.RetrieveAETitle, header.getString(Tag.RetrieveAETitle));
            group.setTagNoNull(TagW.ReferringPhysicianName, header.getString(Tag.ReferringPhysicianName));
            group.setTagNoNull(TagW.InstitutionName, header.getString(Tag.InstitutionName));
            group.setTagNoNull(TagW.InstitutionalDepartmentName, header.getString(Tag.InstitutionalDepartmentName));
            group.setTagNoNull(TagW.StationName, header.getString(Tag.StationName));
            group.setTagNoNull(TagW.Manufacturer, header.getString(Tag.Manufacturer));
            group.setTagNoNull(TagW.ManufacturerModelName, header.getString(Tag.ManufacturerModelName));
            // TODO sequence: define data structure
            group.setTagNoNull(TagW.ReferencedPerformedProcedureStepSequence,
                header.get(Tag.ReferencedPerformedProcedureStepSequence));
            group.setTagNoNull(TagW.SeriesNumber, getIntegerFromDicomElement(header, Tag.SeriesNumber, null));
            group.setTagNoNull(TagW.PreferredPlaybackSequencing,
                getIntegerFromDicomElement(header, Tag.PreferredPlaybackSequencing, null));
            group.setTagNoNull(
                TagW.CineRate,
                getIntegerFromDicomElement(header, Tag.CineRate,
                    getIntegerFromDicomElement(header, Tag.RecommendedDisplayFrameRate, null)));
            group.setTagNoNull(TagW.KVP, getFloatFromDicomElement(header, Tag.KVP, null));
            group.setTagNoNull(TagW.Laterality, header.getString(Tag.Laterality));
            group.setTagNoNull(TagW.BodyPartExamined, header.getString(Tag.BodyPartExamined));
            group.setTagNoNull(TagW.ReferencedImageSequence, header.get(Tag.ReferencedImageSequence));
            group.setTagNoNull(TagW.FrameOfReferenceUID, header.getString(Tag.FrameOfReferenceUID));
            group.setTagNoNull(TagW.NumberOfSeriesRelatedInstances,
                getIntegerFromDicomElement(header, Tag.NumberOfSeriesRelatedInstances, null));
            group.setTagNoNull(TagW.PerformedProcedureStepStartDate,
                getDateFromDicomElement(header, Tag.PerformedProcedureStepStartDate, null));
            group.setTagNoNull(TagW.PerformedProcedureStepStartTime,
                getDateFromDicomElement(header, Tag.PerformedProcedureStepStartTime, null));
            // TODO sequence: define data structure
            group.setTagNoNull(TagW.RequestAttributesSequence, header.get(Tag.RequestAttributesSequence));

            // Information for series ToolTips
            group.setTagNoNull(TagW.PatientName, getTagValue(TagW.PatientName));
            group.setTagNoNull(TagW.StudyDescription, header.getString(Tag.StudyDescription));

            if ("1.2.840.10008.1.2.4.94".equals(tsuid)) {
                MediaElement[] elements = getMediaElement();
                if (elements != null) {
                    for (MediaElement mediaElement : elements) {
                        mediaElement.setTag(TagW.ExplorerModel, group.getTagValue(TagW.ExplorerModel));
                    }
                }

            }
        }
    }

    private void writeInstanceTags() {
        if (dicomObject != null) {
            // -------- Mandatory Tags --------
            // Tags for identifying group (Patient, Study, Series)
            setTag(TagW.PatientID, dicomObject.getString(Tag.PatientID, Messages.getString("DicomMediaIO.unknown"))); //$NON-NLS-1$
            String name = dicomObject.getString(Tag.PatientName, Messages.getString("DicomMediaIO.unknown")); //$NON-NLS-1$
            if (name.trim().equals("")) { //$NON-NLS-1$
                name = Messages.getString("DicomMediaIO.unknown"); //$NON-NLS-1$
            }
            name = name.replace("^", " "); //$NON-NLS-1$ //$NON-NLS-2$
            setTag(TagW.PatientName, name);
            setTagNoNull(TagW.PatientBirthDate, getDateFromDicomElement(dicomObject, Tag.PatientBirthDate, null));
            // Identifier for the patient. Tend to be unique.
            // TODO set preferences for what is PatientUID
            setTag(TagW.PatientPseudoUID,
                getTagValue(TagW.PatientID).toString() + TagW.formatDate((Date) getTagValue(TagW.PatientBirthDate)));
            setTag(TagW.StudyInstanceUID,
                dicomObject.getString(Tag.StudyInstanceUID, Messages.getString("DicomMediaIO.unknown"))); //$NON-NLS-1$
            setTag(TagW.SeriesInstanceUID,
                dicomObject.getString(Tag.SeriesInstanceUID, Messages.getString("DicomMediaIO.unknown"))); //$NON-NLS-1$
            setTag(TagW.Modality, dicomObject.getString(Tag.Modality, Messages.getString("DicomMediaIO.unknown"))); //$NON-NLS-1$
            setTag(TagW.InstanceNumber, dicomObject.getInt(Tag.InstanceNumber, TagW.AppID.incrementAndGet()));
            setTag(TagW.SOPInstanceUID,
                dicomObject.getString(Tag.SOPInstanceUID, getTagValue(TagW.InstanceNumber).toString()));
            // -------- End of Mandatory Tags --------

            writeOnlyinstance(dicomObject);
            writeSharedFunctionalGroupsSequence(dicomObject);
            writePerFrameFunctionalGroupsSequence(tags, dicomObject, 0);

            validateDicomImageValues(tags);
            computeSlicePositionVector(tags);
        }
    }

    private void writeSharedFunctionalGroupsSequence(DicomObject dicomObject) {

        if (dicomObject != null) {
            DicomElement seq = dicomObject.get(Tag.SharedFunctionalGroupsSequence);
            if (seq != null && seq.vr() == VR.SQ) {
                DicomObject dcm = null;
                try {
                    dcm = seq.getDicomObject(0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (dcm != null) {
                    writeFunctionalGroupsSequence(tags, dcm);
                }
            }
        }
    }

    private void writeOnlyinstance(DicomObject dicomObject) {
        if (dicomObject != null && tags != null) {
            // Instance tags
            setTagNoNull(TagW.ImageType, dicomObject.getString(Tag.ImageType));
            setTagNoNull(TagW.ImageComments, dicomObject.getString(Tag.ImageComments));
            setTagNoNull(TagW.ContrastBolusAgent, dicomObject.getString(Tag.ContrastBolusAgent));
            setTagNoNull(TagW.TransferSyntaxUID, dicomObject.getString(Tag.TransferSyntaxUID));
            setTagNoNull(TagW.SOPClassUID, dicomObject.getString(Tag.SOPClassUID));
            setTagNoNull(TagW.ScanningSequence, dicomObject.getString(Tag.ScanningSequence));
            setTagNoNull(TagW.SequenceVariant, dicomObject.getString(Tag.SequenceVariant));
            setTagNoNull(TagW.ScanOptions, dicomObject.getString(Tag.ScanOptions));
            setTagNoNull(TagW.RepetitionTime, getFloatFromDicomElement(dicomObject, Tag.RepetitionTime, null));
            setTagNoNull(TagW.EchoTime, getFloatFromDicomElement(dicomObject, Tag.EchoTime, null));
            setTagNoNull(TagW.InversionTime, getFloatFromDicomElement(dicomObject, Tag.InversionTime, null));
            setTagNoNull(TagW.EchoNumbers, getIntegerFromDicomElement(dicomObject, Tag.EchoNumbers, null));
            setTagNoNull(TagW.GantryDetectorTilt, getFloatFromDicomElement(dicomObject, Tag.GantryDetectorTilt, null));
            setTagNoNull(TagW.ConvolutionKernel, dicomObject.getString(Tag.ConvolutionKernel));
            setTagNoNull(TagW.FlipAngle, getFloatFromDicomElement(dicomObject, Tag.FlipAngle, null));
            setTagNoNull(TagW.SliceLocation, getFloatFromDicomElement(dicomObject, Tag.SliceLocation, null));
            setTagNoNull(TagW.SliceThickness, getFloatFromDicomElement(dicomObject, Tag.SliceThickness, null));
            setTagNoNull(TagW.AcquisitionDate, getDateFromDicomElement(dicomObject, Tag.AcquisitionDate, null));
            setTagNoNull(TagW.AcquisitionTime, getDateFromDicomElement(dicomObject, Tag.AcquisitionTime, null));

            setTagNoNull(TagW.ImagePositionPatient, dicomObject.getDoubles(Tag.ImagePositionPatient, (double[]) null));
            setTagNoNull(TagW.ImageOrientationPatient,
                dicomObject.getDoubles(Tag.ImageOrientationPatient, (double[]) null));
            setTagNoNull(
                TagW.ImageOrientationPlane,
                ImageOrientation
                    .makeImageOrientationLabelFromImageOrientationPatient((double[]) getTagValue(TagW.ImageOrientationPatient)));

            setTagNoNull(TagW.ImagerPixelSpacing, dicomObject.getDoubles(Tag.ImagerPixelSpacing, (double[]) null));
            setTagNoNull(TagW.PixelSpacing, dicomObject.getDoubles(Tag.PixelSpacing, (double[]) null));
            setTagNoNull(TagW.PixelSpacingCalibrationDescription,
                dicomObject.getString(Tag.PixelSpacingCalibrationDescription));

            setTagNoNull(TagW.WindowWidth, getFloatFromDicomElement(dicomObject, Tag.WindowWidth, null));
            setTagNoNull(TagW.WindowCenter, getFloatFromDicomElement(dicomObject, Tag.WindowCenter, null));

            setTagNoNull(TagW.RescaleSlope, getFloatFromDicomElement(dicomObject, Tag.RescaleSlope, null));
            setTagNoNull(TagW.RescaleIntercept, getFloatFromDicomElement(dicomObject, Tag.RescaleIntercept, null));
            setTagNoNull(TagW.RescaleType, dicomObject.getString(Tag.RescaleType));
            setTagNoNull(TagW.Units, dicomObject.getString(Tag.Units));

            setTagNoNull(TagW.SmallestImagePixelValue,
                getIntegerFromDicomElement(dicomObject, Tag.SmallestImagePixelValue, null));
            setTagNoNull(TagW.LargestImagePixelValue,
                getIntegerFromDicomElement(dicomObject, Tag.LargestImagePixelValue, null));
            setTagNoNull(TagW.PixelPaddingValue, getIntegerFromDicomElement(dicomObject, Tag.PixelPaddingValue, null));
            setTagNoNull(TagW.NumberOfFrames, getIntegerFromDicomElement(dicomObject, Tag.NumberOfFrames, null));
            setTagNoNull(TagW.PixelPaddingRangeLimit,
                getIntegerFromDicomElement(dicomObject, Tag.PixelPaddingRangeLimit, null));
            setTagNoNull(TagW.OverlayRows, getIntegerFromDicomElement(dicomObject, Tag.OverlayRows, null));

            Integer samplesPerPixel = getIntegerFromDicomElement(dicomObject, Tag.SamplesPerPixel, null);
            setTagNoNull(TagW.SamplesPerPixel, samplesPerPixel);
            String photometricInterpretation = dicomObject.getString(Tag.PhotometricInterpretation);
            setTagNoNull(TagW.PhotometricInterpretation, photometricInterpretation);
            if (samplesPerPixel != null) {
                setTag(TagW.MonoChrome,
                    samplesPerPixel == 1 && !"PALETTE COLOR".equalsIgnoreCase(photometricInterpretation)); //$NON-NLS-1$
            }

            setTagNoNull(TagW.Rows, getIntegerFromDicomElement(dicomObject, Tag.Rows, null));
            setTagNoNull(TagW.Columns, getIntegerFromDicomElement(dicomObject, Tag.Columns, null));
            setTagNoNull(TagW.BitsAllocated, getIntegerFromDicomElement(dicomObject, Tag.BitsAllocated, null));
            setTagNoNull(TagW.BitsStored,
                getIntegerFromDicomElement(dicomObject, Tag.BitsStored, (Integer) getTagValue(TagW.BitsAllocated)));
            setTagNoNull(TagW.PixelRepresentation,
                getIntegerFromDicomElement(dicomObject, Tag.PixelRepresentation, null));

            setTagNoNull(TagW.MIMETypeOfEncapsulatedDocument, dicomObject.getString(Tag.MIMETypeOfEncapsulatedDocument));
        }
    }

    private static void validateDicomImageValues(HashMap<TagW, Object> tagList) {
        if (tagList != null) {
            Float window = (Float) tagList.get(TagW.WindowWidth);
            Float level = (Float) tagList.get(TagW.WindowCenter);
            if (window != null && level != null) {
                Integer minValue = (Integer) tagList.get(TagW.SmallestImagePixelValue);
                Integer maxValue = (Integer) tagList.get(TagW.LargestImagePixelValue);
                // Test if DICOM min and max pixel values are consistent
                if (minValue != null && maxValue != null) {
                    float min = pixel2rescale(tagList, minValue.floatValue());
                    float max = pixel2rescale(tagList, maxValue.floatValue());
                    // Empirical test
                    float low = level - window / 4.0f;
                    float high = level + window / 4.0f;
                    if (low < min || high > max) {
                        // Min and Max seems to be not consistent
                        // Remove them, it will search in min and max in pixel data
                        tagList.remove(TagW.SmallestImagePixelValue);
                        tagList.remove(TagW.LargestImagePixelValue);
                    }
                }
                Integer bitsStored = (Integer) tagList.get(TagW.BitsStored);
                if (bitsStored != null) {
                    if (window > (1 << bitsStored)) {
                        // Remove w/l values that are not consistent to the bits stored
                        tagList.remove(TagW.WindowCenter);
                        tagList.remove(TagW.WindowWidth);
                    }
                }
            }
        }
    }

    private static float pixel2rescale(HashMap<TagW, Object> tagList, float pixelValue) {
        if (tagList != null) {
            // Hounsfield units: hu
            // hu = pixelValue * rescale slope + intercept value
            Float slope = (Float) tagList.get(TagW.RescaleSlope);
            Float intercept = (Float) tagList.get(TagW.RescaleIntercept);
            if (slope != null || intercept != null) {
                return (pixelValue * (slope == null ? 1.0f : slope) + (intercept == null ? 0.0f : intercept));
            }
        }
        return pixelValue;
    }

    private static void computeSlicePositionVector(HashMap<TagW, Object> tagList) {
        double[] patientPos = (double[]) tagList.get(TagW.ImagePositionPatient);
        if (patientPos != null && patientPos.length == 3) {
            double[] imgOrientation =
                ImageOrientation.computeNormalVectorOfPlan((double[]) tagList.get(TagW.ImageOrientationPatient));
            if (imgOrientation != null) {
                double[] slicePosition = new double[3];
                slicePosition[0] = imgOrientation[0] * patientPos[0];
                slicePosition[1] = imgOrientation[1] * patientPos[1];
                slicePosition[2] = imgOrientation[2] * patientPos[2];
                setTag(tagList, TagW.SlicePosition, slicePosition);
            }
        }
    }

    private Date getDateFromDicomElement(DicomObject dicom, int tag, Date defaultValue) {
        DicomElement element = dicom.get(tag);
        if (element == null || element.isEmpty()) {
            return defaultValue;
        } else {
            try {
                return dicom.getDate(tag);
            } catch (Exception e) {
                // Value that not respect DICOM standard
                e.printStackTrace();
            }
        }
        return null;
    }

    private Float getFloatFromDicomElement(DicomObject dicom, int tag, Float defaultValue) {
        DicomElement element = dicom.get(tag);
        if (element == null || element.isEmpty()) {
            return defaultValue;
        } else {
            return dicom.getFloat(tag);
        }
    }

    private Integer getIntegerFromDicomElement(DicomObject dicom, int tag, Integer defaultValue) {
        DicomElement element = dicom.get(tag);
        if (element == null || element.isEmpty()) {
            return defaultValue;
        } else {
            return dicom.getInt(tag);
        }
    }

    private Double getDoubleFromDicomElement(DicomObject dicom, int tag, Double defaultValue) {
        DicomElement element = dicom.get(tag);
        if (element == null || element.isEmpty()) {
            return defaultValue;
        } else {
            return dicom.getDouble(tag);
        }
    }

    // public boolean readMediaTags(ImageInputStream iis) {
    // if (iis != null) {
    // try {
    // setInput(iis, false, false);
    // if (getStreamMetadata() instanceof DicomStreamMetaData) {
    // dicomObject = ((DicomStreamMetaData) getStreamMetadata()).getDicomObject();
    // }
    // // Exclude DICOMDIR
    // if (dicomObject == null
    // || dicomObject.getString(Tag.MediaStorageSOPClassUID, "").equals("1.2.840.10008.1.3.10")) {
    // return false;
    // }
    // if ("1.2.840.10008.1.2.4.100".equals(dicomObject.getString(Tag.TransferSyntaxUID))) {
    // video = true;
    // }
    // stored = dicomObject.getInt(Tag.BitsStored, dicomObject.getInt(Tag.BitsAllocated, 0));
    // if (stored > 0) {
    // numberOfFrame = getNumImages(false);
    // }
    // else {
    // return false;
    // }
    // }
    // catch (Exception e) {
    // e.printStackTrace();
    // return false;
    // }
    // writeInstanceTags();
    // }
    // return true;
    // }

    public boolean containTag(int id) {
        for (Iterator<TagW> it = tags.keySet().iterator(); it.hasNext();) {
            if (it.next().getId() == id) {
                return true;
            }
        }
        return false;
    }

    public int getStored() {
        return stored;
    }

    @Override
    public void reset() {
        // Reset must be called only after reading the header, because closing imageStream does not let through
        // getTile(x,y) read image data.
        // unlock file to be deleted on exit
        FileUtil.safeClose(imageStream);
        imageStream = null;
        dicomObject = null;
        super.reset();
    }

    // public boolean isVolumeImage() {
    // // Exclude DicomDir and DicomSR if (getStored() == 0)
    // return dicomObject.contains(Tag.ImageOrientationPatient) && stored != 0;
    // }

    public boolean writeDICOM(File destination) throws Exception {
        DicomOutputStream out = null;
        DicomInputStream dis = null;
        try {
            out = new DicomOutputStream(new BufferedOutputStream(new FileOutputStream(destination)));
            if (uri.toString().startsWith("file:/")) { //$NON-NLS-1$
                dis = new DicomInputStream(new File(uri));
            } else {
                dis = new DicomInputStream(uri.toURL().openStream());
            }
            // dis.setHandler(new StopTagInputHandler(Tag.PixelData));
            DicomObject dcm = dis.readDicomObject();
            out.writeDicomFile(dcm);
        } catch (IOException e) {
            e.printStackTrace();
            destination.delete();
            return false;
        } finally {
            FileUtil.safeClose(out);
            FileUtil.safeClose(dis);
        }
        return true;
    }

    public DicomObject getDicomObject() {
        if (dicomObject == null) {
            readMediaTags();
            DicomObject dcm = dicomObject;
            reset();
            return dcm;
        }
        return dicomObject;
    }

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public PlanarImage getMediaFragment(MediaElement<PlanarImage> media) throws Exception {
        if (media != null && media.getKey() instanceof Integer) {
            if (dicomObject == null) {
                readMediaTags();
            }
            int frame = (Integer) media.getKey();
            if (frame >= 0 && frame < numberOfFrame && stored > 0) {
                // read as tiled rendered image
                logger.debug("read dicom image frame: {} sopUID: {}", frame, dicomObject.getString(Tag.SOPInstanceUID)); //$NON-NLS-1$
                RenderedImage buffer = null;
                if ("1.2.840.10008.1.2.4.94".equals(tsuid)) {
                    if (jpipReader == null) {
                        ImageReaderFactory f = ImageReaderFactory.getInstance();
                        jpipReader = f.getReaderForTransferSyntax(tsuid);
                    }
                    setTagNoNull(TagW.PixelDataProviderURL, dicomObject.getString(Tag.PixelDataProviderURL));
                    MediaElement[] elements = getMediaElement();
                    if (elements != null && elements.length > frame) {
                        jpipReader.setInput(elements[frame]);
                        buffer = jpipReader.readAsRenderedImage(frame, null);
                    }
                } else {
                    buffer = readAsRenderedImage(frame, null);
                }
                PlanarImage img = null;
                if (buffer != null) {
                    img = NullDescriptor.create(buffer, LayoutUtil.createTiledLayoutHints(buffer));
                    // Bug fix: CLibImageReader and J2KImageReaderCodecLib (imageio libs) do not handle negative values
                    // for short data. They convert signed short to unsigned short.
                    if (getDataType() == DataBuffer.TYPE_SHORT
                        && img.getSampleModel().getDataType() == DataBuffer.TYPE_USHORT) {
                        img = RectifyUShortToShortDataDescriptor.create(img, null);
                    } else if (ImageUtil.isBinary(img.getSampleModel())) {
                        ParameterBlock pb = new ParameterBlock();
                        pb.addSource(img);
                        img = JAI.create("formatbinary", pb, null); //$NON-NLS-1$
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
            if (readMediaTags()) {
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
                    } else {
                        image = new MediaElement[1];
                        String modality = (String) getTagValue(TagW.Modality);
                        boolean ps =
                            modality != null
                                && ("PR".equals(modality) || "KO".equals(modality) || "SR".equals(modality)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        if (ps) {
                            image[0] = new DicomSpecialElement(this, null);
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
        if (readMediaTags()) {
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
                if (writePerFrameFunctionalGroupsSequence(tagList, dicomObject, (Integer) key)) {
                    validateDicomImageValues(tagList);
                    computeSlicePositionVector(tagList);
                }
                return tagList;
            }
        }
        return tags;
    }

    private boolean writePerFrameFunctionalGroupsSequence(HashMap<TagW, Object> tagList, DicomObject dicomObject,
        int index) {
        if (dicomObject != null && tagList != null) {
            DicomElement seq = dicomObject.get(Tag.PerFrameFunctionalGroupsSequence);
            if (seq != null && seq.vr() == VR.SQ) {
                DicomObject dcm = null;
                try {
                    dcm = seq.getDicomObject(index);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (dcm != null) {
                    writeFunctionalGroupsSequence(tagList, dcm);
                    return true;
                }
            }
        }
        return false;
    }

    private void writeFunctionalGroupsSequence(HashMap<TagW, Object> tagList, DicomObject dcm) {
        if (dcm != null && tagList != null) {
            if (dcm != null) {
                DicomElement seq = dcm.get(Tag.PlanePositionSequence);
                if (seq != null && seq.vr() == VR.SQ && seq.countItems() > 0) {
                    setTagNoNull(tagList, TagW.ImagePositionPatient,
                        seq.getDicomObject(0).getDoubles(Tag.ImagePositionPatient, (double[]) null));
                }
                seq = dcm.get(Tag.PlaneOrientationSequence);
                if (seq != null && seq.vr() == VR.SQ && seq.countItems() > 0) {
                    double[] imgOrientation =
                        seq.getDicomObject(0).getDoubles(Tag.ImageOrientationPatient, (double[]) null);
                    setTagNoNull(tagList, TagW.ImageOrientationPatient, imgOrientation);
                    setTagNoNull(tagList, TagW.ImageOrientationPlane,
                        ImageOrientation.makeImageOrientationLabelFromImageOrientationPatient(imgOrientation));
                }
                seq = dcm.get(Tag.FrameVOILUTSequence);
                if (seq != null && seq.vr() == VR.SQ && seq.countItems() > 0) {
                    DicomObject lut = seq.getDicomObject(0);
                    setTagNoNull(tagList, TagW.WindowWidth, getFloatFromDicomElement(lut, Tag.WindowWidth, null));
                    setTagNoNull(tagList, TagW.WindowCenter, getFloatFromDicomElement(lut, Tag.WindowCenter, null));
                    // setTagNoNull(tagList, TagW.WindowCenterWidthExplanation,
                    // lut.getString(Tag.WindowCenterWidthExplanation));
                    // setTagNoNull(tagList, TagW.VOILUTFunction, lut.getString(Tag.VOILUTFunction));
                }

                seq = dcm.get(Tag.PixelValueTransformationSequence);
                if (seq != null && seq.vr() == VR.SQ && seq.countItems() > 0) {
                    DicomObject pix = seq.getDicomObject(0);
                    setTagNoNull(tagList, TagW.RescaleSlope, getFloatFromDicomElement(pix, Tag.RescaleSlope, null));
                    setTagNoNull(tagList, TagW.RescaleIntercept,
                        getFloatFromDicomElement(pix, Tag.RescaleIntercept, null));
                    setTagNoNull(tagList, TagW.RescaleType, pix.getString(Tag.RescaleType));
                }

                seq = dcm.get(Tag.PixelMeasuresSequence);
                if (seq != null && seq.vr() == VR.SQ && seq.countItems() > 0) {
                    DicomObject measure = seq.getDicomObject(0);
                    setTagNoNull(tagList, TagW.PixelSpacing, measure.getDoubles(Tag.PixelSpacing, (double[]) null));
                    setTagNoNull(tagList, TagW.SliceThickness,
                        getFloatFromDicomElement(measure, Tag.SliceThickness, null));
                }

                // Identifies the characteristics of this frame. Only a single Item shall be permitted in this sequence.
                seq = dcm.get(Tag.MRImageFrameTypeSequence);
                if (seq == null) {
                    seq = dcm.get(Tag.CTImageFrameTypeSequence);
                }
                if (seq == null) {
                    seq = dcm.get(Tag.MRSpectroscopyFrameTypeSequence);
                }
                if (seq != null && seq.vr() == VR.SQ && seq.countItems() > 0) {
                    DicomObject frame = seq.getDicomObject(0);
                    // Type of Frame. A multi-valued attribute analogous to the Image Type (0008,0008).
                    // Enumerated Values and Defined Terms are the same as those for the four values of the Image Type
                    // (0008,0008) attribute, except that the value MIXED is not allowed. See C.8.16.1 and C.8.13.3.1.1.
                    setTagNoNull(tagList, TagW.FrameType, frame.getString(Tag.FrameType));
                }

                seq = dcm.get(Tag.FrameContentSequence);
                if (seq != null && seq.vr() == VR.SQ && seq.countItems() > 0) {
                    DicomObject frame = seq.getDicomObject(0);
                    setTagNoNull(tagList, TagW.FrameAcquisitionNumber,
                        getIntegerFromDicomElement(frame, Tag.FrameAcquisitionNumber, null));
                    setTagNoNull(tagList, TagW.StackID, frame.getString(Tag.StackID));
                    setTagNoNull(tagList, TagW.InstanceNumber,
                        getIntegerFromDicomElement(frame, Tag.InStackPositionNumber, null));
                }

                // TODO implement: Frame Pixel Shift, Pixel Intensity Relationship LUT, Frame Display Shutter

                // setTagNoNull(tagList, TagW.PixelSpacingCalibrationDescription,
                // dicomObject.getString(Tag.PixelSpacingCalibrationDescription));
                // setTagNoNull(tagList, TagW.Units, dicomObject.getString(Tag.Units));

            }
        }
    }

    @Override
    public URI getMediaFragmentURI(Object key) {
        return uri;
    }

    @Override
    public void close() {
        reset();
        dispose();
    }

    @Override
    public Codec getCodec() {
        return CODEC;
    }

    @Override
    public String[] getReaderDescription() {
        String[] desc = new String[3];
        desc[0] = "DICOM Codec: " + CODEC.getCodecName(); //$NON-NLS-1$
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
}
