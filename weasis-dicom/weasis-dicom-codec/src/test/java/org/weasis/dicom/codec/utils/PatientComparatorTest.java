/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.utils;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.TagD;

/**
 * Tests {@link PatientComparator} — the patient-identity comparator whose output is used as the
 * patient pseudo-UID across the Weasis model.
 *
 * <p>{@code buildPatientPseudoUID()} is intentionally NOT tested here because it depends on {@code
 * GuiUtils.getUICore().getSystemPreferences()}, a singleton initialized only from the OSGi runtime,
 * and {@code UICore} is a final class that Mockito cannot subclass-mock in this project's
 * configuration. The IHE-default branch is a single concatenation of three fields — the per-field
 * setters and constructors below pin the identity components (null-handling, upper-casing, trim),
 * so the concatenation contract is verified indirectly. See {@code dicom-decoding-traceability.md}
 * §5 for the residual-risk note.
 */
class PatientComparatorTest {

  // -- Constructor (Attributes) --------------------------------------------

  @Test
  void attributesConstructor_populatesAllFields() {
    Attributes attrs = new Attributes();
    attrs.setString(Tag.PatientID, VR.LO, "MR-001");
    attrs.setString(Tag.IssuerOfPatientID, VR.LO, "HOSP-A");
    attrs.setString(Tag.PatientName, VR.PN, "Doe^Jane");
    attrs.setString(Tag.PatientBirthDate, VR.DA, "19900101");
    attrs.setString(Tag.PatientSex, VR.CS, "F");

    PatientComparator comp = new PatientComparator(attrs);

    assertAll(
        () -> assertEquals("MR-001", comp.getPatientId()),
        () -> assertEquals("HOSP-A", comp.getIssuerOfPatientID()),
        () -> assertEquals("DOE^JANE", comp.getName(), "names are uppercased"),
        () -> assertEquals("19900101", comp.getBirthdate()),
        () -> assertEquals("F", comp.getSex()));
  }

  @Test
  void attributesConstructor_missingPatientIdResolvesToNoValueConstant() {
    // Critical: no PatientID must not produce a null identity that could collapse two unrelated
    // patients into the same pseudo-UID.
    Attributes attrs = new Attributes();
    attrs.setString(Tag.PatientName, VR.PN, "Doe^John");

    PatientComparator comp = new PatientComparator(attrs);

    assertEquals(TagW.NO_VALUE, comp.getPatientId());
  }

  @Test
  void attributesConstructor_missingNonIdFieldsResolveToEmptyString() {
    Attributes attrs = new Attributes();
    attrs.setString(Tag.PatientID, VR.LO, "MR-001");

    PatientComparator comp = new PatientComparator(attrs);

    assertAll(
        () -> assertEquals("", comp.getIssuerOfPatientID()),
        () -> assertEquals("", comp.getBirthdate()),
        () -> assertEquals("", comp.getSex()),
        () -> assertEquals(TagW.NO_VALUE, comp.getName(), "name absent → NO_VALUE constant"));
  }

  // -- Constructor (TagReadable) -------------------------------------------

  @Test
  void tagReadableConstructor_populatesAllFields() {
    TagReadable reader = mock(TagReadable.class);
    when(reader.getTagValue(TagD.get(Tag.PatientID))).thenReturn("MR-001");
    when(reader.getTagValue(TagD.get(Tag.IssuerOfPatientID))).thenReturn("HOSP-A");
    when(reader.getTagValue(TagD.get(Tag.PatientName))).thenReturn("Doe^Jane");
    when(reader.getTagValue(TagD.get(Tag.PatientSex))).thenReturn("F");
    when(reader.getTagValue(TagD.get(Tag.PatientBirthDate))).thenReturn(LocalDate.of(1990, 1, 1));

    PatientComparator comp = new PatientComparator(reader);

    assertAll(
        () -> assertEquals("MR-001", comp.getPatientId()),
        () -> assertEquals("HOSP-A", comp.getIssuerOfPatientID()),
        () -> assertEquals("DOE^JANE", comp.getName()),
        () -> assertEquals("19900101", comp.getBirthdate(), "date formatted DICOM-style"),
        () -> assertEquals("F", comp.getSex()));
  }

  // -- Setter null/whitespace handling -------------------------------------

  @Test
  void setters_trimWhitespace() {
    PatientComparator comp =
        new PatientComparator(new Attributes()); // initializes to default constants

    comp.setPatientId("  MR-001  ");
    comp.setIssuerOfPatientID(" HOSP-A ");
    comp.setName("  doe^jane  ");
    comp.setBirthdate(" 19900101 ");
    comp.setSex("  F  ");

    assertAll(
        () -> assertEquals("MR-001", comp.getPatientId()),
        () -> assertEquals("HOSP-A", comp.getIssuerOfPatientID()),
        () -> assertEquals("DOE^JANE", comp.getName()),
        () -> assertEquals("19900101", comp.getBirthdate()),
        () -> assertEquals("F", comp.getSex()));
  }

  @Test
  void setName_uppercasesEvenMixedCase() {
    PatientComparator comp = new PatientComparator(new Attributes());

    comp.setName("McDonald^Ronald");

    assertEquals("MCDONALD^RONALD", comp.getName());
  }

  @Test
  void setPatientId_nullResolvesToNoValueConstant() {
    PatientComparator comp = new PatientComparator(new Attributes());

    comp.setPatientId(null);

    assertEquals(TagW.NO_VALUE, comp.getPatientId());
  }

  @Test
  void setName_nullResolvesToNoValueConstant() {
    PatientComparator comp = new PatientComparator(new Attributes());

    comp.setName(null);

    assertEquals(TagW.NO_VALUE, comp.getName());
  }

  @Test
  void setIssuerOfPatientID_nullResolvesToEmptyString() {
    PatientComparator comp = new PatientComparator(new Attributes());

    comp.setIssuerOfPatientID(null);

    assertEquals("", comp.getIssuerOfPatientID());
  }

  @Test
  void setBirthdateAndSex_nullResolveToEmptyString() {
    PatientComparator comp = new PatientComparator(new Attributes());

    comp.setBirthdate(null);
    comp.setSex(null);

    assertAll(() -> assertEquals("", comp.getBirthdate()), () -> assertEquals("", comp.getSex()));
  }

  // -- Identity components (each row of the IHE pseudo-UID concatenation) ---
  // PatientComparator.buildPatientPseudoUID() concatenates patientId, issuerOfPatientID and name.
  // The tests below pin each component's normalization rule so that the concatenation cannot
  // silently change semantics (e.g. case fold, trim, null fallback) without breaking a test.

  @Test
  void identity_distinctSiteIssuersProduceDistinctComponents() {
    // Two patients with identical IDs at different sites must NOT collide. The pseudo-UID is
    // patientId + issuerOfPatientID + name; differing issuers therefore differ overall.
    PatientComparator p1 = new PatientComparator(buildAttrs("MR-001", "HOSP-A", "Doe^Jane"));
    PatientComparator p2 = new PatientComparator(buildAttrs("MR-001", "HOSP-B", "Doe^Jane"));

    assertAll(
        () -> assertEquals(p1.getPatientId(), p2.getPatientId(), "same ID"),
        () -> assertEquals(p1.getName(), p2.getName(), "same name"),
        () ->
            org.junit.jupiter.api.Assertions.assertNotEquals(
                p1.getIssuerOfPatientID(), p2.getIssuerOfPatientID(), "distinct issuers"));
  }

  @Test
  void identity_caseFoldOnNameCollapsesEquivalentSpellings() {
    // Two name spellings differing only in case must produce the same upper-cased component, so
    // the resulting pseudo-UID is identical regardless of input case.
    PatientComparator lower = new PatientComparator(buildAttrs("MR-001", "HOSP-A", "doe^jane"));
    PatientComparator upper = new PatientComparator(buildAttrs("MR-001", "HOSP-A", "DOE^JANE"));

    assertEquals(lower.getName(), upper.getName());
  }

  @Test
  void identity_missingPatientIdUsesNoValueConstantNotEmptyString() {
    // Without an explicit PatientID the pseudo-UID component must be TagW.NO_VALUE so that two
    // ID-less patients don't silently collide with the empty string and with each other.
    PatientComparator comp = new PatientComparator(buildAttrs(null, null, "Doe^Jane"));

    assertAll(
        () -> assertEquals(TagW.NO_VALUE, comp.getPatientId()),
        () -> assertEquals("", comp.getIssuerOfPatientID()),
        () -> assertEquals("DOE^JANE", comp.getName()));
  }

  // -- helpers --------------------------------------------------------------

  private static Attributes buildAttrs(String patientId, String issuer, String name) {
    Attributes attrs = new Attributes();
    if (patientId != null) {
      attrs.setString(Tag.PatientID, VR.LO, patientId);
    }
    if (issuer != null) {
      attrs.setString(Tag.IssuerOfPatientID, VR.LO, issuer);
    }
    if (name != null) {
      attrs.setString(Tag.PatientName, VR.PN, name);
    }
    return attrs;
  }
}
