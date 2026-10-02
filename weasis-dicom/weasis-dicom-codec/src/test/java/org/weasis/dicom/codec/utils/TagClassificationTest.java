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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.AnonymizationAction;
import org.weasis.core.api.media.data.MaskingModel;
import org.weasis.core.api.media.data.MaskingModel.TagRule;
import org.weasis.core.api.media.data.MaskingModelRegistry;
import org.weasis.core.api.media.data.MaskingProfile;
import org.weasis.core.api.media.data.TagCategory;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.TagD;

/**
 * The classification is what every {@link MaskingProfile} resolves an action from. A tag left
 * unclassified is silently shown by every profile, so the cost of a mistake here is
 * one-directional.
 */
@DisplayName("DICOM tag classification")
class TagClassificationTest {

  @BeforeAll
  static void classify() {
    DicomMediaUtils.classifyTags();
  }

  private static TagCategory categoryOf(int tagId) {
    TagW tag = TagD.getNullable(tagId);
    assertNotNull(tag, () -> "tag missing from the TagD dictionary: 0x%08X".formatted(tagId));
    return tag.getCategory();
  }

  @Test
  @DisplayName("names and visit numbers are direct identifiers")
  void directIdentifiers() {
    assertAll(
        () -> assertEquals(TagCategory.DIRECT_ID, categoryOf(Tag.PatientName)),
        () -> assertEquals(TagCategory.DIRECT_ID, categoryOf(Tag.PatientID)),
        () -> assertEquals(TagCategory.DIRECT_ID, categoryOf(Tag.AccessionNumber)),
        () -> assertEquals(TagCategory.DIRECT_ID, categoryOf(Tag.StudyID)),
        () -> assertEquals(TagCategory.DIRECT_ID, categoryOf(Tag.ReferringPhysicianName)),
        () -> assertEquals(TagCategory.DIRECT_ID, TagW.PatientPseudoUID.getCategory()));
  }

  @Test
  @DisplayName("weight and size stay available: SUV and dose need them")
  void patientCharacteristicsAreKeptByEveryProfile() {
    for (int tagId :
        new int[] {Tag.PatientSex, Tag.PatientAge, Tag.PatientWeight, Tag.PatientSize}) {
      TagCategory category = categoryOf(tagId);
      assertEquals(TagCategory.PATIENT_CHARACTERISTIC, category);
      assertAll(
          () ->
              assertEquals(AnonymizationAction.KEEP, MaskingProfile.display().actionFor(category)),
          () ->
              assertEquals(AnonymizationAction.KEEP, MaskingProfile.teaching().actionFor(category)),
          () ->
              assertEquals(
                  AnonymizationAction.KEEP, MaskingProfile.publication().actionFor(category)),
          () ->
              assertEquals(
                  AnonymizationAction.KEEP, MaskingProfile.aiRequest().actionFor(category)));
    }
  }

  @Test
  @DisplayName("descriptions describe the image, so no profile removes them")
  void descriptorsAreKeptByEveryProfile() {
    for (int tagId : new int[] {Tag.StudyDescription, Tag.SeriesDescription, Tag.ProtocolName}) {
      TagCategory category = categoryOf(tagId);
      assertEquals(TagCategory.DESCRIPTOR, category);
      assertEquals(AnonymizationAction.KEEP, MaskingProfile.teaching().actionFor(category));
      assertEquals(AnonymizationAction.KEEP, MaskingProfile.aiRequest().actionFor(category));
    }
  }

  @Test
  @DisplayName("a birth date is not a study date")
  void datesAreSplit() {
    assertAll(
        () -> assertEquals(TagCategory.BIRTH_DATE, categoryOf(Tag.PatientBirthDate)),
        () -> assertEquals(TagCategory.BIRTH_DATE, categoryOf(Tag.PatientBirthTime)),
        () -> assertEquals(TagCategory.DATE, categoryOf(Tag.StudyDate)),
        () -> assertEquals(TagCategory.DATE, categoryOf(Tag.SeriesDate)),
        () -> assertEquals(TagCategory.DATE, categoryOf(Tag.AcquisitionDate)));
  }

  @Test
  @DisplayName("site, equipment and operator text are separated")
  void institutionDeviceAndFreeText() {
    assertAll(
        () -> assertEquals(TagCategory.INSTITUTION, categoryOf(Tag.InstitutionName)),
        () -> assertEquals(TagCategory.INSTITUTION, categoryOf(Tag.InstitutionalDepartmentName)),
        () -> assertEquals(TagCategory.DEVICE, categoryOf(Tag.StationName)),
        () -> assertEquals(TagCategory.FREE_TEXT, categoryOf(Tag.PatientComments)),
        () -> assertEquals(TagCategory.FREE_TEXT, categoryOf(Tag.ImageComments)));
  }

  @Test
  @DisplayName("technical tags are left alone")
  void unclassifiedTagsStayOther() {
    assertAll(
        () -> assertEquals(TagCategory.OTHER, categoryOf(Tag.Modality)),
        () -> assertEquals(TagCategory.OTHER, categoryOf(Tag.Rows)),
        () -> assertEquals(TagCategory.OTHER, categoryOf(Tag.Columns)),
        () -> assertEquals(TagCategory.OTHER, categoryOf(Tag.WindowWidth)),
        () -> assertEquals(TagCategory.OTHER, categoryOf(Tag.SliceThickness)));
  }

  @Test
  @DisplayName("every bundled DICOM rule names a known tag")
  void bundledRulesResolve() {
    MaskingModelRegistry.getInstance().tags().entries().stream()
        .filter(rule -> !rule.isInternal())
        .forEach(rule -> assertNotNull(TagD.get(rule.keyword()), rule.key()));
  }

  @Test
  @DisplayName("a configuration can classify more tags, including private ones")
  void configurationAddsTags() {
    MaskingModelRegistry registry = MaskingModelRegistry.getInstance();
    List<TagRule> rules = new ArrayList<>(registry.tags().entries());
    rules.add(new TagRule(MaskingModel.normalizeKey("(0008,1070)", null), TagCategory.DIRECT_ID));
    rules.add(
        new TagRule(MaskingModel.normalizeKey("(0009,1010)", "ACME_SITE"), TagCategory.DIRECT_ID));
    try {
      DicomMediaUtils.applyClassification(rules);
      TagW operators = DicomMediaUtils.maskingTag(Tag.OperatorsName, null);
      TagW privateTag = DicomMediaUtils.maskingTag(0x00091010, "ACME_SITE");
      assertAll(
          () -> assertEquals(TagCategory.DIRECT_ID, operators.getCategory()),
          () -> assertNotNull(privateTag, "a private tag named by the configuration is kept"),
          () -> assertEquals(TagCategory.DIRECT_ID, privateTag.getCategory()),
          () ->
              assertEquals(
                  AnonymizationAction.PSEUDONYMIZE, MaskingProfile.display().actionFor(privateTag)),
          () -> assertEquals(TagCategory.DIRECT_ID, categoryOf(Tag.PatientName)));
    } finally {
      DicomMediaUtils.applyClassification(registry.tags().entries());
    }
    assertEquals(
        TagCategory.OTHER,
        DicomMediaUtils.maskingTag(Tag.OperatorsName, null).getCategory(),
        "a tag no longer named is reset");
  }

  @Test
  @DisplayName("classifying twice changes nothing")
  void isIdempotent() {
    DicomMediaUtils.classifyTags();
    DicomMediaUtils.classifyTags();

    assertEquals(TagCategory.DIRECT_ID, categoryOf(Tag.PatientName));
  }
}
