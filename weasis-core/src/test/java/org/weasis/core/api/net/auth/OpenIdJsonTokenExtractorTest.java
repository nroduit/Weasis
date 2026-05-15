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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenIdJsonTokenExtractorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void instanceIsSingleton() {
    assertSame(OpenIdJsonTokenExtractor.instance(), OpenIdJsonTokenExtractor.instance());
  }

  @Test
  void createTokenExtractsIdTokenWhenPresent() throws Exception {
    JsonNode response = MAPPER.readTree("{\"id_token\":\"jwt-value\"}");
    var token =
        OpenIdJsonTokenExtractor.instance()
            .createToken("at", "Bearer", 3600, "rt", "openid", response, "{}");
    assertNotNull(token);
    assertEquals("jwt-value", token.getOpenIdToken());
    assertEquals("at", token.getAccessToken());
  }

  @Test
  void createTokenReturnsNullIdTokenWhenAbsent() throws Exception {
    JsonNode response = MAPPER.readTree("{\"foo\":\"bar\"}");
    var token =
        OpenIdJsonTokenExtractor.instance()
            .createToken("at", "Bearer", 3600, "rt", "openid", response, "{}");
    assertNull(token.getOpenIdToken());
  }
}
