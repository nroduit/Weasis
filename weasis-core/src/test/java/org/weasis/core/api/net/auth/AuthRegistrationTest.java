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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AuthRegistrationTest {

  @Test
  void emptyReturnsAllNullValues() {
    var r = AuthRegistration.empty();
    assertNull(r.clientId());
    assertNull(r.clientSecret());
    assertNull(r.scope());
    assertNull(r.audience());
    assertNull(r.user());
    assertFalse(r.hasClientId());
    assertFalse(r.hasClientSecret());
    assertFalse(r.isComplete());
  }

  @Test
  void ofSetsUserToNull() {
    var r = AuthRegistration.of("id", "secret", "scope", "aud");
    assertEquals("id", r.clientId());
    assertEquals("secret", r.clientSecret());
    assertEquals("scope", r.scope());
    assertEquals("aud", r.audience());
    assertNull(r.user());
    assertTrue(r.isComplete());
  }

  @Test
  void compactConstructorNormalizesBlankToNullAndStripsWhitespace() {
    var r = new AuthRegistration("  id ", "", "  ", "\t\n", "  bob ");
    assertEquals("id", r.clientId());
    assertNull(r.clientSecret());
    assertNull(r.scope());
    assertNull(r.audience());
    assertEquals("bob", r.user());
  }

  @Test
  void hasClientHelpers() {
    assertTrue(AuthRegistration.of("id", null, null, null).hasClientId());
    assertFalse(AuthRegistration.of(null, "secret", null, null).hasClientId());
    assertTrue(AuthRegistration.of(null, "secret", null, null).hasClientSecret());
    assertFalse(AuthRegistration.of("id", null, null, null).hasClientSecret());
  }

  @Test
  void isCompleteRequiresBothClientIdAndSecret() {
    assertFalse(AuthRegistration.of("id", null, null, null).isComplete());
    assertFalse(AuthRegistration.of(null, "s", null, null).isComplete());
    assertTrue(AuthRegistration.of("id", "s", null, null).isComplete());
  }

  @Test
  void getAuthorizationGrantTypeReturnsCodeConstant() {
    assertEquals(AuthRegistration.CODE, AuthRegistration.empty().getAuthorizationGrantType());
    assertEquals("code", AuthRegistration.CODE);
  }
}
