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

  private final int connectTimeout;
  private final int readTimeout;
  private final ProxySelector proxySelector;

  public JavaNetHttpClientConfig() {
    this(
        NetworkUtil.getUrlConnectionTimeout(),
        NetworkUtil.getUrlReadTimeout(),
        ProxySelector.getDefault());
  }

  public JavaNetHttpClientConfig(int connectTimeout, int readTimeout, ProxySelector proxySelector) {
    this.connectTimeout = connectTimeout;
    this.readTimeout = readTimeout;
    this.proxySelector = proxySelector;
  }

  public int getConnectTimeout() {
    return connectTimeout;
  }

  public int getReadTimeout() {
    return readTimeout;
  }

  public ProxySelector getProxy() {
    return proxySelector;
  }
}
