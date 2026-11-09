/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.export;

import java.util.Date;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.weasis.core.api.gui.util.AppProperties;

/**
 * Weasis as the Contributing Equipment (0018,A001) of an object it derives: a reformat, a
 * projection, a subtraction, a capture. The item names the software, its version and the purpose of
 * the contribution (DCM 109102, Processing Equipment, by default), so the reader of the object
 * knows what produced it beside the acquisition device the other attributes describe.
 */
public final class ContributingEquipment {

  /** DCM 109102, Processing Equipment. */
  public static final String PROCESSING_EQUIPMENT = "109102"; // NON-NLS

  private static final String DCM = "DCM"; // NON-NLS

  private ContributingEquipment() {}

  /** Adds Weasis as processing equipment, with what it did as the contribution description. */
  public static void weasis(Attributes dcm, String contribution) {
    weasis(dcm, contribution, PROCESSING_EQUIPMENT, "Processing Equipment");
  }

  /** Adds Weasis as contributing equipment with the given purpose of reference. */
  public static void weasis(
      Attributes dcm, String contribution, String purposeCode, String purposeMeaning) {
    Sequence sequence = dcm.ensureSequence(Tag.ContributingEquipmentSequence, 1);
    Attributes item = new Attributes(8);
    Sequence purpose = item.newSequence(Tag.PurposeOfReferenceCodeSequence, 1);
    Attributes code = new Attributes(3);
    code.setString(Tag.CodeValue, VR.SH, purposeCode);
    code.setString(Tag.CodingSchemeDesignator, VR.SH, DCM);
    code.setString(Tag.CodeMeaning, VR.LO, purposeMeaning);
    purpose.add(code);
    item.setString(Tag.Manufacturer, VR.LO, "Weasis Team"); // NON-NLS
    item.setString(Tag.ManufacturerModelName, VR.LO, AppProperties.WEASIS_NAME);
    item.setString(Tag.SoftwareVersions, VR.LO, AppProperties.WEASIS_VERSION);
    item.setDate(Tag.ContributionDateTime, VR.DT, new Date());
    if (contribution != null && !contribution.isBlank()) {
      item.setString(Tag.ContributionDescription, VR.ST, contribution);
    }
    sequence.add(item);
  }
}
