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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.net.URLParameters;

@DisplayNameGeneration(ReplaceUnderscores.class)
class RsQueryResultTest {

  private static final String STUDIES_JSON =
      "[{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"1.2.4\"]}},"
          + "{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"1.2.3\"]}}]";

  private static HttpServer server;

  @BeforeAll
  static void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/studies",
        exchange -> {
          byte[] body = STUDIES_JSON.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/dicom+json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
  }

  @AfterAll
  static void stopServer() {
    server.stop(0);
  }

  @Test
  void parseJSON_returns_a_list_that_can_be_sorted_and_filtered() throws Exception {
    String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/studies";

    List<Attributes> studies = RsQueryResult.parseJSON(url, null, new URLParameters());

    assertEquals(2, studies.size());
    // buildFromPatientID sorts the studies and then removes the ones filtered out
    assertDoesNotThrow(
        () -> studies.sort(Comparator.comparing(study -> study.getString(Tag.StudyInstanceUID))));
    assertEquals("1.2.3", studies.get(0).getString(Tag.StudyInstanceUID));
    assertDoesNotThrow(() -> studies.remove(0));
    assertEquals(1, studies.size());
  }
}
