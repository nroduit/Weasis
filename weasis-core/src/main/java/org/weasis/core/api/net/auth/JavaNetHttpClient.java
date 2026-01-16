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
import org.weasis.core.api.net.HttpUtils;
import org.weasis.core.util.StringUtil;

/** HTTP client implementation using Java's {@link HttpClient} for ScribeJava OAuth integration. */
public class JavaNetHttpClient implements com.github.scribejava.core.httpclient.HttpClient {

  // Hold a reference to the shared client to avoid recreation
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

    var requestBuilder =
        createRequestBuilder(userAgent, headers, httpVerb, completeUrl, bodyContents, readTimeout);

    return sharedClient
        .sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream())
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

    var requestBuilder =
        createRequestBuilder(userAgent, headers, httpVerb, completeUrl, bodyContents, readTimeout);

    try {
      var httpResponse =
          sharedClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
      return new Response(
          httpResponse.statusCode(),
          httpResponse.version().toString(),
          parseHeaders(httpResponse),
          httpResponse.body());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Request interrupted", e);
    } catch (HttpTimeoutException e) {
      throw new IOException("Request timed out after " + readTimeout.toMillis() + "ms", e);
    }
  }

  private <T> T processAsyncResponse(
      HttpResponse<InputStream> httpResponse,
      OAuthAsyncRequestCallback<T> callback,
      OAuthRequest.ResponseConverter<T> converter) {
    try {
      var response =
          new Response(
              httpResponse.statusCode(),
              httpResponse.version().toString(),
              parseHeaders(httpResponse),
              httpResponse.body());

      @SuppressWarnings("unchecked")
      T result = converter == null ? (T) httpResponse : converter.convert(response);

      if (callback != null) {
        callback.onCompleted(result);
      }
      return result;
    } catch (IOException e) {
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
      requestBuilder.setHeader("User-Agent", userAgent);
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
            java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> String.join(", ", entry.getValue()),
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
      case Path path -> {
        try {
          requestBuilder.method(httpVerb.name(), HttpRequest.BodyPublishers.ofFile(path));
        } catch (Exception e) {
          throw new RuntimeException("Failed to read file: " + path, e);
        }
      }
      case MultipartPayload multi -> addMultipartBody(requestBuilder, multi, httpVerb);
      default ->
          throw new IllegalArgumentException("Unsupported body type: " + bodyContents.getClass());
    }
  }

  private static void addMultipartBody(
      HttpRequest.Builder requestBuilder, MultipartPayload multipartPayload, Verb httpVerb) {
    var bodySuppliers = new ArrayList<BodySupplier<InputStream>>();
    prepareMultipartPayload(bodySuppliers, multipartPayload);

    try {
      var multipartData = combineBodySuppliers(bodySuppliers);
      var boundary = multipartPayload.getBoundary();
      requestBuilder.setHeader("Content-Type", "multipart/form-data; boundary=" + boundary);
      requestBuilder.method(httpVerb.name(), HttpRequest.BodyPublishers.ofByteArray(multipartData));
    } catch (IOException e) {
      throw new RuntimeException("Failed to prepare multipart payload", e);
    }
  }

  private static byte[] combineBodySuppliers(List<BodySupplier<InputStream>> bodySuppliers)
      throws IOException {
    var outputStream = new ByteArrayOutputStream();
    for (var supplier : bodySuppliers) {
      try (var inputStream = supplier.get()) {
        inputStream.transferTo(outputStream);
      }
    }

    return outputStream.toByteArray();
  }

  private static void prepareMultipartPayload(
      List<BodySupplier<InputStream>> bodySuppliers, MultipartPayload multipartPayload) {

    addPreambleIfPresent(bodySuppliers, multipartPayload.getPreamble());

    var bodyParts = multipartPayload.getBodyParts();
    if (!bodyParts.isEmpty()) {
      processBodyParts(bodySuppliers, bodyParts, multipartPayload.getBoundary());
      addEpilogueIfPresent(bodySuppliers, multipartPayload.getEpilogue());
    } else {
      bodySuppliers.add(BodySupplier.empty());
    }
  }

  private static void addPreambleIfPresent(
      List<BodySupplier<InputStream>> bodySuppliers, String preamble) {
    if (preamble != null) {
      bodySuppliers.add(BodySupplier.ofString(preamble + "\r\n"));
    }
  }

  private static void processBodyParts(
      List<BodySupplier<InputStream>> bodySuppliers,
      List<BodyPartPayload> bodyParts,
      String boundary) {

    for (var bodyPart : bodyParts) {
      addBoundaryAndHeaders(bodySuppliers, bodyPart, boundary);
      addBodyPartContent(bodySuppliers, bodyPart);
    }

    addClosingBoundary(bodySuppliers, boundary);
  }

  private static void addBoundaryAndHeaders(
      List<BodySupplier<InputStream>> bodySuppliers, BodyPartPayload bodyPart, String boundary) {

    var buf = new StringBuilder().append("--").append(boundary).append("\r\n");

    var headers = bodyPart.getHeaders();
    if (headers != null) {
      headers.forEach((key, value) -> buf.append(key).append(": ").append(value).append("\r\n"));
    }

    buf.append("\r\n");
    bodySuppliers.add(BodySupplier.ofString(buf.toString()));
  }

  private static void addBodyPartContent(
      List<BodySupplier<InputStream>> bodySuppliers, BodyPartPayload bodyPart) {

    switch (bodyPart) {
      case MultipartPayload multi -> prepareMultipartPayload(bodySuppliers, multi);
      case ByteArrayBodyPartPayload byteArrayPart ->
          bodySuppliers.add(
              BodySupplier.ofBytes(
                  byteArrayPart.getPayload(), byteArrayPart.getOff(), byteArrayPart.getLen()));
      case FileBodyPartPayload filePart -> bodySuppliers.add(filePart.getPayload());
      default ->
          throw new IllegalArgumentException("Unsupported body part type: " + bodyPart.getClass());
    }

    bodySuppliers.add(BodySupplier.ofString("\r\n"));
  }

  private static void addClosingBoundary(
      List<BodySupplier<InputStream>> bodySuppliers, String boundary) {
    bodySuppliers.add(BodySupplier.ofString("--" + boundary + "--"));
  }

  private static void addEpilogueIfPresent(
      List<BodySupplier<InputStream>> bodySuppliers, String epilogue) {
    if (epilogue != null) {
      bodySuppliers.add(BodySupplier.ofString("\r\n" + epilogue));
    }
  }
}
