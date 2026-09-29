/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.sr;

import java.util.Date;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.util.DicomUtils;
import org.weasis.dicom.macro.Code;
import org.weasis.dicom.macro.Module;
import org.weasis.dicom.macro.SOPInstanceReference;

/**
 * Accessors for the attributes of an SR content item (PS3.3 C.17.3 SR Document Content Module). The
 * root content item and every item of a Content Sequence share these attributes.
 */
public class SRDocumentContentModule extends Module {

  public SRDocumentContentModule(Attributes dcmItems) {
    super(dcmItems);
  }

  public Code getNestedCode(int tag) {
    Attributes item = dcmItems.getNestedDataset(tag);
    return item != null ? new Code(item) : null;
  }

  /** Value Type (0040,A040). Absent for a by-reference content item. */
  public String getValueType() {
    return dcmItems.getString(Tag.ValueType);
  }

  public Code getConceptNameCode() {
    return getNestedCode(Tag.ConceptNameCodeSequence);
  }

  public Date getDateTime() {
    return dcmItems.getDate(Tag.DateTime);
  }

  /** Raw DT value of DateTime (0040,A120), preserving the time zone offset when present. */
  public String getDateTimeString() {
    return dcmItems.getString(Tag.DateTime);
  }

  public Date getDate() {
    return dcmItems.getDate(Tag.Date);
  }

  /** Raw DA value of Date (0040,A121). */
  public String getDateString() {
    return dcmItems.getString(Tag.Date);
  }

  public Date getTime() {
    return dcmItems.getDate(Tag.Time);
  }

  /** Raw TM value of Time (0040,A122). */
  public String getTimeString() {
    return dcmItems.getString(Tag.Time);
  }

  public String getPersonName() {
    return dcmItems.getString(Tag.PersonName);
  }

  public String getUID() {
    return dcmItems.getString(Tag.UID);
  }

  public String getTextValue() {
    return dcmItems.getString(Tag.TextValue);
  }

  /** First item of Measured Value Sequence (0040,A300), or null. */
  public Attributes getMeasuredValue() {
    return dcmItems.getNestedDataset(Tag.MeasuredValueSequence);
  }

  public Code getNumericValueQualifierCode() {
    return getNestedCode(Tag.NumericValueQualifierCodeSequence);
  }

  public Code getConceptCode() {
    return getNestedCode(Tag.ConceptCodeSequence);
  }

  /** First item of Referenced SOP Sequence (0008,1199) for IMAGE, COMPOSITE and WAVEFORM items. */
  public SOPInstanceReference getReferencedSOPInstance() {
    Attributes item = dcmItems.getNestedDataset(Tag.ReferencedSOPSequence);
    return item != null ? new SOPInstanceReference(item) : null;
  }

  public Sequence getReferencedSOPSequence() {
    return dcmItems.getSequence(Tag.ReferencedSOPSequence);
  }

  public Sequence getContent() {
    return dcmItems.getSequence(Tag.ContentSequence);
  }

  public String getContinuityOfContent() {
    return dcmItems.getString(Tag.ContinuityOfContent);
  }

  public Date getObservationDateTime() {
    return dcmItems.getDate(Tag.ObservationDateTime);
  }

  // ===== SCOORD / SCOORD3D (PS3.3 C.18.6 and C.18.9) =====

  public String getGraphicType() {
    return dcmItems.getString(Tag.GraphicType);
  }

  public float[] getGraphicData() {
    return DicomUtils.getFloatArrayFromDicomElement(dcmItems, Tag.GraphicData, null);
  }

  public String getReferencedFrameOfReferenceUID() {
    return dcmItems.getString(Tag.ReferencedFrameOfReferenceUID);
  }

  public String getFiducialUID() {
    return dcmItems.getString(Tag.FiducialUID);
  }

  // ===== TCOORD (PS3.3 C.18.7) =====

  public String getTemporalRangeType() {
    return dcmItems.getString(Tag.TemporalRangeType);
  }

  public int[] getReferencedSamplePositions() {
    return DicomUtils.getIntArrayFromDicomElement(dcmItems, Tag.ReferencedSamplePositions, null);
  }

  public double[] getReferencedTimeOffsets() {
    return DicomUtils.getDoubleArrayFromDicomElement(dcmItems, Tag.ReferencedTimeOffsets, null);
  }

  public String[] getReferencedDateTime() {
    return DicomUtils.getStringArrayFromDicomElement(dcmItems, Tag.ReferencedDateTime);
  }

  // ===== Document level =====

  /** First item of Content Template Sequence (0040,A504), or null. */
  public Attributes getContentTemplate() {
    return dcmItems.getNestedDataset(Tag.ContentTemplateSequence);
  }
}
