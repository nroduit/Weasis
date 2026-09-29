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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.core.Messages;
import org.weasis.core.api.media.data.MaskingModelRegistry;
import org.weasis.core.api.media.data.MaskingProfile;

@DisplayName("MaskingIndicator")
class MaskingIndicatorTest {

  @Test
  @DisplayName("names the profile only when it is not the default display profile")
  void labelDependsOnSessionProfile() {
    MaskingModelRegistry registry = new MaskingModelRegistry(MaskingModelRegistry.loadBuiltIn());
    MaskingProfile display = registry.profile(MaskingProfile.DISPLAY_ID).orElseThrow();
    MaskingProfile teaching = registry.profile(MaskingProfile.TEACHING_ID).orElseThrow();
    String base = Messages.getString("masking.indicator");

    assertAll(
        () -> assertEquals(base, MaskingIndicator.label(display)),
        () -> assertEquals(base, MaskingIndicator.label(null)),
        () -> assertTrue(MaskingIndicator.label(teaching).startsWith(base)),
        () -> assertTrue(MaskingIndicator.label(teaching).endsWith("(Teaching)")));
  }
}
