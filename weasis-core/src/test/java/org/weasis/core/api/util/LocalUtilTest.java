/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.ResourceBundle.Control;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.DefaultLocale;

class LocalUtilTest {

  /**
   * Method under test:
   *
   * <ul>
   *   <li>{@link LocalUtil#textToLocale(String)}
   *   <li>{@link LocalUtil#localeToText(Locale)}
   * </ul>
   */
  @Test
  @DefaultLocale(language = "fr", country = "CH")
  void testTextToLocale() {
    Control control = Control.getControl(Control.FORMAT_PROPERTIES);

    Locale result = LocalUtil.textToLocale(null);
    assertSame(Locale.ENGLISH, result);
    assertEquals("en", LocalUtil.localeToText(null));

    result = LocalUtil.textToLocale("");
    assertSame(Locale.ENGLISH, result);
    assertEquals("en", LocalUtil.localeToText(result));

    result = LocalUtil.textToLocale("system"); // NON-NLS
    assertSame(Locale.getDefault(), result);

    result = LocalUtil.textToLocale("test-invalid$"); // NON-NLS
    assertEquals("test", LocalUtil.localeToText(result));

    result = LocalUtil.textToLocale("en"); // NON-NLS
    assertSame(Locale.ENGLISH, result);
    assertEquals("en", LocalUtil.localeToText(result));

    result = LocalUtil.textToLocale("fr_FR"); // NON-NLS
    assertSame(Locale.FRANCE, result);

    result = LocalUtil.textToLocale("zh_Hans"); // NON-NLS
    String val = control.toBundleName("", result);
    assertEquals("_zh_Hans", val);
    result = LocalUtil.textToLocale("zh-Hans-TW"); // NON-NLS
    val = control.toBundleName("message", result); // NON-NLS
    assertEquals("message_zh_Hans_TW", val);

    // ISO3 language code: SRP
    result = LocalUtil.textToLocale("en_BA-SRP"); // NON-NLS
    val = control.toBundleName("message", result); // NON-NLS
    assertEquals("message_en_BA", val);

    result = LocalUtil.textToLocale("sr_Latn"); // NON-NLS
    val = control.toBundleName("message", result); // NON-NLS
    assertEquals("message_sr_Latn", val);
  }

  /**
   * Method under test:
   *
   * <ul>
   *   <li>{@link LocalUtil#textToLocale(String)}
   *   <li>{@link LocalUtil#localeToText(Locale)}
   * </ul>
   */
  @Test
  void testSetLocaleFormat() {
    double number = 2543456.346;

    Locale locale = Locale.of("de", "CH"); // NON-NLS
    Locale.setDefault(Locale.Category.FORMAT, locale);

    NumberFormat nf = NumberFormat.getInstance();
    assertEquals("2’543’456.346", nf.format(number));
    assertEquals("2’543’456.346", String.format("%,.3f", number));
    assertEquals("2’543’456.346", "%,.3f".formatted(number));
    assertEquals(
        "Donnerstag, 25. November 2021, 15:39:59 Mittlere Greenwich-Zeit",
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL)
            .format(ZonedDateTime.of(2021, 11, 25, 15, 39, 59, 0, ZoneId.of("GMT"))));

    locale = Locale.of("fr", "CH"); // NON-NLS
    Locale.setDefault(Locale.Category.FORMAT, locale);
    nf = NumberFormat.getInstance();
    assertEquals("2 543 456,346", nf.format(number));
    assertEquals("2 543 456,346", String.format("%,.3f", number));
    assertEquals("2 543 456,346", "%,.3f".formatted(number));
    assertEquals(
        "jeudi, 25 novembre 2021, 15.39:59 h heure moyenne de Greenwich",
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL)
            .format(ZonedDateTime.of(2021, 11, 25, 15, 39, 59, 0, ZoneId.of("GMT"))));
  }
}
