/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.net.auth;

import com.github.scribejava.core.httpclient.multipart.BodyPartPayload;
import com.github.scribejava.core.httpclient.multipart.ByteArrayBodyPartPayload;
import com.github.scribejava.core.httpclient.multipart.MultipartPayload;
import com.github.scribejava.core.model.OAuthAsyncRequestCallback;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.net.HttpUtils;
import org.weasis.core.util.StringUtil;

/** HTTP client implementation using Java's {@link HttpClient} for ScribeJava OAuth integration. */
public class JavaNetHttpClient implements com.github.scribejava.core.httpclient.HttpClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(JavaNetHttpClient.class);

  private static final String CRLF = "\r\n";
  private static final String BOUNDARY_PREFIX = "--";
  private static final String HEADER_USER_AGENT = "User-Agent";
  private static final String HEADER_CONTENT_TYPE = "Content-Type";
  private static final String MULTIPART_CT_PREFIX = "multipart/form-data; boundary=";

  private final HttpClient sharedClient;
  private final Duration readTimeout;

  public JavaNetHttpClient() {
    this(new JavaNetHttpClientConfig());
  }

  public JavaNetHttpClient(JavaNetHttpClientConfig config) {
    this.sharedClient =
        HttpUtils.buildHttpClient(
            Duration.ofMillis(config.getConnectTimeout()),
            HttpClient.Redirect.NORMAL,
            config.getProxy());
    this.readTimeout = Duration.ofMillis(config.getReadTimeout());
  }

  @Override
  public void close() {
    // HttpClient is managed externally, no cleanup required
  }

  @Override
  public <T> Future<T> executeAsync(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      byte[] bodyContents,
      OAuthAsyncRequestCallback<T> callback,
      OAuthRequest.ResponseConverter<T> converter) {
    return doExecuteAsync(
        userAgent, headers, httpVerb, completeUrl, bodyContents, callback, converter);
  }

  @Override
  public <T> Future<T> executeAsync(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      MultipartPayload bodyContents,
      OAuthAsyncRequestCallback<T> callback,
      OAuthRequest.ResponseConverter<T> converter) {
    return doExecuteAsync(
        userAgent, headers, httpVerb, completeUrl, bodyContents, callback, converter);
  }

  @Override
  public <T> Future<T> executeAsync(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      String bodyContents,
      OAuthAsyncRequestCallback<T> callback,
      OAuthRequest.ResponseConverter<T> converter) {
    return doExecuteAsync(
        userAgent, headers, httpVerb, completeUrl, bodyContents, callback, converter);
  }

  @Override
  public <T> Future<T> executeAsync(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      File bodyContents,
      OAuthAsyncRequestCallback<T> callback,
      OAuthRequest.ResponseConverter<T> converter) {
    return doExecuteAsync(
        userAgent, headers, httpVerb, completeUrl, bodyContents.toPath(), callback, converter);
  }

  @Override
  public Response execute(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      byte[] bodyContents)
      throws InterruptedException, ExecutionException, IOException {
    return doExecute(userAgent, headers, httpVerb, completeUrl, bodyContents);
  }

  @Override
  public Response execute(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      MultipartPayload multipartPayloads)
      throws InterruptedException, ExecutionException, IOException {
    return doExecute(userAgent, headers, httpVerb, completeUrl, multipartPayloads);
  }

  @Override
  public Response execute(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      String bodyContents)
      throws InterruptedException, ExecutionException, IOException {
    return doExecute(userAgent, headers, httpVerb, completeUrl, bodyContents);
  }

  @Override
  public Response execute(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      File bodyContents)
      throws InterruptedException, ExecutionException, IOException {
    return doExecute(userAgent, headers, httpVerb, completeUrl, bodyContents.toPath());
  }

  private <T> CompletableFuture<T> doExecuteAsync(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      Object bodyContents,
      OAuthAsyncRequestCallback<T> callback,
      OAuthRequest.ResponseConverter<T> converter) {

    var request =
        createRequestBuilder(userAgent, headers, httpVerb, completeUrl, bodyContents, readTimeout)
            .build();

    return sharedClient
        .sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
        .thenApply(httpResponse -> processAsyncResponse(httpResponse, callback, converter))
        .exceptionally(
            throwable -> {
              if (callback != null) {
                callback.onThrowable(throwable);
              }
              return null;
            });
  }

  private Response doExecute(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      Object bodyContents)
      throws IOException {

    var request =
        createRequestBuilder(userAgent, headers, httpVerb, completeUrl, bodyContents, readTimeout)
            .build();

    try {
      return toResponse(sharedClient.send(request, HttpResponse.BodyHandlers.ofInputStream()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Request interrupted", e);
    } catch (HttpTimeoutException e) {
      throw new IOException("Request timed out after " + readTimeout.toMillis() + "ms", e);
    }
  }

  private static Response toResponse(HttpResponse<InputStream> httpResponse) {
    return new Response(
        httpResponse.statusCode(),
        httpResponse.version().toString(),
        parseHeaders(httpResponse),
        httpResponse.body());
  }

  private <T> T processAsyncResponse(
      HttpResponse<InputStream> httpResponse,
      OAuthAsyncRequestCallback<T> callback,
      OAuthRequest.ResponseConverter<T> converter) {
    try {
      @SuppressWarnings("unchecked")
      T result = converter == null ? (T) httpResponse : converter.convert(toResponse(httpResponse));
      if (callback != null) {
        callback.onCompleted(result);
      }
      return result;
    } catch (IOException e) {
      LOGGER.warn("Failed to process async HTTP response", e);
      if (callback != null) {
        callback.onThrowable(e);
      }
      return null;
    }
  }

  private static HttpRequest.Builder createRequestBuilder(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      Object bodyContents,
      Duration readTimeout) {
    var requestBuilder = HttpRequest.newBuilder(URI.create(completeUrl)).timeout(readTimeout);
    if (StringUtil.hasText(userAgent)) {
      requestBuilder.setHeader(HEADER_USER_AGENT, userAgent);
    }
    if (headers != null) {
      headers.forEach(requestBuilder::setHeader);
    }
    setBody(requestBuilder, bodyContents, httpVerb);
    return requestBuilder;
  }

  public static Map<String, String> parseHeaders(HttpResponse<?> response) {
    return response.headers().map().entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                e -> String.join(", ", e.getValue()),
                (existing, replacement) -> existing));
  }

  private static void setBody(
      HttpRequest.Builder requestBuilder, Object bodyContents, Verb httpVerb) {
    if (!httpVerb.isPermitBody()) {
      requestBuilder.method(httpVerb.name(), HttpRequest.BodyPublishers.noBody());
      return;
    }

    switch (bodyContents) {
      case null -> {
        if (httpVerb.isRequiresBody()) {
          throw new IllegalArgumentException("Body content is required but null");
        }
        requestBuilder.method(httpVerb.name(), HttpRequest.BodyPublishers.noBody());
      }
      case byte[] bytes ->
          requestBuilder.method(httpVerb.name(), HttpRequest.BodyPublishers.ofByteArray(bytes));
      case String str ->
          requestBuilder.method(
              httpVerb.name(),
              HttpRequest.BodyPublishers.ofByteArray(str.getBytes(StandardCharsets.UTF_8)));
      case Path path ->
          requestBuilder.method(httpVerb.name(), MultipartEncoder.filePublisher(path));
      case MultipartPayload multi -> MultipartEncoder.applyTo(requestBuilder, multi, httpVerb);
      default ->
          throw new IllegalArgumentException("Unsupported body type: " + bodyContents.getClass());
    }
  }

  /** Encodes a {@link MultipartPayload} into a single byte-array body. */
  private static final class MultipartEncoder {

    private MultipartEncoder() {}

    static HttpRequest.BodyPublisher filePublisher(Path path) {
      try {
        return HttpRequest.BodyPublishers.ofFile(path);
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to read file: " + path, e);
      }
    }

    static void applyTo(HttpRequest.Builder requestBuilder, MultipartPayload payload, Verb verb) {
      var parts = new ArrayList<BodySupplier<InputStream>>();
      collectParts(parts, payload);
      try {
        byte[] body = serialize(parts);
        requestBuilder.setHeader(HEADER_CONTENT_TYPE, MULTIPART_CT_PREFIX + payload.getBoundary());
        requestBuilder.method(verb.name(), HttpRequest.BodyPublishers.ofByteArray(body));
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to prepare multipart payload", e);
      }
    }

    private static byte[] serialize(List<BodySupplier<InputStream>> parts) throws IOException {
      var out = new ByteArrayOutputStream();
      for (var supplier : parts) {
        try (var in = supplier.get()) {
          in.transferTo(out);
        }
      }
      return out.toByteArray();
    }

    private static void collectParts(
        List<BodySupplier<InputStream>> parts, MultipartPayload payload) {
      if (payload.getPreamble() != null) {
        parts.add(BodySupplier.ofString(payload.getPreamble() + CRLF));
      }
      var bodyParts = payload.getBodyParts();
      if (bodyParts.isEmpty()) {
        parts.add(BodySupplier.empty());
        return;
      }
      String boundary = payload.getBoundary();
      for (var bodyPart : bodyParts) {
        parts.add(BodySupplier.ofString(renderBoundaryAndHeaders(bodyPart, boundary)));
        appendContent(parts, bodyPart);
        parts.add(BodySupplier.ofString(CRLF));
      }
      parts.add(BodySupplier.ofString(BOUNDARY_PREFIX + boundary + BOUNDARY_PREFIX));
      if (payload.getEpilogue() != null) {
        parts.add(BodySupplier.ofString(CRLF + payload.getEpilogue()));
      }
    }

    private static String renderBoundaryAndHeaders(BodyPartPayload bodyPart, String boundary) {
      var buf = new StringBuilder().append(BOUNDARY_PREFIX).append(boundary).append(CRLF);
      var headers = bodyPart.getHeaders();
      if (headers != null) {
        headers.forEach((k, v) -> buf.append(k).append(": ").append(v).append(CRLF));
      }
      return buf.append(CRLF).toString();
    }

    private static void appendContent(
        List<BodySupplier<InputStream>> parts, BodyPartPayload bodyPart) {
      switch (bodyPart) {
        case MultipartPayload multi -> collectParts(parts, multi);
        case ByteArrayBodyPartPayload b ->
            parts.add(BodySupplier.ofBytes(b.getPayload(), b.getOff(), b.getLen()));
        case FileBodyPartPayload f -> parts.add(f.getPayload());
        default ->
            throw new IllegalArgumentException(
                "Unsupported body part type: " + bodyPart.getClass());
      }
    }
  }
}
