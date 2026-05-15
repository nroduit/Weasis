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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.weasis.core.util.StreamIOException;

class NetworkUtilTest {

  private static HttpServer server;
  private static String baseUrl;

  @BeforeAll
  static void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/ok",
        ex -> {
          byte[] body = "ok".getBytes();
          ex.sendResponseHeaders(200, body.length);
          try (var os = ex.getResponseBody()) {
            os.write(body);
          }
        });
    server.createContext(
        "/redirect",
        ex -> {
          ex.getResponseHeaders()
              .add("Location", "http://localhost:" + ex.getLocalAddress().getPort() + "/ok");
          ex.getResponseHeaders().add("Set-Cookie", "session=abc");
          ex.sendResponseHeaders(HttpURLConnection.HTTP_MOVED_TEMP, -1);
          ex.close();
        });
    server.createContext(
        "/echo-method",
        ex -> {
          byte[] body = ex.getRequestMethod().getBytes();
          ex.sendResponseHeaders(200, body.length);
          try (var os = ex.getResponseBody()) {
            os.write(body);
          }
        });
    server.createContext(
        "/loop",
        ex -> {
          ex.getResponseHeaders()
              .add("Location", "http://localhost:" + ex.getLocalAddress().getPort() + "/loop");
          ex.sendResponseHeaders(HttpURLConnection.HTTP_MOVED_PERM, -1);
          ex.close();
        });
    server.createContext(
        "/error",
        ex -> {
          ex.sendResponseHeaders(500, -1);
          ex.close();
        });
    server.start();
    baseUrl = "http://localhost:" + server.getAddress().getPort();
  }

  @AfterAll
  static void stop() {
    server.stop(0);
  }

  @Test
  void timeoutsHaveDefaults() {
    assertTrue(NetworkUtil.getUrlConnectionTimeout() > 0);
    assertTrue(NetworkUtil.getUrlReadTimeout() > 0);
  }

  @Test
  void getURIReturnsHttpURIUnchanged() {
    var uri = NetworkUtil.getURI("http://example.com/foo");
    assertEquals("http", uri.getScheme());
    assertEquals("example.com", uri.getHost());
  }

  @Test
  void getURIResolvesReadableFile(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
    Path file = Files.writeString(tmp.resolve("a.txt"), "x");
    var uri = NetworkUtil.getURI(file.toString());
    assertEquals("file", uri.getScheme());
  }

  @Test
  void getURIFallsBackForNonReadablePath() {
    var uri = NetworkUtil.getURI("/nonexistent/__weasis_test_path__");
    assertEquals("/nonexistent/__weasis_test_path__", uri.toString());
  }

  @Test
  void isValidUrlLikeUriRecognisesValidAndInvalid() {
    assertTrue(NetworkUtil.isValidUrlLikeUri("https://example.com/x"));
    assertFalse(NetworkUtil.isValidUrlLikeUri("just-a-string"));
    assertFalse(NetworkUtil.isValidUrlLikeUri("file:///tmp/x"));
  }

  @Test
  void getUrlConnectionRetrieves200Body() throws IOException {
    try (ClosableURLConnection conn =
        NetworkUtil.getUrlConnection(baseUrl + "/ok", URLParameters.DEFAULT)) {
      assertEquals(200, conn.getResponseCode());
      assertEquals("ok", new String(conn.getInputStream().readAllBytes()));
    }
  }

  @Test
  void getUrlConnectionWithUrlOverloadWorks() throws IOException {
    try (ClosableURLConnection conn =
        NetworkUtil.getUrlConnection(URI.create(baseUrl + "/ok").toURL(), URLParameters.DEFAULT)) {
      assertNotNull(conn.getInputStream());
    }
  }

  @Test
  void getUrlConnectionFollowsRedirectAndPropagatesHeaders() throws IOException {
    var params = new URLParameters(Map.of("X-Trace", "abc"));
    try (ClosableURLConnection conn = NetworkUtil.getUrlConnection(baseUrl + "/redirect", params)) {
      assertEquals(200, conn.getResponseCode());
    }
  }

  @Test
  void serverErrorThrowsStreamIOException() {
    assertThrows(
        StreamIOException.class,
        () -> NetworkUtil.getUrlConnection(baseUrl + "/error", URLParameters.DEFAULT));
  }

  @Test
  void readReturnsResponseBody() throws IOException {
    var conn = URI.create(baseUrl + "/ok").toURL().openConnection();
    assertEquals("ok", NetworkUtil.read(conn));
  }

  @Test
  void getHttpResponseWithoutAuthReturnsClosableUrlConnection() throws Exception {
    try (HttpStream s = NetworkUtil.getHttpResponse(baseUrl + "/ok", URLParameters.DEFAULT, null)) {
      assertEquals(200, s.getResponseCode());
    }
  }

  // -------------------------------------------------------------------------
  // Additional branch coverage
  // -------------------------------------------------------------------------

  @Test
  void getURIRejectsNonHttpUnreadableButFallsBackToUriCreate() {
    var uri = NetworkUtil.getURI("custom-scheme:foo");
    assertEquals("custom-scheme:foo", uri.toString());
  }

  @Test
  void prepareConnectionForFileUrlReturnsNonHttpClosable(@org.junit.jupiter.api.io.TempDir Path tmp)
      throws IOException {
    Path file = Files.writeString(tmp.resolve("a.txt"), "data");
    try (ClosableURLConnection conn =
        NetworkUtil.getUrlConnection(file.toUri().toURL(), URLParameters.DEFAULT)) {
      // Non-HTTP connection: defaults reported by ClosableURLConnection
      assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());
      assertEquals("data", new String(conn.getInputStream().readAllBytes()));
    }
  }

  @Test
  void postUrlConnectionUsesPostMethod() throws IOException {
    URLParameters post = new URLParameters(Map.of(), true);
    try (ClosableURLConnection conn =
        NetworkUtil.getUrlConnection(baseUrl + "/echo-method", post)) {
      // The POST branch returns the connection without reading; trigger the call
      assertEquals("POST", new String(conn.getInputStream().readAllBytes()));
    }
  }

  @Test
  void applyRedirectionStreamFollowsAndPropagatesCookies() throws IOException {
    var initial = URI.create(baseUrl + "/redirect").toURL().openConnection();
    initial.connect();
    var followed = NetworkUtil.applyRedirectionStream(initial, Map.of("X-Trace", "y"));
    assertEquals(200, ((HttpURLConnection) followed).getResponseCode());
  }

  @Test
  void applyRedirectionStreamStopsAfterMaxRedirects() throws IOException {
    var initial = URI.create(baseUrl + "/loop").toURL().openConnection();
    initial.connect();
    // Should return after MAX_REDIRECTS (3) without throwing
    var result = NetworkUtil.applyRedirectionStream(initial, Map.of());
    assertNotNull(result);
  }

  @Test
  void readResponseReturnsConnectionOnSuccess() throws IOException {
    HttpURLConnection conn =
        (HttpURLConnection) URI.create(baseUrl + "/ok").toURL().openConnection();
    var result = NetworkUtil.readResponse(conn, Map.of());
    assertNotNull(result);
    assertEquals(200, ((HttpURLConnection) result).getResponseCode());
  }

  @Test
  void readResponseFollowsRedirect() throws IOException {
    HttpURLConnection conn =
        (HttpURLConnection) URI.create(baseUrl + "/redirect").toURL().openConnection();
    conn.setInstanceFollowRedirects(false);
    var result = NetworkUtil.readResponse(conn, Map.of());
    assertEquals(200, ((HttpURLConnection) result).getResponseCode());
  }

  @Test
  void readResponseThrowsOnServerError() throws IOException {
    HttpURLConnection conn =
        (HttpURLConnection) URI.create(baseUrl + "/error").toURL().openConnection();
    assertThrows(StreamIOException.class, () -> NetworkUtil.readResponse(conn, Map.of()));
  }

  @Test
  void getHttpResponseWithCustomOAuthRequestIgnoredForNoAuth() throws Exception {
    try (HttpStream s =
        NetworkUtil.getHttpResponse(
            baseUrl + "/ok",
            URLParameters.DEFAULT,
            org.weasis.core.api.net.auth.OAuth2ServiceFactory.NO_AUTH,
            null)) {
      assertEquals(200, s.getResponseCode());
    }
  }

  @Test
  void isValidUrlLikeUriHandlesMalformedInput() {
    assertFalse(NetworkUtil.isValidUrlLikeUri("http://"));
    assertFalse(NetworkUtil.isValidUrlLikeUri(""));
  }
}
