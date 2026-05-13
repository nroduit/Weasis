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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.scribejava.core.model.Response;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthResponseTest {

  private static Response sampleResponse() {
    return new Response(
        200,
        "OK",
        Map.of("Content-Type", "text/plain"),
        new ByteArrayInputStream("payload".getBytes()));
  }

  @Test
  void constructorRejectsNull() {
    assertThrows(NullPointerException.class, () -> new AuthResponse(null));
  }

  @Test
  void delegatesToWrappedResponse() throws IOException {
    Response r = sampleResponse();
    AuthResponse auth = new AuthResponse(r);

    assertSame(r, auth.response());
    assertEquals(200, auth.getResponseCode());
    assertEquals("OK", auth.getResponseMessage());
    assertEquals("text/plain", auth.getHeaderField("Content-Type"));
    assertNull(auth.getHeaderField("Missing"));
    assertEquals("payload", new String(auth.getInputStream().readAllBytes()));
  }

  @Test
  void closeIsIdempotent() {
    AuthResponse auth = new AuthResponse(sampleResponse());
    auth.close();
    auth.close(); // safeClose should not throw
  }
}
