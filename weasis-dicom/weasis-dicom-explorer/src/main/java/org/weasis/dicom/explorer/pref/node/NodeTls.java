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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.dcm4che3.net.Connection;
import org.weasis.core.api.service.SecretStore;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.TlsOptions;

/**
 * The TLS part of a DICOM node document: whether an association to the node is secured, which
 * certificates are trusted to authenticate it, and whether an old archive needs the legacy
 * protocols. Weasis acts as the SCU and authenticates the server only; a client certificate (mutual
 * authentication) is not offered.
 *
 * <pre>
 * "tls": {"enabled": true, "trust": "platform", "trustStore": null, "legacy": false}
 * </pre>
 *
 * @param enabled whether the association uses TLS
 * @param trust the certificates that authenticate the archive: the platform store (the same
 *     certificates as HTTPS) or a trust store file
 * @param trustStore the PKCS12 or JKS file of {@link Trust#FILE}; its password is in the secret
 *     store under the node id, never in the document
 * @param legacy whether TLS 1.0 / 1.1 and the dcm4che cipher suites are offered too, for an archive
 *     that cannot do better; logged at every association
 */
public record NodeTls(boolean enabled, Trust trust, String trustStore, boolean legacy) {

  /** Where the certificates that authenticate the archive come from. */
  public enum Trust {
    /** The trust store the platform certificates were loaded into, as for HTTPS. */
    PLATFORM,
    /** A trust store file of the site, for an archive certificate the platform does not know. */
    FILE;

    static Trust of(String value) {
      return value == null ? PLATFORM : valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
  }

  /** No TLS: the association is in clear, as every node before 5.0. */
  public static final NodeTls OFF = new NodeTls(false, Trust.PLATFORM, null, false);

  static final String T_TLS = "tls"; // NON-NLS
  static final String T_ENABLED = "enabled"; // NON-NLS
  static final String T_TRUST = "trust"; // NON-NLS
  static final String T_TRUST_STORE = "trustStore"; // NON-NLS
  static final String T_LEGACY = "legacy"; // NON-NLS
  static final String T_PROTOCOLS = "protocols"; // NON-NLS

  /** The name of the trust store password in the secret store, under the node id. */
  public static final String SECRET_TRUST_STORE_PASSWORD = "truststorePassword"; // NON-NLS

  /** The cipher suites of a current JDK, forward secrecy and AEAD only. */
  public static final List<String> MODERN_CIPHER_SUITES =
      List.of(
          "TLS_AES_256_GCM_SHA384",
          "TLS_AES_128_GCM_SHA256",
          "TLS_CHACHA20_POLY1305_SHA256",
          "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
          "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
          "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
          "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");

  /** What an old archive offers: the dcm4che defaults, in front of the modern ones. */
  static final List<String> LEGACY_PROTOCOLS = List.of("TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3");

  public NodeTls {
    trust = trust == null ? Trust.PLATFORM : trust;
    trustStore = trustStore == null || trustStore.isBlank() ? null : trustStore.trim();
  }

  /** The TLS of a node document, {@link #OFF} when the document has none. */
  static NodeTls fromJson(JsonObject json) {
    if (json == null || !json.containsKey(T_TLS)) {
      return OFF;
    }
    JsonObject tls = json.getJsonObject(T_TLS);
    return new NodeTls(
        tls.getBoolean(T_ENABLED, true),
        Trust.of(tls.getString(T_TRUST, null)),
        tls.getString(T_TRUST_STORE, null),
        tls.getBoolean(T_LEGACY, false));
  }

  /** The object a node document carries; the protocols are stated for a reader of the file. */
  public JsonObject toJson() {
    JsonObjectBuilder b =
        Json.createObjectBuilder()
            .add(T_ENABLED, enabled)
            .add(T_TRUST, trust.name().toLowerCase(Locale.ROOT));
    if (trustStore != null) {
      b.add(T_TRUST_STORE, trustStore);
    }
    b.add(T_LEGACY, legacy);
    var protocols = Json.createArrayBuilder();
    protocols().forEach(protocols::add);
    b.add(T_PROTOCOLS, protocols);
    return b.build();
  }

  /** The protocol versions offered. */
  public List<String> protocols() {
    return legacy ? LEGACY_PROTOCOLS : TlsOptions.MODERN_TLS;
  }

  /** The cipher suites offered: the modern ones, with the dcm4che list first for an old archive. */
  public List<String> cipherSuites() {
    if (!legacy) {
      return MODERN_CIPHER_SUITES;
    }
    List<String> suites = new ArrayList<>(TlsOptions.TLS);
    suites.addAll(MODERN_CIPHER_SUITES);
    return List.copyOf(suites);
  }

  /** A short description for a tooltip or a log line. */
  public String describe() {
    if (!enabled) {
      return "TLS off"; // NON-NLS
    }
    return "TLS "
        + (legacy ? "legacy" : "1.2+") // NON-NLS
        + ", trust " // NON-NLS
        + (trust == Trust.FILE ? trustStore : "platform"); // NON-NLS
  }

  /**
   * The options of an association to the node, or null when TLS is off.
   *
   * @param nodeId the node id, which keys the trust store password in the secret store
   * @throws IOException when the trust store file is missing or the platform store cannot be
   *     written for dcm4che
   */
  public TlsOptions tlsOptions(String nodeId) throws IOException {
    return tlsOptions(nodeId, null);
  }

  /**
   * The options with the trust store password given, for a test before the node is saved.
   *
   * @param password the password of the trust store file, null for the one of the secret store
   */
  public TlsOptions tlsOptions(String nodeId, String password) throws IOException {
    if (!enabled) {
      return null;
    }
    Path store;
    String type;
    String secret;
    if (trust == Trust.FILE) {
      if (trustStore == null) {
        throw new IOException("TLS with a trust store file, but no file is given");
      }
      store = Path.of(trustStore);
      if (!Files.isReadable(store)) {
        throw new IOException("The trust store file is not readable: " + store);
      }
      type = TrustStores.typeOf(store);
      secret =
          password != null
              ? password
              : nodeId == null
                  ? ""
                  : SecretStore.getInstance().get(nodeId, SECRET_TRUST_STORE_PASSWORD).orElse("");
    } else {
      store = TrustStores.platformTrustStore();
      type = TrustStores.PKCS12;
      secret = TrustStores.PASSWORD;
    }
    // dcm4che builds a key manager even when no client certificate is needed: an empty store
    Path keyStore = TrustStores.emptyKeyStore();
    return new TlsOptions(
        cipherSuites(),
        protocols(),
        false,
        keyStore.toUri().toString(),
        TrustStores.PKCS12,
        TrustStores.PASSWORD,
        TrustStores.PASSWORD,
        store.toUri().toString(),
        type,
        secret);
  }

  /** Puts the TLS of the node on the parameters of an association; nothing when TLS is off. */
  public void apply(AdvancedParams params, String nodeId) throws IOException {
    Objects.requireNonNull(params);
    if (enabled) {
      params.setTlsOptions(tlsOptions(nodeId));
    }
  }

  /**
   * Secures a connection built by hand (the print SCU): the same configuration dcm4che gets from
   * the parameters of the other associations.
   */
  public void configure(Connection local, Connection remote, String nodeId) throws IOException {
    if (!enabled) {
      return;
    }
    AdvancedParams params = new AdvancedParams();
    params.setTlsOptions(tlsOptions(nodeId));
    params.configureTLS(local, remote);
  }
}
