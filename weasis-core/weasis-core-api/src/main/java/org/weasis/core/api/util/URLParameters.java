/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class URLParameters {

  private final Map<String, String> headers;
  private final long ifModifiedSince;
  private final int connectTimeout;
  private final int readTimeout;
  private final boolean httpPost;
  private final boolean useCaches;
  private final boolean allowUserInteraction;

  public URLParameters() {
    this(null, null, null, null, null, null, null);
  }

  public URLParameters(Map<String, String> headers) {
    this(headers, null, null, null, null, null, null);
  }

  public URLParameters(Map<String, String> headers, boolean httpPost) {
    this(headers, null, null, httpPost, null, null, null);
  }

  public URLParameters(Map<String, String> headers, int connectTimeout, int readTimeout) {
    this(headers, connectTimeout, readTimeout, null, null, null, null);
  }

  public URLParameters(
      Map<String, String> headers,
      Integer connectTimeout,
      Integer readTimeout,
      Boolean httpPost,
      Boolean useCaches,
      Long ifModifiedSince,
      Boolean allowUserInteraction) {
    this.headers = headers == null ? Collections.emptyMap() : Collections.unmodifiableMap(headers);
    this.ifModifiedSince = ifModifiedSince == null ? 0L : ifModifiedSince;
    this.connectTimeout =
        connectTimeout == null ? NetworkUtil.getUrlConnectionTimeout() : connectTimeout;
    this.readTimeout = readTimeout == null ? NetworkUtil.getUrlReadTimeout() : readTimeout;
    this.httpPost = httpPost == null ? Boolean.FALSE : httpPost;
    this.useCaches = useCaches == null ? Boolean.TRUE : useCaches;
    this.allowUserInteraction = allowUserInteraction == null ? Boolean.FALSE : allowUserInteraction;
  }

  public Map<String, String> getUnmodifiableHeaders() {
    return headers;
  }

  public long getIfModifiedSince() {
    return ifModifiedSince;
  }

  public int getConnectTimeout() {
    return connectTimeout;
  }

  public int getReadTimeout() {
    return readTimeout;
  }

  public boolean isHttpPost() {
    return httpPost;
  }

  public boolean isUseCaches() {
    return useCaches;
  }

  public boolean isAllowUserInteraction() {
    return allowUserInteraction;
  }

  public static Map<String, String> splitParameter(URL url) {
    Map<String, String> queryPairs = new LinkedHashMap<>();
    String query = url.getQuery();
    String[] pairs = query.split("&");
    for (String pair : pairs) {
      int idx = pair.indexOf("=");
      queryPairs.put(
          URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8),
          URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
    }
    return queryPairs;
  }

  public static Map<String, List<String>> splitMultipleValuesParameter(URL url) {
    final Map<String, List<String>> queryPairs = new LinkedHashMap<>();
    final String[] pairs = url.getQuery().split("&");
    for (String pair : pairs) {
      final int idx = pair.indexOf("=");
      final String key =
          idx > 0 ? URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8) : pair;
      if (!queryPairs.containsKey(key)) {
        queryPairs.put(key, new LinkedList<>());
      }
      final String value =
          idx > 0 && pair.length() > idx + 1
              ? URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8)
              : null;
      queryPairs.get(key).add(value);
    }
    return queryPairs;
  }
}
