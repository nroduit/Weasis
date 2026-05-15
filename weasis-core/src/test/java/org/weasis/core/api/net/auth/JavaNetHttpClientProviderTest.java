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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.scribejava.core.httpclient.HttpClientConfig;
import java.net.ProxySelector;
import org.junit.jupiter.api.Test;

class JavaNetHttpClientProviderTest {

  @Test
  void createsClientFromKnownConfig() {
    var provider = new JavaNetHttpClientProvider();
    var client =
        provider.createClient(new JavaNetHttpClientConfig(100, 100, ProxySelector.getDefault()));
    assertInstanceOf(JavaNetHttpClient.class, client);
  }

  @Test
  void rejectsNullConfig() {
    var provider = new JavaNetHttpClientProvider();
    assertThrows(NullPointerException.class, () -> provider.createClient(null));
  }

  @Test
  void rejectsUnsupportedConfigType() {
    var provider = new JavaNetHttpClientProvider();
    HttpClientConfig foreign =
        new HttpClientConfig() {
          @Override
          public HttpClientConfig createDefaultConfig() {
            return this;
          }
        };
    assertThrows(IllegalArgumentException.class, () -> provider.createClient(foreign));
  }
}
