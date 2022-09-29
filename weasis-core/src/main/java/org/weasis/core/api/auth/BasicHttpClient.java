/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.auth;

import com.github.scribejava.core.exceptions.OAuthException;
import com.github.scribejava.core.httpclient.HttpClient;
import com.github.scribejava.core.httpclient.jdk.JDKHttpFuture;
import com.github.scribejava.core.httpclient.multipart.BodyPartPayload;
import com.github.scribejava.core.httpclient.multipart.ByteArrayBodyPartPayload;
import com.github.scribejava.core.httpclient.multipart.MultipartPayload;
import com.github.scribejava.core.model.OAuthAsyncRequestCallback;
import com.github.scribejava.core.model.OAuthConstants;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.weasis.core.api.util.NetworkUtil;
import org.weasis.core.util.FileUtil;

public class BasicHttpClient implements HttpClient {

  @Override
  public void close() {
    // Do nothing
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
        userAgent,
        headers,
        httpVerb,
        completeUrl,
        BodyType.BYTE_ARRAY,
        bodyContents,
        callback,
        converter);
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
        userAgent,
        headers,
        httpVerb,
        completeUrl,
        BodyType.MULTIPART,
        bodyContents,
        callback,
        converter);
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
        userAgent,
        headers,
        httpVerb,
        completeUrl,
        BodyType.STRING,
        bodyContents,
        callback,
        converter);
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
    throw new UnsupportedOperationException(
        "JDKHttpClient does not support File payload for the moment");
  }

  private <T> Future<T> doExecuteAsync(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      BodyType bodyType,
      Object bodyContents,
      OAuthAsyncRequestCallback<T> callback,
      OAuthRequest.ResponseConverter<T> converter) {
    try {
      final Response response =
          doExecute(userAgent, headers, httpVerb, completeUrl, bodyType, bodyContents);
      @SuppressWarnings("unchecked")
      final T t = converter == null ? (T) response : converter.convert(response);
      if (callback != null) {
        callback.onCompleted(t);
      }
      return new JDKHttpFuture<>(t);
    } catch (IOException | RuntimeException e) {
      if (callback != null) {
        callback.onThrowable(e);
      }
      return new JDKHttpFuture<>(e);
    }
  }

  @Override
  public Response execute(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      byte[] bodyContents)
      throws InterruptedException, ExecutionException, IOException {
    return doExecute(userAgent, headers, httpVerb, completeUrl, BodyType.BYTE_ARRAY, bodyContents);
  }

  @Override
  public Response execute(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      MultipartPayload multipartPayloads)
      throws InterruptedException, ExecutionException, IOException {
    return doExecute(
        userAgent, headers, httpVerb, completeUrl, BodyType.MULTIPART, multipartPayloads);
  }

  @Override
  public Response execute(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      String bodyContents)
      throws InterruptedException, ExecutionException, IOException {
    return doExecute(userAgent, headers, httpVerb, completeUrl, BodyType.STRING, bodyContents);
  }

  @Override
  public Response execute(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      File bodyContents)
      throws InterruptedException, ExecutionException, IOException {
    throw new UnsupportedOperationException(
        "JDKHttpClient does not support File payload for the moment");
  }

  private Response doExecute(
      String userAgent,
      Map<String, String> headers,
      Verb httpVerb,
      String completeUrl,
      BodyType bodyType,
      Object bodyContents)
      throws IOException {
    final URL url = new URL(completeUrl);
    final HttpURLConnection connection = (HttpURLConnection) url.openConnection();

    connection.setInstanceFollowRedirects(true);
    connection.setRequestMethod(httpVerb.name());
    connection.setConnectTimeout(NetworkUtil.getUrlConnectionTimeout());
    connection.setReadTimeout(NetworkUtil.getUrlReadTimeout());
    addHeaders(connection, headers, userAgent);
    if (httpVerb.isPermitBody()) {
      bodyType.setBody(connection, bodyContents, httpVerb.isRequiresBody());
    }

    try {
      connection.connect();
      final int responseCode = connection.getResponseCode();
      return new Response(
          responseCode,
          connection.getResponseMessage(),
          parseHeaders(connection),
          responseCode >= 200 && responseCode < 400
              ? connection.getInputStream()
              : connection.getErrorStream());
    } catch (UnknownHostException e) {
      throw new OAuthException("The IP address of a host could not be determined.", e);
    }
  }

  private enum BodyType {
    BYTE_ARRAY {
      @Override
      void setBody(HttpURLConnection connection, Object bodyContents, boolean requiresBody)
          throws IOException {
        addBody(connection, (byte[]) bodyContents, requiresBody);
      }
    },
    MULTIPART {
      @Override
      void setBody(HttpURLConnection connection, Object bodyContents, boolean requiresBody)
          throws IOException {
        addBody(connection, (MultipartPayload) bodyContents, requiresBody);
      }
    },
    STRING {
      @Override
      void setBody(HttpURLConnection connection, Object bodyContents, boolean requiresBody)
          throws IOException {
        addBody(connection, ((String) bodyContents).getBytes(StandardCharsets.UTF_8), requiresBody);
      }
    };

    abstract void setBody(HttpURLConnection connection, Object bodyContents, boolean requiresBody)
        throws IOException;
  }

  private static Map<String, String> parseHeaders(HttpURLConnection conn) {
    final Map<String, String> headers = new HashMap<>();

    for (Map.Entry<String, List<String>> headerField : conn.getHeaderFields().entrySet()) {
      final String key = headerField.getKey();
      final String value = headerField.getValue().get(0);
      if ("Content-Encoding".equalsIgnoreCase(key)) { // NON-NLS
        headers.put("Content-Encoding", value); // NON-NLS
      } else {
        headers.put(key, value);
      }
    }
    return headers;
  }

  private static void addHeaders(
      HttpURLConnection connection, Map<String, String> headers, String userAgent) {
    for (Map.Entry<String, String> header : headers.entrySet()) {
      connection.setRequestProperty(header.getKey(), header.getValue());
    }

    if (userAgent != null) {
      connection.setRequestProperty(OAuthConstants.USER_AGENT_HEADER_NAME, userAgent);
    }
  }

  private static void addBody(HttpURLConnection connection, byte[] content, boolean requiresBody)
      throws IOException {
    final int contentLength = content.length;
    if (requiresBody || contentLength > 0) {
      final OutputStream outputStream =
          prepareConnectionForBodyAndGetOutputStream(connection, contentLength);
      if (contentLength > 0) {
        outputStream.write(content);
      }
    }
  }

  public static void addBody(
      HttpURLConnection connection, MultipartPayload multipartPayload, boolean requiresBody)
      throws IOException {

    for (Map.Entry<String, String> header : multipartPayload.getHeaders().entrySet()) {
      connection.setRequestProperty(header.getKey(), header.getValue());
    }

    if (requiresBody) {
      List<BodySupplier<InputStream>> bodySuppliers = new ArrayList<>();
      prepareMultipartPayload(bodySuppliers, multipartPayload);
      long contentLength =
          bodySuppliers.stream().mapToLong(BodySupplier::length).reduce(0L, Long::sum);
      final OutputStream outputStream =
          prepareConnectionForBodyAndGetOutputStream(connection, contentLength);
      if (contentLength > 0) {
        byte[] buf = new byte[FileUtil.FILE_BUFFER];
        for (BodySupplier<InputStream> b : bodySuppliers) {
          try (InputStream inputStream = b.get()) {
            int offset;
            while ((offset = inputStream.read(buf)) > 0) {
              outputStream.write(buf, 0, offset);
            }
            outputStream.flush();
          }
        }
      }
    }
  }

  private static OutputStream prepareConnectionForBodyAndGetOutputStream(
      HttpURLConnection connection, long contentLength) throws IOException {
    connection.setRequestProperty(CONTENT_LENGTH, String.valueOf(contentLength));
    if (connection.getRequestProperty(CONTENT_TYPE) == null) {
      connection.setRequestProperty(CONTENT_TYPE, DEFAULT_CONTENT_TYPE);
    }
    connection.setDoOutput(true);
    connection.setFixedLengthStreamingMode(contentLength);
    return connection.getOutputStream();
  }

  public static void prepareMultipartPayload(
      List<BodySupplier<InputStream>> bodySuppliers, MultipartPayload multipartPayload) {
    StringBuilder buf = new StringBuilder();

    final String preamble = multipartPayload.getPreamble();
    if (preamble != null) {
      buf.append(preamble);
      buf.append("\r\n");
    }
    final List<BodyPartPayload> bodyParts = multipartPayload.getBodyParts();
    if (!bodyParts.isEmpty()) {
      final String boundary = multipartPayload.getBoundary();

      for (BodyPartPayload bodyPart : bodyParts) {
        buf.append("--");
        buf.append(boundary);
        buf.append("\r\n");

        final Map<String, String> bodyPartHeaders = bodyPart.getHeaders();
        if (bodyPartHeaders != null) {
          for (Map.Entry<String, String> header : bodyPartHeaders.entrySet()) {
            buf.append(header.getKey());
            buf.append(": ");
            buf.append(header.getValue());
            buf.append("\r\n");
          }
        }

        buf.append("\r\n");
        bodySuppliers.add(newBodySupplier(buf));

        if (bodyPart instanceof MultipartPayload multi) {
          prepareMultipartPayload(bodySuppliers, multi);
        } else if (bodyPart instanceof ByteArrayBodyPartPayload byteArrayBodyPart) {
          bodySuppliers.add(
              newBodySupplier(
                  byteArrayBodyPart.getPayload(),
                  byteArrayBodyPart.getOff(),
                  byteArrayBodyPart.getLen()));
        } else if (bodyPart instanceof FileBodyPartPayload fileBodyPart) {
          bodySuppliers.add(fileBodyPart.getPayload());
        } else {
          throw new AssertionError(bodyPart.getClass());
        }
        buf.append("\r\n"); // CRLF for the next (starting or closing) boundary
      }

      buf.append("--");
      buf.append(boundary);
      buf.append("--");

      final String epilogue = multipartPayload.getEpilogue();
      if (epilogue != null) {
        buf.append("\r\n");
        buf.append(epilogue);
      }
    }
    bodySuppliers.add(newBodySupplier(buf));
  }

  private static BodySupplier<InputStream> newBodySupplier(StringBuilder buf) {
    byte[] bytes = buf.toString().getBytes(StandardCharsets.UTF_8);
    buf.setLength(0);
    return newBodySupplier(bytes);
  }

  private static BodySupplier<InputStream> newBodySupplier(byte[] bytes) {
    return new BodySupplier<>() {
      @Override
      public InputStream get() {
        return new ByteArrayInputStream(bytes);
      }

      @Override
      public long length() {
        return bytes.length;
      }
    };
  }

  private static BodySupplier<InputStream> newBodySupplier(byte[] payload, int off, int len) {
    if (off == 0 && len == payload.length) {
      return newBodySupplier(payload);
    }
    return new BodySupplier<>() {
      @Override
      public InputStream get() {
        return new ByteArrayInputStream(payload, off, len);
      }

      @Override
      public long length() {
        return len;
      }
    };
  }
}
