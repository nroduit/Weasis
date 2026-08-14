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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.weasis.core.api.net.BodyPart;
import org.weasis.core.api.net.HttpResponseStream;
import org.weasis.core.api.net.HttpUtils;
import org.weasis.core.api.net.MultipartBody;
import org.weasis.core.api.net.WebRequest;

/** Executes {@link WebRequest}s with Java's {@link HttpClient}, including multipart bodies. */
public class JavaNetHttpClient {

  private static final String CRLF = "\r\n";
  private static final String BOUNDARY_PREFIX = "--";
  private static final String HEADER_CONTENT_TYPE = "Content-Type";
  private static final String DEFAULT_FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";

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

  /**
   * Executes the request and returns the response with its body guarded against stalled reads
   * rather than capped by a whole-exchange deadline, so a large retrieve on a slow link is only
   * aborted when the data actually stops flowing.
   */
  public HttpResponseStream execute(WebRequest request) throws IOException {
    var httpRequest = createRequestBuilder(request).build();
    var future = sharedClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
    try {
      long millis = readTimeout.toMillis();
      var response = millis > 0 ? future.get(millis, TimeUnit.MILLISECONDS) : future.get();
      return new HttpResponseStream(response, (int) millis);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw new IOException("No response after " + readTimeout.toMillis() + "ms", e);
    } catch (InterruptedException e) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw new IOException("Request interrupted", e);
    } catch (ExecutionException e) {
      var cause = e.getCause() instanceof UncheckedIOException u ? u.getCause() : e.getCause();
      throw cause instanceof IOException io ? io : new IOException("Request failed", cause);
    }
  }

  /**
   * Serializes {@code body} into an in-memory POST body on {@code builder}, setting the multipart
   * Content-Type declared by the body. Shared with the no-auth STOW-RS path in {@link HttpUtils}.
   */
  public static void applyMultipart(HttpRequest.Builder builder, MultipartBody body) {
    MultipartEncoder.applyTo(builder, body, WebRequest.Method.POST);
  }

  private static HttpRequest.Builder createRequestBuilder(WebRequest request) {
    var builder = HttpRequest.newBuilder(URI.create(request.getUrl()));
    boolean callerSetContentType = false;
    for (var entry : request.getHeaders().entrySet()) {
      builder.setHeader(entry.getKey(), entry.getValue());
      if (HEADER_CONTENT_TYPE.equalsIgnoreCase(entry.getKey())) {
        callerSetContentType = true;
      }
    }
    var method = request.getMethod();
    var multipart = request.getMultipartBody();
    byte[] body = request.getBody();
    if (multipart != null && method.permitsBody()) {
      MultipartEncoder.applyTo(builder, multipart, method);
    } else if (body != null && method.permitsBody()) {
      // Default to form-urlencoded: providers like Google reject token requests without it.
      if (!callerSetContentType) {
        builder.setHeader(HEADER_CONTENT_TYPE, DEFAULT_FORM_CONTENT_TYPE);
      }
      builder.method(method.name(), HttpRequest.BodyPublishers.ofByteArray(body));
    } else {
      builder.method(method.name(), HttpRequest.BodyPublishers.noBody());
    }
    return builder;
  }

  /** Encodes a {@link MultipartBody} into a single byte-array body. */
  private static final class MultipartEncoder {

    private MultipartEncoder() {}

    static void applyTo(HttpRequest.Builder builder, MultipartBody body, WebRequest.Method method) {
      try {
        builder.setHeader(HEADER_CONTENT_TYPE, body.contentType());
        builder.method(method.name(), HttpRequest.BodyPublishers.ofByteArray(serialize(body)));
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to prepare multipart payload", e);
      }
    }

    private static byte[] serialize(MultipartBody body) throws IOException {
      var out = new ByteArrayOutputStream();
      String boundary = body.getBoundary();
      List<BodyPart> parts = body.getBodyParts();
      for (var part : parts) {
        write(out, renderBoundaryAndHeaders(part, boundary));
        try (InputStream in = part.getContent().get()) {
          in.transferTo(out);
        }
        write(out, CRLF);
      }
      write(out, BOUNDARY_PREFIX + boundary + BOUNDARY_PREFIX);
      return out.toByteArray();
    }

    private static String renderBoundaryAndHeaders(BodyPart part, String boundary) {
      var buf = new StringBuilder().append(BOUNDARY_PREFIX).append(boundary).append(CRLF);
      part.getHeaders().forEach((k, v) -> buf.append(k).append(": ").append(v).append(CRLF));
      return buf.append(CRLF).toString();
    }

    private static void write(ByteArrayOutputStream out, String text) throws IOException {
      out.write(text.getBytes(StandardCharsets.UTF_8));
    }
  }
}
