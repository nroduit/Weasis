/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.utils;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
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
import org.dcm4che3.img.lut.ModalityLutModule;
import org.dcm4che3.img.lut.VoiLutModule;
import org.dcm4che3.img.util.DicomObjectUtil;
import org.dcm4che3.util.ByteUtils;
import org.dcm4che3.util.TagUtils;
import org.dcm4che3.util.UIDUtils;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.core.api.media.data.Taggable;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.MathUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.TagSeq;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.codec.geometry.PatientOrientation;
import org.weasis.dicom.codec.geometry.VectorUtils;

/**
 * @author Nicolas Roduit
 * @author Benoit Jacquemoud
 */
public class DicomMediaUtils {

  private DicomMediaUtils() {}

  private static final Logger LOGGER = LoggerFactory.getLogger(DicomMediaUtils.class);

  private static final int[] modalityLutAttributes =
      new int[] {Tag.RescaleIntercept, Tag.RescaleSlope};
  private static final int[] VOILUTWindowLevelAttributes =
      new int[] {Tag.WindowCenter, Tag.WindowWidth};
  private static final int[] LUTAttributes = new int[] {Tag.LUTDescriptor, Tag.LUTData};

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
    int[] list = {
      Tag.PatientName,
      Tag.PatientID,
      Tag.PatientSex,
      Tag.PatientBirthDate,
      Tag.PatientBirthTime,
      Tag.PatientAge,
      Tag.PatientComments,
      Tag.PatientWeight,
      Tag.AccessionNumber,
      Tag.StudyID,
      Tag.InstitutionalDepartmentName,
      Tag.InstitutionName,
      Tag.ReferringPhysicianName,
      Tag.StudyDescription,
      Tag.SeriesDescription,
      Tag.StationName,
      Tag.ImageComments
    };
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
   * @return false if either an argument is null or if at least one tag value is empty in the given
   *     dicomObject
   */
  public static boolean containsRequiredAttributes(Attributes dcmItems, int... requiredTags) {
    if (dcmItems == null || requiredTags == null || requiredTags.length == 0) {
      return false;
    }

    int countValues = 0;
    for (int tag : requiredTags) {
      if (dcmItems.containsValue(tag)) {
        countValues++;
      }
    }
    return countValues == requiredTags.length;
  }

  /**
   * Either a Modality LUT Sequence containing a single Item or Rescale Slope and Intercept values
   * shall be present but not both.<br>
   * This requirement for only a single transformation makes it possible to unambiguously define the
   * input of succeeding stages of the grayscale pipeline such as the VOI LUT
   *
   * @return True if the specified object contains some type of Modality LUT attributes at the
   *     current level. <br>
   * @see - Dicom Standard 2011 - PS 3.3 § C.11.1 Modality LUT Module
   */
  public static boolean containsRequiredModalityLUTAttributes(Attributes dcmItems) {
    return containsRequiredAttributes(dcmItems, modalityLutAttributes);
  }

  public static boolean containsRequiredModalityLUTDataAttributes(Attributes dcmItems) {
    return containsRequiredAttributes(dcmItems, Tag.ModalityLUTType)
        && containsLUTAttributes(dcmItems);
  }

  /**
   * If any VOI LUT Table is included by an Image, a Window Width and Window Center or the VOI LUT
   * Table, but not both, may be applied to the Image for display. Inclusion of both indicates that
   * multiple alternative views may be presented. <br>
   * If multiple items are present in VOI LUT Sequence, only one may be applied to the Image for
   * display. Multiple items indicate that multiple alternative views may be presented.
   *
   * @return True if the specified object contains some type of VOI LUT attributes at the current
   *     level (ie:Window Level or VOI LUT Sequence).
   * @see - Dicom Standard 2011 - PS 3.3 § C.11.2 VOI LUT Module
   */
  public static boolean containsRequiredVOILUTWindowLevelAttributes(Attributes dcmItems) {
    return containsRequiredAttributes(dcmItems, VOILUTWindowLevelAttributes);
  }

  public static boolean containsLUTAttributes(Attributes dcmItems) {
    return containsRequiredAttributes(dcmItems, LUTAttributes);
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
      sb.append("\\").append(s[i]);
    }
    return sb.toString();
  }

  public static String[] getStringArrayFromDicomElement(Attributes dicom, int tag) {
    return getStringArrayFromDicomElement(dicom, tag, (String) null);
  }

  public static String[] getStringArrayFromDicomElement(
      Attributes dicom, int tag, String privateCreatorID) {
    if (dicom == null || !dicom.containsValue(tag)) {
      return null;
    }
    return dicom.getStrings(privateCreatorID, tag);
  }

  public static String[] getStringArrayFromDicomElement(
      Attributes dicom, int tag, String[] defaultValue) {
    return getStringArrayFromDicomElement(dicom, tag, null, defaultValue);
  }

  public static String[] getStringArrayFromDicomElement(
      Attributes dicom, int tag, String privateCreatorID, String[] defaultValue) {
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

  public static Date[] getDatesFromDicomElement(
      Attributes dicom, int tag, String privateCreatorID, Date[] defaultValue) {
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

  public static String getPatientAgeInPeriod(
      Attributes dicom,
      int tag,
      String privateCreatorID,
      String defaultValue,
      boolean computeOnlyIfNull) {
    if (dicom == null) {
      return defaultValue;
    }

    if (computeOnlyIfNull) {
      String s = dicom.getString(privateCreatorID, tag, defaultValue);
      if (StringUtil.hasText(s) && StringUtil.hasText(TagD.getDicomPeriod(s))) {
        return s;
      }
    }

    Date date =
        getDate(
            dicom,
            Tag.ContentDate,
            Tag.AcquisitionDate,
            Tag.DateOfSecondaryCapture,
            Tag.SeriesDate,
            Tag.StudyDate);

    if (date != null) {
      Date birthdate = dicom.getDate(Tag.PatientBirthDate);
      if (birthdate != null) {
        return getPeriod(TagUtil.toLocalDate(birthdate), TagUtil.toLocalDate(date));
      }
    }
    return null;
  }

  private static Date getDate(Attributes dicom, int... tagID) {
    Date date;
    for (int i : tagID) {
      date = dicom.getDate(i);
      if (date != null) {
        return date;
      }
    }
    return null;
  }

  public static String getPeriod(LocalDate first, LocalDate last) {
    Objects.requireNonNull(first);
    Objects.requireNonNull(last);

    long years = ChronoUnit.YEARS.between(first, last);
    if (years < 2) {
      long months = ChronoUnit.MONTHS.between(first, last);
      if (months < 2) {
        return String.format("%03dD", ChronoUnit.DAYS.between(first, last)); // NON-NLS
      }
      return String.format("%03dM", months); // NON-NLS
    }
    return String.format("%03dY", years); // NON-NLS
  }

  public static Float getFloatFromDicomElement(Attributes dicom, int tag, Float defaultValue) {
    return getFloatFromDicomElement(dicom, tag, null, defaultValue);
  }

  public static Float getFloatFromDicomElement(
      Attributes dicom, int tag, String privateCreatorID, Float defaultValue) {
    if (dicom == null || !dicom.containsValue(tag)) {
      return defaultValue;
    }
    try {
      return dicom.getFloat(privateCreatorID, tag, defaultValue == null ? 0.0F : defaultValue);
    } catch (NumberFormatException e) {
      LOGGER.error("Cannot parse Float of {}: {} ", TagUtils.toString(tag), e.getMessage());
    }
    return defaultValue;
  }

  public static Integer getIntegerFromDicomElement(
      Attributes dicom, int tag, Integer defaultValue) {
    return getIntegerFromDicomElement(dicom, tag, null, defaultValue);
  }

  public static Integer getIntegerFromDicomElement(
      Attributes dicom, int tag, String privateCreatorID, Integer defaultValue) {
    if (dicom == null || !dicom.containsValue(tag)) {
      return defaultValue;
    }
    try {
      return dicom.getInt(privateCreatorID, tag, defaultValue == null ? 0 : defaultValue);
    } catch (NumberFormatException e) {
      LOGGER.error("Cannot parse Integer of {}: {} ", TagUtils.toString(tag), e.getMessage());
    }
    return defaultValue;
  }

  public static Long getLongFromDicomElement(Attributes dicom, int tag, Long defaultValue) {
    return getLongFromDicomElement(dicom, tag, null, defaultValue);
  }

  public static Long getLongFromDicomElement(
      Attributes dicom, int tag, String privateCreatorID, Long defaultValue) {
    if (dicom == null || !dicom.containsValue(tag)) {
      return defaultValue;
    }
    try {
      return dicom.getLong(privateCreatorID, tag, defaultValue == null ? 0L : defaultValue);
    } catch (NumberFormatException e) {
      LOGGER.error("Cannot parse Long of {}: {} ", TagUtils.toString(tag), e.getMessage());
    }
    return defaultValue;
  }

  public static Double getDoubleFromDicomElement(Attributes dicom, int tag, Double defaultValue) {
    return getDoubleFromDicomElement(dicom, tag, null, defaultValue);
  }

  public static Double getDoubleFromDicomElement(
      Attributes dicom, int tag, String privateCreatorID, Double defaultValue) {
    if (dicom == null || !dicom.containsValue(tag)) {
      return defaultValue;
    }
    try {
      return dicom.getDouble(privateCreatorID, tag, defaultValue == null ? 0.0 : defaultValue);
    } catch (NumberFormatException e) {
      LOGGER.error("Cannot parse Double of {}: {} ", TagUtils.toString(tag), e.getMessage());
    }
    return defaultValue;
  }

  public static int[] getIntArrayFromDicomElement(Attributes dicom, int tag, int[] defaultValue) {
    return getIntArrayFromDicomElement(dicom, tag, null, defaultValue);
  }

  public static int[] getIntArrayFromDicomElement(
      Attributes dicom, int tag, String privateCreatorID, int[] defaultValue) {
    if (dicom == null || !dicom.containsValue(tag)) {
      return defaultValue;
    }
    try {
      return dicom.getInts(privateCreatorID, tag);
    } catch (NumberFormatException e) {
      LOGGER.error("Cannot parse int[] of {}: {} ", TagUtils.toString(tag), e.getMessage());
    }
    return defaultValue;
  }

  public static float[] getFloatArrayFromDicomElement(
      Attributes dicom, int tag, float[] defaultValue) {
    return getFloatArrayFromDicomElement(dicom, tag, null, defaultValue);
  }

  public static float[] getFloatArrayFromDicomElement(
      Attributes dicom, int tag, String privateCreatorID, float[] defaultValue) {
    if (dicom == null || !dicom.containsValue(tag)) {
      return defaultValue;
    }
    try {
      return dicom.getFloats(privateCreatorID, tag);
    } catch (NumberFormatException e) {
      LOGGER.error("Cannot parse float[] of {}: {} ", TagUtils.toString(tag), e.getMessage());
    }
    return defaultValue;
  }

  public static double[] getDoubleArrayFromDicomElement(
      Attributes dicom, int tag, double[] defaultValue) {
    return getDoubleArrayFromDicomElement(dicom, tag, null, defaultValue);
  }

  public static double[] getDoubleArrayFromDicomElement(
      Attributes dicom, int tag, String privateCreatorID, double[] defaultValue) {
    if (dicom == null || !dicom.containsValue(tag)) {
      return defaultValue;
    }
    try {
      return dicom.getDoubles(privateCreatorID, tag);
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
        LOGGER.error("Cannot read {} ", TagUtils.toString(tag), e);
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
    return result < minInValue ? minInValue : Math.min(result, maxInValue);
  }

  public static void setTag(Map<TagW, Object> tags, TagW tag, Object value) {
    if (tag != null) {
      if (value instanceof Sequence seq) {
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
      Object pid = group.getTagValue(TagW.PatientPseudoUID);
      if (pid != null) {
        String pid2 = new PatientComparator(header).buildPatientPseudoUID();
        if (!Objects.equals(pid, pid2)) {
          LOGGER.warn(
              "Inconsistent Patient ID + Issuer of Patient ID between DICOM objets: {} and {}",
              pid,
              pid2);
        }
      }
      DicomMediaIO.tagManager.readTags(Level.PATIENT, header, group);
    }
    // Study Group
    else if (TagD.getUID(Level.STUDY).equals(group.getTagID())) {
      TagW tagID = TagD.getUID(Level.STUDY);
      Object studyUID = group.getTagValue(tagID);
      if (studyUID != null && !Objects.equals(studyUID, header.getString(tagID.getId()))) {
        LOGGER.warn(
            "Inconsistent Study Instance UID between DICOM objets: {} and {}",
            group.getTagValue(TagD.getUID(Level.STUDY)),
            header.getString(Tag.StudyInstanceUID));
      }
      DicomMediaIO.tagManager.readTags(Level.STUDY, header, group);
    }
    // Series Group
    else if (TagD.getUID(Level.SERIES).equals(group.getTagID())) {
      TagW tagID = TagD.get(Tag.SeriesInstanceUID); // cannot compare sub-series, only in group
      Object seriesUID = group.getTagValue(tagID);
      if (seriesUID != null && !Objects.equals(seriesUID, header.getString(tagID.getId()))) {
        LOGGER.warn(
            "Inconsistent Series Instance UID between DICOM objets: {} and {}",
            seriesUID,
            header.getString(tagID.getId()));
      }
      DicomMediaIO.tagManager.readTags(Level.SERIES, header, group);
      // Build patient age if not present
      group.setTagNoNull(
          TagD.get(Tag.PatientAge), getPatientAgeInPeriod(header, Tag.PatientAge, true));
    }
  }

  public static void computeSlicePositionVector(Taggable taggable) {
    if (taggable != null) {
      Vector3d pPos = PatientOrientation.getPatientPosition(taggable);
      if (pPos != null) {
        Vector3d vr = ImageOrientation.getRowImagePosition(taggable);
        Vector3d vc = ImageOrientation.getColumnImagePosition(taggable);
        if (vr != null && vc != null) {
          Vector3d normal = VectorUtils.computeNormalOfSurface(vr, vc);
          normal.mul(pPos);
          taggable.setTag(TagW.SlicePosition, new double[] {normal.x, normal.y, normal.z});
        }
      }
    }
  }

  /**
   * Build the shape from DICOM Shutter
   *
   * @see <a
   *     href="http://dicom.nema.org/MEDICAL/DICOM/current/output/chtml/part03/sect_C.7.6.11.html">C.7.6.11
   *     Display Shutter Module</a>
   * @see <a
   *     href="http://dicom.nema.org/MEDICAL/DICOM/current/output/chtml/part03/sect_C.7.6.15.html">C.7.6.15
   *     Bitmap Display Shutter Module</a>
   */
  public static void setShutter(Taggable taggable, Attributes dcmObject) {
    taggable.setTagNoNull(TagW.ShutterFinalShape, DicomObjectUtil.getShutterShape(dcmObject));

    // Set color also for BITMAP shape (bitmap is extracted in overlay class)
    taggable.setTagNoNull(TagW.ShutterRGBColor, DicomObjectUtil.getShutterColor(dcmObject));
  }

  public static void writeFunctionalGroupsSequence(Taggable taggable, Attributes dcm) {
    if (dcm != null && taggable != null) {
      /**
       * @see - Dicom Standard 2011 - PS 3.3 §C.7.6.16.2.1 Pixel Measures Macro
       */
      TagSeq.MacroSeqData data =
          new TagSeq.MacroSeqData(dcm, TagD.getTagFromIDs(Tag.PixelSpacing, Tag.SliceThickness));
      TagD.get(Tag.PixelMeasuresSequence).readValue(data, taggable);

      /**
       * @see - Dicom Standard 2011 - PS 3.3 §C.7.6.16.2.2 Frame Content Macro
       */
      data =
          new TagSeq.MacroSeqData(
              dcm,
              TagD.getTagFromIDs(
                  Tag.FrameAcquisitionNumber,
                  Tag.StackID,
                  Tag.InStackPositionNumber,
                  Tag.TemporalPositionIndex));
      TagD.get(Tag.FrameContentSequence).readValue(data, taggable);
      // If not null override instance number for a better image sorting.
      taggable.setTagNoNull(
          TagD.get(Tag.InstanceNumber), taggable.getTagValue(TagD.get(Tag.InStackPositionNumber)));

      /**
       * @see - Dicom Standard 2011 - PS 3.3 § C.7.6.16.2.3 Plane Position (Patient) Macro
       */
      data = new TagSeq.MacroSeqData(dcm, TagD.getTagFromIDs(Tag.ImagePositionPatient));
      TagD.get(Tag.PlanePositionSequence).readValue(data, taggable);

      /**
       * @see - Dicom Standard 2011 - PS 3.3 § C.7.6.16.2.4 Plane Orientation (Patient) Macro
       */
      data = new TagSeq.MacroSeqData(dcm, TagD.getTagFromIDs(Tag.ImageOrientationPatient));
      TagD.get(Tag.PlaneOrientationSequence).readValue(data, taggable);
      // If not null add ImageOrientationPlane for getting a orientation label.
      taggable.setTagNoNull(TagW.ImageOrientationPlane, ImageOrientation.getPlan(taggable));

      /**
       * @see - Dicom Standard 2011 - PS 3.3 § C.7.6.16.2.8 Frame Anatomy Macro
       */
      data = new TagSeq.MacroSeqData(dcm, TagD.getTagFromIDs(Tag.FrameLaterality));
      TagD.get(Tag.FrameAnatomySequence).readValue(data, taggable);

      /**
       * Specifies the attributes of the Pixel Value Transformation Functional Group. This is
       * equivalent with the Modality LUT transformation in non Multi-frame IODs. It constrains the
       * Modality LUT transformation step in the grayscale rendering pipeline to be an identity
       * transformation.
       *
       * @see - Dicom Standard 2011 - PS 3.3 § C.7.6.16.2.9-b Pixel Value Transformation
       */
      Attributes mLutItems = dcm.getNestedDataset(Tag.PixelValueTransformationSequence);
      if (mLutItems != null) {
        ModalityLutModule mlut = new ModalityLutModule(mLutItems);
        taggable.setTag(TagW.ModalityLUTData, mlut);
      }

      /**
       * Specifies the attributes of the Frame VOI LUT Functional Group. It contains one or more
       * sets of linear or sigmoid window values and/or one or more sets of lookup tables
       *
       * @see - Dicom Standard 2011 - PS 3.3 § C.7.6.16.2.10b Frame VOI LUT With LUT Macro
       */
      Attributes vLutItems = dcm.getNestedDataset(Tag.FrameVOILUTSequence);
      if (vLutItems != null) {
        VoiLutModule vlut = new VoiLutModule(vLutItems);
        taggable.setTag(TagW.VOILUTsData, vlut);
      }

      // TODO implement: Frame Pixel Shift, Pixel Intensity Relationship LUT (C.7.6.16-14),
      // Real World Value Mapping (C.7.6.16-12)
      // This transformation should be applied in in the pixel value (add a list of transformation
      // for pixel
      // statistics)

      /**
       * Display Shutter Macro Table C.7-17A in PS 3.3
       *
       * @see - Dicom Standard 2011 - PS 3.3 § C.7.6.16.2.16 Frame Display Shutter Macro
       */
      Attributes macroFrameDisplayShutter = dcm.getNestedDataset(Tag.FrameDisplayShutterSequence);
      if (macroFrameDisplayShutter != null) {
        setShutter(taggable, macroFrameDisplayShutter);
      }

      /**
       * @see - Dicom Standard 2011 - PS 3.3 §C.8 Frame Type Macro
       */
      // Type of Frame. A multivalued attribute analogous to the Image Type (0008,0008).
      // Enumerated Values and Defined Terms are the same as those for the four values of the Image
      // Type
      // (0008,0008) attribute, except that the value MIXED is not allowed. See C.8.16.1 and
      // C.8.13.3.1.1.
      data = new TagSeq.MacroSeqData(dcm, TagD.getTagFromIDs(Tag.FrameType));
      // C.8.13.5.1 MR Image Frame Type Macro
      TagD.get(Tag.MRImageFrameTypeSequence).readValue(data, taggable);
      // // C.8.15.3.1 CT Image Frame Type Macro
      TagD.get(Tag.CTImageFrameTypeSequence).readValue(data, taggable);
      // C.8.14.3.1 MR Spectroscopy Frame Type Macro
      TagD.get(Tag.MRSpectroscopyFrameTypeSequence).readValue(data, taggable);
      // C.8.22.5.1 PET Frame Type Macro
      TagD.get(Tag.PETFrameTypeSequence).readValue(data, taggable);
    }
  }

  public static boolean writePerFrameFunctionalGroupsSequence(
      Taggable taggable, Attributes header, int index) {
    if (header != null && taggable != null) {
      /*
       * C.7.6.16 The number of Items shall be the same as the number of frames in the Multi-frame image.
       */
      Attributes a = header.getNestedDataset(Tag.PerFrameFunctionalGroupsSequence, index);
      if (a != null) {
        DicomMediaUtils.writeFunctionalGroupsSequence(taggable, a);
        return true;
      }
    }
    return false;
  }

  public static void computeSUVFactor(Attributes dicomObject, Taggable taggable, int index) {
    // From vendor neutral code at
    // http://qibawiki.rsna.org/index.php?title=Standardized_Uptake_Value_%28SUV%29
    String modality = TagD.getTagValue(taggable, Tag.Modality, String.class);
    if ("PT".equals(modality)) {
      String correctedImage = getStringFromDicomElement(dicomObject, Tag.CorrectedImage);
      if (correctedImage != null
          && correctedImage.contains("ATTN")
          && correctedImage.contains("DECY")) { // NON-NLS
        double suvFactor = 0.0;
        String units = dicomObject.getString(Tag.Units);
        // DICOM $C.8.9.1.1.3 Units
        // The units of the pixel values obtained after conversion from the stored pixel values (SV)
        // (Pixel
        // Data (7FE0,0010)) to pixel value units (U), as defined by Rescale Intercept (0028,1052)
        // and
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
        if ("BQML".equals(units)) {
          Float weight = getFloatFromDicomElement(dicomObject, Tag.PatientWeight, 0.0f); // in Kg
          if (MathUtil.isDifferentFromZero(weight)) {
            Attributes dcm =
                dicomObject.getNestedDataset(Tag.RadiopharmaceuticalInformationSequence, index);
            if (dcm != null) {
              Float totalDose = getFloatFromDicomElement(dcm, Tag.RadionuclideTotalDose, null);
              Float halfLife = getFloatFromDicomElement(dcm, Tag.RadionuclideHalfLife, null);
              Date injectTime =
                  getDateFromDicomElement(dcm, Tag.RadiopharmaceuticalStartTime, null);
              Date injectDateTime =
                  getDateFromDicomElement(dcm, Tag.RadiopharmaceuticalStartDateTime, null);
              Date acquisitionDateTime =
                  TagUtil.dateTime(
                      getDateFromDicomElement(dicomObject, Tag.AcquisitionDate, null),
                      getDateFromDicomElement(dicomObject, Tag.AcquisitionTime, null));
              Date scanDate = getDateFromDicomElement(dicomObject, Tag.SeriesDate, null);
              if ("START".equals(dicomObject.getString(Tag.DecayCorrection))
                  && totalDose != null
                  && halfLife != null
                  && acquisitionDateTime != null
                  && (injectDateTime != null || (scanDate != null && injectTime != null))) {
                double time = 0.0;
                long scanDateTime =
                    TagUtil.dateTime(
                            scanDate, getDateFromDicomElement(dicomObject, Tag.SeriesTime, null))
                        .getTime();
                if (injectDateTime == null) {
                  if (scanDateTime > acquisitionDateTime.getTime()) {
                    // per GE docs, may have been updated during post-processing into new series
                    String privateCreator = dicomObject.getString(0x00090010);
                    Date privateScanDateTime = getDateFromDicomElement(dcm, 0x0009100d, null);
                    if ("GEMS_PETD_01".equals(privateCreator) // NON-NLS
                        && privateScanDateTime != null) {
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
                  // Weight converts in kg to g
                  suvFactor = weight * 1000.0 / correctedDose;
                }
              }
            }
          }
        } else if ("CNTS".equals(units)) {
          String privateTagCreator = dicomObject.getString(0x70530010);
          double privateSUVFactor = dicomObject.getDouble(0x70531000, 0.0);
          if ("Philips PET Private Group".equals(privateTagCreator) // NON-NLS
              && MathUtil.isDifferentFromZero(privateSUVFactor)) {
            suvFactor = privateSUVFactor; // units => "g/ml"
          }
        } else if ("GML".equals(units)) {
          suvFactor = 1.0;
        }
        if (MathUtil.isDifferentFromZero(suvFactor)) {
          taggable.setTag(TagW.SuvFactor, suvFactor);
        }
      }
    }
  }

  public static Attributes createDicomPR(
      Attributes dicomSourceAttribute, String seriesInstanceUID, String sopInstanceUID) {

    final int[] patientStudyAttributes = {
      Tag.SpecificCharacterSet,
      Tag.StudyDate,
      Tag.StudyTime,
      Tag.StudyDescription,
      Tag.AccessionNumber,
      Tag.IssuerOfAccessionNumberSequence,
      Tag.ReferringPhysicianName,
      Tag.PatientName,
      Tag.PatientID,
      Tag.IssuerOfPatientID,
      Tag.PatientBirthDate,
      Tag.PatientSex,
      Tag.AdditionalPatientHistory,
      Tag.StudyInstanceUID,
      Tag.StudyID
    };
    Arrays.sort(patientStudyAttributes);
    Attributes pr = new Attributes(dicomSourceAttribute, patientStudyAttributes);

    // TODO implement other ColorSoftcopyPresentationStateStorageSOPClass...
    pr.setString(Tag.SOPClassUID, VR.UI, UID.GrayscaleSoftcopyPresentationStateStorage);
    pr.setString(
        Tag.SOPInstanceUID,
        VR.UI,
        StringUtil.hasText(sopInstanceUID) ? sopInstanceUID : UIDUtils.createUID());
    Date now = new Date();
    pr.setDate(Tag.PresentationCreationDateAndTime, now);
    pr.setDate(Tag.ContentDateAndTime, now);
    pr.setString(Tag.Modality, VR.CS, "PR");
    pr.setString(
        Tag.SeriesInstanceUID,
        VR.UI,
        StringUtil.hasText(seriesInstanceUID) ? seriesInstanceUID : UIDUtils.createUID());
    return pr;
  }

  // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Creates a dicomKeyObjectSelection Attributes from another SOP Instance keeping its patient and
   * study information. For instance, it can be can an IMAGE or a previous build dicomKOS Document.
   *
   * @param dicomSourceAttribute : Must be valid
   * @param keyObjectDescription : Optional, can be null
   * @param seriesInstanceUID is supposed to be valid and won't be verified, it's the user
   *     responsibility to manage this value. If null a randomly new one will be generated instead
   * @return new dicomKeyObjectSelection Document Attributes
   */
  public static Attributes createDicomKeyObject(
      Attributes dicomSourceAttribute, String keyObjectDescription, String seriesInstanceUID) {

    /**
     * @see DICOM standard PS 3.3 - § C.17.6.1 Key Object Document Series Module
     * @note Series of Key Object Selection Documents are separate from Series of Images or other
     *     Composite SOP Instances. Key Object Documents do not reside in a Series of Images or
     *     other Composite SOP Instances.
     */

    /**
     * @note Loads properties that reference all "Key Object Codes" defined in the following
     *     resource : KeyObjectSelectionCodes.xml
     * @see These Codes are up-to-date regarding Dicom Conformance : <br>
     *     PS 3.16 - § Context ID 7010 Key Object Selection Document Title <br>
     *     PS 3.16 - § Context ID 7011 Rejected for Quality Reasons - <br>
     *     PS 3.16 - § Context ID 7012 Best In Set<br>
     *     Correction Proposal - § CP 1152 Parts 16 (Additional document titles for Key Object
     *     Selection Document)
     */
    Map<String, KeyObjectSelectionCode> codeByValue = getKeyObjectSelectionMappingResources();
    Map<String, Set<KeyObjectSelectionCode>> resourcesByContextID = new HashMap<>();

    for (KeyObjectSelectionCode code : codeByValue.values()) {
      Set<KeyObjectSelectionCode> resourceSet =
          resourcesByContextID.computeIfAbsent(code.contextGroupID, k -> new TreeSet<>());
      resourceSet.add(code);
    }

    /**
     * Document Title of created KOS - must be one of the values specified by "Context ID 7010" in
     * KeyObjectSelectionCodes.xml<br>
     *
     * @note Default is code [DCM-113000] with following attributes : <br>
     *     Tag.CodingSchemeDesignator = "DCM" <br>
     *     Tag.CodeValue = 113000 <br>
     *     Tag.CodeMeaning = "Of Interest"
     */
    final Attributes documentTitle = codeByValue.get("113000").toCodeItem();
    // TODO - the user or some preferences should be able to set this title value from a predefined
    // list of code

    /**
     * @note "Document Title Modifier" should be set when "Document Title" meets one of the
     *     following case : <br>
     *     - Concept Name = (113001, DCM, "Rejected for Quality Reasons") <br>
     *     - Concept Name = (113010, DCM," Quality Issue") <br>
     *     - Concept Name = (113013, DCM, "Best In Set")
     * @see PS 3.16 - Structured Reporting Templates § TID 2010 Key Object Selection
     */

    // TODO - add ability to set "Optional Document Title Modifier" for created KOS from the
    // predefined list of code
    // final Attributes documentTitleModifier = null;

    final String seriesNumber = "999"; // A number that identifies the Series. (default: 999)
    final String instanceNumber = "1"; // A number that identifies the Document. (default: 1)

    // TODO - add ability to override default instanceNumber and seriesNumber from given parameters
    // in case many
    // KEY OBJECT DOCUMENT SERIES and KEY OBJECT DOCUMENT are build for the same Study in the same
    // context

    final int[] patientStudyAttributes = {
      Tag.SpecificCharacterSet,
      Tag.StudyDate,
      Tag.StudyTime,
      Tag.AccessionNumber,
      Tag.IssuerOfAccessionNumberSequence,
      Tag.ReferringPhysicianName,
      Tag.PatientName,
      Tag.PatientID,
      Tag.IssuerOfPatientID,
      Tag.PatientBirthDate,
      Tag.PatientSex,
      Tag.StudyInstanceUID,
      Tag.StudyID
    };
    Arrays.sort(patientStudyAttributes);

    /**
     * @note Add selected attributes from another Attributes object to this. The specified array of
     *     tag values must be sorted (as by the {@link java.util.Arrays#sort(int[])} method) prior
     *     to making this call.
     */
    Attributes dKOS = new Attributes(dicomSourceAttribute, patientStudyAttributes);

    dKOS.setString(Tag.SOPClassUID, VR.UI, UID.KeyObjectSelectionDocumentStorage);
    dKOS.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
    dKOS.setDate(Tag.ContentDateAndTime, new Date());
    dKOS.setString(Tag.Modality, VR.CS, "KO");
    dKOS.setNull(Tag.ReferencedPerformedProcedureStepSequence, VR.SQ);
    dKOS.setString(
        Tag.SeriesInstanceUID,
        VR.UI,
        StringUtil.hasText(seriesInstanceUID) ? seriesInstanceUID : UIDUtils.createUID());
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
    // documentTitleModifierSequence.newSequence(Tag.ConceptCodeSequence,
    // 1).add(documentTitleModifier);
    //
    // contentSeq.add(documentTitleModifierSequence);
    // }

    if (StringUtil.hasText(keyObjectDescription)) {

      Attributes keyObjectDescriptionSequence = new Attributes(4);
      keyObjectDescriptionSequence.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
      keyObjectDescriptionSequence.setString(Tag.ValueType, VR.CS, "TEXT");
      keyObjectDescriptionSequence
          .newSequence(Tag.ConceptNameCodeSequence, 1)
          .add(codeByValue.get("113012").toCodeItem());
      keyObjectDescriptionSequence.setString(Tag.TextValue, VR.UT, keyObjectDescription);

      contentSeq.add(keyObjectDescriptionSequence);
      dKOS.setString(Tag.SeriesDescription, VR.LO, keyObjectDescription);
    }

    // TODO - Handle Identical Documents Sequence (see below)
    /**
     * @see DICOM standard PS 3.3 - § C.17.6 Key Object Selection Modules && § C.17.6.2.1 Identical
     *     Documents
     * @note The Unique identifier for the Study (studyInstanceUID) is supposed to be the same as to
     *     one of the referenced image, but it's not necessary. Standard says that if the Current
     *     Requested Procedure Evidence Sequence (0040,A375) references SOP Instances both in the
     *     current study and in one or more other studies, this document shall be duplicated into
     *     each of those other studies, and the duplicates shall be referenced in the Identical
     *     Documents Sequence (0040,A525).
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
      stream =
          DicomMediaUtils.class.getResourceAsStream(
              "/config/KeyObjectSelectionCodes.xml"); // NON-NLS
      xmler = factory.createXMLStreamReader(stream);

      while (xmler.hasNext()) {
        if (xmler.next() == XMLStreamConstants.START_ELEMENT) {
          String key = xmler.getName().getLocalPart();
          if ("resources".equals(key)) { // NON-NLS
            while (xmler.hasNext()) {
              if (xmler.next() == XMLStreamConstants.START_ELEMENT) {
                readCodeResource(xmler, codeByValue);
              }
            }
          }
        }
      }
    } catch (XMLStreamException e) {
      LOGGER.error("Reading KO Codes", e);
      codeByValue = null;
    } finally {
      FileUtil.safeClose(xmler);
      FileUtil.safeClose(stream);
    }
    return codeByValue;
  }

  private static void readCodeResource(
      XMLStreamReader xmler, Map<String, KeyObjectSelectionCode> codeByValue)
      throws XMLStreamException {
    String key = xmler.getName().getLocalPart();
    if ("resource".equals(key)) { // NON-NLS
      String resourceName = xmler.getAttributeValue(null, "name"); // NON-NLS
      String contextGroupID = xmler.getAttributeValue(null, "contextId");

      while (xmler.hasNext()) {
        int eventType = xmler.next();
        if (eventType == XMLStreamConstants.START_ELEMENT) {
          key = xmler.getName().getLocalPart();
          if ("code".equals(key)) { // NON-NLS

            String codingSchemeDesignator = xmler.getAttributeValue(null, "scheme"); // NON-NLS
            String codeValue = xmler.getAttributeValue(null, "value"); // NON-NLS
            String codeMeaning = xmler.getAttributeValue(null, "meaning"); // NON-NLS

            String conceptNameCodeModifier = xmler.getAttributeValue(null, "conceptMod");
            String contextGroupIdModifier = xmler.getAttributeValue(null, "contexId");

            codeByValue.put(
                codeValue,
                new KeyObjectSelectionCode(
                    resourceName,
                    contextGroupID,
                    codingSchemeDesignator,
                    codeValue,
                    codeMeaning,
                    conceptNameCodeModifier,
                    contextGroupIdModifier));
          }
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
    final String contextGroupIdModifier;

    public KeyObjectSelectionCode(
        String resourceName,
        String contextGroupID,
        String codingSchemeDesignator,
        String codeValue,
        String codeMeaning,
        String conceptNameCodeModifier,
        String contextGroupIdModifier) {

      this.resourceName = resourceName;
      this.contextGroupID = contextGroupID;

      this.codingSchemeDesignator = codingSchemeDesignator;
      this.codeValue = codeValue;
      this.codeMeaning = codeMeaning;

      this.conceptNameCodeModifier = conceptNameCodeModifier;
      this.contextGroupIdModifier = contextGroupIdModifier;
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

  public static TemporalAccessor getDateFromDicomElement(
      TagType type,
      Attributes dicom,
      int tag,
      String privateCreatorID,
      TemporalAccessor defaultValue) {
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

  public static TemporalAccessor[] getDatesFromDicomElement(
      TagType type,
      Attributes dicom,
      int tag,
      String privateCreatorID,
      TemporalAccessor[] defaultValue) {
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

  public static TemporalAccessor getDateFromDicomElement(
      XMLStreamReader xmler, String attribute, TagType type, TemporalAccessor defaultValue) {
    if (attribute != null) {
      String val = xmler.getAttributeValue(null, attribute);
      if (val != null) {
        if (TagType.DICOM_TIME.equals(type)) {
          return TagD.getDicomTime(val);
        } else if (TagType.DICOM_DATETIME.equals(type)) {
          return TagD.getDicomDateTime(val);
        } else {
          return TagD.getDicomDate(val);
        }
      }
    }
    return defaultValue;
  }

  public static TemporalAccessor[] getDatesFromDicomElement(
      XMLStreamReader xmler, String attribute, TagType type, TemporalAccessor[] defaultValue) {
    return getDatesFromDicomElement(xmler, attribute, type, defaultValue, "\\");
  }

  public static TemporalAccessor[] getDatesFromDicomElement(
      XMLStreamReader xmler,
      String attribute,
      TagType type,
      TemporalAccessor[] defaultValue,
      String separator) {
    if (attribute != null) {
      String val = xmler.getAttributeValue(null, attribute);
      if (val != null) {
        String[] strs = val.split(Pattern.quote(separator));
        TemporalAccessor[] vals = new TemporalAccessor[strs.length];
        for (int i = 0; i < strs.length; i++) {
          if (TagType.DICOM_TIME.equals(type)) {
            vals[i] = TagD.getDicomTime(strs[i]);
          } else if (TagType.DICOM_DATETIME.equals(type)) {
            vals[i] = TagD.getDicomDateTime(strs[i]);
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

  public static void fillAttributes(
      Attributes dataset, final TagW tag, final Object val, ElementDictionary dic) {
    if (dataset != null && tag != null) {
      TagType type = tag.getType();
      int id = tag.getId();
      String key = dic.keywordOf(id);
      if (val == null || !StringUtil.hasLength(key)) {
        return;
      }

      if (tag.isStringFamilyType()) {
        if (val instanceof String[] stringArray) {
          dataset.setString(id, dic.vrOf(id), stringArray);
        } else {
          dataset.setString(id, dic.vrOf(id), val.toString());
        }
      } else if (TagType.DICOM_DATE.equals(type)
          || TagType.DICOM_TIME.equals(type)
          || TagType.DICOM_DATETIME.equals(type)) {
        if (val instanceof TemporalAccessor temporalAccessor) {
          dataset.setDate(id, dic.vrOf(id), TagUtil.toLocalDate(temporalAccessor));
        } else if (val.getClass().isArray()) {
          dataset.setDate(id, dic.vrOf(id), TagUtil.toLocalDates(val));
        }
      } else if (TagType.INTEGER.equals(type)) {
        if (val instanceof Integer integer) {
          dataset.setInt(id, dic.vrOf(id), integer);
        } else if (val instanceof int[] intArray) {
          dataset.setInt(id, dic.vrOf(id), intArray);
        }
      } else if (TagType.FLOAT.equals(type)) {
        if (val instanceof Float floatVal) {
          dataset.setFloat(id, dic.vrOf(id), floatVal);
        } else if (val instanceof float[] floatArray) {
          dataset.setFloat(id, dic.vrOf(id), floatArray);
        }
      } else if (TagType.DOUBLE.equals(type)) {
        if (val instanceof Double doubleVal) {
          dataset.setDouble(id, dic.vrOf(id), doubleVal);
        } else if (val instanceof double[] doubleArray) {
          dataset.setDouble(id, dic.vrOf(id), doubleArray);
        }
      } else if (TagType.DICOM_SEQUENCE.equals(type) && val instanceof Attributes[] sIn) {
        Sequence sOut = dataset.newSequence(id, sIn.length);
        for (Attributes attributes : sIn) {
          sOut.add(new Attributes(attributes));
        }
      }
    }
  }
}
