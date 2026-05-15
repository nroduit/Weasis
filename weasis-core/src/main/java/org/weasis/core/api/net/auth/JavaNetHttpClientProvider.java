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

import com.github.scribejava.core.httpclient.HttpClient;
import com.github.scribejava.core.httpclient.HttpClientConfig;
import com.github.scribejava.core.httpclient.HttpClientProvider;
import java.util.Objects;

/** Factory for creating JavaNet HTTP clients with ScribeJava OAuth integration. */
public class JavaNetHttpClientProvider implements HttpClientProvider {

  @Override
  public HttpClient createClient(HttpClientConfig httpClientConfig) {
    Objects.requireNonNull(httpClientConfig, "HTTP client configuration cannot be null");
    if (httpClientConfig instanceof JavaNetHttpClientConfig config) {
      return new JavaNetHttpClient(config);
    }
    throw new IllegalArgumentException(
        "Unsupported configuration type: " + httpClientConfig.getClass().getName());
  }
}
