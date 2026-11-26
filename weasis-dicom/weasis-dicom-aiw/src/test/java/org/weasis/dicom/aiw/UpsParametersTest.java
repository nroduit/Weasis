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

  @Test
  void a_numeric_parameter_carries_its_measurement_units() {
    Attributes item =
        UpsParameters.item(new UpsParameters.Parameter("CLIP_LIMIT", "NUMERIC", "2.0"));

    assertAll(
        () -> assertEquals(VR.SQ, item.getVR(Tag.MeasurementUnitsCodeSequence)),
        () -> assertEquals(1, item.getSequence(Tag.MeasurementUnitsCodeSequence).size()));
    Attributes units = item.getSequence(Tag.MeasurementUnitsCodeSequence).get(0);
    assertAll(
        () -> assertEquals("1", units.getString(Tag.CodeValue)),
        () -> assertEquals("UCUM", units.getString(Tag.CodingSchemeDesignator)),
        () -> assertEquals("unity", units.getString(Tag.CodeMeaning)));
  }

  @Test
  void a_name_longer_than_sixteen_characters_uses_the_long_code_value() {
    String name = "CONFIDENCE_THRESHOLD"; // NON-NLS
    Attributes item = UpsParameters.item(new UpsParameters.Parameter(name, "NUMERIC", "0.9"));

    Attributes nameCode = item.getSequence(Tag.ConceptNameCodeSequence).get(0);
    assertAll(
        () -> assertFalse(nameCode.contains(Tag.CodeValue)),
        () -> assertEquals(name, nameCode.getString(Tag.LongCodeValue)),
        () -> assertEquals(VR.UC, nameCode.getVR(Tag.LongCodeValue)));
  }

  @Test
  void a_code_value_longer_than_sixteen_characters_uses_the_long_code_value() {
    String codeValue = "12345678901234567"; // NON-NLS 17 characters
    Attributes item =
        UpsParameters.item(new UpsParameters.Parameter("MODE", "CODE", "99CLARUS:" + codeValue));

    Attributes code = item.getSequence(Tag.ConceptCodeSequence).get(0);
    assertAll(
        () -> assertFalse(code.contains(Tag.CodeValue)),
        () -> assertEquals(codeValue, code.getString(Tag.LongCodeValue)),
        () -> assertEquals(VR.UC, code.getVR(Tag.LongCodeValue)));
  }

  @Test
  void a_numeric_value_is_a_single_decimal_of_at_most_sixteen_bytes() {
    assertAll(
        () -> assertEquals("2.0", new UpsParameters.Parameter("X", "NUMERIC", "2.0").value()),
        () -> assertEquals("1e-3", new UpsParameters.Parameter("X", "NUMERIC", "1e-3").value()),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> new UpsParameters.Parameter("X", "NUMERIC", "abc")),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> new UpsParameters.Parameter("X", "NUMERIC", "")),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> new UpsParameters.Parameter("X", "NUMERIC", "2.0\\4.0")),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> new UpsParameters.Parameter("X", "NUMERIC", "1".repeat(17))));
  }

  @Test
  void a_code_value_with_an_empty_component_is_rejected() {
    assertAll(
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> new UpsParameters.Parameter("MODE", "CODE", ":FAST")),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> new UpsParameters.Parameter("MODE", "CODE", "99CLARUS:")));
  }

  @Test
  void a_bare_code_value_and_a_colon_inside_the_value_are_kept() {
    Attributes bare = UpsParameters.item(new UpsParameters.Parameter("MODE", "CODE", "FAST"));
    Attributes bareCode = bare.getSequence(Tag.ConceptCodeSequence).get(0);
    assertAll(
        () -> assertEquals("FAST", bareCode.getString(Tag.CodeValue)),
        () -> assertEquals(UpsParameters.SCHEME, bareCode.getString(Tag.CodingSchemeDesignator)));

    Attributes coloned =
        UpsParameters.item(new UpsParameters.Parameter("MODE", "CODE", "99CLARUS:A:B"));
    Attributes colonedCode = coloned.getSequence(Tag.ConceptCodeSequence).get(0);
    assertAll(
        () -> assertEquals("A:B", colonedCode.getString(Tag.CodeValue)),
        () ->
            assertEquals(
                "99CLARUS", colonedCode.getString(Tag.CodingSchemeDesignator)));
  }
}
