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

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.OAuth20Service;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.util.StringUtil;

/** OAuth2 authentication method with automatic token refresh and browser-based authorization. */
public class DefaultAuthMethod implements AuthMethod {
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAuthMethod.class);

  private static final Duration POLLING_TIMEOUT = Duration.ofSeconds(60);
  private static final Duration TOKEN_RETRIEVAL_TIMEOUT = Duration.ofSeconds(15);
  private static final Duration POLLING_INTERVAL = Duration.ofSeconds(1);

  private final String uid;
  private final AuthProvider authProvider;
  private final AuthRegistration authRegistration;
  private volatile OAuth2AccessToken token; // NOSONAR guarantees visibility of the reference
  private volatile String code;
  private boolean local;

  /**
   * Creates a new authentication method instance.
   *
   * @param uid unique identifier (generates random UUID if null/empty)
   * @param authProvider OAuth provider configuration
   * @param authRegistration OAuth registration details
   */
  public DefaultAuthMethod(
      String uid, AuthProvider authProvider, AuthRegistration authRegistration) {
    this.uid = StringUtil.hasText(uid) ? uid : UUID.randomUUID().toString();
    this.authProvider = Objects.requireNonNull(authProvider);
    this.authRegistration = Objects.requireNonNull(authRegistration);
  }

  @Override
  public String getName() {
    return authProvider.name();
  }

  @Override
  public String toString() {
    return authProvider.name();
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

  @Override
  public void resetToken() {
    this.token = null;
  }

  @Override
  public OAuth2AccessToken getToken() {
    if (token == null && code != null) {
      refreshExistingToken();
    }
    if (token == null) {
      authenticate();
    }
    return token;
  }

  private void refreshExistingToken() {
    var service = OAuth2ServiceFactory.getService(this);
    if (service == null) {
      return;
    }
    try {
      this.token = service.refreshAccessToken(code);
    } catch (Exception e) {
      token = null;
      LOGGER.error("Cannot refresh existing token", e);
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void authenticate() {
    AsyncCallbackServerHandler authServer = null;
    try {
      authServer = createCallbackHandler();
      authServer.start();
      token = performOAuthFlow(authServer);
      if (token == null) {
        throw new IllegalArgumentException("Authentication failed - unable to obtain access token");
      }
    } catch (IOException e) {
      LOGGER.error("Authentication server error", e);
    } finally {
      shutdownServer(authServer);
    }
  }

  private AsyncCallbackServerHandler createCallbackHandler() throws IOException {
    var service = OAuth2ServiceFactory.getService(this);
    if (service == null) {
      throw new IOException("Cannot build OAuth service for " + getName());
    }

    var handler = new AcceptCompletionHandler(service);
    int port = extractPortFromCallback(service.getCallback());
    return new AsyncCallbackServerHandler(port, handler);
  }

  private static int extractPortFromCallback(String callback) {
    return Integer.parseInt(callback.substring(OAuth2ServiceFactory.CALLBACK_URL.length()));
  }

  private OAuth2AccessToken performOAuthFlow(AsyncCallbackServerHandler server) {
    try {
      var service = server.getResponseHandler().service();

      openAuthorizationUrl(service);
      var authCode = waitForAuthorizationCode(server);

      if (authCode == null) {
        return null;
      }

      var accessToken = retrieveAccessToken(service, authCode);
      this.code = accessToken != null ? accessToken.getRefreshToken() : null;

      return accessToken != null ? service.refreshAccessToken(this.code) : null;

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOGGER.error("Authentication flow interrupted", e);
      return null;
    } catch (Exception e) {
      LOGGER.error("OAuth flow failed", e);
      return null;
    }
  }

  private void openAuthorizationUrl(OAuth20Service service) throws Exception {
    var additionalParams = createAuthParams();
    var authorizationUrl =
        service.createAuthorizationUrlBuilder().additionalParams(additionalParams).build();

    GuiUtils.openInDefaultBrowser(null, URI.create(authorizationUrl).toURL());
  }

  private Map<String, String> createAuthParams() {
    var audience = authRegistration.audience();
    return StringUtil.hasText(audience)
        ? Map.of("access_type", "offline", "prompt", "consent", "audience", audience)
        : Map.of("access_type", "offline", "prompt", "consent");
  }

  private String waitForAuthorizationCode(AsyncCallbackServerHandler server)
      throws InterruptedException {
    long timeoutMillis = POLLING_TIMEOUT.toMillis();
    long intervalMillis = POLLING_INTERVAL.toMillis();
    long iterations = timeoutMillis / intervalMillis;

    var handler = server.getResponseHandler();

    for (long i = 0; i < iterations; i++) {
      var authCode = handler.code();
      if (authCode.isPresent()) {
        return authCode.get();
      }
      Thread.sleep(intervalMillis);
    }

    LOGGER.warn(
        "Timeout waiting for authorization code after {} seconds", POLLING_TIMEOUT.toSeconds());
    return null;
  }

  private static OAuth2AccessToken retrieveAccessToken(OAuth20Service service, String authCode)
      throws Exception {
    var future =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return service.getAccessTokenAsync(authCode).get();
              } catch (Exception e) {
                if (e instanceof InterruptedException) {
                  Thread.currentThread().interrupt();
                }
                throw new RuntimeException("Failed to get access token", e);
              }
            });

    return future.get(TOKEN_RETRIEVAL_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
  }

  private static void shutdownServer(AsyncCallbackServerHandler server) {
    if (server != null) {
      try {
        server.close();
      } catch (Exception e) {
        LOGGER.debug("Error shutting down auth server", e);
      }
    }
  }
}
