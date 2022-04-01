/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.io.DicomOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.util.StringUtil;
import org.weasis.core.util.StringUtil.Suffix;
import org.weasis.dicom.codec.macro.SOPInstanceReferenceAndMAC;
import org.weasis.dicom.param.AttributeEditor;

public class DicomSpecialElement extends MediaElement implements DicomElement {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomSpecialElement.class);

  public static final SeriesComparator<DicomSpecialElement> ORDER_BY_DESCRIPTION =
      new SeriesComparator<>() {
        @Override
        public int compare(DicomSpecialElement arg0, DicomSpecialElement arg1) {
          return String.CASE_INSENSITIVE_ORDER.compare(arg0.getLabel(), arg1.getLabel());
        }
      };

  public static final SeriesComparator<DicomSpecialElement> ORDER_BY_DATE =
      new SeriesComparator<>() {

        @Override
        public int compare(DicomSpecialElement m1, DicomSpecialElement m2) {

          // Note : Dicom Standard PS3.3 - Table C.17.6-1 KEY OBJECT DOCUMENT SERIES MODULE
          // ATTRIBUTES
          //
          // SeriesDate stands for "Date the Series started" and is optional parameter, don't use
          // this to compare
          // and prefer "Content Date And Time" Tags (date and time the document content creation
          // started)

          LocalDateTime date1 = TagD.dateTime(Tag.ContentDate, Tag.ContentTime, m1);
          LocalDateTime date2 = TagD.dateTime(Tag.ContentDate, Tag.ContentTime, m2);

          if (date1 == null || date2 == null) {
            // SeriesDate and time
            date1 = TagD.dateTime(Tag.SeriesDate, Tag.SeriesTime, m1);
            date2 = TagD.dateTime(Tag.SeriesDate, Tag.SeriesTime, m2);
          }
          if (date1 != null && date2 != null) {
            // inverse time
            int comp = date2.compareTo(date1);
            if (comp != 0) {
              return comp;
            }
          }

          // Note : SeriesNumber stands for a number that identifies the Series.
          // No specific semantics are specified.
          Integer val1 = TagD.getTagValue(m1, Tag.SeriesNumber, Integer.class);
          Integer val2 = TagD.getTagValue(m2, Tag.SeriesNumber, Integer.class);
          if (val1 != null && val2 != null) {
            int comp = val1.compareTo(val2);
            if (comp != 0) {
              return comp;
            }
          }

          return String.CASE_INSENSITIVE_ORDER.compare(m1.getLabel(), m2.getLabel());
        }
      };

  protected String label;

  public DicomSpecialElement(DicomMediaIO mediaIO) {
    super(mediaIO, null);
    initLabel();
  }

  protected String getLabelPrefix() {
    StringBuilder buf = new StringBuilder();
    String modality = TagD.getTagValue(this, Tag.Modality, String.class);
    if (modality != null) {
      buf.append(modality);
      buf.append(" ");
    }
    Integer val = TagD.getTagValue(this, Tag.InstanceNumber, Integer.class);
    if (val != null) {
      buf.append("[");
      buf.append(val);
      buf.append("] ");
    }
    return buf.toString();
  }

  protected void initLabel() {
    StringBuilder buf = new StringBuilder(getLabelPrefix());
    String desc = TagD.getTagValue(this, Tag.SeriesDescription, String.class);
    if (desc != null) {
      buf.append(desc);
    }
    label = buf.toString();
  }

  @Override
  public DicomMediaIO getMediaReader() {
    return (DicomMediaIO) super.getMediaReader();
  }

  public String getShortLabel() {
    return StringUtil.getTruncatedString(label, 50, Suffix.THREE_PTS);
  }

  public String getLabel() {
    return label;
  }

  @Override
  public String toString() {
    String modality = TagD.getTagValue(this, Tag.Modality, String.class);
    int prefix = modality == null ? 0 : modality.length() + 1;
    String l = getShortLabel();
    return l.length() > prefix ? label.substring(prefix) : l;
  }

  @Override
  public Attributes saveToFile(File output, DicomExportParameters params) {
    return saveToFile(this, output, params.dicomEditors());
  }

  public static Attributes saveToFile(
      DicomElement dicom, File output, List<AttributeEditor> dicomEditors) {
    DcmMediaReader reader = dicom.getMediaReader();
    boolean hasTransformation = dicomEditors != null && !dicomEditors.isEmpty();
    // When object is in memory, write it
    if (reader.isEditableDicom() || hasTransformation) {
      Attributes dcm = reader.getDicomObject();
      if (dcm != null) {
        try (DicomOutputStream out = new DicomOutputStream(output)) {
          Attributes dataSet;
          if (hasTransformation) {
            dataSet = new Attributes(dcm);
            dicomEditors.forEach(e -> e.apply(dataSet, null));
          } else {
            dataSet = dcm;
          }
          String dstTsuid = reader.getDicomMetaData().getTransferSyntaxUID();
          if (UID.ImplicitVRLittleEndian.equals(dstTsuid)
              || UID.ExplicitVRBigEndian.equals(dstTsuid)) {
            dstTsuid = UID.ImplicitVRLittleEndian;
          }
          out.writeDataset(dataSet.createFileMetaInformation(dstTsuid), dataSet);
          return dataSet;
        } catch (IOException e) {
          LOGGER.error(
              "Cannot write dicom ({}) into {}", dcm.getString(Tag.SOPInstanceUID), output, e);
        }
      }
    } else {
      MediaElement.saveToFile(reader, output);
      return new Attributes();
    }
    return null;
  }

  public static boolean isSopuidInReferencedSeriesSequence(
      Map<String, SOPInstanceReferenceAndMAC> seq, String sopUID, Integer dicomFrameNumber) {
    if (seq != null && StringUtil.hasText(sopUID) && seq.containsKey(sopUID)) {
      if (dicomFrameNumber != null) {
        SOPInstanceReferenceAndMAC val = seq.get(sopUID);
        int[] seqFrame = val == null ? null : val.getReferencedFrameNumber();
        if (seqFrame == null || seqFrame.length == 0) {
          return true;
        } else {
          for (int k : seqFrame) {
            if (k == dicomFrameNumber) {
              return true;
            }
          }
        }
      } else {
        return true;
      }
    }

    return false;
  }

  /**
   * @param seriesUID the Series Instance UID
   * @param specialElements the list of DicomSpecialElement
   * @return the KOSpecialElement collection for the given parameters, if the referenced seriesUID
   *     is null all the KOSpecialElement from specialElements collection are returned. In any case
   *     all the KOSpecialElement that are writable will be added to the returned collection
   *     whatever is the seriesUID. These KO are part of the new created one's by users of the
   *     application
   */
  public static Collection<KOSpecialElement> getKoSpecialElements(
      Collection<DicomSpecialElement> specialElements, String seriesUID) {

    if (specialElements == null) {
      return Collections.emptySet();
    }

    SortedSet<KOSpecialElement> koElementSet = null;

    for (DicomSpecialElement element : specialElements) {

      if (element instanceof KOSpecialElement koElement) {
        Set<String> referencedSeriesInstanceUIDSet = koElement.getReferencedSeriesInstanceUIDSet();

        if (seriesUID == null
            || referencedSeriesInstanceUIDSet.contains(seriesUID)
            || koElement.getMediaReader().isEditableDicom()) {

          if (koElementSet == null) {
            koElementSet = new TreeSet<>(ORDER_BY_DATE);
          }
          koElementSet.add(koElement);
        }
      }
    }
    return koElementSet == null ? Collections.emptySet() : koElementSet;
  }

  public static Collection<RejectedKOSpecialElement> getRejectionKoSpecialElements(
      Collection<DicomSpecialElement> specialElements, String seriesUID) {

    if (specialElements == null) {
      return Collections.emptySet();
    }

    SortedSet<RejectedKOSpecialElement> koElementSet = null;

    for (DicomSpecialElement element : specialElements) {

      if (element instanceof RejectedKOSpecialElement koElement) {
        Set<String> referencedSeriesInstanceUIDSet = koElement.getReferencedSeriesInstanceUIDSet();

        if (seriesUID == null
            || referencedSeriesInstanceUIDSet.contains(seriesUID)
            || koElement.getMediaReader().isEditableDicom()) {

          if (koElementSet == null) {
            koElementSet = new TreeSet<>(ORDER_BY_DATE);
          }
          koElementSet.add(koElement);
        }
      }
    }
    return koElementSet == null ? Collections.emptySet() : koElementSet;
  }

  public static RejectedKOSpecialElement getRejectionKoSpecialElement(
      Collection<DicomSpecialElement> specialElements,
      String seriesUID,
      String sopUID,
      Integer dicomFrameNumber) {

    if (specialElements == null) {
      return null;
    }
    List<RejectedKOSpecialElement> koList = null;

    for (DicomSpecialElement element : specialElements) {
      if (element instanceof RejectedKOSpecialElement koElement
          && isSopuidInReferencedSeriesSequence(
              koElement.getReferencedSOPInstanceUIDObject(seriesUID), sopUID, dicomFrameNumber)) {
        if (koList == null) {
          koList = new ArrayList<>();
        }
        koList.add(koElement);
      }
    }

    if (koList != null) {
      // return the most recent Rejection Object
      koList.sort(ORDER_BY_DATE);
      return koList.get(0);
    }
    return null;
  }

  public static List<PRSpecialElement> getPRSpecialElements(
      Collection<DicomSpecialElement> specialElements, DicomImageElement img) {

    if (specialElements == null) {
      return Collections.emptyList();
    }
    List<PRSpecialElement> prList = null;

    for (DicomSpecialElement element : specialElements) {
      if (element instanceof PRSpecialElement prElement
          && PresentationStateReader.isImageApplicable(prElement, img)) {
        if (prList == null) {
          prList = new ArrayList<>();
        }
        prList.add(prElement);
      }
    }
    if (prList != null) {
      prList.sort(ORDER_BY_DATE);
    }
    return prList == null ? Collections.emptyList() : prList;
  }
}
