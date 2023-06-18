/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.auth;

import com.github.scribejava.core.model.OAuth2AccessToken;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncCallbackServerHandler implements Runnable {
  private static final Logger LOGGER = LoggerFactory.getLogger(AsyncCallbackServerHandler.class);

  private final AtomicBoolean stopped = new AtomicBoolean(true);
  private final AcceptCallbackHandler responseHandler;
  private final AsynchronousServerSocketChannel socketChannel;
  private final int port;
  private CountDownLatch latch;
  private Thread worker;
  private OAuth2AccessToken token;

  public AsyncCallbackServerHandler(int port, AcceptCallbackHandler responseHandler)
      throws IOException {
    this.port = port;
    this.latch = new CountDownLatch(1);
    this.responseHandler = responseHandler;
    this.socketChannel = AsynchronousServerSocketChannel.open();
    socketChannel.bind(new InetSocketAddress(port));
    LOGGER.info("The async callback server is start in port {}", port);
  }

  @Override
  public void run() {
    stopped.set(false);
    doAccept();
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    stopped.set(true);
  }

  public synchronized void start() {
    if (isStopped()) {
      this.latch = new CountDownLatch(1);
      this.worker = new Thread(this, "AsyncCallbackServerHandler");
      worker.start();
    }
  }

  boolean isStopped() {
    return stopped.get();
  }

  public void shutdown() {
    try {
      if (socketChannel != null && socketChannel.isOpen()) {
        socketChannel.close();
      }
      latch.countDown();
      worker.interrupt();
      LOGGER.info("Async callback server shutdown");
    } catch (Exception e) {
      LOGGER.error("Fail to shutdown async callback server");
    }
  }

  void doAccept() {
    socketChannel.accept(this, responseHandler);
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
