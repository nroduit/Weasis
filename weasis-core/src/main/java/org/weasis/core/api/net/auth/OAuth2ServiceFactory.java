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

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.extractors.OAuth2AccessTokenJsonExtractor;
import com.github.scribejava.core.extractors.TokenExtractor;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.OAuth20Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.net.SocketUtil;

/** Factory for creating and caching OAuth2 services with predefined authentication methods. */
public final class OAuth2ServiceFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(OAuth2ServiceFactory.class);

  public static final String CALLBACK_URL = "http://127.0.0.1:";
  public static final String NO_AUTH_ID = "5aa85854-8f1f-11eb-b339-d3daace59a05"; // NOSONAR !secret

  public static final DefaultAuthMethod NO_AUTH = createNoAuthMethod();

  public static final DefaultAuthMethod GOOGLE_AUTH_TEMPLATE = createGoogleAuthTemplate();
  public static final DefaultAuthMethod KEYCLOAK_TEMPLATE = createKeycloakTemplate();

  private static final String PORT_PREF_KEY = "weasis.auth.back.port"; // NON-NLS
  private static final Map<String, OAuth20Service> services = new ConcurrentHashMap<>();

  private OAuth2ServiceFactory() {}

  public static OAuth20Service getService(AuthMethod authMethod) {
    int port = GuiUtils.getUICore().getSystemPreferences().getIntProperty(PORT_PREF_KEY, 0);
    return getService(authMethod, port);
  }

  public static OAuth20Service getService(AuthMethod authMethod, int port) {
    String serviceKey = authMethod.getUid() + ":" + port; // NON-NLS
    return services.computeIfAbsent(serviceKey, uid -> createOAuth20Service(authMethod, port));
  }

  public static AuthProvider buildKeycloakProvider(String name, String baseUrl, String realm) {
    String normalizedUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    String realmUrl = normalizedUrl + "realms/" + realm.trim(); // NON-NLS

    return new AuthProvider(
        name,
        realmUrl + "/protocol/openid-connect/auth", // NON-NLS
        realmUrl + "/protocol/openid-connect/token", // NON-NLS
        realmUrl + "/protocol/openid-connect/revoke", // NON-NLS
        true);
  }

  private static DefaultAuthMethod createNoAuthMethod() {
    return new DefaultAuthMethod(
        NO_AUTH_ID,
        new AuthProvider(Messages.getString("no.authentication"), null, null, null, false),
        AuthRegistration.empty()) {
      @Override
      public OAuth2AccessToken getToken() {
        return null;
      }
    };
  }

  private static DefaultAuthMethod createGoogleAuthTemplate() {
    var provider =
        new AuthProvider(
            "Google Cloud Healthcare", // NON-NLS
            "https://accounts.google.com/o/oauth2/v2/auth",
            "https://oauth2.googleapis.com/token",
            "https://oauth2.googleapis.com/revoke",
            true);
    return new DefaultAuthMethod(
        "2c5dc28c-8fa0-11eb-9321-7fffcd64cef1", // NON-NLS
        provider,
        AuthRegistration.of(
            null,
            null,
            "https://www.googleapis.com/auth/cloud-healthcare https://www.googleapis.com/auth/cloudplatformprojects.readonly",
            null));
  }

  private static DefaultAuthMethod createKeycloakTemplate() {
    return new DefaultAuthMethod(
        "68c845fc-93c5-11eb-b2f8-0f5db063091d", // NON-NLS
        buildKeycloakProvider(
            "Default Keycloak 18+", "http://localhost:8080/", "master"), // NON-NLS
        AuthRegistration.of(null, null, "openid", null)); // NON-NLS
  }

  private static OAuth20Service createOAuth20Service(AuthMethod authMethod, int port) {
    var registration = authMethod.getAuthRegistration();
    var provider = authMethod.getAuthProvider();
    if (registration == null || provider == null) {
      LOGGER.debug(
          "Cannot build OAuth service for '{}': missing registration/provider", authMethod);
      return null;
    }
    int actualPort = port <= 0 ? SocketUtil.findAvailablePort() : port;

    return new ServiceBuilder(registration.clientId())
        .apiSecret(registration.clientSecret())
        .httpClient(new JavaNetHttpClient())
        .defaultScope(registration.scope())
        .callback(CALLBACK_URL + actualPort)
        .responseType(registration.getAuthorizationGrantType())
        .userAgent(System.getProperty("http.agent"))
        .build(new OAuth2Api(provider));
  }

  /** OAuth2 API implementation for custom providers. */
  static final class OAuth2Api extends DefaultApi20 {
    private final AuthProvider provider;

    OAuth2Api(AuthProvider provider) {
      this.provider = provider;
    }

    @Override
    public String getAccessTokenEndpoint() {
      return provider.tokenUri();
    }

    @Override
    public String getAuthorizationBaseUrl() {
      return provider.authorizationUri();
    }

    @Override
    public String getRevokeTokenEndpoint() {
      return provider.revokeTokenUri();
    }

    @Override
    public TokenExtractor<OAuth2AccessToken> getAccessTokenExtractor() {
      return provider.openId()
          ? OpenIdJsonTokenExtractor.instance()
          : OAuth2AccessTokenJsonExtractor.instance();
    }
  }
}
