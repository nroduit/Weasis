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

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP request definition executed by {@link org.weasis.core.api.net.auth.JavaNetHttpClient},
 * optionally signed with a bearer token by {@link HttpUtils#executeAuthenticatedRequest}.
 */
public final class WebRequest {

  /** HTTP method. */
  public enum Method {
    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
    HEAD;

    public boolean permitsBody() {
      return this == POST || this == PUT || this == PATCH || this == DELETE;
    }
  }

  private final Method method;
  private final String url;
  private final Map<String, String> headers = new LinkedHashMap<>();
  private byte[] body;
  private MultipartBody multipartBody;

  public WebRequest(Method method, String url) {
    this.method = Objects.requireNonNull(method, "method");
    this.url = Objects.requireNonNull(url, "url");
  }

  public Method getMethod() {
    return method;
  }

  public String getUrl() {
    return url;
  }

  public void addHeader(String name, String value) {
    headers.put(name, value);
  }

  public Map<String, String> getHeaders() {
    return Map.copyOf(headers);
  }

  /** Sets a text body encoded as UTF-8; exclusive with {@link #setMultipartBody}. */
  public void setBody(String content) {
    this.body = content == null ? null : content.getBytes(StandardCharsets.UTF_8);
  }

  public byte[] getBody() {
    return body;
  }

  /** Sets a multipart body; exclusive with {@link #setBody}. */
  public void setMultipartBody(MultipartBody multipartBody) {
    this.multipartBody = multipartBody;
  }

  public MultipartBody getMultipartBody() {
    return multipartBody;
  }
}
