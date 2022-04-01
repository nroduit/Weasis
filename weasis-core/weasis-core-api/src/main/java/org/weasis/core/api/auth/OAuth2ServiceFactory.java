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

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.extractors.OAuth2AccessTokenJsonExtractor;
import com.github.scribejava.core.extractors.TokenExtractor;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.OAuth20Service;
import java.util.HashMap;
import java.util.Map;
import org.weasis.core.api.Messages;
import org.weasis.core.api.util.SocketUtil;

public class OAuth2ServiceFactory {
  static final String CALLBACK_URL = "http://127.0.0.1:";
  public static final String NO = "5aa85854-8f1f-11eb-b339-d3daace59a05"; // NON-NLS
  public static final DefaultAuthMethod noAuth =
      new DefaultAuthMethod(
          NO,
          new AuthProvider(Messages.getString("no.authentication"), null, null, null, false),
          new AuthRegistration(null, null, null)) {
        @Override
        public OAuth2AccessToken getToken() {
          return null;
        }
      };
  private static final AuthProvider googleProvider =
      new AuthProvider(
          "Google Cloud Healthcare", // NON-NLS
          "https://accounts.google.com/o/oauth2/v2/auth",
          "https://oauth2.googleapis.com/token",
          "https://oauth2.googleapis.com/revoke",
          true);
  public static final DefaultAuthMethod googleAuthTemplate =
      new DefaultAuthMethod(
          "2c5dc28c-8fa0-11eb-9321-7fffcd64cef1", // NON-NLS
          googleProvider,
          new AuthRegistration(
              null,
              null,
              "https://www.googleapis.com/auth/cloud-healthcare https://www.googleapis.com/auth/cloudplatformprojects.readonly"));
  public static final DefaultAuthMethod keycloackTemplate =
      new DefaultAuthMethod(
          "68c845fc-93c5-11eb-b2f8-0f5db063091d", // NON-NLS
          buildKeycloackProvider(
              "Default Keycloack", "http://localhost:8080/", "master"), // NON-NLS
          new AuthRegistration(null, null, "openid")); // NON-NLS

  private static final Map<String, OAuth20Service> services = new HashMap<>();

  private OAuth2ServiceFactory() {}

  public static AuthProvider buildKeycloackProvider(String name, String baseUrl, String realm) {
    String baseUrlWithRealm =
        baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "auth/realms/" + realm.trim(); // NON-NLS
    return new AuthProvider(
        name,
        baseUrlWithRealm + "/protocol/openid-connect/auth", // NON-NLS
        baseUrlWithRealm + "/protocol/openid-connect/token", // NON-NLS
        baseUrlWithRealm + "/protocol/openid-connect/revoke", // NON-NLS
        true);
  }

  public static OAuth20Service getService(AuthMethod authMethod) {
    if (services.containsKey(authMethod.getUid())) {
      return services.get(authMethod.getUid());
    }

    AuthRegistration registration = authMethod.getAuthRegistration();
    AuthProvider provider = authMethod.getAuthProvider();
    if (registration == null || provider == null) {
      return null;
    }
    int port = SocketUtil.findAvailablePort();
    OAuth20Service oAuth20Service =
        new ServiceBuilder(registration.getClientId())
            .apiSecret(registration.getClientSecret())
            .httpClient(new BasicHttpClient())
            .defaultScope(registration.getScope())
            .callback(CALLBACK_URL + port)
            .responseType(registration.getAuthorizationGrantType())
            .userAgent(System.getProperty("http.agent"))
            .build(new OAuth2Api(provider));

    services.put(authMethod.getUid(), oAuth20Service);
    return oAuth20Service;
  }

  static class OAuth2Api extends DefaultApi20 {
    private final AuthProvider provider;

    OAuth2Api(AuthProvider provider) {
      this.provider = provider;
    }

    @Override
    public String getAccessTokenEndpoint() {
      return provider.getTokenUri();
    }

    @Override
    public String getAuthorizationBaseUrl() {
      return provider.getAuthorizationUri();
    }

    @Override
    public String getRevokeTokenEndpoint() {
      return provider.getRevokeTokenUri();
    }

    @Override
    public TokenExtractor<OAuth2AccessToken> getAccessTokenExtractor() {
      if (provider.getOpenId()) {
        return OpenIdJsonTokenExtractor.instance();
      }
      return OAuth2AccessTokenJsonExtractor.instance();
    }
  }
}
