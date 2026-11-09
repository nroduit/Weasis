/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.audit;

import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.weasis.core.api.util.SiteDocuments;

/**
 * The site document {@code audit.json}: whether the trail is on, where the audit record repository
 * listens, which classes of events are recorded and how long the local file is kept. The user layer
 * cannot turn off what the site turns on; without the document the trail is off.
 *
 * <pre>
 * {"schema": 1, "enabled": true, "transport": "tls", "host": "arr.hospital.org", "port": 6514,
 *  "events": ["application", "access", "transfer", "command", "security"],
 *  "file": true, "retentionDays": 90, "clientCertificate": "site.arr"}
 * </pre>
 *
 * @param enabled whether the trail runs
 * @param transport {@code udp} (RFC 5424 over UDP), {@code tls} (RFC 5425) or {@code file} (the
 *     local file only)
 * @param host the audit record repository
 * @param port its port, 514 for UDP and 6514 for TLS by default
 * @param events the classes recorded: application, access, transfer, command, security
 * @param file whether the local JSON Lines file is written too (always with {@code file})
 * @param retentionDays how long the local files are kept, 0 for ever
 * @param clientCertificate the secret store entry of a client certificate for TLS, or null
 */
public record AuditConfig(
    boolean enabled,
    String transport,
    String host,
    int port,
    Set<String> events,
    boolean file,
    int retentionDays,
    String clientCertificate) {

  public static final String DOCUMENT = "audit.json"; // NON-NLS
  public static final Set<String> ALL_EVENTS =
      Set.of("application", "access", "transfer", "command", "security"); // NON-NLS

  /** The trail off. */
  public static final AuditConfig OFF =
      new AuditConfig(false, "file", null, 0, ALL_EVENTS, true, 90, null); // NON-NLS

  public AuditConfig {
    transport = transport == null ? "file" : transport.toLowerCase(Locale.ROOT); // NON-NLS
    events = events == null || events.isEmpty() ? ALL_EVENTS : Set.copyOf(events);
    if (port <= 0) {
      port = "tls".equals(transport) ? 6514 : 514; // NON-NLS
    }
  }

  public boolean records(String eventClass) {
    return events.contains(eventClass);
  }

  public boolean sendsSyslog() {
    return enabled && host != null && !host.isBlank() && !"file".equals(transport); // NON-NLS
  }

  public boolean isTls() {
    return "tls".equals(transport); // NON-NLS
  }

  /** The configuration of the site, {@link #OFF} without a document. */
  public static AuditConfig load() {
    return SiteDocuments.read(DOCUMENT).map(AuditConfig::fromJson).orElse(OFF);
  }

  public static AuditConfig fromJson(JsonObject json) {
    if (json == null) {
      return OFF;
    }
    Set<String> events =
        json.containsKey("events") // NON-NLS
            ? Set.copyOf(
                json.getJsonArray("events").getValuesAs(JsonString.class).stream() // NON-NLS
                    .map(JsonString::getString)
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .toList())
            : ALL_EVENTS;
    return new AuditConfig(
        json.getBoolean("enabled", false), // NON-NLS
        json.getString("transport", "file"), // NON-NLS
        json.getString("host", null), // NON-NLS
        json.getInt("port", 0), // NON-NLS
        events,
        json.getBoolean("file", true), // NON-NLS
        json.getInt("retentionDays", 90), // NON-NLS
        json.getString("clientCertificate", null)); // NON-NLS
  }

  /** The classes a configuration may name, for a message. */
  public static Optional<String> eventClassOf(String eventName) {
    if (eventName == null) {
      return Optional.empty();
    }
    return switch (eventName) {
      case "weasis:ready", "weasis:shutdown" -> Optional.of("application"); // NON-NLS
      case "dicom:study.opened" -> Optional.of("access"); // NON-NLS
      case "dicom:loading.started", "dicom:loading.stopped" -> Optional.of("transfer"); // NON-NLS
      case "command:finished" -> Optional.of("command"); // NON-NLS
      case "weasis:security.alert" -> Optional.of("security"); // NON-NLS
      default -> Optional.empty();
    };
  }

  public static List<String> eventNames() {
    return List.of(
        "weasis:ready", // NON-NLS
        "weasis:shutdown", // NON-NLS
        "dicom:study.opened", // NON-NLS
        "dicom:loading.started", // NON-NLS
        "dicom:loading.stopped", // NON-NLS
        "command:finished", // NON-NLS
        "weasis:security.alert"); // NON-NLS
  }
}
