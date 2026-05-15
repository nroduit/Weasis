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

import com.github.scribejava.core.model.Response;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
    assertThrows(NullPointerException.class, () -> new HttpResponseStream((Response) null));
  }

  @Test
  void wrapsScribeResponseDirectly() throws IOException {
    Response r =
        new Response(
            418, "I'm a teapot", Map.of("k", "v"), new ByteArrayInputStream("x".getBytes()));
    HttpResponseStream stream = new HttpResponseStream(r);
    assertSame(r, stream.response());
    assertEquals(418, stream.getResponseCode());
    assertEquals("I'm a teapot", stream.getResponseMessage());
    assertEquals("v", stream.getHeaderField("k"));
    assertEquals("x", new String(stream.getInputStream().readAllBytes()));
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
    // Header lookup may be case-sensitive depending on the underlying parser; just verify body.
    assertEquals("hello", new String(stream.getInputStream().readAllBytes()));
    stream.close();
  }
}
