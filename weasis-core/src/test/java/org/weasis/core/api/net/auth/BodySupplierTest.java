/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.net.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BodySupplierTest {

  @Test
  void ofBytesExposesFullArray() throws Exception {
    byte[] data = {1, 2, 3, 4};
    var supplier = BodySupplier.ofBytes(data);
    assertEquals(4, supplier.length());
    try (InputStream in = supplier.get()) {
      assertArrayEquals(data, in.readAllBytes());
    }
  }

  @Test
  void ofBytesWithOffsetExposesSlice() throws Exception {
    byte[] data = {10, 20, 30, 40, 50};
    var supplier = BodySupplier.ofBytes(data, 1, 3);
    assertEquals(3, supplier.length());
    try (InputStream in = supplier.get()) {
      assertArrayEquals(new byte[] {20, 30, 40}, in.readAllBytes());
    }
  }

  @Test
  void ofStringDefaultsToUtf8() throws Exception {
    var supplier = BodySupplier.ofString("héllo");
    try (InputStream in = supplier.get()) {
      assertArrayEquals("héllo".getBytes(StandardCharsets.UTF_8), in.readAllBytes());
    }
  }

  @Test
  void ofStringRespectsCharset() throws Exception {
    var supplier = BodySupplier.ofString("é", StandardCharsets.ISO_8859_1);
    try (InputStream in = supplier.get()) {
      assertArrayEquals(new byte[] {(byte) 0xE9}, in.readAllBytes());
    }
  }

  @Test
  void emptyHasZeroLength() throws Exception {
    var supplier = BodySupplier.empty();
    assertEquals(0, supplier.length());
    try (InputStream in = supplier.get()) {
      assertEquals(0, in.readAllBytes().length);
    }
  }

  @Test
  void ofPathExposesFileContent(@TempDir Path tmp) throws Exception {
    Path file = tmp.resolve("data.bin");
    byte[] payload = "weasis".getBytes(StandardCharsets.UTF_8);
    Files.write(file, payload);

    var supplier = BodySupplier.ofPath(file);
    assertEquals(payload.length, supplier.length());

    // get() must return a fresh stream each call.
    try (InputStream first = supplier.get();
        InputStream second = supplier.get()) {
      assertArrayEquals(payload, first.readAllBytes());
      assertArrayEquals(payload, second.readAllBytes());
    }
  }
}
