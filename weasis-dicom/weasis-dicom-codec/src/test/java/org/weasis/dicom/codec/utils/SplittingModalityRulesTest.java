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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.codec.utils.SplittingModalityRules.And;
import org.weasis.dicom.codec.utils.SplittingModalityRules.Condition;
import org.weasis.dicom.codec.utils.SplittingModalityRules.DefaultCondition;
import org.weasis.dicom.codec.utils.SplittingModalityRules.Or;
import org.weasis.dicom.codec.utils.SplittingModalityRules.Rule;

/**
 * Tests {@link SplittingModalityRules} and its {@link Condition}/{@link Rule} machinery — the logic
 * that decides whether a new instance joins an existing series in the model tree or splits it into
 * a substack (e.g. by EchoNumbers on MR, or by ImageType on CT).
 *
 * <p>This is a clinically load-bearing layer: a wrong split silently presents two functionally
 * distinct image sets as one stack (or scatters one stack across N tabs), changing what the
 * clinician sees side-by-side.
 */
class SplittingModalityRulesTest {

  // Stable test-only TagW instances so we don't collide with the DICOM dictionary.
  // `DefaultCondition` parses its literal value via TagW.getValue(String), which on the base
  // class only handles an XMLStreamReader; we override getValue(Object) here so a plain String
  // round-trips unchanged. That lets us cover the condition-matching contract without standing
  // up a real splitting-rules XML loader.
  private static final TagW TAG_STRING = new StringPassThroughTag(97_000, "TestTagString");
  private static final TagW TAG_OTHER = new StringPassThroughTag(97_001, "TestTagOther");

  // -- Constructor --------------------------------------------------------

  @Test
  void constructor_modalityOnly_initialisesEmptyRuleLists() {
    SplittingModalityRules rules = new SplittingModalityRules(Modality.MR);

    assertAll(
        () -> assertSame(Modality.MR, rules.getModality()),
        () -> assertNotNull(rules.getSingleFrameRules()),
        () -> assertTrue(rules.getSingleFrameRules().isEmpty()),
        () -> assertNotNull(rules.getMultiFrameRules()),
        () -> assertTrue(rules.getMultiFrameRules().isEmpty()),
        () ->
            assertEquals(
                null, rules.getExtendRules(), "no extend-rules when constructor takes one arg"));
  }

  @Test
  void constructor_withExtendRules_copiesRulesFromParent() {
    SplittingModalityRules parent = new SplittingModalityRules(Modality.DEFAULT);
    parent.getSingleFrameRules().add(new Rule(TAG_STRING, null));
    parent.getMultiFrameRules().add(new Rule(TAG_OTHER, null));

    SplittingModalityRules child = new SplittingModalityRules(Modality.MR, parent);

    assertAll(
        () -> assertSame(Modality.MR, child.getModality()),
        () -> assertSame(parent, child.getExtendRules()),
        () -> assertEquals(1, child.getSingleFrameRules().size(), "single-frame rules inherited"),
        () -> assertEquals(1, child.getMultiFrameRules().size(), "multi-frame rules inherited"),
        () ->
            assertNotSame(
                parent.getSingleFrameRules(),
                child.getSingleFrameRules(),
                "child gets a defensive copy — mutating child must not mutate parent"));
  }

  @Test
  void constructor_extendRulesCopyIsIndependentOfParent() {
    SplittingModalityRules parent = new SplittingModalityRules(Modality.DEFAULT);
    parent.getSingleFrameRules().add(new Rule(TAG_STRING, null));

    SplittingModalityRules child = new SplittingModalityRules(Modality.MR, parent);
    child.getSingleFrameRules().clear();

    assertEquals(1, parent.getSingleFrameRules().size(), "parent unaffected by child mutation");
    assertTrue(child.getSingleFrameRules().isEmpty());
  }

  // -- Rule.isTagValueMatching -------------------------------------------

  @Test
  void rule_sameTagValueAcrossMedia_isMatching() {
    Rule rule = new Rule(TAG_STRING, null);
    MediaElement seriesMedia = mediaWith(TAG_STRING, "VALUE_A");
    MediaElement newMedia = mediaWith(TAG_STRING, "VALUE_A");

    assertTrue(rule.isTagValueMatching(seriesMedia, newMedia), "same tag value -> stays in series");
  }

  @Test
  void rule_differentTagValueAndNullCondition_doesNotMatch() {
    // No condition supplied -> any value mismatch triggers a split.
    Rule rule = new Rule(TAG_STRING, null);
    MediaElement seriesMedia = mediaWith(TAG_STRING, "VALUE_A");
    MediaElement newMedia = mediaWith(TAG_STRING, "VALUE_B");

    assertFalse(rule.isTagValueMatching(seriesMedia, newMedia), "split when values differ");
  }

  @Test
  void rule_differentValueWithMatchingCondition_doesNotMatch() {
    // Reading the implementation: "When all conditions match then the tag values not matching
    // anymore (media goes into a new subseries)". So a condition that matches the new media
    // forces a SPLIT (return false).
    Condition matchEverything = new AlwaysCondition(true);
    Rule rule = new Rule(TAG_STRING, matchEverything);
    MediaElement seriesMedia = mediaWith(TAG_STRING, "VALUE_A");
    MediaElement newMedia = mediaWith(TAG_STRING, "VALUE_B");

    assertFalse(
        rule.isTagValueMatching(seriesMedia, newMedia),
        "condition matches new media -> split into subseries");
  }

  @Test
  void rule_differentValueWithNonMatchingCondition_returnsTrueForMerge() {
    // Different values BUT the condition does NOT match → the rule treats this as "still the
    // same series" (the condition would have signalled the split). Pinning the contract.
    Condition neverMatch = new AlwaysCondition(false);
    Rule rule = new Rule(TAG_STRING, neverMatch);
    MediaElement seriesMedia = mediaWith(TAG_STRING, "VALUE_A");
    MediaElement newMedia = mediaWith(TAG_STRING, "VALUE_B");

    assertTrue(
        rule.isTagValueMatching(seriesMedia, newMedia),
        "values differ but condition does NOT signal a split -> stay merged");
  }

  // -- DefaultCondition ---------------------------------------------------

  @Test
  void defaultCondition_equals_matchesExactValue() {
    DefaultCondition cond = new DefaultCondition(TAG_STRING, Condition.Type.equals, "VALUE_A");

    assertAll(
        () -> assertTrue(cond.match(mediaWith(TAG_STRING, "VALUE_A"))),
        () -> assertFalse(cond.match(mediaWith(TAG_STRING, "VALUE_B"))),
        () -> assertFalse(cond.match(mediaWith(TAG_STRING, "value_a")), "case-sensitive"));
  }

  @Test
  void defaultCondition_notEquals_isInverse() {
    DefaultCondition cond = new DefaultCondition(TAG_STRING, Condition.Type.notEquals, "VALUE_A");

    assertAll(
        () -> assertFalse(cond.match(mediaWith(TAG_STRING, "VALUE_A"))),
        () -> assertTrue(cond.match(mediaWith(TAG_STRING, "VALUE_B"))));
  }

  @Test
  void defaultCondition_equalsIgnoreCase_isCaseInsensitive() {
    DefaultCondition cond =
        new DefaultCondition(TAG_STRING, Condition.Type.equalsIgnoreCase, "VALUE_A");

    assertAll(
        () -> assertTrue(cond.match(mediaWith(TAG_STRING, "VALUE_A"))),
        () -> assertTrue(cond.match(mediaWith(TAG_STRING, "value_a"))),
        () -> assertFalse(cond.match(mediaWith(TAG_STRING, "VALUE_B"))));
  }

  @Test
  void defaultCondition_contains_findsSubstring() {
    DefaultCondition cond = new DefaultCondition(TAG_STRING, Condition.Type.contains, "DERIV");

    assertAll(
        () -> assertTrue(cond.match(mediaWith(TAG_STRING, "ORIGINAL\\PRIMARY\\DERIVED"))),
        () -> assertFalse(cond.match(mediaWith(TAG_STRING, "ORIGINAL\\PRIMARY"))),
        () -> assertFalse(cond.match(mediaWith(TAG_STRING, "derived")), "case-sensitive"));
  }

  @Test
  void defaultCondition_containsIgnoreCase() {
    DefaultCondition cond =
        new DefaultCondition(TAG_STRING, Condition.Type.containsIgnoreCase, "DERIV");

    assertAll(
        () -> assertTrue(cond.match(mediaWith(TAG_STRING, "ORIGINAL\\PRIMARY\\DERIVED"))),
        () -> assertTrue(cond.match(mediaWith(TAG_STRING, "original\\primary\\derived"))));
  }

  @Test
  void defaultCondition_notContains_isInverse() {
    DefaultCondition cond = new DefaultCondition(TAG_STRING, Condition.Type.notContains, "DERIV");

    assertAll(
        () -> assertFalse(cond.match(mediaWith(TAG_STRING, "ORIGINAL\\PRIMARY\\DERIVED"))),
        () -> assertTrue(cond.match(mediaWith(TAG_STRING, "ORIGINAL\\PRIMARY"))));
  }

  // -- And / Or composite conditions -------------------------------------

  @Test
  void and_emptyChildren_returnsTrueAndIsEmpty() {
    And and = new And();

    assertAll(
        () -> assertTrue(and.isEmpty()),
        () ->
            assertTrue(
                and.match(mock(MediaElement.class)),
                "empty AND is vacuously true (matches the documented contract)"));
  }

  @Test
  void or_emptyChildren_returnsFalseAndIsEmpty() {
    Or or = new Or();

    assertAll(
        () -> assertTrue(or.isEmpty()),
        () -> assertFalse(or.match(mock(MediaElement.class)), "empty OR is vacuously false"));
  }

  @Test
  void and_allChildrenTrue_returnsTrue() {
    And and = new And();
    and.addChild(new AlwaysCondition(true));
    and.addChild(new AlwaysCondition(true));

    assertTrue(and.match(mock(MediaElement.class)));
  }

  @Test
  void and_oneChildFalse_returnsFalse() {
    And and = new And();
    and.addChild(new AlwaysCondition(true));
    and.addChild(new AlwaysCondition(false));

    assertFalse(and.match(mock(MediaElement.class)));
  }

  @Test
  void or_oneChildTrue_returnsTrue() {
    Or or = new Or();
    or.addChild(new AlwaysCondition(false));
    or.addChild(new AlwaysCondition(true));

    assertTrue(or.match(mock(MediaElement.class)));
  }

  @Test
  void or_allChildrenFalse_returnsFalse() {
    Or or = new Or();
    or.addChild(new AlwaysCondition(false));
    or.addChild(new AlwaysCondition(false));

    assertFalse(or.match(mock(MediaElement.class)));
  }

  // -- Condition.not() modifier ------------------------------------------

  @Test
  void not_invertsResultOfAnd() {
    And and = new And();
    and.addChild(new AlwaysCondition(true));
    and.not();

    assertFalse(and.match(mock(MediaElement.class)), "AND of [true] inverted -> false");
  }

  @Test
  void not_isInvolutive() {
    And and = new And();
    and.addChild(new AlwaysCondition(true));

    and.not();
    and.not();

    assertTrue(and.match(mock(MediaElement.class)), "not().not() returns original behaviour");
  }

  @Test
  void not_invertsResultOfOr() {
    Or or = new Or();
    or.addChild(new AlwaysCondition(false));
    or.not();

    assertTrue(or.match(mock(MediaElement.class)), "OR of [false] inverted -> true");
  }

  // -- helpers --------------------------------------------------------------

  private static MediaElement mediaWith(TagW tag, Object value) {
    MediaElement m = mock(MediaElement.class);
    lenient().when(m.getTagValue(tag)).thenReturn(value);
    return m;
  }

  /** A condition that ignores the media argument and returns the configured boolean. */
  private static final class AlwaysCondition extends Condition {
    private final boolean result;

    AlwaysCondition(boolean result) {
      this.result = result;
    }

    @Override
    public boolean match(MediaElement media) {
      return result;
    }
  }

  /**
   * STRING-type TagW whose {@code getValue(Object)} returns a non-XML input verbatim, matching what
   * production code expects after the XML loader has already extracted the literal.
   */
  private static final class StringPassThroughTag extends TagW {
    StringPassThroughTag(int id, String keyword) {
      super(id, keyword, TagType.STRING);
    }

    @Override
    public Object getValue(Object data) {
      return data instanceof String s ? s : super.getValue(data);
    }
  }
}
