/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.MaskingProfile;

/** {@link Isolated}: the default selection depends on the global session masking state. */
@Isolated
@DisplayName("MaskingProfileSelector")
class MaskingProfileSelectorTest {

  @Test
  @DisplayName("selects nothing by default and round-trips a saved profile id")
  void savedProfileIdRoundTrips() {
    MaskingProfileSelector selector = new MaskingProfileSelector();
    boolean maskedByDefault = selector.isMasking();

    selector.setSelectedProfileId(MaskingProfile.TEACHING_ID);
    String teaching = selector.getSelectedProfileId();
    selector.setSelectedProfileId("no-such-profile");
    String kept = selector.getSelectedProfileId();
    selector.setSelectedProfileId(null);
    String stillKept = selector.getSelectedProfileId();
    selector.setSelectedProfileId("");

    assertAll(
        () -> assertFalse(maskedByDefault),
        () -> assertEquals(MaskingProfile.TEACHING_ID, teaching),
        () -> assertEquals(MaskingProfile.TEACHING_ID, kept, "an unknown id keeps the selection"),
        () -> assertEquals(MaskingProfile.TEACHING_ID, stillKept, "a null id keeps the selection"),
        () -> assertNull(selector.getSelectedProfile(), "an empty id selects none"),
        () -> assertEquals("", selector.getSelectedProfileId()));
  }

  @Test
  @DisplayName("starts from the session profile while the screen is masked")
  void followsSessionMasking() {
    IdentityMask.setSessionMasking(true);
    try {
      MaskingProfileSelector selector = new MaskingProfileSelector();
      assertAll(
          () -> assertTrue(selector.isMasking()),
          () -> assertEquals(MaskingProfile.DISPLAY_ID, selector.getSelectedProfileId()));
    } finally {
      IdentityMask.setSessionMasking(false);
    }
  }

  @Test
  @DisplayName("a selector with a default starts from that profile")
  void startsFromDefaultProfile() {
    MaskingProfileSelector selector = MaskingProfileSelector.withDefault(MaskingProfile.DISPLAY_ID);
    assertEquals(MaskingProfile.DISPLAY_ID, selector.getSelectedProfileId());
  }

  @Test
  @DisplayName("the session selector has no None entry and starts from the session profile")
  void sessionSelectorStartsFromSessionProfile() {
    IdentityMask.setSessionMasking(false, MaskingProfile.teaching());
    try {
      MaskingProfileSelector selector = MaskingProfileSelector.forSession();
      boolean hasNone =
          IntStream.range(0, selector.getItemCount()).anyMatch(i -> selector.getItemAt(i) == null);
      assertAll(
          () -> assertFalse(hasNone),
          () -> assertEquals(MaskingProfile.TEACHING_ID, selector.getSelectedProfileId()));
    } finally {
      IdentityMask.setSessionMasking(false, null);
    }
  }

  @Test
  @DisplayName("binds the selected profile only while the task runs")
  void bindsOnlyDuringTask() {
    MaskingProfileSelector selector = new MaskingProfileSelector();
    selector.setSelectedProfileId(MaskingProfile.PUBLICATION_ID);
    String[] inside = new String[1];

    selector.runMaskedAction(
        () ->
            inside[0] =
                IdentityMask.active()
                    .map(mask -> ((MaskingProfile) mask.policy()).id())
                    .orElse(null));

    assertAll(
        () -> assertEquals(MaskingProfile.PUBLICATION_ID, inside[0]),
        () -> assertTrue(IdentityMask.active().isEmpty()));
  }
}
