/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonMiniTest {

  @Test
  void parsesObjectsArraysAndScalars() {
    Object root =
        JsonMini.parse(
            """
            {"a": [1, 2.5, -3e2], "b": "x\\"y\\u0041\\n", "c": true, "d": null, "e": {}}
            """);
    assertTrue(root instanceof Map<?, ?>);
    Map<?, ?> map = (Map<?, ?>) root;
    assertEquals(List.of(1.0, 2.5, -300.0), map.get("a"));
    assertEquals("x\"yA\n", map.get("b"));
    assertEquals(Boolean.TRUE, map.get("c"));
    assertNull(map.get("d"));
    assertEquals(Map.of(), map.get("e"));
  }

  @Test
  void refusesTrailingCharactersAndBadEscapes() {
    assertThrows(IllegalArgumentException.class, () -> JsonMini.parse("{} x"));
    assertThrows(IllegalArgumentException.class, () -> JsonMini.parse("\"\\q\""));
    assertThrows(IllegalArgumentException.class, () -> JsonMini.parse("[1,"));
  }
}
