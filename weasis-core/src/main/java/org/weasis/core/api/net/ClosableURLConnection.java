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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.util.Objects;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

/** URLConnection wrapper providing HttpStream compatibility with proper resource cleanup. */
public record ClosableURLConnection(URLConnection urlConnection) implements HttpStream {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClosableURLConnection.class);

  public ClosableURLConnection {
    Objects.requireNonNull(urlConnection, "urlConnection");
  }

  @Override
  public void close() {
    if (urlConnection instanceof HttpURLConnection httpConnection) {
      httpConnection.disconnect();
    }
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return urlConnection.getInputStream();
  }

  @Override
  public int getResponseCode() {
    return urlConnection instanceof HttpURLConnection http
        ? safeGet(http::getResponseCode, HttpURLConnection.HTTP_OK, "response code")
        : HttpURLConnection.HTTP_OK;
  }

  @Override
  public String getResponseMessage() {
    return urlConnection instanceof HttpURLConnection http
        ? safeGet(http::getResponseMessage, StringUtil.EMPTY_STRING, "response message")
        : StringUtil.EMPTY_STRING;
  }

  @Override
  public String getHeaderField(String key) {
    return urlConnection.getHeaderField(key);
  }

  public OutputStream getOutputStream() throws IOException {
    return urlConnection.getOutputStream();
  }

  private static <T> T safeGet(Callable<T> supplier, T fallback, String what) {
    try {
      return supplier.call();
    } catch (Exception e) {
      LOGGER.warn("Failed to retrieve HTTP {}", what, e);
      return fallback;
    }
  }
}
