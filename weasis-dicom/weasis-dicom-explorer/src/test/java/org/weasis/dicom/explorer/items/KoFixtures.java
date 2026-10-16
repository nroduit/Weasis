/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.items;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.UIDUtils;

/** Datasets of key objects for the explorer tests, shared across packages. */
public final class KoFixtures {

  private KoFixtures() {}

  public static Attributes koAttributes(String studyUID, String refSeries, int images) {
    Attributes a = new Attributes();
    a.setString(Tag.SOPClassUID, VR.UI, UID.KeyObjectSelectionDocumentStorage);
    a.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
    a.setString(Tag.Modality, VR.CS, "KO");
    a.setString(Tag.PatientID, VR.LO, "P-" + UIDUtils.createUID());
    a.setString(Tag.PatientName, VR.PN, "Doe^Jane");
    a.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
    a.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
    a.setInt(Tag.SeriesNumber, VR.IS, 999);
    a.setInt(Tag.InstanceNumber, VR.IS, 1);
    a.setString(Tag.ContentDate, VR.DA, "20260115");
    a.setString(Tag.ContentTime, VR.TM, "103000");
    Attributes code = item(a, Tag.ConceptNameCodeSequence);
    code.setString(Tag.CodeValue, VR.SH, "113000");
    code.setString(Tag.CodingSchemeDesignator, VR.SH, "DCM");
    code.setString(Tag.CodeMeaning, VR.LO, "Of Interest");
    Attributes text = item(a, Tag.ContentSequence);
    text.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
    text.setString(Tag.ValueType, VR.CS, "TEXT");
    text.setString(Tag.TextValue, VR.UT, "Key images");
    Attributes study = item(a, Tag.CurrentRequestedProcedureEvidenceSequence);
    study.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
    Attributes series = item(study, Tag.ReferencedSeriesSequence);
    series.setString(Tag.SeriesInstanceUID, VR.UI, refSeries);
    Sequence sops = series.newSequence(Tag.ReferencedSOPSequence, images);
    for (int i = 0; i < images; i++) {
      Attributes sop = new Attributes();
      sop.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.CTImageStorage);
      sop.setString(Tag.ReferencedSOPInstanceUID, VR.UI, UIDUtils.createUID());
      sops.add(sop);
    }
    return a;
  }

  /** Adds a one-item sequence to {@code parent} and returns the item. */
  public static Attributes item(Attributes parent, int tag) {
    Sequence sequence = parent.newSequence(tag, 1);
    Attributes item = new Attributes();
    sequence.add(item);
    return item;
  }
}
