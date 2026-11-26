/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.aiw;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * The encoder of the Scheduled Processing Parameters Sequence (0074,1210): one Content Item per
 * value, the value field and its VR following the Value Type (PS3.3 Table 10-2).
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class UpsParametersTest {

  @Test
  void a_numeric_parameter_is_a_numeric_content_item() {
    Attributes item =
        UpsParameters.item(new UpsParameters.Parameter("CLIP_LIMIT", "NUMERIC", "2.0"));

    assertAll(
        () -> assertEquals("NUMERIC", item.getString(Tag.ValueType)),
        () -> assertEquals(VR.CS, item.getVR(Tag.ValueType)),
        () -> assertEquals("2.0", item.getString(Tag.NumericValue)),
        () -> assertEquals(VR.DS, item.getVR(Tag.NumericValue)),
        () -> assertFalse(item.contains(Tag.TextValue)),
        () -> assertFalse(item.contains(Tag.ConceptCodeSequence)));
  }

  @Test
  void a_text_parameter_is_a_text_content_item() {
    Attributes item = UpsParameters.item(new UpsParameters.Parameter("NOTE", "TEXT", "hello"));

    assertAll(
        () -> assertEquals("TEXT", item.getString(Tag.ValueType)),
        () -> assertEquals("hello", item.getString(Tag.TextValue)),
        () -> assertEquals(VR.UT, item.getVR(Tag.TextValue)),
        () -> assertFalse(item.contains(Tag.NumericValue)),
        () -> assertFalse(item.contains(Tag.ConceptCodeSequence)));
  }

  @Test
  void a_code_parameter_is_a_concept_code_sequence() {
    Attributes item =
        UpsParameters.item(new UpsParameters.Parameter("MODE", "CODE", "99CLARUS:FAST"));

    assertAll(
        () -> assertEquals("CODE", item.getString(Tag.ValueType)),
        () -> assertEquals(VR.SQ, item.getVR(Tag.ConceptCodeSequence)),
        () -> assertFalse(item.contains(Tag.NumericValue)),
        () -> assertFalse(item.contains(Tag.TextValue)));
    Attributes code = item.getSequence(Tag.ConceptCodeSequence).get(0);
    assertAll(
        () -> assertEquals("FAST", code.getString(Tag.CodeValue)),
        () -> assertEquals("99CLARUS", code.getString(Tag.CodingSchemeDesignator)),
        () -> assertEquals(VR.SH, code.getVR(Tag.CodeValue)),
        () -> assertEquals(VR.SH, code.getVR(Tag.CodingSchemeDesignator)),
        () -> assertEquals(VR.LO, code.getVR(Tag.CodeMeaning)));
  }

  @Test
  void the_concept_name_code_sequence_names_the_parameter() {
    Attributes item =
        UpsParameters.item(new UpsParameters.Parameter("TILE_GRID", "NUMERIC", "8"));

    Attributes name = item.getSequence(Tag.ConceptNameCodeSequence).get(0);
    assertAll(
        () -> assertEquals(VR.SQ, item.getVR(Tag.ConceptNameCodeSequence)),
        () -> assertEquals("TILE_GRID", name.getString(Tag.CodeValue)),
        () -> assertEquals("99CLARUS", name.getString(Tag.CodingSchemeDesignator)),
        () -> assertEquals(VR.SH, name.getVR(Tag.CodeValue)),
        () -> assertEquals(VR.SH, name.getVR(Tag.CodingSchemeDesignator)),
        () -> assertEquals(VR.LO, name.getVR(Tag.CodeMeaning)));
  }

  @Test
  void two_values_of_one_name_share_the_concept_name() {
    List<Attributes> items =
        UpsParameters.items(
            List.of(
                new UpsParameters.Parameter("CLIP_LIMIT", "NUMERIC", "2.0"),
                new UpsParameters.Parameter("CLIP_LIMIT", "NUMERIC", "4.0")));

    assertAll(
        () -> assertEquals(2, items.size()),
        () ->
            assertEquals(
                "CLIP_LIMIT",
                items.get(0).getSequence(Tag.ConceptNameCodeSequence).get(0).getString(Tag.CodeValue)),
        () ->
            assertEquals(
                "CLIP_LIMIT",
                items.get(1).getSequence(Tag.ConceptNameCodeSequence).get(0).getString(Tag.CodeValue)));
  }

  @Test
  void the_value_type_is_normalized_and_a_foreign_one_is_rejected() {
    assertAll(
        () -> assertEquals("NUMERIC", new UpsParameters.Parameter("X", "numeric", "1").type()),
        () -> assertEquals("TEXT", new UpsParameters.Parameter("X", "text", "a").type()),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> new UpsParameters.Parameter("X", "BOOLEAN", "1")));
  }
}
