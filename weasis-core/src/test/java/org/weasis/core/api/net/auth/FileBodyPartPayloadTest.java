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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.github.scribejava.core.httpclient.HttpClient;
import org.junit.jupiter.api.Test;

class FileBodyPartPayloadTest {

  private static final BodySupplier<java.io.InputStream> EMPTY = BodySupplier.empty();

  @Test
  void singleArgConstructorOmitsContentTypeAndFilename() {
    var payload = new FileBodyPartPayload(EMPTY);
    var headers = payload.getHeaders();
    assertFalse(headers.containsKey(HttpClient.CONTENT_TYPE));
    assertEquals("form-data", headers.get("Content-Disposition"));
    assertSame(EMPTY, payload.getPayload());
  }

  @Test
  void filenameIsAddedToContentDisposition() {
    var payload = new FileBodyPartPayload(EMPTY, "image.dcm");
    assertEquals(
        "form-data; filename=\"image.dcm\"", payload.getHeaders().get("Content-Disposition"));
  }

  @Test
  void contentTypeIsForwardedWhenSupplied() {
    var payload = new FileBodyPartPayload("application/dicom", EMPTY, null);
    var headers = payload.getHeaders();
    assertEquals("application/dicom", headers.get(HttpClient.CONTENT_TYPE));
    assertEquals("form-data", headers.get("Content-Disposition"));
  }

  @Test
  void allHeadersArePresentWhenAllArgumentsProvided() {
    var payload = new FileBodyPartPayload("application/json", EMPTY, "meta.json");
    var headers = payload.getHeaders();
    assertNotNull(headers);
    assertEquals("application/json", headers.get(HttpClient.CONTENT_TYPE));
    assertEquals("form-data; filename=\"meta.json\"", headers.get("Content-Disposition"));
  }
}
