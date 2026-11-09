/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.provenance.Provenance;
import org.weasis.core.api.util.ResourceUtil.ResourceIconPath;
import org.weasis.core.util.StringUtil;

/**
 * What the explorer shows for a non-image DICOM object: the same contract for a key object
 * selection, a presentation state, a segmentation, an RT object or any hidden element a plugin
 * registers. Built once per element by {@link #describe(DicomSpecialElement)}, which asks the
 * {@link DicomSpecialElementFactory} of the modality so a plugin owns the wording of its objects.
 *
 * @param kind the kind of object
 * @param modality the DICOM modality
 * @param label what the row shows: KO title, PR content label, SEG label, RT label...
 * @param date content date and time, else series date and time, else instance creation
 * @param creator the software or device that produced the object (contributing equipment first,
 *     then manufacturer, model and software versions, then the station name); null when unknown
 * @param studyUID Study Instance UID of the object itself
 * @param seriesUID Series Instance UID of the object itself (the hidden series)
 * @param sopInstanceUID SOP Instance UID of the object
 * @param referencedSeriesUIDs the image series the object references, never null
 * @param referencedImages number of images (or frames) referenced, 0 when not counted
 * @param parts segments of a SEG, structures of an RT Structure Set, 0 otherwise
 * @param icon the badge drawn on the referenced thumbnails
 * @param provenance where the object came from: {@code EXTERNAL} for a device or an algorithm
 *     (segment algorithm, contributing equipment, manufacturer), {@code USER} for a person's key
 *     object or presentation state; null when the object says nothing
 */
public record ObjectDescriptor(
    ObjectKind kind,
    String modality,
    String label,
    LocalDateTime date,
    String creator,
    String studyUID,
    String seriesUID,
    String sopInstanceUID,
    Set<String> referencedSeriesUIDs,
    int referencedImages,
    int parts,
    ResourceIconPath icon,
    Provenance provenance) {

  /** A descriptor without provenance, for the callers that predate it. */
  public ObjectDescriptor(
      ObjectKind kind,
      String modality,
      String label,
      LocalDateTime date,
      String creator,
      String studyUID,
      String seriesUID,
      String sopInstanceUID,
      Set<String> referencedSeriesUIDs,
      int referencedImages,
      int parts,
      ResourceIconPath icon) {
    this(
        kind,
        modality,
        label,
        date,
        creator,
        studyUID,
        seriesUID,
        sopInstanceUID,
        referencedSeriesUIDs,
        referencedImages,
        parts,
        icon,
        null);
  }

  public ObjectDescriptor {
    Objects.requireNonNull(kind);
    referencedSeriesUIDs =
        referencedSeriesUIDs == null
            ? Set.of()
            : Collections.unmodifiableSet(new LinkedHashSet<>(referencedSeriesUIDs));
  }

  /** True when the object references the given image series. */
  public boolean references(String seriesInstanceUID) {
    return seriesInstanceUID != null && referencedSeriesUIDs.contains(seriesInstanceUID);
  }

  /**
   * Describes an element through the factory registered for its modality, falling back to the
   * generic reading of the standard attributes when no factory is registered.
   */
  public static ObjectDescriptor describe(DicomSpecialElement element) {
    Objects.requireNonNull(element);
    String modality = TagD.getTagValue(element, Tag.Modality, String.class);
    DicomSpecialElementFactory factory =
        modality == null ? null : DicomMediaIO.DCM_ELEMENT_FACTORIES.get(modality);
    ObjectDescriptor descriptor = factory == null ? null : factory.describe(element);
    return descriptor == null ? generic(element) : descriptor;
  }

  /**
   * The generic description from the standard attributes of the object; what {@link
   * DicomSpecialElementFactory#describe} returns unless a plugin overrides it.
   */
  public static ObjectDescriptor generic(DicomSpecialElement element) {
    Objects.requireNonNull(element);
    Attributes dicom =
        element.getMediaReader() == null ? null : element.getMediaReader().getDicomObject();
    String modality = string(element, dicom, Tag.Modality);
    ObjectKind kind = kindOf(element, modality);
    String seriesUID = string(element, dicom, Tag.SeriesInstanceUID);
    Set<String> referenced = new LinkedHashSet<>();
    int images = 0;
    int parts = 0;
    String label = element.getLabel();

    if (element instanceof AbstractKOSpecialElement ko) {
      Set<String> set = ko.getReferencedSeriesInstanceUIDSet();
      if (set != null) {
        referenced.addAll(set);
      }
      images = ko.getNumberSelectedImages();
      String title = ko.getDocumentTitle();
      String text = ko.getLabelWithoutPrefix();
      label = StringUtil.hasText(text) ? text : (StringUtil.hasText(title) ? title : label);
    } else if (dicom != null) {
      images = addReferencedSeries(dicom, referenced);
      if (element instanceof PRSpecialElement) {
        String content = dicom.getString(Tag.ContentLabel);
        if (!StringUtil.hasText(content)) {
          content = dicom.getString(Tag.ContentDescription);
        }
        if (StringUtil.hasText(content)) {
          label = content;
        }
      } else if (kind == ObjectKind.SEGMENTATION) {
        parts = sequenceSize(dicom, Tag.SegmentSequence);
        // The element label carries the instance number; a row wants the name alone
        String description = dicom.getString(Tag.SeriesDescription);
        if (!StringUtil.hasText(description)) {
          description = dicom.getString(Tag.ContentLabel);
        }
        if (StringUtil.hasText(description)) {
          label = description;
        }
      } else if (kind == ObjectKind.RT_STRUCTURE_SET) {
        parts = sequenceSize(dicom, Tag.StructureSetROISequence);
      }
    }
    // What the index learnt from the object, for plugins that register references themselves
    referenced.addAll(HiddenSeriesManager.getReferencedSeries(seriesUID));

    return new ObjectDescriptor(
        kind,
        modality,
        label,
        dateOf(element, dicom),
        creatorOf(dicom),
        string(element, dicom, Tag.StudyInstanceUID),
        seriesUID,
        string(element, dicom, Tag.SOPInstanceUID),
        referenced,
        images,
        parts,
        element instanceof HiddenSpecialElement hidden ? hidden.getIconPath() : null,
        provenanceOf(kind, dicom, creatorOf(dicom)));
  }

  /**
   * Where the object came from, read from the carriers the standard gives: the Segment Algorithm
   * Type and Name of a segmentation, the algorithm identification of an SR (TID 4019, Algorithm
   * Name 111001 and Version 111003), the Contributing Equipment or the manufacturer of a device
   * made object; a key object or a presentation state without equipment is the user's, named by the
   * person who created it when the object says so.
   */
  static Provenance provenanceOf(ObjectKind kind, Attributes dicom, String creator) {
    if (dicom == null) {
      return null;
    }
    String version = dicom.getString(Tag.SoftwareVersions);
    if (kind == ObjectKind.SEGMENTATION) {
      Attributes segment = dicom.getNestedDataset(Tag.SegmentSequence);
      if (segment != null) {
        String type = segment.getString(Tag.SegmentAlgorithmType);
        String name = segment.getString(Tag.SegmentAlgorithmName);
        if ("MANUAL".equals(type)) { // NON-NLS
          return Provenance.user(
              StringUtil.hasText(creator) ? creator : "segmentation", "manual segmentation");
        }
        if (StringUtil.hasText(name) || StringUtil.hasText(type)) {
          return Provenance.external(
              StringUtil.hasText(name) ? name : (creator == null ? "algorithm" : creator),
              (type == null ? "" : type.toLowerCase(java.util.Locale.ROOT) + " ") + "segmentation",
              version);
        }
      }
    }
    Attributes algorithm = algorithmItem(dicom, "111001"); // NON-NLS
    if (algorithm != null && StringUtil.hasText(algorithm.getString(Tag.TextValue))) {
      Attributes algorithmVersion = algorithmItem(dicom, "111003"); // NON-NLS
      return Provenance.external(
          algorithm.getString(Tag.TextValue),
          "algorithm of the report", // NON-NLS
          algorithmVersion == null ? null : algorithmVersion.getString(Tag.TextValue));
    }
    Attributes equipment = dicom.getNestedDataset(Tag.ContributingEquipmentSequence);
    if (equipment != null) {
      Attributes code = equipment.getNestedDataset(Tag.PurposeOfReferenceCodeSequence);
      String purpose = code == null ? null : code.getString(Tag.CodeMeaning);
      String who = equipmentOf(equipment);
      return Provenance.external(
          who == null ? "equipment" : who, // NON-NLS
          purpose == null ? "contributing equipment" : purpose, // NON-NLS
          equipment.getString(Tag.SoftwareVersions));
    }
    boolean personal =
        kind == ObjectKind.KEY_OBJECT
            || kind == ObjectKind.REJECTION_NOTE
            || kind == ObjectKind.PRESENTATION_STATE;
    String author = dicom.getString(Tag.ContentCreatorName);
    if (!StringUtil.hasText(author)) {
      author = dicom.getString(Tag.OperatorsName);
    }
    if (personal && StringUtil.hasText(author)) {
      return Provenance.user(author, kind.getId());
    }
    String device = equipmentOf(dicom);
    if (device != null && !personal) {
      return Provenance.external(device, kind.getId(), version);
    }
    return personal && StringUtil.hasText(creator) ? Provenance.user(creator, kind.getId()) : null;
  }

  /** The content item of the first level whose concept has the code, for TID 4019. */
  private static Attributes algorithmItem(Attributes dicom, String code) {
    Sequence content = dicom.getSequence(Tag.ContentSequence);
    if (content == null) {
      return null;
    }
    for (Attributes item : content) {
      Attributes concept = item.getNestedDataset(Tag.ConceptNameCodeSequence);
      if (concept != null && code.equals(concept.getString(Tag.CodeValue))) {
        return item;
      }
    }
    return null;
  }

  /**
   * The value of a tag as the element carries it, else as the dataset holds it: the series and
   * study level tags reach the element only once its series group has been built by the loader.
   */
  private static String string(DicomSpecialElement element, Attributes dicom, int tag) {
    String value = TagD.getTagValue(element, tag, String.class);
    if (!StringUtil.hasText(value) && dicom != null) {
      value = dicom.getString(tag);
    }
    return StringUtil.hasText(value) ? value : null;
  }

  /** The kind of an element, without building its description. */
  public static ObjectKind kindOf(DicomSpecialElement element) {
    Objects.requireNonNull(element);
    Attributes dicom =
        element.getMediaReader() == null ? null : element.getMediaReader().getDicomObject();
    return kindOf(element, string(element, dicom, Tag.Modality));
  }

  private static ObjectKind kindOf(DicomSpecialElement element, String modality) {
    if (element instanceof RejectedKOSpecialElement) {
      return ObjectKind.REJECTION_NOTE;
    }
    if (element instanceof AbstractKOSpecialElement) {
      return ObjectKind.KEY_OBJECT;
    }
    if (element instanceof PRSpecialElement) {
      return ObjectKind.PRESENTATION_STATE;
    }
    return ObjectKind.fromModality(modality);
  }

  /**
   * Adds the Series Instance UIDs of the Referenced Series Sequence (PR, SEG, SR) and of the RT
   * Referenced Series Sequence (RTSTRUCT) to {@code out}; returns the number of referenced images
   * or instances listed under them.
   */
  private static int addReferencedSeries(Attributes dicom, Set<String> out) {
    int images = 0;
    Sequence seriesSeq = dicom.getSequence(Tag.ReferencedSeriesSequence);
    if (seriesSeq != null) {
      for (Attributes item : seriesSeq) {
        images += addSeriesItem(item, out);
      }
    }
    Sequence forSeq = dicom.getSequence(Tag.ReferencedFrameOfReferenceSequence);
    if (forSeq != null) {
      for (Attributes frameOfRef : forSeq) {
        Sequence studies = frameOfRef.getSequence(Tag.RTReferencedStudySequence);
        if (studies != null) {
          for (Attributes study : studies) {
            Sequence rtSeries = study.getSequence(Tag.RTReferencedSeriesSequence);
            if (rtSeries != null) {
              for (Attributes item : rtSeries) {
                images += addSeriesItem(item, out);
              }
            }
          }
        }
      }
    }
    return images;
  }

  private static int addSeriesItem(Attributes item, Set<String> out) {
    String uid = item.getString(Tag.SeriesInstanceUID);
    if (StringUtil.hasText(uid)) {
      out.add(uid);
    }
    int count = sequenceSize(item, Tag.ReferencedImageSequence);
    if (count == 0) {
      count = sequenceSize(item, Tag.ReferencedInstanceSequence);
    }
    if (count == 0) {
      count = sequenceSize(item, Tag.ContourImageSequence);
    }
    return count;
  }

  private static int sequenceSize(Attributes attributes, int tag) {
    Sequence seq = attributes.getSequence(tag);
    return seq == null ? 0 : seq.size();
  }

  /** Content date and time, else series date and time, else instance creation date and time. */
  static LocalDateTime dateOf(DicomSpecialElement element, Attributes dicom) {
    LocalDateTime date = TagD.dateTime(Tag.ContentDate, Tag.ContentTime, element);
    if (date == null) {
      date = TagD.dateTime(Tag.SeriesDate, Tag.SeriesTime, element);
    }
    if (date == null && dicom != null) {
      date = toLocal(dicom.getDate(Tag.ContentDateAndTime));
      if (date == null) {
        date = toLocal(dicom.getDate(Tag.SeriesDateAndTime));
      }
      if (date == null) {
        date = toLocal(dicom.getDate(Tag.InstanceCreationDateAndTime));
      }
    }
    return date;
  }

  private static LocalDateTime toLocal(Date date) {
    return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
  }

  /**
   * The producer of the object: the first item of the Contributing Equipment Sequence (what an
   * algorithm fills), else the Manufacturer, Manufacturer's Model Name and Software Versions of the
   * General Equipment module, else the Station Name.
   */
  static String creatorOf(Attributes dicom) {
    if (dicom == null) {
      return null;
    }
    Attributes equipment = dicom.getNestedDataset(Tag.ContributingEquipmentSequence);
    String creator = equipmentOf(equipment);
    if (creator == null) {
      creator = equipmentOf(dicom);
    }
    if (creator == null) {
      String station = dicom.getString(Tag.StationName);
      creator = StringUtil.hasText(station) ? station : null;
    }
    return creator;
  }

  private static String equipmentOf(Attributes attributes) {
    if (attributes == null) {
      return null;
    }
    StringBuilder buf = new StringBuilder();
    append(buf, attributes.getString(Tag.Manufacturer), " ");
    append(buf, attributes.getString(Tag.ManufacturerModelName), " ");
    String[] versions = attributes.getStrings(Tag.SoftwareVersions);
    if (versions != null && versions.length > 0 && StringUtil.hasText(versions[0])) {
      if (!buf.isEmpty()) {
        buf.append(' ');
      }
      buf.append('(').append(String.join(" ", versions).trim()).append(')');
    }
    return buf.isEmpty() ? null : buf.toString();
  }

  private static void append(StringBuilder buf, String value, String separator) {
    if (StringUtil.hasText(value)) {
      if (!buf.isEmpty()) {
        buf.append(separator);
      }
      buf.append(value.trim());
    }
  }
}
