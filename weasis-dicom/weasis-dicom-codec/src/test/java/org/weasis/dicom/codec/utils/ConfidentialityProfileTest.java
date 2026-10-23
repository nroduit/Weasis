/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.utils;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.MaskingModel;
import org.weasis.core.api.media.data.TagCategory;
import org.weasis.dicom.codec.TagD;

/**
 * The bundled classification against DICOM PS3.15 Table E.1-1: a tag may only fall in a category
 * whose actions the table allows for it, and every attribute of the table that Weasis displays as
 * text is classified.
 */
@DisplayName("DICOM classification and the confidentiality profile")
class ConfidentialityProfileTest {

  private static final String TABLE = "/confidentiality-profile-e1-1.csv"; // NON-NLS

  /** Values that are not text: pseudonymizing them means nothing and costs a full read. */
  private static final Set<VR> BINARY = Set.of(VR.OB, VR.OW, VR.OF, VR.OD, VR.OL, VR.OV, VR.UN);

  /** Weasis lets a site shift the birth date, which the table only removes or empties. */
  private static final Set<Integer> BIRTH = Set.of(Tag.PatientBirthDate, Tag.PatientBirthTime);

  private record Row(String action, Set<String> options, String name) {}

  private static Map<Integer, Row> table;
  private static Map<Integer, TagCategory> bundled;

  @BeforeAll
  static void load() throws IOException {
    table = readTable();
    bundled = new HashMap<>();
    try (InputStream in =
        DicomMediaUtils.class.getResourceAsStream("/identityMasking-dicom.json")) {
      assertNotNull(in);
      for (MaskingModel.TagRule rule : MaskingModel.read(in).tags()) {
        TagD tag = (TagD) TagD.get(rule.keyword());
        assertNotNull(tag, rule.key());
        bundled.put(tag.getId(), rule.category());
      }
    }
  }

  private static Map<Integer, Row> readTable() throws IOException {
    Map<Integer, Row> rows = new HashMap<>();
    try (InputStream in = ConfidentialityProfileTest.class.getResourceAsStream(TABLE)) {
      assertNotNull(in, TABLE);
      BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank() || line.startsWith("#")) {
          continue;
        }
        String[] fields = line.split(";", 4);
        Set<String> options =
            fields[2].isEmpty()
                ? Set.of()
                : Set.of(fields[2].replaceAll("=[A-Z]+", "").split(",")); // NON-NLS
        rows.put(Integer.parseUnsignedInt(fields[0], 16), new Row(fields[1], options, fields[3]));
      }
    }
    return rows;
  }

  /** Whether the table allows the actions of that category for an attribute with these options. */
  private static boolean allowed(TagCategory category, int tag, Set<String> options) {
    return switch (category) {
      // Never kept by any profile, so always at least as strict as the table
      case DIRECT_ID -> true;
      case PATIENT_CHARACTERISTIC -> options.contains("patientCharacteristics");
      case DATE -> options.contains("fullDates") || options.contains("modifiedDates");
      case BIRTH_DATE -> BIRTH.contains(tag);
      case INSTITUTION -> options.contains("institutionIdentity");
      case DEVICE -> options.contains("deviceIdentity");
      case DESCRIPTOR, FREE_TEXT -> options.contains("cleanDescriptors");
      case OTHER -> false;
    };
  }

  @Test
  @DisplayName("the table is read")
  void tableIsRead() {
    assertTrue(table.size() > 500, "rows: " + table.size());
  }

  @Test
  @DisplayName("a tag is only kept where an option of the table keeps it")
  void categoriesFollowTheOptions() {
    List<String> wrong = new ArrayList<>();
    bundled.forEach(
        (tag, category) -> {
          Row row = table.get(tag);
          if (row != null && !allowed(category, tag, row.options())) {
            wrong.add("%s (%08X) %s %s".formatted(row.name(), tag, category, row.options()));
          }
        });
    assertEquals(List.of(), wrong);
  }

  @Test
  @DisplayName("every text attribute of the table is classified")
  void tableIsCovered() {
    List<String> missing = new ArrayList<>();
    table.forEach(
        (tag, row) -> {
          if (TagD.getNullable(tag, null) instanceof TagD dicom
              && dicom.getValueRepresentation() instanceof VR vr
              && vr != VR.SQ
              && !BINARY.contains(vr)
              && (vr != VR.UI || row.options().contains("deviceIdentity"))
              && !bundled.containsKey(tag)) {
            missing.add("%s (%08X)".formatted(row.name(), tag));
          }
        });
    assertAll(
        () -> assertEquals(List.of(), missing),
        () -> assertEquals(TagCategory.DIRECT_ID, bundled.get(Tag.IssuerOfPatientID)),
        () -> assertEquals(TagCategory.DIRECT_ID, bundled.get(Tag.CountryOfResidence)),
        () -> assertEquals(TagCategory.INSTITUTION, bundled.get(Tag.InstitutionAddress)));
  }
}
