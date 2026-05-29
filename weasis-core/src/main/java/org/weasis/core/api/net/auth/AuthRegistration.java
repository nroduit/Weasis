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

import java.util.Arrays;
import java.util.stream.Collectors;
import org.weasis.core.util.StringUtil;

/**
 * Creates an AuthRegistration with the specified parameters.
 *
 * @param clientId OAuth2 client identifier
 * @param clientSecret OAuth2 client secret
 * @param scope OAuth2 scope permissions
 * @param audience OAuth2 audience claim
 * @param user associated username
 * @param grantType OAuth2 grant type ({@link #CODE} or {@link #CLIENT_CREDENTIALS}); {@code null}
 *     defaults to {@link #CODE} for backward compatibility
 */
public record AuthRegistration(
    String clientId,
    String clientSecret,
    String scope,
    String audience,
    String user,
    String grantType) {

  /** OAuth2 authorization code grant type. */
  public static final String CODE = "code"; // NON-NLS

  /** OAuth2 client credentials grant type (RFC 6749 §4.4). */
  public static final String CLIENT_CREDENTIALS = "client_credentials"; // NON-NLS

  public AuthRegistration {
    clientId = normalizeString(clientId);
    clientSecret = normalizeString(clientSecret);
    scope = normalizeScope(scope);
    audience = normalizeString(audience);
    user = normalizeString(user);
    grantType = StringUtil.hasText(grantType) ? grantType.strip() : CODE;
  }

  /**
   * @return empty AuthRegistration (defaults to {@link #CODE} grant type)
   */
  public static AuthRegistration empty() {
    return new AuthRegistration(null, null, null, null, null, CODE);
  }

  public static AuthRegistration of(
      String clientId, String clientSecret, String scope, String audience) {
    return new AuthRegistration(clientId, clientSecret, scope, audience, null, CODE);
  }

  public static AuthRegistration of(
      String clientId, String clientSecret, String scope, String audience, String grantType) {
    return new AuthRegistration(clientId, clientSecret, scope, audience, null, grantType);
  }

  /**
   * @return OAuth2 authorization grant type (never {@code null})
   */
  public String getAuthorizationGrantType() {
    return grantType;
  }

  public boolean isClientCredentialsGrant() {
    return CLIENT_CREDENTIALS.equals(grantType);
  }

  public boolean hasClientId() {
    return StringUtil.hasText(clientId);
  }

  public boolean hasClientSecret() {
    return StringUtil.hasText(clientSecret);
  }

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

  /**
   * Accepts scopes separated by commas, semicolons, or whitespace and rejoins them as a single
   * space-separated list (the on-the-wire form per RFC 6749 §3.3). This lets users paste
   * Spring-style {@code "read, write"} without producing an {@code invalid_scope} error.
   */
  private static String normalizeScope(String value) {
    if (value == null) {
      return null;
    }
    var joined =
        Arrays.stream(value.split("[,;\\s]+"))
            .filter(s -> !s.isEmpty())
            .collect(Collectors.joining(" "));
    return joined.isEmpty() ? null : joined;
  }
}
