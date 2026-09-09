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

import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import org.weasis.core.api.net.WebRequest;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.util.StringUtil;

/**
 * OAuth2 client for a single provider/registration pair: builds the authorization URL (with PKCE,
 * RFC 7636) and performs the authorization-code, refresh-token, and client-credentials grants
 * against the token endpoint.
 */
public final class OAuth2Service {

  private static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";
  private static final String PARAM_CLIENT_ID = "client_id"; // NON-NLS
  private static final String PARAM_GRANT_TYPE = "grant_type"; // NON-NLS
  private static final String PARAM_SCOPE = "scope"; // NON-NLS

  private final AuthProvider provider;
  private final AuthRegistration registration;
  private final String callbackUrl;
  private final JavaNetHttpClient httpClient;

  /**
   * @param callbackUrl loopback redirect URI for the authorization-code grant; {@code null} for
   *     client-credentials-only usage
   */
  public OAuth2Service(
      AuthProvider provider,
      AuthRegistration registration,
      String callbackUrl,
      JavaNetHttpClient httpClient) {
    this.provider = Objects.requireNonNull(provider, "provider");
    this.registration = Objects.requireNonNull(registration, "registration");
    this.callbackUrl = callbackUrl;
    this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
  }

  public AuthProvider getProvider() {
    return provider;
  }

  public String getCallbackUrl() {
    return callbackUrl;
  }

  /**
   * @return the port of the loopback callback URL
   * @throws IllegalStateException if no callback URL is configured
   */
  public int getCallbackPort() {
    if (callbackUrl == null) {
      throw new IllegalStateException("No callback URL configured for " + provider.name());
    }
    return Integer.parseInt(callbackUrl.substring(callbackUrl.lastIndexOf(':') + 1));
  }

  /**
   * Builds the authorization endpoint URL for the authorization-code grant.
   *
   * @param additionalParams extra query parameters (e.g. {@code prompt}, {@code audience})
   * @param pkce PKCE challenge to bind the flow to (optional but recommended)
   */
  public String buildAuthorizationUrl(Map<String, String> additionalParams, Pkce pkce) {
    var params = new LinkedHashMap<String, String>();
    params.put("response_type", registration.getAuthorizationGrantType()); // NON-NLS
    if (registration.hasClientId()) {
      params.put(PARAM_CLIENT_ID, registration.clientId());
    }
    if (callbackUrl != null) {
      params.put("redirect_uri", callbackUrl); // NON-NLS
    }
    if (StringUtil.hasText(registration.scope())) {
      params.put(PARAM_SCOPE, registration.scope());
    }
    if (pkce != null) {
      params.put("code_challenge", pkce.codeChallenge()); // NON-NLS
      params.put("code_challenge_method", Pkce.CHALLENGE_METHOD); // NON-NLS
    }
    if (additionalParams != null) {
      params.putAll(additionalParams);
    }
    return provider.authorizationUri() + "?" + encodeForm(params);
  }

  /** Exchanges an authorization code for a token (RFC 6749 §4.1.3). */
  public OAuth2Token exchangeAuthorizationCode(String code, Pkce pkce) throws IOException {
    var params = new LinkedHashMap<String, String>();
    params.put(PARAM_GRANT_TYPE, "authorization_code"); // NON-NLS
    params.put("code", code); // NON-NLS
    if (callbackUrl != null) {
      params.put("redirect_uri", callbackUrl); // NON-NLS
    }
    if (pkce != null) {
      params.put("code_verifier", pkce.codeVerifier()); // NON-NLS
    }
    return requestToken(params);
  }

  /** Obtains a fresh token from a refresh token (RFC 6749 §6). */
  public OAuth2Token refreshAccessToken(String refreshToken) throws IOException {
    var params = new LinkedHashMap<String, String>();
    params.put(PARAM_GRANT_TYPE, "refresh_token"); // NON-NLS
    params.put("refresh_token", refreshToken); // NON-NLS
    if (StringUtil.hasText(registration.scope())) {
      params.put(PARAM_SCOPE, registration.scope());
    }
    return requestToken(params);
  }

  /** Obtains a token with the client-credentials grant (RFC 6749 §4.4). */
  public OAuth2Token getClientCredentialsToken() throws IOException {
    var params = new LinkedHashMap<String, String>();
    params.put(PARAM_GRANT_TYPE, AuthRegistration.CLIENT_CREDENTIALS);
    if (StringUtil.hasText(registration.scope())) {
      params.put(PARAM_SCOPE, registration.scope());
    }
    return requestToken(params);
  }

  /**
   * Confidential clients authenticate with HTTP Basic (RFC 6749 §2.3.1); public clients pass their
   * {@code client_id} in the body.
   */
  private OAuth2Token requestToken(Map<String, String> params) throws IOException {
    var request = new WebRequest(WebRequest.Method.POST, provider.tokenUri());
    request.addHeader("Content-Type", FORM_CONTENT_TYPE);
    request.addHeader("Accept", "application/json"); // NON-NLS
    var userAgent = System.getProperty("http.agent");
    if (StringUtil.hasText(userAgent)) {
      request.addHeader("User-Agent", userAgent); // NON-NLS
    }
    if (registration.hasClientSecret()) {
      var credentials = registration.clientId() + ":" + registration.clientSecret();
      request.addHeader(
          "Authorization", // NON-NLS
          "Basic " // NON-NLS
              + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
    } else if (registration.hasClientId()) {
      params.put(PARAM_CLIENT_ID, registration.clientId());
    }
    request.setBody(encodeForm(params));

    try (var response = httpClient.execute(request)) {
      byte[] body = response.getInputStream().readAllBytes();
      int status = response.getResponseCode();
      if (status < 200 || status >= 300) {
        throw new IOException(
            "Token request to %s failed with status %d: %s"
                .formatted(provider.tokenUri(), status, errorDetails(body)));
      }
      return parseToken(body);
    }
  }

  private static OAuth2Token parseToken(byte[] body) throws IOException {
    JsonObject json;
    try {
      json = JsonUtil.readObject(body);
    } catch (JsonException e) {
      throw new IOException("Malformed token response: " + errorDetails(body), e);
    }
    String accessToken = json.getString("access_token", null); // NON-NLS
    if (!StringUtil.hasText(accessToken)) {
      throw new IOException("Token response contains no access_token: " + errorDetails(body));
    }
    int expiresIn = json.getInt("expires_in", -1); // NON-NLS
    return new OAuth2Token(
        accessToken,
        json.getString("token_type", null), // NON-NLS
        expiresIn < 0 ? null : expiresIn,
        json.getString("refresh_token", null), // NON-NLS
        json.getString("scope", null), // NON-NLS
        json.getString("id_token", null)); // NON-NLS
  }

  /** Extracts RFC 6749 §5.2 error fields when present, else returns a truncated raw body. */
  private static String errorDetails(byte[] body) {
    try {
      JsonObject json = JsonUtil.readObject(body);
      var error = json.getString("error", null); // NON-NLS
      if (error != null) {
        var description = json.getString("error_description", null); // NON-NLS
        return description == null ? error : error + " - " + description;
      }
    } catch (JsonException e) {
      // Not JSON, fall through to the raw body
    }
    var raw = new String(body, StandardCharsets.UTF_8).strip();
    return raw.length() > 300 ? raw.substring(0, 300) + "..." : raw;
  }

  private static String encodeForm(Map<String, String> params) {
    var joiner = new StringJoiner("&");
    params.forEach(
        (k, v) ->
            joiner.add(
                URLEncoder.encode(k, StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(v, StandardCharsets.UTF_8)));
    return joiner.toString();
  }

  /**
   * PKCE code verifier and its S256 challenge (RFC 7636), binding the authorization code to this
   * client instance.
   */
  public record Pkce(String codeVerifier, String codeChallenge) {

    public static final String CHALLENGE_METHOD = "S256"; // NON-NLS

    private static final SecureRandom RANDOM = new SecureRandom();

    public static Pkce generate() {
      byte[] bytes = new byte[32];
      RANDOM.nextBytes(bytes);
      var encoder = Base64.getUrlEncoder().withoutPadding();
      String verifier = encoder.encodeToString(bytes);
      String challenge = encoder.encodeToString(sha256(verifier.getBytes(StandardCharsets.UTF_8)));
      return new Pkce(verifier, challenge);
    }

    private static byte[] sha256(byte[] input) {
      try {
        return MessageDigest.getInstance("SHA-256").digest(input); // NON-NLS
      } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException("SHA-256 unavailable", e);
      }
    }
  }
}
