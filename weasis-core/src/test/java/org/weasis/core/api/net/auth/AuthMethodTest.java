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

import com.github.scribejava.core.model.OAuth2AccessToken;
import org.junit.jupiter.api.Test;

class AuthMethodTest {

  /** Minimal {@link AuthMethod} that only fills the abstract API to exercise default methods. */
  private static final class StubAuthMethod implements AuthMethod {
    private final OAuth2AccessToken token;
    private boolean local;

    StubAuthMethod(OAuth2AccessToken token) {
      this.token = token;
    }

    @Override
    public String getCode() {
      return "code";
    }

    @Override
    public String getUid() {
      return "uid";
    }

    @Override
    public void resetToken() {
      // no-op
    }

    @Override
    public OAuth2AccessToken getToken() {
      return token;
    }

    @Override
    public AuthRegistration getAuthRegistration() {
      return AuthRegistration.empty();
    }

    @Override
    public boolean isLocal() {
      return local;
    }

    @Override
    public void setLocal(boolean local) {
      this.local = local;
    }

    @Override
    public AuthProvider getAuthProvider() {
      return new AuthProvider("Stub", null, null, null, false);
    }
  }

  @Test
  void defaultGetNameDelegatesToProvider() {
    assertEquals("Stub", new StubAuthMethod(null).getName());
  }

  @Test
  void hasValidTokenIsFalseWhenTokenIsNull() {
    assertFalse(new StubAuthMethod(null).hasValidToken());
  }

  @Test
  void hasValidTokenIsFalseForEmptyAccessToken() {
    var token = new OAuth2AccessToken("", "raw");
    assertFalse(new StubAuthMethod(token).hasValidToken());
  }

  @Test
  void hasValidTokenIsTrueForNonEmptyAccessToken() {
    var token = new OAuth2AccessToken("at", "raw");
    assertTrue(new StubAuthMethod(token).hasValidToken());
  }

  @Test
  void setLocalRoundTrip() {
    var method = new StubAuthMethod(null);
    method.setLocal(true);
    assertTrue(method.isLocal());
    assertNull(method.getToken());
  }
}
