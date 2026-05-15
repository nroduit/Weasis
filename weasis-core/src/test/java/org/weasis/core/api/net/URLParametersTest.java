/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class URLParametersTest {

  private static URL url(String spec) throws MalformedURLException {
    return URI.create(spec).toURL();
  }

  @Test
  void defaultInstanceHasSensibleValues() {
    URLParameters p = URLParameters.DEFAULT;
    assertTrue(p.headers().isEmpty());
    assertEquals(0L, p.ifModifiedSince());
    assertFalse(p.httpPost());
    assertTrue(p.useCaches());
    assertFalse(p.allowUserInteraction());
  }

  @Test
  void canonicalConstructorRejectsNegativeTimeouts() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new URLParameters(Map.of(), 0L, -1, 100, false, true, false));
    assertThrows(
        IllegalArgumentException.class,
        () -> new URLParameters(Map.of(), 0L, 100, -1, false, true, false));
  }

  @Test
  void canonicalConstructorRejectsNegativeIfModifiedSince() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new URLParameters(Map.of(), -1L, 100, 100, false, true, false));
  }

  @Test
  void headersAreImmutableCopy() {
    var mutable = new java.util.HashMap<String, String>();
    mutable.put("A", "1");
    URLParameters p = new URLParameters(mutable);
    mutable.put("B", "2");
    assertEquals(1, p.headers().size());
    assertThrows(UnsupportedOperationException.class, () -> p.headers().put("C", "3"));
  }

  @Test
  void nullHeadersBecomeEmptyMap() {
    URLParameters p = new URLParameters((Map<String, String>) null);
    assertNotNull(p.headers());
    assertTrue(p.headers().isEmpty());
  }

  @Test
  void postConstructorSetsHttpPostFlag() {
    URLParameters p = new URLParameters(Map.of(), true);
    assertTrue(p.httpPost());
  }

  @Test
  void timeoutConstructorPreservesValues() {
    URLParameters p = new URLParameters(Map.of(), 1234, 5678);
    assertEquals(1234, p.connectTimeout());
    assertEquals(5678, p.readTimeout());
  }

  @Test
  void builderProducesEquivalentInstance() {
    URLParameters built =
        URLParameters.builder()
            .headers(Map.of("X", "Y"))
            .ifModifiedSince(10L)
            .connectTimeout(1000)
            .readTimeout(2000)
            .httpPost(true)
            .useCaches(false)
            .allowUserInteraction(true)
            .build();
    assertEquals(Map.of("X", "Y"), built.headers());
    assertEquals(10L, built.ifModifiedSince());
    assertEquals(1000, built.connectTimeout());
    assertEquals(2000, built.readTimeout());
    assertTrue(built.httpPost());
    assertFalse(built.useCaches());
    assertTrue(built.allowUserInteraction());
  }

  @Test
  void toBuilderRoundTripsAllFields() {
    URLParameters source =
        URLParameters.builder()
            .headers(Map.of("k", "v"))
            .ifModifiedSince(42L)
            .connectTimeout(10)
            .readTimeout(20)
            .httpPost(true)
            .useCaches(false)
            .allowUserInteraction(true)
            .build();
    URLParameters copy = source.toBuilder().build();
    assertEquals(source, copy);
    assertNotSame(source, copy);
  }

  @Test
  void splitParameterReturnsEmptyForNoQuery() throws Exception {
    assertEquals(Map.of(), URLParameters.splitParameter(url("http://h/path")));
  }

  @Test
  void splitParameterDecodesAndKeepsLastValue() throws Exception {
    Map<String, String> map =
        URLParameters.splitParameter(url("http://h/?a=1&b=hello%20world&a=2&flag"));
    assertEquals("2", map.get("a"));
    assertEquals("hello world", map.get("b"));
    assertEquals("", map.get("flag"));
  }

  @Test
  void splitMultipleValuesParameterAggregatesValues() throws Exception {
    Map<String, List<String>> map =
        URLParameters.splitMultipleValuesParameter(url("http://h/?a=1&a=2&b=x"));
    assertEquals(List.of("1", "2"), map.get("a"));
    assertEquals(List.of("x"), map.get("b"));
  }

  @Test
  void splitMultipleValuesParameterRejectsNullUrl() {
    assertThrows(
        NullPointerException.class, () -> URLParameters.splitMultipleValuesParameter(null));
  }

  @Test
  void splitParameterRejectsNullUrl() {
    assertThrows(NullPointerException.class, () -> URLParameters.splitParameter(null));
  }
}
