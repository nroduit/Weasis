/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.setting;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * The HTTP transport against a stand-in of the Viewer Hub preference endpoint, following its
 * contract: {@code user}, {@code profile}, {@code module} query parameters; {@code 204} when the
 * module holds nothing; the body returned as stored, as {@code text/plain} without a charset.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class RemoteSettingsHttpTest {
  private HttpServer server;
  private final Map<String, String> stored = new ConcurrentHashMap<>();
  private final Map<String, String> lastQuery = new ConcurrentHashMap<>();
  private String url;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/weasisconfig/ws/preferences", this::handle); // NON-NLS
    server.start();
    url = "http://127.0.0.1:" + server.getAddress().getPort() + "/weasisconfig/ws/preferences";
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private void handle(HttpExchange exchange) throws IOException {
    Map<String, String> query = new TreeMap<>();
    for (String pair : exchange.getRequestURI().getRawQuery().split("&")) {
      String[] kv = pair.split("=", 2);
      query.put(kv[0], URLDecoder.decode(kv.length > 1 ? kv[1] : "", StandardCharsets.UTF_8));
    }
    lastQuery.clear();
    lastQuery.putAll(query);
    String key = query.get("user") + "|" + query.get("profile") + "|" + query.get("module");
    if ("POST".equals(exchange.getRequestMethod())) {
      stored.put(key, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      exchange.sendResponseHeaders(201, -1);
    } else {
      String body = stored.get(key);
      if (body == null) {
        exchange.sendResponseHeaders(204, -1);
      } else {
        // text/plain without a charset: Spring writes such a body in ISO-8859-1
        byte[] bytes = body.getBytes(StandardCharsets.ISO_8859_1);
        exchange.getResponseHeaders().add("Content-Type", "text/plain");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
          out.write(bytes);
        }
      }
    }
    exchange.close();
  }

  @Test
  void a_module_is_written_and_read_back_by_user_profile_and_module() throws IOException {
    var transport = new RemoteSettings.HttpTransport(url, "José", "default"); // NON-NLS
    String body =
        RemoteSettings.canonical(
            jakarta.json.Json.createObjectBuilder().add("name", "Thorax é").build()); // NON-NLS

    assertNull(transport.get("documents/windowPresets@1"));
    transport.put("documents/windowPresets@1", body);
    String back = transport.get("documents/windowPresets@1");

    assertAll(
        () -> assertEquals(body, back),
        () -> assertTrue(back.chars().allMatch(c -> c < 0x80)),
        () -> assertEquals("José", lastQuery.get("user")),
        () -> assertEquals("default", lastQuery.get("profile")),
        () -> assertEquals("documents/windowPresets@1", lastQuery.get("module")));
  }
}
