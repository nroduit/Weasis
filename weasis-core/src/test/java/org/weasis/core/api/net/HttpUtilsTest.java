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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.weasis.core.api.net.auth.AuthMethod;
import org.weasis.core.api.net.auth.OAuth2ServiceFactory;
import org.weasis.core.api.net.auth.OAuth2Token;

class HttpUtilsTest {

  private static final int SLOW_CHUNKS = 8;
  private static final int CHUNK_SIZE = 1024;

  /**
   * An upload big enough to outlast {@link #UPLOAD_TIMEOUT_MS} while the server keeps draining it.
   * {@link ThrottledUploadServer} bounds its receive buffer so the gap between two upload progress
   * signals stays far below the timeout even though socket buffering makes that signal bursty.
   */
  private static final int UPLOAD_SIZE = 8 << 20;

  private static final int UPLOAD_DRAIN_CHUNK = 64 * 1024;
  private static final long UPLOAD_DRAIN_PAUSE_MS = 20;
  private static final int UPLOAD_TIMEOUT_MS = 1_500;

  private static HttpServer server;
  private static ExecutorService serverPool;
  private static String baseUrl;
  private static final AtomicReference<Map<String, java.util.List<String>>> LAST_HEADERS =
      new AtomicReference<>();
  private static final AtomicReference<byte[]> LAST_BODY = new AtomicReference<>();

  @BeforeAll
  static void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/ok",
        ex -> {
          LAST_HEADERS.set(Map.copyOf(ex.getRequestHeaders()));
          byte[] body = "ok".getBytes();
          ex.sendResponseHeaders(200, body.length);
          try (var os = ex.getResponseBody()) {
            os.write(body);
          }
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
        "/error",
        ex -> {
          ex.sendResponseHeaders(500, -1);
          ex.close();
        });
    server.createContext(
        "/stow",
        ex -> {
          LAST_HEADERS.set(Map.copyOf(ex.getRequestHeaders()));
          byte[] received = ex.getRequestBody().readAllBytes();
          LAST_BODY.set(received);
          // Reject unless the caller declares the DICOM multipart/related content type,
          // mimicking dcm4chee's 415 response.
          String contentType = ex.getRequestHeaders().getFirst("Content-Type");
          int status =
              contentType != null && contentType.startsWith("multipart/related") ? 200 : 415;
          byte[] body = "stow".getBytes();
          ex.sendResponseHeaders(status, body.length);
          try (var os = ex.getResponseBody()) {
            os.write(body);
          }
        });
    server.createContext(
        "/slow-stream",
        ex -> {
          ex.sendResponseHeaders(200, 0);
          try (var os = ex.getResponseBody()) {
            for (int i = 0; i < SLOW_CHUNKS; i++) {
              os.write(new byte[CHUNK_SIZE]);
              os.flush();
              sleepQuietly(120);
            }
          }
        });
    server.createContext(
        "/stalled-stream",
        ex -> {
          ex.sendResponseHeaders(200, 0);
          try (var os = ex.getResponseBody()) {
            os.write(new byte[CHUNK_SIZE]);
            os.flush();
            // Headers and a first chunk arrive, then the peer goes quiet without closing.
            sleepQuietly(3_000);
          }
        });
    // Without an explicit executor the handlers run on the dispatcher thread, so the slow
    // endpoints above would serialize every other request in this class.
    serverPool = Executors.newCachedThreadPool();
    server.setExecutor(serverPool);
    server.start();
    baseUrl = "http://localhost:" + server.getAddress().getPort();
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @AfterAll
  static void stop() {
    server.stop(0);
    serverPool.shutdownNow();
  }

  @Test
  void sharedClientIsReused() {
    assertSame(HttpUtils.getDefaulttHttpClient(), HttpUtils.getDefaulttHttpClient());
  }

  @Test
  void buildHttpClientHonoursOverloads() {
    assertNotNull(HttpUtils.buildHttpClient());
    assertNotNull(HttpUtils.buildHttpClient(Duration.ofSeconds(1)));
    assertNotNull(
        HttpUtils.buildHttpClient(
            Duration.ofSeconds(1), HttpClient.Redirect.NEVER, ProxySelector.getDefault()));
  }

  @Test
  void getHttpConnectionReturnsBodyOn200() throws IOException {
    HttpResponse<String> response =
        HttpUtils.getHttpConnection(
            URI.create(baseUrl + "/ok").toURL(),
            URLParameters.DEFAULT,
            HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());
    assertEquals("ok", response.body());
    assertNotNull(LAST_HEADERS.get());
  }

  @Test
  void getHttpConnectionFailsOnNon200() throws Exception {
    var url = URI.create(baseUrl + "/error").toURL();
    assertThrows(
        IOException.class,
        () ->
            HttpUtils.getHttpConnection(
                url, URLParameters.DEFAULT, HttpResponse.BodyHandlers.discarding()));
  }

  @Test
  void getHttpResponseWithoutAuthReturnsHttpStream() throws IOException {
    try (HttpStream stream =
        HttpUtils.getHttpResponse(baseUrl + "/ok", URLParameters.DEFAULT, null)) {
      assertEquals(200, stream.getResponseCode());
      assertEquals("ok", new String(stream.getInputStream().readAllBytes()));
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  @Test
  void isNoAuthRequiredHandlesNullAndDefault() {
    assertTrue(HttpUtils.isNoAuthRequired(null));
  }

  @Test
  void applyHeadersAddsCustomHeadersAndSkipsNullAppHeaders() {
    var collected = new java.util.LinkedHashMap<String, String>();
    HttpUtils.applyHeaders(Map.of("X", "1"), collected::put);
    assertEquals("1", collected.get("X"));
    // Application headers may be null in the test environment; only assert non-null
    // entries were added (no NullPointerException raised).
  }

  // -------------------------------------------------------------------------
  // Additional branch coverage
  // -------------------------------------------------------------------------

  @Test
  void getHttpConnectionWithCustomClientUsesProvidedClient() throws IOException {
    HttpClient client = HttpUtils.buildHttpClient(Duration.ofSeconds(2));
    HttpResponse<String> response =
        HttpUtils.getHttpConnection(
            client,
            URI.create(baseUrl + "/ok").toURL(),
            URLParameters.DEFAULT,
            HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());
    assertEquals("ok", response.body());
  }

  @Test
  void getHttpResponseWithClientOverloadWorks() throws Exception {
    HttpClient client = HttpUtils.buildHttpClient(Duration.ofSeconds(2));
    try (HttpStream stream =
        HttpUtils.getHttpResponse(client, baseUrl + "/ok", URLParameters.DEFAULT, null)) {
      assertEquals(200, stream.getResponseCode());
    }
  }

  @Test
  void getHttpResponseFourArgClientOverloadIgnoresAuthRequestForNoAuth() throws Exception {
    HttpClient client = HttpUtils.buildHttpClient(Duration.ofSeconds(2));
    try (HttpStream stream =
        HttpUtils.getHttpResponse(
            client, baseUrl + "/ok", URLParameters.DEFAULT, OAuth2ServiceFactory.NO_AUTH, null)) {
      assertEquals(200, stream.getResponseCode());
    }
  }

  @Test
  void getHttpResponseRecognisesNoAuthSentinel() throws Exception {
    try (HttpStream stream =
        HttpUtils.getHttpResponse(
            baseUrl + "/ok", URLParameters.DEFAULT, OAuth2ServiceFactory.NO_AUTH, null)) {
      assertEquals(200, stream.getResponseCode());
    }
    assertTrue(HttpUtils.isNoAuthRequired(OAuth2ServiceFactory.NO_AUTH));
  }

  @Test
  void postRequestSendsHttpPostMethod() throws IOException {
    URLParameters post = new URLParameters(Map.of(), true);
    HttpResponse<String> response =
        HttpUtils.getHttpConnection(
            URI.create(baseUrl + "/echo-method").toURL(),
            post,
            HttpResponse.BodyHandlers.ofString());
    assertEquals("POST", response.body());
  }

  @Test
  void postNoAuthHttpResponseSendsPost() throws Exception {
    URLParameters post = new URLParameters(Map.of(), true);
    try (HttpStream s = HttpUtils.getHttpResponse(baseUrl + "/echo-method", post, null)) {
      assertEquals("POST", new String(s.getInputStream().readAllBytes()));
    }
  }

  private static MultipartBody dicomMultipart(byte[] content) {
    String boundary = "weasisBoundary";
    var headers = new java.util.HashMap<String, String>();
    headers.put(
        "Content-Type", "multipart/related;type=\"application/dicom\";boundary=" + boundary);
    headers.put("Accept", "application/dicom+xml");
    var multipart = new MultipartBody(boundary, headers);
    multipart.addBodyPart(
        new BodyPart(Map.of("Content-Type", "application/dicom"), BodySupplier.ofBytes(content)));
    return multipart;
  }

  @Test
  void noAuthMultipartSendsBodyWithRelatedContentType() throws Exception {
    LAST_BODY.set(null);
    byte[] content = "DICM-payload".getBytes();
    var request = new WebRequest(WebRequest.Method.POST, baseUrl + "/stow");
    request.setMultipartBody(dicomMultipart(content));
    URLParameters post = new URLParameters(Map.of(), true);

    try (HttpStream stream =
        HttpUtils.getHttpResponse(baseUrl + "/stow", post, OAuth2ServiceFactory.NO_AUTH, request)) {
      // The multipart/related content type is preserved, so the server accepts it (no 415).
      assertEquals(200, stream.getResponseCode());
    }
    String contentType = LAST_HEADERS.get().get("Content-type").getFirst();
    assertTrue(contentType.startsWith("multipart/related"), contentType);
    // The DICOM payload actually reached the server instead of an empty body.
    assertNotNull(LAST_BODY.get());
    assertTrue(new String(LAST_BODY.get()).contains("DICM-payload"));
  }

  @Test
  void noAuthMultipartReturnsServerStatusWithoutThrowing() throws Exception {
    // A server that rejects the content type (415) must surface the code to the caller rather
    // than throwing, so STOW-RS can build a proper error message.
    var request = new WebRequest(WebRequest.Method.POST, baseUrl + "/stow");
    request.setMultipartBody(new MultipartBody("b", Map.of("Content-Type", "text/plain")));
    URLParameters post = new URLParameters(Map.of(), true);
    try (HttpStream stream =
        HttpUtils.getHttpResponse(baseUrl + "/stow", post, OAuth2ServiceFactory.NO_AUTH, request)) {
      assertEquals(415, stream.getResponseCode());
    }
  }

  private static URLParameters uploadParams() {
    return URLParameters.builder()
        .httpPost(true)
        .connectTimeoutMillis(2_000)
        .inactivityTimeoutMillis(UPLOAD_TIMEOUT_MS)
        .build();
  }

  @Test
  void slowUploadOutlivesTheInactivityTimeoutWhileBytesKeepFlowing() throws Exception {
    // The upload takes several times the timeout. Before the stall guard covered the request half,
    // the response future's deadline aborted this healthy transfer: a STOW-RS send of anything
    // bigger than the link could carry in one timeout window always failed.
    try (var upstream = ThrottledUploadServer.draining(UPLOAD_DRAIN_CHUNK, UPLOAD_DRAIN_PAUSE_MS)) {
      var request = new WebRequest(WebRequest.Method.POST, upstream.url());
      request.setMultipartBody(dicomMultipart(new byte[UPLOAD_SIZE]));
      try (HttpStream stream =
          HttpUtils.getHttpResponse(
              upstream.url(), uploadParams(), OAuth2ServiceFactory.NO_AUTH, request)) {
        assertEquals(200, stream.getResponseCode());
      }
    }
  }

  @Test
  void stalledUploadIsAbortedOnceBytesStopFlowing() throws Exception {
    try (var upstream = ThrottledUploadServer.stalling(UPLOAD_DRAIN_CHUNK)) {
      var request = new WebRequest(WebRequest.Method.POST, upstream.url());
      request.setMultipartBody(dicomMultipart(new byte[UPLOAD_SIZE]));
      assertThrows(
          StallTimeoutException.class,
          () ->
              HttpUtils.getHttpResponse(
                  upstream.url(), uploadParams(), OAuth2ServiceFactory.NO_AUTH, request));
    }
  }

  @Test
  void slowStreamOutlivesTheReadTimeoutWhileDataKeepsFlowing() throws Exception {
    // ~1s of streaming under a 400ms timeout: the timeout bounds a stalled read, not the transfer.
    URLParameters params =
        URLParameters.builder().connectTimeoutMillis(2_000).inactivityTimeoutMillis(400).build();
    try (HttpStream stream =
        HttpUtils.getHttpResponse(baseUrl + "/slow-stream", params, OAuth2ServiceFactory.NO_AUTH)) {
      assertEquals(200, stream.getResponseCode());
      assertEquals(SLOW_CHUNKS * CHUNK_SIZE, stream.getInputStream().readAllBytes().length);
    }
  }

  @Test
  void stalledStreamIsAbortedOnceDataStopsFlowing() throws Exception {
    URLParameters params =
        URLParameters.builder().connectTimeoutMillis(2_000).inactivityTimeoutMillis(400).build();
    try (HttpStream stream =
        HttpUtils.getHttpResponse(
            baseUrl + "/stalled-stream", params, OAuth2ServiceFactory.NO_AUTH)) {
      assertEquals(200, stream.getResponseCode());
      InputStream body = stream.getInputStream();
      assertThrows(StallTimeoutException.class, body::readAllBytes);
    }
  }

  @Test
  void getHttpConnectionFailsOnInvalidHostQuickly() throws Exception {
    URLParameters fast =
        URLParameters.builder().connectTimeoutMillis(50).inactivityTimeoutMillis(200).build();
    var url = URI.create("http://127.0.0.1:1/").toURL();
    assertThrows(
        IOException.class,
        () -> HttpUtils.getHttpConnection(url, fast, HttpResponse.BodyHandlers.discarding()));
  }

  // -------------------------------------------------------------------------
  // executeAuthenticatedRequest
  // -------------------------------------------------------------------------

  private static AuthMethod fakeAuthMethod(OAuth2Token token) {
    AuthMethod auth = Mockito.mock(AuthMethod.class);
    Mockito.when(auth.getToken()).thenReturn(token);
    return auth;
  }

  @Test
  void executeAuthenticatedRequestThrowsWhenTokenMissing() {
    AuthMethod auth = fakeAuthMethod(null);
    var request = new WebRequest(WebRequest.Method.GET, baseUrl + "/ok");
    IOException ex =
        assertThrows(
            IOException.class,
            () -> HttpUtils.executeAuthenticatedRequest(request, URLParameters.DEFAULT, auth));
    assertTrue(ex.getMessage().startsWith("Cannot get an access token"));
  }

  @Test
  void executeAuthenticatedRequestSignsWithBearerToken() throws Exception {
    AuthMethod auth = fakeAuthMethod(new OAuth2Token("tok-123", "Bearer", 3600, null, null, null));
    var request = new WebRequest(WebRequest.Method.GET, baseUrl + "/ok");
    try (var result = HttpUtils.executeAuthenticatedRequest(request, URLParameters.DEFAULT, auth)) {
      assertEquals(200, result.getResponseCode());
      assertEquals("ok", new String(result.getInputStream().readAllBytes()));
    }
    assertEquals("Bearer tok-123", LAST_HEADERS.get().get("Authorization").getFirst());
  }

  @Test
  void isIdempotentRecognizesSafeMethodsOnly() {
    HttpRequest get = HttpRequest.newBuilder(URI.create(baseUrl + "/ok")).GET().build();
    HttpRequest post =
        HttpRequest.newBuilder(URI.create(baseUrl + "/ok"))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
    HttpRequest put =
        HttpRequest.newBuilder(URI.create(baseUrl + "/ok"))
            .PUT(HttpRequest.BodyPublishers.noBody())
            .build();
    assertTrue(HttpUtils.isIdempotent(get));
    assertFalse(HttpUtils.isIdempotent(post));
    assertFalse(HttpUtils.isIdempotent(put));
  }

  @Test
  void isStaleConnectionFailureMatchesKnownSignatures() {
    // Direct SocketException with "Connection reset"
    assertTrue(
        HttpUtils.isStaleConnectionFailure(new java.net.SocketException("Connection reset")));

    // Wrapped: top-level IOException with the JDK HttpClient header-parser message
    var outer = new IOException("HTTP/1.1 header parser received no bytes");
    assertTrue(HttpUtils.isStaleConnectionFailure(outer));

    // Nested cause chain — the actual stack we see from the JDK
    var rst = new java.net.SocketException("Connection reset");
    var headerErr = new IOException("HTTP/1.1 header parser received no bytes", rst);
    assertTrue(HttpUtils.isStaleConnectionFailure(headerErr));

    // Unrelated errors must NOT match
    assertFalse(HttpUtils.isStaleConnectionFailure(new IOException("404 Not Found")));
    assertFalse(HttpUtils.isStaleConnectionFailure(new IOException("Connection refused")));
    assertFalse(HttpUtils.isStaleConnectionFailure(null));
  }

  @Test
  void getHttpResponseWithAuthDelegatesToExecuteAuthenticatedRequest() throws Exception {
    AuthMethod auth = fakeAuthMethod(new OAuth2Token("tok", null, null, null, null, null));
    try (HttpStream stream =
        HttpUtils.getHttpResponse(baseUrl + "/ok", URLParameters.DEFAULT, auth)) {
      assertEquals(200, stream.getResponseCode());
    }
  }
}
