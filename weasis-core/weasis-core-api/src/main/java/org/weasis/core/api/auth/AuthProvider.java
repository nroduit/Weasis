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

public class AuthProvider {

  public static final String DEFAULT_NAME = "name"; // NON-NLS
  private String name;
  private String authorizationUri;
  private String tokenUri;
  private String revokeTokenUri;
  private boolean openId;

  public AuthProvider(
      String name,
      String authorizationUri,
      String tokenUri,
      String revokeTokenUri,
      boolean openId) {
    this.name = name;
    this.authorizationUri = authorizationUri;
    this.tokenUri = tokenUri;
    this.revokeTokenUri = revokeTokenUri;
    this.openId = openId;
  }

  public boolean getOpenId() {
    return openId;
  }

  public void setOpenId(boolean openId) {
    this.openId = openId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getAuthorizationUri() {
    return authorizationUri;
  }

  public void setAuthorizationUri(String authorizationUri) {
    this.authorizationUri = authorizationUri;
  }

  public String getTokenUri() {
    return tokenUri;
  }

  public void setTokenUri(String tokenUri) {
    this.tokenUri = tokenUri;
  }

  public String getRevokeTokenUri() {
    return revokeTokenUri;
  }

  public void setRevokeTokenUri(String revokeTokenUri) {
    this.revokeTokenUri = revokeTokenUri;
  }

  public String getUserNameAttribute() {
    return DEFAULT_NAME;
  }
}
