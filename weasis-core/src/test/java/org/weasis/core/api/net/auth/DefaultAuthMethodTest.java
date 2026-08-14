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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.weasis.core.api.net.auth.OAuth2Service.Pkce;

class DefaultAuthMethodTest {

  private static final AuthProvider PROVIDER =
      new AuthProvider("Test", "https://auth", "https://token", null, false);

  private static OAuth2Token token(String accessToken, String refreshToken) {
    return new OAuth2Token(accessToken, "Bearer", 3600, refreshToken, null, null);
  }

  @Test
  void constructorAcceptsExplicitUid() {
    var method = new DefaultAuthMethod("uid-1", PROVIDER, AuthRegistration.empty());
    assertEquals("uid-1", method.getUid());
    assertSame(PROVIDER, method.getAuthProvider());
    assertEquals("Test", method.getName());
    assertEquals("Test", method.toString());
  }

  @Test
  void constructorGeneratesUidWhenBlank() {
    var method = new DefaultAuthMethod("  ", PROVIDER, AuthRegistration.empty());
    assertNotNull(method.getUid());
    assertFalse(method.getUid().isBlank());
  }

  @Test
  void constructorRejectsNullProviderOrRegistration() {
    assertThrows(
        NullPointerException.class,
        () -> new DefaultAuthMethod("u", null, AuthRegistration.empty()));
    assertThrows(NullPointerException.class, () -> new DefaultAuthMethod("u", PROVIDER, null));
  }

  @Test
  void codeAndLocalAccessors() {
    var method = new DefaultAuthMethod("u", PROVIDER, AuthRegistration.empty());
    method.setCode("CODE");
    assertEquals("CODE", method.getCode());
    assertFalse(method.isLocal());
    method.setLocal(true);
    assertTrue(method.isLocal());
  }

  @Test
  void resetTokenClearsCachedToken() throws Exception {
    var method = new DefaultAuthMethod("u", PROVIDER, AuthRegistration.empty());
    var tokenField = DefaultAuthMethod.class.getDeclaredField("token");
    tokenField.setAccessible(true);
    tokenField.set(method, token("at", null));
    method.resetToken();
    assertNull(tokenField.get(method));
  }

  @Test
  void getTokenRefreshesUsingExistingCode() throws Exception {
    var method = new DefaultAuthMethod("u", PROVIDER, AuthRegistration.empty());
    method.setCode("refresh-token");

    var service = Mockito.mock(OAuth2Service.class);
    var refreshed = token("at", "refresh-token");
    Mockito.when(service.refreshAccessToken("refresh-token")).thenReturn(refreshed);

    try (MockedStatic<OAuth2ServiceFactory> mocked =
        Mockito.mockStatic(OAuth2ServiceFactory.class)) {
      mocked.when(() -> OAuth2ServiceFactory.getService(method)).thenReturn(service);
      assertSame(refreshed, method.getToken());
    }
  }

  @Test
  void getTokenSwallowsRefreshFailureAndReturnsNull() throws Exception {
    var method = new DefaultAuthMethod("u", PROVIDER, AuthRegistration.empty());
    method.setCode("refresh-token");

    var service = Mockito.mock(OAuth2Service.class);
    Mockito.when(service.refreshAccessToken(Mockito.anyString()))
        .thenThrow(new RuntimeException("nope"));

    try (MockedStatic<OAuth2ServiceFactory> mocked =
        Mockito.mockStatic(OAuth2ServiceFactory.class)) {
      mocked.when(() -> OAuth2ServiceFactory.getService(method)).thenReturn(service);
      // authenticate() is invoked next; with no service available the IOException is logged and
      // null is returned.
      mocked.when(() -> OAuth2ServiceFactory.getService(method)).thenReturn(null);
      assertNull(method.getToken());
    }
  }

  @Test
  void noAuthMethodAlwaysReturnsNullToken() {
    assertNull(OAuth2ServiceFactory.NO_AUTH.getToken());
  }

  @Test
  void getTokenUsesClientCredentialsGrantWhenRegistrationDeclaresIt() throws Exception {
    var registration =
        AuthRegistration.of("client", "secret", "scope", null, AuthRegistration.CLIENT_CREDENTIALS);
    var method = new DefaultAuthMethod("u", PROVIDER, registration);
    var service = Mockito.mock(OAuth2Service.class);
    var token = token("at", null);
    Mockito.when(service.getClientCredentialsToken()).thenReturn(token);

    try (MockedStatic<OAuth2ServiceFactory> mocked =
        Mockito.mockStatic(OAuth2ServiceFactory.class)) {
      mocked.when(() -> OAuth2ServiceFactory.getService(method)).thenReturn(service);
      assertSame(token, method.getToken());
    }
    // No browser flow should have been triggered.
    Mockito.verify(service, Mockito.never()).buildAuthorizationUrl(Mockito.anyMap(), Mockito.any());
    Mockito.verify(service, Mockito.never()).refreshAccessToken(Mockito.any());
  }

  @Test
  void clientCredentialsGrantSwallowsFailureAndReturnsNull() throws Exception {
    var registration =
        AuthRegistration.of("c", "s", null, null, AuthRegistration.CLIENT_CREDENTIALS);
    var method = new DefaultAuthMethod("u", PROVIDER, registration);
    var service = Mockito.mock(OAuth2Service.class);
    Mockito.when(service.getClientCredentialsToken())
        .thenThrow(new IOException("upstream rejected"));

    try (MockedStatic<OAuth2ServiceFactory> mocked =
        Mockito.mockStatic(OAuth2ServiceFactory.class)) {
      mocked.when(() -> OAuth2ServiceFactory.getService(method)).thenReturn(service);
      assertNull(method.getToken());
    }
  }

  // -------------------------------------------------------------------------
  // Branch coverage on private helpers (refresh/auth/wait/exchange)
  // -------------------------------------------------------------------------

  @Test
  void refreshExistingTokenSwallowsRuntimeException() throws Exception {
    var method = new DefaultAuthMethod("u", PROVIDER, AuthRegistration.empty());
    method.setCode("rt");
    var service = Mockito.mock(OAuth2Service.class);
    Mockito.when(service.refreshAccessToken("rt")).thenThrow(new RuntimeException("boom"));

    try (MockedStatic<OAuth2ServiceFactory> mocked =
        Mockito.mockStatic(OAuth2ServiceFactory.class)) {
      mocked.when(() -> OAuth2ServiceFactory.getService(method)).thenReturn(service);
      // Direct invocation through reflection isolates the helper under test.
      Method m = DefaultAuthMethod.class.getDeclaredMethod("refreshExistingToken");
      m.setAccessible(true);
      m.invoke(method);
      assertNull(getInternalToken(method));
    }
  }

  @Test
  void refreshExistingTokenSwallowsIoException() throws Exception {
    var method = new DefaultAuthMethod("u", PROVIDER, AuthRegistration.empty());
    method.setCode("rt");
    var service = Mockito.mock(OAuth2Service.class);
    Mockito.when(service.refreshAccessToken("rt")).thenThrow(new IOException("stop"));

    try (MockedStatic<OAuth2ServiceFactory> mocked =
        Mockito.mockStatic(OAuth2ServiceFactory.class)) {
      mocked.when(() -> OAuth2ServiceFactory.getService(method)).thenReturn(service);
      Method m = DefaultAuthMethod.class.getDeclaredMethod("refreshExistingToken");
      m.setAccessible(true);
      m.invoke(method);
      assertNull(getInternalToken(method));
    }
  }

  @Test
  void refreshExistingTokenIsNoOpWhenServiceMissing() throws Exception {
    var method = new DefaultAuthMethod("u", PROVIDER, AuthRegistration.empty());
    method.setCode("rt");
    try (MockedStatic<OAuth2ServiceFactory> mocked =
        Mockito.mockStatic(OAuth2ServiceFactory.class)) {
      mocked.when(() -> OAuth2ServiceFactory.getService(method)).thenReturn(null);
      Method m = DefaultAuthMethod.class.getDeclaredMethod("refreshExistingToken");
      m.setAccessible(true);
      m.invoke(method);
      assertNull(getInternalToken(method));
    }
  }

  @Test
  void createAuthParamsIncludesAudienceWhenProvided() throws Exception {
    var method =
        new DefaultAuthMethod(
            "u", PROVIDER, AuthRegistration.of("c", "s", "scope", "the-audience"));
    Map<String, String> params = invokeCreateAuthParams(method);
    assertEquals("the-audience", params.get("audience"));
    assertEquals("offline", params.get("access_type"));
    assertEquals("consent", params.get("prompt"));
  }

  @Test
  void createAuthParamsOmitsAudienceWhenBlank() throws Exception {
    var method = new DefaultAuthMethod("u", PROVIDER, AuthRegistration.empty());
    Map<String, String> params = invokeCreateAuthParams(method);
    assertFalse(params.containsKey("audience"));
    assertEquals(2, params.size());
  }

  @Test
  void createCallbackHandlerThrowsWhenServiceMissing() throws Exception {
    var method = new DefaultAuthMethod("u", PROVIDER, AuthRegistration.empty());
    try (MockedStatic<OAuth2ServiceFactory> mocked =
        Mockito.mockStatic(OAuth2ServiceFactory.class)) {
      mocked.when(() -> OAuth2ServiceFactory.getService(method)).thenReturn(null);
      Method m = DefaultAuthMethod.class.getDeclaredMethod("createCallbackHandler");
      m.setAccessible(true);
      var ex =
          assertThrows(java.lang.reflect.InvocationTargetException.class, () -> m.invoke(method));
      assertTrue(ex.getCause() instanceof IOException);
    }
  }

  @Test
  void createCallbackHandlerExtractsPortFromCallbackUrl() throws Exception {
    var method = new DefaultAuthMethod("u", PROVIDER, AuthRegistration.empty());
    var service =
        new OAuth2Service(
            PROVIDER,
            AuthRegistration.empty(),
            OAuth2ServiceFactory.CALLBACK_URL + "12345",
            new JavaNetHttpClient());
    try (MockedStatic<OAuth2ServiceFactory> mocked =
        Mockito.mockStatic(OAuth2ServiceFactory.class)) {
      mocked.when(() -> OAuth2ServiceFactory.getService(method)).thenReturn(service);
      Method m = DefaultAuthMethod.class.getDeclaredMethod("createCallbackHandler");
      m.setAccessible(true);
      Object handler = m.invoke(method);
      assertNotNull(handler);
      assertEquals(12345, ((AsyncCallbackServerHandler) handler).getPort());
    }
  }

  @Test
  void exchangeAndRefreshSwallowsExceptions() throws Exception {
    var method = new DefaultAuthMethod("u", PROVIDER, AuthRegistration.empty());
    var service = Mockito.mock(OAuth2Service.class);
    Mockito.when(service.exchangeAuthorizationCode(Mockito.anyString(), Mockito.any()))
        .thenThrow(new RuntimeException("boom"));
    assertNull(invokeExchangeAndRefresh(method, service, "c", Pkce.generate()));
  }

  @Test
  void shutdownServerSwallowsCloseFailure() throws Exception {
    var server = Mockito.mock(AsyncCallbackServerHandler.class);
    Mockito.doThrow(new RuntimeException("nope")).when(server).close();
    Method m =
        DefaultAuthMethod.class.getDeclaredMethod(
            "shutdownServer", AsyncCallbackServerHandler.class);
    m.setAccessible(true);
    m.invoke(null, server); // must not throw
    m.invoke(null, (Object) null); // null path is safe
  }

  @Test
  void waitForAuthorizationCodeReturnsCodeWhenHandlerProvidesOne() throws Exception {
    var responseHandler = stubResponseHandler(null, null);
    var server = new AsyncCallbackServerHandler(0, responseHandler);
    // pre-populate the code so the loop returns immediately
    responseHandler.code("auth-code");
    Method m =
        DefaultAuthMethod.class.getDeclaredMethod(
            "waitForAuthorizationCode", AsyncCallbackServerHandler.class);
    m.setAccessible(true);
    @SuppressWarnings("unchecked")
    Optional<String> result = (Optional<String>) m.invoke(null, server);
    assertEquals("auth-code", result.orElseThrow());
  }

  // -------------------------------------------------------------------------
  // performOAuthFlow / openAuthorizationUrl / exchangeAndRefresh full path
  // -------------------------------------------------------------------------

  @Test
  void exchangeAndRefreshHappyPathReturnsAccessTokenAndStoresRefreshToken() throws Exception {
    var method = new DefaultAuthMethod("u", PROVIDER, AuthRegistration.empty());
    var service = Mockito.mock(OAuth2Service.class);
    var initial = token("at", "rt-1");
    var pkce = Pkce.generate();
    Mockito.when(service.exchangeAuthorizationCode("auth-code", pkce)).thenReturn(initial);

    var result = invokeExchangeAndRefresh(method, service, "auth-code", pkce);
    assertSame(initial, result);
    assertEquals("rt-1", method.getCode());
    Mockito.verify(service, Mockito.never()).refreshAccessToken(Mockito.any());
  }

  @Test
  void exchangeAndRefreshReturnsAccessTokenWhenNoRefreshTokenReturned() throws Exception {
    // Google occasionally omits refresh_token (e.g. when the user has already granted consent).
    // The flow must succeed using the access token directly.
    var method = new DefaultAuthMethod("u", PROVIDER, AuthRegistration.empty());
    var service = Mockito.mock(OAuth2Service.class);
    var initial = token("at", null);
    var pkce = Pkce.generate();
    Mockito.when(service.exchangeAuthorizationCode("auth-code", pkce)).thenReturn(initial);

    var result = invokeExchangeAndRefresh(method, service, "auth-code", pkce);
    assertSame(initial, result);
    assertNull(method.getCode());
    Mockito.verify(service, Mockito.never()).refreshAccessToken(Mockito.any());
  }

  @Test
  void openAuthorizationUrlInvokesBrowserWithBuiltUrl() throws Exception {
    var method =
        new DefaultAuthMethod("u", PROVIDER, AuthRegistration.of("c", "s", "scope", "aud"));
    var service = Mockito.mock(OAuth2Service.class);
    Mockito.when(service.buildAuthorizationUrl(Mockito.anyMap(), Mockito.any()))
        .thenReturn("https://example/auth?x=1");

    AtomicReference<java.net.URL> openedUrl = new AtomicReference<>();
    try (MockedStatic<org.weasis.core.api.gui.util.GuiUtils> mocked =
        Mockito.mockStatic(org.weasis.core.api.gui.util.GuiUtils.class)) {
      mocked
          .when(
              () ->
                  org.weasis.core.api.gui.util.GuiUtils.openInDefaultBrowser(
                      Mockito.any(), Mockito.any(java.net.URL.class)))
          .thenAnswer(
              inv -> {
                openedUrl.set(inv.getArgument(1));
                return null;
              });

      Method m =
          DefaultAuthMethod.class.getDeclaredMethod(
              "openAuthorizationUrl", OAuth2Service.class, Pkce.class);
      m.setAccessible(true);
      m.invoke(method, service, Pkce.generate());
    }
    assertNotNull(openedUrl.get());
    assertEquals("https://example/auth?x=1", openedUrl.get().toString());
  }

  @Test
  void performOAuthFlowExchangesCodeProvidedByCallbackHandler() throws Exception {
    var method = new DefaultAuthMethod("u", PROVIDER, AuthRegistration.empty());
    var service = Mockito.mock(OAuth2Service.class);
    Mockito.when(service.buildAuthorizationUrl(Mockito.anyMap(), Mockito.any()))
        .thenReturn("https://example/auth");

    var responseHandler = stubResponseHandler(service, null);
    var server = new AsyncCallbackServerHandler(0, responseHandler);

    try (MockedStatic<org.weasis.core.api.gui.util.GuiUtils> mocked =
        Mockito.mockStatic(org.weasis.core.api.gui.util.GuiUtils.class)) {
      mocked
          .when(
              () ->
                  org.weasis.core.api.gui.util.GuiUtils.openInDefaultBrowser(
                      Mockito.any(), Mockito.any(java.net.URL.class)))
          .thenAnswer(inv -> null);
      Method m =
          DefaultAuthMethod.class.getDeclaredMethod(
              "performOAuthFlow", AsyncCallbackServerHandler.class);
      m.setAccessible(true);
      // Make handler return code immediately so the polling loop exits on the first iteration.
      responseHandler.code("the-code");
      var initial = token("at", "rt");
      Mockito.when(service.exchangeAuthorizationCode(Mockito.eq("the-code"), Mockito.any()))
          .thenReturn(initial);

      var result = m.invoke(method, server);
      assertSame(initial, result);
    }
  }

  @Test
  void performOAuthFlowSwallowsExceptionsFromOpenAuthorizationUrl() throws Exception {
    var method = new DefaultAuthMethod("u", PROVIDER, AuthRegistration.empty());
    var service = Mockito.mock(OAuth2Service.class);
    Mockito.when(service.buildAuthorizationUrl(Mockito.anyMap(), Mockito.any()))
        .thenThrow(new RuntimeException("oops"));
    var responseHandler = stubResponseHandler(service, null);
    var server = new AsyncCallbackServerHandler(0, responseHandler);
    Method m =
        DefaultAuthMethod.class.getDeclaredMethod(
            "performOAuthFlow", AsyncCallbackServerHandler.class);
    m.setAccessible(true);
    assertNull(m.invoke(method, server));
  }

  private static AcceptCallbackHandler stubResponseHandler(OAuth2Service service, String code) {
    return new AcceptCallbackHandler() {
      private volatile String c = code;

      @Override
      public Optional<String> code() {
        return Optional.ofNullable(c);
      }

      @Override
      public void code(String value) {
        this.c = value;
      }

      @Override
      public OAuth2Service service() {
        return service;
      }

      @Override
      public void completed(
          java.nio.channels.AsynchronousSocketChannel r, AsyncCallbackServerHandler a) {}

      @Override
      public void failed(Throwable t, AsyncCallbackServerHandler a) {}
    };
  }

  private static OAuth2Token getInternalToken(DefaultAuthMethod method) throws Exception {
    var f = DefaultAuthMethod.class.getDeclaredField("token");
    f.setAccessible(true);
    return (OAuth2Token) f.get(method);
  }

  private static Object invokeExchangeAndRefresh(
      DefaultAuthMethod method, OAuth2Service service, String code, Pkce pkce) throws Exception {
    Method m =
        DefaultAuthMethod.class.getDeclaredMethod(
            "exchangeAndRefresh", OAuth2Service.class, String.class, Pkce.class);
    m.setAccessible(true);
    return m.invoke(method, service, code, pkce);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, String> invokeCreateAuthParams(DefaultAuthMethod method)
      throws Exception {
    Method m = DefaultAuthMethod.class.getDeclaredMethod("createAuthParams");
    m.setAccessible(true);
    return (Map<String, String>) m.invoke(method);
  }
}
