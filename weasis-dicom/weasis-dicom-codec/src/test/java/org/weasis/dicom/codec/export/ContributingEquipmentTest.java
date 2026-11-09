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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.gui.util.AppProperties;

class ContributingEquipmentTest {

  @Test
  void weasisIsTheProcessingEquipmentOfWhatItDerives() {
    Attributes dcm = new Attributes();
    ContributingEquipment.weasis(dcm, "Multiplanar reformat"); // NON-NLS
    Attributes item = dcm.getNestedDataset(Tag.ContributingEquipmentSequence);
    assertNotNull(item);
    Attributes purpose = item.getNestedDataset(Tag.PurposeOfReferenceCodeSequence);
    assertAll(
        () -> assertEquals("109102", purpose.getString(Tag.CodeValue)),
        () -> assertEquals("DCM", purpose.getString(Tag.CodingSchemeDesignator)),
        () -> assertEquals(AppProperties.WEASIS_NAME, item.getString(Tag.ManufacturerModelName)),
        () -> assertEquals(AppProperties.WEASIS_VERSION, item.getString(Tag.SoftwareVersions)),
        () -> assertEquals("Multiplanar reformat", item.getString(Tag.ContributionDescription)),
        () -> assertNotNull(item.getDate(Tag.ContributionDateTime)),
        () -> assertEquals(1, dcm.getSequence(Tag.ContributingEquipmentSequence).size()));
  }
}
