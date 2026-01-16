/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.net;

import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.net.auth.AuthMethod;
import org.weasis.core.api.net.auth.OAuth2ServiceFactory;
import org.weasis.core.util.StreamIOException;

/** HTTP operations utility with OAuth2 authentication support. */
public final class HttpUtils {

  private static final int HTTP_OK = 200;
  // Use a shared client to enable connection pooling (Keep-Alive) and avoid handshake overhead
  private static final HttpClient SHARED_CLIENT = buildHttpClient();

  private HttpUtils() {}

  public static <S> HttpResponse<S> getHttpConnection(
      URL url, URLParameters urlParameters, HttpResponse.BodyHandler<S> bodyHandler)
      throws IOException {
    return createHttpRequest(SHARED_CLIENT, url.toString(), urlParameters, bodyHandler);
  }

  public static <S> HttpResponse<S> getHttpConnection(
      HttpClient client,
      URL url,
      URLParameters urlParameters,
      HttpResponse.BodyHandler<S> bodyHandler)
      throws IOException {
    return createHttpRequest(client, url.toString(), urlParameters, bodyHandler);
  }

  public static HttpClient getDefaulttHttpClient() {
    return SHARED_CLIENT;
  }

  public static HttpClient buildHttpClient() {
    return createHttpClient(
        Duration.ofMillis(NetworkUtil.getUrlConnectionTimeout()),
        HttpClient.Redirect.NORMAL,
        ProxySelector.getDefault());
  }

  public static HttpClient buildHttpClient(Duration timeout) {
    return createHttpClient(timeout, HttpClient.Redirect.NORMAL, ProxySelector.getDefault());
  }

  public static HttpClient buildHttpClient(
      Duration timeout, HttpClient.Redirect redirect, ProxySelector proxySelector) {
    return createHttpClient(timeout, redirect, proxySelector);
  }

  public static HttpStream getHttpResponse(
      String url, URLParameters urlParameters, AuthMethod authMethod) throws IOException {
    return getHttpResponse(SHARED_CLIENT, url, urlParameters, authMethod, null);
  }

  public static HttpStream getHttpResponse(
      HttpClient client, String url, URLParameters urlParameters, AuthMethod authMethod)
      throws IOException {
    return getHttpResponse(client, url, urlParameters, authMethod, null);
  }

  public static HttpStream getHttpResponse(
      String url, URLParameters urlParameters, AuthMethod authMethod, OAuthRequest authRequest)
      throws IOException {
    return getHttpResponse(SHARED_CLIENT, url, urlParameters, authMethod, authRequest);
  }

  public static HttpStream getHttpResponse(
      HttpClient client,
      String url,
      URLParameters urlParameters,
      AuthMethod authMethod,
      OAuthRequest authRequest)
      throws IOException {
    if (isNoAuthRequired(authMethod)) {
      var response =
          createHttpRequest(client, url, urlParameters, HttpResponse.BodyHandlers.ofInputStream());
      return new HttpResponseStream(response);
    }
    var request =
        Objects.requireNonNullElseGet(
            authRequest,
            () -> new OAuthRequest(urlParameters.httpPost() ? Verb.POST : Verb.GET, url));
    return executeAuthenticatedRequest(request, urlParameters, authMethod);
  }

  private static boolean isNoAuthRequired(AuthMethod authMethod) {
    return authMethod == null || OAuth2ServiceFactory.NO_AUTH.equals(authMethod);
  }

  private static HttpClient createHttpClient(
      Duration timeout, HttpClient.Redirect redirect, ProxySelector proxySelector) {
    return HttpClient.newBuilder()
        .connectTimeout(timeout)
        .followRedirects(redirect)
        .proxy(proxySelector)
        .build();
  }

  private static <S> HttpResponse<S> createHttpRequest(
      String url, URLParameters urlParameters, HttpResponse.BodyHandler<S> bodyHandler)
      throws IOException {
    return createHttpRequest(SHARED_CLIENT, url, urlParameters, bodyHandler);
  }

  private static <S> HttpResponse<S> createHttpRequest(
      HttpClient client,
      String url,
      URLParameters urlParameters,
      HttpResponse.BodyHandler<S> bodyHandler)
      throws IOException {
    var requestBuilder =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMillis(urlParameters.readTimeout()));

    addHeaders(requestBuilder, urlParameters.headers());
    addApplicationHeaders(requestBuilder);

    var request =
        urlParameters.httpPost()
            ? requestBuilder.POST(HttpRequest.BodyPublishers.noBody()).build()
            : requestBuilder.GET().build();

    return executeRequest(client, request, bodyHandler);
  }

  private static void addHeaders(HttpRequest.Builder requestBuilder, Map<String, String> headers) {
    headers.forEach(requestBuilder::header);
  }

  private static void addApplicationHeaders(HttpRequest.Builder requestBuilder) {
    requestBuilder.header("User-Agent", AppProperties.WEASIS_USER_AGENT);
    requestBuilder.header("Weasis-User", AppProperties.WEASIS_USER);
  }

  private static <S> HttpResponse<S> executeRequest(
      HttpClient client, HttpRequest request, HttpResponse.BodyHandler<S> bodyHandler)
      throws IOException {
    try {
      var response = client.send(request, bodyHandler);
      validateResponseStatus(response.statusCode());
      return response;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Request interrupted", e);
    }
  }

  private static void validateResponseStatus(int statusCode) throws IOException {
    if (statusCode != HTTP_OK) {
      throw new IOException("HTTP request failed with status code: " + statusCode);
    }
  }

  public static AuthResponse executeAuthenticatedRequest(
      OAuthRequest request, URLParameters urlParameters, AuthMethod authMethod) throws IOException {
    addOAuthHeaders(request, urlParameters.headers());

    try {
      var service = getOAuth20Service(authMethod);
      service.signRequest(authMethod.getToken(), request);
      return new AuthResponse(service.execute(request));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new StreamIOException("Authentication request interrupted", e);
    } catch (Exception e) {
      throw new StreamIOException("Authentication failed", e);
    }
  }

  private static void addOAuthHeaders(OAuthRequest request, Map<String, String> headers) {
    headers.forEach(request::addHeader);
    request.addHeader("User-Agent", AppProperties.WEASIS_USER_AGENT);
    request.addHeader("Weasis-User", AppProperties.WEASIS_USER);
  }

  private static OAuth20Service getOAuth20Service(AuthMethod authMethod) throws IOException {
    var service = OAuth2ServiceFactory.getService(authMethod);
    if (service == null) {
      throw new IOException("Invalid authentication method: " + authMethod);
    }
    return service;
  }
}
