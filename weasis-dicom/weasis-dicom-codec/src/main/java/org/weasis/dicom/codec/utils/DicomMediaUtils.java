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

package org.weasis.dicom.codec.utils;

import java.awt.Color;
import java.awt.Polygon;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.media.jai.LookupTableJAI;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.data.VR;
import org.dcm4che.util.ByteUtils;
import org.dcm4che.util.TagUtils;
import org.dcm4che.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.image.util.CIELab;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.Messages;
import org.weasis.dicom.codec.PresentationStateReader;
import org.weasis.dicom.codec.geometry.ImageOrientation;

/**
 * @author Nicolas Roduit
 * @author Benoit Jacquemoud
 * @version $Rev$ $Date$
 */
public class DicomMediaUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(DicomMediaUtils.class);

    public static final String weasisRootUID;

    static {
        /**
         * Set value for dicom root UID which should be registered at the
         * http://www.iana.org/assignments/enterprise-numbers <br>
         * Default value is 2.25, this enables users to generate OIDs without any registration procedure
         * 
         * @see http://www.dclunie.com/medical-image-faq/html/part2.html#UUID <br>
         *      http://www.oid-info.com/get/2.25 <br>
         *      http://www.itu.int/ITU-T/asn1/uuid.html<br>
         *      http://healthcaresecprivacy.blogspot.ch/2011/02/creating-and-using-unique-id-uuid-oid.html
         */

        weasisRootUID = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.dicom.root.uid", UIDUtils.getRoot());

        UIDUtils.setRoot(weasisRootUID);
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
                    missingTagList = new ArrayList<String>(requiredTags.length);
                }
                missingTagList.add(TagUtils.toString(tag));
            }
        }
        return (countValues == requiredTags.length);
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
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

    public static final int[] modalityLutAttributes = new int[] { Tag.RescaleIntercept, Tag.RescaleSlope };

    public static boolean containsRequiredModalityLUTAttributes(Attributes dcmItems) {
        return containsRequiredAttributes(dcmItems, modalityLutAttributes);
    }

    public static boolean containsRequiredModalityLUTDataAttributes(Attributes dcmItems) {
        return containsRequiredAttributes(dcmItems, Tag.ModalityLUTType) && containsLUTAttributes(dcmItems);
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
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

    public static final int[] VOILUTWindowLevelAttributes = new int[] { Tag.WindowCenter, Tag.WindowWidth };

    public static boolean containsRequiredVOILUTWindowLevelAttributes(Attributes dcmItems) {
        return containsRequiredAttributes(dcmItems, VOILUTWindowLevelAttributes);
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static final int[] LUTAttributes = //
        new int[] { Tag.LUTDescriptor, Tag.LUTData };

    public static boolean containsLUTAttributes(Attributes dcmItems) {
        return containsRequiredAttributes(dcmItems, LUTAttributes);
    }

    /**
     * 
     * @param dicomLutObject
     *            defines LUT data dicom structure
     * 
     * @param isValueRepresentationSigned
     *            of the descriptor (US or SS) is specified by Pixel Representation (0028,0103).
     * @return LookupTableJAI object if Data Element and Descriptors are consistent
     * 
     * @see - Dicom Standard 2011 - PS 3.3 § C.11 LOOK UP TABLES AND PRESENTATION STATES
     */

    public static LookupTableJAI createLut(Attributes dicomLutObject, boolean isValueRepresentationSigned) {
        if (dicomLutObject == null || dicomLutObject.isEmpty()) {
            return null;
        }

        LookupTableJAI lookupTable = null;

        // Three values of the LUT Descriptor describe the format of the LUT Data in the corresponding Data Element
        int[] descriptor = DicomMediaUtils.getIntAyrrayFromDicomElement(dicomLutObject, Tag.LUTDescriptor, null);

        if (descriptor == null) {
            LOGGER.debug("Missing LUT Descriptor");
        } else if (descriptor.length != 3) {
            LOGGER.debug("Illegal number of LUT Descriptor values \"{}\"", descriptor.length);
        } else {

            // First value is the number of entries in the lookup table.
            // When this value is 0 the number of table entries is equal to 65536 <=> 0x10000.
            int numEntries = (descriptor[0] == 0) ? 65536 : descriptor[0];

            // Second value is mapped to the first entry in the LUT.
            int offset = (numEntries <= 65536) ? //
                ((numEntries <= 256) ? (byte) descriptor[1] : (short) descriptor[1]) : //
                descriptor[1]; // necessary to cast in order to get negative value when present

            // Third value specifies the number of bits for each entry in the LUT Data.
            int numBits = descriptor[2];

            int dataLength = 0; // number of entry values in the LUT Data.

            if (numBits <= 8) { // LUT Data should be stored in 8 bits allocated format

                // LUT Data contains the LUT entry values, assuming data is always unsigned data
                byte[] bData = null;
                try {
                    bData = dicomLutObject.getBytes(Tag.LUTData);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (bData != null && numEntries <= 256 && (bData.length == (numEntries << 1))) {
                    // Some implementations have encoded 8 bit entries with 16 bits allocated, padding the high bits

                    byte[] bDataNew = new byte[numEntries];
                    int byteShift = (dicomLutObject.bigEndian() ? 1 : 0);
                    for (int i = 0; i < numEntries; i++) {
                        bDataNew[i] = bData[(i << 1) + byteShift];
                    }

                    dataLength = bDataNew.length;
                    lookupTable = new LookupTableJAI(bDataNew, offset);

                } else {
                    dataLength = bData.length;
                    lookupTable = new LookupTableJAI(bData, offset); // LUT entry value range should be [0,255]
                }
            } else if (numBits <= 16) { // LUT Data should be stored in 16 bits allocated format

                if (numEntries <= 256) {

                    // LUT Data contains the LUT entry values, assuming data is always unsigned data
                    byte[] bData = null;
                    try {
                        bData = dicomLutObject.getBytes(Tag.LUTData);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if (bData != null && bData.length == (numEntries << 1)) {

                        // Some implementations have encoded 8 bit entries with 16 bits allocated, padding the high bits

                        byte[] bDataNew = new byte[numEntries];
                        int byteShift = (dicomLutObject.bigEndian() ? 1 : 0);
                        for (int i = 0; i < numEntries; i++) {
                            bDataNew[i] = bData[(i << 1) + byteShift];
                        }

                        dataLength = bDataNew.length;
                        lookupTable = new LookupTableJAI(bDataNew, offset);
                    }

                } else {

                    // LUT Data contains the LUT entry values, assuming data is always unsigned data
                    // short[] sData = dicomLutObject.getShorts(Tag.LUTData);
                    int[] iData = DicomMediaUtils.getIntAyrrayFromDicomElement(dicomLutObject, Tag.LUTData, null);
                    if (iData != null) {
                        short[] sData = new short[iData.length];
                        for (int i = 0; i < iData.length; i++) {
                            sData[i] = (short) iData[i];
                        }

                        dataLength = sData.length;
                        lookupTable = new LookupTableJAI(sData, offset, true);
                    }
                }
            } else {
                LOGGER.debug("Illegal number of bits for each entry in the LUT Data");
            }

            if (lookupTable != null) {
                if (dataLength != numEntries) {
                    LOGGER.debug("LUT Data length \"{}\" mismatch number of entries \"{}\" in LUT Descriptor ",
                        dataLength, numEntries);
                }
                if (dataLength > (1 << numBits)) {
                    LOGGER.debug(
                        "Illegal LUT Data length \"{}\" with respect to the number of bits in LUT descriptor \"{}\"",
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
        if (s == null) {
            return null;
        }
        if (s.length == 1) {
            return s[0];
        }
        if (s.length == 0) {
            return ""; //$NON-NLS-1$
        }
        StringBuilder sb = new StringBuilder(s[0]);
        for (int i = 1; i < s.length; i++) {
            sb.append("\\" + s[i]); //$NON-NLS-1$
        }
        return sb.toString();
    }

    public static String[] getStringArrayFromDicomElement(Attributes dicom, int tag) {
        if (dicom == null || !dicom.containsValue(tag)) {
            return null;
        }
        return dicom.getStrings(tag);
        // DicomElement element = dicom.get(tag);
        // if (element == null || element.isEmpty()) {
        // return defaultValue;
        // }
        // return element.getStrings(dicom.getSpecificCharacterSet(), false);
    }

    public static Date getDateFromDicomElement(Attributes dicom, int tag, Date defaultValue) {
        if (dicom == null || !dicom.containsValue(tag)) {
            return null;
        }
        return dicom.getDate(tag, defaultValue);
    }

    private static Float[] toFloatArray(float[] arrays) {
        if (arrays == null) {
            return null;
        }
        Float[] ret = new Float[arrays.length];
        for (int i = 0; i < arrays.length; i++) {
            ret[i] = arrays[i];
        }
        return ret;
    }

    public static Float[] getFloatArrayFromDicomElement(Attributes dicom, int tag) {
        if (dicom == null || !dicom.containsValue(tag)) {
            return null;
        }
        return toFloatArray(DicomMediaUtils.getFloatArrayFromDicomElement(dicom, tag, null));
    }

    public static Float getFloatFromDicomElement(Attributes dicom, int tag, Float defaultValue) {
        if (dicom == null || !dicom.containsValue(tag)) {
            return defaultValue;
        }
        try {
            return dicom.getFloat(tag, defaultValue == null ? 0.0F : defaultValue);
        } catch (NumberFormatException e) {
            LOGGER.error("Cannot parse Float of {}: {} ", TagUtils.toString(tag), e.getMessage());
        }
        return defaultValue;
    }

    public static Integer getIntegerFromDicomElement(Attributes dicom, int tag, Integer defaultValue) {
        if (dicom == null || !dicom.containsValue(tag)) {
            return defaultValue;
        }
        try {
            return dicom.getInt(tag, defaultValue == null ? 0 : defaultValue);
        } catch (NumberFormatException e) {
            LOGGER.error("Cannot parse Integer of {}: {} ", TagUtils.toString(tag), e.getMessage());
        }
        return defaultValue;
    }

    public static Double getDoubleFromDicomElement(Attributes dicom, int tag, Double defaultValue) {
        if (dicom == null || !dicom.containsValue(tag)) {
            return defaultValue;
        }
        try {
            return dicom.getDouble(tag, defaultValue == null ? 0.0 : defaultValue);
        } catch (NumberFormatException e) {
            LOGGER.error("Cannot parse Double of {}: {} ", TagUtils.toString(tag), e.getMessage());
        }
        return defaultValue;
    }

    public static int[] getIntAyrrayFromDicomElement(Attributes dicom, int tag, int[] defaultValue) {
        if (dicom == null || !dicom.containsValue(tag)) {
            return defaultValue;
        }
        try {
            return dicom.getInts(tag);
        } catch (NumberFormatException e) {
            LOGGER.error("Cannot parse int[] of {}: {} ", TagUtils.toString(tag), e.getMessage());
        }
        return defaultValue;
    }

    public static float[] getFloatArrayFromDicomElement(Attributes dicom, int tag, float[] defaultValue) {
        if (dicom == null || !dicom.containsValue(tag)) {
            return defaultValue;
        }
        try {
            return dicom.getFloats(tag);
        } catch (NumberFormatException e) {
            LOGGER.error("Cannot parse float[] of {}: {} ", TagUtils.toString(tag), e.getMessage());
        }
        return defaultValue;
    }

    public static double[] getDoubleArrayFromDicomElement(Attributes dicom, int tag, double[] defaultValue) {
        if (dicom == null || !dicom.containsValue(tag)) {
            return defaultValue;
        }
        try {
            return dicom.getDoubles(tag);
        } catch (NumberFormatException e) {
            LOGGER.error("Cannot parse double[] of {}: {} ", TagUtils.toString(tag), e.getMessage());
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

    public static void buildLUTs(HashMap<TagW, Object> dicomTagMap) {
        if (dicomTagMap != null) {

            Integer pixelRepresentation = (Integer) dicomTagMap.get(TagW.PixelRepresentation);
            boolean isPixelRepresentationSigned = (pixelRepresentation != null && pixelRepresentation != 0);

            // NOTE : Either a Modality LUT Sequence containing a single Item or Rescale Slope and Intercept values
            // shall be present but not both (@see Dicom Standard 2011 - PS 3.3 § C.11.1 Modality LUT Module)

            Attributes mLutItems = (Attributes) dicomTagMap.get(TagW.ModalityLUTSequence);

            if (containsRequiredModalityLUTDataAttributes(mLutItems)) {
                boolean canApplyMLUT = true;
                String modlality = (String) dicomTagMap.get(TagW.Modality);
                if ("XA".equals(modlality) || "XRF".equals(modlality)) {
                    // See PS 3.4 N.2.1.2.
                    String pixRel = (String) dicomTagMap.get(TagW.PixelIntensityRelationship);
                    if (pixRel != null && ("LOG".equalsIgnoreCase(pixRel) || "DISP".equalsIgnoreCase(pixRel))) {
                        canApplyMLUT = false;
                        LOGGER
                            .debug("Modality LUT Sequence shall NOT be applied according to PixelIntensityRelationship"); //$NON-NLS-1$
                    }
                }

                if (canApplyMLUT) {
                    DicomMediaUtils.setTagNoNull(dicomTagMap, TagW.ModalityLUTData,
                        createLut(mLutItems, isPixelRepresentationSigned));
                    DicomMediaUtils.setTagNoNull(dicomTagMap, TagW.ModalityLUTType,
                        getStringFromDicomElement(mLutItems, Tag.ModalityLUTType));
                    DicomMediaUtils.setTagNoNull(dicomTagMap, TagW.ModalityLUTExplanation, // Optional Tag
                        getStringFromDicomElement(mLutItems, Tag.LUTExplanation));
                }
            }

            if (LOGGER.isDebugEnabled()) {

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

                if (dicomTagMap.get(TagW.ModalityLUTData) != null) {
                    if (dicomTagMap.get(TagW.RescaleIntercept) != null) {
                        LOGGER.debug("Modality LUT Sequence shall NOT be present if Rescale Intercept is present"); //$NON-NLS-1$
                    }
                    if (dicomTagMap.get(TagW.ModalityLUTType) == null) {
                        LOGGER.debug("Modality Type is required if Modality LUT Sequence is present. "); //$NON-NLS-1$
                    }
                } else if (dicomTagMap.get(TagW.RescaleIntercept) != null) {
                    if (dicomTagMap.get(TagW.RescaleSlope) == null) {
                        LOGGER.debug("Modality Rescale Slope is required if Rescale Intercept is present."); //$NON-NLS-1$
                    }
                } else {
                    LOGGER.debug("Modality Rescale Intercept is required if Modality LUT Sequence is not present. "); //$NON-NLS-1$
                }
            }

            // NOTE : If any VOI LUT Table is included by an Image, a Window Width and Window Center or the VOI LUT
            // Table, but not both, may be applied to the Image for display. Inclusion of both indicates that multiple
            // alternative views may be presented. (@see Dicom Standard 2011 - PS 3.3 § C.11.2 VOI LUT Module)

            Sequence voiLUTSequence = (Sequence) dicomTagMap.get(TagW.VOILUTSequence);

            if (voiLUTSequence != null && !voiLUTSequence.isEmpty()) {
                LookupTableJAI[] voiLUTsData = new LookupTableJAI[voiLUTSequence.size()];
                String[] voiLUTsExplanation = new String[voiLUTSequence.size()];

                boolean isOutModalityLutSigned = isPixelRepresentationSigned;

                // Evaluate outModality min value if signed
                LookupTableJAI modalityLookup = (LookupTableJAI) dicomTagMap.get(TagW.ModalityLUTData);

                Integer smallestPixelValue = (Integer) dicomTagMap.get(TagW.SmallestImagePixelValue);
                float minPixelValue = (smallestPixelValue == null) ? 0.0f : smallestPixelValue.floatValue();

                if (modalityLookup == null) {
                    Float intercept = (Float) dicomTagMap.get(TagW.RescaleIntercept);
                    Float slope = (Float) dicomTagMap.get(TagW.RescaleSlope);

                    slope = (slope == null) ? 1.0f : slope;
                    intercept = (intercept == null) ? 0.0f : intercept;

                    if ((minPixelValue * slope + intercept) < 0) {
                        isOutModalityLutSigned = true;
                    }
                } else {
                    int minInLutValue = modalityLookup.getOffset();
                    int maxInLutValue = modalityLookup.getOffset() + modalityLookup.getNumEntries() - 1;

                    if (minPixelValue >= minInLutValue && minPixelValue <= maxInLutValue
                        && modalityLookup.lookup(0, (int) minPixelValue) < 0) {
                        isOutModalityLutSigned = true;
                    }
                }

                for (int i = 0; i < voiLUTSequence.size(); i++) {
                    Attributes voiLUTobj = voiLUTSequence.get(i);
                    if (containsLUTAttributes(voiLUTobj)) {
                        voiLUTsData[i] = createLut(voiLUTobj, isOutModalityLutSigned);
                        voiLUTsExplanation[i] = getStringFromDicomElement(voiLUTobj, Tag.LUTExplanation);
                    } else {
                        LOGGER.info("Cannot read VOI LUT Data [{}]", i); //$NON-NLS-1$
                    }
                }

                DicomMediaUtils.setTag(dicomTagMap, TagW.VOILUTsData, voiLUTsData);
                DicomMediaUtils.setTag(dicomTagMap, TagW.VOILUTsExplanation, voiLUTsExplanation); // Optional Tag
            }

            if (LOGGER.isDebugEnabled()) {
                // If multiple items are present in VOI LUT Sequence, only one may be applied to the
                // Image for display. Multiple items indicate that multiple alternative views may be presented.

                // If multiple Window center and window width values are present, both Attributes shall have the same
                // number of values and shall be considered as pairs. Multiple values indicate that multiple alternative
                // views may be presented

                Float[] windowCenterDefaultTagArray = (Float[]) dicomTagMap.get(TagW.WindowCenter);
                Float[] windowWidthDefaultTagArray = (Float[]) dicomTagMap.get(TagW.WindowWidth);

                if (windowCenterDefaultTagArray == null && windowWidthDefaultTagArray != null) {
                    LOGGER.debug("VOI Window Center is required if Window Width is present"); //$NON-NLS-1$
                } else if (windowWidthDefaultTagArray == null && windowCenterDefaultTagArray != null) {
                    LOGGER.debug("VOI Window Width is required if Window Center is present"); //$NON-NLS-1$
                } else if (windowCenterDefaultTagArray != null && windowWidthDefaultTagArray != null
                    && windowWidthDefaultTagArray.length != windowCenterDefaultTagArray.length) {
                    LOGGER.debug("VOI Window Center and Width attributes have different number of values : {} // {}", //$NON-NLS-1$
                        windowCenterDefaultTagArray, windowWidthDefaultTagArray);
                }
            }

            /**
             * @see - Dicom Standard 2011 - PS 3.3 § C.11.6 Softcopy Presentation LUT Module
             * 
             *      Presentation LUT Module is always implicitly specified to apply over the full range of output of the
             *      preceding transformation, and it never selects a subset or superset of the that range (unlike the
             *      VOI LUT).
             */
            Attributes prLUTSequence = (Attributes) dicomTagMap.get(TagW.PresentationLUTSequence);
            if (prLUTSequence != null) {
                DicomMediaUtils.setTag(dicomTagMap, TagW.PRLUTsData, createLut(prLUTSequence, false));
                DicomMediaUtils.setTag(dicomTagMap, TagW.PRLUTsExplanation,
                    getStringFromDicomElement(prLUTSequence, Tag.LUTExplanation)); // Optional Tag
                // TODO implement PresentationLUTSequence renderer
            }
        }
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
                e.printStackTrace();
            }
            if (signed) {
                if ((result & (1 << (stored - 1))) != 0) {
                    int andmask = (1 << stored) - 1;
                    int ormask = ~andmask;
                    result |= ormask;
                }
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

    public static String buildPatientName(String rawName) {
        String name = rawName == null ? DicomMediaIO.NO_VALUE : rawName;
        if (name.trim().equals("")) { //$NON-NLS-1$
            name = DicomMediaIO.NO_VALUE;
        }
        return buildPersonName(name);
    }

    public static String buildPatientSex(String val) {
        // Sex attribute can have the following values: M(male), F(female), or O(other)
        String name = val == null ? "O" : val;
        return name.startsWith("F") ? Messages.getString("DicomMediaIO.female") : name.startsWith("M") ? Messages.getString("DicomMediaIO.Male") : Messages.getString("DicomMediaIO.other"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
    }

    public static String buildPersonName(String name) {
        if (name == null) {
            return null;
        }
        /*
         * Further internationalization issues arise in countries where the language has a phonetic or ideographic
         * representation, such as in Japan and Korea. For these situations, DICOM allows up to three “component
         * groups,” the first a single-byte representation as is used for western languages, then an ideographic (Kanji
         * or Hanga) representation and then a phonetic representation (Hiragana or Hangul). These are separated by ‘=’
         * (0x3d) characters.
         */
        StringBuilder buf = new StringBuilder();
        String[] names = name.split("=");
        for (int k = 0; k < names.length; k++) {
            if (k > 0) {
                buf.append("=");
            }
            /*
             * In DICOM “family name^given name^middle name^prefix^suffix”
             * 
             * In HL7 “family name^given name^middle name^suffix^prefix^ degree”
             */
            String[] vals = names[k].split("\\^");

            for (int i = 0; i < vals.length; i++) {
                if (!"".equals(vals[i])) {
                    if (i >= 3) {
                        buf.append(", ");
                    } else {
                        buf.append(" ");
                    }
                }
                buf.append(vals[i]);
            }

        }
        return buf.toString().trim();
    }

    public static String buildPatientPseudoUID(String patientID, String issuerOfPatientID, String patientName,
        Date birthdate) {
        /*
         * IHE RAD TF-­‐2: 4.16.4.2.2.5.3
         * 
         * The Image Display shall not display FrameSets for multiple patients simultaneously. Only images with exactly
         * the same value for Patient’s ID (0010,0020) and Patient’s Name (0010,0010) shall be displayed at the same
         * time (other Patient-level attributes may be different, empty or absent). Though it is possible that the same
         * patient may have slightly different identifying attributes in different DICOM images performed at different
         * sites or on different occasions, it is expected that such differences will have been reconciled prior to the
         * images being provided to the Image Display (e.g., in the Image Manager/Archive or by the Portable Media
         * Creator).
         */
        // Build a global identifier for the patient.
        StringBuilder buffer = new StringBuilder(patientID == null ? DicomMediaIO.NO_VALUE : patientID);
        if (issuerOfPatientID != null && !"".equals(issuerOfPatientID.trim())) { //$NON-NLS-1$
            // patientID + issuerOfPatientID => should be unique globally
            buffer.append(issuerOfPatientID);
        }
        if (birthdate != null) {
            buffer.append(TagW.dicomformatDate.format(birthdate).toString());
        }
        if (patientName != null) {
            buffer.append(patientName.toUpperCase());
        }

        return buffer.toString();

    }

    public static void setTag(Map<TagW, Object> tags, TagW tag, Object value) {
        if (tag != null) {
            tags.put(tag, value);
        }
    }

    public static void setTagNoNull(Map<TagW, Object> tags, TagW tag, Object value) {
        if (tag != null && value != null) {
            tags.put(tag, value);
        }
    }

    public static void writeMetaData(MediaSeriesGroup group, Attributes header) {
        if (group == null || header == null) {
            return;
        }
        // Patient Group
        if (TagW.PatientPseudoUID.equals(group.getTagID())) {
            // -------- Mandatory Tags --------
            group.setTag(TagW.PatientID, header.getString(Tag.PatientID, DicomMediaIO.NO_VALUE));
            group.setTag(TagW.PatientName, buildPatientName(header.getString(Tag.PatientName)));
            // -------- End of Mandatory Tags --------

            group.setTagNoNull(TagW.PatientBirthDate, getDateFromDicomElement(header, Tag.PatientBirthDate, null));
            group.setTagNoNull(TagW.PatientBirthTime, getDateFromDicomElement(header, Tag.PatientBirthTime, null));
            group.setTag(TagW.PatientSex, DicomMediaUtils.buildPatientSex(header.getString(Tag.PatientSex)));
            group.setTagNoNull(TagW.IssuerOfPatientID, header.getString(Tag.IssuerOfPatientID));
            group.setTagNoNull(TagW.PatientWeight, getFloatFromDicomElement(header, Tag.PatientWeight, null));
            group.setTagNoNull(TagW.PatientComments, header.getString(Tag.PatientComments));
        }
        // Study Group
        else if (TagW.StudyInstanceUID.equals(group.getTagID())) {
            // -------- Mandatory Tags --------
            // StudyInstanceUID is the unique identifying tag for this study group
            // -------- End of Mandatory Tags --------

            group.setTagNoNull(TagW.StudyID, header.getString(Tag.StudyID));
            group.setTagNoNull(TagW.StudyTime, getDateFromDicomElement(header, Tag.StudyTime, null));
            // Merge date and time, used in display
            group.setTagNoNull(
                TagW.StudyDate,
                TagW.dateTime(getDateFromDicomElement(header, Tag.StudyDate, null),
                    (Date) group.getTagValue(TagW.StudyTime)));
            group.setTagNoNull(TagW.StudyDescription, header.getString(Tag.StudyDescription));
            group.setTagNoNull(TagW.StudyComments, header.getString(Tag.StudyComments));

            group.setTagNoNull(TagW.AccessionNumber, header.getString(Tag.AccessionNumber));
            group.setTagNoNull(TagW.ModalitiesInStudy,
                DicomMediaUtils.getStringArrayFromDicomElement(header, Tag.ModalitiesInStudy));
            group.setTagNoNull(TagW.NumberOfStudyRelatedInstances,
                getIntegerFromDicomElement(header, Tag.NumberOfStudyRelatedInstances, null));
            group.setTagNoNull(TagW.NumberOfStudyRelatedSeries,
                getIntegerFromDicomElement(header, Tag.NumberOfStudyRelatedSeries, null));

            // TODO sequence: define data structure
            group.setTagNoNull(TagW.ProcedureCodeSequence, header.getSequence(Tag.ProcedureCodeSequence));
        }
        // Series Group
        else if (TagW.SubseriesInstanceUID.equals(group.getTagID())) {
            // -------- Mandatory Tags --------
            // SubseriesInstanceUID is the unique identifying tag for this series group
            group.setTag(TagW.SeriesInstanceUID, header.getString(Tag.SeriesInstanceUID, DicomMediaIO.NO_VALUE));
            group.setTag(TagW.Modality, header.getString(Tag.Modality, DicomMediaIO.NO_VALUE));
            // -------- End of Mandatory Tags --------

            group.setTagNoNull(
                TagW.SeriesDate,
                TagW.dateTime(getDateFromDicomElement(header, Tag.SeriesDate, null),
                    getDateFromDicomElement(header, Tag.SeriesTime, null)));

            group.setTagNoNull(TagW.SeriesDescription, header.getString(Tag.SeriesDescription));
            group.setTagNoNull(TagW.RetrieveAETitle,
                DicomMediaUtils.getStringArrayFromDicomElement(header, Tag.RetrieveAETitle));
            group.setTagNoNull(TagW.ReferringPhysicianName,
                buildPersonName(header.getString(Tag.ReferringPhysicianName)));
            group.setTagNoNull(TagW.InstitutionName, header.getString(Tag.InstitutionName));
            group.setTagNoNull(TagW.InstitutionalDepartmentName, header.getString(Tag.InstitutionalDepartmentName));
            group.setTagNoNull(TagW.StationName, header.getString(Tag.StationName));
            group.setTagNoNull(TagW.Manufacturer, header.getString(Tag.Manufacturer));
            group.setTagNoNull(TagW.ManufacturerModelName, header.getString(Tag.ManufacturerModelName));
            // TODO sequence: define data structure
            group.setTagNoNull(TagW.ReferencedPerformedProcedureStepSequence,
                header.getSequence(Tag.ReferencedPerformedProcedureStepSequence));
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
            // TODO sequence: define data structure
            group.setTagNoNull(TagW.ReferencedImageSequence, header.getSequence(Tag.ReferencedImageSequence));
            group.setTagNoNull(TagW.FrameOfReferenceUID, header.getString(Tag.FrameOfReferenceUID));
            group.setTagNoNull(TagW.NumberOfSeriesRelatedInstances,
                getIntegerFromDicomElement(header, Tag.NumberOfSeriesRelatedInstances, null));
            group.setTagNoNull(TagW.PerformedProcedureStepStartDate,
                getDateFromDicomElement(header, Tag.PerformedProcedureStepStartDate, null));
            group.setTagNoNull(TagW.PerformedProcedureStepStartTime,
                getDateFromDicomElement(header, Tag.PerformedProcedureStepStartTime, null));
            // TODO sequence: define data structure
            group.setTagNoNull(TagW.RequestAttributesSequence, header.getSequence(Tag.RequestAttributesSequence));

        }
    }

    public static void computeSlicePositionVector(HashMap<TagW, Object> tagList) {
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

    public static Area buildShutterArea(Attributes dcmObject) {
        Area shape = null;
        String shutterShape = getStringFromDicomElement(dcmObject, Tag.ShutterShape);
        if (shutterShape != null) {
            // RECTANGULAR is legal
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
                    int radius = getIntegerFromDicomElement(dcmObject, Tag.RadiusOfCircularShutter, 0);
                    // Thanks Dicom for reversing x,y by row,column
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
                        // Thanks Dicom for reversing x,y by row,column
                        polygon.addPoint(points[i * 2 + 1], points[i * 2]);
                    }
                    if (shape == null) {
                        shape = new Area(polygon);
                    } else {
                        shape.intersect(new Area(polygon));
                    }
                }
            }
        }
        return shape;
    }

    public static void writeFunctionalGroupsSequence(HashMap<TagW, Object> tagList, Attributes dcm) {
        if (dcm != null && tagList != null) {

            /**
             * @see - Dicom Standard 2011 - PS 3.3 §C.7.6.16.2.1 Pixel Measures Macro
             */
            Attributes macroPixelMeasures = dcm.getNestedDataset(Tag.PixelMeasuresSequence);
            if (macroPixelMeasures != null) {
                setTagNoNull(tagList, TagW.PixelSpacing,
                    DicomMediaUtils.getDoubleArrayFromDicomElement(macroPixelMeasures, Tag.PixelSpacing, null));
                setTagNoNull(tagList, TagW.SliceThickness,
                    getDoubleFromDicomElement(macroPixelMeasures, Tag.SliceThickness, null));
            }

            /**
             * @see - Dicom Standard 2011 - PS 3.3 §C.7.6.16.2.2 Frame Content Macro
             */
            Attributes macroFrameContent = dcm.getNestedDataset(Tag.FrameContentSequence);
            if (macroFrameContent != null) {
                setTagNoNull(tagList, TagW.FrameAcquisitionNumber,
                    getIntegerFromDicomElement(macroFrameContent, Tag.FrameAcquisitionNumber, null));
                setTagNoNull(tagList, TagW.StackID, macroFrameContent.getString(Tag.StackID));
                setTagNoNull(tagList, TagW.InstanceNumber,
                    getIntegerFromDicomElement(macroFrameContent, Tag.InStackPositionNumber, null));
                setTagNoNull(tagList, TagW.TemporalPositionIndex,
                    getIntegerFromDicomElement(macroFrameContent, Tag.TemporalPositionIndex, null));
            }

            /**
             * @see - Dicom Standard 2011 - PS 3.3 § C.7.6.16.2.3 Plane Position (Patient) Macro
             */
            Attributes macroPlanePosition = dcm.getNestedDataset(Tag.PlanePositionSequence);
            if (macroPlanePosition != null) {
                setTagNoNull(tagList, TagW.ImagePositionPatient,
                    macroPlanePosition.getDoubles(Tag.ImagePositionPatient));
            }

            /**
             * @see - Dicom Standard 2011 - PS 3.3 § C.7.6.16.2.4 Plane Orientation (Patient) Macro
             */
            Attributes macroPlaneOrientation = dcm.getNestedDataset(Tag.PlaneOrientationSequence);
            if (macroPlaneOrientation != null) {
                double[] imgOrientation = macroPlaneOrientation.getDoubles(Tag.ImageOrientationPatient);
                setTagNoNull(tagList, TagW.ImageOrientationPatient, imgOrientation);
                setTagNoNull(tagList, TagW.ImageOrientationPlane,
                    ImageOrientation.makeImageOrientationLabelFromImageOrientationPatient(imgOrientation));
            }

            /**
             * @see - Dicom Standard 2011 - PS 3.3 § C.7.6.16.2.8 Frame Anatomy Macro
             */
            Attributes macroFrameAnatomy = dcm.getNestedDataset(Tag.FrameAnatomySequence);
            if (macroFrameAnatomy != null) {
                setTagNoNull(tagList, TagW.ImageLaterality, macroFrameAnatomy.getString(Tag.FrameLaterality));
            }

            /**
             * Specifies the attributes of the Pixel Value Transformation Functional Group. This is equivalent with the
             * Modality LUT transformation in non Multi-frame IODs. It constrains the Modality LUT transformation step
             * in the grayscale rendering pipeline to be an identity transformation.
             * 
             * @see - Dicom Standard 2011 - PS 3.3 § C.7.6.16.2.9-b Pixel Value Transformation
             */
            applyModalityLutModule(dcm.getNestedDataset(Tag.PixelValueTransformationSequence), tagList,
                Tag.PixelValueTransformationSequence);

            /**
             * Specifies the attributes of the Frame VOI LUT Functional Group. It contains one or more sets of linear or
             * sigmoid window values and/or one or more sets of lookup tables
             * 
             * @see - Dicom Standard 2011 - PS 3.3 § C.7.6.16.2.10b Frame VOI LUT With LUT Macro
             */
            applyVoiLutModule(dcm.getNestedDataset(Tag.FrameVOILUTSequence), tagList, Tag.FrameVOILUTSequence);

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
                Area shape = buildShutterArea(macroFrameDisplayShutter);
                if (shape != null) {
                    setTagNoNull(tagList, TagW.ShutterFinalShape, shape);
                    Integer psVal =
                        getIntegerFromDicomElement(macroFrameDisplayShutter, Tag.ShutterPresentationValue, null);
                    setTagNoNull(tagList, TagW.ShutterPSValue, psVal);
                    float[] rgb =
                        CIELab.convertToFloatLab(DicomMediaUtils.getIntAyrrayFromDicomElement(macroFrameDisplayShutter,
                            Tag.ShutterPresentationColorCIELabValue, null));
                    Color color =
                        rgb == null ? null : PresentationStateReader.getRGBColor(psVal == null ? 0 : psVal, rgb,
                            (int[]) null);
                    setTagNoNull(tagList, TagW.ShutterRGBColor, color);
                }
            }

            /**
             * @see - Dicom Standard 2011 - PS 3.3 §C.8.13.5.1 MR Image Frame Type Macro
             */

            Attributes imageFrameType = dcm.getNestedDataset(Tag.MRImageFrameTypeSequence);
            if (imageFrameType == null) {
                // C.8.15.3.1 CT Image Frame Type Macro
                imageFrameType = dcm.getNestedDataset(Tag.CTImageFrameTypeSequence);
            }
            if (imageFrameType == null) {
                // C.8.14.3.1 MR Spectroscopy Frame Type Macro
                imageFrameType = dcm.getNestedDataset(Tag.MRSpectroscopyFrameTypeSequence);
            }
            if (imageFrameType == null) {
                // C.8.22.5.1 PET Frame Type Macro
                imageFrameType = dcm.getNestedDataset(Tag.PETFrameTypeSequence);
            }

            if (imageFrameType != null) {
                // Type of Frame. A multi-valued attribute analogous to the Image Type (0008,0008).
                // Enumerated Values and Defined Terms are the same as those for the four values of the Image Type
                // (0008,0008) attribute, except that the value MIXED is not allowed. See C.8.16.1 and C.8.13.3.1.1.
                setTagNoNull(tagList, TagW.FrameType, imageFrameType.getString(Tag.FrameType));
            }
        }
    }

    public static boolean writePerFrameFunctionalGroupsSequence(HashMap<TagW, Object> tagList, Attributes header,
        int index) {
        if (header != null && tagList != null) {
            /*
             * C.7.6.16 The number of Items shall be the same as the number of frames in the Multi-frame image.
             */
            Attributes a = header.getNestedDataset(Tag.PerFrameFunctionalGroupsSequence, index);
            if (a != null) {
                DicomMediaUtils.writeFunctionalGroupsSequence(tagList, a);
                return true;
            }
        }
        return false;
    }

    public static void applyModalityLutModule(Attributes mLutItems, HashMap<TagW, Object> tagList, Integer seqParentTag) {
        if (mLutItems != null && tagList != null) {
            // Overrides Modality LUT Transformation attributes only if sequence is consistent
            if (containsRequiredModalityLUTAttributes(mLutItems)) {
                String modlality = (String) tagList.get(TagW.Modality);
                if ("MR".equals(modlality) || "XA".equals(modlality) || "XRF".equals(modlality)
                    || "PT".equals(modlality)) {
                    /*
                     * IHE BIR: 4.16.4.2.2.5.4
                     * 
                     * The grayscale rendering pipeline shall be appropriate to the SOP Class and modality. If Rescale
                     * Slope and Rescale Intercept are present in the image for MR and PET and XA/XRF images, they shall
                     * be ignored from the perspective of applying window values, and for those SOP Classes, window
                     * values shall be applied directly to the stored pixel values without rescaling.
                     */
                    LOGGER.info("Do not apply Modality LUT to {}", modlality);//$NON-NLS-1$
                } else {
                    setTagNoNull(tagList, TagW.RescaleSlope,
                        getFloatFromDicomElement(mLutItems, Tag.RescaleSlope, null));
                    setTagNoNull(tagList, TagW.RescaleIntercept,
                        getFloatFromDicomElement(mLutItems, Tag.RescaleIntercept, null));
                    setTagNoNull(tagList, TagW.RescaleType, getStringFromDicomElement(mLutItems, Tag.RescaleType));
                }

            } else if (seqParentTag != null) {
                LOGGER.info(
                    "Cannot apply Modality LUT from {} with inconsistent attributes", TagUtils.toString(seqParentTag));//$NON-NLS-1$
            }

            // Should exist only in root DICOM (when seqParentTag == null)
            Attributes mLutSeq = mLutItems.getNestedDataset(Tag.ModalityLUTSequence);
            if (mLutSeq != null && containsRequiredModalityLUTDataAttributes(mLutSeq)) {
                setTagNoNull(tagList, TagW.ModalityLUTSequence, mLutItems.getNestedDataset(Tag.ModalityLUTSequence));
            }
        }
    }

    public static void applyVoiLutModule(Attributes voiItems, HashMap<TagW, Object> tagList, Integer seqParentTag) {
        if (voiItems != null && tagList != null) {
            // Overrides VOI LUT Transformation attributes only if sequence is consistent
            if (containsRequiredVOILUTWindowLevelAttributes(voiItems)) {
                setTagNoNull(tagList, TagW.WindowWidth, getFloatArrayFromDicomElement(voiItems, Tag.WindowWidth));
                setTagNoNull(tagList, TagW.WindowCenter, getFloatArrayFromDicomElement(voiItems, Tag.WindowCenter));
                setTagNoNull(tagList, TagW.WindowCenterWidthExplanation,
                    getStringArrayFromDicomElement(voiItems, Tag.WindowCenterWidthExplanation));
                setTagNoNull(tagList, TagW.VOILutFunction, getStringFromDicomElement(voiItems, Tag.VOILUTFunction));
                setTagNoNull(tagList, TagW.VOILUTSequence, voiItems.getSequence(Tag.VOILUTSequence));
            }
            // else if (seqParentTag != null) {
            // LOGGER.info(
            //                    "Cannot apply VOI LUT from {} with inconsistent attributes", TagUtils.toString(seqParentTag));//$NON-NLS-1$
            // }

            Sequence vLutSeq = voiItems.getSequence(Tag.VOILUTSequence);
            if (vLutSeq != null && vLutSeq.size() > 0) {
                setTagNoNull(tagList, TagW.VOILUTSequence, vLutSeq);
            }
        }
    }

    public static void applyPrLutModule(Attributes dcmItems, HashMap<TagW, Object> tagList) {
        if (dcmItems != null && tagList != null) {
            // TODO implement 1.2.840.10008.5.1.4.1.1.11.2 -5 color and xray
            if ("1.2.840.10008.5.1.4.1.1.11.1".equals(dcmItems.getString(Tag.SOPClassUID))) {
                /**
                 * @see - Dicom Standard 2011 - PS 3.3 § C.11.6 Softcopy Presentation LUT Module
                 */
                Attributes presentationLUT = dcmItems.getNestedDataset(Tag.PresentationLUTSequence);
                if (presentationLUT != null) {
                    setTagNoNull(tagList, TagW.PresentationLUTSequence, presentationLUT);
                } else {
                    // value: INVERSE, IDENTITY
                    // INVERSE => must inverse values (same as monochrome 1)
                    setTagNoNull(tagList, TagW.PresentationLUTShape, dcmItems.getString(Tag.PresentationLUTShape));
                }
            }
        }
    }

    public static void readPRLUTsModule(Attributes dcmItems, HashMap<TagW, Object> tagList) {
        if (dcmItems != null && tagList != null) {
            // Modality LUT Module
            applyModalityLutModule(dcmItems, tagList, null);

            // VOI LUT Module
            applyVoiLutModule(dcmItems.getNestedDataset(Tag.SoftcopyVOILUTSequence), tagList,
                Tag.SoftcopyVOILUTSequence);

            // Presentation LUT Module
            applyPrLutModule(dcmItems, tagList);
        }
    }

    public static void computeSUVFactor(Attributes dicomObject, HashMap<TagW, Object> tagList, int index) {
        // From vendor neutral code at http://qibawiki.rsna.org/index.php?title=Standardized_Uptake_Value_%28SUV%29
        String modlality = (String) tagList.get(TagW.Modality);
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
                    Float weight = getFloatFromDicomElement(dicomObject, Tag.PatientWeight, 0.0f);
                    if (weight != 0.0f) {
                        Attributes dcm =
                            dicomObject.getNestedDataset(Tag.RadiopharmaceuticalInformationSequence, index);
                        if (dcm != null) {
                            Float totalDose = getFloatFromDicomElement(dcm, Tag.RadionuclideTotalDose, null);
                            Float halfLife = getFloatFromDicomElement(dcm, Tag.RadionuclideHalfLife, null);
                            Date injectTime = getDateFromDicomElement(dcm, Tag.RadiopharmaceuticalStartTime, null);
                            Date injectDateTime =
                                getDateFromDicomElement(dcm, Tag.RadiopharmaceuticalStartDateTime, null);
                            Date acquisitionDateTime =
                                TagW.dateTime((Date) tagList.get(TagW.AcquisitionDate),
                                    (Date) tagList.get(TagW.AcquisitionTime));
                            Date scanDate = getDateFromDicomElement(dicomObject, Tag.SeriesDate, null);
                            if ("START".equals(dicomObject.getString(Tag.DecayCorrection)) && totalDose != null //$NON-NLS-1$
                                && halfLife != null && acquisitionDateTime != null
                                && (injectDateTime != null || (scanDate != null && injectTime != null))) {
                                double time = 0.0;
                                long scanDateTime =
                                    TagW.dateTime(scanDate, getDateFromDicomElement(dicomObject, Tag.SeriesTime, null))
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
                                        injectDateTime = TagW.dateTime(scanDate, injectTime);
                                        time = scanDateTime - injectDateTime.getTime();
                                    }

                                } else {
                                    time = scanDateTime - injectDateTime.getTime();
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
                    if ("Philips PET Private Group".equals(privateTagCreator) && privateSUVFactor != 0.0) { //$NON-NLS-1$
                        suvFactor = privateSUVFactor;
                        // units= "g/ml";
                    }
                } else if ("GML".equals(units)) { //$NON-NLS-1$
                    suvFactor = 1.0;
                    // UNIT
                    // String unit = dicomObject.getString(Tag.SUVType);

                }
                if (suvFactor != 0.0) {
                    DicomMediaUtils.setTag(tagList, TagW.SuvFactor, suvFactor);
                }
            }
        }
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Creates a Dicom Key Object from another dicom KOS document copying its CurrentRequestedProcedureEvidences and
     * keeping its patient informations. It's the user responsibility to manage the studyUID and serieUID values. Given
     * UIDs parameters are supposed to be valid and won't be verified. If their value is null a new one will be
     * generated instead.
     * 
     * @param dicomObject
     * @param description
     * @param studyInstanceUID
     *            can be null
     * @param seriesInstanceUID
     *            can be null
     * @return
     */

    // public static Attributes createDicomKeyObject(Attributes dicomObject, String description, String
    // studyInstanceUID,
    // String seriesInstanceUID) {
    //
    // if (description == null || "".equals(description)) {
    // description = "new KO selection";
    // }
    //
    // String patientID = dicomObject.getString(Tag.PatientID);
    // String patientName = dicomObject.getString(Tag.PatientName);
    // Date patientBirthdate = dicomObject.getDate(Tag.PatientBirthDate);
    //
    // // TODO see implementation in dcm4che3
    //
    // // DicomObject newDicomKeyObject =
    // // createDicomKeyObject(patientID, patientName, patientBirthdate, description, studyInstanceUID,
    // // seriesInstanceUID);
    // //
    // // HierachicalSOPInstanceReference[] referencedStudySequence =
    // // new KODocumentModule(dicomObject).getCurrentRequestedProcedureEvidences();
    // //
    // // new KODocumentModule(newDicomKeyObject).setCurrentRequestedProcedureEvidences(referencedStudySequence);
    // // return newDicomKeyObject;
    //
    // return null;
    // }

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

        if (seriesInstanceUID == null || "".equals(seriesInstanceUID)) {
            seriesInstanceUID = UIDUtils.createUID();
        }

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
        Map<String, Set<KeyObjectSelectionCode>> resourcesByContextID =
            new HashMap<String, Set<KeyObjectSelectionCode>>();

        for (KeyObjectSelectionCode code : codeByValue.values()) {
            Set<KeyObjectSelectionCode> resourceSet = resourcesByContextID.get(code.contextGroupID);
            if (resourceSet == null) {
                resourcesByContextID.put(code.contextGroupID, resourceSet =
                    new TreeSet<DicomMediaUtils.KeyObjectSelectionCode>());
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

        final Attributes documentTitle = codeByValue.get("113000").toCodeItem();
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

        final String seriesNumber = "999"; // A number that identifies the Series. (default: 999)
        final String instanceNumber = "1"; // A number that identifies the Document. (default: 1)

        // TODO - add ability to override default instanceNumber and seriesNumber from given parameters in case many
        // KEY OBJECT DOCUMENT SERIES and KEY OBJECT DOCUMENT are build for the same Study in the same context

        final int[] PATIENT_AND_STUDY_ATTRS =
            { Tag.SpecificCharacterSet, Tag.StudyDate, Tag.StudyTime, Tag.AccessionNumber,
                Tag.IssuerOfAccessionNumberSequence, Tag.ReferringPhysicianName, Tag.PatientName, Tag.PatientID,
                Tag.IssuerOfPatientID, Tag.PatientBirthDate, Tag.PatientSex, Tag.StudyInstanceUID, Tag.StudyID };
        Arrays.sort(PATIENT_AND_STUDY_ATTRS);

        /**
         * @note : Add selected attributes from another Attributes object to this. The specified array of tag values
         *       must be sorted (as by the {@link java.util.Arrays#sort(int[])} method) prior to making this call.
         */
        Attributes dKOS = new Attributes(dicomSourceAttribute, PATIENT_AND_STUDY_ATTRS);

        dKOS.setString(Tag.SOPClassUID, VR.UI, UID.KeyObjectSelectionDocumentStorage);
        dKOS.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
        dKOS.setDate(Tag.ContentDateAndTime, new Date());
        dKOS.setString(Tag.Modality, VR.CS, "KO");
        dKOS.setNull(Tag.ReferencedPerformedProcedureStepSequence, VR.SQ);
        dKOS.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUID);
        dKOS.setString(Tag.SeriesNumber, VR.IS, seriesNumber);
        dKOS.setString(Tag.InstanceNumber, VR.IS, instanceNumber);
        dKOS.setString(Tag.ValueType, VR.CS, "CONTAINER");
        dKOS.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE");
        dKOS.newSequence(Tag.ConceptNameCodeSequence, 1).add(documentTitle);
        dKOS.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1);

        Attributes templateIdentifier = new Attributes(2);
        templateIdentifier.setString(Tag.MappingResource, VR.CS, "DCMR");
        templateIdentifier.setString(Tag.TemplateIdentifier, VR.CS, "2010");
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
            keyObjectDescriptionSequence.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
            keyObjectDescriptionSequence.setString(Tag.ValueType, VR.CS, "TEXT");
            keyObjectDescriptionSequence.newSequence(Tag.ConceptNameCodeSequence, 1).add(
                codeByValue.get("113012").toCodeItem());
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

        Map<String, KeyObjectSelectionCode> codeByValue = new HashMap<String, DicomMediaUtils.KeyObjectSelectionCode>();

        XMLStreamReader xmler = null;
        InputStream stream = null;
        try {
            XMLInputFactory xmlif = XMLInputFactory.newInstance();
            stream = DicomMediaUtils.class.getResourceAsStream("/config/KeyObjectSelectionCodes.xml");
            xmler = xmlif.createXMLStreamReader(stream);

            int eventType;
            while (xmler.hasNext()) {
                eventType = xmler.next();
                switch (eventType) {
                    case XMLStreamConstants.START_ELEMENT:
                        String key = xmler.getName().getLocalPart();
                        if ("resources".equals(key)) {
                            while (xmler.hasNext()) {
                                eventType = xmler.next();
                                switch (eventType) {
                                    case XMLStreamConstants.START_ELEMENT:
                                        key = xmler.getName().getLocalPart();
                                        if ("resource".equals(key)) {
                                            String resourceName = xmler.getAttributeValue(null, "name");
                                            String contextGroupID = xmler.getAttributeValue(null, "contextId");

                                            while (xmler.hasNext()) {
                                                eventType = xmler.next();
                                                switch (eventType) {
                                                    case XMLStreamConstants.START_ELEMENT:
                                                        key = xmler.getName().getLocalPart();
                                                        if ("code".equals(key)) {

                                                            String codingSchemeDesignator =
                                                                xmler.getAttributeValue(null, "scheme");
                                                            String codeValue = xmler.getAttributeValue(null, "value");
                                                            String codeMeaning =
                                                                xmler.getAttributeValue(null, "meaning");

                                                            String conceptNameCodeModifier =
                                                                xmler.getAttributeValue(null, "conceptMod");
                                                            String contexGroupIdModifier =
                                                                xmler.getAttributeValue(null, "contexId");

                                                            codeByValue.put(codeValue,
                                                                new DicomMediaUtils.KeyObjectSelectionCode(
                                                                    resourceName, contextGroupID,
                                                                    codingSchemeDesignator, codeValue, codeMeaning,
                                                                    conceptNameCodeModifier, contexGroupIdModifier));
                                                        }
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
                        break;
                    default:
                        break;
                }
            }
        }

        catch (XMLStreamException e) {
            e.printStackTrace();
            codeByValue = null;
        } finally {
            FileUtil.safeClose(xmler);
            FileUtil.safeClose(stream);
        }
        return codeByValue;
    }

    public static class KeyObjectSelectionCode implements Comparable<KeyObjectSelectionCode> {

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

        final String resourceName;
        final String contextGroupID;

        final String codingSchemeDesignator;
        final String codeValue;
        final String codeMeaning;

        final String conceptNameCodeModifier;
        final String contexGroupIdModifier;

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
}
