/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.sr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.model.utils.bean.QuantityKind;

/**
 * Writes a DICOM Structured Report of planar measurements: PS3.16 TID 1500 <i>Measurement
 * Report</i> with one TID 1410 <i>Planar ROI Measurements</i> group per region, each region an
 * SCOORD on the image it was drawn on and its values as NUM items with UCUM units. The report is a
 * Comprehensive SR, unverified and partial, which any reader of TID 1500 takes back.
 */
public final class MeasurementReport {

  private static final String DCM = "DCM"; // NON-NLS
  private static final String SCT = "SCT"; // NON-NLS
  private static final String UCUM = "UCUM"; // NON-NLS
  private static final String CONTAINS = "CONTAINS"; // NON-NLS
  private static final String HAS_OBS_CONTEXT = "HAS OBS CONTEXT"; // NON-NLS
  private static final String HAS_CONCEPT_MOD = "HAS CONCEPT MOD"; // NON-NLS
  private static final String SELECTED_FROM = "SELECTED FROM"; // NON-NLS
  private static final String TEMPLATE_RESOURCE = "DCMR"; // NON-NLS

  /** Device Observer UID of this application (TID 1004). */
  public static final String DEVICE_OBSERVER_UID = "1.2.826.0.1.3680043.10.1497.1"; // NON-NLS

  static final Code REPORT = new Code("126000", DCM, null, "Imaging Measurement Report"); // NON-NLS
  static final Code LANGUAGE =
      new Code("121049", DCM, null, "Language of Content Item and Descendants"); // NON-NLS
  static final Code ENGLISH = new Code("eng", "RFC5646", null, "English"); // NON-NLS
  static final Code OBSERVER_TYPE = new Code("121005", DCM, null, "Observer Type"); // NON-NLS
  static final Code DEVICE = new Code("121007", DCM, null, "Device"); // NON-NLS
  static final Code DEVICE_UID = new Code("121012", DCM, null, "Device Observer UID"); // NON-NLS
  static final Code DEVICE_NAME = new Code("121013", DCM, null, "Device Observer Name"); // NON-NLS
  static final Code PROCEDURE = new Code("121058", DCM, null, "Procedure reported"); // NON-NLS
  static final Code IMAGING_PROCEDURE =
      new Code("363679005", SCT, null, "Imaging procedure"); // NON-NLS
  static final Code IMAGE_LIBRARY = new Code("111028", DCM, null, "Image Library"); // NON-NLS
  static final Code IMAGE_LIBRARY_GROUP =
      new Code("126200", DCM, null, "Image Library Group"); // NON-NLS
  static final Code MEASUREMENTS = new Code("126010", DCM, null, "Imaging Measurements"); // NON-NLS
  static final Code MEASUREMENT_GROUP =
      new Code("125007", DCM, null, "Measurement Group"); // NON-NLS
  static final Code TRACKING_ID = new Code("112039", DCM, null, "Tracking Identifier"); // NON-NLS
  static final Code TRACKING_UID =
      new Code("112040", DCM, null, "Tracking Unique Identifier"); // NON-NLS
  static final Code IMAGE_REGION = new Code("111030", DCM, null, "Image Region"); // NON-NLS

  static final Code LENGTH = new Code("410668003", SCT, null, "Length"); // NON-NLS
  static final Code AREA = new Code("42798000", SCT, null, "Area"); // NON-NLS
  static final Code PERIMETER = new Code("131191004", SCT, null, "Perimeter"); // NON-NLS
  static final Code DIAMETER = new Code("81827009", SCT, null, "Diameter"); // NON-NLS
  static final Code WIDTH = new Code("103355008", SCT, null, "Width"); // NON-NLS
  static final Code HEIGHT = new Code("121207", DCM, null, "Height"); // NON-NLS
  static final Code LONG_AXIS = new Code("103339001", SCT, null, "Long Axis"); // NON-NLS
  static final Code SHORT_AXIS = new Code("103340003", SCT, null, "Short Axis"); // NON-NLS

  /** A measured value: its concept, number and UCUM unit. */
  public record Value(Code concept, double value, String ucum) {}

  /**
   * A region of an image.
   *
   * @param graphicType the SCOORD graphic type: POINT, POLYLINE, CIRCLE or ELLIPSE
   * @param pixels the Graphic Data, column and row in DICOM PIXEL units (PS3.3 C.18.6.1.1)
   */
  public record Region(
      String trackingId, String label, String graphicType, double[] pixels, List<Value> values) {}

  /**
   * The image the regions are drawn on.
   *
   * @param frames the referenced frames, empty for all or a single-frame image
   * @param volumeOrigin whether the coordinates are those of the Total Pixel Matrix of a tiled
   *     image (Pixel Origin Interpretation VOLUME)
   */
  public record ImageRef(
      String studyInstanceUID,
      String seriesInstanceUID,
      String sopClassUID,
      String sopInstanceUID,
      int[] frames,
      boolean volumeOrigin) {}

  private MeasurementReport() {}

  /**
   * The value of a measurement when the report has a concept for it: lengths and areas of the
   * geometry; nothing for angles, ratios, coordinates and pixel statistics.
   *
   * @param key the measurement key, e.g. {@code measure.length}
   * @param spatial the unit of the value
   */
  public static Optional<Value> value(QuantityKind kind, String key, double value, Unit spatial) {
    if (!Double.isFinite(value) || spatial == null) {
      return Optional.empty();
    }
    String full = key == null ? "" : key.toLowerCase(Locale.ROOT);
    if (full.contains("ombb")) { // NON-NLS
      return Optional.empty(); // the bounding box of a region describes its drawing, not it
    }
    String name = full.substring(full.lastIndexOf('.') + 1);
    if (kind == QuantityKind.AREA) {
      return Optional.of(new Value(AREA, value, spatial.getUcumArea()));
    }
    if (kind != QuantityKind.LENGTH) {
      return Optional.empty();
    }
    Code concept =
        switch (name) {
          case "perimeter" -> PERIMETER; // NON-NLS
          case "diameter" -> DIAMETER; // NON-NLS
          case "width" -> WIDTH; // NON-NLS
          case "height" -> HEIGHT; // NON-NLS
          case "long" -> LONG_AXIS; // NON-NLS
          case "short" -> SHORT_AXIS; // NON-NLS
          case "radius", "dx", "dy" -> null; // NON-NLS
          default -> LENGTH;
        };
    return concept == null
        ? Optional.empty()
        : Optional.of(new Value(concept, value, spatial.getUcum()));
  }

  /**
   * Builds the report.
   *
   * @param source the object the patient and study attributes are copied from
   * @param image the image of the regions
   * @param regions the regions, each one measurement group
   * @param description the series description of the report
   */
  public static Attributes build(
      Attributes source, ImageRef image, List<Region> regions, String description) {
    int[] copied = {
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
    Arrays.sort(copied);
    Attributes sr = new Attributes(source, copied);
    sr.setString(Tag.SpecificCharacterSet, VR.CS, "ISO_IR 192"); // NON-NLS
    sr.setString(Tag.SOPClassUID, VR.UI, UID.ComprehensiveSRStorage);
    sr.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
    sr.setString(Tag.Modality, VR.CS, "SR"); // NON-NLS
    sr.setString(Tag.Manufacturer, VR.LO, "Weasis"); // NON-NLS
    sr.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
    sr.setString(Tag.SeriesNumber, VR.IS, "9900"); // NON-NLS
    sr.setString(Tag.SeriesDescription, VR.LO, description);
    sr.setString(Tag.InstanceNumber, VR.IS, "1"); // NON-NLS
    Date now = new Date();
    sr.setDate(Tag.ContentDateAndTime, now);
    sr.setDate(Tag.SeriesDateAndTime, now);
    sr.setNull(Tag.ReferencedPerformedProcedureStepSequence, VR.SQ);
    sr.setNull(Tag.PerformedProcedureCodeSequence, VR.SQ);
    sr.setString(Tag.CompletionFlag, VR.CS, "PARTIAL"); // NON-NLS
    sr.setString(Tag.VerificationFlag, VR.CS, "UNVERIFIED"); // NON-NLS
    sr.setString(Tag.PreliminaryFlag, VR.CS, "PRELIMINARY"); // NON-NLS
    if (image.studyInstanceUID() != null && !sr.containsValue(Tag.StudyInstanceUID)) {
      sr.setString(Tag.StudyInstanceUID, VR.UI, image.studyInstanceUID());
    }
    evidence(sr, image);

    sr.setString(Tag.ValueType, VR.CS, "CONTAINER"); // NON-NLS
    sr.newSequence(Tag.ConceptNameCodeSequence, 1).add(REPORT.toItem());
    sr.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE"); // NON-NLS
    template(sr, "1500"); // NON-NLS
    Sequence content = sr.newSequence(Tag.ContentSequence, 6 + regions.size());
    content.add(code(HAS_CONCEPT_MOD, LANGUAGE, ENGLISH));
    content.add(code(HAS_OBS_CONTEXT, OBSERVER_TYPE, DEVICE));
    content.add(uidref(HAS_OBS_CONTEXT, DEVICE_UID, DEVICE_OBSERVER_UID));
    content.add(text(HAS_OBS_CONTEXT, DEVICE_NAME, "Weasis")); // NON-NLS
    content.add(code(HAS_CONCEPT_MOD, PROCEDURE, IMAGING_PROCEDURE));
    content.add(imageLibrary(image));
    Attributes measurements = container(CONTAINS, MEASUREMENTS, null);
    Sequence groups = measurements.newSequence(Tag.ContentSequence, regions.size());
    for (Region region : regions) {
      groups.add(group(region, image));
    }
    content.add(measurements);
    return sr;
  }

  /** Writes the report as an Explicit VR Little Endian file. */
  public static void write(Attributes report, Path file) throws IOException {
    if (file.getParent() != null) {
      Files.createDirectories(file.getParent());
    }
    try (DicomOutputStream out =
        new DicomOutputStream(Files.newOutputStream(file), UID.ExplicitVRLittleEndian)) {
      out.writeDataset(report.createFileMetaInformation(UID.ExplicitVRLittleEndian), report);
    }
  }

  // Current Requested Procedure Evidence: the study, series and instance the regions refer to
  private static void evidence(Attributes sr, ImageRef image) {
    Attributes instance = new Attributes(2);
    instance.setString(Tag.ReferencedSOPClassUID, VR.UI, image.sopClassUID());
    instance.setString(Tag.ReferencedSOPInstanceUID, VR.UI, image.sopInstanceUID());
    Attributes series = new Attributes(2);
    series.setString(Tag.SeriesInstanceUID, VR.UI, image.seriesInstanceUID());
    series.newSequence(Tag.ReferencedSOPSequence, 1).add(instance);
    Attributes study = new Attributes(2);
    study.setString(
        Tag.StudyInstanceUID,
        VR.UI,
        image.studyInstanceUID() != null
            ? image.studyInstanceUID()
            : sr.getString(Tag.StudyInstanceUID));
    study.newSequence(Tag.ReferencedSeriesSequence, 1).add(series);
    sr.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1).add(study);
  }

  private static Attributes imageLibrary(ImageRef image) {
    Attributes library = container(CONTAINS, IMAGE_LIBRARY, null);
    Attributes group = container(CONTAINS, IMAGE_LIBRARY_GROUP, null);
    group.newSequence(Tag.ContentSequence, 1).add(imageItem(CONTAINS, image));
    library.newSequence(Tag.ContentSequence, 1).add(group);
    return library;
  }

  private static Attributes group(Region region, ImageRef image) {
    Attributes group = container(CONTAINS, MEASUREMENT_GROUP, "1410"); // NON-NLS
    Sequence content = group.newSequence(Tag.ContentSequence, 3 + region.values().size());
    content.add(text(HAS_OBS_CONTEXT, TRACKING_ID, region.trackingId()));
    content.add(uidref(HAS_OBS_CONTEXT, TRACKING_UID, UIDUtils.createUID()));
    Attributes scoord = item(CONTAINS, "SCOORD", IMAGE_REGION); // NON-NLS
    scoord.setString(Tag.GraphicType, VR.CS, region.graphicType());
    scoord.setDouble(Tag.GraphicData, VR.FL, region.pixels());
    scoord.newSequence(Tag.ContentSequence, 1).add(imageItem(SELECTED_FROM, image));
    content.add(scoord);
    for (Value value : region.values()) {
      Attributes num = item(CONTAINS, "NUM", value.concept()); // NON-NLS
      Attributes measured = new Attributes(2);
      measured.setDouble(Tag.NumericValue, VR.DS, value.value());
      measured
          .newSequence(Tag.MeasurementUnitsCodeSequence, 1)
          .add(new Code(value.ucum(), UCUM, null, value.ucum()).toItem());
      num.newSequence(Tag.MeasuredValueSequence, 1).add(measured);
      content.add(num);
    }
    return group;
  }

  private static Attributes imageItem(String relationship, ImageRef image) {
    Attributes item = new Attributes(3);
    item.setString(Tag.RelationshipType, VR.CS, relationship);
    item.setString(Tag.ValueType, VR.CS, "IMAGE"); // NON-NLS
    Attributes ref = new Attributes(4);
    ref.setString(Tag.ReferencedSOPClassUID, VR.UI, image.sopClassUID());
    ref.setString(Tag.ReferencedSOPInstanceUID, VR.UI, image.sopInstanceUID());
    if (image.frames() != null && image.frames().length > 0) {
      ref.setInt(Tag.ReferencedFrameNumber, VR.IS, image.frames());
    }
    if (image.volumeOrigin()) {
      ref.setString(Tag.PixelOriginInterpretation, VR.CS, "VOLUME"); // NON-NLS
    }
    item.newSequence(Tag.ReferencedSOPSequence, 1).add(ref);
    return item;
  }

  private static Attributes container(String relationship, Code name, String templateId) {
    Attributes item = item(relationship, "CONTAINER", name); // NON-NLS
    item.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE"); // NON-NLS
    if (templateId != null) {
      template(item, templateId);
    }
    return item;
  }

  private static void template(Attributes item, String templateId) {
    Attributes template = new Attributes(2);
    template.setString(Tag.MappingResource, VR.CS, TEMPLATE_RESOURCE);
    template.setString(Tag.TemplateIdentifier, VR.CS, templateId);
    item.newSequence(Tag.ContentTemplateSequence, 1).add(template);
  }

  private static Attributes code(String relationship, Code name, Code value) {
    Attributes item = item(relationship, "CODE", name); // NON-NLS
    item.newSequence(Tag.ConceptCodeSequence, 1).add(value.toItem());
    return item;
  }

  private static Attributes text(String relationship, Code name, String value) {
    Attributes item = item(relationship, "TEXT", name); // NON-NLS
    item.setString(Tag.TextValue, VR.UT, value);
    return item;
  }

  private static Attributes uidref(String relationship, Code name, String uid) {
    Attributes item = item(relationship, "UIDREF", name); // NON-NLS
    item.setString(Tag.UID, VR.UI, uid);
    return item;
  }

  private static Attributes item(String relationship, String valueType, Code name) {
    Attributes item = new Attributes(4);
    item.setString(Tag.RelationshipType, VR.CS, relationship);
    item.setString(Tag.ValueType, VR.CS, valueType);
    item.newSequence(Tag.ConceptNameCodeSequence, 1).add(name.toItem());
    return item;
  }
}
