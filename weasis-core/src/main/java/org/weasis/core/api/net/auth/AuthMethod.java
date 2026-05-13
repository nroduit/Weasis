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

import com.github.scribejava.core.model.OAuth2AccessToken;

/** OAuth2-based authentication method providing token management and configuration access. */
public interface AuthMethod {

  /**
   * @return unique authentication code
   */
  String getCode();

  /**
   * @return unique identifier for this authentication method
   */
  String getUid();

  /** Invalidates the current authentication token, forcing re-authentication on next access. */
  void resetToken();

  /**
   * Retrieves the current OAuth2 access token, refreshing if necessary.
   *
   * @return OAuth2 access token, or {@code null} if authentication fails
   */
  OAuth2AccessToken getToken();

  /**
   * @return authentication registration details
   */
  AuthRegistration getAuthRegistration();

  /**
   * @return {@code true} if this is a local authentication method
   */
  boolean isLocal();

  /**
   * @param local whether this authentication method is local
   */
  void setLocal(boolean local);

  /**
   * @return authentication provider configuration
   */
  AuthProvider getAuthProvider();

  /**
   * @return provider name for display purposes
   */
  default String getName() {
    return getAuthProvider().name();
  }

  /**
   * @return {@code true} if the authentication token is currently valid
   */
  default boolean hasValidToken() {
    var token = getToken();
    return token != null && !token.getAccessToken().isEmpty();
  }
}
