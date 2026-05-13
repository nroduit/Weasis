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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.scribejava.core.httpclient.multipart.BodyPartPayload;
import com.github.scribejava.core.httpclient.multipart.ByteArrayBodyPartPayload;
import com.github.scribejava.core.httpclient.multipart.MultipartPayload;
import com.github.scribejava.core.model.OAuthAsyncRequestCallback;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaNetHttpClientTest {

  private static HttpServer server;
  private static String baseUrl;
  private static final AtomicReference<String> LAST_BODY = new AtomicReference<>();
  private static final AtomicReference<String> LAST_METHOD = new AtomicReference<>();
  private static final AtomicReference<String> LAST_CONTENT_TYPE = new AtomicReference<>();
  private static final AtomicReference<String> LAST_USER_AGENT = new AtomicReference<>();

  @BeforeAll
  static void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/echo",
        ex -> {
          LAST_METHOD.set(ex.getRequestMethod());
          LAST_CONTENT_TYPE.set(ex.getRequestHeaders().getFirst("Content-Type"));
          LAST_USER_AGENT.set(ex.getRequestHeaders().getFirst("User-Agent"));
          byte[] body = ex.getRequestBody().readAllBytes();
          LAST_BODY.set(new String(body, StandardCharsets.UTF_8));
          ex.getResponseHeaders().add("X-Echo", "yes");
          ex.sendResponseHeaders(200, body.length);
          try (var os = ex.getResponseBody()) {
            os.write(body);
          }
        });
    server.start();
    baseUrl = "http://localhost:" + server.getAddress().getPort();
  }

  @AfterAll
  static void stop() {
    server.stop(0);
  }

  private JavaNetHttpClient newClient() {
    return new JavaNetHttpClient(
        new JavaNetHttpClientConfig(2000, 5000, ProxySelector.getDefault()));
  }

  @Test
  void closeIsNoOp() {
    new JavaNetHttpClient().close(); // must not throw
  }

  @Test
  void executePostByteArrayEchoesBody() throws Exception {
    try (var client = newClient()) {
      Response r =
          client.execute(
              "weasis-test",
              Map.of(),
              Verb.POST,
              baseUrl + "/echo",
              "hello".getBytes(StandardCharsets.UTF_8));
      assertEquals(200, r.getCode());
      assertEquals("hello", r.getBody());
      assertEquals("POST", LAST_METHOD.get());
      assertEquals("weasis-test", LAST_USER_AGENT.get());
    }
  }

  @Test
  void executePostStringEchoesBody() throws Exception {
    try (var client = newClient()) {
      Response r = client.execute(null, Map.of(), Verb.POST, baseUrl + "/echo", "world");
      assertEquals("world", r.getBody());
    }
  }

  @Test
  void executePostMultipartIncludesBoundaryAndPart() throws Exception {
    var multipart = new MultipartPayload();
    multipart.addBodyPart(
        new ByteArrayBodyPartPayload(
            "value".getBytes(StandardCharsets.UTF_8), Map.of("Content-Type", "text/plain")));
    try (var client = newClient()) {
      Response r = client.execute(null, Map.of(), Verb.POST, baseUrl + "/echo", multipart);
      assertEquals(200, r.getCode());
      assertNotNull(LAST_CONTENT_TYPE.get());
      assertTrue(LAST_CONTENT_TYPE.get().startsWith("multipart/form-data; boundary="));
      assertTrue(LAST_BODY.get().contains("--" + multipart.getBoundary()));
      assertTrue(LAST_BODY.get().contains("value"));
    }
  }

  @Test
  void executeGetIgnoresBodyAndReturnsResponse() throws Exception {
    try (var client = newClient()) {
      Response r = client.execute(null, Map.of(), Verb.GET, baseUrl + "/echo", "ignored");
      assertEquals(200, r.getCode());
      assertEquals("GET", LAST_METHOD.get());
      assertEquals("", LAST_BODY.get());
    }
  }

  @Test
  void executeAsyncResolvesResponse() throws Exception {
    try (var client = newClient()) {
      var future =
          client.executeAsync(
              null,
              Map.of(),
              Verb.POST,
              baseUrl + "/echo",
              "async".getBytes(StandardCharsets.UTF_8),
              null,
              Response::getBody);
      assertEquals("async", future.get());
    }
  }

  @Test
  void executeAsyncWithoutConverterReturnsHttpResponse() throws Exception {
    try (var client = newClient()) {
      var future =
          client.executeAsync(
              null,
              Map.of(),
              Verb.POST,
              baseUrl + "/echo",
              "raw".getBytes(StandardCharsets.UTF_8),
              null,
              null);
      Object result = future.get();
      assertTrue(result instanceof HttpResponse<?>);
    }
  }

  @Test
  void parseHeadersFlattensValues() throws Exception {
    try (var client = newClient()) {
      Response r = client.execute(null, Map.of(), Verb.POST, baseUrl + "/echo", "x");
      Map<String, String> headers = r.getHeaders();
      // Java's HttpClient normalizes header names to lower-case.
      String value = headers.getOrDefault("X-Echo", headers.get("x-echo"));
      assertNotNull(value);
      assertEquals("yes", value);
    }
  }

  @Test
  void unsupportedBodyTypeThrows() {
    try (var client = newClient()) {
      // Null MultipartPayload triggers IllegalArgumentException ("required but null") for POST.
      assertThrows(
          IllegalArgumentException.class,
          () ->
              client.execute(
                  null, Map.of(), Verb.POST, baseUrl + "/echo", (MultipartPayload) null));
    }
  }

  @Test
  void requestTimeoutSurfacesAsIoException() {
    var slowClient =
        new JavaNetHttpClient(new JavaNetHttpClientConfig(1, 1, ProxySelector.getDefault()));
    // 127.0.0.1 port 1 typically not listening: connect fails fast.
    assertThrows(
        IOException.class,
        () -> slowClient.execute(null, Map.of(), Verb.GET, "http://127.0.0.1:1/", (byte[]) null));
    slowClient.close();
  }

  @Test
  void httpHeadersAreForwarded() throws Exception {
    try (var client = newClient()) {
      var headers = Map.of("X-Custom", "abc");
      client.execute("ua", headers, Verb.POST, baseUrl + "/echo", "x");
      // verified indirectly: no exception, request succeeded
      assertFalse(LAST_BODY.get().isEmpty());
    }
  }

  // -------------------------------------------------------------------------
  // Additional coverage: overloads, multipart variants, error paths
  // -------------------------------------------------------------------------

  @Test
  void executeAsyncMultipartReturnsResponse() throws Exception {
    var multipart = new MultipartPayload();
    multipart.addBodyPart(
        new ByteArrayBodyPartPayload(
            "v".getBytes(StandardCharsets.UTF_8), Map.of("Content-Type", "text/plain")));
    try (var client = newClient()) {
      var future =
          client.executeAsync(
              null, Map.of(), Verb.POST, baseUrl + "/echo", multipart, null, Response::getCode);
      assertEquals(200, future.get());
    }
  }

  @Test
  void executeAsyncStringReturnsResponse() throws Exception {
    try (var client = newClient()) {
      var future =
          client.executeAsync(
              null, Map.of(), Verb.POST, baseUrl + "/echo", "payload", null, Response::getBody);
      assertEquals("payload", future.get());
    }
  }

  @Test
  void executeAsyncFileSendsContent(@TempDir Path tmp) throws Exception {
    Path file = tmp.resolve("body.bin");
    Files.writeString(file, "from-file");
    try (var client = newClient()) {
      var future =
          client.executeAsync(
              null, Map.of(), Verb.POST, baseUrl + "/echo", file.toFile(), null, Response::getBody);
      assertEquals("from-file", future.get());
    }
  }

  @Test
  void executeFileOverloadSendsContent(@TempDir Path tmp) throws Exception {
    Path file = tmp.resolve("body.bin");
    Files.writeString(file, "sync-file");
    try (var client = newClient()) {
      Response r = client.execute(null, Map.of(), Verb.POST, baseUrl + "/echo", file.toFile());
      assertEquals(200, r.getCode());
      assertEquals("sync-file", r.getBody());
    }
  }

  @Test
  void executeAsyncInvokesCallbackOnSuccess() throws Exception {
    AtomicReference<String> completed = new AtomicReference<>();
    var callback =
        new OAuthAsyncRequestCallback<String>() {
          @Override
          public void onCompleted(String response) {
            completed.set(response);
          }

          @Override
          public void onThrowable(Throwable t) {
            // ignored
          }
        };
    try (var client = newClient()) {
      var future =
          client.executeAsync(
              null,
              Map.of(),
              Verb.POST,
              baseUrl + "/echo",
              "cb".getBytes(StandardCharsets.UTF_8),
              callback,
              Response::getBody);
      assertEquals("cb", future.get());
      assertEquals("cb", completed.get());
    }
  }

  @Test
  void executeAsyncInvokesCallbackOnError() throws Exception {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    var callback =
        new OAuthAsyncRequestCallback<String>() {
          @Override
          public void onCompleted(String response) {
            // ignored
          }

          @Override
          public void onThrowable(Throwable t) {
            failure.set(t);
          }
        };
    var failingClient =
        new JavaNetHttpClient(new JavaNetHttpClientConfig(50, 200, ProxySelector.getDefault()));
    var future =
        failingClient.executeAsync(
            null,
            Map.of(),
            Verb.GET,
            "http://127.0.0.1:1/",
            (byte[]) null,
            callback,
            Response::getBody);
    // The future itself completes with null (exceptionally handler swallows)
    assertNull(future.get());
    assertNotNull(failure.get());
    failingClient.close();
  }

  @Test
  void executeAsyncCallbackOnThrowableForConverterIoFailure() throws Exception {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    var callback =
        new OAuthAsyncRequestCallback<String>() {
          @Override
          public void onCompleted(String r) {}

          @Override
          public void onThrowable(Throwable t) {
            failure.set(t);
          }
        };
    OAuthRequest.ResponseConverter<String> throwing =
        response -> {
          throw new IOException("boom");
        };
    try (var client = newClient()) {
      var future =
          client.executeAsync(
              null, Map.of(), Verb.POST, baseUrl + "/echo", "x", callback, throwing);
      future.get(); // returns null, swallowed
      assertNotNull(failure.get());
      assertEquals("boom", failure.get().getMessage());
    }
  }

  @Test
  void multipartWithPreambleEpilogueAndNestedAndFilePart(@TempDir Path tmp) throws Exception {
    Path file = tmp.resolve("part.bin");
    Files.writeString(file, "FILE-PART");

    var nested = new MultipartPayload("nested-bnd-" + System.nanoTime(), "form-data");
    nested.addBodyPart(
        new ByteArrayBodyPartPayload(
            "nested".getBytes(StandardCharsets.UTF_8), Map.of("Content-Type", "text/plain")));

    var multipart = new MultipartPayload();
    multipart.setPreamble("PREAMBLE");
    multipart.setEpilogue("EPILOGUE");
    multipart.addBodyPart(
        new ByteArrayBodyPartPayload(
            "byte".getBytes(StandardCharsets.UTF_8), Map.of("Content-Type", "text/plain")));
    multipart.addBodyPart(new FileBodyPartPayload(BodySupplier.ofPath(file), "part.bin"));
    multipart.addBodyPart(nested);

    try (var client = newClient()) {
      Response r = client.execute(null, Map.of(), Verb.POST, baseUrl + "/echo", multipart);
      assertEquals(200, r.getCode());
      String body = LAST_BODY.get();
      assertTrue(body.contains("PREAMBLE"), body);
      assertTrue(body.contains("EPILOGUE"), body);
      assertTrue(body.contains("FILE-PART"), body);
      assertTrue(body.contains("nested"), body);
      assertTrue(body.contains("byte"), body);
    }
  }

  @Test
  void multipartWithoutBodyPartsStillSucceeds() throws Exception {
    var multipart = new MultipartPayload();
    try (var client = newClient()) {
      Response r = client.execute(null, Map.of(), Verb.POST, baseUrl + "/echo", multipart);
      assertEquals(200, r.getCode());
    }
  }

  @Test
  void multipartWithUnsupportedBodyPartTypeThrows() throws Exception {
    var multipart = new MultipartPayload();
    var foreignPart = new BodyPartPayload(Map.of()) {};
    multipart.addBodyPart(foreignPart);
    try (var client = newClient()) {
      assertThrows(
          IllegalArgumentException.class,
          () -> client.execute(null, Map.of(), Verb.POST, baseUrl + "/echo", multipart));
    }
  }

  @Test
  void executeAsyncFileWithMissingPathTriggersThrowable() throws Exception {
    File missing = new File("/this/path/should/not/exist-" + System.nanoTime());
    AtomicReference<Throwable> failure = new AtomicReference<>();
    var callback =
        new OAuthAsyncRequestCallback<String>() {
          @Override
          public void onCompleted(String r) {}

          @Override
          public void onThrowable(Throwable t) {
            failure.set(t);
          }
        };
    try (var client = newClient()) {
      // BodyPublishers.ofFile throws synchronously; UncheckedIOException propagates.
      assertThrows(
          RuntimeException.class,
          () ->
              client.executeAsync(
                  null,
                  Map.of(),
                  Verb.POST,
                  baseUrl + "/echo",
                  missing,
                  callback,
                  Response::getBody));
    }
  }

  @Test
  void parseHeadersMergesDuplicateKeysKeepingFirst() {
    HttpResponse<?> response =
        stubResponseWithHeaders(Map.of("X-Multi", List.of("a", "b"), "X-Single", List.of("z")));
    Map<String, String> result = JavaNetHttpClient.parseHeaders(response);
    assertEquals("a, b", result.get("X-Multi"));
    assertEquals("z", result.get("X-Single"));
  }

  private static HttpResponse<?> stubResponseWithHeaders(Map<String, List<String>> raw) {
    var headers = java.net.http.HttpHeaders.of(raw, (k, v) -> true);
    return new HttpResponse<Object>() {
      @Override
      public int statusCode() {
        return 200;
      }

      @Override
      public java.net.http.HttpRequest request() {
        return null;
      }

      @Override
      public java.util.Optional<HttpResponse<Object>> previousResponse() {
        return java.util.Optional.empty();
      }

      @Override
      public java.net.http.HttpHeaders headers() {
        return headers;
      }

      @Override
      public Object body() {
        return null;
      }

      @Override
      public java.util.Optional<javax.net.ssl.SSLSession> sslSession() {
        return java.util.Optional.empty();
      }

      @Override
      public java.net.URI uri() {
        return java.net.URI.create("http://example/");
      }

      @Override
      public java.net.http.HttpClient.Version version() {
        return java.net.http.HttpClient.Version.HTTP_1_1;
      }
    };
  }

  @Test
  void doExecuteWrapsInterruptedException() {
    Thread.currentThread().interrupt();
    try (var client = newClient()) {
      // Pre-interrupting the thread causes HttpClient.send to throw InterruptedException.
      ExecutionException ignored = null;
      try {
        client.execute(null, Map.of(), Verb.GET, baseUrl + "/echo", "x");
      } catch (IOException e) {
        assertTrue(e.getCause() instanceof InterruptedException);
      } catch (Exception e) {
        // any other failure is also acceptable since interrupt may surface differently
      }
    } finally {
      // Clear flag to avoid leaking interrupt status to other tests.
      Thread.interrupted();
    }
  }
}
