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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.net.SocketUtil;
import org.weasis.core.util.StringUtil;

/** Factory for creating and caching OAuth2 services with predefined authentication methods. */
public final class OAuth2ServiceFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(OAuth2ServiceFactory.class);

  public static final String CALLBACK_URL = "http://127.0.0.1:";
  public static final String NO_AUTH_ID = "5aa85854-8f1f-11eb-b339-d3daace59a05"; // NOSONAR !secret

  public static final DefaultAuthMethod NO_AUTH = createNoAuthMethod();

  public static final DefaultAuthMethod GOOGLE_AUTH_TEMPLATE = createGoogleAuthTemplate();
  public static final DefaultAuthMethod KEYCLOAK_TEMPLATE = createKeycloakTemplate();

  private static final String PORT_PREF_KEY = "weasis.auth.back.port"; // NON-NLS
  private static final Map<String, OAuth2Service> services = new ConcurrentHashMap<>();

  private OAuth2ServiceFactory() {}

  public static OAuth2Service getService(AuthMethod authMethod) {
    int port = GuiUtils.getUICore().getSystemPreferences().getIntProperty(PORT_PREF_KEY, 0);
    return getService(authMethod, port);
  }

  public static OAuth2Service getService(AuthMethod authMethod, int port) {
    String serviceKey = authMethod.getUid() + ":" + port; // NON-NLS
    return services.computeIfAbsent(serviceKey, uid -> createService(authMethod, port));
  }

  /**
   * Discards the services cached for this method so that the next request is built from the current
   * provider and registration. Must be called whenever an authentication method is edited or
   * removed.
   */
  public static void invalidateService(String uid) {
    if (StringUtil.hasText(uid)) {
      services.keySet().removeIf(key -> key.startsWith(uid + ":"));
    }
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
        NO_AUTH_ID, // NOSONAR !secret
        new AuthProvider(Messages.getString("no.authentication"), null, null, null, false),
        AuthRegistration.empty()) {
      @Override
      public OAuth2Token getToken() {
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

  private static OAuth2Service createService(AuthMethod authMethod, int port) {
    var registration = authMethod.getAuthRegistration();
    var provider = authMethod.getAuthProvider();
    if (registration == null || provider == null) {
      LOGGER.debug(
          "Cannot build OAuth service for '{}': missing registration/provider", authMethod);
      return null;
    }

    String callbackUrl = null;
    if (!registration.isClientCredentialsGrant()) {
      // Authorization-code grant needs a loopback redirect URI for the browser callback.
      int actualPort = port <= 0 ? SocketUtil.findAvailablePort() : port;
      callbackUrl = CALLBACK_URL + actualPort;
    }
    return new OAuth2Service(provider, registration, callbackUrl, new JavaNetHttpClient());
  }
}
