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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.util.ResourceUtil.ResourceIconPath;

/**
 * The composition of the thumbnail badges: the special-element icons keep their place, the
 * providers append in registration order, and a broken provider costs nothing to the others.
 */
class SeriesBadgeProviderTest {

  private static final ResourceIconPath KO = () -> "svg/ko.svg";
  private static final ResourceIconPath ROBOT = () -> "svg/robot.svg";
  private static final ResourceIconPath RESULT = () -> "svg/result.svg";

  @Test
  void providersAppendAfterTheSpecialElementIcons() {
    Set<ResourceIconPath> merged =
        SeriesThumbnail.mergeBadges(
            new LinkedHashSet<>(List.of(KO)), List.of(uid -> Set.of(ROBOT)), "1.2.3");

    assertEquals(List.of(KO, ROBOT), List.copyOf(merged));
  }

  @Test
  void aNullBaseAndAnEmptyProviderGiveAnEmptySet() {
    assertTrue(SeriesThumbnail.mergeBadges(null, List.of(uid -> Set.of()), "1.2.3").isEmpty());
    assertTrue(SeriesThumbnail.mergeBadges(null, List.of(), "1.2.3").isEmpty());
  }

  @Test
  void duplicatesAreDrawnOnce() {
    Set<ResourceIconPath> merged =
        SeriesThumbnail.mergeBadges(
            Set.of(ROBOT), List.of(uid -> Set.of(ROBOT), uid -> Set.of(ROBOT, RESULT)), "1.2.3");

    assertEquals(2, merged.size());
  }

  @Test
  void aFailingProviderIsSkipped() {
    SeriesBadgeProvider broken =
        uid -> {
          throw new IllegalStateException("not ready");
        };
    SeriesBadgeProvider nullAnswer = uid -> null;

    Set<ResourceIconPath> merged =
        SeriesThumbnail.mergeBadges(
            null, List.of(broken, nullAnswer, uid -> Set.of(RESULT)), "1.2.3");

    assertEquals(List.of(RESULT), List.copyOf(merged));
  }

  @Test
  void theProviderReceivesTheSeriesUid() {
    String[] seen = new String[1];
    SeriesThumbnail.mergeBadges(
        null,
        List.of(
            uid -> {
              seen[0] = uid;
              return Set.of();
            }),
        "1.2.840.1");

    assertEquals("1.2.840.1", seen[0]);
  }
}
