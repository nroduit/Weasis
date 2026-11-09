/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.pref.node;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.dicom.param.TlsOptions;

class NodeTlsTest {

  @Test
  void aNodeWithoutTlsReadsAsOffAndWritesNothing() {
    JsonObject plain =
        Json.createObjectBuilder().add("aeTitle", "PACS").add("port", 104).build(); // NON-NLS
    NodeTls tls = NodeTls.fromJson(plain);
    assertAll(
        () -> assertEquals(NodeTls.OFF, tls),
        () -> assertFalse(tls.enabled()),
        () -> assertNull(assertThrowsNothing(() -> tls.tlsOptions("user.node.pacs"))),
        () -> assertEquals("TLS off", tls.describe()));
  }

  private static TlsOptions assertThrowsNothing(ThrowingSupplier supplier) {
    try {
      return supplier.get();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  interface ThrowingSupplier {
    TlsOptions get() throws IOException;
  }

  @Test
  void theDocumentRoundTripsAndStatesTheProtocols() {
    NodeTls tls = new NodeTls(true, NodeTls.Trust.FILE, "/etc/pki/archive.p12", true);
    JsonObject json = Json.createObjectBuilder().add(NodeTls.T_TLS, tls.toJson()).build();
    NodeTls read = NodeTls.fromJson(json);
    JsonObject written = tls.toJson();
    assertAll(
        () -> assertEquals(tls, read),
        () -> assertTrue(written.getBoolean("enabled")),
        () -> assertEquals("file", written.getString("trust")),
        () -> assertEquals("/etc/pki/archive.p12", written.getString("trustStore")),
        () -> assertTrue(written.getBoolean("legacy")),
        () -> assertEquals(4, written.getJsonArray("protocols").size()),
        () -> assertEquals(TlsOptions.MODERN_TLS, new NodeTls(true, null, null, false).protocols()),
        () ->
            assertEquals(
                NodeTls.MODERN_CIPHER_SUITES, new NodeTls(true, null, null, false).cipherSuites()),
        () -> assertTrue(tls.cipherSuites().containsAll(TlsOptions.TLS)),
        () -> assertEquals("TLS legacy, trust /etc/pki/archive.p12", tls.describe()));
  }

  @Test
  void aTrustStoreFileGivesTheOptionsOfAServerAuthenticatedAssociation(@TempDir Path dir)
      throws Exception {
    Path store = dir.resolve("archive.p12");
    KeyStore empty = KeyStore.getInstance("PKCS12"); // NON-NLS
    empty.load(null, null);
    try (OutputStream out = Files.newOutputStream(store)) {
      empty.store(out, "secret".toCharArray()); // NON-NLS
    }
    NodeTls tls = new NodeTls(true, NodeTls.Trust.FILE, store.toString(), false);
    TlsOptions options = tls.tlsOptions(null);
    assertAll(
        () -> assertFalse(options.tlsNeedClientAuth(), "the server is authenticated, not Weasis"),
        () -> assertEquals(store.toUri().toString(), options.truststoreURL()),
        () -> assertEquals(TrustStores.PKCS12, options.truststoreType()),
        () -> assertEquals("", options.truststorePass(), "no node id, no secret"),
        () -> assertTrue(options.keystoreURL().endsWith("empty-keystore.p12")),
        () -> assertEquals(TlsOptions.MODERN_TLS, options.tlsProtocols()),
        () -> assertEquals(NodeTls.MODERN_CIPHER_SUITES, options.cipherSuites()),
        () -> assertEquals(TrustStores.JKS, TrustStores.typeOf(Path.of("x/archive.jks"))));

    NodeTls missing =
        new NodeTls(true, NodeTls.Trust.FILE, dir.resolve("none.p12").toString(), false);
    IOException e = assertThrows(IOException.class, () -> missing.tlsOptions(null));
    assertTrue(e.getMessage().contains("not readable"), e.getMessage());
  }
}
