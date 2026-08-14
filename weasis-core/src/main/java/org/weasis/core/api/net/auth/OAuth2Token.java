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

import java.util.Objects;

/**
 * OAuth2 token response (RFC 6749 §5.1), including the OpenID Connect {@code id_token} when the
 * provider returns one.
 *
 * @param accessToken access token (never {@code null})
 * @param tokenType token type, typically {@code Bearer} (optional)
 * @param expiresIn lifetime in seconds (optional)
 * @param refreshToken refresh token (optional)
 * @param scope granted scope (optional)
 * @param idToken OpenID Connect ID token (optional)
 */
public record OAuth2Token(
    String accessToken,
    String tokenType,
    Integer expiresIn,
    String refreshToken,
    String scope,
    String idToken) {

  public OAuth2Token {
    Objects.requireNonNull(accessToken, "accessToken");
  }
}
