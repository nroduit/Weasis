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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import org.junit.jupiter.api.Test;

class ClosableURLConnectionTest {

  private static URLConnection minimalConnection() throws IOException {
    return new URLConnection(URI.create("http://localhost/").toURL()) {
      @Override
      public void connect() {
        // no-op
      }

      @Override
      public InputStream getInputStream() {
        return new ByteArrayInputStream("hello".getBytes());
      }

      @Override
      public String getHeaderField(String name) {
        return "header-" + name;
      }
    };
  }

  @Test
  void constructorRejectsNull() {
    assertThrows(NullPointerException.class, () -> new ClosableURLConnection(null));
  }

  @Test
  void nonHttpConnectionReportsHttpOk() throws IOException {
    var conn = new ClosableURLConnection(minimalConnection());
    assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());
    assertEquals("", conn.getResponseMessage());
    assertEquals("header-X", conn.getHeaderField("X"));
    assertNotNull(conn.getInputStream());
    conn.close(); // should be a no-op for non-http
  }

  @Test
  void httpConnectionDelegatesToUnderlying() throws IOException {
    var http =
        new HttpURLConnection(URI.create("http://localhost/").toURL()) {
          boolean disconnected;

          @Override
          public void disconnect() {
            disconnected = true;
          }

          @Override
          public boolean usingProxy() {
            return false;
          }

          @Override
          public void connect() {
            // no-op
          }

          @Override
          public int getResponseCode() {
            return 201;
          }

          @Override
          public String getResponseMessage() {
            return "Created";
          }

          @Override
          public InputStream getInputStream() {
            return new ByteArrayInputStream("data".getBytes());
          }

          @Override
          public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
          }
        };

    var conn = new ClosableURLConnection(http);
    assertSame(http, conn.urlConnection());
    assertEquals(201, conn.getResponseCode());
    assertEquals("Created", conn.getResponseMessage());
    assertNotNull(conn.getInputStream());
    assertNotNull(conn.getOutputStream());
    conn.close();
    assertTrue(http.disconnected);
  }

  @Test
  void getResponseCodeFallsBackOnIoException() throws IOException {
    var http =
        new HttpURLConnection(URI.create("http://localhost/").toURL()) {
          @Override
          public void disconnect() {}

          @Override
          public boolean usingProxy() {
            return false;
          }

          @Override
          public void connect() {}

          @Override
          public int getResponseCode() throws IOException {
            throw new IOException("boom");
          }

          @Override
          public String getResponseMessage() throws IOException {
            throw new IOException("boom");
          }
        };
    var conn = new ClosableURLConnection(http);
    assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());
    assertEquals("", conn.getResponseMessage());
  }

  @Test
  void getInputStreamPropagatesIOException() throws IOException {
    var failing =
        new URLConnection(URI.create("http://localhost/").toURL()) {
          @Override
          public void connect() {}

          @Override
          public InputStream getInputStream() throws IOException {
            throw new IOException("nope");
          }
        };
    var conn = new ClosableURLConnection(failing);
    assertThrows(IOException.class, conn::getInputStream);
  }

  @Test
  void recordEqualityIsValueBased() throws IOException {
    URL u = URI.create("http://localhost/").toURL();
    URLConnection c = u.openConnection();
    assertEquals(new ClosableURLConnection(c), new ClosableURLConnection(c));
  }
}
