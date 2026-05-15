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
 * OAuth2/OpenID Connect provider configuration.
 *
 * @param name provider name
 * @param authorizationUri authorization endpoint URI
 * @param tokenUri token endpoint URI
 * @param revokeTokenUri token revocation endpoint URI (optional)
 * @param openId whether OpenID Connect is supported
 */
public record AuthProvider(
    String name, String authorizationUri, String tokenUri, String revokeTokenUri, boolean openId) {
  /**
   * @return {@code true} if token revocation is supported
   */
  public boolean supportsTokenRevocation() {
    return StringUtil.hasText(revokeTokenUri);
  }
}
