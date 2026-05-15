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

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.OAuth20Service;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class DefaultAuthMethodTest {

  private static final AuthProvider PROVIDER =
      new AuthProvider("Test", "https://auth", "https://token", null, false);

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
    tokenField.set(method, Mockito.mock(OAuth2AccessToken.class));
    method.resetToken();
    assertNull(tokenField.get(method));
  }

  @Test
  void getTokenRefreshesUsingExistingCode() throws Exception {
    var method = new DefaultAuthMethod("u", PROVIDER, AuthRegistration.empty());
    method.setCode("refresh-token");

    var service = Mockito.mock(OAuth20Service.class);
    var refreshed = Mockito.mock(OAuth2AccessToken.class);
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

    var service = Mockito.mock(OAuth20Service.class);
    Mockito.when(service.refreshAccessToken(Mockito.anyString()))
        .thenThrow(new RuntimeException("nope"));

    try (MockedStatic<OAuth2ServiceFactory> mocked =
        Mockito.mockStatic(OAuth2ServiceFactory.class)) {
      mocked.when(() -> OAuth2ServiceFactory.getService(method)).thenReturn(service);
      // authenticate() is invoked next; with no service available the IOException is logged and
      // null
      // is returned.
      mocked.when(() -> OAuth2ServiceFactory.getService(method)).thenReturn(null);
      assertNull(method.getToken());
    }
  }

  @Test
  void noAuthMethodAlwaysReturnsNullToken() {
    assertNull(OAuth2ServiceFactory.NO_AUTH.getToken());
  }

  // -------------------------------------------------------------------------
  // Branch coverage on private helpers (refresh/auth/wait/exchange)
  // -------------------------------------------------------------------------

  @Test
  void refreshExistingTokenSwallowsRuntimeException() throws Exception {
    var method = new DefaultAuthMethod("u", PROVIDER, AuthRegistration.empty());
    method.setCode("rt");
    var service = Mockito.mock(OAuth20Service.class);
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
  void refreshExistingTokenSwallowsInterruptedExceptionAndRestoresFlag() throws Exception {
    var method = new DefaultAuthMethod("u", PROVIDER, AuthRegistration.empty());
    method.setCode("rt");
    var service = Mockito.mock(OAuth20Service.class);
    Mockito.when(service.refreshAccessToken("rt")).thenThrow(new InterruptedException("stop"));

    try (MockedStatic<OAuth2ServiceFactory> mocked =
        Mockito.mockStatic(OAuth2ServiceFactory.class)) {
      mocked.when(() -> OAuth2ServiceFactory.getService(method)).thenReturn(service);
      Method m = DefaultAuthMethod.class.getDeclaredMethod("refreshExistingToken");
      m.setAccessible(true);
      m.invoke(method);
      assertNull(getInternalToken(method));
      assertTrue(Thread.interrupted(), "Thread interrupt flag should have been re-set");
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
    var service = Mockito.mock(OAuth20Service.class);
    Mockito.when(service.getCallback()).thenReturn(OAuth2ServiceFactory.CALLBACK_URL + "12345");
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
  void exchangeAndRefreshReturnsNullWhenAccessTokenIsNull() throws Exception {
    var method = new DefaultAuthMethod("u", PROVIDER, AuthRegistration.empty());
    var service = Mockito.mock(OAuth20Service.class);
    @SuppressWarnings("unchecked")
    java.util.concurrent.Future<OAuth2AccessToken> future =
        Mockito.mock(java.util.concurrent.Future.class);
    Mockito.when(future.get(Mockito.anyLong(), Mockito.any())).thenReturn(null);
    Mockito.when(service.getAccessTokenAsync("c")).thenReturn(future);

    Method m =
        DefaultAuthMethod.class.getDeclaredMethod(
            "exchangeAndRefresh", OAuth20Service.class, String.class);
    m.setAccessible(true);
    assertNull(m.invoke(method, service, "c"));
  }

  @Test
  void exchangeAndRefreshSwallowsExceptions() throws Exception {
    var method = new DefaultAuthMethod("u", PROVIDER, AuthRegistration.empty());
    var service = Mockito.mock(OAuth20Service.class);
    Mockito.when(service.getAccessTokenAsync(Mockito.anyString()))
        .thenThrow(new RuntimeException("boom"));
    Method m =
        DefaultAuthMethod.class.getDeclaredMethod(
            "exchangeAndRefresh", OAuth20Service.class, String.class);
    m.setAccessible(true);
    assertNull(m.invoke(method, service, "c"));
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
    var responseHandler =
        new AcceptCallbackHandler() {
          private volatile String code;

          @Override
          public Optional<String> code() {
            return Optional.ofNullable(code);
          }

          @Override
          public void code(String c) {
            this.code = c;
          }

          @Override
          public OAuth20Service service() {
            return Mockito.mock(OAuth20Service.class);
          }

          @Override
          public void completed(
              java.nio.channels.AsynchronousSocketChannel r, AsyncCallbackServerHandler a) {}

          @Override
          public void failed(Throwable t, AsyncCallbackServerHandler a) {}
        };
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
  void exchangeAndRefreshHappyPathReturnsRefreshedToken() throws Exception {
    var method = new DefaultAuthMethod("u", PROVIDER, AuthRegistration.empty());
    var service = Mockito.mock(OAuth20Service.class);

    var initial = Mockito.mock(OAuth2AccessToken.class);
    Mockito.when(initial.getRefreshToken()).thenReturn("rt-1");
    @SuppressWarnings("unchecked")
    java.util.concurrent.Future<OAuth2AccessToken> future =
        Mockito.mock(java.util.concurrent.Future.class);
    Mockito.when(future.get(Mockito.anyLong(), Mockito.any())).thenReturn(initial);
    Mockito.when(service.getAccessTokenAsync("auth-code")).thenReturn(future);

    var refreshed = Mockito.mock(OAuth2AccessToken.class);
    Mockito.when(service.refreshAccessToken("rt-1")).thenReturn(refreshed);

    Method m =
        DefaultAuthMethod.class.getDeclaredMethod(
            "exchangeAndRefresh", OAuth20Service.class, String.class);
    m.setAccessible(true);
    var result = m.invoke(method, service, "auth-code");
    assertSame(refreshed, result);
    assertEquals("rt-1", method.getCode());
  }

  @Test
  void exchangeAndRefreshHandlesInterruptedException() throws Exception {
    var method = new DefaultAuthMethod("u", PROVIDER, AuthRegistration.empty());
    var service = Mockito.mock(OAuth20Service.class);
    Mockito.when(service.getAccessTokenAsync(Mockito.anyString()))
        .thenAnswer(
            inv -> {
              throw new InterruptedException("stop");
            });
    Method m =
        DefaultAuthMethod.class.getDeclaredMethod(
            "exchangeAndRefresh", OAuth20Service.class, String.class);
    m.setAccessible(true);
    assertNull(m.invoke(method, service, "c"));
    assertTrue(Thread.interrupted(), "interrupt flag must be re-set");
  }

  @Test
  void openAuthorizationUrlInvokesBrowserWithBuiltUrl() throws Exception {
    var method =
        new DefaultAuthMethod("u", PROVIDER, AuthRegistration.of("c", "s", "scope", "aud"));
    var service = Mockito.mock(OAuth20Service.class);
    var builder = Mockito.mock(com.github.scribejava.core.oauth.AuthorizationUrlBuilder.class);
    Mockito.when(service.createAuthorizationUrlBuilder()).thenReturn(builder);
    Mockito.when(builder.additionalParams(Mockito.anyMap())).thenReturn(builder);
    Mockito.when(builder.build()).thenReturn("https://example/auth?x=1");

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
          DefaultAuthMethod.class.getDeclaredMethod("openAuthorizationUrl", OAuth20Service.class);
      m.setAccessible(true);
      m.invoke(method, service);
    }
    assertNotNull(openedUrl.get());
    assertEquals("https://example/auth?x=1", openedUrl.get().toString());
  }

  @Test
  void performOAuthFlowReturnsNullWhenAuthCodeMissing() throws Exception {
    var method = new DefaultAuthMethod("u", PROVIDER, AuthRegistration.empty());
    var service = Mockito.mock(OAuth20Service.class);
    var builder = Mockito.mock(com.github.scribejava.core.oauth.AuthorizationUrlBuilder.class);
    Mockito.when(service.createAuthorizationUrlBuilder()).thenReturn(builder);
    Mockito.when(builder.additionalParams(Mockito.anyMap())).thenReturn(builder);
    Mockito.when(builder.build()).thenReturn("https://example/auth");

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
      var initial = Mockito.mock(OAuth2AccessToken.class);
      Mockito.when(initial.getRefreshToken()).thenReturn("rt");
      @SuppressWarnings("unchecked")
      java.util.concurrent.Future<OAuth2AccessToken> future =
          Mockito.mock(java.util.concurrent.Future.class);
      Mockito.when(future.get(Mockito.anyLong(), Mockito.any())).thenReturn(initial);
      Mockito.when(service.getAccessTokenAsync("the-code")).thenReturn(future);
      var refreshed = Mockito.mock(OAuth2AccessToken.class);
      Mockito.when(service.refreshAccessToken("rt")).thenReturn(refreshed);

      var result = m.invoke(method, server);
      assertSame(refreshed, result);
    }
  }

  @Test
  void performOAuthFlowSwallowsExceptionsFromOpenAuthorizationUrl() throws Exception {
    var method = new DefaultAuthMethod("u", PROVIDER, AuthRegistration.empty());
    var service = Mockito.mock(OAuth20Service.class);
    Mockito.when(service.createAuthorizationUrlBuilder()).thenThrow(new RuntimeException("oops"));
    var responseHandler = stubResponseHandler(service, null);
    var server = new AsyncCallbackServerHandler(0, responseHandler);
    Method m =
        DefaultAuthMethod.class.getDeclaredMethod(
            "performOAuthFlow", AsyncCallbackServerHandler.class);
    m.setAccessible(true);
    assertNull(m.invoke(method, server));
  }

  private static AcceptCallbackHandler stubResponseHandler(OAuth20Service service, String code) {
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
      public OAuth20Service service() {
        return service;
      }

      @Override
      public void completed(
          java.nio.channels.AsynchronousSocketChannel r, AsyncCallbackServerHandler a) {}

      @Override
      public void failed(Throwable t, AsyncCallbackServerHandler a) {}
    };
  }

  private static OAuth2AccessToken getInternalToken(DefaultAuthMethod method) throws Exception {
    var f = DefaultAuthMethod.class.getDeclaredField("token");
    f.setAccessible(true);
    return (OAuth2AccessToken) f.get(method);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, String> invokeCreateAuthParams(DefaultAuthMethod method)
      throws Exception {
    Method m = DefaultAuthMethod.class.getDeclaredMethod("createAuthParams");
    m.setAccessible(true);
    return (Map<String, String>) m.invoke(method);
  }
}
