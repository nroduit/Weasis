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
package org.weasis.core.api.media.data;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.InfoViewListPanel;

/**
 * 
 * 
 * @version $Rev$ $Date$
 */
public class TagW implements Transferable, Serializable {
    public static final AtomicInteger AppID = new AtomicInteger(1);
    private static final AtomicInteger idCounter = new AtomicInteger(Integer.MAX_VALUE);
    // TODO date format in general settings
    public static final long MILLIS_PER_DAY = 24 * 60 * 60 * 1000;
    public static final SimpleDateFormat formatDate = new SimpleDateFormat("dd-MM-yyyy"); //$NON-NLS-1$
    public static final SimpleDateFormat formatTime = new SimpleDateFormat("HH:mm:ss.SSSSSS"); //$NON-NLS-1$
    public static final SimpleDateFormat formatDateTime = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss"); //$NON-NLS-1$

    public static final SimpleDateFormat dicomformatDate = new SimpleDateFormat("yyyyMMdd"); //$NON-NLS-1$
    public static final SimpleDateFormat dicomformatTime = new SimpleDateFormat("HHmmss"); //$NON-NLS-1$

    public enum DICOM_LEVEL {
        Patient, Study, Series, Instance
    };

    public enum TagType {
        // Peridod is 3 digits followed by one of the characters 'D' (Day),'W' (Week), 'M' (Month) or 'Y' (Year)
        String, Text, URI, Sequence, Date, DateTime, Time, Period, Boolean, Integer, IntegerArray, Float, FloatArray,
        Double, DoubleArray, Color, Thumbnail, Object, List

    };

    // Pseudo unique identifier: as PatientID is not a unique identifier for the patient outside an institution,
    // PatientPseudoUID tend to be unique (PatientID, PatientName and PatientBirthDate can be used simultaneously to
    // enforce the unique behavior)
    public static final TagW PatientPseudoUID = new TagW(Messages.getString("TagElement.pat_uid"), TagType.String, 1); //$NON-NLS-1$
    public static final TagW SeriesLoading = new TagW(Messages.getString("TagElement.laod"), TagType.Integer); //$NON-NLS-1$
    public static final TagW Thumbnail = new TagW(Messages.getString("TagElement.thumb"), TagType.Thumbnail); //$NON-NLS-1$
    public static final TagW ExplorerModel = new TagW(Messages.getString("TagElement.exp_model"), TagType.Object); //$NON-NLS-1$
    public static final TagW MeasurementGraphics = new TagW(
        Messages.getString("TagElement.measure_graph"), TagType.List);; //$NON-NLS-1$
    public static final TagW SplitSeriesNumber = new TagW(Messages.getString("TagElement.split_no"), TagType.Boolean); //$NON-NLS-1$
    public static final TagW SeriesSelected = new TagW(Messages.getString("TagElement.select"), TagType.Boolean); //$NON-NLS-1$
    public static final TagW SeriesOpen = new TagW(Messages.getString("TagElement.open"), TagType.Boolean); //$NON-NLS-1$
    public static final TagW ImageWidth = new TagW(Messages.getString("TagElement.img_w"), TagType.Integer); //$NON-NLS-1$
    public static final TagW ImageHeight = new TagW(Messages.getString("TagElement.img_h"), TagType.Integer); //$NON-NLS-1$
    public static final TagW ImageDepth = new TagW(Messages.getString("TagElement.img_d"), TagType.Integer); //$NON-NLS-1$
    public static final TagW ImageOrientationPlane = new TagW(Messages.getString("TagElement.img_or"), TagType.String); //$NON-NLS-1$
    public static final TagW ImageBitsPerPixel = new TagW(Messages.getString("TagElement.img_bpp"), TagType.Integer); //$NON-NLS-1$
    public static final TagW ImageCache = new TagW("Image Cache", TagType.Boolean); //$NON-NLS-1$
    public static final TagW ShutterFinalShape = new TagW("Shutter Shape", TagType.Object); //$NON-NLS-1$
    public static final TagW ShutterRGBColor = new TagW("Shutter Color", TagType.IntegerArray); //$NON-NLS-1$
    public static final TagW ShutterPSValue = new TagW("Shutter PS Value", TagType.Integer); //$NON-NLS-1$

    // Do not internationalize WadoTransferSyntaxUID, WadoCompressionRate and DirectDownloadFile because they are
    // defined in wado_query.xsd
    public static final TagW WadoCompressionRate = new TagW("Wado Compression Rate", TagType.Integer); //$NON-NLS-1$
    public static final TagW WadoTransferSyntaxUID = new TagW("Wado Transfer Syntax UID", TagType.String); //$NON-NLS-1$
    public static final TagW DirectDownloadFile = new TagW("Direct Download File", TagType.String); //$NON-NLS-1$

    public static final TagW WadoParameters = new TagW("Wado Parameter", TagType.Object); //$NON-NLS-1$
    public static final TagW WadoInstanceReferenceList = new TagW("List of DICOM instance References", TagType.List); //$NON-NLS-1$
    public static final TagW DicomSpecialElement = new TagW("Special DICOM Object", TagType.Object); //$NON-NLS-1$
    public static final TagW DicomSpecialElementList = new TagW("Special DICOM List", TagType.List); //$NON-NLS-1$
    public static final TagW SlicePosition = new TagW("Slice Position", TagType.DoubleArray); //$NON-NLS-1$
    public static final TagW SuvFactor = new TagW("SUV Factor", TagType.DoubleArray); //$NON-NLS-1$

    public static final TagW RootElement = new TagW("Root Element", TagType.String); //$NON-NLS-1$
    public static final TagW CurrentFolder = new TagW(Messages.getString("TagElement.cur_dir"), TagType.String); //$NON-NLS-1$

    /**
     * DICOM common tags
     * 
     */
    public static final TagW TransferSyntaxUID = new TagW(0x00020010, "Transfer Syntax UID", TagType.String); //$NON-NLS-1$

    public static final TagW PatientName = new TagW(0x00100010, "Patient Name", TagType.String, 1); //$NON-NLS-1$
    public static final TagW PatientID = new TagW(0x00100020, "PatientID", TagType.String, 1); //$NON-NLS-1$

    public static final TagW IssuerOfPatientID = new TagW(0x00100021, "Issuer of PatientID", TagType.String, 1); //$NON-NLS-1$
    public static final TagW PatientBirthDate = new TagW(0x00100030, "Patient Birth Date", TagType.Date, 1); //$NON-NLS-1$
    public static final TagW PatientBirthTime = new TagW(0x00100032, "Patient Birth Time", TagType.Time, 1); //$NON-NLS-1$
    public static final TagW PatientSex = new TagW(0x00100040, "Patient Sex", TagType.String, 1); //$NON-NLS-1$
    public static final TagW PatientWeight = new TagW(0x00101030, "Patient Weight", TagType.Float, 1); //$NON-NLS-1$
    public static final TagW PatientComments = new TagW(0x00104000, "Patient Comments", TagType.String, 1); //$NON-NLS-1$

    public static final TagW StudyInstanceUID = new TagW(0x0020000D, "Study Instance UID", TagType.String, 2); //$NON-NLS-1$
    public static final TagW SubseriesInstanceUID = new TagW("Subseries Instance UID", TagType.String, 3); //$NON-NLS-1$
    public static final TagW SeriesInstanceUID = new TagW(0x0020000E, "Series Instance UID", TagType.String, 3); //$NON-NLS-1$
    public static final TagW StudyID = new TagW(0x00200010, "Study ID", TagType.String, 2); //$NON-NLS-1$
    public static final TagW InstanceNumber = new TagW(0x00200013, "Instance Number", TagType.Integer, 4); //$NON-NLS-1$
    public static final TagW ImagePositionPatient = new TagW(0x00200032,
        "Image Position Patient", TagType.DoubleArray, 4); //$NON-NLS-1$
    public static final TagW ImageOrientationPatient =
        new TagW(0x00200037, "Image Orientation", TagType.DoubleArray, 4); //$NON-NLS-1$
    public static final TagW SliceLocation = new TagW(0x00201041, "Slice Location", TagType.Float, 4); //$NON-NLS-1$
    public static final TagW SliceThickness = new TagW(0x00180050, "Slice Thickness", TagType.Float, 4); //$NON-NLS-1$

    public static final TagW ImageType = new TagW(0x00080008, "Image Type", TagType.String, 4); //$NON-NLS-1$

    public static final TagW SOPClassUID = new TagW(0x00080016, "SOP Class UID", TagType.String, 4); //$NON-NLS-1$
    public static final TagW SOPInstanceUID = new TagW(0x00080018, "SOP Instance UID", TagType.String, 4); //$NON-NLS-1$
    public static final TagW StudyDate = new TagW(0x00080020, "Study Date", TagType.Date, 2); //$NON-NLS-1$
    public static final TagW SeriesDate = new TagW(0x00080021, "Series Date", TagType.Date, 3); //$NON-NLS-1$
    public static final TagW AcquisitionDate = new TagW(0x00080022, "Acquisition Date", TagType.Date, 4); //$NON-NLS-1$

    public static final TagW StudyTime = new TagW(0x00080030, "Study Time", TagType.Time, 2); //$NON-NLS-1$
    public static final TagW AcquisitionTime = new TagW(0x00080032, "Acquisition Time", TagType.Time, 4); //$NON-NLS-1$
    public static final TagW AccessionNumber = new TagW(0x00080050, "Accession Number", TagType.String, 2); //$NON-NLS-1$
    public static final TagW RetrieveAETitle = new TagW(0x00080054, "Retrieve AE Title", TagType.String, 3); //$NON-NLS-1$
    public static final TagW Modality = new TagW(0x00080060, "Modality", TagType.String, 3); //$NON-NLS-1$
    public static final TagW ModalitiesInStudy = new TagW(0x00080061, "Modalities in Study", TagType.String, 2); //$NON-NLS-1$
    public static final TagW Manufacturer = new TagW(0x00080070, "Manufacturer", TagType.String, 3); //$NON-NLS-1$
    public static final TagW InstitutionName = new TagW(0x00080080, "Institution Name", TagType.String, 3); //$NON-NLS-1$

    public static final TagW ReferringPhysicianName = new TagW(0x00080090,
        "Referring Physician Name", TagType.String, 3); //$NON-NLS-1$
    public static final TagW StationName = new TagW(0x00081010, "Station Name", TagType.String, 3); //$NON-NLS-1$
    public static final TagW StudyDescription = new TagW(0x00081030, "Study Description", TagType.String, 2); //$NON-NLS-1$
    public static final TagW ProcedureCodeSequence = new TagW(0x00081032,
        "Procedure Code Sequence", TagType.Sequence, 2); //$NON-NLS-1$
    public static final TagW SeriesDescription = new TagW(0x0008103E, "Series Description", TagType.String, 3); //$NON-NLS-1$
    public static final TagW InstitutionalDepartmentName = new TagW(0x00081040,
        "Institutional Department Name", TagType.String, 3); //$NON-NLS-1$
    public static final TagW ManufacturerModelName = new TagW(0x00081090, "Manufacturer Model Name", TagType.String, 3); //$NON-NLS-1$

    public static final TagW ReferencedSeriesSequence = new TagW(0x00081115,
        "Referenced Series Sequence", TagType.Sequence, 4); //$NON-NLS-1$
    public static final TagW ReferencedPerformedProcedureStepSequence = new TagW(0x00081111,
        "Referenced Performed Procedure Step Sequence", TagType.Sequence, 3); //$NON-NLS-1$
    public static final TagW ReferencedImageSequence = new TagW(0x00081140,
        "Referenced Image Sequence", TagType.Sequence); //$NON-NLS-1$
    public static final TagW FrameType = new TagW(0x00089007, "Frame Type", TagType.String, 4); //$NON-NLS-1$

    public static final TagW ContrastBolusAgent = new TagW(0x00180010, "Contras tBolus Agent", TagType.String); //$NON-NLS-1$
    public static final TagW ScanningSequence = new TagW(0x00180020, "Scanning Sequence", TagType.String); //$NON-NLS-1$

    public static final TagW SequenceVariant = new TagW(0x00180021, "Sequence Variant", TagType.String); //$NON-NLS-1$
    public static final TagW ScanOptions = new TagW(0x00180022, "Scan Options", TagType.String); //$NON-NLS-1$
    public static final TagW CineRate = new TagW(0x00180040, "Cine Rate", TagType.Integer); //$NON-NLS-1$
    public static final TagW KVP = new TagW(0x00180060, "kVP", TagType.String); //$NON-NLS-1$

    public static final TagW RepetitionTime = new TagW(0x00180080, "Repetition Time", TagType.Float); //$NON-NLS-1$
    public static final TagW EchoTime = new TagW(0x00180081, "Echo Time", TagType.Float); //$NON-NLS-1$
    public static final TagW InversionTime = new TagW(0x00180082, "Inversion Time", TagType.Float); //$NON-NLS-1$
    public static final TagW EchoNumbers = new TagW(0x00180086, "Echo Number", TagType.Integer); //$NON-NLS-1$
    public static final TagW ImagerPixelSpacing = new TagW(0x00181164, "Imager Pixel Spacing", TagType.DoubleArray); //$NON-NLS-1$

    public static final TagW GantryDetectorTilt = new TagW(0x00181120, "Gantry Detector Tilt", TagType.Float); //$NON-NLS-1$
    public static final TagW PreferredPlaybackSequencing = new TagW(0x00181244,
        "Preferred Playback Sequencing", TagType.Integer); //$NON-NLS-1$
    public static final TagW ConvolutionKernel = new TagW(0x00181210, "Convolution Kernel", TagType.String); //$NON-NLS-1$
    public static final TagW FlipAngle = new TagW(0x00181314, "Scan Options", TagType.Float); //$NON-NLS-1$
    public static final TagW FrameOfReferenceUID = new TagW(0x00200052, "Frame Of Reference UID", TagType.String); //$NON-NLS-1$

    public static final TagW PixelData = new TagW(0x7FE00010, "Pixel Data", TagType.Text); //$NON-NLS-1$
    public static final TagW PixelSpacing = new TagW(0x00280030, "Pixel Spacing", TagType.DoubleArray); //$NON-NLS-1$
    public static final TagW PixelSpacingCalibrationDescription = new TagW(0x00280A04,
        "Pixel Spacing Calibration Description", TagType.String); //$NON-NLS-1$
    public static final TagW WindowWidth = new TagW(0x00281051, "Window Width", TagType.Float); //$NON-NLS-1$
    public static final TagW WindowCenter = new TagW(0x00281050, "Window Center", TagType.Float); //$NON-NLS-1$
    public static final TagW RescaleSlope = new TagW(0x00281053, "Rescale Slope", TagType.Float); //$NON-NLS-1$
    public static final TagW RescaleIntercept = new TagW(0x00281052, "Rescale Intercept", TagType.Float); //$NON-NLS-1$
    public static final TagW RescaleType = new TagW(0x00281054, "Rescale Type", TagType.String); //$NON-NLS-1$
    public static final TagW PixelDataProviderURL = new TagW(0x00287FE0, "Pixel Data Provider URL", TagType.String); //$NON-NLS-1$

    public static final TagW SmallestImagePixelValue = new TagW(0x00280106, "Smallest ImagePixel Value", TagType.Float); //$NON-NLS-1$
    public static final TagW LargestImagePixelValue = new TagW(0x00280107, "Largest Image PixelValue", TagType.Float); //$NON-NLS-1$
    public static final TagW BodyPartExamined = new TagW(0x00180015, "Body Part Examined", TagType.String, 3); //$NON-NLS-1$

    public static final TagW SeriesNumber = new TagW(0x00200011, "Series Number", TagType.Integer, 3); //$NON-NLS-1$

    public static final TagW Laterality = new TagW(0x00200060, "Laterality", TagType.String, 3); //$NON-NLS-1$

    public static final TagW NumberOfStudyRelatedSeries = new TagW(0x00201206,
        "Number of Study Related Series", TagType.Integer, 2); //$NON-NLS-1$
    public static final TagW NumberOfStudyRelatedInstances = new TagW(0x00201208,
        "Number of Study Related Instances", TagType.Integer, 2); //$NON-NLS-1$
    public static final TagW NumberOfSeriesRelatedInstances = new TagW(0x00201209,
        "Number of Series Related Instances", TagType.Integer); //$NON-NLS-1$
    public static final TagW ImageComments = new TagW(0x00204000, "Image Comments", TagType.String); //$NON-NLS-1$
    public static final TagW StackID = new TagW(0x00209056, "Stack ID", TagType.String, 4); //$NON-NLS-1$
    public static final TagW FrameAcquisitionNumber = new TagW(0x00209156,
        "Frame Acquisition Number", TagType.Integer, 4); //$NON-NLS-1$

    public static final TagW NumberOfFrames = new TagW(0x00280008, "Number of Frames", TagType.Integer); //$NON-NLS-1$
    public static final TagW PixelPaddingValue = new TagW(0x00280120, "Pixel Padding Value", TagType.Integer); //$NON-NLS-1$
    public static final TagW PixelPaddingRangeLimit =
        new TagW(0x00280121, "Pixel Padding Range Limit", TagType.Integer); //$NON-NLS-1$
    public static final TagW SamplesPerPixel = new TagW(0x00280002, "Samples Per Pixel", TagType.Integer); //$NON-NLS-1$
    public static final TagW MonoChrome = new TagW("MonoChrome", TagType.Boolean); //$NON-NLS-1$
    public static final TagW PhotometricInterpretation = new TagW(0x00280004,
        "Photometric Interpretation", TagType.String); //$NON-NLS-1$

    public static final TagW OverlayRows = new TagW(0x60000010, "Overlay Rows", TagType.Integer); //$NON-NLS-1$
    public static final TagW Rows = new TagW(0x00280010, "Rows", TagType.Integer); //$NON-NLS-1$
    public static final TagW Columns = new TagW(0x00280011, "Columns", TagType.Integer); //$NON-NLS-1$
    public static final TagW BitsAllocated = new TagW(0x00280100, "Bits Allocated", TagType.Integer); //$NON-NLS-1$
    public static final TagW BitsStored = new TagW(0x00280101, "Bits Stored", TagType.Integer); //$NON-NLS-1$
    public static final TagW PixelRepresentation = new TagW(0x00280103, "Pixel Representation", TagType.Integer); //$NON-NLS-1$

    public static final TagW StudyStatusID = new TagW(0x0032000A, "Study StatusID", TagType.String, 2); //$NON-NLS-1$
    public static final TagW PerformedProcedureStepStartDate = new TagW(0x00400244,
        "Performed Procedure Step Start Date", TagType.Date, 3); //$NON-NLS-1$
    public static final TagW PerformedProcedureStepStartTime = new TagW(0x00400245,
        "Performed Procedure Step Start Time", TagType.Time, 3); //$NON-NLS-1$
    public static final TagW RequestAttributesSequence = new TagW(0x00400275,
        "Request Attributes Sequence", TagType.Sequence, 3); //$NON-NLS-1$
    public static final TagW Units = new TagW(0x00541001, "Units", TagType.String); //$NON-NLS-1$

    public static final TagW MIMETypeOfEncapsulatedDocument = new TagW(0x00420012,
        "MIME Type Of Encapsulated Document", TagType.String); //$NON-NLS-1$

    private static final long serialVersionUID = -7914330824854199622L;
    public static DataFlavor infoElementDataFlavor;
    static {
        try {
            infoElementDataFlavor =
                new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + InfoViewListPanel.class.getName(), //$NON-NLS-1$
                    null, InfoViewListPanel.class.getClassLoader());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static final DataFlavor[] flavors = { infoElementDataFlavor };

    static {
        // TODO init with a profile
        TagW.enableAnonymizationProfile(true);
    }

    protected final int id;
    protected final int level;
    protected final String name;
    protected final TagType type;
    protected String format;
    protected int anonymizationType;

    public TagW(int id, String name, TagType type, int level) {
        this.id = id;
        this.name = name;
        this.type = type == null ? TagType.String : type;
        this.format = null;
        this.level = level;
        this.anonymizationType = 0;
    }

    public TagW(int id, String name, TagType type) {
        this(id, name, type, 0);
    }

    public TagW(String name) {
        this(idCounter.getAndDecrement(), name, null);
    }

    public TagW(String name, TagType type) {
        this(idCounter.getAndDecrement(), name, type);
    }

    public TagW(String name, TagType type, int level) {
        this(idCounter.getAndDecrement(), name, type, level);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTagName() {
        return name.replaceAll(" ", ""); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public TagType getType() {
        return type;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TagW) {
            return ((TagW) obj).id == id;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return id;
    }

    public static String getFormattedText(Object value, TagType type, String format) {
        if (value == null) {
            return ""; //$NON-NLS-1$
        }

        String str;

        if (TagType.String.equals(type)) {
            str = value.toString();
        } else if (TagType.Date.equals(type)) {
            str = formatDate.format((Date) value);
        } else if (TagType.Time.equals(type)) {
            str = formatTime.format((Date) value);
        } else if (TagType.DateTime.equals(type)) {
            str = formatDateTime.format((Date) value);
        } else if (TagType.Period.equals(type)) {
            // 3 digits followed by one of the characters 'D' (Day),'W' (Week), 'M' (Month) or 'Y' (Year)
            // For ex: DICOM (0010,1010) = 031Y
            str = value.toString();
            char[] tab = str.toCharArray();
            for (int i = 0; i < tab.length; i++) {
                if (tab[i] == '0') {
                    str = str.substring(1);
                } else {
                    break;
                }
            }
            if (tab.length > 0) {
                switch (tab[tab.length - 1]) {
                    case 'Y':
                        str = str.replaceFirst("Y", " years"); //$NON-NLS-1$ //$NON-NLS-2$
                        break;
                    case 'M':
                        str = str.replaceFirst("M", " months"); //$NON-NLS-1$ //$NON-NLS-2$
                        break;
                    case 'W':
                        str = str.replaceFirst("W", " weeks"); //$NON-NLS-1$ //$NON-NLS-2$
                        break;
                    case 'D':
                        str = str.replaceFirst("D", " days"); //$NON-NLS-1$ //$NON-NLS-2$
                        break;
                }
            }
        } else if (value instanceof float[]) {
            float[] array = (float[]) value;
            StringBuffer s = new StringBuffer();
            for (int i = 0; i < array.length; i++) {
                s.append(array[i]);
                if (i < array.length - 1) {
                    s.append(", "); //$NON-NLS-1$
                }
            }
            str = s.toString();
        } else if (value instanceof double[]) {
            double[] array = (double[]) value;
            StringBuffer s = new StringBuffer();
            for (int i = 0; i < array.length; i++) {
                s.append(array[i]);
                if (i < array.length - 1) {
                    s.append(", "); //$NON-NLS-1$
                }
            }
            str = s.toString();
        } else if (value instanceof int[]) {
            int[] array = (int[]) value;
            StringBuffer s = new StringBuffer();
            for (int i = 0; i < array.length; i++) {
                s.append(array[i]);
                if (i < array.length - 1) {
                    s.append(", "); //$NON-NLS-1$
                }
            }
            str = s.toString();
        } else {
            str = value.toString();
        }

        if (format != null && !format.trim().equals("$V") && !str.equals("")) { //$NON-NLS-1$ //$NON-NLS-2$
            int index = format.indexOf("$V"); //$NON-NLS-1$
            int fmLength = 2;
            if (index != -1) {
                boolean suffix = format.length() > index + fmLength;
                // If the value ($V) is followed by ':' that means a number formatter is used
                if (suffix && format.charAt(index + fmLength) == ':') {
                    fmLength++;
                    if (format.charAt(index + fmLength) == 'f' && TagType.Float.equals(type)
                        || TagType.Double.equals(type)) {
                        fmLength++;
                        String pattern = getPattern(index + fmLength, format);
                        if (pattern != null) {
                            fmLength += pattern.length() + 2;
                            try {
                                str = new DecimalFormat(pattern).format(Double.parseDouble(str));
                            } catch (NumberFormatException e) {
                            }
                        }
                    } else if (format.charAt(index + fmLength) == 'l') {
                        fmLength++;
                        String pattern = getPattern(index + fmLength, format);
                        if (pattern != null) {
                            fmLength += pattern.length() + 2;
                            try {
                                int limit = Integer.parseInt(pattern);
                                int size = str.length();
                                if (size > limit) {
                                    str = str.substring(0, limit) + "..."; //$NON-NLS-1$
                                }
                            } catch (NumberFormatException e) {
                            }
                        }
                    }
                }
                str = format.substring(0, index) + str;
                if (format.length() > index + fmLength) {
                    str += format.substring(index + fmLength);
                }
            }
        }

        return str;
    }

    private static String getPattern(int startIndex, String format) {
        int beginIndex = format.indexOf('$', startIndex);
        int endIndex = format.indexOf('$', startIndex + 2);
        if (beginIndex == -1 || endIndex == -1) {
            return null;
        }
        return format.substring(beginIndex + 1, endIndex);
    }

    public String getFormattedText(Object value) {
        return getFormattedText(value, type, format);
    }

    public synchronized void setFormat(String format) {
        this.format = format;
    }

    public synchronized String getFormat() {
        return format;
    }

    public synchronized int getAnonymizationType() {
        return anonymizationType;
    }

    public synchronized void setAnonymizationType(int anonymizationType) {
        this.anonymizationType = anonymizationType;
    }

    public static Date getDateTime(String dateTime) {
        if (dateTime != null) {
            try {
                return formatDateTime.parse(dateTime);
            } catch (Exception e) {
            }
        }
        return null;
    }

    public static Date getDate(String date) {
        if (date != null) {
            try {
                return formatDate.parse(date);
            } catch (Exception e) {
            }
        }
        return null;
    }

    public static Date getDicomDate(String date) {
        if (date != null) {
            try {
                return dicomformatDate.parse(date);
            } catch (Exception e) {
            }
        }
        return null;
    }

    public static Date getDicomTime(String dateTime) {
        if (dateTime != null) {
            try {
                return dicomformatTime.parse(dateTime);
            } catch (Exception e) {
            }
        }
        return null;
    }

    public static Date dateTime(Date date, Date time) {
        if (time == null) {
            return date;
        } else if (date == null) {
            return time;
        }
        Calendar calendarA = Calendar.getInstance();
        calendarA.setTime(date);

        Calendar calendarB = Calendar.getInstance();
        calendarB.setTime(time);

        calendarA.set(Calendar.HOUR_OF_DAY, calendarB.get(Calendar.HOUR_OF_DAY));
        calendarA.set(Calendar.MINUTE, calendarB.get(Calendar.MINUTE));
        calendarA.set(Calendar.SECOND, calendarB.get(Calendar.SECOND));
        calendarA.set(Calendar.MILLISECOND, calendarB.get(Calendar.MILLISECOND));

        return calendarA.getTime();
    }

    public static Date getOnlyDate(Date date) {
        if (date == null) {
            return null;
        }
        Calendar calendarA = Calendar.getInstance();
        calendarA.setTime(date);

        calendarA.set(Calendar.HOUR_OF_DAY, 0);
        calendarA.set(Calendar.MINUTE, 0);
        calendarA.set(Calendar.SECOND, 0);
        calendarA.set(Calendar.MILLISECOND, 0);

        return calendarA.getTime();
    }

    public static String formatDate(Date date) {
        if (date != null) {
            return formatDate.format(date);
        }
        return ""; //$NON-NLS-1$
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return flavors.clone();
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        for (int i = 0; i < flavors.length; i++) {
            if (flavor.equals(flavors[i])) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
        if (flavor.equals(flavors[0])) {
            return this;
        }
        throw new UnsupportedFlavorException(flavor);
    }

    public static synchronized void enableAnonymizationProfile(boolean activate) {
        // Default anonymization profile
        /*
         * Other Patient tags to activate if there are accessible 1052673=Other Patient Names (0010,1001) 1052672=Other
         * Patient IDs (0010,1000) 1052704=Patient's Size (0010,1020) 1052688=Patient's Age (0010,1010)
         * 1052736=Patient's Address (0010,1040) 1057108=Patient's Telephone Numbers (0010,2154) 1057120=Ethnic Group
         * (0010,2160)
         */

        /*
         * Other tags to activate if there are accessible 524417=Institution Address (0008,0081) 528456=Physician(s) of
         * Record (0008,1048) 524436=Referring Physician's Telephone Numbers (0008,0094) 524434=Referring Physician's
         * Address (0008,0092) 528480=Name of Physician(s) Reading Study (0008,1060) 3280946=Requesting Physician
         * (0032,1032) 528464=Performing Physician's Name (0008,1050) 528496=Operators' Name (0008,1070)
         * 1057152=Occupation (0010,2180) 1577008=*Protocol Name (0018,1030) 4194900=*Performed Procedure Step
         * Description (0040,0254) 3280992=*Requested Procedure Description (0032,1060) 4237104=Content Sequence
         * (0040,A730) 532753=Derivation Description (0008,2111) 1576960=Device Serial Number (0018,1000)
         * 1052816=Medical Record Locator (0010,1090) 528512=Admitting Diagnoses Description (0008,1080)
         * 1057200=Additional Patient History (0010,21B0)
         */

        TagW[] list =
            { TagW.PatientName, TagW.PatientID, TagW.PatientSex, TagW.PatientBirthDate, TagW.PatientBirthTime,
                TagW.PatientComments, TagW.PatientPseudoUID, TagW.PatientWeight, TagW.AccessionNumber, TagW.StudyID,
                TagW.InstitutionalDepartmentName, TagW.InstitutionName, TagW.ReferringPhysicianName,
                TagW.StudyDescription, TagW.SeriesDescription, TagW.RequestAttributesSequence, TagW.StationName,
                TagW.ImageComments };
        int type = activate ? 1 : 0;
        for (TagW t : list) {
            t.setAnonymizationType(type);
        }

    }

}
