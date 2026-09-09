/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.media;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MimeInspectorTest {

  @Test
  void imageSignaturesAreRecognized(@TempDir Path dir) throws IOException {
    assertAll(
        () -> assertEquals("image/jpeg", mime(dir, "jpeg", 0xFF, 0xD8, 0xFF, 0xE0)),
        // Signatures with bytes outside ASCII are written with \x escapes in magic.mime.
        () -> assertEquals("image/png", mime(dir, "png", 0x89, 'P', 'N', 'G', 0x0D, 0x0A)),
        () ->
            assertEquals(
                "image/jp2",
                mime(dir, "jp2", 0, 0, 0, 0x0C, 0x6A, 0x50, 0x20, 0x20, 0x0D, 0x0A, 0x87, 0x0A)),
        () -> assertEquals("image/jp2", mime(dir, "j2k", 0xFF, 0x4F, 0xFF, 0x51)),
        () -> assertEquals("image/gif", mime(dir, "gif", 'G', 'I', 'F', '8', '9', 'a')),
        () -> assertEquals("image/bmp", mime(dir, "bmp", 'B', 'M', 0, 0)));
  }

  @Test
  void payloadsThatAreNotImagesAreNotReportedAsSuch(@TempDir Path dir) throws IOException {
    assertAll(
        () -> assertEquals("text/html", mime(dir, "html", '<', 'h', 't', 'm', 'l', '>')),
        () -> assertNull(mime(dir, "json", '{', '"', 'a', '"', ':', '1', '}')),
        () -> assertNull(mime(dir, "empty")));
  }

  private static String mime(Path dir, String name, int... content) throws IOException {
    byte[] bytes = new byte[content.length];
    for (int i = 0; i < content.length; i++) {
      bytes[i] = (byte) content[i];
    }
    return MimeInspector.getMimeTypeFromMagicNumber(Files.write(dir.resolve(name), bytes));
  }
}
