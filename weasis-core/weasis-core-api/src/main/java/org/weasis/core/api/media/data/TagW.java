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
    public final static AtomicInteger AppID = new AtomicInteger(1);
    private final static AtomicInteger idCounter = new AtomicInteger(Integer.MAX_VALUE);
    // TODO date format in general settings
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
    public final static TagW PatientPseudoUID = new TagW(Messages.getString("TagW.pat_uid"), TagType.String, 1); //$NON-NLS-1$
    public static final TagW SeriesLoading = new TagW(Messages.getString("TagW.laod"), TagType.Integer); //$NON-NLS-1$
    public static final TagW Thumbnail = new TagW(Messages.getString("TagW.thumb"), TagType.Thumbnail); //$NON-NLS-1$
    public static final TagW ExplorerModel = new TagW(Messages.getString("TagW.exp_model"), TagType.Object); //$NON-NLS-1$
    public static final TagW MeasurementGraphics = new TagW(Messages.getString("TagW.measure_graph"), TagType.List);; //$NON-NLS-1$
    public static final TagW SplitSeriesNumber = new TagW(Messages.getString("TagW.split_no"), TagType.Boolean); //$NON-NLS-1$
    public static final TagW SeriesSelected = new TagW(Messages.getString("TagW.select"), TagType.Boolean); //$NON-NLS-1$
    public static final TagW SeriesOpen = new TagW(Messages.getString("TagW.open"), TagType.Boolean); //$NON-NLS-1$
    public static final TagW ImageWidth = new TagW(Messages.getString("TagW.img_w"), TagType.Integer); //$NON-NLS-1$
    public static final TagW ImageHeight = new TagW(Messages.getString("TagW.img_h"), TagType.Integer); //$NON-NLS-1$
    public static final TagW ImageDepth = new TagW(Messages.getString("TagW.img_d"), TagType.Integer); //$NON-NLS-1$
    public static final TagW ImageOrientationPlane = new TagW(Messages.getString("TagW.img_or"), TagType.String); //$NON-NLS-1$
    public static final TagW ImageBitsPerPixel = new TagW(Messages.getString("TagW.img_bpp"), TagType.Integer); //$NON-NLS-1$
    public static final TagW ImageCache = new TagW("Image Cache", TagType.Boolean); //$NON-NLS-1$

    // Do not internationalize WadoTransferSyntaxUID and WadoCompressionRate because they are defined in wado_query.xsd
    public static final TagW WadoCompressionRate = new TagW("Wado Compression Rate", TagType.Integer); //$NON-NLS-1$
    public final static TagW WadoTransferSyntaxUID = new TagW("Wado Transfer Syntax UID", TagType.String); //$NON-NLS-1$

    public final static TagW WadoParameters = new TagW("Wado Parameter", TagType.Object); //$NON-NLS-1$
    public final static TagW WadoInstanceReferenceList = new TagW("List of DICOM instance References", TagType.List); //$NON-NLS-1$
    public static final TagW DicomSpecialElement = new TagW("Special DICOM Object", TagType.Object); //$NON-NLS-1$
    public static final TagW DicomSpecialElementList = new TagW("Special DICOM List", TagType.List); //$NON-NLS-1$
    public final static TagW SlicePosition = new TagW("Slice Position", TagType.DoubleArray); //$NON-NLS-1$

    public final static TagW RootElement = new TagW("Root Element", TagType.String); //$NON-NLS-1$
    public final static TagW CurrentFolder = new TagW(Messages.getString("TagW.cur_dir"), TagType.String); //$NON-NLS-1$

    /**
     * DICOM common tags
     * 
     */
    public final static TagW TransferSyntaxUID = new TagW(0x00020010, "Transfer Syntax UID", TagType.String); //$NON-NLS-1$

    public final static TagW PatientName = new TagW(0x00100010, "Patient Name", TagType.String, 1); //$NON-NLS-1$
    public final static TagW PatientID = new TagW(0x00100020, "PatientID", TagType.String, 1); //$NON-NLS-1$

    public final static TagW IssuerOfPatientID = new TagW(0x00100021, "Issuer of PatientID", TagType.String, 1); //$NON-NLS-1$
    public final static TagW PatientBirthDate = new TagW(0x00100030, "Patient Birth Date", TagType.Date, 1); //$NON-NLS-1$
    public final static TagW PatientBirthTime = new TagW(0x00100032, "Patient Birth Time", TagType.Time, 1); //$NON-NLS-1$
    public final static TagW PatientSex = new TagW(0x00100040, "Patient Sex", TagType.String, 1); //$NON-NLS-1$
    public final static TagW PatientComments = new TagW(0x00104000, "Patient Comments", TagType.String, 1); //$NON-NLS-1$

    public final static TagW StudyInstanceUID = new TagW(0x0020000D, "Study Instance UID", TagType.String, 2); //$NON-NLS-1$
    public final static TagW SubseriesInstanceUID = new TagW("Subseries Instance UID", TagType.String, 3); //$NON-NLS-1$
    public final static TagW SeriesInstanceUID = new TagW(0x0020000E, "Series Instance UID", TagType.String, 3); //$NON-NLS-1$
    public final static TagW StudyID = new TagW(0x00200010, "Study ID", TagType.String, 2); //$NON-NLS-1$
    public final static TagW InstanceNumber = new TagW(0x00200013, "Instance Number", TagType.Integer, 4); //$NON-NLS-1$
    public static final TagW ImagePositionPatient = new TagW(0x00200032,
        "Image Position Patient", TagType.DoubleArray, 4); //$NON-NLS-1$
    public static final TagW ImageOrientationPatient =
        new TagW(0x00200037, "Image Orientation", TagType.DoubleArray, 4); //$NON-NLS-1$
    public final static TagW SliceLocation = new TagW(0x00201041, "Slice Location", TagType.Float, 4); //$NON-NLS-1$
    public final static TagW SliceThickness = new TagW(0x00180050, "Slice Thickness", TagType.Float, 4); //$NON-NLS-1$

    public final static TagW ImageType = new TagW(0x00080008, "Image Type", TagType.String, 4); //$NON-NLS-1$

    public final static TagW SOPClassUID = new TagW(0x00080016, "SOP Class UID", TagType.String, 4); //$NON-NLS-1$
    public final static TagW SOPInstanceUID = new TagW(0x00080018, "SOP Instance UID", TagType.String, 4); //$NON-NLS-1$
    public final static TagW StudyDate = new TagW(0x00080020, "Study Date", TagType.Date, 2); //$NON-NLS-1$
    public final static TagW SeriesDate = new TagW(0x00080021, "Series Date", TagType.Date, 3); //$NON-NLS-1$
    public final static TagW AcquisitionDate = new TagW(0x00080022, "Acquisition Date", TagType.Date, 4); //$NON-NLS-1$

    public final static TagW StudyTime = new TagW(0x00080030, "Study Time", TagType.Time, 2); //$NON-NLS-1$
    public final static TagW AcquisitionTime = new TagW(0x00080032, "Acquisition Time", TagType.Time, 4); //$NON-NLS-1$
    public final static TagW AccessionNumber = new TagW(0x00080050, "Accession Number", TagType.String, 2); //$NON-NLS-1$
    public final static TagW RetrieveAETitle = new TagW(0x00080054, "Retrieve AE Title", TagType.String, 3); //$NON-NLS-1$
    public final static TagW Modality = new TagW(0x00080060, "Modality", TagType.String, 3); //$NON-NLS-1$
    public final static TagW ModalitiesInStudy = new TagW(0x00080061, "Modalities in Study", TagType.String, 2); //$NON-NLS-1$
    public final static TagW Manufacturer = new TagW(0x00080070, "Manufacturer", TagType.String, 3); //$NON-NLS-1$
    public final static TagW InstitutionName = new TagW(0x00080080, "Institution Name", TagType.String, 3); //$NON-NLS-1$

    public final static TagW ReferringPhysicianName = new TagW(0x00080090,
        "Referring Physician Name", TagType.String, 3); //$NON-NLS-1$
    public final static TagW StationName = new TagW(0x00081010, "Station Name", TagType.String, 3); //$NON-NLS-1$
    public final static TagW StudyDescription = new TagW(0x00081030, "Study Description", TagType.String, 2); //$NON-NLS-1$
    public final static TagW ProcedureCodeSequence = new TagW(0x00081032,
        "Procedure Code Sequence", TagType.Sequence, 2); //$NON-NLS-1$
    public final static TagW SeriesDescription = new TagW(0x0008103E, "Series Description", TagType.String, 3); //$NON-NLS-1$
    public final static TagW InstitutionalDepartmentName = new TagW(0x00081040,
        "Institutional Department Name", TagType.String, 3); //$NON-NLS-1$
    public final static TagW ManufacturerModelName = new TagW(0x00081090, "Manufacturer Model Name", TagType.String, 3); //$NON-NLS-1$

    public final static TagW ReferencedPerformedProcedureStepSequence = new TagW(0x00081111,
        "Referenced Performed Procedure Step Sequence", TagType.Sequence, 3); //$NON-NLS-1$
    public final static TagW ReferencedImageSequence = new TagW(0x00081140,
        "Referenced Image Sequence", TagType.Sequence); //$NON-NLS-1$
    public static final TagW FrameType = new TagW(0x00089007, "Frame Type", TagType.String, 4); //$NON-NLS-1$

    public final static TagW ContrastBolusAgent = new TagW(0x00180010, "Contras tBolus Agent", TagType.String); //$NON-NLS-1$
    public final static TagW ScanningSequence = new TagW(0x00180020, "Scanning Sequence", TagType.String); //$NON-NLS-1$

    public final static TagW SequenceVariant = new TagW(0x00180021, "Sequence Variant", TagType.String); //$NON-NLS-1$
    public final static TagW ScanOptions = new TagW(0x00180022, "Scan Options", TagType.String); //$NON-NLS-1$
    public static final TagW CineRate = new TagW(0x00180040, "Cine Rate", TagType.Integer); //$NON-NLS-1$
    public final static TagW KVP = new TagW(0x00180060, "kVP", TagType.String); //$NON-NLS-1$

    public final static TagW RepetitionTime = new TagW(0x00180080, "Repetition Time", TagType.Float); //$NON-NLS-1$
    public final static TagW EchoTime = new TagW(0x00180081, "Echo Time", TagType.Float); //$NON-NLS-1$
    public final static TagW InversionTime = new TagW(0x00180082, "Inversion Time", TagType.Float); //$NON-NLS-1$
    public static final TagW EchoNumbers = new TagW(0x00180086, "Echo Number", TagType.Integer); //$NON-NLS-1$
    public final static TagW ImagerPixelSpacing = new TagW(0x00181164, "Imager Pixel Spacing", TagType.DoubleArray); //$NON-NLS-1$

    public final static TagW GantryDetectorTilt = new TagW(0x00181120, "Gantry Detector Tilt", TagType.Float); //$NON-NLS-1$
    public static final TagW PreferredPlaybackSequencing = new TagW(0x00181244,
        "Preferred Playback Sequencing", TagType.Integer); //$NON-NLS-1$
    public final static TagW ConvolutionKernel = new TagW(0x00181210, "Convolution Kernel", TagType.String); //$NON-NLS-1$
    public final static TagW FlipAngle = new TagW(0x00181314, "Scan Options", TagType.Float); //$NON-NLS-1$
    public final static TagW FrameOfReferenceUID = new TagW(0x00200052, "Frame Of Reference UID", TagType.String); //$NON-NLS-1$

    public static final TagW PixelData = new TagW(0x7FE00010, "Pixel Data", TagType.Text); //$NON-NLS-1$
    public static final TagW PixelSpacing = new TagW(0x00280030, "Pixel Spacing", TagType.DoubleArray); //$NON-NLS-1$
    public final static TagW PixelSpacingCalibrationDescription = new TagW(0x00280A04,
        "Pixel Spacing Calibration Description", TagType.String); //$NON-NLS-1$
    public static final TagW WindowWidth = new TagW(0x00281051, "Window Width", TagType.Float); //$NON-NLS-1$
    public static final TagW WindowCenter = new TagW(0x00281050, "Window Center", TagType.Float); //$NON-NLS-1$
    public static final TagW RescaleSlope = new TagW(0x00281053, "Rescale Slope", TagType.Float); //$NON-NLS-1$
    public static final TagW RescaleIntercept = new TagW(0x00281052, "Rescale Intercept", TagType.Float); //$NON-NLS-1$
    public final static TagW RescaleType = new TagW(0x00281054, "Rescale Type", TagType.String); //$NON-NLS-1$

    public static final TagW SmallestImagePixelValue = new TagW(0x00280106, "Smallest ImagePixel Value", TagType.Float); //$NON-NLS-1$
    public static final TagW LargestImagePixelValue = new TagW(0x00280107, "Largest Image PixelValue", TagType.Float); //$NON-NLS-1$
    public final static TagW BodyPartExamined = new TagW(0x00180015, "Body Part Examined", TagType.String, 3); //$NON-NLS-1$

    public static final TagW SeriesNumber = new TagW(0x00200011, "Series Number", TagType.Integer, 3); //$NON-NLS-1$

    public final static TagW Laterality = new TagW(0x00200060, "Laterality", TagType.String, 3); //$NON-NLS-1$

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

    public final static TagW StudyStatusID = new TagW(0x0032000A, "Study StatusID", TagType.String, 2); //$NON-NLS-1$
    public final static TagW PerformedProcedureStepStartDate = new TagW(0x00400244,
        "Performed Procedure Step Start Date", TagType.Date, 3); //$NON-NLS-1$
    public final static TagW PerformedProcedureStepStartTime = new TagW(0x00400245,
        "Performed Procedure Step Start Time", TagType.Time, 3); //$NON-NLS-1$
    public final static TagW RequestAttributesSequence = new TagW(0x00400275,
        "Request Attributes Sequence", TagType.Sequence, 3); //$NON-NLS-1$
    public final static TagW Units = new TagW(0x00541001, "Units", TagType.String); //$NON-NLS-1$

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

    private final static DataFlavor[] flavors = { infoElementDataFlavor };

    protected final int id;
    protected final int level;
    protected final String name;
    protected final TagType type;
    protected String format;

    public TagW(int id, String name, TagType type, int level) {
        this.id = id;
        this.name = name;
        this.type = type == null ? TagType.String : type;
        this.format = null;
        this.level = level;
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
                    s.append(", ");
                }
            }
            str = s.toString();
        } else if (value instanceof double[]) {
            double[] array = (double[]) value;
            StringBuffer s = new StringBuffer();
            for (int i = 0; i < array.length; i++) {
                s.append(array[i]);
                if (i < array.length - 1) {
                    s.append(", ");
                }
            }
            str = s.toString();
        } else if (value instanceof int[]) {
            int[] array = (int[]) value;
            StringBuffer s = new StringBuffer();
            for (int i = 0; i < array.length; i++) {
                s.append(array[i]);
                if (i < array.length - 1) {
                    s.append(", ");
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

    public static String formatDate(Date date) {
        if (date != null) {
            return formatDate.format(date);
        }
        return ""; //$NON-NLS-1$
    }

    public DataFlavor[] getTransferDataFlavors() {
        return flavors.clone();
    }

    public boolean isDataFlavorSupported(DataFlavor flavor) {
        for (int i = 0; i < flavors.length; i++) {
            if (flavor.equals(flavors[i])) {
                return true;
            }
        }
        return false;
    }

    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
        if (flavor.equals(flavors[0])) {
            return this;
        }
        throw new UnsupportedFlavorException(flavor);
    }
}
