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

import java.util.LinkedHashMap;
import java.util.Map;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.TagD;

/**
 * Tests {@link DicomMediaUtils#enableAnonymizationProfile(boolean)} — the static toggle that flags
 * which DICOM tags must be suppressed from on-screen display when the user enables anonymization.
 *
 * <p>Coverage focus is two-fold:
 *
 * <ol>
 *   <li>Every tag in the project's documented PHI list (see DicomMediaUtils.java lines 105..123)
 *       plus {@link TagW#PatientPseudoUID} must get {@code anonymizationType=1} when the profile is
 *       enabled and revert to 0 when disabled.
 *   <li>Tags that are explicitly NOT in the PHI list (Modality, StudyDate, ImageType…) must be
 *       unaffected — otherwise the toggle would silently broaden or narrow the suppression scope.
 * </ol>
 *
 * <p>The flag lives on static, singleton {@link TagW} instances; tests below snapshot the relevant
 * flags in {@code @BeforeEach} and restore them in {@code @AfterEach} so the assertions are
 * independent of any other test that might have flipped the profile globally.
 */
class AnonymizationProfileTest {

  /** Tags from DicomMediaUtils.enableAnonymizationProfile — kept in lockstep with the source. */
  private static final int[] PHI_TAGS = {
    Tag.PatientName,
    Tag.PatientID,
    Tag.PatientSex,
    Tag.PatientBirthDate,
    Tag.PatientBirthTime,
    Tag.PatientAge,
    Tag.PatientComments,
    Tag.PatientWeight,
    Tag.AccessionNumber,
    Tag.StudyID,
    Tag.InstitutionalDepartmentName,
    Tag.InstitutionName,
    Tag.ReferringPhysicianName,
    Tag.StudyDescription,
    Tag.SeriesDescription,
    Tag.StationName,
    Tag.ImageComments
  };

  /** A representative set of NON-PHI tags that must NOT be flagged by the profile. */
  private static final int[] NON_PHI_TAGS = {
    Tag.Modality,
    Tag.StudyDate,
    Tag.ImageType,
    Tag.SOPClassUID,
    Tag.TransferSyntaxUID,
    Tag.PixelSpacing,
    Tag.WindowCenter,
    Tag.WindowWidth,
    Tag.RescaleSlope,
    Tag.RescaleIntercept
  };

  private final Map<Integer, Integer> savedPhiState = new LinkedHashMap<>();
  private final Map<Integer, Integer> savedNonPhiState = new LinkedHashMap<>();
  private int savedPseudoUidState;

  @BeforeEach
  void snapshotFlags() {
    for (int id : PHI_TAGS) {
      TagW t = TagD.getNullable(id);
      if (t != null) {
        savedPhiState.put(id, t.getAnonymizationType());
      }
    }
    for (int id : NON_PHI_TAGS) {
      TagW t = TagD.getNullable(id);
      if (t != null) {
        savedNonPhiState.put(id, t.getAnonymizationType());
      }
    }
    savedPseudoUidState = TagW.PatientPseudoUID.getAnonymizationType();
  }

  @AfterEach
  void restoreFlags() {
    savedPhiState.forEach(
        (id, type) -> {
          TagW t = TagD.getNullable(id);
          if (t != null) {
            t.setAnonymizationType(type);
          }
        });
    savedNonPhiState.forEach(
        (id, type) -> {
          TagW t = TagD.getNullable(id);
          if (t != null) {
            t.setAnonymizationType(type);
          }
        });
    TagW.PatientPseudoUID.setAnonymizationType(savedPseudoUidState);
  }

  // -- enable(true) flags every PHI tag listed in the profile ---------------

  @Test
  void enable_flagsAllPatientDirectIdentifiers() {
    DicomMediaUtils.enableAnonymizationProfile(true);

    assertAll(
        () -> assertFlagged(Tag.PatientName),
        () -> assertFlagged(Tag.PatientID),
        () -> assertFlagged(Tag.PatientSex),
        () -> assertFlagged(Tag.PatientBirthDate),
        () -> assertFlagged(Tag.PatientBirthTime),
        () -> assertFlagged(Tag.PatientAge),
        () -> assertFlagged(Tag.PatientComments),
        () -> assertFlagged(Tag.PatientWeight));
  }

  @Test
  void enable_flagsStudyLevelIdentifiers() {
    DicomMediaUtils.enableAnonymizationProfile(true);

    assertAll(
        () -> assertFlagged(Tag.AccessionNumber),
        () -> assertFlagged(Tag.StudyID),
        () -> assertFlagged(Tag.StudyDescription));
  }

  @Test
  void enable_flagsInstitutionAndOperatorIdentifiers() {
    DicomMediaUtils.enableAnonymizationProfile(true);

    assertAll(
        () -> assertFlagged(Tag.InstitutionalDepartmentName),
        () -> assertFlagged(Tag.InstitutionName),
        () -> assertFlagged(Tag.ReferringPhysicianName),
        () -> assertFlagged(Tag.StationName));
  }

  @Test
  void enable_flagsSeriesAndImageDescriptiveText() {
    // Series and image descriptions are free text that may contain operator-entered patient info.
    DicomMediaUtils.enableAnonymizationProfile(true);

    assertAll(() -> assertFlagged(Tag.SeriesDescription), () -> assertFlagged(Tag.ImageComments));
  }

  @Test
  void enable_flagsPatientPseudoUid() {
    // The Weasis-internal pseudo-UID derived from patient identifiers must also be suppressed,
    // otherwise it could leak the original identifier patterns it was derived from.
    DicomMediaUtils.enableAnonymizationProfile(true);

    assertEquals(1, TagW.PatientPseudoUID.getAnonymizationType());
  }

  @Test
  void enable_completePhiListIsFlaggedInOneCall() {
    DicomMediaUtils.enableAnonymizationProfile(true);

    for (int id : PHI_TAGS) {
      TagW t = TagD.getNullable(id);
      if (t != null) {
        assertEquals(1, t.getAnonymizationType(), () -> "PHI tag must be flagged: " + tagName(id));
      }
    }
  }

  // -- enable(true) does NOT flag non-PHI tags ------------------------------

  @Test
  void enable_doesNotFlagNonPhiTags() {
    DicomMediaUtils.enableAnonymizationProfile(true);

    for (int id : NON_PHI_TAGS) {
      TagW t = TagD.getNullable(id);
      if (t != null) {
        assertEquals(
            0, t.getAnonymizationType(), () -> "non-PHI tag must NOT be flagged: " + tagName(id));
      }
    }
  }

  // -- enable(false) reverts every flagged tag back to 0 -------------------

  @Test
  void disable_revertsAllPhiTagsToZero() {
    DicomMediaUtils.enableAnonymizationProfile(true);

    DicomMediaUtils.enableAnonymizationProfile(false);

    for (int id : PHI_TAGS) {
      TagW t = TagD.getNullable(id);
      if (t != null) {
        assertEquals(
            0,
            t.getAnonymizationType(),
            () -> "PHI tag must revert to 0 when profile disabled: " + tagName(id));
      }
    }
  }

  @Test
  void disable_revertsPatientPseudoUidToZero() {
    DicomMediaUtils.enableAnonymizationProfile(true);

    DicomMediaUtils.enableAnonymizationProfile(false);

    assertEquals(0, TagW.PatientPseudoUID.getAnonymizationType());
  }

  // -- Idempotency: repeated enables/disables stay consistent --------------

  @Test
  void enable_isIdempotent() {
    DicomMediaUtils.enableAnonymizationProfile(true);
    DicomMediaUtils.enableAnonymizationProfile(true);

    assertEquals(1, TagD.getNullable(Tag.PatientName).getAnonymizationType());
  }

  @Test
  void disable_isIdempotent() {
    DicomMediaUtils.enableAnonymizationProfile(false);
    DicomMediaUtils.enableAnonymizationProfile(false);

    assertEquals(0, TagD.getNullable(Tag.PatientName).getAnonymizationType());
  }

  @Test
  void toggle_repeatedEnableDisableLeavesFlagsInExpectedState() {
    DicomMediaUtils.enableAnonymizationProfile(true);
    DicomMediaUtils.enableAnonymizationProfile(false);
    DicomMediaUtils.enableAnonymizationProfile(true);

    assertAll(
        () -> assertFlagged(Tag.PatientName),
        () -> assertFlagged(Tag.AccessionNumber),
        () -> assertEquals(1, TagW.PatientPseudoUID.getAnonymizationType()));
  }

  // -- helpers --------------------------------------------------------------

  private static void assertFlagged(int tagId) {
    TagW t = TagD.getNullable(tagId);
    if (t == null) {
      // TagD may not have a definition for every standard DICOM tag in every build; document but
      // do not silently pass — if the dictionary is missing a tag from the PHI list, that is a
      // real defect (the suppression will never fire for that tag).
      org.junit.jupiter.api.Assertions.fail(
          "PHI tag missing from TagD dictionary: " + tagName(tagId));
      return;
    }
    assertEquals(
        1, t.getAnonymizationType(), () -> "expected PHI tag to be flagged: " + tagName(tagId));
  }

  private static String tagName(int id) {
    return String.format("0x%08X", id);
  }
}
