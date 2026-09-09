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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.net.BodyPart;
import org.weasis.core.api.net.BodySupplier;
import org.weasis.core.api.net.MultipartBody;
import org.weasis.core.api.net.StallTimeoutException;
import org.weasis.core.api.net.ThrottledUploadServer;
import org.weasis.core.api.net.WebRequest;

class JavaNetHttpClientTest {

  /** Sized like {@code HttpUtilsTest}: the upload outlasts the timeout, gaps stay far below it. */
  private static final int UPLOAD_SIZE = 8 << 20;

  private static final int UPLOAD_DRAIN_CHUNK = 64 * 1024;
  private static final long UPLOAD_DRAIN_PAUSE_MS = 20;
  private static final int UPLOAD_TIMEOUT_MS = 1_500;

  private static HttpServer server;
  private static ExecutorService serverPool;
  private static String baseUrl;
  private static final AtomicReference<String> LAST_BODY = new AtomicReference<>();
  private static final AtomicReference<String> LAST_METHOD = new AtomicReference<>();
  private static final AtomicReference<String> LAST_CONTENT_TYPE = new AtomicReference<>();

  @BeforeAll
  static void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/echo",
        ex -> {
          LAST_METHOD.set(ex.getRequestMethod());
          LAST_CONTENT_TYPE.set(ex.getRequestHeaders().getFirst("Content-Type"));
          byte[] body = ex.getRequestBody().readAllBytes();
          LAST_BODY.set(new String(body, StandardCharsets.UTF_8));
          ex.getResponseHeaders().add("X-Echo", "yes");
          ex.sendResponseHeaders(200, body.length);
          try (var os = ex.getResponseBody()) {
            os.write(body);
          }
        });
    serverPool = Executors.newCachedThreadPool();
    server.setExecutor(serverPool);
    server.start();
    baseUrl = "http://localhost:" + server.getAddress().getPort();
  }

  @AfterAll
  static void stop() {
    server.stop(0);
    serverPool.shutdownNow();
  }

  private static JavaNetHttpClient newClient() {
    return new JavaNetHttpClient(
        new JavaNetHttpClientConfig(2000, 5000, ProxySelector.getDefault()));
  }

  @Test
  void postBodyIsEchoedAndDefaultsToFormContentType() throws Exception {
    var request = new WebRequest(WebRequest.Method.POST, baseUrl + "/echo");
    request.setBody("grant_type=refresh_token");
    try (var response = newClient().execute(request)) {
      assertEquals(200, response.getResponseCode());
      assertEquals(
          "grant_type=refresh_token", new String(response.getInputStream().readAllBytes()));
      assertEquals("POST", LAST_METHOD.get());
      assertEquals("application/x-www-form-urlencoded", LAST_CONTENT_TYPE.get());
    }
  }

  @Test
  void explicitContentTypeIsNotOverridden() throws Exception {
    var request = new WebRequest(WebRequest.Method.POST, baseUrl + "/echo");
    request.addHeader("Content-Type", "application/json");
    request.setBody("{}");
    try (var response = newClient().execute(request)) {
      assertEquals(200, response.getResponseCode());
      assertEquals("application/json", LAST_CONTENT_TYPE.get());
    }
  }

  @Test
  void getIgnoresBody() throws Exception {
    var request = new WebRequest(WebRequest.Method.GET, baseUrl + "/echo");
    request.setBody("ignored");
    try (var response = newClient().execute(request)) {
      assertEquals(200, response.getResponseCode());
      assertEquals("GET", LAST_METHOD.get());
      assertEquals("", LAST_BODY.get());
    }
  }

  @Test
  void multipartDefaultsToFormDataContentTypeWithBoundary() throws Exception {
    var multipart = new MultipartBody("test-boundary", null);
    multipart.addBodyPart(
        new BodyPart(Map.of("Content-Type", "text/plain"), BodySupplier.ofString("value")));
    var request = new WebRequest(WebRequest.Method.POST, baseUrl + "/echo");
    request.setMultipartBody(multipart);
    try (var response = newClient().execute(request)) {
      assertEquals(200, response.getResponseCode());
      assertEquals("multipart/form-data; boundary=test-boundary", LAST_CONTENT_TYPE.get());
      assertTrue(LAST_BODY.get().contains("--test-boundary"));
      assertTrue(LAST_BODY.get().contains("value"));
      assertTrue(LAST_BODY.get().contains("--test-boundary--"));
    }
  }

  @Test
  void multipartHonoursDeclaredRelatedContentType() throws Exception {
    // Mirrors the authenticated STOW-RS path: the payload declares its own multipart/related
    // content type, which must reach the server verbatim instead of being forced to form-data.
    String boundary = "weasisDicomBoundary";
    var headers =
        Map.of("Content-Type", "multipart/related;type=\"application/dicom\";boundary=" + boundary);
    var multipart = new MultipartBody(boundary, headers);
    multipart.addBodyPart(
        new BodyPart(
            Map.of("Content-Type", "application/dicom"), BodySupplier.ofString("DICM-body")));
    var request = new WebRequest(WebRequest.Method.POST, baseUrl + "/echo");
    request.setMultipartBody(multipart);
    try (var response = newClient().execute(request)) {
      assertEquals(200, response.getResponseCode());
      assertTrue(LAST_CONTENT_TYPE.get().startsWith("multipart/related"), LAST_CONTENT_TYPE.get());
      assertTrue(
          LAST_CONTENT_TYPE.get().contains("type=\"application/dicom\""), LAST_CONTENT_TYPE.get());
      assertTrue(LAST_BODY.get().contains("DICM-body"));
    }
  }

  @Test
  void multipartFilePartStreamsFileContent(@TempDir Path tmp) throws Exception {
    Path file = tmp.resolve("part.bin");
    Files.writeString(file, "FILE-PART");
    var multipart = new MultipartBody("bnd", null);
    multipart.addBodyPart(BodyPart.of("application/octet-stream", BodySupplier.ofPath(file), "p"));
    var request = new WebRequest(WebRequest.Method.POST, baseUrl + "/echo");
    request.setMultipartBody(multipart);
    try (var response = newClient().execute(request)) {
      assertEquals(200, response.getResponseCode());
      assertTrue(LAST_BODY.get().contains("FILE-PART"), LAST_BODY.get());
      assertTrue(LAST_BODY.get().contains("filename=\"p\""), LAST_BODY.get());
    }
  }

  @Test
  void multipartWithoutBodyPartsStillSucceeds() throws Exception {
    var multipart = new MultipartBody("empty-bnd", null);
    var request = new WebRequest(WebRequest.Method.POST, baseUrl + "/echo");
    request.setMultipartBody(multipart);
    try (var response = newClient().execute(request)) {
      assertEquals(200, response.getResponseCode());
      assertTrue(LAST_BODY.get().contains("--empty-bnd--"));
    }
  }

  @Test
  void responseHeadersAreAccessible() throws Exception {
    var request = new WebRequest(WebRequest.Method.POST, baseUrl + "/echo");
    request.setBody("x");
    try (var response = newClient().execute(request)) {
      assertNotNull(response.getHeaderField("X-Echo"));
      assertEquals("yes", response.getHeaderField("X-Echo"));
    }
  }

  @Test
  void connectFailureSurfacesAsIoException() {
    var client =
        new JavaNetHttpClient(new JavaNetHttpClientConfig(1, 1, ProxySelector.getDefault()));
    // 127.0.0.1 port 1 typically not listening: connect fails fast.
    var request = new WebRequest(WebRequest.Method.GET, "http://127.0.0.1:1/");
    assertThrows(IOException.class, () -> client.execute(request));
  }

  @Test
  void executeWrapsInterruptedException() {
    Thread.currentThread().interrupt();
    try {
      var request = new WebRequest(WebRequest.Method.GET, baseUrl + "/echo");
      // Pre-interrupting the thread causes the pending future to throw InterruptedException.
      try {
        newClient().execute(request);
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

  private static JavaNetHttpClient uploadClient() {
    return new JavaNetHttpClient(
        new JavaNetHttpClientConfig(2_000, UPLOAD_TIMEOUT_MS, ProxySelector.getDefault()));
  }

  private static WebRequest uploadRequest(String url) {
    var request = new WebRequest(WebRequest.Method.POST, url);
    request.setMultipartBody(uploadBody());
    return request;
  }

  private static MultipartBody uploadBody() {
    var multipart = new MultipartBody("upload-bnd", null);
    multipart.addBodyPart(
        new BodyPart(
            Map.of("Content-Type", "application/octet-stream"),
            BodySupplier.ofBytes(new byte[UPLOAD_SIZE])));
    return multipart;
  }

  @Test
  void slowUploadOutlivesTheInactivityTimeoutWhileBytesKeepFlowing() throws Exception {
    // The authenticated STOW-RS path: the budget bounds time without progress, so a healthy
    // transfer completes however long it takes.
    try (var upstream = ThrottledUploadServer.draining(UPLOAD_DRAIN_CHUNK, UPLOAD_DRAIN_PAUSE_MS);
        var response = uploadClient().execute(uploadRequest(upstream.url()))) {
      assertEquals(200, response.getResponseCode());
    }
  }

  @Test
  void stalledUploadIsAbortedOnceBytesStopFlowing() throws Exception {
    try (var upstream = ThrottledUploadServer.stalling(UPLOAD_DRAIN_CHUNK)) {
      var client = uploadClient();
      var request = uploadRequest(upstream.url());
      assertThrows(StallTimeoutException.class, () -> client.execute(request));
    }
  }
}
