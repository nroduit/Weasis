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

import java.text.MessageFormat;
import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;

class PseudoLocaleTest {

  @Test
  void theLettersAreAccentedTheTextFramedAndLengthened() {
    String out = PseudoLocale.transform("Cancel");
    assertAll(
        () -> assertTrue(out.startsWith("[") && out.endsWith("]"), out),
        () -> assertTrue(out.contains("Çåñçél"), out),
        () -> assertTrue(out.length() >= 9, out));
  }

  @Test
  void argumentsTagsEntitiesAndValuePatternsAreKept() {
    String message = PseudoLocale.transform("Delete {0} graphic(s) of {1,number,0.00}?");
    String html = PseudoLocale.transform("<html><b>Open</b> &amp; close<br>now</html>");
    String format = PseudoLocale.transform("Size: $V:f$#,##0.##$ mm");
    assertAll(
        () -> assertTrue(message.contains("{0}") && message.contains("{1,number,0.00}"), message),
        () -> {
          String filled = new MessageFormat(message, Locale.ROOT).format(new Object[] {3, 7});
          assertTrue(filled.contains("3") && filled.contains("7.00"), filled);
        },
        () -> assertTrue(html.startsWith("<html>[") && html.endsWith("]</html>"), html),
        () ->
            assertTrue(
                html.contains("<b>") && html.contains("&amp;") && html.contains("<br>"), html),
        () -> assertTrue(format.contains("$V:f$#,##0.##$"), format));
  }

  @Test
  void aStringWithoutLettersAndTheLocaleTestAreUnchanged() {
    assertAll(
        () -> assertEquals("{0} %", PseudoLocale.transform("{0} %")),
        () -> assertEquals("", PseudoLocale.transform("")),
        () -> assertTrue(PseudoLocale.isPseudo(Locale.forLanguageTag("en-XA"))),
        () -> assertFalse(PseudoLocale.isPseudo(Locale.ENGLISH)),
        () -> assertFalse(PseudoLocale.isPseudo(Locale.forLanguageTag("fr-XA"))),
        () -> assertFalse(PseudoLocale.isPseudo(null)));
  }

  @Test
  void aWrappedBundleTransformsEveryStringAndKeepsTheKeys() {
    ResourceBundle english =
        new ListResourceBundle() {
          @Override
          protected Object[][] getContents() {
            return new Object[][] {{"a", "Open"}, {"b", "Close {0}"}};
          }
        };
    ResourceBundle pseudo = PseudoLocale.wrap(english);
    assertAll(
        () -> assertEquals(2, pseudo.keySet().size()),
        () -> assertTrue(pseudo.getString("a").contains("Öpéñ"), pseudo.getString("a")),
        () -> assertTrue(pseudo.getString("b").contains("{0}")),
        () -> assertEquals(PseudoLocale.LOCALE, pseudo.getLocale()));
  }

  @Test
  void theRightToLeftFormOverridesTheTextAndKeepsTheArgumentsOutside() {
    String out = PseudoLocale.transformRtl("Open {0} now");
    String html = PseudoLocale.transformRtl("<html><b>Open</b></html>");
    assertAll(
        () -> assertEquals("\u200F\u202EOpen \u202C{0}\u202E now\u202C\u200F", out),
        () -> assertTrue(html.startsWith("<html>\u200F") && html.endsWith("\u200F</html>"), html),
        () -> assertTrue(html.contains("\u202EOpen\u202C"), html),
        () -> assertEquals("{0} %", PseudoLocale.transformRtl("{0} %")),
        () -> assertTrue(PseudoLocale.isPseudo(Locale.forLanguageTag("ar-XB"))),
        () -> assertTrue(PseudoLocale.isRightToLeft(Locale.forLanguageTag("ar-XB"))),
        () -> assertFalse(PseudoLocale.isRightToLeft(PseudoLocale.LOCALE)),
        () -> assertFalse(PseudoLocale.isPseudo(Locale.forLanguageTag("ar"))));
  }

  @Test
  void aBundleWrappedForArXbIsRightToLeftAndEnXaUnchanged() {
    ResourceBundle english =
        new ListResourceBundle() {
          @Override
          protected Object[][] getContents() {
            return new Object[][] {{"a", "Open"}};
          }
        };
    ResourceBundle rtl = PseudoLocale.wrap(english, PseudoLocale.RTL_LOCALE);
    ResourceBundle xa = PseudoLocale.wrap(english, PseudoLocale.LOCALE);
    assertAll(
        () -> assertEquals("\u200F\u202EOpen\u202C\u200F", rtl.getString("a")),
        () -> assertEquals(PseudoLocale.RTL_LOCALE, rtl.getLocale()),
        () -> assertEquals(PseudoLocale.wrap(english).getString("a"), xa.getString("a")));
  }
}
