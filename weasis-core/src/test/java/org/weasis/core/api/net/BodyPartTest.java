/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.InputStream;
import org.junit.jupiter.api.Test;

class BodyPartTest {

  private static final BodySupplier<InputStream> EMPTY = BodySupplier.empty();

  @Test
  void factoryOmitsContentTypeAndFilenameWhenAbsent() {
    var part = BodyPart.of(null, EMPTY, null);
    var headers = part.getHeaders();
    assertFalse(headers.containsKey("Content-Type"));
    assertEquals("form-data", headers.get("Content-Disposition"));
    assertSame(EMPTY, part.getContent());
  }

  @Test
  void filenameIsAddedToContentDisposition() {
    var part = BodyPart.of(null, EMPTY, "image.dcm");
    assertEquals("form-data; filename=\"image.dcm\"", part.getHeaders().get("Content-Disposition"));
  }

  @Test
  void contentTypeIsForwardedWhenSupplied() {
    var part = BodyPart.of("application/dicom", EMPTY, null);
    var headers = part.getHeaders();
    assertEquals("application/dicom", headers.get("Content-Type"));
    assertEquals("form-data", headers.get("Content-Disposition"));
  }

  @Test
  void allHeadersArePresentWhenAllArgumentsProvided() {
    var part = BodyPart.of("application/json", EMPTY, "meta.json");
    var headers = part.getHeaders();
    assertNotNull(headers);
    assertEquals("application/json", headers.get("Content-Type"));
    assertEquals("form-data; filename=\"meta.json\"", headers.get("Content-Disposition"));
  }

  @Test
  void constructorCopiesHeaders() {
    var mutable = new java.util.HashMap<String, String>();
    mutable.put("Content-Type", "text/plain");
    var part = new BodyPart(mutable, EMPTY);
    mutable.put("X-Extra", "later");
    assertFalse(part.getHeaders().containsKey("X-Extra"));
  }
}
