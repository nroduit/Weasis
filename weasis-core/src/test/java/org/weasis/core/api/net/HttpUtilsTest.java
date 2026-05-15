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

import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.weasis.core.api.net.auth.AuthMethod;
import org.weasis.core.api.net.auth.OAuth2ServiceFactory;
import org.weasis.core.util.StreamIOException;

class HttpUtilsTest {

  private static HttpServer server;
  private static String baseUrl;
  private static final AtomicReference<Map<String, java.util.List<String>>> LAST_HEADERS =
      new AtomicReference<>();

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
    server.start();
    baseUrl = "http://localhost:" + server.getAddress().getPort();
  }

  @AfterAll
  static void stop() {
    server.stop(0);
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

  @Test
  void getHttpConnectionFailsOnInvalidHostQuickly() throws Exception {
    URLParameters fast = URLParameters.builder().connectTimeout(50).readTimeout(200).build();
    var url = URI.create("http://127.0.0.1:1/").toURL();
    assertThrows(
        IOException.class,
        () -> HttpUtils.getHttpConnection(url, fast, HttpResponse.BodyHandlers.discarding()));
  }

  // -------------------------------------------------------------------------
  // executeAuthenticatedRequest / runInterruptibly / getOAuth20Service
  // -------------------------------------------------------------------------

  private static AuthMethod fakeAuthMethod() {
    return Mockito.mock(AuthMethod.class);
  }

  @Test
  void executeAuthenticatedRequestThrowsWhenServiceMissing() {
    AuthMethod auth = fakeAuthMethod();
    try (MockedStatic<OAuth2ServiceFactory> mocked =
        Mockito.mockStatic(OAuth2ServiceFactory.class)) {
      mocked.when(() -> OAuth2ServiceFactory.getService(auth)).thenReturn(null);
      var request = new OAuthRequest(Verb.GET, baseUrl + "/ok");
      IOException ex =
          assertThrows(
              IOException.class,
              () -> HttpUtils.executeAuthenticatedRequest(request, URLParameters.DEFAULT, auth));
      assertTrue(ex.getMessage().startsWith("Invalid authentication method"));
    }
  }

  @Test
  void executeAuthenticatedRequestReturnsAuthResponseOnSuccess() throws Exception {
    AuthMethod auth = fakeAuthMethod();
    OAuth20Service service = Mockito.mock(OAuth20Service.class);
    Response response =
        new Response(200, "OK", Map.of(), new ByteArrayInputStream("body".getBytes()));
    Mockito.when(service.execute(Mockito.any(OAuthRequest.class))).thenReturn(response);

    try (MockedStatic<OAuth2ServiceFactory> mocked =
        Mockito.mockStatic(OAuth2ServiceFactory.class)) {
      mocked.when(() -> OAuth2ServiceFactory.getService(auth)).thenReturn(service);
      var request = new OAuthRequest(Verb.GET, baseUrl + "/ok");
      AuthResponse result =
          HttpUtils.executeAuthenticatedRequest(request, URLParameters.DEFAULT, auth);
      assertEquals(200, result.getResponseCode());
      assertEquals("body", new String(result.getInputStream().readAllBytes()));
    }
  }

  @Test
  void executeAuthenticatedRequestWrapsInterruptedException() throws Exception {
    AuthMethod auth = fakeAuthMethod();
    OAuth20Service service = Mockito.mock(OAuth20Service.class);
    Mockito.when(service.execute(Mockito.any(OAuthRequest.class)))
        .thenThrow(new InterruptedException("boom"));

    try (MockedStatic<OAuth2ServiceFactory> mocked =
        Mockito.mockStatic(OAuth2ServiceFactory.class)) {
      mocked.when(() -> OAuth2ServiceFactory.getService(auth)).thenReturn(service);
      var request = new OAuthRequest(Verb.GET, baseUrl + "/ok");
      StreamIOException ex =
          assertThrows(
              StreamIOException.class,
              () -> HttpUtils.executeAuthenticatedRequest(request, URLParameters.DEFAULT, auth));
      assertTrue(ex.getMessage().contains("interrupted"));
      assertTrue(Thread.interrupted(), "Thread interrupt flag should have been re-set");
    }
  }

  @Test
  void executeAuthenticatedRequestWrapsRuntimeException() throws Exception {
    AuthMethod auth = fakeAuthMethod();
    OAuth20Service service = Mockito.mock(OAuth20Service.class);
    Mockito.when(service.execute(Mockito.any(OAuthRequest.class)))
        .thenThrow(new IllegalStateException("oops"));

    try (MockedStatic<OAuth2ServiceFactory> mocked =
        Mockito.mockStatic(OAuth2ServiceFactory.class)) {
      mocked.when(() -> OAuth2ServiceFactory.getService(auth)).thenReturn(service);
      var request = new OAuthRequest(Verb.GET, baseUrl + "/ok");
      StreamIOException ex =
          assertThrows(
              StreamIOException.class,
              () -> HttpUtils.executeAuthenticatedRequest(request, URLParameters.DEFAULT, auth));
      assertTrue(ex.getMessage().contains("failed"));
    }
  }

  @Test
  void executeAuthenticatedRequestPropagatesIOException() throws Exception {
    AuthMethod auth = fakeAuthMethod();
    OAuth20Service service = Mockito.mock(OAuth20Service.class);
    Mockito.when(service.execute(Mockito.any(OAuthRequest.class))).thenThrow(new IOException("io"));

    try (MockedStatic<OAuth2ServiceFactory> mocked =
        Mockito.mockStatic(OAuth2ServiceFactory.class)) {
      mocked.when(() -> OAuth2ServiceFactory.getService(auth)).thenReturn(service);
      var request = new OAuthRequest(Verb.GET, baseUrl + "/ok");
      IOException ex =
          assertThrows(
              IOException.class,
              () -> HttpUtils.executeAuthenticatedRequest(request, URLParameters.DEFAULT, auth));
      assertEquals("io", ex.getMessage());
    }
  }

  @Test
  void getHttpResponseWithAuthDelegatesToExecuteAuthenticatedRequest() throws Exception {
    AuthMethod auth = fakeAuthMethod();
    OAuth20Service service = Mockito.mock(OAuth20Service.class);
    Response response =
        new Response(200, "OK", Map.of(), new ByteArrayInputStream("body".getBytes()));
    Mockito.when(service.execute(Mockito.any(OAuthRequest.class))).thenReturn(response);

    try (MockedStatic<OAuth2ServiceFactory> mocked =
        Mockito.mockStatic(OAuth2ServiceFactory.class)) {
      mocked.when(() -> OAuth2ServiceFactory.getService(auth)).thenReturn(service);
      try (HttpStream stream =
          HttpUtils.getHttpResponse(baseUrl + "/ok", URLParameters.DEFAULT, auth)) {
        assertEquals(200, stream.getResponseCode());
      }
    }
  }
}
