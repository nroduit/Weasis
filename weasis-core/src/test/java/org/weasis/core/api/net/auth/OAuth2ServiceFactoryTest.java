/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.net.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OAuth2ServiceFactoryTest {

  @Test
  void noAuthSentinelExposesExpectedIdentity() {
    assertEquals(OAuth2ServiceFactory.NO_AUTH_ID, OAuth2ServiceFactory.NO_AUTH.getUid());
    assertNull(OAuth2ServiceFactory.NO_AUTH.getToken());
    assertNotNull(OAuth2ServiceFactory.NO_AUTH.getName());
  }

  @Test
  void templatesAreInstantiatedAndStable() {
    assertNotNull(OAuth2ServiceFactory.GOOGLE_AUTH_TEMPLATE);
    assertNotNull(OAuth2ServiceFactory.KEYCLOAK_TEMPLATE);
    assertSame(
        OAuth2ServiceFactory.GOOGLE_AUTH_TEMPLATE, OAuth2ServiceFactory.GOOGLE_AUTH_TEMPLATE);
    assertEquals("Google Cloud Healthcare", OAuth2ServiceFactory.GOOGLE_AUTH_TEMPLATE.getName());
    assertTrue(OAuth2ServiceFactory.GOOGLE_AUTH_TEMPLATE.getAuthProvider().openId());
  }

  @Test
  void buildKeycloakProviderComposesUris() {
    var provider = OAuth2ServiceFactory.buildKeycloakProvider("kc", "https://kc.example/", "demo");
    assertEquals("kc", provider.name());
    assertEquals(
        "https://kc.example/realms/demo/protocol/openid-connect/auth", provider.authorizationUri());
    assertEquals(
        "https://kc.example/realms/demo/protocol/openid-connect/token", provider.tokenUri());
    assertEquals(
        "https://kc.example/realms/demo/protocol/openid-connect/revoke", provider.revokeTokenUri());
    assertTrue(provider.openId());
    assertTrue(provider.supportsTokenRevocation());
  }

  @Test
  void buildKeycloakProviderAddsTrailingSlashIfMissing() {
    var provider = OAuth2ServiceFactory.buildKeycloakProvider("kc", "https://kc.example", "demo");
    assertTrue(provider.authorizationUri().startsWith("https://kc.example/realms/demo"));
  }

  @Test
  void getServiceCachesByUidAndPort() {
    var provider = new AuthProvider("p", "https://a", "https://t", null, false);
    var method =
        new DefaultAuthMethod(
            "uid-cache", provider, AuthRegistration.of("client", "secret", "scope", null));
    int port = 64999;
    var first = OAuth2ServiceFactory.getService(method, port);
    var second = OAuth2ServiceFactory.getService(method, port);
    assertSame(first, second);
  }

  // -------------------------------------------------------------------------
  // Branch coverage: getService(AuthMethod), createOAuth20Service null path,
  // OAuth2Api accessor branches.
  // -------------------------------------------------------------------------

  @Test
  void getServiceSingleArgUsesPreferenceFromUiCore() {
    var provider = new AuthProvider("single-arg-p", "https://a", "https://t", null, false);
    var method =
        new DefaultAuthMethod(
            "uid-single-arg", provider, AuthRegistration.of("client", "secret", "scope", null));
    var uiCore = org.mockito.Mockito.mock(org.weasis.core.api.service.UICore.class);
    var prefs = org.mockito.Mockito.mock(org.weasis.core.api.service.WProperties.class);
    org.mockito.Mockito.when(uiCore.getSystemPreferences()).thenReturn(prefs);
    org.mockito.Mockito.when(
            prefs.getIntProperty(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyInt()))
        .thenReturn(0);
    try (var mocked = org.mockito.Mockito.mockStatic(org.weasis.core.api.gui.util.GuiUtils.class)) {
      mocked.when(org.weasis.core.api.gui.util.GuiUtils::getUICore).thenReturn(uiCore);
      var service = OAuth2ServiceFactory.getService(method);
      assertNotNull(service);
    }
  }

  @Test
  void createOAuth20ServiceReturnsNullWhenAuthRegistrationIsNull() {
    var method =
        new AuthMethod() {
          @Override
          public String getCode() {
            return null;
          }

          @Override
          public String getUid() {
            return "uid-no-reg";
          }

          @Override
          public void resetToken() {}

          @Override
          public com.github.scribejava.core.model.OAuth2AccessToken getToken() {
            return null;
          }

          @Override
          public AuthRegistration getAuthRegistration() {
            return null;
          }

          @Override
          public boolean isLocal() {
            return false;
          }

          @Override
          public void setLocal(boolean local) {}

          @Override
          public AuthProvider getAuthProvider() {
            return new AuthProvider("p", null, null, null, false);
          }
        };
    assertNull(OAuth2ServiceFactory.getService(method, 65010));
  }

  @Test
  void createOAuth20ServiceReturnsNullWhenAuthProviderIsNull() {
    var method =
        new AuthMethod() {
          @Override
          public String getCode() {
            return null;
          }

          @Override
          public String getUid() {
            return "uid-no-provider";
          }

          @Override
          public void resetToken() {}

          @Override
          public com.github.scribejava.core.model.OAuth2AccessToken getToken() {
            return null;
          }

          @Override
          public AuthRegistration getAuthRegistration() {
            return AuthRegistration.empty();
          }

          @Override
          public boolean isLocal() {
            return false;
          }

          @Override
          public void setLocal(boolean local) {}

          @Override
          public AuthProvider getAuthProvider() {
            return null;
          }
        };
    assertNull(OAuth2ServiceFactory.getService(method, 65011));
  }

  @Test
  void createOAuth20ServicePicksAvailablePortWhenZero() {
    var provider = new AuthProvider("p", "https://a", "https://t", null, false);
    var method =
        new DefaultAuthMethod(
            "uid-zero-port", provider, AuthRegistration.of("client", "secret", "scope", null));
    var service = OAuth2ServiceFactory.getService(method, 0);
    assertNotNull(service);
    assertTrue(service.getCallback().startsWith(OAuth2ServiceFactory.CALLBACK_URL));
  }

  @Test
  void oauth2ApiExposesProviderEndpoints() {
    var provider =
        new AuthProvider("p", "https://auth/", "https://token/", "https://revoke/", false);
    var api = new OAuth2ServiceFactory.OAuth2Api(provider);
    assertEquals("https://token/", api.getAccessTokenEndpoint());
    assertEquals("https://auth/", api.getAuthorizationBaseUrl());
    assertEquals("https://revoke/", api.getRevokeTokenEndpoint());
  }

  @Test
  void oauth2ApiAccessTokenExtractorVariesByOpenIdFlag() {
    var openId = new OAuth2ServiceFactory.OAuth2Api(new AuthProvider("p", "a", "t", null, true));
    var classic = new OAuth2ServiceFactory.OAuth2Api(new AuthProvider("p", "a", "t", null, false));
    assertSame(OpenIdJsonTokenExtractor.instance(), openId.getAccessTokenExtractor());
    assertSame(
        com.github.scribejava.core.extractors.OAuth2AccessTokenJsonExtractor.instance(),
        classic.getAccessTokenExtractor());
  }
}
