/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.media.data;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.TagW.TagType;

@DisplayName("MaskingProfile")
class MaskingProfileTest {

  private static final MaskingModelRegistry BUNDLED =
      new MaskingModelRegistry(MaskingModelRegistry.loadBuiltIn());

  private static final MaskingProfile DISPLAY = BUNDLED.require(MaskingProfile.DISPLAY_ID);
  private static final MaskingProfile TEACHING = BUNDLED.require(MaskingProfile.TEACHING_ID);
  private static final MaskingProfile PUBLICATION = BUNDLED.require(MaskingProfile.PUBLICATION_ID);
  private static final MaskingProfile AI_REQUEST = BUNDLED.require(MaskingProfile.AI_REQUEST_ID);

  private static final List<MaskingProfile> ALL =
      List.of(DISPLAY, TEACHING, PUBLICATION, AI_REQUEST);

  private static TagW tag(String keyword, TagCategory category) {
    TagW t = new TagW(keyword, TagType.STRING);
    t.setCategory(category);
    return t;
  }

  @Test
  @DisplayName("every profile hides direct identifiers")
  void directIdentifiersAreNeverKept() {
    TagW name = tag("Name", TagCategory.DIRECT_ID);
    ALL.forEach(
        p ->
            assertNotEquals(
                AnonymizationAction.KEEP, p.actionFor(name), () -> p.name() + " must hide names"));
  }

  @Test
  @DisplayName("every profile keeps what the image is and what the patient is")
  void clinicalInformationSurvives() {
    TagW description = tag("Desc", TagCategory.DESCRIPTOR);
    TagW weight = tag("Weight", TagCategory.PATIENT_CHARACTERISTIC);
    ALL.forEach(
        p ->
            assertAll(
                () -> assertEquals(AnonymizationAction.KEEP, p.actionFor(description), p.name()),
                () -> assertEquals(AnonymizationAction.KEEP, p.actionFor(weight), p.name())));
  }

  @Test
  @DisplayName("every profile drops operator free text and birth dates")
  void freeTextAndBirthDatesGo() {
    TagW comment = tag("Comment", TagCategory.FREE_TEXT);
    TagW birth = tag("Birth", TagCategory.BIRTH_DATE);
    ALL.forEach(
        p ->
            assertAll(
                () -> assertEquals(AnonymizationAction.REMOVE, p.actionFor(comment), p.name()),
                () -> assertEquals(AnonymizationAction.REMOVE, p.actionFor(birth), p.name())));
  }

  @Test
  @DisplayName("the profiles differ on dates, institution and device")
  void profilesDifferWhereTheyShould() {
    TagW date = tag("StudyDate", TagCategory.DATE);
    TagW institution = tag("Institution", TagCategory.INSTITUTION);
    TagW device = tag("Station", TagCategory.DEVICE);

    assertAll(
        () -> assertEquals(AnonymizationAction.KEEP, DISPLAY.actionFor(date)),
        () -> assertEquals(AnonymizationAction.SHIFT, TEACHING.actionFor(date)),
        () -> assertEquals(AnonymizationAction.REMOVE, PUBLICATION.actionFor(date)),
        () -> assertEquals(AnonymizationAction.SHIFT, AI_REQUEST.actionFor(date)),
        () -> assertEquals(AnonymizationAction.PSEUDONYMIZE, DISPLAY.actionFor(institution)),
        () -> assertEquals(AnonymizationAction.REMOVE, PUBLICATION.actionFor(institution)),
        () -> assertEquals(AnonymizationAction.KEEP, AI_REQUEST.actionFor(device)),
        () -> assertEquals(AnonymizationAction.REMOVE, PUBLICATION.actionFor(device)));
  }

  @Test
  @DisplayName("an unclassified tag is untouched")
  void unclassifiedIsKept() {
    TagW other = tag("Modality", TagCategory.OTHER);
    ALL.forEach(p -> assertEquals(AnonymizationAction.KEEP, p.actionFor(other)));
    ALL.forEach(p -> assertEquals(AnonymizationAction.KEEP, p.actionFor((TagW) null)));
  }

  @Test
  @DisplayName("a shifting profile moves dates and preserves intervals")
  void shiftingProfileMovesDates() {
    TagW date = tag("StudyDate", TagCategory.DATE);
    IdentityMask mask = IdentityMask.forProfile(AI_REQUEST);

    LocalDate first = (LocalDate) mask.apply(date, LocalDate.of(2026, 1, 1));
    LocalDate second = (LocalDate) mask.apply(date, LocalDate.of(2026, 2, 1));

    assertAll(
        () -> assertNotEquals(LocalDate.of(2026, 1, 1), first, "the date must move"),
        () -> assertEquals(31, second.toEpochDay() - first.toEpochDay(), "intervals are preserved"),
        () -> assertTrue(mask.dateShiftDays() > 0));
  }

  @Test
  @DisplayName("a non-shifting profile leaves dates where they are")
  void displayProfileDoesNotShift() {
    assertEquals(0, IdentityMask.forProfile(DISPLAY).dateShiftDays());
  }
}
