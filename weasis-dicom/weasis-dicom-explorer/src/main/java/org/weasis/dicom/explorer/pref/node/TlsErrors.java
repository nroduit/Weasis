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

import java.util.Locale;

/**
 * Names a TLS handshake failure for what it is, so the user and the command caller learn whether
 * the certificate is untrusted, expired, or the archive and Weasis share no protocol or cipher
 * suite, instead of a generic connection failure.
 */
public final class TlsErrors {

  private TlsErrors() {}

  /** The kind of failure, for a caller that acts on it. */
  public enum Kind {
    UNTRUSTED,
    EXPIRED,
    PROTOCOL,
    HOSTNAME,
    OTHER;

    public String key() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  /** The kind a dcm4che or JDK message points at; {@link Kind#OTHER} when it is not about TLS. */
  public static Kind kindOf(String message) {
    if (message == null) {
      return Kind.OTHER;
    }
    String text = message.toLowerCase(Locale.ROOT);
    if (text.contains("certificate_expired")
        || text.contains("notafter")
        || text.contains("expired")) { // NON-NLS
      return Kind.EXPIRED;
    }
    if (text.contains("pkix") // NON-NLS
        || text.contains("unable to find valid certification path") // NON-NLS
        || text.contains("certificate_unknown") // NON-NLS
        || text.contains("unknown_ca") // NON-NLS
        || text.contains("self signed") // NON-NLS
        || text.contains("self-signed")) { // NON-NLS
      return Kind.UNTRUSTED;
    }
    if (text.contains("no subject alternative") // NON-NLS
        || text.contains("hostname") && text.contains("certificate")) { // NON-NLS
      return Kind.HOSTNAME;
    }
    if (text.contains("protocol_version") // NON-NLS
        || text.contains("no cipher suites in common") // NON-NLS
        || text.contains("handshake_failure") // NON-NLS
        || text.contains("no appropriate protocol") // NON-NLS
        || text.contains("sslhandshakeexception") // NON-NLS
        || text.contains("unsupported_certificate")) { // NON-NLS
      return Kind.PROTOCOL;
    }
    return Kind.OTHER;
  }

  /**
   * The message to show: what failed and what to do, with the original text kept at the end.
   *
   * @param host the host the association went to
   */
  public static String describe(String host, String message) {
    Kind kind = kindOf(message);
    String where = host == null ? "the archive" : host;
    return switch (kind) {
      case UNTRUSTED ->
          "The certificate of "
              + where
              + " is not trusted: its issuer is not in the trust store (add the archive's CA to"
              + " the platform certificates, or point the node at a trust store file). "
              + message;
      case EXPIRED -> "The certificate of " + where + " has expired. " + message;
      case HOSTNAME ->
          "The certificate of " + where + " was issued for another name than the host. " + message;
      case PROTOCOL ->
          "No TLS protocol or cipher suite in common with "
              + where
              + " (an old archive may need the legacy option of the node). "
              + message;
      case OTHER -> message;
    };
  }
}
