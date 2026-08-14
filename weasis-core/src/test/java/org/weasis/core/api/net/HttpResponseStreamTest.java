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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HttpResponseStreamTest {

  private static HttpServer server;
  private static int port;

  @BeforeAll
  static void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/echo",
        ex -> {
          byte[] body = "hello".getBytes();
          ex.getResponseHeaders().add("X-Test", "ok");
          ex.sendResponseHeaders(200, body.length);
          try (var os = ex.getResponseBody()) {
            os.write(body);
          }
        });
    server.start();
    port = server.getAddress().getPort();
  }

  @AfterAll
  static void stopServer() {
    server.stop(0);
  }

  @Test
  void constructorRejectsNullResponse() {
    assertThrows(NullPointerException.class, () -> new HttpResponseStream(null));
  }

  @Test
  void adaptsJavaNetHttpResponse() throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    HttpResponse<InputStream> response =
        client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/echo")).build(),
            HttpResponse.BodyHandlers.ofInputStream());

    HttpResponseStream stream = new HttpResponseStream(response);
    assertEquals(200, stream.getResponseCode());
    assertNotNull(stream.getResponseMessage()); // HTTP/1.1
    assertEquals("ok", stream.getHeaderField("X-Test"));
    assertEquals("hello", new String(stream.getInputStream().readAllBytes()));
    stream.close();
  }

  @Test
  void headerLookupIsCaseInsensitive() throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    HttpResponse<InputStream> response =
        client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/echo")).build(),
            HttpResponse.BodyHandlers.ofInputStream());
    try (var stream = new HttpResponseStream(response)) {
      assertEquals("ok", stream.getHeaderField("x-test"));
    }
  }

  @Test
  void parseHeadersMergesDuplicateKeysKeepingFirst() {
    HttpResponse<?> response =
        stubResponseWithHeaders(Map.of("X-Multi", List.of("a", "b"), "X-Single", List.of("z")));
    Map<String, String> result = HttpResponseStream.parseHeaders(response);
    assertEquals("a, b", result.get("X-Multi"));
    assertEquals("z", result.get("X-Single"));
  }

  private static HttpResponse<?> stubResponseWithHeaders(Map<String, List<String>> raw) {
    var headers = java.net.http.HttpHeaders.of(raw, (k, v) -> true);
    return new HttpResponse<Object>() {
      @Override
      public int statusCode() {
        return 200;
      }

      @Override
      public java.net.http.HttpRequest request() {
        return null;
      }

      @Override
      public java.util.Optional<HttpResponse<Object>> previousResponse() {
        return java.util.Optional.empty();
      }

      @Override
      public java.net.http.HttpHeaders headers() {
        return headers;
      }

      @Override
      public Object body() {
        return null;
      }

      @Override
      public java.util.Optional<javax.net.ssl.SSLSession> sslSession() {
        return java.util.Optional.empty();
      }

      @Override
      public java.net.URI uri() {
        return java.net.URI.create("http://example/");
      }

      @Override
      public java.net.http.HttpClient.Version version() {
        return java.net.http.HttpClient.Version.HTTP_1_1;
      }
    };
  }
}
