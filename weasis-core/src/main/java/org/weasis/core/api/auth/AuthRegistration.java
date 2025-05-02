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

public class AuthRegistration {

  public static final String CODE = "code"; // NON-NLS
  private String clientId;
  private String clientSecret;
  private String scope;
  private String audience;
  private String user;

  public AuthRegistration() {
    this(null, null, null, null);
  }

  public AuthRegistration(String clientId, String clientSecret, String scope, String audience) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.scope = scope;
    this.audience = audience;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(String clientSecret) {
    this.clientSecret = clientSecret;
  }

  public String getScope() {
    return scope;
  }

  public void setScope(String scope) {
    this.scope = scope;
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  public String getAudience() {
    return audience;
  }

  public void setAudience(String audience) {
    this.audience = audience;
  }

  public String getAuthorizationGrantType() {
    return CODE;
  }
}
