/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.launcher;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.ComponentOrientation;
import java.text.MessageFormat;
import java.util.Locale;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class UiOrientationTest {

  private static final ComponentOrientation RTL = ComponentOrientation.RIGHT_TO_LEFT;

  @Test
  void theOrientationFollowsTheLanguageUnlessThePreferenceForcesIt() {
    assertAll(
        () -> assertFalse(UiOrientation.resolve(Locale.forLanguageTag("ar"), null).isLeftToRight()),
        () ->
            assertFalse(UiOrientation.resolve(Locale.forLanguageTag("fa-IR"), "").isLeftToRight()),
        () ->
            assertFalse(UiOrientation.resolve(Locale.forLanguageTag("he"), "auto").isLeftToRight()),
        () -> assertFalse(UiOrientation.resolve(PseudoLocale.RTL_LOCALE, "AUTO").isLeftToRight()),
        () -> assertTrue(UiOrientation.resolve(Locale.FRENCH, "auto").isLeftToRight()),
        () -> assertTrue(UiOrientation.resolve(PseudoLocale.LOCALE, null).isLeftToRight()),
        () -> assertTrue(UiOrientation.resolve(Locale.forLanguageTag("ar"), "ltr").isLeftToRight()),
        () -> assertFalse(UiOrientation.resolve(Locale.ENGLISH, " rtl ").isLeftToRight()),
        () -> assertTrue(UiOrientation.resolve(Locale.ENGLISH, "unknown").isLeftToRight()),
        () -> assertTrue(UiOrientation.resolve(null, null).isLeftToRight()));
  }

  @Test
  void eachArgumentIsIsolatedExceptInsideATag() {
    MessageFormat format =
        new MessageFormat("Patient {0}: {1,number,0.0} mm in <a href=\"{2}\">{2}</a>", Locale.ROOT);
    String out =
        UiOrientation.isolateArguments(format, new Object[] {"John Smith", 12.5, "C:\\dir"});
    assertEquals(
        "Patient \u2068John Smith\u2069: \u206812.5\u2069 mm in <a href=\"C:\\dir\">\u2068C:\\dir\u2069</a>",
        out);
  }

  @Test
  void anEmptyArgumentAndNoArgumentGiveNoIsolate() {
    MessageFormat format = new MessageFormat("[{0}]{1}", Locale.ROOT);
    assertAll(
        () ->
            assertEquals(
                "[]\u2068x\u2069", UiOrientation.isolateArguments(format, new Object[] {"", "x"})),
        () ->
            assertEquals(
                "Plain",
                UiOrientation.isolateArguments(new MessageFormat("Plain"), new Object[0])));
  }

  @Test
  void applyMirrorsATreeButNotAFixedSubtree() {
    JPanel root = new JPanel();
    JLabel label = new JLabel("a");
    JPanel canvas = UiOrientation.fix(new JPanel());
    JLabel overlay = new JLabel("b");
    canvas.add(overlay);
    root.add(label);
    root.add(canvas);

    UiOrientation.apply(root, RTL);

    assertAll(
        () -> assertFalse(root.getComponentOrientation().isLeftToRight()),
        () -> assertFalse(label.getComponentOrientation().isLeftToRight()),
        () -> assertTrue(UiOrientation.isFixed(canvas)),
        () -> assertTrue(canvas.getComponentOrientation().isLeftToRight()),
        () -> assertTrue(overlay.getComponentOrientation().isLeftToRight()));
  }

  @Test
  void theRightToLeftRuleAlignsTheTextOfAnHtmlCellOnTheRight() {
    javax.swing.text.html.StyleSheet sheet = new javax.swing.text.html.StyleSheet();
    sheet.addRule(UiOrientation.RTL_HTML_RULE);
    for (String tag : new String[] {"td", "p", "body"}) {
      assertEquals(
          "right",
          String.valueOf(
              sheet.getRule(tag).getAttribute(javax.swing.text.html.CSS.Attribute.TEXT_ALIGN)),
          tag);
    }
  }
}
