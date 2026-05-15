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

import com.github.scribejava.core.model.Response;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.Objects;
import org.weasis.core.api.net.auth.JavaNetHttpClient;
import org.weasis.core.util.StreamUtil;

/** HTTP response wrapper providing HttpStream compatibility for OAuth2 and Java 11+ responses. */
public record HttpResponseStream(Response response) implements HttpStream {

  public HttpResponseStream {
    Objects.requireNonNull(response, "response");
  }

  public HttpResponseStream(HttpResponse<InputStream> httpResponse) {
    this(
        new Response(
            httpResponse.statusCode(),
            httpResponse.version().toString(),
            JavaNetHttpClient.parseHeaders(httpResponse),
            httpResponse.body()));
  }

  @Override
  public void close() {
    StreamUtil.safeClose(response);
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return response.getStream();
  }

  @Override
  public int getResponseCode() {
    return response.getCode();
  }

  @Override
  public String getResponseMessage() {
    return response.getMessage();
  }

  @Override
  public String getHeaderField(String key) {
    return response.getHeader(key);
  }
}
