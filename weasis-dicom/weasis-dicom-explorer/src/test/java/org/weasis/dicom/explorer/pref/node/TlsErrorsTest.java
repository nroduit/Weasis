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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.weasis.dicom.explorer.pref.node.TlsErrors.Kind;

class TlsErrorsTest {

  @Test
  void aHandshakeFailureIsNamedForWhatItIs() {
    String untrusted =
        "DICOM Echo failed: PKIX path building failed: unable to find valid certification path to"
            + " requested target";
    assertAll(
        () -> assertEquals(Kind.UNTRUSTED, TlsErrors.kindOf(untrusted)),
        () -> assertEquals(Kind.EXPIRED, TlsErrors.kindOf("certificate_expired")),
        () -> assertEquals(Kind.EXPIRED, TlsErrors.kindOf("NotAfter: Jan 1 2025")),
        () ->
            assertEquals(Kind.PROTOCOL, TlsErrors.kindOf("Received fatal alert: protocol_version")),
        () -> assertEquals(Kind.PROTOCOL, TlsErrors.kindOf("no cipher suites in common")),
        () ->
            assertEquals(
                Kind.HOSTNAME, TlsErrors.kindOf("No subject alternative DNS name matching pacs")),
        () -> assertEquals(Kind.OTHER, TlsErrors.kindOf("Connection refused")),
        () -> assertEquals(Kind.OTHER, TlsErrors.kindOf(null)),
        () ->
            assertTrue(
                TlsErrors.describe("pacs", untrusted)
                    .startsWith("The certificate of pacs is not trusted")),
        () -> assertTrue(TlsErrors.describe("pacs", untrusted).endsWith(untrusted)),
        () -> assertEquals("Connection refused", TlsErrors.describe("pacs", "Connection refused")),
        () -> assertTrue(TlsErrors.describe(null, "protocol_version").contains("legacy option")));
  }
}
