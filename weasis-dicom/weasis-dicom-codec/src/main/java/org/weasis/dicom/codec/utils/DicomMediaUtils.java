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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.media.jai.LookupTableJAI;

import org.dcm4che.data.BasicDicomObject;
import org.dcm4che.data.DicomElement;
import org.dcm4che.data.DicomObject;
import org.dcm4che.data.Tag;
import org.dcm4che.data.VR;
import org.dcm4che.iod.module.pr.DisplayShutterModule;
import org.dcm4che.iod.module.sr.HierachicalSOPInstanceReference;
import org.dcm4che.iod.module.sr.KODocumentModule;
import org.dcm4che.iod.value.Modality;
import org.dcm4che.util.ByteUtils;
import org.dcm4che.util.TagUtils;
import org.dcm4che.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
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

        weasisRootUID = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.dicom.root.uid", UIDUtils.UUID_ROOT);
    }

    /**
     * @return false if either an argument is null or if at least one tag value is empty in the given dicomObject
     */
    public static boolean containsRequiredAttributes(DicomObject dicomObj, int... requiredTags) {
        if (dicomObj == null || requiredTags == null || requiredTags.length == 0) {
            return false;
        }

        int countValues = 0;
        List<String> missingTagList = null;

        for (int tag : requiredTags) {
            DicomElement attr = dicomObj.get(tag);
            if (attr != null && !attr.isEmpty()) {
                countValues++;
            } else {
                if (missingTagList == null) {
                    missingTagList = new ArrayList<String>(requiredTags.length);
                }
                missingTagList.add(TagUtils.toString(tag));
            }
        }
        if (countValues > 0 && countValues < requiredTags.length) {
            LOGGER.debug("Missing attributes \"{}\" in required list \"{}\"", missingTagList, requiredTags);
        }
        return (countValues == requiredTags.length);
    }

    /**
     * @return False if either an argument is null or if at least one tag value is empty in the first nested sequence of
     *         the given DicomObject for the given sequence Tag
     */
    public static boolean containsRequiredAttributes(DicomObject dicomObj, int sequenceTag, int... requiredTags) {
        DicomElement sequenceElt = (dicomObj != null) ? dicomObj.get(sequenceTag) : null;
        return containsRequiredAttributes(sequenceElt, 0, requiredTags);
    }

    /**
     * @return False if either an argument is null or if at least one tag value is empty in the given dicomElement
     *         Sequence at the given index
     */
    public static boolean containsRequiredAttributes(DicomElement sequenceElt, int itemIndex, int... requiredTags) {
        if (sequenceElt == null || sequenceElt.isEmpty()) {
        } else if (sequenceElt.vr() != VR.SQ) {
            LOGGER.debug("Invalid DicomElement argument \"{}\" which is not a sequence", sequenceElt.toString());
        } else if (sequenceElt.countItems() <= itemIndex) {
            LOGGER.debug("Index \"{}\" is out of bound for this sequence \"{}\"", itemIndex, sequenceElt.toString());
        } else {
            return containsRequiredAttributes(sequenceElt.getDicomObject(itemIndex), requiredTags);
        }

        return false;
    }

    static final int[] ModalityLUTRescaleAttributes = //
        new int[] { Tag.RescaleIntercept, Tag.RescaleSlope, Tag.RescaleType };

    public static boolean containsRequiredModalityLUTRescaleAttributes(DicomObject dicomObj) {
        return containsRequiredAttributes(dicomObj, ModalityLUTRescaleAttributes);
    }

    public static final int[] ModalityLUTSequenceAttributes = //
        new int[] { Tag.ModalityLUTType, Tag.LUTDescriptor, Tag.LUTData };

    public static boolean containsRequiredModalityLUTSequence(DicomObject dicomObj) {
        return containsRequiredAttributes(dicomObj, Tag.ModalityLUTSequence, ModalityLUTSequenceAttributes);
    }

    public static boolean containsRequiredModalityLUTSequenceAttributes(DicomElement sequenceElt) {
        // Only a single Item shall be included in this sequence
        return containsRequiredAttributes(sequenceElt, 0, ModalityLUTSequenceAttributes);
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

    public static boolean containsRequiredModalityLUTAttributes(DicomObject dicomObj) {
        return containsRequiredModalityLUTRescaleAttributes(dicomObj) || //
            containsRequiredModalityLUTSequence(dicomObj);
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static final int[] VOILUTWindowLevelAttributes = //
        new int[] { Tag.WindowCenter, Tag.WindowWidth };

    public static boolean containsRequiredVOILUTWindowLevelAttributes(DicomObject dicomObj) {
        return containsRequiredAttributes(dicomObj, VOILUTWindowLevelAttributes);
    }

    public static final int[] VOILUTSequenceAttributes = //
        new int[] { Tag.LUTDescriptor, Tag.LUTData };

    public static boolean containsRequiredVOILUTSequence(DicomObject dicomObj) {
        return containsRequiredAttributes(dicomObj, Tag.VOILUTSequence, VOILUTSequenceAttributes);
    }

    public static boolean containsRequiredVOILUTSequenceAttributes(DicomElement sequenceElt) {
        // One or more Items shall be included in this sequence, only first is considered
        return containsRequiredAttributes(sequenceElt, 0, VOILUTSequenceAttributes);
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
    public static boolean containsRequiredVOILUTAttributes(DicomObject dicomObj) {
        boolean windowLevelAttributes = containsRequiredVOILUTWindowLevelAttributes(dicomObj);
        boolean sequenceAttributes = containsRequiredVOILUTSequence(dicomObj);

        return windowLevelAttributes || sequenceAttributes;
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static final int[] PRLUTAttributes = //
        new int[] { Tag.LUTDescriptor, Tag.LUTData };

    private static boolean containsRequiredPRLUTSequenceAttributes(DicomElement prLUTSequence) {
        // One or more Items shall be included in this sequence, only first is considered
        return containsRequiredAttributes(prLUTSequence, 0, PRLUTAttributes);
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

    public static LookupTableJAI createLut(DicomObject dicomLutObject, boolean isValueRepresentationSigned) {
        if (dicomLutObject == null || dicomLutObject.isEmpty()) {
            return null;
        }

        LookupTableJAI lookupTable = null;

        // Three values of the LUT Descriptor describe the format of the LUT Data in the corresponding Data Element
        int[] descriptor = dicomLutObject.getInts(Tag.LUTDescriptor);

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
                byte[] bData = dicomLutObject.getBytes(Tag.LUTData);

                if (numEntries <= 256 && (bData.length == (numEntries << 1))) {
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
                    byte[] bData = dicomLutObject.getBytes(Tag.LUTData);

                    if (bData.length == (numEntries << 1)) {

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
                    short[] sData = dicomLutObject.getShorts(Tag.LUTData);

                    dataLength = sData.length;
                    lookupTable = new LookupTableJAI(sData, offset, true);
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

    public static String getStringFromDicomElement(DicomObject dicom, int tag, String defaultValue) {
        if (dicom == null) {
            return defaultValue;
        }
        DicomElement element = dicom.get(tag);
        if (element == null || element.isEmpty()) {
            return defaultValue;
        }
        String[] s = element.getStrings(dicom.getSpecificCharacterSet(), false);
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

    public static String[] getStringArrayFromDicomElement(DicomObject dicom, int tag, String[] defaultValue) {
        DicomElement element = dicom.get(tag);
        if (element == null || element.isEmpty()) {
            return defaultValue;
        }
        return element.getStrings(dicom.getSpecificCharacterSet(), false);
    }

    public static Date getDateFromDicomElement(DicomObject dicom, int tag, Date defaultValue) {
        DicomElement element = (dicom != null) ? dicom.get(tag) : null;

        if (element != null && !element.isEmpty()) {
            try {
                return element.getDate(false);
            } catch (Exception e) {
                // Value not valid according to DICOM standard
                LOGGER.error("Cannot parse date {}", element.toString()); //$NON-NLS-1$
            }
        }
        return defaultValue;
    }

    public static Float[] getFloatArrayFromDicomElement(DicomObject dicom, int tag, Float[] defaultValue) {
        DicomElement element = (dicom != null) ? dicom.get(tag) : null;

        if (element != null && !element.isEmpty()) {
            float[] fResults = element.getFloats(false);

            if (fResults != null && fResults.length > 0) {
                List<Float> fResultList = new ArrayList<Float>(fResults.length);
                for (float result : fResults) {
                    fResultList.add(result);

                }
                return fResultList.toArray(new Float[fResultList.size()]);
            }
        }
        return defaultValue;
    }

    public static Float getFloatFromDicomElement(DicomObject dicom, int tag, Float defaultValue) {
        if (dicom == null) {
            return defaultValue;
        }
        DicomElement element = dicom.get(tag);
        if (element == null || element.isEmpty()) {
            return defaultValue;
        } else {
            try {
                return element.getFloat(false);
            } catch (NumberFormatException e) {
                return defaultValue;
            } catch (UnsupportedOperationException e) {
                return defaultValue;
            }
        }
    }

    public static Integer getIntegerFromDicomElement(DicomObject dicom, int tag, Integer defaultValue) {
        if (dicom == null) {
            return defaultValue;
        }
        DicomElement element = dicom.get(tag);
        if (element == null || element.isEmpty()) {
            return defaultValue;
        } else {
            try {
                return element.getInt(false);
            } catch (NumberFormatException e) {
                return defaultValue;
            } catch (UnsupportedOperationException e) {
                return defaultValue;
            }
        }
    }

    public static Double getDoubleFromDicomElement(DicomObject dicom, int tag, Double defaultValue) {
        if (dicom == null) {
            return defaultValue;
        }
        DicomElement element = dicom.get(tag);
        if (element == null || element.isEmpty()) {
            return defaultValue;
        } else {
            try {
                return element.getDouble(false);
            } catch (NumberFormatException e) {
                return defaultValue;
            } catch (UnsupportedOperationException e) {
                return defaultValue;
            }
        }
    }

    public static void computeSUVFactor(DicomObject dicomObject, HashMap<TagW, Object> tagList, int index) {
        // From vendor neutral code at http://qibawiki.rsna.org/index.php?title=Standardized_Uptake_Value_%28SUV%29
        String modlality = (String) tagList.get(TagW.Modality);
        if ("PT".equals(modlality)) { //$NON-NLS-1$
            String correctedImage = getStringFromDicomElement(dicomObject, Tag.CorrectedImage, null);
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
                    DicomElement seq = dicomObject.get(Tag.RadiopharmaceuticalInformationSequence);
                    if (weight != 0.0f && seq != null && seq.vr() == VR.SQ) {
                        DicomObject dcm = null;
                        try {
                            dcm = seq.getDicomObject(index);
                        } catch (Exception e) {
                            LOGGER.warn("", e); //$NON-NLS-1$
                        }
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

    public static void buildLUTs(HashMap<TagW, Object> dicomTagMap) {
        if (dicomTagMap != null) {

            Integer pixelRepresentation = (Integer) dicomTagMap.get(TagW.PixelRepresentation);
            boolean isPixelRepresentationSigned = (pixelRepresentation != null && pixelRepresentation != 0);

            // NOTE : Either a Modality LUT Sequence containing a single Item or Rescale Slope and Intercept values
            // shall be present but not both (@see Dicom Standard 2011 - PS 3.3 § C.11.1 Modality LUT Module)

            DicomElement modalityLUTSequence = (DicomElement) dicomTagMap.get(TagW.ModalityLUTSequence);

            if (containsRequiredModalityLUTSequenceAttributes(modalityLUTSequence)) {
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
                    DicomObject modalityLUTobj = modalityLUTSequence.getDicomObject(0);

                    DicomMediaUtils.setTagNoNull(dicomTagMap, TagW.ModalityLUTData,
                        createLut(modalityLUTobj, isPixelRepresentationSigned));
                    DicomMediaUtils.setTagNoNull(dicomTagMap, TagW.ModalityLUTType,
                        getStringFromDicomElement(modalityLUTobj, Tag.ModalityLUTType, null));
                    DicomMediaUtils.setTagNoNull(dicomTagMap, TagW.ModalityLUTExplanation, // Optional Tag
                        getStringFromDicomElement(modalityLUTobj, Tag.LUTExplanation, null));
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
                    } else if (dicomTagMap.get(TagW.RescaleType) == null) {
                        LOGGER.debug("Modality Rescale Type is required if Rescale Intercept is present."); //$NON-NLS-1$
                    }
                } else {
                    LOGGER.debug("Modality Rescale Intercept is required if Modality LUT Sequence is not present. "); //$NON-NLS-1$
                }
            }

            // NOTE : If any VOI LUT Table is included by an Image, a Window Width and Window Center or the VOI LUT
            // Table, but not both, may be applied to the Image for display. Inclusion of both indicates that multiple
            // alternative views may be presented. (@see Dicom Standard 2011 - PS 3.3 § C.11.2 VOI LUT Module)

            DicomElement voiLUTSequence = (DicomElement) dicomTagMap.get(TagW.VOILUTSequence);

            if (containsRequiredVOILUTSequenceAttributes(voiLUTSequence)) {
                LookupTableJAI[] voiLUTsData = new LookupTableJAI[voiLUTSequence.countItems()];
                String[] voiLUTsExplanation = new String[voiLUTSequence.countItems()];

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

                for (int i = 0; i < voiLUTSequence.countItems(); i++) {
                    DicomObject voiLUTobj = voiLUTSequence.getDicomObject(i);

                    voiLUTsData[i] = createLut(voiLUTobj, isOutModalityLutSigned);
                    voiLUTsExplanation[i] = getStringFromDicomElement(voiLUTobj, Tag.LUTExplanation, null);
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

            DicomElement prLUTSequence = (DicomElement) dicomTagMap.get(TagW.PresentationLUTSequence);

            if (containsRequiredPRLUTSequenceAttributes(prLUTSequence)) {
                LookupTableJAI[] LUTsData = new LookupTableJAI[prLUTSequence.countItems()];
                String[] prLUTsExplanation = new String[prLUTSequence.countItems()];

                for (int i = 0; i < prLUTSequence.countItems(); i++) {
                    DicomObject prLUTobj = prLUTSequence.getDicomObject(i);

                    LUTsData[i] = createLut(prLUTobj, false);
                    prLUTsExplanation[i] = getStringFromDicomElement(prLUTobj, Tag.LUTExplanation, null);
                }

                DicomMediaUtils.setTag(dicomTagMap, TagW.PRLUTsData, LUTsData);
                DicomMediaUtils.setTag(dicomTagMap, TagW.PRLUTsExplanation, prLUTsExplanation); // Optional Tag
            }
        }
    }

    public static Integer getIntPixelValue(DicomObject ds, int tag, boolean signed, int stored) {
        DicomElement de = ds.get(tag);
        if (de == null) {
            return null;
        }
        int result;
        VR vr = de.vr();
        // Bug fix: http://www.dcm4che.org/jira/browse/DCM-460
        if (vr == VR.OB || vr == VR.OW) {
            result = ByteUtils.bytesLE2ushort(de.getBytes(), 0);
            if (signed) {
                if ((result & (1 << (stored - 1))) != 0) {
                    int andmask = (1 << stored) - 1;
                    int ormask = ~andmask;
                    result |= ormask;
                }
            }
        } else if ((!signed && vr != VR.US) || (signed && vr != VR.SS)) {
            vr = signed ? VR.SS : VR.US;
            result = vr.toInt(de.getBytes(), de.bigEndian());
        } else {
            result = de.getInt(false);
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
        StringBuffer buf = new StringBuffer();
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
        // Build a global identifier for the patient.
        StringBuffer buffer = new StringBuffer(patientID == null ? DicomMediaIO.NO_VALUE : patientID);
        if (issuerOfPatientID != null && !"".equals(issuerOfPatientID.trim())) { //$NON-NLS-1$
            // patientID + issuerOfPatientID => should be unique globally
            buffer.append(issuerOfPatientID);
        } else {
            // Try to make it unique.
            if (birthdate != null) {
                buffer.append(TagW.dicomformatDate.format(birthdate).toString());
            }
            if (patientName != null) {
                buffer.append(patientName.substring(0, patientName.length() < 5 ? patientName.length() : 5));
            }
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

    public static void writeMetaData(MediaSeriesGroup group, DicomObject header) {
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
                DicomMediaUtils.getStringArrayFromDicomElement(header, Tag.ModalitiesInStudy, null));
            group.setTagNoNull(TagW.NumberOfStudyRelatedInstances,
                getIntegerFromDicomElement(header, Tag.NumberOfStudyRelatedInstances, null));
            group.setTagNoNull(TagW.NumberOfStudyRelatedSeries,
                getIntegerFromDicomElement(header, Tag.NumberOfStudyRelatedSeries, null));

            // TODO sequence: define data structure
            group.setTagNoNull(TagW.ProcedureCodeSequence, header.get(Tag.ProcedureCodeSequence));
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
                DicomMediaUtils.getStringArrayFromDicomElement(header, Tag.RetrieveAETitle, null));
            group.setTagNoNull(TagW.ReferringPhysicianName,
                buildPersonName(header.getString(Tag.ReferringPhysicianName)));
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
            // TODO sequence: define data structure
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

    public static Area buildShutterArea(DicomObject dcmObject) {
        Area shape = null;
        String shutterShape = getStringFromDicomElement(dcmObject, Tag.ShutterShape, null);
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
                int[] centerOfCircularShutter = dcmObject.getInts(Tag.CenterOfCircularShutter, (int[]) null);
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
                int[] points = dcmObject.getInts(Tag.VerticesOfThePolygonalShutter, (int[]) null);
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

    public static void writeFunctionalGroupsSequence(HashMap<TagW, Object> tagList, DicomObject dcm) {
        if (dcm != null && tagList != null) {
            if (dcm != null) {

                DicomElement sequenceElt = dcm.get(Tag.PlanePositionSequence);
                if (sequenceElt != null && sequenceElt.vr() == VR.SQ && sequenceElt.countItems() > 0) {
                    setTagNoNull(tagList, TagW.ImagePositionPatient,
                        sequenceElt.getDicomObject(0).getDoubles(Tag.ImagePositionPatient, (double[]) null));
                }

                sequenceElt = dcm.get(Tag.PlaneOrientationSequence);
                if (sequenceElt != null && sequenceElt.vr() == VR.SQ && sequenceElt.countItems() > 0) {
                    double[] imgOrientation =
                        sequenceElt.getDicomObject(0).getDoubles(Tag.ImageOrientationPatient, (double[]) null);
                    setTagNoNull(tagList, TagW.ImageOrientationPatient, imgOrientation);
                    setTagNoNull(tagList, TagW.ImageOrientationPlane,
                        ImageOrientation.makeImageOrientationLabelFromImageOrientationPatient(imgOrientation));
                }

                /**
                 * Specifies the attributes of the Pixel Value Transformation Functional Group. This is equivalent with
                 * the Modality LUT transformation in non Multi-frame IODs. It constrains the Modality LUT
                 * transformation step in the grayscale rendering pipeline to be an identity transformation.
                 * 
                 * @see - Dicom Standard 2011 - PS 3.3 § C.7.6.16.2.9b Identity Pixel Value Transformation
                 */
                sequenceElt = dcm.get(Tag.PixelValueTransformationSequence);

                if (sequenceElt != null && sequenceElt.vr() == VR.SQ && sequenceElt.countItems() > 0) {
                    // Only one single item is permitted in this sequence
                    DicomObject pixelValueTransformation = sequenceElt.getDicomObject(0);

                    // Overrides Modality LUT Transformation attributes only if sequence is consistent
                    if (containsRequiredModalityLUTRescaleAttributes(pixelValueTransformation)) {
                        setTagNoNull(tagList, TagW.RescaleSlope,
                            getFloatFromDicomElement(pixelValueTransformation, Tag.RescaleSlope, null));
                        setTagNoNull(tagList, TagW.RescaleIntercept,
                            getFloatFromDicomElement(pixelValueTransformation, Tag.RescaleIntercept, null));
                        setTagNoNull(tagList, TagW.RescaleType,
                            getStringFromDicomElement(pixelValueTransformation, Tag.RescaleType, null));
                        setTagNoNull(tagList, TagW.ModalityLUTSequence,
                            pixelValueTransformation.get(Tag.ModalityLUTSequence));
                    } else {
                        LOGGER.info("Ignore {} with unconsistent attributes", //$NON-NLS-1$
                            TagUtils.toString(Tag.PixelValueTransformationSequence));
                    }
                }

                /**
                 * Specifies the attributes of the Frame VOI LUT Functional Group. It contains one or more sets of
                 * linear or sigmoid window values and/or one or more sets of lookup tables
                 * 
                 * @see - Dicom Standard 2011 - PS 3.3 § C.7.6.16.2.10b Frame VOI LUT With LUT Macro
                 */

                sequenceElt = dcm.get(Tag.FrameVOILUTSequence);
                if (sequenceElt != null && sequenceElt.vr() == VR.SQ && sequenceElt.countItems() > 0) {
                    // Only one single item is permitted in this sequence
                    DicomObject frameVOILUTSequence = sequenceElt.getDicomObject(0);

                    // Overrides VOI LUT Transformation attributes only if sequence is consistent
                    if (containsRequiredVOILUTAttributes(frameVOILUTSequence)) {
                        setTagNoNull(tagList, TagW.WindowWidth,
                            getFloatArrayFromDicomElement(frameVOILUTSequence, Tag.WindowWidth, null));
                        setTagNoNull(tagList, TagW.WindowCenter,
                            getFloatArrayFromDicomElement(frameVOILUTSequence, Tag.WindowCenter, null));
                        setTagNoNull(tagList, TagW.WindowCenterWidthExplanation,
                            getStringArrayFromDicomElement(frameVOILUTSequence, Tag.WindowCenterWidthExplanation, null));
                        setTagNoNull(tagList, TagW.VOILutFunction,
                            getStringFromDicomElement(frameVOILUTSequence, Tag.VOILUTFunction, null));
                        setTagNoNull(tagList, TagW.VOILUTSequence, frameVOILUTSequence.get(Tag.VOILUTSequence));
                    } else {
                        LOGGER.info("Ignore {} with unconsistent attributes", //$NON-NLS-1$
                            TagUtils.toString(Tag.FrameVOILUTSequence));
                    }
                }

                sequenceElt = dcm.get(Tag.PixelMeasuresSequence);
                if (sequenceElt != null && sequenceElt.vr() == VR.SQ && sequenceElt.countItems() > 0) {
                    DicomObject measure = sequenceElt.getDicomObject(0);
                    setTagNoNull(tagList, TagW.PixelSpacing, measure.getDoubles(Tag.PixelSpacing, (double[]) null));
                    setTagNoNull(tagList, TagW.SliceThickness,
                        getDoubleFromDicomElement(measure, Tag.SliceThickness, null));
                }

                // Identifies the characteristics of this frame. Only a single Item shall be permitted in this sequence.
                sequenceElt = dcm.get(Tag.MRImageFrameTypeSequence);
                if (sequenceElt == null) {
                    sequenceElt = dcm.get(Tag.CTImageFrameTypeSequence);
                }
                if (sequenceElt == null) {
                    sequenceElt = dcm.get(Tag.MRSpectroscopyFrameTypeSequence);
                }
                if (sequenceElt != null && sequenceElt.vr() == VR.SQ && sequenceElt.countItems() > 0) {
                    DicomObject frame = sequenceElt.getDicomObject(0);
                    // Type of Frame. A multi-valued attribute analogous to the Image Type (0008,0008).
                    // Enumerated Values and Defined Terms are the same as those for the four values of the Image Type
                    // (0008,0008) attribute, except that the value MIXED is not allowed. See C.8.16.1 and C.8.13.3.1.1.
                    setTagNoNull(tagList, TagW.FrameType, frame.getString(Tag.FrameType));
                }

                sequenceElt = dcm.get(Tag.FrameContentSequence);
                if (sequenceElt != null && sequenceElt.vr() == VR.SQ && sequenceElt.countItems() > 0) {
                    DicomObject frame = sequenceElt.getDicomObject(0);
                    setTagNoNull(tagList, TagW.FrameAcquisitionNumber,
                        getIntegerFromDicomElement(frame, Tag.FrameAcquisitionNumber, null));
                    setTagNoNull(tagList, TagW.StackID, frame.getString(Tag.StackID));
                    setTagNoNull(tagList, TagW.InstanceNumber,
                        getIntegerFromDicomElement(frame, Tag.InStackPositionNumber, null));
                }

                // TODO implement: Frame Pixel Shift, Pixel Intensity Relationship LUT (C.7.6.16-14),
                // Real World Value Mapping (C.7.6.16-12)
                // This transformation should be applied in in the pixel value (add a list of transformation for pixel
                // statistics)

                // Frame Display Shutter Sequence (0018,9472)
                // Display Shutter Macro Table C.7-17A in PS 3.3
                sequenceElt = dcm.get(Tag.FrameDisplayShutterSequence);
                if (sequenceElt != null && sequenceElt.vr() == VR.SQ && sequenceElt.countItems() > 0) {
                    DicomObject frame = sequenceElt.getDicomObject(0);
                    Area shape = buildShutterArea(frame);
                    if (shape != null) {
                        setTagNoNull(tagList, TagW.ShutterFinalShape, shape);
                        Integer psVal = getIntegerFromDicomElement(frame, Tag.ShutterPresentationValue, null);
                        setTagNoNull(tagList, TagW.ShutterPSValue, psVal);
                        float[] rgb =
                            DisplayShutterModule.convertToFloatLab(frame.getInts(
                                Tag.ShutterPresentationColorCIELabValue, (int[]) null));
                        Color color =
                            rgb == null ? null : PresentationStateReader.getRGBColor(psVal == null ? 0 : psVal, rgb,
                                (int[]) null);
                        setTagNoNull(tagList, TagW.ShutterRGBColor, color);
                    }
                }

                sequenceElt = dcm.get(Tag.FrameAnatomySequence);
                if (sequenceElt != null && sequenceElt.vr() == VR.SQ && sequenceElt.countItems() > 0) {
                    DicomObject frame = sequenceElt.getDicomObject(0);
                    setTagNoNull(tagList, TagW.ImageLaterality, frame.getString(Tag.FrameLaterality));
                }
            }
        }
    }

    public static boolean writePerFrameFunctionalGroupsSequence(HashMap<TagW, Object> tagList, DicomObject header,
        int index) {
        if (header != null && tagList != null) {
            DicomElement seq = header.get(Tag.PerFrameFunctionalGroupsSequence);
            if (seq != null && seq.vr() == VR.SQ) {
                DicomObject dcm = null;
                try {
                    dcm = seq.getDicomObject(index);
                } catch (Exception e) {
                    LOGGER.warn("", e); //$NON-NLS-1$
                }
                if (dcm != null) {
                    writeFunctionalGroupsSequence(tagList, dcm);
                    return true;
                }
            }
        }
        return false;
    }

    public static void readPRLUTsModule(DicomObject header, HashMap<TagW, Object> tags) {
        if (header != null && tags != null) {
            // Modality LUT Module
            DicomElement sequenceElt = header.get(Tag.ModalityLUTSequence);
            if (sequenceElt != null && sequenceElt.vr() == VR.SQ && sequenceElt.countItems() > 0) {
                // Only one single item is permitted in this sequence
                DicomObject modalitySeq = sequenceElt.getDicomObject(0);

                // Overrides Modality LUT Transformation attributes only if sequence is consistent
                if (containsRequiredModalityLUTSequenceAttributes(sequenceElt)) {
                    setTagNoNull(tags, TagW.ModalityLUTSequence, modalitySeq.get(Tag.ModalityLUTSequence));
                    setTagNoNull(tags, TagW.RescaleSlope,
                        DicomMediaUtils.getFloatFromDicomElement(modalitySeq, Tag.RescaleSlope, null));
                    setTagNoNull(tags, TagW.RescaleIntercept,
                        DicomMediaUtils.getFloatFromDicomElement(modalitySeq, Tag.RescaleIntercept, null));
                    setTagNoNull(tags, TagW.RescaleType,
                        DicomMediaUtils.getStringFromDicomElement(modalitySeq, Tag.RescaleType, null));
                } else {
                    LOGGER.info("Ignore {} with unconsistent attributes", //$NON-NLS-1$
                        TagUtils.toString(Tag.ModalityLUTSequence));
                }
            }

            // VOI LUT Module
            sequenceElt = header.get(Tag.SoftcopyVOILUTSequence);
            if (sequenceElt != null && sequenceElt.vr() == VR.SQ && sequenceElt.countItems() > 0) {
                // Only one single item is permitted in this sequence
                DicomObject voiSeq = sequenceElt.getDicomObject(0);

                // Overrides VOI LUT Transformation attributes only if sequence is consistent
                if (containsRequiredVOILUTAttributes(voiSeq)) {
                    setTagNoNull(tags, TagW.WindowWidth, getFloatArrayFromDicomElement(voiSeq, Tag.WindowWidth, null));
                    setTagNoNull(tags, TagW.WindowCenter, getFloatArrayFromDicomElement(voiSeq, Tag.WindowCenter, null));
                    setTagNoNull(tags, TagW.WindowCenterWidthExplanation,
                        getStringArrayFromDicomElement(voiSeq, Tag.WindowCenterWidthExplanation, null));
                    setTagNoNull(tags, TagW.VOILutFunction, getStringFromDicomElement(voiSeq, Tag.VOILUTFunction, null));
                    setTagNoNull(tags, TagW.VOILUTSequence, voiSeq.get(Tag.VOILUTSequence));
                } else {
                    LOGGER.info("Ignore {} with unconsistent attributes", //$NON-NLS-1$
                        TagUtils.toString(Tag.SoftcopyVOILUTSequence));
                }
            }

            // TODO implement 1.2.840.10008.5.1.4.1.1.11.2 -5 color and xray
            if ("1.2.840.10008.5.1.4.1.1.11.1".equals(header.getString(Tag.SOPClassUID))) {
                // Presentation LUT Module
                sequenceElt = header.get(Tag.PresentationLUTSequence);
                if (sequenceElt != null && sequenceElt.vr() == VR.SQ && sequenceElt.countItems() > 0) {
                    // Only one single item is permitted in this sequence
                    setTagNoNull(tags, TagW.PresentationLUTSequence, sequenceElt);
                } else {
                    setTagNoNull(tags, TagW.PresentationLUTShape, header.getString(Tag.PresentationLUTShape));
                }
            }
        }
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Creates a Dicom Key Object from another one copying its CurrentRequestedProcedureEvidences and keeping its
     * patient informations. It's the user responsibility to manage the studyUID and serieUID values. Given UIDs
     * parameters are supposed to be valid and won't be verified. If their value is null a new one will be generated
     * instead.
     * 
     * @param dicomObject
     * @param description
     * @param studyInstanceUID
     *            can be null
     * @param seriesInstanceUID
     *            can be null
     * @return
     */
    public static DicomObject createDicomKeyObject(DicomObject dicomObject, String description,
        String studyInstanceUID, String seriesInstanceUID) {

        if (description == null || "".equals(description)) {
            description = "new KO selection";
        }

        String patientID = dicomObject.getString(Tag.PatientID);
        String patientName = dicomObject.getString(Tag.PatientName);
        Date patientBirthdate = dicomObject.getDate(Tag.PatientBirthDate);

        DicomObject newDicomKeyObject =
            createDicomKeyObject(patientID, patientName, patientBirthdate, description, studyInstanceUID,
                seriesInstanceUID);

        HierachicalSOPInstanceReference[] referencedStudySequence =
            new KODocumentModule(dicomObject).getCurrentRequestedProcedureEvidences();

        new KODocumentModule(newDicomKeyObject).setCurrentRequestedProcedureEvidences(referencedStudySequence);

        return newDicomKeyObject;
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * 
     * Creates a Dicom Key Object from patient information. Given UIDs parameters are supposed to be valid and won't be
     * verified. If their value is null a new one will be generated instead.
     * 
     * @param patientID
     * @param patientName
     * @param patientBirthdate
     * @param description
     * @param studyInstanceUID
     *            can be null
     * @param seriesInstanceUID
     *            can be null
     * @return
     */
    public static DicomObject createDicomKeyObject(String patientID, String patientName, Date patientBirthdate,
        String description, String studyInstanceUID, String seriesInstanceUID) {

        DicomObject newDicomKeyObject = new BasicDicomObject();

        if (description == null || "".equals(description)) {
            description = "new KO selection";
        }

        newDicomKeyObject.putString(Tag.SeriesDescription, VR.LO, description);

        newDicomKeyObject.putString(Tag.Modality, VR.CS, Modality.KO);

        Date dateTimeNow = Calendar.getInstance().getTime();
        newDicomKeyObject.putDate(Tag.ContentDate, VR.DA, dateTimeNow);
        newDicomKeyObject.putDate(Tag.ContentTime, VR.TM, dateTimeNow);

        newDicomKeyObject.putString(Tag.PatientID, VR.LO, patientID);
        newDicomKeyObject.putString(Tag.PatientName, VR.PN, patientName);
        newDicomKeyObject.putDate(Tag.PatientBirthDate, VR.DA, patientBirthdate);

        /**
         * @see DICOM standard PS 3.3
         * 
         *      C.17.6 Key Object Selection Modules && C.17.6.2.1 Identical Documents
         * 
         *      The Unique identifier for the Study (studyInstanceUID) is supposed to be the same as to one of the
         *      referenced image but it's not necessary. Standard says that if the Current Requested Procedure Evidence
         *      Sequence (0040,A375) references SOP Instances both in the current study and in one or more other
         *      studies, this document shall be duplicated into each of those other studies, and the duplicates shall be
         *      referenced in the Identical Documents Sequence (0040,A525).
         */

        if (studyInstanceUID == null || !studyInstanceUID.equals("")) {
            studyInstanceUID = UIDUtils.createUID(weasisRootUID);
        }
        newDicomKeyObject.putString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);

        if (seriesInstanceUID == null || !seriesInstanceUID.equals("")) {
            seriesInstanceUID = UIDUtils.createUID(weasisRootUID);
        }
        newDicomKeyObject.putString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUID);

        String newSopInstanceUid = UIDUtils.createUID(weasisRootUID);
        newDicomKeyObject.putString(Tag.SOPInstanceUID, VR.UI, newSopInstanceUid);

        newDicomKeyObject.putString(Tag.TransferSyntaxUID, VR.UI, "1.2.840.10008.1.2");
        newDicomKeyObject.putString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.88.59");

        newDicomKeyObject.putString(Tag.SeriesNumber, VR.IS, "1");
        newDicomKeyObject.putString(Tag.InstanceNumber, VR.IS, "1");

        newDicomKeyObject.putString(Tag.ValueType, VR.CS, "CONTAINER");

        newDicomKeyObject.putSequence(Tag.CurrentRequestedProcedureEvidenceSequence);

        return newDicomKeyObject;
    }
}
