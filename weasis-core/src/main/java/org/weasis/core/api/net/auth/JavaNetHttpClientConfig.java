/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.net.auth;

import java.net.ProxySelector;
import org.weasis.core.api.net.NetworkUtil;

/** Configuration for JavaNet HTTP client with timeout and proxy settings. */
public class JavaNetHttpClientConfig {

  private final int connectTimeoutMillis;
  private final int inactivityTimeoutMillis;
  private final int responseTimeoutMillis;
  private final ProxySelector proxySelector;

  public JavaNetHttpClientConfig() {
    this(
        NetworkUtil.getUrlConnectTimeoutMillis(),
        NetworkUtil.getUrlInactivityTimeoutMillis(),
        ProxySelector.getDefault());
  }

  public JavaNetHttpClientConfig(
      int connectTimeoutMillis, int inactivityTimeoutMillis, ProxySelector proxySelector) {
    this(
        connectTimeoutMillis,
        inactivityTimeoutMillis,
        NetworkUtil.getUrlResponseTimeoutMillis(),
        proxySelector);
  }

  public JavaNetHttpClientConfig(
      int connectTimeoutMillis,
      int inactivityTimeoutMillis,
      int responseTimeoutMillis,
      ProxySelector proxySelector) {
    this.connectTimeoutMillis = connectTimeoutMillis;
    this.inactivityTimeoutMillis = inactivityTimeoutMillis;
    this.responseTimeoutMillis = responseTimeoutMillis;
    this.proxySelector = proxySelector;
  }

  public int getConnectTimeoutMillis() {
    return connectTimeoutMillis;
  }

  /** Budget for time without progress, never for the total transfer. */
  public int getInactivityTimeoutMillis() {
    return inactivityTimeoutMillis;
  }

  /** Budget for the wait between the request being sent and the response headers arriving. */
  public int getResponseTimeoutMillis() {
    return responseTimeoutMillis;
  }

  public ProxySelector getProxy() {
    return proxySelector;
  }
}
