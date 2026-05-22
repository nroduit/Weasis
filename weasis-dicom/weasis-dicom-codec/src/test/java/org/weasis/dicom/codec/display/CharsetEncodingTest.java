/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dcm4che3.data.SpecificCharacterSet;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link CharsetEncoding} — the mapping from DICOM Specific-Character-Set labels (PS3.3
 * §C.12.1.1.2) to JDK charset codes used when interpreting patient-identifying string fields.
 *
 * <p>A bug here can silently garble patient names containing non-ASCII characters (cyrillic,
 * arabic, kanji, hangul, …) — which in turn feeds {@code PatientComparator} and can produce a
 * mojibake mismatch between a referring system's record and Weasis's display. This is the
 * patient-identity hazard (WEA-004 R23) at the lowest layer of the DICOM text pipeline.
 *
 * <p>The two static lookup helpers ({@code fromLabel}, {@code fromCode}) and their fallback
 * behaviour are the critical surface:
 *
 * <ul>
 *   <li>{@code fromLabel} returns {@code null} for unknown labels — the caller must decide what to
 *       do (typically log + skip).
 *   <li>{@code fromCode} returns {@code ISO_IR_192} (UTF-8) for unknown codes — never null. This is
 *       the "fail safe to widest" policy: an unknown JDK charset name still gets *some* decoding
 *       attempt rather than crashing the load.
 * </ul>
 */
class CharsetEncodingTest {

  @Test
  void enum_exposes18EncodingsCoveringDicomDefinedTerms() {
    // DICOM PS3.3 §C.12.1.1.2 single-byte + multi-byte specific character sets plus GB18030,
    // plus a sentinel ASCII row with no DICOM label. If a future revision of PS3.3 adds new
    // defined terms, this assertion is the canary that catches the schema drift — bump the
    // expected count and add the corresponding entry to the enum.
    assertEquals(18, CharsetEncoding.values().length);
  }

  // ---------------------------------------------------------------------------
  // Accessors
  // ---------------------------------------------------------------------------

  @Test
  void getLabel_returnsDicomDefinedTermVerbatim() {
    assertEquals("ISO_IR 192", CharsetEncoding.ISO_IR_192.getLabel());
    assertEquals("ISO 2022 IR 87", CharsetEncoding.ISO_2022_IR_87.getLabel());
    assertEquals("GB18030", CharsetEncoding.GB18030.getLabel());
  }

  @Test
  void getLabel_asciiSentinelIsEmptyString() {
    // ASCII has no DICOM defined-term — the empty label signals "default / pre-PS3.3 era".
    assertEquals("", CharsetEncoding.ASCII.getLabel());
  }

  @Test
  void getCode_returnsJdkCharsetName() {
    assertEquals("UTF-8", CharsetEncoding.ISO_IR_192.getCode());
    assertEquals("US-ASCII", CharsetEncoding.ASCII.getCode());
    assertEquals("ISO-8859-1", CharsetEncoding.ISO_IR_100.getCode());
    assertEquals("ISO-8859-5", CharsetEncoding.ISO_IR_144.getCode());
    assertEquals("EUC-KR", CharsetEncoding.ISO_2022_IR_149.getCode());
    assertEquals("GB18030", CharsetEncoding.GB18030.getCode());
  }

  @Test
  void getReadableText_isNonNullAndNonBlankForEveryEntry() {
    // Drives the dropdown UI — a blank entry would be invisible to the user.
    for (CharsetEncoding encoding : CharsetEncoding.values()) {
      assertNotNull(encoding.getReadableText(), encoding.name());
      assertTrue(!encoding.getReadableText().isBlank(), encoding.name());
    }
  }

  // ---------------------------------------------------------------------------
  // SpecificCharacterSet bridge (dcm4che)
  // ---------------------------------------------------------------------------

  @Test
  void getSpecificCharacterSet_returnsDcm4cheInstance() {
    SpecificCharacterSet scs = CharsetEncoding.ISO_IR_192.getSpecificCharacterSet();
    assertNotNull(scs);
  }

  // ---------------------------------------------------------------------------
  // fromLabel — null on unknown (caller-decides policy)
  // ---------------------------------------------------------------------------

  @Test
  void fromLabel_resolvesAllKnownLabels() {
    for (CharsetEncoding encoding : CharsetEncoding.values()) {
      // ASCII's label is "" — fromLabel("") would resolve to ASCII (first match). Skip it
      // here since the lookup loop returns the first match in declaration order; testing
      // it would over-constrain that detail.
      if (encoding == CharsetEncoding.ASCII) {
        continue;
      }
      assertSame(encoding, CharsetEncoding.fromLabel(encoding.getLabel()), encoding.name());
    }
  }

  @Test
  void fromLabel_returnsNullForUnknownLabel() {
    assertNull(CharsetEncoding.fromLabel("ISO_IR 999"));
    assertNull(CharsetEncoding.fromLabel("bogus"));
  }

  @Test
  void fromLabel_returnsNullForNull() {
    assertNull(CharsetEncoding.fromLabel(null));
  }

  // ---------------------------------------------------------------------------
  // fromCode — fail-safe fallback to ISO_IR_192 (UTF-8)
  // ---------------------------------------------------------------------------

  @Test
  void fromCode_resolvesKnownCodes() {
    assertSame(CharsetEncoding.ISO_IR_192, CharsetEncoding.fromCode("UTF-8"));
    assertSame(CharsetEncoding.ISO_IR_100, CharsetEncoding.fromCode("ISO-8859-1"));
    assertSame(CharsetEncoding.GB18030, CharsetEncoding.fromCode("GB18030"));
    assertSame(CharsetEncoding.ISO_2022_IR_149, CharsetEncoding.fromCode("EUC-KR"));
  }

  @Test
  void fromCode_unknownCodeFallsBackToUtf8() {
    // The fail-safe policy: an unknown JDK charset name still gets a valid encoding back
    // (UTF-8), not null. The caller can then attempt to decode with UTF-8 which catches
    // the most common modern case.
    assertSame(CharsetEncoding.ISO_IR_192, CharsetEncoding.fromCode("X-not-a-real-charset"));
    assertSame(CharsetEncoding.ISO_IR_192, CharsetEncoding.fromCode("windows-1252"));
  }

  @Test
  void fromCode_nullFallsBackToUtf8() {
    // Symmetric to the unknown-code path; null DICOM SpecificCharacterSet attribute is
    // legitimately common (pre-PS3.3 files, defaulted exports) and must not crash.
    assertSame(CharsetEncoding.ISO_IR_192, CharsetEncoding.fromCode(null));
  }

  // ---------------------------------------------------------------------------
  // toString — drives the dropdown text the user clicks on
  // ---------------------------------------------------------------------------

  @Test
  void toString_combinesReadableTextAndCode() {
    String text = CharsetEncoding.ISO_IR_192.toString();
    assertTrue(text.contains(CharsetEncoding.ISO_IR_192.getReadableText()), text);
    assertTrue(text.contains("UTF-8"), text);
  }

  @Test
  void toString_isStableForAllEntries() {
    for (CharsetEncoding encoding : CharsetEncoding.values()) {
      String s = encoding.toString();
      assertNotNull(s);
      assertTrue(s.contains(encoding.getCode()), encoding.name());
    }
  }
}
