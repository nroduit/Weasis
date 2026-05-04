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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class KeyDoubleTest {

  @Test
  void rounds_key_to_two_decimals_while_preserving_full_precision_value() {
    KeyDouble kd = new KeyDouble(1.23456);
    assertEquals(1.23456, kd.getValue());
    assertEquals(1.23, kd.getKey(), 1e-9);
  }

  @Test
  void equals_uses_rounded_key_so_close_values_are_equal() {
    KeyDouble a = new KeyDouble(1.234);
    KeyDouble b = new KeyDouble(1.231);
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void equals_returns_false_for_different_keys_and_other_types() {
    assertNotEquals(new KeyDouble(1.0), new KeyDouble(2.0));
    assertNotEquals(new KeyDouble(1.0), "1.0");
    assertNotEquals(null, new KeyDouble(1.0));
  }

  @Test
  void compare_to_orders_by_full_precision_value() {
    KeyDouble a = new KeyDouble(1.234);
    KeyDouble b = new KeyDouble(1.235);
    assertTrue(a.compareTo(b) < 0);
    assertTrue(b.compareTo(a) > 0);
    assertEquals(0, a.compareTo(new KeyDouble(1.234)));
  }

  @Test
  void to_string_returns_full_precision_value() {
    assertEquals("1.2345", new KeyDouble(1.2345).toString());
  }
}
