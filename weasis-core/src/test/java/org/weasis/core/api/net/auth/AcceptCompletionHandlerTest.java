/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.net.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.scribejava.core.oauth.OAuth20Service;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AcceptCompletionHandlerTest {

  private static final long POLL_TIMEOUT_MS = 5_000;

  @Test
  void serviceAccessorReturnsConstructorArgument() {
    OAuth20Service service = Mockito.mock(OAuth20Service.class);
    var handler = new AcceptCompletionHandler(service);
    assertSame(service, handler.service());
    assertTrue(handler.code().isEmpty());
  }

  @Test
  void manualCodeSetterUpdatesAccessor() {
    var handler = new AcceptCompletionHandler(Mockito.mock(OAuth20Service.class));
    handler.code("ABC");
    assertEquals("ABC", handler.code().orElseThrow());
  }

  @Test
  void incomingHttpRequestExtractsAuthorizationCodeAndAcknowledges() throws Exception {
    var handler = new AcceptCompletionHandler(Mockito.mock(OAuth20Service.class));
    try (var server = new AsyncCallbackServerHandler(0, handler)) {
      server.start();
      int port =
          server.getSocketChannel().getLocalAddress() instanceof InetSocketAddress addr
              ? addr.getPort()
              : -1;
      assertTrue(port > 0);

      String response =
          sendRequest(port, "GET /?code=AUTHCODE&state=xyz HTTP/1.1\r\nHost: x\r\n\r\n");
      assertNotNull(response);
      assertTrue(response.startsWith("HTTP/1.1 200 OK"), response);

      Awaited.until(() -> handler.code().isPresent());
      assertEquals("AUTHCODE", handler.code().orElseThrow());
    }
  }

  @Test
  void incomingRequestWithoutCodeReturns404() throws Exception {
    var handler = new AcceptCompletionHandler(Mockito.mock(OAuth20Service.class));
    try (var server = new AsyncCallbackServerHandler(0, handler)) {
      server.start();
      int port = ((InetSocketAddress) server.getSocketChannel().getLocalAddress()).getPort();
      String response = sendRequest(port, "GET /?state=xyz HTTP/1.1\r\nHost: x\r\n\r\n");
      assertTrue(response.startsWith("HTTP/1.1 404"), response);
      assertTrue(handler.code().isEmpty());
    }
  }

  @Test
  void codeWithoutTrailingParamIsExtracted() throws Exception {
    var handler = new AcceptCompletionHandler(Mockito.mock(OAuth20Service.class));
    try (var server = new AsyncCallbackServerHandler(0, handler)) {
      server.start();
      int port = ((InetSocketAddress) server.getSocketChannel().getLocalAddress()).getPort();
      sendRequest(port, "GET /?code=SINGLE HTTP/1.1\r\nHost: x\r\n\r\n");
      Awaited.until(() -> handler.code().isPresent());
      assertEquals("SINGLE", handler.code().orElseThrow());
    }
  }

  private static String sendRequest(int port, String request) throws IOException {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress("localhost", port), 2000);
      socket.setSoTimeout(2000);
      socket.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
      socket.getOutputStream().flush();
      return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  /** Tiny polling helper to avoid extra dependencies. */
  private static final class Awaited {
    static void until(java.util.function.BooleanSupplier condition) throws InterruptedException {
      long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
      while (System.currentTimeMillis() < deadline) {
        if (condition.getAsBoolean()) {
          return;
        }
        Thread.sleep(20);
      }
      throw new AssertionError("Condition not met within " + POLL_TIMEOUT_MS + "ms");
    }
  }
}
