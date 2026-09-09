/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.internal.mime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The escapes of magic.mime all decode to one char per byte, whatever their notation. */
class MagicMimeEntryTest {

  @Test
  void hexAndOctalEscapesDecodeToTheSameBytes() throws Exception {
    assertEquals(entry("\\x1F\\x8B").getContent(), entry("\\037\\213").getContent());
    assertEquals("", entry("\\x1F\\x8B").getContent());
  }

  @Test
  void aHexEscapeConsumesExactlyTwoDigits() throws Exception {
    // The signature of PNG: the letter following the escape is a plain character.
    assertEquals("PNG", entry("\\x89PNG").getContent());
    // The digits of the next byte must not be swallowed by the previous escape.
    assertEquals("j", entry("\\x0C\\x6A").getContent());
  }

  @Test
  void anIncompleteHexEscapeIsKeptLiterally() throws Exception {
    assertEquals("xZ", entry("\\xZ").getContent());
  }

  @Test
  void escapedBytesMatchTheRawContent() throws Exception {
    MagicMimeEntry png = entry("\\x89PNG");
    byte[] header = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    assertNotNull(png.getMatch(header));
  }

  private static MagicMimeEntry entry(String content) throws InvalidMagicMimeEntryException {
    return new MagicMimeEntry(new ArrayList<>(List.of("0\tstring\t" + content + "\tx/test")));
  }
}
