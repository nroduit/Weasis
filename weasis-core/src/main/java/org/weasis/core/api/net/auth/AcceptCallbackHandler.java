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
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Optional;

/**
 * Handles OAuth2 callback server operations for asynchronous socket connections. Processes OAuth2
 * authorization code responses via the {@link CompletionHandler} interface.
 */
public interface AcceptCallbackHandler
    extends CompletionHandler<AsynchronousSocketChannel, AsyncCallbackServerHandler> {

  /**
   * @return the OAuth2 authorization code received from the callback, or empty if not yet received
   */
  Optional<String> code();

  /**
   * @param code the OAuth2 authorization code received from the callback
   */
  void code(String code);

  /**
   * @return the OAuth2 service used for authentication
   */
  OAuth20Service service();
}
