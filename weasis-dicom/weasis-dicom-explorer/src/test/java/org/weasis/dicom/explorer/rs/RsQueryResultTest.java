/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.rs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the DICOMweb launch by PatientID against a local QIDO-RS server. {@code buildFromPatientID}
 * logs its failures, so the series queries it sends tell which studies it kept and in which order.
 */
class RsQueryResultTest {

  private static final String OLD_CT = "1.2.3.1";
  private static final String NEW_MR = "1.2.3.2";

  /** Deliberately oldest first: the sort must put the most recent study first. */
  private static final String STUDIES =
      "[" + study(OLD_CT, "20200101", "CT") + "," + study(NEW_MR, "20240101", "MR") + "]";

  private final List<String> seriesQueries = new CopyOnWriteArrayList<>();
  private final RsQueryParams params = mock(RsQueryParams.class);
  private HttpServer server;

  private static String study(String uid, String date, String modality) {
    return """
        {"0020000D":{"vr":"UI","Value":["%s"]},\
        "00080020":{"vr":"DA","Value":["%s"]},\
        "00080061":{"vr":"CS","Value":["%s"]}}"""
        .formatted(uid, date, modality);
  }

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/rs/studies", this::answer);
    server.start();

    when(params.getBaseUrl())
        .thenReturn(
            "http://%s:%d/rs"
                .formatted(server.getAddress().getHostString(), server.getAddress().getPort()));
    when(params.getProperties()).thenReturn(new Properties());
    when(params.getQueryHeaders()).thenReturn(Map.of());
    when(params.getRetrieveHeaders()).thenReturn(Map.of());
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  private void answer(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    String body = "[]";
    if (path.endsWith("/series")) {
      seriesQueries.add(path.substring("/rs/studies/".length(), path.lastIndexOf('/')));
    } else {
      body = STUDIES;
    }
    byte[] content = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/dicom+json");
    exchange.sendResponseHeaders(200, content.length);
    try (exchange) {
      exchange.getResponseBody().write(content);
    }
  }

  private List<String> queryByPatientId() {
    new RsQueryResult(params, null, false).buildFromPatientID(List.of("PAT1"));
    return seriesQueries;
  }

  @Test
  void patientStudiesAreLoadedMostRecentFirst() {
    assertEquals(List.of(NEW_MR, OLD_CT), queryByPatientId());
  }

  @Test
  void mostRecentResultsKeepsTheLatestStudies() {
    when(params.getMostRecentResults()).thenReturn("1");

    assertEquals(List.of(NEW_MR), queryByPatientId());
  }

  @Test
  void modalityFilterRemovesTheOtherStudies() {
    when(params.getModalitiesInStudy()).thenReturn("CT");

    assertEquals(List.of(OLD_CT), queryByPatientId());
  }
}
