/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.time.LocalTime;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.MaskingProfile;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

/**
 * Dates shown outside a {@code TagView} — the series tooltip — go through their own builder, so
 * they need their own coverage: a profile that shifts must shift them, and one that removes must
 * hide them.
 */
@DisplayName("Date masking in tooltips")
class DateMaskingTest {

  private static final LocalDate DATE = LocalDate.of(2026, 5, 4);
  private static final LocalTime TIME = LocalTime.of(10, 30);

  @BeforeAll
  static void classify() {
    DicomMediaUtils.classifyTags();
  }

  private static TagReadable seriesWith(LocalDate date, LocalTime time) {
    TagReadable readable = mock(TagReadable.class);
    lenient().when(readable.getTagValue(TagD.get(Tag.SeriesDate))).thenReturn(date);
    lenient().when(readable.getTagValue(TagD.get(Tag.SeriesTime))).thenReturn(time);
    lenient().when(readable.getTagValue(TagW.Timezone)).thenReturn(null);
    return readable;
  }

  private static String build(TagReadable readable) {
    return DcmMediaReader.buildDateTimeWithTimeZone(readable, Tag.SeriesDate, Tag.SeriesTime);
  }

  @Test
  @DisplayName("without a mask the real date is shown")
  void unmasked() {
    assertTrue(build(seriesWith(DATE, TIME)).contains("2026"));
  }

  @Test
  @DisplayName("a keeping profile leaves the date alone")
  void displayProfileKeepsDates() {
    TagReadable series = seriesWith(DATE, TIME);
    String expected = build(series);

    assertEquals(
        expected,
        IdentityMask.forProfile(MaskingProfile.display()).callMasked(() -> build(series)));
  }

  @Test
  @DisplayName("a shifting profile moves the date")
  void teachingProfileShiftsDates() {
    TagReadable series = seriesWith(DATE, TIME);
    String unmasked = build(series);

    String shifted =
        IdentityMask.forProfile(MaskingProfile.teaching()).callMasked(() -> build(series));

    assertAll(
        () -> assertNotEquals(unmasked, shifted, "the date must move"),
        () -> assertTrue(StringUtil.hasText(shifted), "but it must still be a date"));
  }

  @Test
  @DisplayName("a removing profile hides the date entirely")
  void publicationProfileRemovesDates() {
    TagReadable series = seriesWith(DATE, TIME);

    assertEquals(
        "", IdentityMask.forProfile(MaskingProfile.publication()).callMasked(() -> build(series)));
  }
}
