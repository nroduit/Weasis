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
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.weasis.core.api.net.BodyPart;
import org.weasis.core.api.net.HttpResponseStream;
import org.weasis.core.api.net.HttpUtils;
import org.weasis.core.api.net.MultipartBody;
import org.weasis.core.api.net.RequestStallGuard;
import org.weasis.core.api.net.WebRequest;

/** Executes {@link WebRequest}s with Java's {@link HttpClient}, including multipart bodies. */
public class JavaNetHttpClient {

  private static final String CRLF = "\r\n";
  private static final String BOUNDARY_PREFIX = "--";
  private static final String HEADER_CONTENT_TYPE = "Content-Type";
  private static final String DEFAULT_FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";

  private final HttpClient sharedClient;
  private final Duration inactivityTimeout;
  private final int responseTimeoutMillis;

  public JavaNetHttpClient() {
    this(new JavaNetHttpClientConfig());
  }

  public JavaNetHttpClient(JavaNetHttpClientConfig config) {
    this.sharedClient =
        HttpUtils.buildHttpClient(
            Duration.ofMillis(config.getConnectTimeoutMillis()),
            HttpClient.Redirect.NORMAL,
            config.getProxy());
    this.inactivityTimeout = Duration.ofMillis(config.getInactivityTimeoutMillis());
    this.responseTimeoutMillis = config.getResponseTimeoutMillis();
  }

  private RequestStallGuard newStallGuard() {
    return new RequestStallGuard((int) inactivityTimeout.toMillis(), responseTimeoutMillis);
  }

  /**
   * Executes the request and returns the response, both halves guarded against stalled transfers
   * rather than capped by a whole-exchange deadline, so a large send or retrieve on a slow link is
   * only aborted when the data actually stops flowing.
   */
  public HttpResponseStream execute(WebRequest request) throws IOException {
    var stallGuard = newStallGuard();
    var httpRequest = createRequestBuilder(request, stallGuard).build();
    var future =
        stallGuard.guard(
            sharedClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofInputStream()));
    try {
      return new HttpResponseStream(future.get(), (int) inactivityTimeout.toMillis());
    } catch (InterruptedException e) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw new IOException("Request interrupted", e);
    } catch (ExecutionException e) {
      var cause = unwrap(e.getCause());
      throw cause instanceof IOException io ? io : new IOException("Request failed", cause);
    }
  }

  private static Throwable unwrap(Throwable t) {
    Throwable current = t;
    while ((current instanceof CompletionException || current instanceof UncheckedIOException)
        && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  /**
   * Serializes {@code body} into an in-memory POST body on {@code builder}, setting the multipart
   * Content-Type declared by the body. Shared with the no-auth STOW-RS path in {@link HttpUtils}.
   * The body is routed through {@code stallGuard} so a slow upload is only aborted once it stops
   * progressing.
   */
  public static void applyMultipart(
      HttpRequest.Builder builder, MultipartBody body, RequestStallGuard stallGuard) {
    builder.POST(stallGuard.track(MultipartEncoder.applyTo(builder, body)));
  }

  private static HttpRequest.Builder createRequestBuilder(
      WebRequest request, RequestStallGuard stallGuard) {
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
    HttpRequest.BodyPublisher publisher;
    if (multipart != null && method.permitsBody()) {
      publisher = MultipartEncoder.applyTo(builder, multipart);
    } else if (body != null && method.permitsBody()) {
      // Default to form-urlencoded: providers like Google reject token requests without it.
      if (!callerSetContentType) {
        builder.setHeader(HEADER_CONTENT_TYPE, DEFAULT_FORM_CONTENT_TYPE);
      }
      publisher = HttpRequest.BodyPublishers.ofByteArray(body);
    } else {
      publisher = HttpRequest.BodyPublishers.noBody();
    }
    builder.method(method.name(), stallGuard.track(publisher));
    return builder;
  }

  /** Encodes a {@link MultipartBody} into a single byte-array body. */
  private static final class MultipartEncoder {

    private MultipartEncoder() {}

    /** Sets the multipart Content-Type and returns the encoded body, left for the caller to set. */
    static HttpRequest.BodyPublisher applyTo(HttpRequest.Builder builder, MultipartBody body) {
      try {
        byte[] content = serialize(body);
        builder.setHeader(HEADER_CONTENT_TYPE, body.contentType());
        return HttpRequest.BodyPublishers.ofByteArray(content);
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
