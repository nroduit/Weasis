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

import com.github.scribejava.core.oauth.OAuth20Service;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.Messages;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;

public class AcceptCompletionHandler implements AcceptCallbackHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(AcceptCompletionHandler.class);

  private final OAuth20Service service;
  private String code;

  public AcceptCompletionHandler(OAuth20Service service) {
    this.service = service;
  }

  @Override
  public void completed(AsynchronousSocketChannel channel, AsyncCallbackServerHandler handler) {
    handler.getSocketChannel().accept(handler, this);
    ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
    channel.read(
        byteBuffer,
        byteBuffer,
        new CompletionHandler<>() {
          @Override
          public void completed(Integer result, ByteBuffer attachment) {
            byteBuffer.flip();
            byte[] body = new byte[byteBuffer.remaining()];
            byteBuffer.get(body);
            String req = new String(body, StandardCharsets.UTF_8);
            String returnedMsg =
                "HTTP/1.1 404 Not Found" // NON-NLS
                    + "\r\n\r\n"
                    + Messages.getString("code.has.failed")
                    + "\n";
            try (BufferedReader reader = new BufferedReader(new StringReader(req))) {
              String line = reader.readLine();
              while (StringUtil.hasText(line)) {
                int idx = line.indexOf("code="); // NON-NLS
                if (idx >= 0) {
                  String key = line.substring(idx + 5).trim();
                  idx = key.indexOf('&');
                  if (idx < 0) {
                    idx = key.indexOf(' ');
                  }
                  if (idx >= 0) {
                    key = key.substring(0, idx);
                  }
                  setCode(key);
                  returnedMsg =
                      "HTTP/1.1 200 OK" // NON-NLS
                          + "\r\n\r\n"
                          + Messages.getString("code.has.been.transmitted");
                  break;
                }
                line = reader.readLine();
              }
            } catch (IOException e) {
              LOGGER.error("Try to get code from callback", e);
              returnedMsg = returnedMsg + e.getMessage();
            } finally {
              ByteBuffer buffer = ByteBuffer.wrap(returnedMsg.getBytes(StandardCharsets.UTF_8));
              channel.write(buffer, channel, new WriteHandler(buffer));
            }
          }

          @Override
          public void failed(Throwable exc, ByteBuffer attachment) {
            LOGGER.error("Cannot get code from callback", exc);
          }
        });
  }

  @Override
  public String getCode() {
    return code;
  }

  @Override
  public void setCode(String code) {
    this.code = code;
  }

  @Override
  public void failed(Throwable t, AsyncCallbackServerHandler handler) {
    LOGGER.error("Socket failed", t);
  }

  @Override
  public OAuth20Service getService() {
    return service;
  }

  private static class WriteHandler
      implements CompletionHandler<Integer, AsynchronousSocketChannel> {
    private final ByteBuffer buffer;

    public WriteHandler(ByteBuffer buffer) {
      this.buffer = buffer;
    }

    @Override
    public void completed(Integer result, AsynchronousSocketChannel attachment) {
      buffer.clear();
      FileUtil.safeClose(attachment);
    }

    @Override
    public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
      LOGGER.error("Cannot send acknowledge from callback", exc);
      FileUtil.safeClose(attachment);
    }
  }
}
