/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.function.LongConsumer;

/**
 * Bounded network access of stage 1: a short connect and read timeout, one retry for the small
 * configuration fetch, a longer read for the set download. Every failure is an {@link IOException}
 * the caller turns into the embedded path.
 */
class Http {

  static final int CONNECT_MS = 5_000;
  static final int READ_MS = 10_000;
  static final int DOWNLOAD_READ_MS = 30_000;

  private final Map<String, String> headers;

  Http(Map<String, String> headers) {
    this.headers = Map.copyOf(headers);
  }

  /** Fetches a small document, retrying once on an I/O failure. */
  byte[] fetch(URI uri) throws IOException {
    try {
      return fetchOnce(uri);
    } catch (IOException first) {
      try {
        return fetchOnce(uri);
      } catch (IOException second) {
        second.addSuppressed(first);
        throw second;
      }
    }
  }

  private byte[] fetchOnce(URI uri) throws IOException {
    URLConnection connection = open(uri, READ_MS);
    try (InputStream in = connection.getInputStream()) {
      return in.readAllBytes();
    }
  }

  /** Streams a file to {@code target} (replaced), reporting the bytes received so far. */
  void download(URI uri, Path target, LongConsumer progress) throws IOException {
    URLConnection connection = open(uri, DOWNLOAD_READ_MS);
    try (InputStream in = connection.getInputStream();
        OutputStream out =
            Files.newOutputStream(
                target,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                java.nio.file.StandardOpenOption.WRITE)) {
      byte[] buffer = new byte[64 * 1024];
      long total = 0;
      int n;
      while ((n = in.read(buffer)) > 0) {
        out.write(buffer, 0, n);
        total += n;
        progress.accept(total);
      }
    }
  }

  private URLConnection open(URI uri, int readTimeout) throws IOException {
    if ("file".equalsIgnoreCase(uri.getScheme())) { // NON-NLS
      return uri.toURL().openConnection();
    }
    URLConnection connection = uri.toURL().openConnection();
    connection.setUseCaches(false);
    connection.setConnectTimeout(CONNECT_MS);
    connection.setReadTimeout(readTimeout);
    headers.forEach(connection::setRequestProperty);
    if (connection instanceof HttpURLConnection http) {
      http.setInstanceFollowRedirects(true);
      int status = http.getResponseCode();
      if (status != HttpURLConnection.HTTP_OK) {
        throw new IOException("HTTP " + status + " for " + uri); // NON-NLS
      }
    }
    return connection;
  }

  /** Copies a local file, the download of the {@code file:} codebase. */
  static void copy(Path source, Path target) throws IOException {
    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
  }
}
