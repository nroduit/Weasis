/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.auth;

import com.github.scribejava.core.model.OAuth2AccessToken;
import java.util.Objects;

public class OpenIdOAuth2AccessToken extends OAuth2AccessToken {

  /**
   * Id_token is part of OpenID Connect specification. It can hold user information that you can
   * directly extract without additional request to provider.
   *
   * <p>See http://openid.net/specs/openid-connect-core-1_0.html#id_token-tokenExample and
   * https://bitbucket.org/nimbusds/nimbus-jose-jwt/wiki/Home
   *
   * <p>Here will be encoded and signed id token in JWT format or null, if not defined.
   */
  private final String openIdToken;

  public OpenIdOAuth2AccessToken(String accessToken, String openIdToken, String rawResponse) {
    this(accessToken, null, null, null, null, openIdToken, rawResponse);
  }

  public OpenIdOAuth2AccessToken(
      String accessToken,
      String tokenType,
      Integer expiresIn,
      String refreshToken,
      String scope,
      String openIdToken,
      String rawResponse) {
    super(accessToken, tokenType, expiresIn, refreshToken, scope, rawResponse);
    this.openIdToken = openIdToken;
  }

  public String getOpenIdToken() {
    return openIdToken;
  }

  @Override
  public int hashCode() {
    int hash = super.hashCode();
    hash = 37 * hash + Objects.hashCode(openIdToken);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    if (!super.equals(obj)) {
      return false;
    }

    return Objects.equals(openIdToken, ((OpenIdOAuth2AccessToken) obj).getOpenIdToken());
  }
}
