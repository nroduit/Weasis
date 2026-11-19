/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class BuiltInNamesTest {

  private static final String DOMAIN = "test"; // NON-NLS

  private static UnaryOperator<String> bundle(Map<String, String> values) {
    return key -> values.getOrDefault(key, '!' + key + '!');
  }

  @Test
  void translates_a_bundled_name_and_gives_back_the_english_one() {
    String fr =
        BuiltInNames.translate(
            DOMAIN, "lung", "Lung", "k.lung", bundle(Map.of("k.lung", "Poumon"))); // NON-NLS
    assertEquals("Poumon", fr); // NON-NLS
    assertEquals("Lung", BuiltInNames.source(DOMAIN, "lung", "Poumon")); // NON-NLS
    assertEquals("Poumon", BuiltInNames.display(DOMAIN, "lung", "Lung")); // NON-NLS
    assertTrue(BuiltInNames.isBundled(DOMAIN, "lung", "Lung")); // NON-NLS
    assertTrue(BuiltInNames.isBundled(DOMAIN, "lung", "Poumon")); // NON-NLS
  }

  @Test
  void keeps_a_name_written_by_a_user_or_a_missing_translation() {
    BuiltInNames.translate(
        DOMAIN, "bone", "Bone", "k.bone", bundle(Map.of("k.bone", "Os"))); // NON-NLS
    assertEquals("My bone", BuiltInNames.display(DOMAIN, "bone", "My bone")); // NON-NLS
    assertEquals("My bone", BuiltInNames.source(DOMAIN, "bone", "My bone")); // NON-NLS
    assertEquals("Bone", BuiltInNames.display(DOMAIN, "other", "Bone")); // NON-NLS
    assertFalse(BuiltInNames.isBundled(DOMAIN, "bone", "My bone")); // NON-NLS

    assertEquals(
        "Brain",
        BuiltInNames.translate(DOMAIN, "brain", "Brain", "k.brain", bundle(Map.of()))); // NON-NLS
  }

  @Test
  void a_document_read_again_keeps_its_english_source() {
    UnaryOperator<String> fr = bundle(Map.of("k.liver", "Foie")); // NON-NLS
    BuiltInNames.translate(DOMAIN, "liver", "Liver", "k.liver", fr); // NON-NLS
    // A reload may hand the translated name back
    assertEquals("Foie", BuiltInNames.translate(DOMAIN, "liver", "Foie", "k.liver", fr)); // NON-NLS
    assertEquals("Liver", BuiltInNames.source(DOMAIN, "liver", "Foie")); // NON-NLS
  }

  @Test
  void slug_of_a_section() {
    assertEquals("soft-tissue-skin", BuiltInNames.slug("Soft Tissue & Skin")); // NON-NLS
    assertEquals("dicom", BuiltInNames.slug(" DICOM ")); // NON-NLS
  }
}
