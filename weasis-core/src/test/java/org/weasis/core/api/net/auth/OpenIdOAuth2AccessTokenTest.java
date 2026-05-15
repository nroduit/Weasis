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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class OpenIdOAuth2AccessTokenTest {

  @Test
  void minimalConstructorOnlyKeepsAccessAndIdTokens() {
    var t = new OpenIdOAuth2AccessToken("at", "id-tok", "{}");
    assertEquals("at", t.getAccessToken());
    assertEquals("id-tok", t.getOpenIdToken());
    assertEquals("{}", t.getRawResponse());
    assertNull(t.getTokenType());
    assertNull(t.getExpiresIn());
    assertNull(t.getRefreshToken());
    assertNull(t.getScope());
  }

  @Test
  void fullConstructorRetainsAllFields() {
    var t = new OpenIdOAuth2AccessToken("at", "Bearer", 3600, "rt", "openid", "id-tok", "{}");
    assertEquals("Bearer", t.getTokenType());
    assertEquals(3600, t.getExpiresIn());
    assertEquals("rt", t.getRefreshToken());
    assertEquals("openid", t.getScope());
    assertEquals("id-tok", t.getOpenIdToken());
  }

  @Test
  void equalsAndHashCodeUseAllRelevantFields() {
    var a = new OpenIdOAuth2AccessToken("at", "Bearer", 60, "rt", "s", "id1", "raw");
    var b = new OpenIdOAuth2AccessToken("at", "Bearer", 60, "rt", "s", "id1", "raw");
    var differentId = new OpenIdOAuth2AccessToken("at", "Bearer", 60, "rt", "s", "id2", "raw");

    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertNotEquals(a, differentId);
    assertNotEquals(a, null);
    assertNotEquals(a, "string");
  }
}
