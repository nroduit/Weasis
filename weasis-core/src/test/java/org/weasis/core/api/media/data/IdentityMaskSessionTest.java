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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Session masking is process-wide state, and Surefire runs with {@code <parallel>all</parallel>};
 * {@link Isolated} keeps these from masking values under another test class's feet.
 */
@Isolated
@DisplayName("IdentityMask session masking")
class IdentityMaskSessionTest {

  @AfterEach
  void clearSessionMasking() {
    IdentityMask.setSessionMasking(false, null);
  }

  @Test
  @DisplayName("applies the profile chosen when switching on and notifies a profile change")
  void usesChosenProfile() {
    MaskingProfile teaching = MaskingProfile.teaching();
    MaskingProfile publication = MaskingProfile.publication();
    int[] calls = {0};
    Runnable listener = () -> calls[0]++;

    IdentityMask.setSessionMasking(true, teaching);
    String first = IdentityMask.sessionProfile().id();
    IdentityMask.addChangeListener(listener);
    try {
      IdentityMask.setSessionMasking(true, publication);
      IdentityMask.setSessionMasking(true, publication);
    } finally {
      IdentityMask.removeChangeListener(listener);
    }

    assertAll(
        () -> assertEquals(MaskingProfile.TEACHING_ID, first),
        () -> assertEquals(publication, IdentityMask.active().orElseThrow().policy()),
        () -> assertEquals(1, calls[0], "only an actual profile change is notified"));
  }

  @Test
  @DisplayName("falls back to the configured profile when none or an unknown one is chosen")
  void fallsBackToConfiguredProfile() {
    MaskingProfile configured = MaskingModelRegistry.getInstance().sessionProfile();
    MaskingProfile unknown =
        new MaskingProfile(
            "not-configured",
            "Unknown",
            Map.of(),
            Map.of(TagCategory.DIRECT_ID, AnonymizationAction.REMOVE),
            Map.of(),
            false,
            false);

    IdentityMask.setSessionMasking(true, unknown);
    MaskingProfile afterUnknown = IdentityMask.sessionProfile();
    IdentityMask.setSessionMasking(true, null);

    assertAll(
        () -> assertEquals(configured, afterUnknown),
        () -> assertEquals(configured, IdentityMask.sessionProfile()));
  }

  @Test
  @DisplayName("makes a mask active without any binding")
  void activatesWithoutBinding() {
    assertTrue(IdentityMask.active().isEmpty());
    IdentityMask.setSessionMasking(true);
    assertAll(
        () -> assertTrue(IdentityMask.isSessionMasking()),
        () -> assertEquals(IdentityMask.session(), IdentityMask.active().orElseThrow()));
  }

  @Test
  @DisplayName("yields to a scoped binding")
  void yieldsToScopedBinding() {
    IdentityMask scoped = IdentityMask.uniform(AnonymizationAction.CLEAR);
    IdentityMask.setSessionMasking(true);
    assertEquals(scoped, scoped.callMasked(() -> IdentityMask.active().orElse(null)));
  }

  @Test
  @DisplayName("notifies listeners only on an actual state change")
  void notifiesOnChangeOnly() {
    int[] calls = {0};
    Runnable listener = () -> calls[0]++;
    IdentityMask.addChangeListener(listener);
    try {
      IdentityMask.setSessionMasking(true);
      IdentityMask.setSessionMasking(true);
      IdentityMask.setSessionMasking(false);
    } finally {
      IdentityMask.removeChangeListener(listener);
    }
    assertEquals(2, calls[0]);
  }

  @Test
  @DisplayName("a removed listener is not notified")
  void removedListenerIsSilent() {
    int[] calls = {0};
    Runnable listener = () -> calls[0]++;
    IdentityMask.addChangeListener(listener);
    IdentityMask.removeChangeListener(listener);
    IdentityMask.setSessionMasking(true);
    assertEquals(0, calls[0]);
  }
}
