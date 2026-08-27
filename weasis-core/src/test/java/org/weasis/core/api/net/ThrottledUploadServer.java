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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Minimal HTTP/1.1 endpoint that controls how fast it drains a request body, used to separate a
 * slow upload from a stalled one.
 *
 * <p>A raw socket is used rather than {@code com.sun.net.httpserver} because the receive buffer has
 * to be bounded: it caps how far the client can run ahead of the server, which is what keeps the
 * gap between two upload progress signals predictable. The JDK's server offers no such control and
 * lets the window grow until a healthy transfer looks stalled.
 */
public final class ThrottledUploadServer implements AutoCloseable {

  private static final int RECEIVE_BUFFER = 64 * 1024;

  private final ServerSocket serverSocket;
  private final Thread acceptor;

  private ThrottledUploadServer(int drainChunk, long pauseMillis, boolean stallAfterFirstChunk)
      throws IOException {
    this.serverSocket = new ServerSocket();
    // Must precede bind() to apply to accepted connections.
    this.serverSocket.setReceiveBufferSize(RECEIVE_BUFFER);
    this.serverSocket.bind(new InetSocketAddress("localhost", 0));
    this.acceptor =
        Thread.ofPlatform()
            .daemon()
            .name("throttled-upload-server")
            .start(() -> acceptLoop(drainChunk, pauseMillis, stallAfterFirstChunk));
  }

  /** Reads the body continuously at roughly {@code drainChunk} bytes per {@code pauseMillis}. */
  public static ThrottledUploadServer draining(int drainChunk, long pauseMillis)
      throws IOException {
    return new ThrottledUploadServer(drainChunk, pauseMillis, false);
  }

  /** Reads one chunk then stops reading without closing, so the client blocks on a full socket. */
  public static ThrottledUploadServer stalling(int drainChunk) throws IOException {
    return new ThrottledUploadServer(drainChunk, 0, true);
  }

  public String url() {
    return "http://localhost:" + serverSocket.getLocalPort() + "/stow";
  }

  @Override
  public void close() throws IOException {
    acceptor.interrupt();
    serverSocket.close();
  }

  private void acceptLoop(int drainChunk, long pauseMillis, boolean stallAfterFirstChunk) {
    while (!serverSocket.isClosed()) {
      try {
        Socket socket = serverSocket.accept();
        Thread.ofPlatform()
            .daemon()
            .start(() -> handle(socket, drainChunk, pauseMillis, stallAfterFirstChunk));
      } catch (IOException e) {
        return; // closed
      }
    }
  }

  private static void handle(
      Socket socket, int drainChunk, long pauseMillis, boolean stallAfterFirstChunk) {
    try (socket) {
      InputStream in = socket.getInputStream();
      int contentLength = readHeaders(in);
      byte[] buffer = new byte[drainChunk];
      if (stallAfterFirstChunk) {
        in.read(buffer);
        Thread.sleep(30_000);
        return;
      }
      long read = 0;
      while (read < contentLength) {
        int n = in.read(buffer, 0, (int) Math.min(drainChunk, contentLength - read));
        if (n < 0) {
          break;
        }
        read += n;
        Thread.sleep(pauseMillis);
      }
      OutputStream out = socket.getOutputStream();
      out.write(
          "HTTP/1.1 200 OK\r\nContent-Length: 4\r\nConnection: close\r\n\r\nstow"
              .getBytes(StandardCharsets.UTF_8));
      out.flush();
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static int readHeaders(InputStream in) throws IOException {
    var head = new StringBuilder();
    int b;
    while ((b = in.read()) >= 0) {
      head.append((char) b);
      if (head.length() >= 4 && head.lastIndexOf("\r\n\r\n") == head.length() - 4) {
        break;
      }
    }
    for (String line : head.toString().split("\r\n")) {
      if (line.regionMatches(true, 0, "content-length:", 0, 15)) {
        return Integer.parseInt(line.substring(15).trim());
      }
    }
    return 0;
  }
}
