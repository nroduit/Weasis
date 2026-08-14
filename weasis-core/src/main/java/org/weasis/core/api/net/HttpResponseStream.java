/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.net;

import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.weasis.core.util.StreamUtil;

/** HTTP response wrapper providing {@link HttpStream} access to a {@link HttpResponse} body. */
public final class HttpResponseStream implements HttpStream {

  private final int code;
  private final String message;
  private final Map<String, String> headers;
  private final InputStream stream;

  public HttpResponseStream(HttpResponse<InputStream> httpResponse) {
    this(httpResponse, 0);
  }

  /** Guards the body against stalled reads; {@code stallTimeoutMillis <= 0} disables the guard. */
  public HttpResponseStream(HttpResponse<InputStream> httpResponse, int stallTimeoutMillis) {
    Objects.requireNonNull(httpResponse, "httpResponse");
    this.code = httpResponse.statusCode();
    this.message = httpResponse.version().toString();
    this.headers = parseHeaders(httpResponse);
    this.stream = StallGuardInputStream.wrap(httpResponse.body(), stallTimeoutMillis);
  }

  /** Flattens multi-valued headers into a case-insensitive map of comma-joined values. */
  public static Map<String, String> parseHeaders(HttpResponse<?> response) {
    return response.headers().map().entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                e -> String.join(", ", e.getValue()),
                (existing, replacement) -> existing,
                () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)));
  }

  @Override
  public void close() {
    StreamUtil.safeClose(stream);
  }

  @Override
  public InputStream getInputStream() {
    return stream;
  }

  @Override
  public int getResponseCode() {
    return code;
  }

  @Override
  public String getResponseMessage() {
    return message;
  }

  @Override
  public String getHeaderField(String key) {
    return headers.get(key);
  }
}
