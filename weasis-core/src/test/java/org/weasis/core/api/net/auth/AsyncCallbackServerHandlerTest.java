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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.scribejava.core.oauth.OAuth20Service;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AsyncCallbackServerHandlerTest {

  private static AcceptCallbackHandler stubHandler() {
    return new AcceptCallbackHandler() {
      @Override
      public Optional<String> code() {
        return Optional.empty();
      }

      @Override
      public void code(String code) {
        // no-op
      }

      @Override
      public OAuth20Service service() {
        return Mockito.mock(OAuth20Service.class);
      }

      @Override
      public void completed(
          java.nio.channels.AsynchronousSocketChannel result,
          AsyncCallbackServerHandler attachment) {
        // no-op
      }

      @Override
      public void failed(Throwable exc, AsyncCallbackServerHandler attachment) {
        // no-op
      }
    };
  }

  @Test
  void newHandlerIsStoppedAndExposesPort() {
    try (var server = new AsyncCallbackServerHandler(0, stubHandler())) {
      assertTrue(server.isStopped());
      assertEquals(0, server.getPort());
      assertSame(server.getResponseHandler(), server.getResponseHandler());
    }
  }

  @Test
  void startBindsSocketAndCloseShutsDown() throws IOException, InterruptedException {
    var responseHandler = stubHandler();
    var server = new AsyncCallbackServerHandler(0, responseHandler);
    try {
      server.start();
      waitUntil(() -> !server.isStopped());
      assertNotNull(server.getSocketChannel());
      assertTrue(server.getSocketChannel().isOpen());
    } finally {
      server.close();
    }
    assertFalse(server.getSocketChannel().isOpen());
  }

  @Test
  void startOnUsedPortThrowsIoException() throws IOException {
    try (ServerSocket blocker = new ServerSocket(0)) {
      int busyPort = blocker.getLocalPort();
      try (var server = new AsyncCallbackServerHandler(busyPort, stubHandler())) {
        assertThrows(IOException.class, server::start);
        assertTrue(server.isStopped());
      }
    }
  }

  @Test
  void doubleStartIsIdempotentWhileRunning() throws IOException, InterruptedException {
    var server = new AsyncCallbackServerHandler(0, stubHandler());
    try {
      server.start();
      waitUntil(() -> !server.isStopped());
      var firstChannel = server.getSocketChannel();
      server.start(); // no-op while not stopped
      assertSame(firstChannel, server.getSocketChannel());
    } finally {
      server.close();
    }
  }

  @Test
  void closeIsSafeToCallTwice() throws IOException {
    var server = new AsyncCallbackServerHandler(0, stubHandler());
    server.start();
    server.close();
    server.close(); // second close must not throw
  }

  private static void waitUntil(java.util.function.BooleanSupplier condition)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + 2_000;
    while (System.currentTimeMillis() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      Thread.sleep(10);
    }
    throw new AssertionError("Condition not met within timeout");
  }
}
