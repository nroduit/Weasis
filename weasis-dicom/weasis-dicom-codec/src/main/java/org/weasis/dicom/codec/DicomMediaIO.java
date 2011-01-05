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
import org.dcm4che2.image.ColorModelFactory;
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
import org.weasis.core.api.media.data.TagElement;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.codec.geometry.ImageOrientation;

import com.sun.media.jai.util.ImageUtil;

public class DicomMediaIO extends DicomImageReader implements MediaReader<PlanarImage> {

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(DicomMediaIO.class);

    public enum DICOM_TYPE {
        Image, Video, EncapsulatedDocument
    };

    public static final String MIMETYPE = "application/dicom"; //$NON-NLS-1$
    public static final String VIDEO_MIMETYPE = "video/dicom"; //$NON-NLS-1$
    public static final String SERIES_MIMETYPE = "series/dicom"; //$NON-NLS-1$
    public static final String SERIES_PR_MIMETYPE = "pr/dicom"; //$NON-NLS-1$
    public static final String SERIES_KO_MIMETYPE = "ko/dicom"; //$NON-NLS-1$
    public static final String SERIES_SR_MIMETYPE = "sr/dicom"; //$NON-NLS-1$
    public static final String IMAGE_MIMETYPE = "image/dicom"; //$NON-NLS-1$
    public static final String IMAGE_XDSI = "xds-i/dicom"; //$NON-NLS-1$
    public static final Codec CODEC = BundleTools.getCodec(DicomMediaIO.MIMETYPE, DicomCodec.NAME);
    private final static DicomImageReaderSpi readerSpi = new DicomImageReaderSpi();
    private final URI uri;
    private DicomObject dicomObject = null;
    private int numberOfFrame;
    private int stored;
    private final HashMap<TagElement, Object> tags;
    private MediaElement image = null;
    private ImageInputStream imageStream = null;
    private DICOM_TYPE dicomType = DICOM_TYPE.Image;

    public DicomMediaIO(URI uri) {
        super(readerSpi);
        this.uri = uri;
        numberOfFrame = 0;
        this.tags = new HashMap<TagElement, Object>();
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
                if ("1.2.840.10008.1.2.4.100".equals(dicomObject.getString(Tag.TransferSyntaxUID))) { //$NON-NLS-1$
                    dicomType = DICOM_TYPE.Video;
                }
                stored = dicomObject.getInt(Tag.BitsStored, dicomObject.getInt(Tag.BitsAllocated, 0));
                if (stored > 0) {
                    numberOfFrame = getNumImages(false);
                }

                else {
                    String modality = dicomObject.getString(Tag.Modality);
                    boolean ps =
                        modality != null && ("PR".equals(modality) || "KO".equals(modality) || "SR".equals(modality)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    // String encap = dicomObject.getString(Tag.MIMETypeOfEncapsulatedDocument);
                    if (!ps) {
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

    public void setTag(TagElement tag, Object value) {
        if (tag != null) {
            tags.put(tag, value);
        }
    }

    public void setTagIfValueNotNull(TagElement tag, Object value) {
        if (tag != null && value != null) {
            tags.put(tag, value);
        }
    }

    public Object getTagValue(TagElement tag) {
        return tags.get(tag);
    }

    private void writeTag(MediaSeriesGroup group, TagElement tag) {
        group.setTag(tag, getTagValue(tag));
    }

    public void writeMetaData(MediaSeriesGroup group) {
        if (group == null) {
            return;
        }
        // Get the dicom header
        DicomObject header = getDicomObject();

        // Patient Group
        if (TagElement.PatientPseudoUID.equals(group.getTagID())) {
            group.setTag(TagElement.PatientID, getTagValue(TagElement.PatientID));
            group.setTag(TagElement.PatientName, getTagValue(TagElement.PatientName));
            group.setTag(TagElement.PatientBirthDate, getTagValue(TagElement.PatientBirthDate));

            group.setTag(TagElement.PatientBirthTime, getDateFromDicomElement(header, Tag.PatientBirthTime, null));
            // Sex attribute can have the following values: M(male), F(female), or O(other)
            String val = header.getString(Tag.PatientSex, "O"); //$NON-NLS-1$
            group
                .setTag(
                    TagElement.PatientSex,
                    val.startsWith("F") ? Messages.getString("DicomMediaIO.female") : val.startsWith("M") ? Messages.getString("DicomMediaIO.Male") : Messages.getString("DicomMediaIO.other")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            group.setTag(TagElement.IssuerOfPatientID, header.getString(Tag.IssuerOfPatientID));
            group.setTag(TagElement.PatientComments, header.getString(Tag.PatientComments));
        }
        // Study Group
        else if (TagElement.StudyInstanceUID.equals(group.getTagID())) {
            // StudyInstanceUID is the unique identifying tag for this study group
            group.setTag(TagElement.StudyID, header.getString(Tag.StudyID));
            group.setTag(TagElement.StudyDate, getDateFromDicomElement(header, Tag.StudyDate, null));
            group.setTag(TagElement.StudyTime, getDateFromDicomElement(header, Tag.StudyTime, null));
            group.setTag(TagElement.StudyDescription, header.getString(Tag.StudyDescription));

            group.setTag(TagElement.AccessionNumber, header.getString(Tag.AccessionNumber));
            group.setTag(TagElement.ReferringPhysicianName, header.getString(Tag.ReferringPhysicianName));
            group.setTag(TagElement.ModalitiesInStudy, header.getString(Tag.ModalitiesInStudy));
            group.setTag(TagElement.NumberOfStudyRelatedInstances, header.getInt(Tag.NumberOfStudyRelatedInstances));
            group.setTag(TagElement.NumberOfStudyRelatedSeries, header.getInt(Tag.NumberOfStudyRelatedSeries));

            group.setTag(TagElement.StudyStatusID, header.getString(Tag.StudyStatusID));
            // TODO sequence: define data structure
            group.setTag(TagElement.ProcedureCodeSequence, header.get(Tag.ProcedureCodeSequence));
        }
        // Series Group
        else if (TagElement.SubseriesInstanceUID.equals(group.getTagID())) {
            // SubseriesInstanceUID is the unique identifying tag for this series group
            group.setTag(TagElement.SeriesInstanceUID, getTagValue(TagElement.SeriesInstanceUID));
            group.setTag(TagElement.Modality, getTagValue(TagElement.Modality));
            group.setTag(TagElement.SeriesDate,
                header.getDate(Tag.SeriesDate, getDateFromDicomElement(header, Tag.StudyDate, null)));
            group.setTag(TagElement.SeriesDescription, header.getString(Tag.SeriesDescription));
            group.setTag(TagElement.RetrieveAETitle, header.getString(Tag.RetrieveAETitle));
            group.setTag(TagElement.InstitutionName, header.getString(Tag.InstitutionName));
            group.setTag(TagElement.InstitutionalDepartmentName, header.getString(Tag.InstitutionalDepartmentName));
            group.setTag(TagElement.StationName, header.getString(Tag.StationName));
            group.setTag(TagElement.Manufacturer, header.getString(Tag.Manufacturer));
            group.setTag(TagElement.ManufacturerModelName, header.getString(Tag.ManufacturerModelName));
            // TODO sequence: define data structure
            group.setTag(TagElement.ReferencedPerformedProcedureStepSequence,
                header.get(Tag.ReferencedPerformedProcedureStepSequence));
            group.setTag(TagElement.SeriesNumber, getIntegerFromDicomElement(header, Tag.SeriesNumber, null));
            group.setTag(TagElement.PreferredPlaybackSequencing, header.getInt(Tag.PreferredPlaybackSequencing, 0));
            group.setTag(TagElement.CineRate,
                header.getInt(Tag.CineRate, header.getInt(Tag.RecommendedDisplayFrameRate, 20)));
            group.setTag(TagElement.KVP, header.getFloat(Tag.KVP, Float.NaN));
            group.setTag(TagElement.Laterality, header.getString(Tag.Laterality));
            group.setTag(TagElement.BodyPartExamined, header.getString(Tag.BodyPartExamined));
            group.setTag(TagElement.ReferencedImageSequence, header.get(Tag.ReferencedImageSequence));
            group.setTag(TagElement.FrameOfReferenceUID, header.getString(Tag.FrameOfReferenceUID));
            group.setTag(TagElement.NumberOfSeriesRelatedInstances, header.getInt(Tag.NumberOfSeriesRelatedInstances));
            group.setTag(TagElement.PerformedProcedureStepStartDate,
                getDateFromDicomElement(header, Tag.PerformedProcedureStepStartDate, null));
            group.setTag(TagElement.PerformedProcedureStepStartTime,
                getDateFromDicomElement(header, Tag.PerformedProcedureStepStartTime, null));
            // TODO sequence: define data structure
            group.setTag(TagElement.RequestAttributesSequence, header.get(Tag.RequestAttributesSequence));

            // Information for series ToolTips
            group.setTag(TagElement.PatientName, getTagValue(TagElement.PatientName));
            group.setTag(TagElement.StudyDescription, header.getString(Tag.StudyDescription));
        }
    }

    private void writeInstanceTags() {
        if (dicomObject != null) {
            // Tags for identifying group (Patient, Study, Series)
            setTag(TagElement.PatientID,
                dicomObject.getString(Tag.PatientID, Messages.getString("DicomMediaIO.unknown"))); //$NON-NLS-1$
            String name = dicomObject.getString(Tag.PatientName, Messages.getString("DicomMediaIO.unknown")); //$NON-NLS-1$
            if (name.trim().equals("")) { //$NON-NLS-1$
                name = Messages.getString("DicomMediaIO.unknown"); //$NON-NLS-1$
            }
            name = name.replace("^", " "); //$NON-NLS-1$ //$NON-NLS-2$
            setTag(TagElement.PatientName, name);
            setTag(TagElement.PatientBirthDate, getDateFromDicomElement(dicomObject, Tag.PatientBirthDate, null));
            // Identifier for the patient. Tend to be unique.
            // TODO set preferences for what is PatientUID
            setTag(
                TagElement.PatientPseudoUID,
                getTagValue(TagElement.PatientID).toString()
                    + TagElement.formatDate((Date) getTagValue(TagElement.PatientBirthDate)));
            setTag(TagElement.StudyInstanceUID,
                dicomObject.getString(Tag.StudyInstanceUID, Messages.getString("DicomMediaIO.unknown"))); //$NON-NLS-1$
            setTag(TagElement.SeriesInstanceUID,
                dicomObject.getString(Tag.SeriesInstanceUID, Messages.getString("DicomMediaIO.unknown"))); //$NON-NLS-1$
            setTag(TagElement.Modality, dicomObject.getString(Tag.Modality, Messages.getString("DicomMediaIO.unknown"))); //$NON-NLS-1$
            // Instance tags
            setTag(TagElement.ImageType, dicomObject.getString(Tag.ImageType));
            setTag(TagElement.ImageComments, dicomObject.getString(Tag.ImageComments));
            setTag(TagElement.ContrastBolusAgent, dicomObject.getString(Tag.ContrastBolusAgent));
            setTag(TagElement.TransferSyntaxUID, dicomObject.getString(Tag.TransferSyntaxUID));
            setTag(TagElement.InstanceNumber, dicomObject.getInt(Tag.InstanceNumber, 0));
            setTag(TagElement.SOPInstanceUID,
                dicomObject.getString(Tag.SOPInstanceUID, getTagValue(TagElement.InstanceNumber).toString()));
            setTag(TagElement.SOPClassUID, dicomObject.getString(Tag.SOPClassUID));
            setTag(TagElement.ScanningSequence, dicomObject.getString(Tag.ScanningSequence));
            setTag(TagElement.SequenceVariant, dicomObject.getString(Tag.SequenceVariant));
            setTag(TagElement.ScanOptions, dicomObject.getString(Tag.ScanOptions));
            setTag(TagElement.RepetitionTime, dicomObject.getFloat(Tag.RepetitionTime));
            setTag(TagElement.EchoTime, dicomObject.getFloat(Tag.EchoTime));
            setTag(TagElement.InversionTime, dicomObject.getFloat(Tag.InversionTime));
            setTag(TagElement.EchoNumbers, getIntegerFromDicomElement(dicomObject, Tag.EchoNumbers, null));
            setTag(TagElement.GantryDetectorTilt, dicomObject.getFloat(Tag.GantryDetectorTilt));
            setTag(TagElement.ConvolutionKernel, dicomObject.getString(Tag.ConvolutionKernel));
            setTag(TagElement.FlipAngle, dicomObject.getFloat(Tag.FlipAngle));
            setTag(TagElement.SliceLocation, getFloatFromDicomElement(dicomObject, Tag.SliceLocation, null));
            setTag(TagElement.SliceThickness, getFloatFromDicomElement(dicomObject, Tag.SliceThickness, null));
            setTag(TagElement.AcquisitionDate, getDateFromDicomElement(dicomObject, Tag.AcquisitionDate, null));
            setTag(TagElement.AcquisitionTime, getDateFromDicomElement(dicomObject, Tag.AcquisitionTime, null));

            setTag(TagElement.ImagePositionPatient, dicomObject.getDoubles(Tag.ImagePositionPatient));
            setTag(TagElement.ImageOrientationPatient, dicomObject.getDoubles(Tag.ImageOrientationPatient));
            setTag(
                TagElement.ImageOrientationPlane,
                ImageOrientation
                    .makeImageOrientationLabelFromImageOrientationPatient((double[]) getTagValue(TagElement.ImageOrientationPatient)));

            setTag(TagElement.ImagerPixelSpacing, dicomObject.getDoubles(Tag.ImagerPixelSpacing, (double[]) null));
            setTag(TagElement.PixelSpacing, dicomObject.getDoubles(Tag.PixelSpacing, (double[]) null));
            setTag(TagElement.PixelSpacingCalibrationDescription,
                dicomObject.getString(Tag.PixelSpacingCalibrationDescription));

            setTag(TagElement.WindowWidth, dicomObject.getFloat(Tag.WindowWidth, Float.NaN));
            setTag(TagElement.WindowCenter, dicomObject.getFloat(Tag.WindowCenter, Float.NaN));

            setTag(TagElement.RescaleSlope, dicomObject.getFloat(Tag.RescaleSlope, 1.f));
            setTag(TagElement.RescaleIntercept, dicomObject.getFloat(Tag.RescaleIntercept, 0.f));
            setTag(TagElement.RescaleType, dicomObject.getString(Tag.RescaleType));
            setTag(TagElement.Units, dicomObject.getString(Tag.Units));

            setTag(TagElement.SmallestImagePixelValue,
                ((Integer) dicomObject.getInt(Tag.SmallestImagePixelValue, 0)).floatValue());
            setTag(TagElement.LargestImagePixelValue,
                ((Integer) dicomObject.getInt(Tag.LargestImagePixelValue, 0)).floatValue());
            setTag(TagElement.PixelPaddingValue, dicomObject.getInt(Tag.PixelPaddingValue, 0));
            setTag(TagElement.NumberOfFrames, dicomObject.getInt(Tag.NumberOfFrames, 1));
            setTag(TagElement.PixelPaddingRangeLimit, dicomObject.getInt(Tag.PixelPaddingRangeLimit, 0));
            setTag(TagElement.OverlayRows, dicomObject.getInt(Tag.OverlayRows, 0));

            setTag(TagElement.SamplesPerPixel, dicomObject.getInt(Tag.SamplesPerPixel, 1));
            setTag(TagElement.MonoChrome, ColorModelFactory.isMonochrome(dicomObject));
            setTag(TagElement.PhotometricInterpretation, dicomObject.getString(Tag.PhotometricInterpretation));

            setTag(TagElement.Rows, dicomObject.getInt(Tag.Rows, 0));
            setTag(TagElement.Columns, dicomObject.getInt(Tag.Columns, 0));
            setTag(TagElement.BitsAllocated, dicomObject.getInt(Tag.BitsAllocated, 8));
            setTag(TagElement.BitsStored,
                dicomObject.getInt(Tag.BitsStored, (Integer) getTagValue(TagElement.BitsAllocated)));
            setTag(TagElement.PixelRepresentation, dicomObject.getInt(Tag.PixelRepresentation, 0));

            setTagIfValueNotNull(TagElement.MIMETypeOfEncapsulatedDocument,
                dicomObject.getString(Tag.MIMETypeOfEncapsulatedDocument));
            validateDicomImageValues();
            computeSlicePositionVector();
        }
    }

    private void validateDicomImageValues() {
        Float window = (Float) getTagValue(TagElement.WindowWidth);
        float minValue = (Float) getTagValue(TagElement.SmallestImagePixelValue);
        float maxValue = (Float) getTagValue(TagElement.LargestImagePixelValue);
        // Test if DICOM min and max pixel values are consistent
        if (minValue != 0 || maxValue != 0) {
            Float level = (Float) getTagValue(TagElement.WindowCenter);
            float min = pixel2rescale(minValue);
            float max = pixel2rescale(maxValue);
            if (!level.isNaN() && !window.isNaN()) {
                // Empirical test
                float low = level - window / 4.0f;
                float high = level + window / 4.0f;
                if (low < min || high > max) {
                    // Min and Max seems to be not consistent
                    // Set to 0, it will search in min and max in pixel data
                    setTag(TagElement.SmallestImagePixelValue, 0.0f);
                    setTag(TagElement.LargestImagePixelValue, 0.0f);
                }
            }
        }
        if (!window.isNaN()) {
            int bitsStored = (Integer) getTagValue(TagElement.BitsStored);
            if (window > (1 << bitsStored)) {
                // Reset w/l values that are not consistent to the bits stored
                setTag(TagElement.WindowCenter, Float.NaN);
                setTag(TagElement.WindowWidth, Float.NaN);
            }
        }
    }

    public float pixel2rescale(float pixelValue) {
        // Hounsfield units: hu
        // hu = pixelValue * rescale slope + intercept value
        return (pixelValue * (Float) getTagValue(TagElement.RescaleSlope) + (Float) getTagValue(TagElement.RescaleIntercept));

    }

    private void computeSlicePositionVector() {
        double[] slicePosition = null;
        double[] patientPos = (double[]) getTagValue(TagElement.ImagePositionPatient);
        if (patientPos != null && patientPos.length == 3) {
            double[] imgOrientation =
                ImageOrientation.computeNormalVectorOfPlan((double[]) getTagValue(TagElement.ImageOrientationPatient));
            if (imgOrientation != null) {
                slicePosition = new double[3];
                slicePosition[0] = imgOrientation[0] * patientPos[0];
                slicePosition[1] = imgOrientation[1] * patientPos[1];
                slicePosition[2] = imgOrientation[2] * patientPos[2];
            }
        }
        setTag(TagElement.SlicePosition, slicePosition);
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
        for (Iterator<TagElement> it = tags.keySet().iterator(); it.hasNext();) {
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

    // public LookupTable getLookupTable(int frame) {
    // if (monochrome) {
    // return createLut((DicomImageReadParam) this.getDefaultReadParam(), 0,
    // getSrcImage(frame).getData()
    // .getDataBuffer());
    // }
    // return null;
    // }
    public int getImageNumber() {
        return (Integer) getTagValue(TagElement.InstanceNumber);
    }

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
                RenderedImage buffer = readAsRenderedImage(frame, null);
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
        if (image == null) {
            if (readMediaTags()) {
                if (DICOM_TYPE.Video.equals(dicomType)) {
                    image = new DicomVideoElement(this, null);
                } else {
                    image = new DicomImageElement(this, 0);
                }
            }
        }
        return image;
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
    public MediaElement getMediaElement() {
        return getSingleImage();
    }

    @Override
    public MediaSeries getMediaSeries() {
        Series series = null;
        if (readMediaTags()) {
            String seriesUID = (String) getTagValue(TagElement.SeriesInstanceUID);
            series = buildSeries(seriesUID);
            writeMetaData(series);
            // no need to apply splitting rules
            // also no model
            series.addMedia(this);
        }
        return series;
    }

    @Override
    public int getMediaElementNumber() {
        return numberOfFrame;
    }

    @Override
    public String getMediaFragmentMimeType(Object key) {
        return DICOM_TYPE.Video.equals(dicomType) ? VIDEO_MIMETYPE : IMAGE_MIMETYPE;
    }

    @Override
    public HashMap<TagElement, Object> getMediaFragmentTags(Object key) {
        return tags;
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
                    (String) getTagValue(TagElement.TransferSyntaxUID));
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
        if (dicomType.equals(DICOM_TYPE.Image)) {
            return new DicomSeries(seriesUID);
        } else if (dicomType.equals(DICOM_TYPE.Video)) {
            return new DicomVideo(seriesUID);
        }

        // else if (dicomType.equals(DICOM_TYPE.EncapsulatedDocument)) {
        // }
        return new DicomSeries(seriesUID);
    }
}
