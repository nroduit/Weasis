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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

public class ClosableURLConnection implements HttpResponse {
  private static final Logger LOGGER = LoggerFactory.getLogger(ClosableURLConnection.class);

  private final URLConnection urlConnection;

  public ClosableURLConnection(URLConnection urlConnection) {
    this.urlConnection = Objects.requireNonNull(urlConnection);
  }

  @Override
  public void close() {
    if (urlConnection instanceof HttpURLConnection httpURLConnection) {
      httpURLConnection.disconnect();
    }
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return urlConnection.getInputStream();
  }

  @Override
  public int getResponseCode() {
    if (urlConnection instanceof HttpURLConnection httpURLConnection) {
      try {
        return httpURLConnection.getResponseCode();
      } catch (IOException e) {
        LOGGER.error("Get code", e);
      }
    }
    return HttpURLConnection.HTTP_OK;
  }

  @Override
  public String getResponseMessage() {
    if (urlConnection instanceof HttpURLConnection httpURLConnection) {
      try {
        return httpURLConnection.getResponseMessage();
      } catch (IOException e) {
        LOGGER.error("Get message", e);
      }
    }
    return StringUtil.EMPTY_STRING;
  }

  @Override
  public String getHeaderField(String key) {
    return urlConnection.getHeaderField(key);
  }

  public OutputStream getOutputStream() throws IOException {
    return urlConnection.getOutputStream();
  }

  public URLConnection getUrlConnection() {
    return urlConnection;
  }
}
