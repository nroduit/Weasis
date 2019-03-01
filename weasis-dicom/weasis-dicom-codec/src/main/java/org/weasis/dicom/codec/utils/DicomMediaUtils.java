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

package org.weasis.dicom.codec.utils;

import java.awt.Color;
import java.awt.Polygon;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.ByteUtils;
import org.dcm4che3.util.TagUtils;
import org.dcm4che3.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.util.CIELab;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.core.api.media.data.Tagable;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.PresentationStateReader;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.TagSeq;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.opencv.data.LookupTableCV;

/**
 * @author Nicolas Roduit
 * @author Benoit Jacquemoud
 * @version $Rev$ $Date$
 */
public class DicomMediaUtils {

    private DicomMediaUtils() {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(DicomMediaUtils.class);

    private static final int[] modalityLutAttributes = new int[] { Tag.RescaleIntercept, Tag.RescaleSlope };
    private static final int[] VOILUTWindowLevelAttributes = new int[] { Tag.WindowCenter, Tag.WindowWidth };
    private static final int[] LUTAttributes = new int[] { Tag.LUTDescriptor, Tag.LUTData };

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
        int[] list = { Tag.PatientName, Tag.PatientID, Tag.PatientSex, Tag.PatientBirthDate, Tag.PatientBirthTime,
            Tag.PatientAge, Tag.PatientComments, Tag.PatientWeight, Tag.AccessionNumber, Tag.StudyID,
            Tag.InstitutionalDepartmentName, Tag.InstitutionName, Tag.ReferringPhysicianName, Tag.StudyDescription,
            Tag.SeriesDescription, Tag.StationName, Tag.ImageComments };
        int type = activate ? 1 : 0;
        for (int id : list) {
            TagW t = TagD.getNullable(id);
            if (t != null) {
                t.setAnonymizationType(type);
            }
        }
        TagW.PatientPseudoUID.setAnonymizationType(type);
    }

    /**
     * @return false if either an argument is null or if at least one tag value is empty in the given dicomObject
     */
    public static boolean containsRequiredAttributes(Attributes dcmItems, int... requiredTags) {
        if (dcmItems == null || requiredTags == null || requiredTags.length == 0) {
            return false;
        }

        int countValues = 0;
        List<String> missingTagList = null;

        for (int tag : requiredTags) {
            if (dcmItems.containsValue(tag)) {
                countValues++;
            } else {
                if (missingTagList == null) {
                    missingTagList = new ArrayList<>(requiredTags.length);
                }
                missingTagList.add(TagUtils.toString(tag));
            }
        }
        return countValues == requiredTags.length;
    }

    /**
     * Either a Modality LUT Sequence containing a single Item or Rescale Slope and Intercept values shall be present
     * but not both.<br>
     * This requirement for only a single transformation makes it possible to unambiguously define the input of
     * succeeding stages of the grayscale pipeline such as the VOI LUT
     *
     * @return True if the specified object contains some type of Modality LUT attributes at the current level. <br>
     *
     * @see - Dicom Standard 2011 - PS 3.3 § C.11.1 Modality LUT Module
     */

    public static boolean containsRequiredModalityLUTAttributes(Attributes dcmItems) {
        return containsRequiredAttributes(dcmItems, modalityLutAttributes);
    }

    public static boolean containsRequiredModalityLUTDataAttributes(Attributes dcmItems) {
        return containsRequiredAttributes(dcmItems, Tag.ModalityLUTType) && containsLUTAttributes(dcmItems);
    }

    /**
     *
     * If any VOI LUT Table is included by an Image, a Window Width and Window Center or the VOI LUT Table, but not
     * both, may be applied to the Image for display. Inclusion of both indicates that multiple alternative views may be
     * presented. <br>
     * If multiple items are present in VOI LUT Sequence, only one may be applied to the Image for display. Multiple
     * items indicate that multiple alternative views may be presented.
     *
     * @return True if the specified object contains some type of VOI LUT attributes at the current level (ie:Window
     *         Level or VOI LUT Sequence).
     *
     * @see - Dicom Standard 2011 - PS 3.3 § C.11.2 VOI LUT Module
     */

    public static boolean containsRequiredVOILUTWindowLevelAttributes(Attributes dcmItems) {
        return containsRequiredAttributes(dcmItems, VOILUTWindowLevelAttributes);
    }

    public static boolean containsLUTAttributes(Attributes dcmItems) {
        return containsRequiredAttributes(dcmItems, LUTAttributes);
    }

    /**
     *
     * @param dicomLutObject
     *            defines LUT data dicom structure
     *
     * @return LookupTableJAI object if Data Element and Descriptors are consistent
     *
     * @see - Dicom Standard 2011 - PS 3.3 § C.11 LOOK UP TABLES AND PRESENTATION STATES
     */

    public static LookupTableCV createLut(Attributes dicomLutObject) {
        if (dicomLutObject == null || dicomLutObject.isEmpty()) {
            return null;
        }

        LookupTableCV lookupTable = null;

        // Three values of the LUT Descriptor describe the format of the LUT Data in the corresponding Data Element
        int[] descriptor = DicomMediaUtils.getIntAyrrayFromDicomElement(dicomLutObject, Tag.LUTDescriptor, null);

        if (descriptor == null) {
            LOGGER.debug("Missing LUT Descriptor"); //$NON-NLS-1$
        } else if (descriptor.length != 3) {
            LOGGER.debug("Illegal number of LUT Descriptor values \"{}\"", descriptor.length); //$NON-NLS-1$
        } else {

            // First value is the number of entries in the lookup table.
            // When this value is 0 the number of table entries is equal to 65536 <=> 0x10000.
            int numEntries = (descriptor[0] == 0) ? 65536 : descriptor[0];

            // Second value is mapped to the first entry in the LUT.
            int offset = (short) descriptor[1]; // necessary to cast in order to get negative value when present

            // Third value specifies the number of bits for each entry in the LUT Data.
            int numBits = descriptor[2];

            int dataLength = 0; // number of entry values in the LUT Data.

            // LUT Data contains the LUT entry values, assuming data is always unsigned data
            byte[] bData = null;
            try {
                bData = dicomLutObject.getBytes(Tag.LUTData);
            } catch (IOException e) {
                LOGGER.error("Cannot get byte[] of {}: {} ", TagUtils.toString(Tag.LUTData), e); //$NON-NLS-1$
                return null;
            }

            if (numBits <= 8) { // LUT Data should be stored in 8 bits allocated format
                if (numEntries <= 256 && (bData.length == (numEntries << 1))) {
                    // Some implementations have encoded 8 bit entries with 16 bits allocated, padding the high bits

                    byte[] bDataNew = new byte[numEntries];
                    int byteShift = (dicomLutObject.bigEndian() ? 1 : 0);
                    for (int i = 0; i < bDataNew.length; i++) {
                        bDataNew[i] = bData[(i << 1) | byteShift];
                    }

                    dataLength = bDataNew.length;
                    lookupTable = new LookupTableCV(bDataNew, offset);

                } else {
                    dataLength = bData.length;
                    lookupTable = new LookupTableCV(bData, offset); // LUT entry value range should be [0,255]
                }
            } else if (numBits <= 16) { // LUT Data should be stored in 16 bits allocated format
                // LUT Data contains the LUT entry values, assuming data is always unsigned data
                short[] sData = new short[numEntries];
                ByteUtils.bytesToShorts(bData, sData, 0, sData.length, dicomLutObject.bigEndian());

                if (numEntries <= 256) {
                    // Some implementations have encoded 8 bit entries with 16 bits allocated, padding the high bits
                    int maxIn = (1 << numBits) - 1;
                    int maxOut = numEntries - 1;

                    byte[] bDataNew = new byte[numEntries];
                    for (int i = 0; i < numEntries; i++) {
                        bDataNew[i] = (byte) ((sData[i] & 0xffff) * maxOut / maxIn);
                    }
                    dataLength = bDataNew.length;
                    lookupTable = new LookupTableCV(bDataNew, offset);
                } else {
                    // LUT Data contains the LUT entry values, assuming data is always unsigned data
                    dataLength = sData.length;
                    lookupTable = new LookupTableCV(sData, offset, true);

                }
            } else {
                LOGGER.debug("Illegal number of bits for each entry in the LUT Data"); //$NON-NLS-1$
            }

            if (lookupTable != null) {
                if (dataLength != numEntries) {
                    LOGGER.debug("LUT Data length \"{}\" mismatch number of entries \"{}\" in LUT Descriptor ", //$NON-NLS-1$
                        dataLength, numEntries);
                }
                if (dataLength > (1 << numBits)) {
                    LOGGER.debug(
                        "Illegal LUT Data length \"{}\" with respect to the number of bits in LUT descriptor \"{}\"", //$NON-NLS-1$
                        dataLength, numBits);
                }
            }
        }
        return lookupTable;
    }

    public static String getStringFromDicomElement(Attributes dicom, int tag) {
        if (dicom == null || !dicom.containsValue(tag)) {
            return null;
        }

        String[] s = dicom.getStrings(tag);
        if (s == null || s.length == 0) {
            return null;
        }
        if (s.length == 1) {
            return s[0];
        }
        StringBuilder sb = new StringBuilder(s[0]);
        for (int i = 1; i < s.length; i++) {
            sb.append("\\" + s[i]); //$NON-NLS-1$
        }
        return sb.toString();
    }

    public static String[] getStringArrayFromDicomElement(Attributes dicom, int tag) {
        return getStringArrayFromDicomElement(dicom, tag, (String) null);
    }

    public static String[] getStringArrayFromDicomElement(Attributes dicom, int tag, String privateCreatorID) {
        if (dicom == null || !dicom.containsValue(tag)) {
            return null;
        }
        return dicom.getStrings(privateCreatorID, tag);
    }

    public static String[] getStringArrayFromDicomElement(Attributes dicom, int tag, String[] defaultValue) {
        return getStringArrayFromDicomElement(dicom, tag, null, defaultValue);
    }

    public static String[] getStringArrayFromDicomElement(Attributes dicom, int tag, String privateCreatorID,
        String[] defaultValue) {
        if (dicom == null || !dicom.containsValue(tag)) {
            return defaultValue;
        }
        String[] val = dicom.getStrings(privateCreatorID, tag);
        if (val == null || val.length == 0) {
            return defaultValue;
        }
        return val;
    }

    public static Date getDateFromDicomElement(Attributes dicom, int tag, Date defaultValue) {
        if (dicom == null || !dicom.containsValue(tag)) {
            return defaultValue;
        }
        return dicom.getDate(tag, defaultValue);
    }

    public static Date[] getDatesFromDicomElement(Attributes dicom, int tag, String privateCreatorID,
        Date[] defaultValue) {
        if (dicom == null || !dicom.containsValue(tag)) {
            return defaultValue;
        }
        Date[] val = dicom.getDates(privateCreatorID, tag);
        if (val == null || val.length == 0) {
            return defaultValue;
        }
        return val;
    }

    public static String getPatientAgeInPeriod(Attributes dicom, int tag, boolean computeOnlyIfNull) {
        return getPatientAgeInPeriod(dicom, tag, null, null, computeOnlyIfNull);
    }

    public static String getPatientAgeInPeriod(Attributes dicom, int tag, String privateCreatorID, String defaultValue,
        boolean computeOnlyIfNull) {
        if (dicom == null) {
            return defaultValue;
        }

        if (computeOnlyIfNull) {
            String s = dicom.getString(privateCreatorID, tag, defaultValue);
            if (StringUtil.hasText(s)) {
                return s;
            }
        }

        Date date = getDate(dicom, Tag.ContentDate, Tag.AcquisitionDate, Tag.DateOfSecondaryCapture, Tag.SeriesDate,
            Tag.StudyDate);

        if (date != null) {
            Date bithdate = dicom.getDate(Tag.PatientBirthDate);
            if (bithdate != null) {
                return getPeriod(TagUtil.toLocalDate(bithdate), TagUtil.toLocalDate(date));
            }
        }
        return null;
    }

    private static Date getDate(Attributes dicom, int... tagID) {
        Date date = null;
        for (int i : tagID) {
            date = dicom.getDate(i);
            if (date != null) {
                return date;
            }
        }
        return date;
    }

    public static String getPeriod(LocalDate first, LocalDate last) {
        Objects.requireNonNull(first);
        Objects.requireNonNull(last);

        long years = ChronoUnit.YEARS.between(first, last);
        if (years < 2) {
            long months = ChronoUnit.MONTHS.between(first, last);
            if (months < 2) {
                return String.format("%03dD", ChronoUnit.DAYS.between(first, last)); //$NON-NLS-1$
            }
            return String.format("%03dM", months); //$NON-NLS-1$
        }
        return String.format("%03dY", years); //$NON-NLS-1$
    }

    public static Float getFloatFromDicomElement(Attributes dicom, int tag, Float defaultValue) {
        return getFloatFromDicomElement(dicom, tag, null, defaultValue);
    }

    public static Float getFloatFromDicomElement(Attributes dicom, int tag, String privateCreatorID,
        Float defaultValue) {
        if (dicom == null || !dicom.containsValue(tag)) {
            return defaultValue;
        }
        try {
            return dicom.getFloat(privateCreatorID, tag, defaultValue == null ? 0.0F : defaultValue);
        } catch (NumberFormatException e) {
            LOGGER.error("Cannot parse Float of {}: {} ", TagUtils.toString(tag), e.getMessage()); //$NON-NLS-1$
        }
        return defaultValue;
    }

    public static Integer getIntegerFromDicomElement(Attributes dicom, int tag, Integer defaultValue) {
        return getIntegerFromDicomElement(dicom, tag, null, defaultValue);
    }

    public static Integer getIntegerFromDicomElement(Attributes dicom, int tag, String privateCreatorID,
        Integer defaultValue) {
        if (dicom == null || !dicom.containsValue(tag)) {
            return defaultValue;
        }
        try {
            return dicom.getInt(privateCreatorID, tag, defaultValue == null ? 0 : defaultValue);
        } catch (NumberFormatException e) {
            LOGGER.error("Cannot parse Integer of {}: {} ", TagUtils.toString(tag), e.getMessage()); //$NON-NLS-1$
        }
        return defaultValue;
    }

    public static Double getDoubleFromDicomElement(Attributes dicom, int tag, Double defaultValue) {
        return getDoubleFromDicomElement(dicom, tag, null, defaultValue);
    }

    public static Double getDoubleFromDicomElement(Attributes dicom, int tag, String privateCreatorID,
        Double defaultValue) {
        if (dicom == null || !dicom.containsValue(tag)) {
            return defaultValue;
        }
        try {
            return dicom.getDouble(privateCreatorID, tag, defaultValue == null ? 0.0 : defaultValue);
        } catch (NumberFormatException e) {
            LOGGER.error("Cannot parse Double of {}: {} ", TagUtils.toString(tag), e.getMessage()); //$NON-NLS-1$
        }
        return defaultValue;
    }

    public static int[] getIntAyrrayFromDicomElement(Attributes dicom, int tag, int[] defaultValue) {
        return getIntArrayFromDicomElement(dicom, tag, null, defaultValue);
    }

    public static int[] getIntArrayFromDicomElement(Attributes dicom, int tag, String privateCreatorID,
        int[] defaultValue) {
        if (dicom == null || !dicom.containsValue(tag)) {
            return defaultValue;
        }
        try {
            return dicom.getInts(privateCreatorID, tag);
        } catch (NumberFormatException e) {
            LOGGER.error("Cannot parse int[] of {}: {} ", TagUtils.toString(tag), e.getMessage()); //$NON-NLS-1$
        }
        return defaultValue;
    }

    public static float[] getFloatArrayFromDicomElement(Attributes dicom, int tag, float[] defaultValue) {
        return getFloatArrayFromDicomElement(dicom, tag, null, defaultValue);
    }

    public static float[] getFloatArrayFromDicomElement(Attributes dicom, int tag, String privateCreatorID,
        float[] defaultValue) {
        if (dicom == null || !dicom.containsValue(tag)) {
            return defaultValue;
        }
        try {
            return dicom.getFloats(privateCreatorID, tag);
        } catch (NumberFormatException e) {
            LOGGER.error("Cannot parse float[] of {}: {} ", TagUtils.toString(tag), e.getMessage()); //$NON-NLS-1$
        }
        return defaultValue;
    }

    public static double[] getDoubleArrayFromDicomElement(Attributes dicom, int tag, double[] defaultValue) {
        return getDoubleArrayFromDicomElement(dicom, tag, null, defaultValue);
    }

    public static double[] getDoubleArrayFromDicomElement(Attributes dicom, int tag, String privateCreatorID,
        double[] defaultValue) {
        if (dicom == null || !dicom.containsValue(tag)) {
            return defaultValue;
        }
        try {
            return dicom.getDoubles(privateCreatorID, tag);
        } catch (NumberFormatException e) {
            LOGGER.error("Cannot parse double[] of {}: {} ", TagUtils.toString(tag), e.getMessage()); //$NON-NLS-1$
        }
        return defaultValue;
    }

    public static boolean hasOverlay(Attributes attrs) {
        if (attrs != null) {
            for (int i = 0; i < 16; i++) {
                int gg0000 = i << 17;
                if ((0xffff & (1 << i)) != 0 && attrs.containsValue(Tag.OverlayRows | gg0000)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Integer getIntPixelValue(Attributes ds, int tag, boolean signed, int stored) {
        VR vr = ds.getVR(tag);
        if (vr == null) {
            return null;
        }
        int result = 0;
        // Bug fix: http://www.dcm4che.org/jira/browse/DCM-460
        if (vr == VR.OB || vr == VR.OW) {
            try {
                result = ByteUtils.bytesToUShortLE(ds.getBytes(tag), 0);
            } catch (IOException e) {
                LOGGER.error("Cannot read {} ", TagUtils.toString(tag), e); //$NON-NLS-1$
            }
            if (signed && (result & (1 << (stored - 1))) != 0) {
                int andmask = (1 << stored) - 1;
                int ormask = ~andmask;
                result |= ormask;
            }
        } else if ((!signed && vr != VR.US) || (signed && vr != VR.SS)) {
            vr = signed ? VR.SS : VR.US;
            result = ds.getInt(null, tag, vr, 0);
        } else {
            result = ds.getInt(tag, 0);
        }
        // Unsigned Short (0 to 65535) and Signed Short (-32768 to +32767)
        int minInValue = signed ? -(1 << (stored - 1)) : 0;
        int maxInValue = signed ? (1 << (stored - 1)) - 1 : (1 << stored) - 1;
        return result < minInValue ? minInValue : result > maxInValue ? maxInValue : result;
    }

    public static void setTag(Map<TagW, Object> tags, TagW tag, Object value) {
        if (tag != null) {
            if (value instanceof Sequence) {
                Sequence seq = (Sequence) value;
                Attributes[] list = new Attributes[seq.size()];
                for (int i = 0; i < list.length; i++) {
                    Attributes attributes = seq.get(i);
                    list[i] = attributes.getParent() == null ? attributes : new Attributes(attributes);
                }
                tags.put(tag, list);
            } else {
                tags.put(tag, value);
            }
        }
    }

    public static void setTagNoNull(Map<TagW, Object> tags, TagW tag, Object value) {
        if (value != null) {
            setTag(tags, tag, value);
        }
    }

    public static void writeMetaData(MediaSeriesGroup group, Attributes header) {
        if (group == null || header == null) {
            return;
        }
        // Patient Group
        if (TagD.getUID(Level.PATIENT).equals(group.getTagID())) {
            DicomMediaIO.tagManager.readTags(Level.PATIENT, header, group);
        }
        // Study Group
        else if (TagD.getUID(Level.STUDY).equals(group.getTagID())) {
            DicomMediaIO.tagManager.readTags(Level.STUDY, header, group);
        }
        // Series Group
        else if (TagD.getUID(Level.SERIES).equals(group.getTagID())) {
            DicomMediaIO.tagManager.readTags(Level.SERIES, header, group);
            // Build patient age if not present
            group.setTagNoNull(TagD.get(Tag.PatientAge), getPatientAgeInPeriod(header, Tag.PatientAge, true));
        }
    }

    public static void computeSlicePositionVector(Tagable tagable) {
        if (tagable != null) {
            double[] patientPos = TagD.getTagValue(tagable, Tag.ImagePositionPatient, double[].class);
            if (patientPos != null && patientPos.length == 3) {
                double[] imgOrientation = ImageOrientation
                    .computeNormalVectorOfPlan(TagD.getTagValue(tagable, Tag.ImageOrientationPatient, double[].class));
                if (imgOrientation != null) {
                    double[] slicePosition = new double[3];
                    slicePosition[0] = imgOrientation[0] * patientPos[0];
                    slicePosition[1] = imgOrientation[1] * patientPos[1];
                    slicePosition[2] = imgOrientation[2] * patientPos[2];
                    tagable.setTag(TagW.SlicePosition, slicePosition);
                }
            }
        }
    }

    public static void buildSeriesReferences(Tagable tagable, Attributes attributes) {
        Sequence seq = attributes.getSequence(Tag.ReferencedSeriesSequence);
        if (Objects.nonNull(seq)) {
            Attributes[] ref = new Attributes[seq.size()];
            for (int i = 0; i < ref.length; i++) {
                ref[i] = new Attributes(seq.get(i));
            }

            tagable.setTagNoNull(TagD.get(Tag.ReferencedSeriesSequence), ref);
        }
    }

    public static void setShutterColor(Tagable tagable, Attributes attributes) {
        Integer psVal = (Integer) TagD.get(Tag.ShutterPresentationValue).getValue(attributes);
        tagable.setTagNoNull(TagW.ShutterPSValue, TagD.get(Tag.ShutterPresentationValue).getValue(attributes));
        float[] rgb =
            CIELab.convertToFloatLab((int[]) TagD.get(Tag.ShutterPresentationColorCIELabValue).getValue(attributes));
        Color color =
            rgb == null ? null : PresentationStateReader.getRGBColor(psVal == null ? 0 : psVal, rgb, (int[]) null);
        tagable.setTagNoNull(TagW.ShutterRGBColor, color);
    }

    /**
     * Build the shape from DICOM Shutter
     *
     * @see <a href="http://dicom.nema.org/MEDICAL/DICOM/current/output/chtml/part03/sect_C.7.6.11.html">C.7.6.11
     *      Display Shutter Module</a>
     * @see <a href="http://dicom.nema.org/MEDICAL/DICOM/current/output/chtml/part03/sect_C.7.6.15.html">C.7.6.15 Bitmap
     *      Display Shutter Module</a>
     *
     */
    public static void setShutter(Tagable tagable, Attributes dcmObject) {
        Area shape = null;
        String shutterShape = getStringFromDicomElement(dcmObject, Tag.ShutterShape);
        if (shutterShape != null) {
            if (shutterShape.contains("RECTANGULAR") || shutterShape.contains("RECTANGLE")) { //$NON-NLS-1$ //$NON-NLS-2$
                Rectangle2D rect = new Rectangle2D.Double();
                rect.setFrameFromDiagonal(getIntegerFromDicomElement(dcmObject, Tag.ShutterLeftVerticalEdge, 0),
                    getIntegerFromDicomElement(dcmObject, Tag.ShutterUpperHorizontalEdge, 0),
                    getIntegerFromDicomElement(dcmObject, Tag.ShutterRightVerticalEdge, 0),
                    getIntegerFromDicomElement(dcmObject, Tag.ShutterLowerHorizontalEdge, 0));
                shape = new Area(rect);

            }
            if (shutterShape.contains("CIRCULAR")) { //$NON-NLS-1$
                int[] centerOfCircularShutter =
                    DicomMediaUtils.getIntAyrrayFromDicomElement(dcmObject, Tag.CenterOfCircularShutter, null);
                if (centerOfCircularShutter != null && centerOfCircularShutter.length >= 2) {
                    Ellipse2D ellipse = new Ellipse2D.Double();
                    double radius = getIntegerFromDicomElement(dcmObject, Tag.RadiusOfCircularShutter, 0);
                    // Thanks DICOM for reversing x,y by row,column
                    ellipse.setFrameFromCenter(centerOfCircularShutter[1], centerOfCircularShutter[0],
                        centerOfCircularShutter[1] + radius, centerOfCircularShutter[0] + radius);
                    if (shape == null) {
                        shape = new Area(ellipse);
                    } else {
                        shape.intersect(new Area(ellipse));
                    }
                }
            }
            if (shutterShape.contains("POLYGONAL")) { //$NON-NLS-1$
                int[] points =
                    DicomMediaUtils.getIntAyrrayFromDicomElement(dcmObject, Tag.VerticesOfThePolygonalShutter, null);
                if (points != null) {
                    Polygon polygon = new Polygon();
                    for (int i = 0; i < points.length / 2; i++) {
                        // Thanks DICOM for reversing x,y by row,column
                        polygon.addPoint(points[i * 2 + 1], points[i * 2]);
                    }
                    if (shape == null) {
                        shape = new Area(polygon);
                    } else {
                        shape.intersect(new Area(polygon));
                    }
                }
            }

            if (shape != null) {
                tagable.setTagNoNull(TagW.ShutterFinalShape, shape);
            }

            // Set color also for BITMAP shape (bitmap is extracted in overlay class)
            setShutterColor(tagable, dcmObject);
        }
    }

    public static void writeFunctionalGroupsSequence(Tagable tagable, Attributes dcm) {
        if (dcm != null && tagable != null) {
            /**
             * @see - Dicom Standard 2011 - PS 3.3 §C.7.6.16.2.1 Pixel Measures Macro
             */
            TagSeq.MacroSeqData data =
                new TagSeq.MacroSeqData(dcm, TagD.getTagFromIDs(Tag.PixelSpacing, Tag.SliceThickness));
            TagD.get(Tag.PixelMeasuresSequence).readValue(data, tagable);

            /**
             * @see - Dicom Standard 2011 - PS 3.3 §C.7.6.16.2.2 Frame Content Macro
             */
            data = new TagSeq.MacroSeqData(dcm, TagD.getTagFromIDs(Tag.FrameAcquisitionNumber, Tag.StackID,
                Tag.InStackPositionNumber, Tag.TemporalPositionIndex));
            TagD.get(Tag.FrameContentSequence).readValue(data, tagable);
            // If not null override instance number for a better image sorting.
            tagable.setTagNoNull(TagD.get(Tag.InstanceNumber),
                tagable.getTagValue(TagD.get(Tag.InStackPositionNumber)));

            /**
             * @see - Dicom Standard 2011 - PS 3.3 § C.7.6.16.2.3 Plane Position (Patient) Macro
             */
            data = new TagSeq.MacroSeqData(dcm, TagD.getTagFromIDs(Tag.ImagePositionPatient));
            TagD.get(Tag.PlanePositionSequence).readValue(data, tagable);

            /**
             * @see - Dicom Standard 2011 - PS 3.3 § C.7.6.16.2.4 Plane Orientation (Patient) Macro
             */
            data = new TagSeq.MacroSeqData(dcm, TagD.getTagFromIDs(Tag.ImageOrientationPatient));
            TagD.get(Tag.PlaneOrientationSequence).readValue(data, tagable);
            // If not null add ImageOrientationPlane for getting a orientation label.
            tagable.setTagNoNull(TagW.ImageOrientationPlane,
                ImageOrientation.makeImageOrientationLabelFromImageOrientationPatient(
                    TagD.getTagValue(tagable, Tag.ImageOrientationPatient, double[].class)));

            /**
             * @see - Dicom Standard 2011 - PS 3.3 § C.7.6.16.2.8 Frame Anatomy Macro
             */
            data = new TagSeq.MacroSeqData(dcm, TagD.getTagFromIDs(Tag.FrameLaterality));
            TagD.get(Tag.FrameAnatomySequence).readValue(data, tagable);

            /**
             * Specifies the attributes of the Pixel Value Transformation Functional Group. This is equivalent with the
             * Modality LUT transformation in non Multi-frame IODs. It constrains the Modality LUT transformation step
             * in the grayscale rendering pipeline to be an identity transformation.
             *
             * @see - Dicom Standard 2011 - PS 3.3 § C.7.6.16.2.9-b Pixel Value Transformation
             */
            Attributes mLutItems = dcm.getNestedDataset(Tag.PixelValueTransformationSequence);
            applyModalityLutModule(mLutItems, tagable, Tag.PixelValueTransformationSequence);

            /**
             * Specifies the attributes of the Frame VOI LUT Functional Group. It contains one or more sets of linear or
             * sigmoid window values and/or one or more sets of lookup tables
             *
             * @see - Dicom Standard 2011 - PS 3.3 § C.7.6.16.2.10b Frame VOI LUT With LUT Macro
             */
            applyVoiLutModule(dcm.getNestedDataset(Tag.FrameVOILUTSequence), mLutItems, tagable,
                Tag.FrameVOILUTSequence);

            // TODO implement: Frame Pixel Shift, Pixel Intensity Relationship LUT (C.7.6.16-14),
            // Real World Value Mapping (C.7.6.16-12)
            // This transformation should be applied in in the pixel value (add a list of transformation for pixel
            // statistics)

            /**
             * Display Shutter Macro Table C.7-17A in PS 3.3
             *
             * @see - Dicom Standard 2011 - PS 3.3 § C.7.6.16.2.16 Frame Display Shutter Macro
             */
            Attributes macroFrameDisplayShutter = dcm.getNestedDataset(Tag.FrameDisplayShutterSequence);
            if (macroFrameDisplayShutter != null) {
                setShutter(tagable, macroFrameDisplayShutter);
            }

            /**
             * @see - Dicom Standard 2011 - PS 3.3 §C.8 Frame Type Macro
             */
            // Type of Frame. A multi-valued attribute analogous to the Image Type (0008,0008).
            // Enumerated Values and Defined Terms are the same as those for the four values of the Image Type
            // (0008,0008) attribute, except that the value MIXED is not allowed. See C.8.16.1 and C.8.13.3.1.1.
            data = new TagSeq.MacroSeqData(dcm, TagD.getTagFromIDs(Tag.FrameType));
            // C.8.13.5.1 MR Image Frame Type Macro
            TagD.get(Tag.MRImageFrameTypeSequence).readValue(data, tagable);
            // // C.8.15.3.1 CT Image Frame Type Macro
            TagD.get(Tag.CTImageFrameTypeSequence).readValue(data, tagable);
            // C.8.14.3.1 MR Spectroscopy Frame Type Macro
            TagD.get(Tag.MRSpectroscopyFrameTypeSequence).readValue(data, tagable);
            // C.8.22.5.1 PET Frame Type Macro
            TagD.get(Tag.PETFrameTypeSequence).readValue(data, tagable);
        }
    }

    public static boolean writePerFrameFunctionalGroupsSequence(Tagable tagable, Attributes header, int index) {
        if (header != null && tagable != null) {
            /*
             * C.7.6.16 The number of Items shall be the same as the number of frames in the Multi-frame image.
             */
            Attributes a = header.getNestedDataset(Tag.PerFrameFunctionalGroupsSequence, index);
            if (a != null) {
                DicomMediaUtils.writeFunctionalGroupsSequence(tagable, a);
                return true;
            }
        }
        return false;
    }

    public static void applyModalityLutModule(Attributes mLutItems, Tagable tagable, Integer seqParentTag) {
        if (mLutItems != null && tagable != null) {
            // Overrides Modality LUT Transformation attributes only if sequence is consistent
            if (containsRequiredModalityLUTAttributes(mLutItems)) {
                String modlality = TagD.getTagValue(tagable, Tag.Modality, String.class);
                if ("MR".equals(modlality) || "XA".equals(modlality) || "XRF".equals(modlality) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    || "PT".equals(modlality)) { //$NON-NLS-1$
                    /*
                     * IHE BIR: 4.16.4.2.2.5.4
                     *
                     * The grayscale rendering pipeline shall be appropriate to the SOP Class and modality. If Rescale
                     * Slope and Rescale Intercept are present in the image for MR and PET and XA/XRF images, they shall
                     * be ignored from the perspective of applying window values, and for those SOP Classes, window
                     * values shall be applied directly to the stored pixel values without rescaling.
                     */
                    LOGGER.trace("Do not apply RescaleSlope and RescaleIntercept to {}", modlality);//$NON-NLS-1$
                } else {
                    TagD.get(Tag.RescaleSlope).readValue(mLutItems, tagable);
                    TagD.get(Tag.RescaleIntercept).readValue(mLutItems, tagable);
                    TagD.get(Tag.RescaleType).readValue(mLutItems, tagable);
                }

            } else if (seqParentTag != null) {
                LOGGER.warn("Cannot apply Modality LUT from {} with inconsistent attributes", //$NON-NLS-1$
                    TagUtils.toString(seqParentTag));
            }

            // Should exist only in root DICOM (when seqParentTag == null)
            buildMoalityLUT(mLutItems.getNestedDataset(Tag.ModalityLUTSequence), tagable);
        }
    }

    public static void buildMoalityLUT(Attributes mLutItems, Tagable tagable) {
        if (tagable != null) {
            // NOTE : Either a Modality LUT Sequence containing a single Item or Rescale Slope and Intercept values
            // shall be present but not both (@see Dicom Standard 2011 - PS 3.3 § C.11.1 Modality LUT Module)

            if (mLutItems != null && containsRequiredModalityLUTDataAttributes(mLutItems)) {
                boolean canApplyMLUT = true;
                String modlality = TagD.getTagValue(tagable, Tag.Modality, String.class);
                if ("XA".equals(modlality) || "XRF".equals(modlality)) { //$NON-NLS-1$ //$NON-NLS-2$
                    // See PS 3.4 N.2.1.2.
                    String pixRel = mLutItems.getParent() == null ? null
                        : mLutItems.getParent().getString(Tag.PixelIntensityRelationship);
                    if (pixRel != null && ("LOG".equalsIgnoreCase(pixRel) || "DISP".equalsIgnoreCase(pixRel))) { //$NON-NLS-1$ //$NON-NLS-2$
                        canApplyMLUT = false;
                        LOGGER.debug(
                            "Modality LUT Sequence shall NOT be applied according to PixelIntensityRelationship"); //$NON-NLS-1$
                    }
                }

                if (canApplyMLUT) {
                    tagable.setTagNoNull(TagW.ModalityLUTData, createLut(mLutItems));
                    tagable.setTagNoNull(TagW.ModalityLUTType, TagD.get(Tag.ModalityLUTType).getValue(mLutItems));
                    tagable.setTagNoNull(TagW.ModalityLUTExplanation, TagD.get(Tag.LUTExplanation).getValue(mLutItems));
                }
            }

            if (LOGGER.isTraceEnabled()) {

                // The output range of the Modality LUT Module depends on whether or not Rescale Slope and Rescale
                // Intercept or the Modality LUT Sequence are used.

                // In the case where Rescale Slope and Rescale Intercept are used, the output ranges from
                // (minimum pixel value*Rescale Slope+Rescale Intercept) to
                // (maximum pixel value*Rescale Slope+Rescale Intercept),
                // where the minimum and maximum pixel values are determined by Bits Stored and Pixel Representation.

                // In the case where the Modality LUT Sequence is used, the output range is from 0 to 2n-1 where n
                // is the third value of LUT Descriptor. This range is always unsigned.
                // The third value specifies the number of bits for each entry in the LUT Data. It shall take the value
                // 8 or 16. The LUT Data shall be stored in a format equivalent to 8 bits allocated when the number
                // of bits for each entry is 8, and 16 bits allocated when the number of bits for each entry is 16

                if (tagable.getTagValue(TagW.ModalityLUTData) != null) {
                    if (TagD.getTagValue(tagable, Tag.RescaleIntercept) != null) {
                        LOGGER.trace("Modality LUT Sequence shall NOT be present if Rescale Intercept is present"); //$NON-NLS-1$
                    }
                    if (TagD.getTagValue(tagable, Tag.ModalityLUTType) == null) {
                        LOGGER.trace("Modality Type is required if Modality LUT Sequence is present. "); //$NON-NLS-1$
                    }
                } else if (TagD.getTagValue(tagable, Tag.RescaleIntercept) != null) {
                    if (TagD.getTagValue(tagable, Tag.RescaleSlope) == null) {
                        LOGGER.debug("Modality Rescale Slope is required if Rescale Intercept is present."); //$NON-NLS-1$
                    }
                } else {
                    String modlality = TagD.getTagValue(tagable, Tag.Modality, String.class);
                    if ("MR".equals(modlality) || "XA".equals(modlality) || "XRF".equals(modlality) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        || !"PT".equals(modlality)) { //$NON-NLS-1$
                        LOGGER
                            .trace("Modality Rescale Intercept is required if Modality LUT Sequence is not present. "); //$NON-NLS-1$
                    }
                }
            }
        }
    }

    public static void applyVoiLutModule(Attributes voiItems, Attributes mLutItems, Tagable tagable,
        Integer seqParentTag) {
        if (voiItems != null && tagable != null) {
            // Overrides VOI LUT Transformation attributes only if sequence is consistent
            if (containsRequiredVOILUTWindowLevelAttributes(voiItems)) {
                TagD.get(Tag.WindowWidth).readValue(voiItems, tagable);
                TagD.get(Tag.WindowCenter).readValue(voiItems, tagable);
                double[] ww = TagD.getTagValue(tagable, Tag.WindowWidth, double[].class);
                double[] wc = TagD.getTagValue(tagable, Tag.WindowCenter, double[].class);

                if (mLutItems != null) {
                    /*
                     * IHE BIR: 4.16.4.2.2.5.4
                     *
                     * If Rescale Slope and Rescale Intercept has been removed in applyModalityLutModule() then the
                     * Window Center and Window Width must be adapted
                     *
                     * see https://groups.google.com/forum/#!topic/comp.protocols.dicom/iTCxWcsqjnM
                     */
                    Double rs = getDoubleFromDicomElement(mLutItems, Tag.RescaleSlope, null);
                    Double ri = getDoubleFromDicomElement(mLutItems, Tag.RescaleIntercept, null);
                    String modality = TagD.getTagValue(tagable, Tag.Modality, String.class);
                    if (ww != null && wc != null && rs != null && ri != null
                        && ("MR".equals(modality) || "XA".equals(modality) || "XRF".equals(modality) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                            || "PT".equals(modality))) { //$NON-NLS-1$
                        int windowLevelDefaultCount = (ww.length == wc.length) ? ww.length : 0;
                        for (int i = 0; i < windowLevelDefaultCount; i++) {
                            ww[i] = ww[i] / rs;
                            wc[i] = (wc[i] - ri) / rs;
                        }
                    }
                }

                TagD.get(Tag.WindowCenterWidthExplanation).readValue(voiItems, tagable);
                TagD.get(Tag.VOILUTFunction).readValue(voiItems, tagable);
            }

            buildVoiLUTs(voiItems.getSequence(Tag.VOILUTSequence), tagable);
        }
    }

    public static void buildVoiLUTs(Sequence voiLUTSequence, Tagable tagable) {
        if (tagable != null) {
            // NOTE : If any VOI LUT Table is included by an Image, a Window Width and Window Center or the VOI LUT
            // Table, but not both, may be applied to the Image for display. Inclusion of both indicates that multiple
            // alternative views may be presented. (@see Dicom Standard 2011 - PS 3.3 § C.11.2 VOI LUT Module)

            if (voiLUTSequence != null && !voiLUTSequence.isEmpty()) {
                LookupTableCV[] voiLUTsData = new LookupTableCV[voiLUTSequence.size()];
                String[] voiLUTsExplanation = new String[voiLUTsData.length];

                for (int i = 0; i < voiLUTsData.length; i++) {
                    Attributes voiLUTobj = voiLUTSequence.get(i);
                    if (containsLUTAttributes(voiLUTobj)) {
                        voiLUTsData[i] = createLut(voiLUTobj);
                        voiLUTsExplanation[i] = getStringFromDicomElement(voiLUTobj, Tag.LUTExplanation);
                    } else {
                        LOGGER.info("Cannot read VOI LUT Data [{}]", i); //$NON-NLS-1$
                    }
                }

                tagable.setTag(TagW.VOILUTsData, voiLUTsData);
                tagable.setTag(TagW.VOILUTsExplanation, voiLUTsExplanation); // Optional Tag
            }

            if (LOGGER.isDebugEnabled()) {
                // If multiple items are present in VOI LUT Sequence, only one may be applied to the
                // Image for display. Multiple items indicate that multiple alternative views may be presented.

                // If multiple Window center and window width values are present, both Attributes shall have the same
                // number of values and shall be considered as pairs. Multiple values indicate that multiple alternative
                // views may be presented

                double[] windowCenter = TagD.getTagValue(tagable, Tag.WindowCenter, double[].class);
                double[] windowWidth = TagD.getTagValue(tagable, Tag.WindowWidth, double[].class);

                if (windowCenter == null && windowWidth == null) {
                    return;
                } else if (windowCenter == null) {
                    LOGGER.debug("VOI Window Center is required if Window Width is present"); //$NON-NLS-1$
                } else if (windowWidth == null) {
                    LOGGER.debug("VOI Window Width is required if Window Center is present"); //$NON-NLS-1$
                } else if (windowWidth.length != windowCenter.length) {
                    LOGGER.debug("VOI Window Center and Width attributes have different number of values : {} // {}", //$NON-NLS-1$
                        windowCenter, windowWidth);
                }
            }
        }
    }

    /**
     * @see <a href="http://dicom.nema.org/medical/Dicom/current/output/chtml/part03/sect_C.11.6.html">C.11.6 Softcopy
     *      Presentation LUT Module</a>
     */
    public static void applyPrLutModule(Attributes dcmItems, Tagable tagable) {
        if (dcmItems != null && tagable != null) {
            // TODO implement 1.2.840.10008.5.1.4.1.1.11.2 -5 color and xray
            if ("1.2.840.10008.5.1.4.1.1.11.1".equals(dcmItems.getString(Tag.SOPClassUID))) { //$NON-NLS-1$
                Attributes presentationLUT = dcmItems.getNestedDataset(Tag.PresentationLUTSequence);
                if (presentationLUT != null) {
                    /**
                     * Presentation LUT Module is always implicitly specified to apply over the full range of output of
                     * the preceding transformation, and it never selects a subset or superset of the that range (unlike
                     * the VOI LUT).
                     */
                    tagable.setTag(TagW.PRLUTsData, createLut(presentationLUT));
                    tagable.setTag(TagW.PRLUTsExplanation,
                        getStringFromDicomElement(presentationLUT, Tag.LUTExplanation));
                    tagable.setTagNoNull(TagD.get(Tag.PresentationLUTShape), "IDENTITY"); //$NON-NLS-1$
                } else {
                    // value: INVERSE, IDENTITY
                    // INVERSE => must inverse values (same as monochrome 1)
                    TagD.get(Tag.PresentationLUTShape).readValue(dcmItems, tagable);
                }
            }
        }
    }

    public static void readPRLUTsModule(Attributes dcmItems, Tagable tagable) {
        if (dcmItems != null && tagable != null) {
            // Modality LUT Module
            applyModalityLutModule(dcmItems, tagable, null);

            // VOI LUT Module
            applyVoiLutModule(dcmItems.getNestedDataset(Tag.SoftcopyVOILUTSequence), dcmItems, tagable,
                Tag.SoftcopyVOILUTSequence);

            // Presentation LUT Module
            applyPrLutModule(dcmItems, tagable);
        }
    }

    public static void computeSUVFactor(Attributes dicomObject, Tagable tagable, int index) {
        // From vendor neutral code at http://qibawiki.rsna.org/index.php?title=Standardized_Uptake_Value_%28SUV%29
        String modlality = TagD.getTagValue(tagable, Tag.Modality, String.class);
        if ("PT".equals(modlality)) { //$NON-NLS-1$
            String correctedImage = getStringFromDicomElement(dicomObject, Tag.CorrectedImage);
            if (correctedImage != null && correctedImage.contains("ATTN") && correctedImage.contains("DECY")) { //$NON-NLS-1$ //$NON-NLS-2$
                double suvFactor = 0.0;
                String units = dicomObject.getString(Tag.Units);
                // DICOM $C.8.9.1.1.3 Units
                // The units of the pixel values obtained after conversion from the stored pixel values (SV) (Pixel
                // Data (7FE0,0010)) to pixel value units (U), as defined by Rescale Intercept (0028,1052) and
                // Rescale Slope (0028,1053). Defined Terms:
                // CNTS = counts
                // NONE = unitless
                // CM2 = centimeter**2
                // PCNT = percent
                // CPS = counts/second
                // BQML = Becquerels/milliliter
                // MGMINML = milligram/minute/milliliter
                // UMOLMINML = micromole/minute/milliliter
                // MLMING = milliliter/minute/gram
                // MLG = milliliter/gram
                // 1CM = 1/centimeter
                // UMOLML = micromole/milliliter
                // PROPCNTS = proportional to counts
                // PROPCPS = proportional to counts/sec
                // MLMINML = milliliter/minute/milliliter
                // MLML = milliliter/milliliter
                // GML = grams/milliliter
                // STDDEV = standard deviations
                if ("BQML".equals(units)) { //$NON-NLS-1$
                    Float weight = getFloatFromDicomElement(dicomObject, Tag.PatientWeight, 0.0f); // in Kg
                    if (MathUtil.isDifferentFromZero(weight)) {
                        Attributes dcm =
                            dicomObject.getNestedDataset(Tag.RadiopharmaceuticalInformationSequence, index);
                        if (dcm != null) {
                            Float totalDose = getFloatFromDicomElement(dcm, Tag.RadionuclideTotalDose, null);
                            Float halfLife = getFloatFromDicomElement(dcm, Tag.RadionuclideHalfLife, null);
                            Date injectTime = getDateFromDicomElement(dcm, Tag.RadiopharmaceuticalStartTime, null);
                            Date injectDateTime =
                                getDateFromDicomElement(dcm, Tag.RadiopharmaceuticalStartDateTime, null);
                            Date acquisitionDateTime =
                                TagUtil.dateTime(getDateFromDicomElement(dicomObject, Tag.AcquisitionDate, null),
                                    getDateFromDicomElement(dicomObject, Tag.AcquisitionTime, null));
                            Date scanDate = getDateFromDicomElement(dicomObject, Tag.SeriesDate, null);
                            if ("START".equals(dicomObject.getString(Tag.DecayCorrection)) && totalDose != null //$NON-NLS-1$
                                && halfLife != null && acquisitionDateTime != null
                                && (injectDateTime != null || (scanDate != null && injectTime != null))) {
                                double time = 0.0;
                                long scanDateTime = TagUtil
                                    .dateTime(scanDate, getDateFromDicomElement(dicomObject, Tag.SeriesTime, null))
                                    .getTime();
                                if (injectDateTime == null) {
                                    if (scanDateTime > acquisitionDateTime.getTime()) {
                                        // per GE docs, may have been updated during post-processing into new series
                                        String privateCreator = dicomObject.getString(0x00090010);
                                        Date privateScanDateTime = getDateFromDicomElement(dcm, 0x0009100d, null);
                                        if ("GEMS_PETD_01".equals(privateCreator) && privateScanDateTime != null) { //$NON-NLS-1$
                                            scanDate = privateScanDateTime;
                                        } else {
                                            scanDate = null;
                                        }
                                    }
                                    if (scanDate != null) {
                                        injectDateTime = TagUtil.dateTime(scanDate, injectTime);
                                        time = (double) scanDateTime - injectDateTime.getTime();
                                    }

                                } else {
                                    time = (double) scanDateTime - injectDateTime.getTime();
                                }
                                // Exclude negative value (case over midnight)
                                if (time > 0) {
                                    double correctedDose = totalDose * Math.pow(2, -time / (1000.0 * halfLife));
                                    // Weight convert in kg to g
                                    suvFactor = weight * 1000.0 / correctedDose;
                                }
                            }
                        }
                    }
                } else if ("CNTS".equals(units)) { //$NON-NLS-1$
                    String privateTagCreator = dicomObject.getString(0x70530010);
                    double privateSUVFactor = dicomObject.getDouble(0x70531000, 0.0);
                    if ("Philips PET Private Group".equals(privateTagCreator) //$NON-NLS-1$
                        && MathUtil.isDifferentFromZero(privateSUVFactor)) {
                        suvFactor = privateSUVFactor; // units => "g/ml"
                    }
                } else if ("GML".equals(units)) { //$NON-NLS-1$
                    suvFactor = 1.0;
                }
                if (MathUtil.isDifferentFromZero(suvFactor)) {
                    tagable.setTag(TagW.SuvFactor, suvFactor);
                }
            }
        }
    }

    public static Attributes createDicomPR(Attributes dicomSourceAttribute, String seriesInstanceUID,
        String sopInstanceUID) {

        final int[] patientStudyAttributes = { Tag.SpecificCharacterSet, Tag.StudyDate, Tag.StudyTime,
            Tag.StudyDescription, Tag.AccessionNumber, Tag.IssuerOfAccessionNumberSequence, Tag.ReferringPhysicianName,
            Tag.PatientName, Tag.PatientID, Tag.IssuerOfPatientID, Tag.PatientBirthDate, Tag.PatientSex,
            Tag.AdditionalPatientHistory, Tag.StudyInstanceUID, Tag.StudyID };
        Arrays.sort(patientStudyAttributes);
        Attributes pr = new Attributes(dicomSourceAttribute, patientStudyAttributes);

        // TODO implement other ColorSoftcopyPresentationStateStorageSOPClass...
        pr.setString(Tag.SOPClassUID, VR.UI, UID.GrayscaleSoftcopyPresentationStateStorage);
        pr.setString(Tag.SOPInstanceUID, VR.UI,
            StringUtil.hasText(sopInstanceUID) ? sopInstanceUID : UIDUtils.createUID());
        Date now = new Date();
        pr.setDate(Tag.PresentationCreationDateAndTime, now);
        pr.setDate(Tag.ContentDateAndTime, now);
        pr.setString(Tag.Modality, VR.CS, "PR"); //$NON-NLS-1$
        pr.setString(Tag.SeriesInstanceUID, VR.UI,
            StringUtil.hasText(seriesInstanceUID) ? seriesInstanceUID : UIDUtils.createUID());
        return pr;
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Creates a dicomKeyObjectSelection Attributes from another SOP Instance keeping it's patient and study
     * informations. For instance it can be can an IMAGE or a previously build dicomKOS Document.
     *
     * @param dicomSourceAttribute
     *            : Must be valid
     * @param keyObjectDescription
     *            : Optional, can be null
     * @param seriesInstanceUID
     *            is supposed to be valid and won't be verified, it's the user responsibility to manage this value. If
     *            null a randomly new one will be generated instead
     *
     * @return new dicomKeyObjectSelection Document Attributes
     * @throws IOException
     */
    public static Attributes createDicomKeyObject(Attributes dicomSourceAttribute, String keyObjectDescription,
        String seriesInstanceUID) {

        /**
         * @see DICOM standard PS 3.3 - § C.17.6.1 Key Object Document Series Module
         *
         * @note Series of Key Object Selection Documents are separate from Series of Images or other Composite SOP
         *       Instances. Key Object Documents do not reside in a Series of Images or other Composite SOP Instances.
         */

        /**
         * @note Loads properties that reference all "Key Object Codes" defined in the following resource :
         *       KeyObjectSelectionCodes.xml
         *
         * @see These Codes are up to date regarding Dicom Conformance : <br>
         *      PS 3.16 - § Context ID 7010 Key Object Selection Document Title <br>
         *      PS 3.16 - § Context ID 7011 Rejected for Quality Reasons - <br>
         *      PS 3.16 - § Context ID 7012 Best In Set<br>
         *      Correction Proposal - § CP 1152 Parts 16 (Additional document titles for Key Object Selection Document)
         */

        Map<String, KeyObjectSelectionCode> codeByValue = getKeyObjectSelectionMappingResources();
        Map<String, Set<KeyObjectSelectionCode>> resourcesByContextID = new HashMap<>();

        for (KeyObjectSelectionCode code : codeByValue.values()) {
            Set<KeyObjectSelectionCode> resourceSet = resourcesByContextID.get(code.contextGroupID);
            if (resourceSet == null) {
                resourceSet = new TreeSet<>();
                resourcesByContextID.put(code.contextGroupID, resourceSet);
            }
            resourceSet.add(code);
        }

        /**
         * Document Title of created KOS - must be one of the values specified by "Context ID 7010" in
         * KeyObjectSelectionCodes.xml<br>
         *
         * @note Default is code [DCM-113000] with following attributes : <br>
         *       Tag.CodingSchemeDesignator = "DCM" <br>
         *       Tag.CodeValue = 113000 <br>
         *       Tag.CodeMeaning = "Of Interest"
         */

        final Attributes documentTitle = codeByValue.get("113000").toCodeItem(); //$NON-NLS-1$
        // TODO - the user or some preferences should be able to set this title value from a predefined list of code

        /**
         * @note "Document Title Modifier" should be set when "Document Title" meets one of the following case : <br>
         *       - Concept Name = (113001, DCM, "Rejected for Quality Reasons") <br>
         *       - Concept Name = (113010, DCM," Quality Issue") <br>
         *       - Concept Name = (113013, DCM, "Best In Set")
         *
         * @see PS 3.16 - Structured Reporting Templates § TID 2010 Key Object Selection
         */

        // TODO - add ability to set "Optional Document Title Modifier" for created KOS from the predefined list of code
        // final Attributes documentTitleModifier = null;

        final String seriesNumber = "999"; // A number that identifies the Series. (default: 999) //$NON-NLS-1$
        final String instanceNumber = "1"; // A number that identifies the Document. (default: 1) //$NON-NLS-1$

        // TODO - add ability to override default instanceNumber and seriesNumber from given parameters in case many
        // KEY OBJECT DOCUMENT SERIES and KEY OBJECT DOCUMENT are build for the same Study in the same context

        final int[] patientStudyAttributes =
            { Tag.SpecificCharacterSet, Tag.StudyDate, Tag.StudyTime, Tag.AccessionNumber,
                Tag.IssuerOfAccessionNumberSequence, Tag.ReferringPhysicianName, Tag.PatientName, Tag.PatientID,
                Tag.IssuerOfPatientID, Tag.PatientBirthDate, Tag.PatientSex, Tag.StudyInstanceUID, Tag.StudyID };
        Arrays.sort(patientStudyAttributes);

        /**
         * @note : Add selected attributes from another Attributes object to this. The specified array of tag values
         *       must be sorted (as by the {@link java.util.Arrays#sort(int[])} method) prior to making this call.
         */
        Attributes dKOS = new Attributes(dicomSourceAttribute, patientStudyAttributes);

        dKOS.setString(Tag.SOPClassUID, VR.UI, UID.KeyObjectSelectionDocumentStorage);
        dKOS.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
        dKOS.setDate(Tag.ContentDateAndTime, new Date());
        dKOS.setString(Tag.Modality, VR.CS, "KO"); //$NON-NLS-1$
        dKOS.setNull(Tag.ReferencedPerformedProcedureStepSequence, VR.SQ);
        dKOS.setString(Tag.SeriesInstanceUID, VR.UI,
            StringUtil.hasText(seriesInstanceUID) ? seriesInstanceUID : UIDUtils.createUID());
        dKOS.setString(Tag.SeriesNumber, VR.IS, seriesNumber);
        dKOS.setString(Tag.InstanceNumber, VR.IS, instanceNumber);
        dKOS.setString(Tag.ValueType, VR.CS, "CONTAINER"); //$NON-NLS-1$
        dKOS.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE"); //$NON-NLS-1$
        dKOS.newSequence(Tag.ConceptNameCodeSequence, 1).add(documentTitle);
        dKOS.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1);

        Attributes templateIdentifier = new Attributes(2);
        templateIdentifier.setString(Tag.MappingResource, VR.CS, "DCMR"); //$NON-NLS-1$
        templateIdentifier.setString(Tag.TemplateIdentifier, VR.CS, "2010"); //$NON-NLS-1$
        dKOS.newSequence(Tag.ContentTemplateSequence, 1).add(templateIdentifier);

        Sequence contentSeq = dKOS.newSequence(Tag.ContentSequence, 1);

        // !! Dead Code !! uncomment this when documentTitleModifier will be handled (see above)
        // if (documentTitleModifier != null) {
        //
        // Attributes documentTitleModifierSequence = new Attributes(4);
        // documentTitleModifierSequence.setString(Tag.RelationshipType, VR.CS, "HAS CONCEPT MOD");
        // documentTitleModifierSequence.setString(Tag.ValueType, VR.CS, "CODE");
        // documentTitleModifierSequence.newSequence(Tag.ConceptNameCodeSequence, 1).add(
        // makeKOS.toCodeItem("DCM-113011"));
        // documentTitleModifierSequence.newSequence(Tag.ConceptCodeSequence, 1).add(documentTitleModifier);
        //
        // contentSeq.add(documentTitleModifierSequence);
        // }

        if (StringUtil.hasText(keyObjectDescription)) {

            Attributes keyObjectDescriptionSequence = new Attributes(4);
            keyObjectDescriptionSequence.setString(Tag.RelationshipType, VR.CS, "CONTAINS"); //$NON-NLS-1$
            keyObjectDescriptionSequence.setString(Tag.ValueType, VR.CS, "TEXT"); //$NON-NLS-1$
            keyObjectDescriptionSequence.newSequence(Tag.ConceptNameCodeSequence, 1)
                .add(codeByValue.get("113012").toCodeItem()); //$NON-NLS-1$
            keyObjectDescriptionSequence.setString(Tag.TextValue, VR.UT, keyObjectDescription);

            contentSeq.add(keyObjectDescriptionSequence);
            dKOS.setString(Tag.SeriesDescription, VR.LO, keyObjectDescription);
        }

        // TODO - Handle Identical Documents Sequence (see below)
        /**
         * @see DICOM standard PS 3.3 - § C.17.6 Key Object Selection Modules && § C.17.6.2.1 Identical Documents
         *
         * @note The Unique identifier for the Study (studyInstanceUID) is supposed to be the same as to one of the
         *       referenced image but it's not necessary. Standard says that if the Current Requested Procedure Evidence
         *       Sequence (0040,A375) references SOP Instances both in the current study and in one or more other
         *       studies, this document shall be duplicated into each of those other studies, and the duplicates shall
         *       be referenced in the Identical Documents Sequence (0040,A525).
         */

        return dKOS;
    }

    static Map<String, KeyObjectSelectionCode> getKeyObjectSelectionMappingResources() {

        Map<String, KeyObjectSelectionCode> codeByValue = new HashMap<>();

        XMLStreamReader xmler = null;
        InputStream stream = null;
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            // disable external entities for security
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            stream = DicomMediaUtils.class.getResourceAsStream("/config/KeyObjectSelectionCodes.xml"); //$NON-NLS-1$
            xmler = factory.createXMLStreamReader(stream);

            while (xmler.hasNext()) {
                switch (xmler.next()) {
                    case XMLStreamConstants.START_ELEMENT:
                        String key = xmler.getName().getLocalPart();
                        if ("resources".equals(key)) { //$NON-NLS-1$
                            while (xmler.hasNext()) {
                                switch (xmler.next()) {
                                    case XMLStreamConstants.START_ELEMENT:
                                        readCodeResource(xmler, codeByValue);
                                        break;
                                    default:
                                        break;
                                }
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        catch (XMLStreamException e) {
            LOGGER.error("Reading KO Codes", e); //$NON-NLS-1$
            codeByValue = null;
        } finally {
            FileUtil.safeClose(xmler);
            FileUtil.safeClose(stream);
        }
        return codeByValue;
    }

    private static void readCodeResource(XMLStreamReader xmler, Map<String, KeyObjectSelectionCode> codeByValue)
        throws XMLStreamException {
        String key = xmler.getName().getLocalPart();
        if ("resource".equals(key)) { //$NON-NLS-1$
            String resourceName = xmler.getAttributeValue(null, "name"); //$NON-NLS-1$
            String contextGroupID = xmler.getAttributeValue(null, "contextId"); //$NON-NLS-1$

            while (xmler.hasNext()) {
                int eventType = xmler.next();
                switch (eventType) {
                    case XMLStreamConstants.START_ELEMENT:
                        key = xmler.getName().getLocalPart();
                        if ("code".equals(key)) { //$NON-NLS-1$

                            String codingSchemeDesignator = xmler.getAttributeValue(null, "scheme"); //$NON-NLS-1$
                            String codeValue = xmler.getAttributeValue(null, "value"); //$NON-NLS-1$
                            String codeMeaning = xmler.getAttributeValue(null, "meaning"); //$NON-NLS-1$

                            String conceptNameCodeModifier = xmler.getAttributeValue(null, "conceptMod"); //$NON-NLS-1$
                            String contexGroupIdModifier = xmler.getAttributeValue(null, "contexId"); //$NON-NLS-1$

                            codeByValue.put(codeValue,
                                new DicomMediaUtils.KeyObjectSelectionCode(resourceName, contextGroupID,
                                    codingSchemeDesignator, codeValue, codeMeaning, conceptNameCodeModifier,
                                    contexGroupIdModifier));
                        }
                        break;
                    default:
                        break;
                }
            }
        }
    }

    public static class KeyObjectSelectionCode implements Comparable<KeyObjectSelectionCode> {

        final String resourceName;
        final String contextGroupID;

        final String codingSchemeDesignator;
        final String codeValue;
        final String codeMeaning;

        final String conceptNameCodeModifier;
        final String contexGroupIdModifier;

        public KeyObjectSelectionCode(String resourceName, String contextGroupID, String codingSchemeDesignator,
            String codeValue, String codeMeaning, String conceptNameCodeModifier, String contexGroupIdModifier) {

            this.resourceName = resourceName;
            this.contextGroupID = contextGroupID;

            this.codingSchemeDesignator = codingSchemeDesignator;
            this.codeValue = codeValue;
            this.codeMeaning = codeMeaning;

            this.conceptNameCodeModifier = conceptNameCodeModifier;
            this.contexGroupIdModifier = contexGroupIdModifier;
        }

        final Boolean hasConceptModifier() {
            return conceptNameCodeModifier != null;
        }

        @Override
        public int compareTo(KeyObjectSelectionCode o) {
            return this.codeValue.compareToIgnoreCase(o.codeValue);
        }

        public Attributes toCodeItem() {
            Attributes attrs = new Attributes(3);
            attrs.setString(Tag.CodeValue, VR.SH, codeValue);
            attrs.setString(Tag.CodingSchemeDesignator, VR.SH, codingSchemeDesignator);
            attrs.setString(Tag.CodeMeaning, VR.LO, codeMeaning);
            return attrs;
        }

    }

    public static TemporalAccessor getDateFromDicomElement(TagType type, Attributes dicom, int tag,
        String privateCreatorID, TemporalAccessor defaultValue) {
        if (dicom == null || !dicom.containsValue(tag)) {
            return defaultValue;
        }
        Date date = dicom.getDate(privateCreatorID, tag);
        if (date == null) {
            return defaultValue;
        }
        if (TagType.DICOM_DATE == type) {
            return TagUtil.toLocalDate(date);
        } else if (TagType.DICOM_TIME == type) {
            return TagUtil.toLocalTime(date);
        }
        return TagUtil.toLocalDateTime(date);
    }

    public static TemporalAccessor[] getDatesFromDicomElement(TagType type, Attributes dicom, int tag,
        String privateCreatorID, TemporalAccessor[] defaultValue) {
        if (dicom == null || !dicom.containsValue(tag)) {
            return defaultValue;
        }
        Date[] dates = dicom.getDates(privateCreatorID, tag);
        if (dates == null || dates.length == 0) {
            return defaultValue;
        }

        TemporalAccessor[] vals;
        if (TagType.DICOM_DATE == type) {
            vals = new LocalDate[dates.length];
            for (int i = 0; i < vals.length; i++) {
                vals[i] = TagUtil.toLocalDate(dates[i]);
            }
        } else if (TagType.DICOM_TIME == type) {
            vals = new LocalTime[dates.length];
            for (int i = 0; i < vals.length; i++) {
                vals[i] = TagUtil.toLocalTime(dates[i]);
            }
        }

        vals = new LocalDateTime[dates.length];
        for (int i = 0; i < vals.length; i++) {
            vals[i] = TagUtil.toLocalDateTime(dates[i]);
        }

        return vals;
    }

    public static TemporalAccessor getDateFromDicomElement(XMLStreamReader xmler, String attribute, TagType type,
        TemporalAccessor defaultValue) {
        if (attribute != null) {
            String val = xmler.getAttributeValue(null, attribute);
            if (val != null) {
                if (TagType.DICOM_TIME.equals(type)) {
                    return TagD.getDicomTime(val);
                } else if (TagType.DICOM_DATETIME.equals(type)) {
                    return TagD.getDicomDateTime(null, val);
                } else {
                    return TagD.getDicomDate(val);
                }
            }
        }
        return defaultValue;
    }

    public static TemporalAccessor[] getDatesFromDicomElement(XMLStreamReader xmler, String attribute, TagType type,
        TemporalAccessor[] defaultValue) {
        return getDatesFromDicomElement(xmler, attribute, type, defaultValue, "\\"); //$NON-NLS-1$
    }

    public static TemporalAccessor[] getDatesFromDicomElement(XMLStreamReader xmler, String attribute, TagType type,
        TemporalAccessor[] defaultValue, String separator) {
        if (attribute != null) {
            String val = xmler.getAttributeValue(null, attribute);
            if (val != null) {
                String[] strs = val.split(Pattern.quote(separator));
                TemporalAccessor[] vals = new TemporalAccessor[strs.length];
                for (int i = 0; i < strs.length; i++) {
                    if (TagType.DICOM_TIME.equals(type)) {
                        vals[i] = TagD.getDicomTime(strs[i]);
                    } else if (TagType.DICOM_DATETIME.equals(type)) {
                        vals[i] = TagD.getDicomDateTime(null, strs[i]);
                    } else {
                        vals[i] = TagD.getDicomDate(strs[i]);
                    }
                }
                return vals;
            }
        }
        return defaultValue;
    }

    public static void fillAttributes(Map<TagW, Object> tags, Attributes dataset) {
        if (tags != null && dataset != null) {
            ElementDictionary dic = ElementDictionary.getStandardElementDictionary();

            for (Entry<TagW, Object> entry : tags.entrySet()) {
                fillAttributes(dataset, entry.getKey(), entry.getValue(), dic);
            }
        }
    }

    public static void fillAttributes(Iterator<Entry<TagW, Object>> iter, Attributes dataset) {
        if (iter != null && dataset != null) {
            ElementDictionary dic = ElementDictionary.getStandardElementDictionary();

            while (iter.hasNext()) {
                Entry<TagW, Object> entry = iter.next();
                fillAttributes(dataset, entry.getKey(), entry.getValue(), dic);
            }
        }
    }

    public static void fillAttributes(Attributes dataset, final TagW tag, final Object val, ElementDictionary dic) {
        if (dataset != null && tag != null) {
            TagType type = tag.getType();
            int id = tag.getId();
            String key = dic.keywordOf(id);
            if (val == null || !StringUtil.hasLength(key)) {
                return;
            }

            if (tag.isStringFamilyType()) {
                if (val instanceof String[]) {
                    dataset.setString(id, dic.vrOf(id), (String[]) val);
                } else {
                    dataset.setString(id, dic.vrOf(id), val.toString());
                }
            } else if (TagType.DICOM_DATE.equals(type) || TagType.DICOM_TIME.equals(type)
                || TagType.DICOM_DATETIME.equals(type)) {
                if (val instanceof TemporalAccessor) {
                    dataset.setDate(id, dic.vrOf(id), TagUtil.toLocalDate((TemporalAccessor) val));
                } else if (val.getClass().isArray()) {
                    dataset.setDate(id, dic.vrOf(id), TagUtil.toLocalDates(val));
                }
            } else if (TagType.INTEGER.equals(type)) {
                if (val instanceof Integer) {
                    dataset.setInt(id, dic.vrOf(id), (Integer) val);
                } else if (val instanceof int[]) {
                    dataset.setInt(id, dic.vrOf(id), (int[]) val);
                }
            } else if (TagType.FLOAT.equals(type)) {
                if (val instanceof Float) {
                    dataset.setFloat(id, dic.vrOf(id), (Float) val);
                } else if (val instanceof float[]) {
                    dataset.setFloat(id, dic.vrOf(id), (float[]) val);
                }
            } else if (TagType.DOUBLE.equals(type)) {
                if (val instanceof Double) {
                    dataset.setDouble(id, dic.vrOf(id), (Double) val);
                } else if (val instanceof double[]) {
                    dataset.setDouble(id, dic.vrOf(id), (double[]) val);
                }
            } else if (TagType.DICOM_SEQUENCE.equals(type) && val instanceof Attributes[]) {
                Attributes[] sIn = (Attributes[]) val;
                Sequence sOut = dataset.newSequence(id, sIn.length);
                for (Attributes attributes : sIn) {
                    sOut.add(new Attributes(attributes));
                }
            }
        }
    }
}
