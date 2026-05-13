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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AuthProviderTest {

  @Test
  void recordExposesAllComponents() {
    var p =
        new AuthProvider(
            "name", "https://auth", "https://token", "https://revoke", true); // NON-NLS
    assertEquals("name", p.name());
    assertEquals("https://auth", p.authorizationUri());
    assertEquals("https://token", p.tokenUri());
    assertEquals("https://revoke", p.revokeTokenUri());
    assertTrue(p.openId());
  }

  @Test
  void supportsTokenRevocationReturnsTrueWhenUriPresent() {
    var p = new AuthProvider("n", "a", "t", "https://revoke", false);
    assertTrue(p.supportsTokenRevocation());
  }

  @Test
  void supportsTokenRevocationReturnsFalseWhenUriBlankOrNull() {
    assertFalse(new AuthProvider("n", "a", "t", null, false).supportsTokenRevocation());
    assertFalse(new AuthProvider("n", "a", "t", "   ", false).supportsTokenRevocation());
    assertFalse(new AuthProvider("n", "a", "t", "", false).supportsTokenRevocation());
  }
}
