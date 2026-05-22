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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;
import javax.xml.stream.XMLStreamReader;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.weasis.core.api.media.data.TagW.TagType;

/**
 * Tests {@link TagUtil} — the toolkit-wide utility for DICOM-tag value conversion, date/time
 * normalisation, equality, substring search and XML-attribute parsing.
 *
 * <p>This class sits at the foundation of the Patient panel, the splitting-rules engine, the
 * modality-view XML loader and several DICOM-tag display paths. Failure modes cluster around four
 * clinically observable hazards:
 *
 * <ul>
 *   <li><strong>Wrong-date display</strong> on the patient panel — feeds the wrong-study-selected
 *       failure mode (WEA-004 R23 / patient identity at the display layer);
 *   <li><strong>Wrong equality verdict</strong> in splitting-rules — silently merges or splits
 *       series that should not be (DSP / DEC hazards);
 *   <li><strong>XML attribute parsing failure</strong> — falls back to defaults silently, partly
 *       closing the XML-loader residual flagged in INDEX §7.4;
 *   <li><strong>Substring-search false negative</strong> — Patient panel search misses a study.
 * </ul>
 */
class TagUtilTest {

  // ---------------------------------------------------------------------------
  // Date / time conversions — TemporalAccessor → java.util.Date
  // ---------------------------------------------------------------------------

  @Test
  void toLocalDate_temporalAccessorNullReturnsNull() {
    assertNull(TagUtil.toLocalDate((java.time.temporal.TemporalAccessor) null));
  }

  @Test
  void toLocalDate_fromLocalDate_anchorsToStartOfDay() {
    Date d = TagUtil.toLocalDate(LocalDate.of(2026, 5, 22));
    assertNotNull(d);
    LocalDateTime ldt = LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
    assertEquals(LocalDate.of(2026, 5, 22), ldt.toLocalDate());
    assertEquals(0, ldt.getHour());
    assertEquals(0, ldt.getMinute());
    assertEquals(0, ldt.getSecond());
  }

  @Test
  void toLocalDate_fromLocalDateTime_preservesInstant() {
    LocalDateTime source = LocalDateTime.of(2026, 5, 22, 14, 30, 45);
    Date d = TagUtil.toLocalDate(source);
    assertNotNull(d);
    assertEquals(source, LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault()));
  }

  @Test
  void toLocalDate_fromInstant_preservesEpochSecond() {
    // Instant is passed through directly (no time-zone reinterpretation).
    Instant source = Instant.ofEpochSecond(1_716_300_000L);
    Date d = TagUtil.toLocalDate(source);
    assertNotNull(d);
    assertEquals(1_716_300_000L, d.toInstant().getEpochSecond());
  }

  @Test
  void toLocalDates_fromTemporalArrayProducesParallelArray() {
    LocalDate[] dates = {LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 31)};
    Date[] result = TagUtil.toLocalDates(dates);
    assertNotNull(result);
    assertEquals(2, result.length);
    assertNotNull(result[0]);
    assertNotNull(result[1]);
  }

  @Test
  void toLocalDates_nullOrNonArrayReturnsNull() {
    assertNull(TagUtil.toLocalDates(null));
    assertNull(TagUtil.toLocalDates("not-an-array"));
  }

  // ---------------------------------------------------------------------------
  // Date → LocalDate / LocalTime / LocalDateTime
  // ---------------------------------------------------------------------------

  @Test
  void toLocalDate_fromDate_nullReturnsNull() {
    assertNull(TagUtil.toLocalDate((Date) null));
  }

  @Test
  void toLocalDate_fromDate_extractsDatePart() {
    Date d =
        Date.from(LocalDateTime.of(2026, 5, 22, 13, 45).atZone(ZoneId.systemDefault()).toInstant());
    assertEquals(LocalDate.of(2026, 5, 22), TagUtil.toLocalDate(d));
  }

  @Test
  void toLocalTime_extractsHoursMinutesSeconds() {
    Date d =
        Date.from(
            LocalDateTime.of(2026, 5, 22, 13, 45, 30).atZone(ZoneId.systemDefault()).toInstant());
    LocalTime t = TagUtil.toLocalTime(d);
    assertEquals(13, t.getHour());
    assertEquals(45, t.getMinute());
    assertEquals(30, t.getSecond());
  }

  @Test
  void toLocalTime_nullReturnsNull() {
    assertNull(TagUtil.toLocalTime(null));
  }

  @Test
  void toLocalDateTime_combinesDateAndTime() {
    LocalDateTime source = LocalDateTime.of(2026, 5, 22, 13, 45);
    Date d = Date.from(source.atZone(ZoneId.systemDefault()).toInstant());
    assertEquals(source, TagUtil.toLocalDateTime(d));
  }

  @Test
  void toLocalDateTime_nullReturnsNull() {
    assertNull(TagUtil.toLocalDateTime(null));
  }

  // ---------------------------------------------------------------------------
  // getZonedDateTime
  // ---------------------------------------------------------------------------

  @Test
  void getZonedDateTime_nullDateTimeReturnsNull() {
    assertNull(TagUtil.getZonedDateTime(null, TimeZone.getDefault()));
  }

  @Test
  void getZonedDateTime_nullTimeZoneFallsBackToSystemDefault() {
    LocalDateTime ldt = LocalDateTime.of(2026, 5, 22, 13, 0);
    ZonedDateTime z = TagUtil.getZonedDateTime(ldt, null);
    assertNotNull(z);
    assertEquals(ZoneId.systemDefault(), z.getZone());
  }

  @Test
  void getZonedDateTime_explicitTimeZoneIsHonoured() {
    LocalDateTime ldt = LocalDateTime.of(2026, 5, 22, 13, 0);
    TimeZone utc = TimeZone.getTimeZone("UTC");
    ZonedDateTime z = TagUtil.getZonedDateTime(ldt, utc);
    assertEquals(ZoneOffset.UTC, z.getOffset());
  }

  // ---------------------------------------------------------------------------
  // formatDateTime — drives Patient panel date display
  // ---------------------------------------------------------------------------

  @Test
  void formatDateTime_nonEmptyForEverySupportedTemporalType() {
    assertFalse(TagUtil.formatDateTime(LocalDate.of(2026, 5, 22)).isEmpty());
    assertFalse(TagUtil.formatDateTime(LocalTime.of(13, 30)).isEmpty());
    assertFalse(TagUtil.formatDateTime(LocalDateTime.of(2026, 5, 22, 13, 30)).isEmpty());
    assertFalse(
        TagUtil.formatDateTime(ZonedDateTime.of(2026, 5, 22, 13, 30, 0, 0, ZoneOffset.UTC))
            .isEmpty());
    assertFalse(TagUtil.formatDateTime(Instant.ofEpochSecond(1_716_300_000L)).isEmpty());
  }

  @Test
  void formatDateTime_unsupportedOrNullReturnsEmpty() {
    // A bare TemporalAccessor that is not one of the supported subtypes must yield "" (no
    // exception leak to the UI).
    assertEquals("", TagUtil.formatDateTime(null));
  }

  // ---------------------------------------------------------------------------
  // isEquals — drives splitting rules and identity-matching
  // ---------------------------------------------------------------------------

  @Test
  void isEquals_bothNullReturnsTrue() {
    assertTrue(TagUtil.isEquals(null, null));
  }

  @Test
  void isEquals_oneNullReturnsFalse() {
    assertFalse(TagUtil.isEquals(null, "x"));
    assertFalse(TagUtil.isEquals("x", null));
  }

  @Test
  void isEquals_scalarEquality() {
    assertTrue(TagUtil.isEquals("abc", "abc"));
    assertTrue(TagUtil.isEquals(42, 42));
    assertFalse(TagUtil.isEquals("abc", "ABC"));
    assertFalse(TagUtil.isEquals(42, 43));
  }

  @Test
  void isEquals_arrayEqualityHonoursLengthAndElementOrder() {
    assertTrue(TagUtil.isEquals(new int[] {1, 2, 3}, new int[] {1, 2, 3}));
    assertFalse(TagUtil.isEquals(new int[] {1, 2, 3}, new int[] {1, 2}));
    assertFalse(TagUtil.isEquals(new int[] {1, 2, 3}, new int[] {1, 3, 2}));
  }

  @Test
  void isEquals_arrayVsScalarReturnsFalse() {
    assertFalse(TagUtil.isEquals(new int[] {1, 2}, "1,2"));
  }

  @Test
  void isEquals_objectArrayWithNullElementsAtSamePositionAreEqual() {
    assertTrue(TagUtil.isEquals(new Object[] {"a", null, "c"}, new Object[] {"a", null, "c"}));
  }

  @Test
  void isEquals_ignoreCaseFlag_collapsesStringCaseDifference() {
    assertTrue(TagUtil.isEquals("DOE^JANE", "doe^jane", true));
    assertFalse(TagUtil.isEquals("DOE^JANE", "doe^jane", false));
  }

  @Test
  void isEquals_ignoreCaseAppliesInsideArrays() {
    assertTrue(TagUtil.isEquals(new String[] {"AB", "cd"}, new String[] {"ab", "CD"}, true));
    assertFalse(TagUtil.isEquals(new String[] {"AB", "cd"}, new String[] {"ab", "CD"}, false));
  }

  @Test
  void isEquals_ignoreCaseDoesNotApplyToNonStrings() {
    // Same non-String scalars compare by .equals regardless of ignoreCase flag.
    assertTrue(TagUtil.isEquals(7, 7, true));
    assertFalse(TagUtil.isEquals(7, 8, true));
  }

  // ---------------------------------------------------------------------------
  // isContaining — Patient panel search and SR text matching
  // ---------------------------------------------------------------------------

  @Test
  void isContaining_nullSearchStringIsUniversallyTrue() {
    // The "no filter" path: a null search term means "match everything".
    assertTrue(TagUtil.isContaining("anything", null, false));
    assertTrue(TagUtil.isContaining(null, null, false));
  }

  @Test
  void isContaining_nullValueReturnsFalseExceptForNullSearch() {
    assertFalse(TagUtil.isContaining(null, "x", false));
  }

  @Test
  void isContaining_caseSensitive() {
    assertTrue(TagUtil.isContaining("PatientName", "Patient", false));
    assertFalse(TagUtil.isContaining("PatientName", "patient", false));
  }

  @Test
  void isContaining_caseInsensitive() {
    assertTrue(TagUtil.isContaining("PatientName", "patient", true));
    assertTrue(TagUtil.isContaining("PatientName", "NAME", true));
  }

  @Test
  void isContaining_searchInsideArray() {
    String[] aliases = {"Smith", "Müller", "Doe"};
    assertTrue(TagUtil.isContaining(aliases, "Doe", false));
    assertTrue(TagUtil.isContaining(aliases, "müller", true));
    assertFalse(TagUtil.isContaining(aliases, "Brown", false));
  }

  // ---------------------------------------------------------------------------
  // getTagValue / getTagFromKeywords
  // ---------------------------------------------------------------------------

  @Test
  void getTagValue_returnsFirstNonNullFromTaggables() {
    TagW tag = TagW.FileName;
    SimpleTaggable empty = new SimpleTaggable();
    SimpleTaggable withValue = new SimpleTaggable();
    withValue.setTag(tag, "study.dcm");
    assertEquals("study.dcm", TagUtil.getTagValue(tag, empty, withValue));
  }

  @Test
  void getTagValue_returnsNullWhenNoTaggableHasValue() {
    assertNull(TagUtil.getTagValue(TagW.FileName, new SimpleTaggable(), new SimpleTaggable()));
  }

  @Test
  void getTagValue_skipsNullTaggables() {
    SimpleTaggable t = new SimpleTaggable();
    t.setTag(TagW.FileName, "X");
    assertEquals("X", TagUtil.getTagValue(TagW.FileName, null, t));
  }

  @Test
  void getTagFromKeywords_resolvesKnownKeywordsAndSkipsUnknown() {
    // FileName is registered in TagW's static map (see TagW.addTag(FileName)).
    TagW[] tags = TagUtil.getTagFromKeywords("FileName", "definitely-not-a-real-tag");
    assertEquals(1, tags.length);
    assertSame(TagW.FileName, tags[0]);
  }

  @Test
  void getTagFromKeywords_nullInputReturnsEmptyArray() {
    assertEquals(0, TagUtil.getTagFromKeywords((String[]) null).length);
  }

  // ---------------------------------------------------------------------------
  // XML attribute parsing — partial coverage of INDEX §7.4 "XML configuration
  // loaders" residual risk. These guard the splitting-rules / modality-view
  // XML loaders against malformed attribute values (NumberFormatException) and
  // against missing attributes (default-value fallback).
  // ---------------------------------------------------------------------------

  private static XMLStreamReader stubReader(String attribute, String value) {
    XMLStreamReader reader = Mockito.mock(XMLStreamReader.class);
    Mockito.when(reader.getAttributeValue(null, attribute)).thenReturn(value);
    return reader;
  }

  @Test
  void getTagAttribute_returnsValueOrDefault() {
    assertEquals("v", TagUtil.getTagAttribute(stubReader("k", "v"), "k", "def"));
    assertEquals("def", TagUtil.getTagAttribute(stubReader("k", null), "k", "def"));
    assertEquals("def", TagUtil.getTagAttribute(stubReader("k", "v"), null, "def"));
  }

  @Test
  void getBooleanTagAttribute_parsesValueOrFallsBack() {
    assertEquals(Boolean.TRUE, TagUtil.getBooleanTagAttribute(stubReader("k", "true"), "k", false));
    assertEquals(
        Boolean.FALSE, TagUtil.getBooleanTagAttribute(stubReader("k", "false"), "k", true));
    assertEquals(
        Boolean.TRUE, TagUtil.getBooleanTagAttribute(stubReader("k", null), "k", Boolean.TRUE));
  }

  @Test
  void getIntegerTagAttribute_fallsBackOnNumberFormatException() {
    assertEquals(42, TagUtil.getIntegerTagAttribute(stubReader("k", "42"), "k", 0));
    // Malformed value → caught NumberFormatException → default returned (no propagation to UI)
    assertEquals(99, TagUtil.getIntegerTagAttribute(stubReader("k", "not-a-number"), "k", 99));
    assertEquals(99, TagUtil.getIntegerTagAttribute(stubReader("k", null), "k", 99));
  }

  @Test
  void getDoubleTagAttribute_handlesScientificAndNegativeValues() {
    assertEquals(3.14, TagUtil.getDoubleTagAttribute(stubReader("k", "3.14"), "k", 0.0));
    assertEquals(-1.5e3, TagUtil.getDoubleTagAttribute(stubReader("k", "-1.5e3"), "k", 0.0));
    assertEquals(0.0, TagUtil.getDoubleTagAttribute(stubReader("k", "bogus"), "k", 0.0));
  }

  @Test
  void getStringArrayTagAttribute_splitsOnDefaultBackslashSeparator() {
    String[] result =
        TagUtil.getStringArrayTagAttribute(stubReader("k", "a\\b\\c"), "k", new String[0]);
    assertArrayEquals(new String[] {"a", "b", "c"}, result);
  }

  @Test
  void getStringArrayTagAttribute_customSeparator() {
    String[] result =
        TagUtil.getStringArrayTagAttribute(stubReader("k", "a,b,c"), "k", new String[0], ",");
    assertArrayEquals(new String[] {"a", "b", "c"}, result);
  }

  @Test
  void getStringArrayTagAttribute_missingAttributeReturnsDefault() {
    String[] def = {"x"};
    assertSame(def, TagUtil.getStringArrayTagAttribute(stubReader("k", null), "k", def));
  }

  @Test
  void getIntArrayTagAttribute_parsesBackslashSeparatedIntegers() {
    int[] result = TagUtil.getIntArrayTagAttribute(stubReader("k", "1\\2\\3"), "k", new int[0]);
    assertArrayEquals(new int[] {1, 2, 3}, result);
  }

  @Test
  void getIntArrayTagAttribute_missingAttributeReturnsDefault() {
    int[] def = {7};
    assertSame(def, TagUtil.getIntArrayTagAttribute(stubReader("k", null), "k", def));
  }

  @Test
  void getFloatTagAttribute_handlesNullAttributeName() {
    // Defensive: when the attribute name itself is null, the lookup is skipped and the default
    // is returned (no NPE inside the XML parser).
    assertEquals(
        Float.valueOf(2.0f), TagUtil.getFloatTagAttribute(stubReader("k", "1.0"), null, 2.0f));
  }

  @Test
  void getDateFromElement_parsesLocalDateFromIsoString() {
    var result = TagUtil.getDateFromElement(stubReader("k", "2026-05-22"), "k", TagType.DATE, null);
    assertEquals(LocalDate.of(2026, 5, 22), result);
  }

  @Test
  void getDateFromElement_parsesLocalTimeFromIsoString() {
    var result = TagUtil.getDateFromElement(stubReader("k", "13:45:30"), "k", TagType.TIME, null);
    assertEquals(LocalTime.of(13, 45, 30), result);
  }

  @Test
  void getDateFromElement_unparsableValueReturnsDefault() {
    LocalDate def = LocalDate.of(2026, 1, 1);
    var result = TagUtil.getDateFromElement(stubReader("k", "not-a-date"), "k", TagType.DATE, def);
    assertSame(def, result);
  }
}
