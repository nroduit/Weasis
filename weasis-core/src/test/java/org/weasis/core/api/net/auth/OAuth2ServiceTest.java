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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.net.auth.OAuth2Service.Pkce;

class OAuth2ServiceTest {

  private static HttpServer server;
  private static String tokenUri;
  private static final AtomicReference<Map<String, String>> LAST_FORM = new AtomicReference<>();
  private static final AtomicReference<String> LAST_AUTHORIZATION = new AtomicReference<>();
  private static final AtomicReference<String> NEXT_RESPONSE = new AtomicReference<>();
  private static final AtomicReference<Integer> NEXT_STATUS = new AtomicReference<>(200);

  @BeforeAll
  static void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/token",
        ex -> {
          LAST_AUTHORIZATION.set(ex.getRequestHeaders().getFirst("Authorization"));
          LAST_FORM.set(parseForm(new String(ex.getRequestBody().readAllBytes())));
          byte[] body = NEXT_RESPONSE.get().getBytes(StandardCharsets.UTF_8);
          ex.getResponseHeaders().add("Content-Type", "application/json");
          ex.sendResponseHeaders(NEXT_STATUS.get(), body.length);
          try (var os = ex.getResponseBody()) {
            os.write(body);
          }
        });
    server.start();
    tokenUri = "http://localhost:" + server.getAddress().getPort() + "/token";
  }

  @AfterAll
  static void stop() {
    server.stop(0);
  }

  private static Map<String, String> parseForm(String body) {
    var map = new HashMap<String, String>();
    for (String pair : body.split("&")) {
      int idx = pair.indexOf('=');
      map.put(
          URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8),
          URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
    }
    return map;
  }

  private static OAuth2Service service(AuthRegistration registration, String callbackUrl) {
    var provider = new AuthProvider("Test", "https://auth.example/authorize", tokenUri, null, true);
    return new OAuth2Service(provider, registration, callbackUrl, new JavaNetHttpClient());
  }

  private static void stubTokenResponse(String json) {
    NEXT_STATUS.set(200);
    NEXT_RESPONSE.set(json);
  }

  @Test
  void buildAuthorizationUrlContainsStandardAndPkceParams() {
    var registration = AuthRegistration.of("my-client", null, "openid profile", null);
    var svc = service(registration, "http://127.0.0.1:4444");
    var pkce = Pkce.generate();
    String url = svc.buildAuthorizationUrl(Map.of("prompt", "consent"), pkce);

    assertTrue(url.startsWith("https://auth.example/authorize?"));
    assertTrue(url.contains("response_type=code"));
    assertTrue(url.contains("client_id=my-client"));
    assertTrue(url.contains("redirect_uri=http%3A%2F%2F127.0.0.1%3A4444"));
    assertTrue(url.contains("scope=openid+profile"));
    assertTrue(url.contains("code_challenge=" + pkce.codeChallenge()));
    assertTrue(url.contains("code_challenge_method=S256"));
    assertTrue(url.contains("prompt=consent"));
  }

  @Test
  void exchangeAuthorizationCodeSendsVerifierAndParsesOpenIdToken() throws Exception {
    stubTokenResponse(
        """
        {"access_token":"AT","token_type":"Bearer","expires_in":3600,
         "refresh_token":"RT","scope":"openid","id_token":"IDT"}""");
    var registration = AuthRegistration.of("my-client", null, "openid", null);
    var svc = service(registration, "http://127.0.0.1:4444");
    var pkce = Pkce.generate();

    OAuth2Token token = svc.exchangeAuthorizationCode("the-code", pkce);
    assertEquals("AT", token.accessToken());
    assertEquals("Bearer", token.tokenType());
    assertEquals(3600, token.expiresIn());
    assertEquals("RT", token.refreshToken());
    assertEquals("IDT", token.idToken());

    var form = LAST_FORM.get();
    assertEquals("authorization_code", form.get("grant_type"));
    assertEquals("the-code", form.get("code"));
    assertEquals("http://127.0.0.1:4444", form.get("redirect_uri"));
    assertEquals(pkce.codeVerifier(), form.get("code_verifier"));
    // Public client: no Basic authentication, client_id in the body instead.
    assertNull(LAST_AUTHORIZATION.get());
    assertEquals("my-client", form.get("client_id"));
  }

  @Test
  void confidentialClientAuthenticatesWithHttpBasic() throws Exception {
    stubTokenResponse("{\"access_token\":\"AT\"}");
    var registration =
        AuthRegistration.of("cid", "cs", null, null, AuthRegistration.CLIENT_CREDENTIALS);
    var svc = service(registration, null);

    OAuth2Token token = svc.getClientCredentialsToken();
    assertEquals("AT", token.accessToken());
    assertNull(token.refreshToken());

    String expected =
        "Basic " + Base64.getEncoder().encodeToString("cid:cs".getBytes(StandardCharsets.UTF_8));
    assertEquals(expected, LAST_AUTHORIZATION.get());
    var form = LAST_FORM.get();
    assertEquals("client_credentials", form.get("grant_type"));
    assertFalse(form.containsKey("client_id"));
  }

  @Test
  void refreshAccessTokenSendsRefreshGrantAndScope() throws Exception {
    stubTokenResponse("{\"access_token\":\"AT2\",\"refresh_token\":\"RT2\"}");
    var registration = AuthRegistration.of("cid", "cs", "openid", null);
    var svc = service(registration, "http://127.0.0.1:4444");

    OAuth2Token token = svc.refreshAccessToken("RT1");
    assertEquals("AT2", token.accessToken());
    assertEquals("RT2", token.refreshToken());

    var form = LAST_FORM.get();
    assertEquals("refresh_token", form.get("grant_type"));
    assertEquals("RT1", form.get("refresh_token"));
    assertEquals("openid", form.get("scope"));
  }

  @Test
  void errorStatusRaisesIoExceptionWithRfcErrorDetails() {
    NEXT_STATUS.set(400);
    NEXT_RESPONSE.set("{\"error\":\"invalid_grant\",\"error_description\":\"expired\"}");
    var svc = service(AuthRegistration.of("cid", "cs", null, null), "http://127.0.0.1:4444");

    IOException ex = assertThrows(IOException.class, () -> svc.refreshAccessToken("RT"));
    assertTrue(ex.getMessage().contains("400"), ex.getMessage());
    assertTrue(ex.getMessage().contains("invalid_grant - expired"), ex.getMessage());
  }

  @Test
  void missingAccessTokenRaisesIoException() {
    stubTokenResponse("{\"token_type\":\"Bearer\"}");
    var svc = service(AuthRegistration.of("cid", "cs", null, null), "http://127.0.0.1:4444");
    IOException ex = assertThrows(IOException.class, () -> svc.refreshAccessToken("RT"));
    assertTrue(ex.getMessage().contains("no access_token"), ex.getMessage());
  }

  @Test
  void callbackPortIsParsedFromCallbackUrl() {
    var svc = service(AuthRegistration.empty(), OAuth2ServiceFactory.CALLBACK_URL + "51234");
    assertEquals(51234, svc.getCallbackPort());
  }

  @Test
  void callbackPortWithoutCallbackUrlThrows() {
    var svc = service(AuthRegistration.empty(), null);
    assertThrows(IllegalStateException.class, svc::getCallbackPort);
  }

  @Test
  void pkceChallengeIsS256OfVerifier() throws Exception {
    var pkce = Pkce.generate();
    byte[] expected =
        MessageDigest.getInstance("SHA-256")
            .digest(pkce.codeVerifier().getBytes(StandardCharsets.UTF_8));
    assertEquals(
        Base64.getUrlEncoder().withoutPadding().encodeToString(expected), pkce.codeChallenge());
    assertNotEquals(Pkce.generate().codeVerifier(), pkce.codeVerifier());
  }
}
