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
import com.github.scribejava.core.oauth.OAuth20Service;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.util.StringUtil;

public class DefaultAuthMethod implements AuthMethod {
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAuthMethod.class);

  private final String uid;
  private final AuthProvider authProvider;
  private final AuthRegistration authRegistration;
  private OAuth2AccessToken token;
  private String code;
  private boolean local;

  public DefaultAuthMethod(
      String uid, AuthProvider authProvider, AuthRegistration authRegistration) {
    this.uid = StringUtil.hasText(uid) ? uid : UUID.randomUUID().toString();
    this.authProvider = Objects.requireNonNull(authProvider);
    this.authRegistration = Objects.requireNonNull(authRegistration);
  }

  public String getName() {
    return authProvider.getName();
  }

  @Override
  public String toString() {
    return authProvider.getName();
  }

  @Override
  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  @Override
  public String getUid() {
    return uid;
  }

  @Override
  public boolean isLocal() {
    return local;
  }

  @Override
  public void setLocal(boolean local) {
    this.local = local;
  }

  @Override
  public AuthProvider getAuthProvider() {
    return authProvider;
  }

  @Override
  public AuthRegistration getAuthRegistration() {
    return authRegistration;
  }

  AsyncCallbackServerHandler getAsyncCallbackServerHandler() throws IOException {
    OAuth20Service service = OAuth2ServiceFactory.getService(this);
    if (service == null) {
      throw new IOException("Cannot build async server handler with " + getName());
    }
    AcceptCompletionHandler handler = new AcceptCompletionHandler(service);
    int port =
        Integer.parseInt(
            service.getCallback().substring(OAuth2ServiceFactory.CALLBACK_URL.length()));
    return new AsyncCallbackServerHandler(port, handler);
  }

  OAuth2AccessToken getRefreshToken(AsyncCallbackServerHandler server) {
    try {
      final Map<String, String> additionalParams = new HashMap<>();
      additionalParams.put("access_type", "offline"); // NON-NLS
      // force to reget refresh token (if user are asked not the first time)
      additionalParams.put("prompt", "consent"); // NON-NLS
      OAuth20Service service = server.getResponseHandler().getService();
      final String authorizationUrl =
          service.createAuthorizationUrlBuilder().additionalParams(additionalParams).build();
      GuiUtils.openInDefaultBrowser(null, new URL(authorizationUrl));
      for (int i = 0; i < 60; i++) {
        if (server.getResponseHandler().getCode() != null) {
          break;
        }
        Thread.sleep(1000);
      }

      Future<OAuth2AccessToken> future =
          service.getAccessTokenAsync(server.getResponseHandler().getCode());
      OAuth2AccessToken access = future.get(15L, TimeUnit.SECONDS);
      code = access.getRefreshToken();
      return service.refreshAccessToken(code);
    } catch (Exception e) {
      LOGGER.error("Cannot get refreshToken", e);
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return null;
    }
  }

  @Override
  public void resetToken() {
    this.token = null;
  }

  @Override
  public OAuth2AccessToken getToken() {
    if (token == null && code != null) {
      OAuth20Service service = OAuth2ServiceFactory.getService(this);
      if (service != null) {
        try {
          this.token = service.refreshAccessToken(code);
        } catch (Exception e) {
          token = null;
          LOGGER.error("Cannot get refreshToken", e);
          if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
          }
        }
      }
    }
    if (token == null) {
      authenticate();
    }
    return token;
  }

  private void authenticate() {
    AsyncCallbackServerHandler authServer = null;
    try {
      authServer = getAsyncCallbackServerHandler();
      authServer.start();
      token = getRefreshToken(authServer);
      if (token == null) {
        throw new IllegalArgumentException("Cannot login!");
      }
    } catch (IOException e) {
      LOGGER.error("", e);
    } finally {
      if (authServer != null) {
        authServer.shutdown();
      }
    }
  }
}
