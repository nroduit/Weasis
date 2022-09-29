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

import com.fasterxml.jackson.databind.JsonNode;
import com.github.scribejava.core.extractors.OAuth2AccessTokenJsonExtractor;

/** additionally parses OpenID id_token */
public class OpenIdJsonTokenExtractor extends OAuth2AccessTokenJsonExtractor {

  protected OpenIdJsonTokenExtractor() {}

  private static class InstanceHolder {

    private static final OpenIdJsonTokenExtractor INSTANCE = new OpenIdJsonTokenExtractor();
  }

  public static OpenIdJsonTokenExtractor instance() {
    return InstanceHolder.INSTANCE;
  }

  @Override
  protected OpenIdOAuth2AccessToken createToken(
      String accessToken,
      String tokenType,
      Integer expiresIn,
      String refreshToken,
      String scope,
      JsonNode response,
      String rawResponse) {
    final JsonNode idToken = response.get("id_token"); // NON-NLS
    return new OpenIdOAuth2AccessToken(
        accessToken,
        tokenType,
        expiresIn,
        refreshToken,
        scope,
        idToken == null ? null : idToken.asText(),
        rawResponse);
  }
}
