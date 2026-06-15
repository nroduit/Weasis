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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.Font;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FontItemTest {

  private Font previousDefault;

  @BeforeEach
  void setUp() {
    previousDefault = UIManager.getFont("defaultFont");
    // Clear every key the enum may resolve, plus the global fallback, to emulate a L&F where the
    // custom Weasis font keys were never registered (see issue #836).
    for (FontItem item : FontItem.values()) {
      UIManager.put(item.getKey(), null);
    }
    UIManager.put("defaultFont", null);
  }

  @AfterEach
  void tearDown() {
    UIManager.put("defaultFont", previousDefault);
  }

  /**
   * Regression test for issue #836: applying a GSPS text annotation threw a {@link
   * NullPointerException} because {@link FontItem#getFont()} returned {@code null} when the custom
   * font key (default {@link FontItem#SMALL_SEMIBOLD} → {@code small.semibold.font}) was not
   * registered in the active look and feel. {@code getFont()} must always return a usable font.
   */
  @Test
  void getFontNeverReturnsNullWhenKeysAreMissing() {
    for (FontItem item : FontItem.values()) {
      assertNotNull(item.getFont(), () -> "getFont() returned null for " + item.getKey());
    }
  }

  @Test
  void getFontFallsBackToDefaultFontWhenKeyMissing() {
    Font fallback = new Font(Font.SERIF, Font.BOLD, 17);
    UIManager.put("defaultFont", fallback);

    assertSame(fallback, FontItem.SMALL_SEMIBOLD.getFont());
  }

  @Test
  void getFontReturnsRegisteredKeyWhenPresent() {
    Font expected = new Font(Font.MONOSPACED, Font.PLAIN, 13);
    UIManager.put(FontItem.SMALL_SEMIBOLD.getKey(), expected);

    assertSame(expected, FontItem.SMALL_SEMIBOLD.getFont());
  }

  @Test
  void getFontItemFallsBackToDefaultForUnknownKey() {
    assertEquals(FontItem.DEFAULT, FontItem.getFontItem("does.not.exist"));
    assertEquals(FontItem.MINI, FontItem.getFontItem("does.not.exist", FontItem.MINI));
    assertEquals(FontItem.SMALL_SEMIBOLD, FontItem.getFontItem(FontItem.SMALL_SEMIBOLD.getKey()));
  }
}
