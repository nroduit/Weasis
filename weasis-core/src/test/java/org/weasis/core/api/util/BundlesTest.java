/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ListResourceBundle;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;
import org.weasis.core.Messages;

class BundlesTest {

  private static final ResourceBundle BUNDLE =
      new ListResourceBundle() {
        @Override
        protected Object[][] getContents() {
          return new Object[][] {
            {"plain", "Open"},
            {"args", "Delete {0} graphic(s) of \"{1}\"?"},
            {"apostrophe", "L''image {0}"},
            {"broken", "Unbalanced {0"}
          };
        }
      };

  @Test
  void aMissingKeyIsVisibleAndTheArgumentsAreFilled() {
    assertAll(
        () -> assertEquals("Open", Bundles.get(BUNDLE, "plain")),
        () -> assertEquals("!nope!", Bundles.get(BUNDLE, "nope")),
        () ->
            assertEquals("Delete 3 graphic(s) of \"CT\"?", Bundles.format(BUNDLE, "args", 3, "CT")),
        () -> assertEquals("L'image 2", Bundles.format(BUNDLE, "apostrophe", 2)),
        () -> assertEquals("Unbalanced {0", Bundles.format(BUNDLE, "broken", 1), "returned as is"),
        () -> assertEquals("!nope!", Bundles.format(BUNDLE, "nope", 1)));
  }

  @Test
  void theCommonSectionOfCoreAnswersEveryModule() {
    assertAll(
        () -> assertEquals("Cancel", Bundles.common("cancel")),
        () -> assertEquals(Messages.common("series"), Bundles.common("series")),
        () -> assertEquals("Series", Messages.getString("common.series")),
        () -> assertTrue(Bundles.common("nope").startsWith("!common.nope!")));
  }
}
