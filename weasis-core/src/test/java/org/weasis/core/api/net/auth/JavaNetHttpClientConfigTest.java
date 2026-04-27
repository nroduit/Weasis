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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.net.ProxySelector;
import org.junit.jupiter.api.Test;

class JavaNetHttpClientConfigTest {

  @Test
  void explicitConstructorRetainsValues() {
    var proxy = ProxySelector.getDefault();
    var cfg = new JavaNetHttpClientConfig(1234, 5678, proxy);
    assertEquals(1234, cfg.getConnectTimeout());
    assertEquals(5678, cfg.getReadTimeout());
    assertSame(proxy, cfg.getProxy());
  }

  @Test
  void defaultConstructorPicksNonNullValues() {
    var cfg = new JavaNetHttpClientConfig();
    assertNotNull(cfg.getProxy());
  }

  @Test
  void createDefaultConfigReturnsFreshInstance() {
    var cfg = new JavaNetHttpClientConfig(1, 2, ProxySelector.getDefault());
    var other = cfg.createDefaultConfig();
    assertInstanceOf(JavaNetHttpClientConfig.class, other);
  }
}
