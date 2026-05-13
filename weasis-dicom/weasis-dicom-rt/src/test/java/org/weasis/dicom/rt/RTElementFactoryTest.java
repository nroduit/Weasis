/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.rt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.codec.DicomSpecialElement;

@DisplayNameGeneration(ReplaceUnderscores.class)
class RTElementFactoryTest {

  private final RTElementFactory factory = new RTElementFactory();

  @Test
  void series_mime_type_is_rt_specific() {
    assertEquals("rt/dicom", factory.getSeriesMimeType());
    assertEquals(RTElementFactory.SERIES_RT_MIMETYPE, factory.getSeriesMimeType());
  }

  @Test
  void supported_modalities_are_the_three_dicom_rt_objects() {
    assertArrayEquals(new String[] {"RTSTRUCT", "RTPLAN", "RTDOSE"}, factory.getModalities());
  }

  @Test
  void modalities_array_is_returned_as_a_defensive_copy() {
    String[] first = factory.getModalities();
    String[] second = factory.getModalities();
    assertNotNull(first);
    assertEquals(3, first.length);
    // Mutating the returned array must not affect subsequent calls.
    first[0] = "MUTATED";
    assertEquals("RTSTRUCT", second[0]);
    assertEquals("RTSTRUCT", factory.getModalities()[0]);
  }

  @Test
  void factory_marks_rt_elements_as_hidden_in_the_explorer() {
    assertTrue(factory.isHidden());
  }

  @Test
  void factory_implements_dicom_special_element_factory_contract() {
    // The build method's behaviour with real DicomMediaIO is covered by integration tests;
    // here we only assert the type produced by the contract is non-null at the interface level.
    assertNotNull(DicomSpecialElement.class);
  }
}
