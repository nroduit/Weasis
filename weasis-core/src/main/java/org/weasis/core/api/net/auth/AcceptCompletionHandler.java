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

import com.github.scribejava.core.oauth.OAuth20Service;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.util.StreamUtil;
import org.weasis.core.util.StringUtil;

/** Handles OAuth callback completion by processing authorization codes from HTTP requests. */
public class AcceptCompletionHandler implements AcceptCallbackHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(AcceptCompletionHandler.class);
  private static final int BUFFER_SIZE = 1024;
  private static final String CODE_PARAM = "code=";
  private static final String HTTP_OK = "HTTP/1.1 200 OK\r\n\r\n";
  private static final String HTTP_NOT_FOUND = "HTTP/1.1 404 Not Found\r\n\r\n";

  private final OAuth20Service service;
  private String code;

  public AcceptCompletionHandler(OAuth20Service service) {
    this.service = service;
  }

  @Override
  public void completed(AsynchronousSocketChannel channel, AsyncCallbackServerHandler handler) {
    handler.getSocketChannel().accept(handler, this);
    ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
    channel.read(buffer, buffer, new RequestHandler(channel));
  }

  @Override
  public Optional<String> code() {
    return Optional.ofNullable(code);
  }

  @Override
  public void code(String code) {
    this.code = code;
  }

  @Override
  public void failed(Throwable t, AsyncCallbackServerHandler handler) {
    LOGGER.error("Socket failed", t);
  }

  @Override
  public OAuth20Service service() {
    return service;
  }

  private String extractCodeFromRequest(String request) {
    try (BufferedReader reader = new BufferedReader(new StringReader(request))) {
      return reader
          .lines()
          .filter(StringUtil::hasText)
          .map(AcceptCompletionHandler::parseCodeFromLine)
          .filter(Objects::nonNull)
          .findFirst()
          .orElse(null);
    } catch (IOException e) {
      LOGGER.error("Error parsing request", e);
      return null;
    }
  }

  private static String parseCodeFromLine(String line) {
    int codeIndex = line.indexOf(CODE_PARAM);
    if (codeIndex < 0) {
      return null;
    }
    // The authorization code is delimited by either '&' (next param) or ' ' (end of HTTP path).
    return line.substring(codeIndex + CODE_PARAM.length()).trim().split("[ &]", 2)[0];
  }

  private static String createResponse(boolean success) {
    return success
        ? HTTP_OK + Messages.getString("code.has.been.transmitted")
        : HTTP_NOT_FOUND + Messages.getString("code.has.failed") + "\n";
  }

  private final class RequestHandler implements CompletionHandler<Integer, ByteBuffer> {
    private final AsynchronousSocketChannel channel;

    private RequestHandler(AsynchronousSocketChannel channel) {
      this.channel = channel;
    }

    @Override
    public void completed(Integer result, ByteBuffer buffer) {
      buffer.flip();
      String request = StandardCharsets.UTF_8.decode(buffer).toString();
      String extractedCode = extractCodeFromRequest(request);
      if (extractedCode != null) {
        code(extractedCode);
      }
      sendResponse(createResponse(extractedCode != null));
    }

    @Override
    public void failed(Throwable exc, ByteBuffer buffer) {
      LOGGER.error("Cannot get code from callback", exc);
    }

    private void sendResponse(String response) {
      ByteBuffer responseBuffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
      channel.write(responseBuffer, channel, new WriteHandler(responseBuffer));
    }
  }

  private record WriteHandler(ByteBuffer buffer)
      implements CompletionHandler<Integer, AsynchronousSocketChannel> {

    @Override
    public void completed(Integer result, AsynchronousSocketChannel channel) {
      buffer.clear();
      StreamUtil.safeClose(channel);
    }

    @Override
    public void failed(Throwable exc, AsynchronousSocketChannel channel) {
      LOGGER.error("Cannot send acknowledge from callback", exc);
      StreamUtil.safeClose(channel);
    }
  }
}
