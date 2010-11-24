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
public class TagElement implements Transferable, Serializable {

    private final static AtomicInteger idCounter = new AtomicInteger(Integer.MAX_VALUE);
    // TODO date format in general settings
    public static final SimpleDateFormat formatDate = new SimpleDateFormat("dd-MM-yyyy"); //$NON-NLS-1$
    public static final SimpleDateFormat formatTime = new SimpleDateFormat("HH:mm:ss"); //$NON-NLS-1$
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
    public final static TagElement PatientPseudoUID =
        new TagElement(Messages.getString("TagElement.pat_uid"), TagType.String); //$NON-NLS-1$
    public static final TagElement SeriesLoading =
        new TagElement(Messages.getString("TagElement.laod"), TagType.Integer); //$NON-NLS-1$
    public static final TagElement Thumbnail =
        new TagElement(Messages.getString("TagElement.thumb"), TagType.Thumbnail); //$NON-NLS-1$
    public static final TagElement ExplorerModel =
        new TagElement(Messages.getString("TagElement.exp_model"), TagType.Object); //$NON-NLS-1$
    public static final TagElement MeasurementGraphics =
        new TagElement(Messages.getString("TagElement.measure_graph"), TagType.List);; //$NON-NLS-1$
    public static final TagElement SplitSeriesNumber =
        new TagElement(Messages.getString("TagElement.split_no"), TagType.Boolean); //$NON-NLS-1$
    public static final TagElement SeriesSelected =
        new TagElement(Messages.getString("TagElement.select"), TagType.Boolean); //$NON-NLS-1$
    public static final TagElement SeriesOpen = new TagElement(Messages.getString("TagElement.open"), TagType.Boolean); //$NON-NLS-1$
    public static final TagElement ImageWidth = new TagElement(Messages.getString("TagElement.img_w"), TagType.Integer); //$NON-NLS-1$
    public static final TagElement ImageHeight =
        new TagElement(Messages.getString("TagElement.img_h"), TagType.Integer); //$NON-NLS-1$
    public static final TagElement ImageDepth = new TagElement(Messages.getString("TagElement.img_d"), TagType.Integer); //$NON-NLS-1$
    public static final TagElement ImageOrientationPlane =
        new TagElement(Messages.getString("TagElement.img_or"), TagType.String); //$NON-NLS-1$
    public static final TagElement ImageBitsPerPixel =
        new TagElement(Messages.getString("TagElement.img_bpp"), TagType.Integer); //$NON-NLS-1$

    // Do not internationalize WadoTransferSyntaxUID and WadoCompressionRate because they are defined in wado_query.xsd
    public static final TagElement WadoCompressionRate = new TagElement("Wado Compression Rate", TagType.Integer); //$NON-NLS-1$
    public final static TagElement WadoTransferSyntaxUID = new TagElement("Wado Transfer Syntax UID", TagType.String); //$NON-NLS-1$

    public final static TagElement WadoParameters = new TagElement("Wado Parameter", TagType.Object); //$NON-NLS-1$
    public final static TagElement WadoInstanceReferenceList =
        new TagElement("List of DICOM instance References", TagType.List); //$NON-NLS-1$
    public static final TagElement DicomSpecialElement = new TagElement("Special DICOM Object", TagType.Object); //$NON-NLS-1$
    public static final TagElement DicomSpecialElementList = new TagElement("Special DICOM List", TagType.List); //$NON-NLS-1$
    public final static TagElement SlicePosition = new TagElement("Slice Position", TagType.DoubleArray); //$NON-NLS-1$

    public final static TagElement RootElement = new TagElement("Root Element", TagType.String); //$NON-NLS-1$
    public final static TagElement CurrentFolder =
        new TagElement(Messages.getString("TagElement.cur_dir"), TagType.String); //$NON-NLS-1$

    /**
     * DICOM common tags
     * 
     */
    public final static TagElement TransferSyntaxUID =
        new TagElement(0x00020010, "Transfer Syntax UID", TagType.String); //$NON-NLS-1$

    public final static TagElement PatientName = new TagElement(0x00100010, "Patient Name", TagType.String); //$NON-NLS-1$
    public final static TagElement PatientID = new TagElement(0x00100020, "PatientID", TagType.String); //$NON-NLS-1$

    public final static TagElement IssuerOfPatientID =
        new TagElement(0x00100021, "Issuer of PatientID", TagType.String); //$NON-NLS-1$
    public final static TagElement PatientBirthDate = new TagElement(0x00100030, "Patient Birth Date", TagType.Date); //$NON-NLS-1$
    public final static TagElement PatientBirthTime = new TagElement(0x00100032, "Patient Birth Time", TagType.Time); //$NON-NLS-1$
    public final static TagElement PatientSex = new TagElement(0x00100040, "Patient Sex", TagType.String); //$NON-NLS-1$
    public final static TagElement PatientComments = new TagElement(0x00104000, "Patient Comments", TagType.String); //$NON-NLS-1$

    public final static TagElement StudyInstanceUID = new TagElement(0x0020000D, "Study Instance UID", TagType.String); //$NON-NLS-1$
    public final static TagElement SubseriesInstanceUID = new TagElement("Subseries Instance UID", TagType.String); //$NON-NLS-1$
    public final static TagElement SeriesInstanceUID =
        new TagElement(0x0020000E, "Series Instance UID", TagType.String); //$NON-NLS-1$
    public final static TagElement StudyID = new TagElement(0x00200010, "Study ID", TagType.String); //$NON-NLS-1$
    public final static TagElement InstanceNumber = new TagElement(0x00200013, "Instance Number", TagType.Integer); //$NON-NLS-1$
    public static final TagElement ImagePositionPatient =
        new TagElement(0x00200032, "Image Position Patient", TagType.DoubleArray); //$NON-NLS-1$
    public static final TagElement ImageOrientationPatient =
        new TagElement(0x00200037, "Image Orientation", TagType.DoubleArray); //$NON-NLS-1$
    public final static TagElement SliceLocation = new TagElement(0x00201041, "Slice Location", TagType.Float); //$NON-NLS-1$
    public final static TagElement SliceThickness = new TagElement(0x00180050, "Slice Thickness", TagType.Float); //$NON-NLS-1$

    public final static TagElement ImageType = new TagElement(0x00080008, "Image Type", TagType.String); //$NON-NLS-1$

    public final static TagElement SOPClassUID = new TagElement(0x00080016, "SOP Class UID", TagType.String); //$NON-NLS-1$
    public final static TagElement SOPInstanceUID = new TagElement(0x00080018, "SOP Instance UID", TagType.String); //$NON-NLS-1$
    public final static TagElement StudyDate = new TagElement(0x00080020, "Study Date", TagType.Date); //$NON-NLS-1$
    public final static TagElement SeriesDate = new TagElement(0x00080021, "Series Date", TagType.Date); //$NON-NLS-1$
    public final static TagElement AcquisitionDate = new TagElement(0x00080022, "Acquisition Date", TagType.Date); //$NON-NLS-1$

    public final static TagElement StudyTime = new TagElement(0x00080030, "Study Time", TagType.Time); //$NON-NLS-1$
    public final static TagElement AcquisitionTime = new TagElement(0x00080032, "Acquisition Time", TagType.Time); //$NON-NLS-1$
    public final static TagElement AccessionNumber = new TagElement(0x00080050, "Accession Number", TagType.String); //$NON-NLS-1$
    public final static TagElement RetrieveAETitle = new TagElement(0x00080054, "Retrieve AE Title", TagType.String); //$NON-NLS-1$
    public final static TagElement Modality = new TagElement(0x00080060, "Modality", TagType.String); //$NON-NLS-1$
    public final static TagElement ModalitiesInStudy =
        new TagElement(0x00080061, "Modalities in Study", TagType.String); //$NON-NLS-1$
    public final static TagElement Manufacturer = new TagElement(0x00080070, "Manufacturer", TagType.String); //$NON-NLS-1$
    public final static TagElement InstitutionName = new TagElement(0x00080080, "Institution Name", TagType.String); //$NON-NLS-1$

    public final static TagElement ReferringPhysicianName =
        new TagElement(0x00080090, "Referring Physician Name", TagType.String); //$NON-NLS-1$
    public final static TagElement StationName = new TagElement(0x00081010, "Station Name", TagType.String); //$NON-NLS-1$
    public final static TagElement StudyDescription = new TagElement(0x00081030, "Study Description", TagType.String); //$NON-NLS-1$
    public final static TagElement ProcedureCodeSequence =
        new TagElement(0x00081032, "Procedure Code Sequence", TagType.Sequence); //$NON-NLS-1$
    public final static TagElement SeriesDescription = new TagElement(0x0008103E, "Series Description", TagType.String); //$NON-NLS-1$
    public final static TagElement InstitutionalDepartmentName =
        new TagElement(0x00081040, "Institutional Department Name", TagType.String); //$NON-NLS-1$
    public final static TagElement ManufacturerModelName =
        new TagElement(0x00081090, "Manufacturer Model Name", TagType.String); //$NON-NLS-1$

    public final static TagElement ReferencedPerformedProcedureStepSequence =
        new TagElement(0x00081111, "Referenced Performed Procedure Step Sequence", TagType.Sequence); //$NON-NLS-1$
    public final static TagElement ReferencedImageSequence =
        new TagElement(0x00081140, "Referenced Image Sequence", TagType.Sequence); //$NON-NLS-1$

    public final static TagElement ContrastBolusAgent =
        new TagElement(0x00180010, "Contras tBolus Agent", TagType.String); //$NON-NLS-1$
    public final static TagElement ScanningSequence = new TagElement(0x00180020, "Scanning Sequence", TagType.String); //$NON-NLS-1$

    public final static TagElement SequenceVariant = new TagElement(0x00180021, "Sequence Variant", TagType.String); //$NON-NLS-1$
    public final static TagElement ScanOptions = new TagElement(0x00180022, "Scan Options", TagType.String); //$NON-NLS-1$
    public static final TagElement CineRate = new TagElement(0x00180040, "Cine Rate", TagType.Integer); //$NON-NLS-1$
    public final static TagElement KVP = new TagElement(0x00180060, "kVP", TagType.String); //$NON-NLS-1$

    public final static TagElement RepetitionTime = new TagElement(0x00180080, "Repetition Time", TagType.Float); //$NON-NLS-1$
    public final static TagElement EchoTime = new TagElement(0x00180081, "Echo Time", TagType.Float); //$NON-NLS-1$
    public final static TagElement InversionTime = new TagElement(0x00180082, "Inversion Time", TagType.Float); //$NON-NLS-1$
    public static final TagElement EchoNumbers = new TagElement(0x00180086, "Echo Number", TagType.Integer); //$NON-NLS-1$
    public final static TagElement ImagerPixelSpacing =
        new TagElement(0x00181164, "Imager Pixel Spacing", TagType.DoubleArray); //$NON-NLS-1$

    public final static TagElement GantryDetectorTilt =
        new TagElement(0x00181120, "Gantry Detector Tilt", TagType.Float); //$NON-NLS-1$
    public static final TagElement PreferredPlaybackSequencing =
        new TagElement(0x00181244, "Preferred Playback Sequencing", TagType.Integer); //$NON-NLS-1$
    public final static TagElement ConvolutionKernel = new TagElement(0x00181210, "Convolution Kernel", TagType.String); //$NON-NLS-1$
    public final static TagElement FlipAngle = new TagElement(0x00181314, "Scan Options", TagType.Float); //$NON-NLS-1$
    public final static TagElement FrameOfReferenceUID =
        new TagElement(0x00200052, "Frame Of Reference UID", TagType.String); //$NON-NLS-1$

    public static final TagElement PixelData = new TagElement(0x7FE00010, "Pixel Data", TagType.Text); //$NON-NLS-1$
    public static final TagElement PixelSpacing = new TagElement(0x00280030, "Pixel Spacing", TagType.DoubleArray); //$NON-NLS-1$
    public final static TagElement PixelSpacingCalibrationDescription =
        new TagElement(0x00280A04, "Pixel Spacing Calibration Description", TagType.String); //$NON-NLS-1$
    public static final TagElement WindowWidth = new TagElement(0x00281051, "Window Width", TagType.Float); //$NON-NLS-1$
    public static final TagElement WindowCenter = new TagElement(0x00281050, "Window Center", TagType.Float); //$NON-NLS-1$
    public static final TagElement RescaleSlope = new TagElement(0x00281053, "Rescale Slope", TagType.Float); //$NON-NLS-1$
    public static final TagElement RescaleIntercept = new TagElement(0x00281052, "Rescale Intercept", TagType.Float); //$NON-NLS-1$
    public final static TagElement RescaleType = new TagElement(0x00281054, "Rescale Type", TagType.String); //$NON-NLS-1$

    public static final TagElement SmallestImagePixelValue =
        new TagElement(0x00280106, "Smallest ImagePixel Value", TagType.Float); //$NON-NLS-1$
    public static final TagElement LargestImagePixelValue =
        new TagElement(0x00280107, "Largest Image PixelValue", TagType.Float); //$NON-NLS-1$
    public final static TagElement BodyPartExamined = new TagElement(0x00180015, "Body Part Examined", TagType.String); //$NON-NLS-1$

    public static final TagElement SeriesNumber = new TagElement(0x00200011, "Series Number", TagType.Integer); //$NON-NLS-1$

    public final static TagElement Laterality = new TagElement(0x00200060, "Laterality", TagType.String); //$NON-NLS-1$

    public static final TagElement NumberOfStudyRelatedSeries =
        new TagElement(0x00201206, "Number of Study Related Series", TagType.Integer); //$NON-NLS-1$
    public static final TagElement NumberOfStudyRelatedInstances =
        new TagElement(0x00201208, "Number of Study Related Instances", TagType.Integer); //$NON-NLS-1$
    public static final TagElement NumberOfSeriesRelatedInstances =
        new TagElement(0x00201209, "Number of Series Related Instances", TagType.Integer); //$NON-NLS-1$
    public static final TagElement ImageComments = new TagElement(0x00204000, "Image Comments", TagType.String); //$NON-NLS-1$
    public static final TagElement NumberOfFrames = new TagElement(0x00280008, "Number of Frames", TagType.Integer); //$NON-NLS-1$
    public static final TagElement PixelPaddingValue =
        new TagElement(0x00280120, "Pixel Padding Value", TagType.Integer); //$NON-NLS-1$
    public static final TagElement PixelPaddingRangeLimit =
        new TagElement(0x00280121, "Pixel Padding Range Limit", TagType.Integer); //$NON-NLS-1$
    public static final TagElement SamplesPerPixel = new TagElement(0x00280002, "Samples Per Pixel", TagType.Integer); //$NON-NLS-1$
    public static final TagElement MonoChrome = new TagElement("MonoChrome", TagType.Boolean); //$NON-NLS-1$
    public static final TagElement PhotometricInterpretation =
        new TagElement(0x00280004, "Photometric Interpretation", TagType.String); //$NON-NLS-1$

    public static final TagElement OverlayRows = new TagElement(0x60000010, "Overlay Rows", TagType.Integer); //$NON-NLS-1$
    public static final TagElement Rows = new TagElement(0x00280010, "Rows", TagType.Integer); //$NON-NLS-1$
    public static final TagElement Columns = new TagElement(0x00280011, "Columns", TagType.Integer); //$NON-NLS-1$
    public static final TagElement BitsAllocated = new TagElement(0x00280100, "Bits Allocated", TagType.Integer); //$NON-NLS-1$
    public static final TagElement BitsStored = new TagElement(0x00280101, "Bits Stored", TagType.Integer); //$NON-NLS-1$
    public static final TagElement PixelRepresentation =
        new TagElement(0x00280103, "Pixel Representation", TagType.Integer); //$NON-NLS-1$

    public final static TagElement StudyStatusID = new TagElement(0x0032000A, "Study StatusID", TagType.String); //$NON-NLS-1$
    public final static TagElement PerformedProcedureStepStartDate =
        new TagElement(0x00400244, "Performed Procedure Step Start Date", TagType.Date); //$NON-NLS-1$
    public final static TagElement PerformedProcedureStepStartTime =
        new TagElement(0x00400245, "Performed Procedure Step Start Time", TagType.Time); //$NON-NLS-1$
    public final static TagElement RequestAttributesSequence =
        new TagElement(0x00400275, "Request Attributes Sequence", TagType.Sequence); //$NON-NLS-1$
    public final static TagElement Units = new TagElement(0x00541001, "Units", TagType.String); //$NON-NLS-1$

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
    protected final String name;
    protected final TagType type;
    protected String format;

    public TagElement(int id, String name) {
        this(id, name, null);
    }

    public TagElement(int id, String name, TagType type) {
        this.id = id;
        this.name = name;
        this.type = type == null ? TagType.String : type;
        this.format = null;
    }

    public TagElement(String name) {
        this(idCounter.getAndDecrement(), name, null);
    }

    public TagElement(String name, TagType type) {
        this(idCounter.getAndDecrement(), name, type);
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
        if (obj instanceof TagElement) {
            return ((TagElement) obj).id == id;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return id;
    }

    public String getFormattedText(Object value) {
        if (value == null) {
            return ""; //$NON-NLS-1$
        }

        String str;

        if (TagType.Date.equals(this.getType())) {
            str = formatDate.format((Date) value);
        } else if (TagType.Time.equals(this.getType())) {
            str = formatTime.format((Date) value);
        } else if (TagType.DateTime.equals(this.getType())) {
            str = formatDateTime.format((Date) value);
        } else if (TagType.Period.equals(this.getType())) {
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
                    if (format.charAt(index + fmLength) == 'f' && TagType.Float.equals(this.getType())
                        || TagType.Double.equals(this.getType())) {
                        fmLength++;
                        String pattern = getPattern(index + fmLength);
                        if (pattern != null) {
                            fmLength += pattern.length() + 2;
                            try {
                                str = new DecimalFormat(pattern).format(Double.parseDouble(str));
                            } catch (NumberFormatException e) {
                            }
                        }
                    } else if (format.charAt(index + fmLength) == 'l') {
                        fmLength++;
                        String pattern = getPattern(index + fmLength);
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

    protected String getPattern(int startIndex) {
        int beginIndex = format.indexOf('$', startIndex);
        int endIndex = format.indexOf('$', startIndex + 2);
        if (beginIndex == -1 || endIndex == -1) {
            return null;
        }
        return format.substring(beginIndex + 1, endIndex);
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
