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

import com.github.scribejava.core.httpclient.multipart.MultipartPayload;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ProxySelector;
import java.net.SocketException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.net.auth.AuthMethod;
import org.weasis.core.api.net.auth.JavaNetHttpClient;
import org.weasis.core.api.net.auth.OAuth2ServiceFactory;
import org.weasis.core.util.StreamIOException;

/** HTTP operations utility with OAuth2 authentication support. */
public final class HttpUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpUtils.class);

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
    return buildHttpClient(Duration.ofMillis(NetworkUtil.getUrlConnectionTimeout()));
  }

  public static HttpClient buildHttpClient(Duration timeout) {
    return buildHttpClient(timeout, HttpClient.Redirect.NORMAL, ProxySelector.getDefault());
  }

  public static HttpClient buildHttpClient(
      Duration timeout, HttpClient.Redirect redirect, ProxySelector proxySelector) {
    return HttpClient.newBuilder()
        .connectTimeout(timeout)
        .followRedirects(redirect)
        .proxy(proxySelector)
        .build();
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
      MultipartPayload multipart = authRequest == null ? null : authRequest.getMultipartPayload();
      if (multipart != null) {
        return sendMultipartRequest(client, url, urlParameters, multipart);
      }
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

  public static AuthResponse executeAuthenticatedRequest(
      OAuthRequest request, URLParameters urlParameters, AuthMethod authMethod) throws IOException {
    applyHeaders(urlParameters.headers(), request::addHeader);
    var service = getOAuth20Service(authMethod);
    service.signRequest(authMethod.getToken(), request);
    return new AuthResponse(runInterruptibly(() -> service.execute(request), "Authentication"));
  }

  static boolean isNoAuthRequired(AuthMethod authMethod) {
    return authMethod == null || OAuth2ServiceFactory.NO_AUTH.equals(authMethod);
  }

  private static HttpStream sendMultipartRequest(
      HttpClient client, String url, URLParameters urlParameters, MultipartPayload multipart)
      throws IOException {
    var builder =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMillis(urlParameters.readTimeout()));
    applyHeaders(urlParameters.headers(), builder::header);
    JavaNetHttpClient.applyMultipart(builder, multipart);
    var request = builder.build();
    var response =
        runInterruptibly(
            () -> client.send(request, HttpResponse.BodyHandlers.ofInputStream()), "Request");
    return new HttpResponseStream(response);
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

    applyHeaders(urlParameters.headers(), requestBuilder::header);

    var request =
        urlParameters.httpPost()
            ? requestBuilder.POST(HttpRequest.BodyPublishers.noBody()).build()
            : requestBuilder.GET().build();

    var response = sendWithStaleConnectionRetry(client, request, bodyHandler);
    validateResponseStatus(response.statusCode());
    return response;
  }

  private static final int STALE_RETRY_ATTEMPTS = 3;
  private static final long STALE_RETRY_BACKOFF_MS = 50;

  /**
   * Sends a request with retries when the failure looks like a stale pooled connection (server
   * closed idle keep-alive sockets; client sends and gets {@code Connection reset} surfaced as
   * {@code "HTTP/1.1 header parser received no bytes"}). Each failed attempt evicts one stale
   * connection from the JDK HttpClient pool, so a small retry budget drains a batch of timed-out
   * sockets. RFC 7230 §6.3.1 explicitly permits retrying idempotent requests on premature close.
   * POST is not retried.
   */
  private static <S> HttpResponse<S> sendWithStaleConnectionRetry(
      HttpClient client, HttpRequest request, HttpResponse.BodyHandler<S> bodyHandler)
      throws IOException {
    IOException last = null;
    for (int attempt = 0; attempt <= STALE_RETRY_ATTEMPTS; attempt++) {
      try {
        return runInterruptibly(() -> client.send(request, bodyHandler), "Request");
      } catch (IOException e) {
        last = e;
        if (!isIdempotent(request) || !isStaleConnectionFailure(e)) {
          throw e;
        }
        if (attempt == STALE_RETRY_ATTEMPTS) {
          LOGGER.error(
              "Stale connection retries exhausted ({} attempts) for {}",
              STALE_RETRY_ATTEMPTS,
              request.uri());
          throw e;
        }
        LOGGER.warn(
            "Stale pooled connection detected for {} (attempt {}/{}, {})",
            request.uri(),
            attempt + 1,
            STALE_RETRY_ATTEMPTS,
            e.getMessage());
        sleepQuietly(STALE_RETRY_BACKOFF_MS * (attempt + 1));
      }
    }
    throw last; // unreachable, loop above always throws or returns
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }

  static boolean isIdempotent(HttpRequest request) {
    var method = request.method();
    return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
  }

  static boolean isStaleConnectionFailure(Throwable t) {
    for (Throwable cur = t; cur != null; cur = cur.getCause()) {
      if (cur instanceof SocketException
          && containsIgnoreCase(cur.getMessage(), "connection reset")) {
        return true;
      }
      if (containsIgnoreCase(cur.getMessage(), "header parser received no bytes")) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsIgnoreCase(String haystack, String needle) {
    return haystack != null && haystack.toLowerCase().contains(needle.toLowerCase());
  }

  /**
   * Applies user-supplied headers followed by the standard application headers (skipping nulls).
   */
  static void applyHeaders(Map<String, String> headers, HeaderSetter setter) {
    headers.forEach((k, v) -> setIfPresent(setter, k, v));
    setIfPresent(setter, "User-Agent", AppProperties.WEASIS_USER_AGENT);
    setIfPresent(setter, "Weasis-User", AppProperties.WEASIS_USER);
  }

  private static void setIfPresent(HeaderSetter setter, String name, String value) {
    if (value != null) {
      setter.set(name, value);
    }
  }

  @FunctionalInterface
  interface HeaderSetter {
    void set(String name, String value);
  }

  private static <T> T runInterruptibly(Callable<T> task, String what) throws IOException {
    try {
      return task.call();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new StreamIOException(what + " interrupted", e);
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new StreamIOException(what + " failed", e);
    }
  }

  private static void validateResponseStatus(int statusCode) throws IOException {
    if (statusCode != HttpURLConnection.HTTP_OK) {
      throw new IOException("HTTP request failed with status code: " + statusCode);
    }
  }

  private static OAuth20Service getOAuth20Service(AuthMethod authMethod) throws IOException {
    var service = OAuth2ServiceFactory.getService(authMethod);
    if (service == null) {
      throw new IOException("Invalid authentication method: " + authMethod);
    }
    return service;
  }
}
