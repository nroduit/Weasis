/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.net.auth;

import org.weasis.core.util.StringUtil;

/**
 * Creates an AuthRegistration with the specified parameters.
 *
 * @param clientId OAuth2 client identifier
 * @param clientSecret OAuth2 client secret
 * @param scope OAuth2 scope permissions
 * @param audience OAuth2 audience claim
 * @param user associated username
 */
public record AuthRegistration(
    String clientId, String clientSecret, String scope, String audience, String user) {

  /** OAuth2 authorization code grant type constant. */
  public static final String CODE = "code"; // NON-NLS

  public AuthRegistration {
    clientId = normalizeString(clientId);
    clientSecret = normalizeString(clientSecret);
    scope = normalizeString(scope);
    audience = normalizeString(audience);
    user = normalizeString(user);
  }

  /**
   * @return empty AuthRegistration with all null values
   */
  public static AuthRegistration empty() {
    return new AuthRegistration(null, null, null, null, null);
  }

  public static AuthRegistration of(
      String clientId, String clientSecret, String scope, String audience) {
    return new AuthRegistration(clientId, clientSecret, scope, audience, null);
  }

  /**
   * @return OAuth2 authorization grant type (always {@value #CODE})
   */
  public String getAuthorizationGrantType() {
    return CODE;
  }

  /**
   * @return {@code true} if client ID is configured
   */
  public boolean hasClientId() {
    return StringUtil.hasText(clientId);
  }

  /**
   * @return {@code true} if client secret is configured
   */
  public boolean hasClientSecret() {
    return StringUtil.hasText(clientSecret);
  }

  /**
   * @return {@code true} if registration is properly configured for OAuth2
   */
  public boolean isComplete() {
    return hasClientId() && hasClientSecret();
  }

  private static String normalizeString(String value) {
    if (value == null) {
      return null;
    }
    var trimmed = value.strip();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
