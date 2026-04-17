/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.net.auth;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asynchronous OAuth2 callback server handler for processing authentication callbacks.
 *
 * <p>This handler creates a temporary HTTP server to receive OAuth2 authorization codes from
 * authentication providers during the OAuth2 authorization code flow.
 *
 * <p>Uses virtual threads for optimal I/O performance with minimal resource overhead.
 */
public final class AsyncCallbackServerHandler implements Runnable, AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(AsyncCallbackServerHandler.class);

  private final AtomicBoolean stopped = new AtomicBoolean(true);
  private final AcceptCallbackHandler responseHandler;
  private volatile AsynchronousServerSocketChannel socketChannel; // NOSONAR visibility reference
  private final int port;
  private final ExecutorService executor;
  private volatile CountDownLatch latch; // NOSONAR guarantees visibility of the reference

  /**
   * Creates a new async callback server handler.
   *
   * @param port the port to bind the server to
   * @param responseHandler the handler for processing incoming connections
   */
  public AsyncCallbackServerHandler(int port, AcceptCallbackHandler responseHandler) {
    this.port = port;
    this.responseHandler = responseHandler;
    this.latch = new CountDownLatch(1);
    this.executor = Executors.newVirtualThreadPerTaskExecutor();
  }

  @Override
  public void run() {
    stopped.set(false);
    try {
      socketChannel.accept(this, responseHandler);
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOGGER.debug("Server handler interrupted", e);
    } finally {
      stopped.set(true);
    }
  }

  /**
   * Opens the server socket and starts the callback server in a background virtual thread.
   *
   * @throws IOException if the server socket cannot be created or bound
   */
  public synchronized void start() throws IOException {
    if (isStopped()) {
      this.latch = new CountDownLatch(1);
      AsynchronousServerSocketChannel channel = AsynchronousServerSocketChannel.open();
      try {
        channel.bind(new InetSocketAddress(port));
      } catch (IOException e) {
        channel.close();
        throw e;
      }
      this.socketChannel = channel;
      LOGGER.info("Async callback server initialized on port {}", port);
      executor.submit(this);
    }
  }

  /**
   * Checks if the server is currently stopped.
   *
   * @return true if the server is stopped, false otherwise
   */
  public boolean isStopped() {
    return stopped.get();
  }

  @Override
  public void close() {
    try {
      if (socketChannel != null && socketChannel.isOpen()) {
        socketChannel.close();
      }
      latch.countDown();
      executor.close(); // Java 19+ auto-shutdown
      LOGGER.info("Async callback server shutdown completed");
    } catch (Exception e) {
      LOGGER.error("Failed to shutdown async callback server", e);
    }
  }

  AsynchronousServerSocketChannel getSocketChannel() {
    return socketChannel;
  }

  public AcceptCallbackHandler getResponseHandler() {
    return responseHandler;
  }

  public int getPort() {
    return port;
  }
}
