/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import java.text.DateFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import org.weasis.core.util.StringUtil;

public class LocalUtil {

  private static final DateTimeFormatter defaultDateFormatter =
      DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);
  private static final DateTimeFormatter defaultTimeFormatter =
      DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM);
  private static final DateTimeFormatter defaultDateTimeFormatter =
      DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM);

  private static Locale localeFormat = null;

  private LocalUtil() {}

  public static String localeToText(Locale value) {
    if (value == null) {
      return "en"; // NON-NLS
    }
    return value.toString();
  }

  public static Locale textToLocale(String value) {
    if (!StringUtil.hasText(value)) {
      return Locale.ENGLISH;
    }

    if ("system".equals(value)) { // NON-NLS
      return getSystemLocale();
    }

    String[] val = value.split("_", 3);
    String language = val.length > 0 ? val[0] : "";
    String country = val.length > 1 ? val[1] : "";
    String variant = val.length > 2 ? val[2] : "";

    return new Locale(language, country, variant);
  }

  public static Locale getSystemLocale() {
    String language = System.getProperty("user.language", "en"); // NON-NLS
    String country = System.getProperty("user.country", ""); // NON-NLS
    String variant = System.getProperty("user.variant", ""); // NON-NLS
    return new Locale(language, country, variant);
  }

  public static synchronized Locale getLocaleFormat() {
    Locale l = LocalUtil.localeFormat;
    if (l == null) {
      l = Locale.getDefault();
    }
    return l;
  }

  public static synchronized void setLocaleFormat(Locale value) {
    LocalUtil.localeFormat = value;
  }

  public static DecimalFormatSymbols getDecimalFormatSymbols() {
    return DecimalFormatSymbols.getInstance(getLocaleFormat());
  }

  public static NumberFormat getNumberInstance() {
    return NumberFormat.getNumberInstance(getLocaleFormat());
  }

  public static NumberFormat getIntegerInstance() {
    return NumberFormat.getIntegerInstance(getLocaleFormat());
  }

  public static NumberFormat getPercentInstance() {
    return NumberFormat.getPercentInstance(getLocaleFormat());
  }

  public static DateFormat getDateInstance(int style) {
    return DateFormat.getDateInstance(style, getLocaleFormat());
  }

  public static DateTimeFormatter getDateFormatter() {
    return defaultDateFormatter.withLocale(getLocaleFormat());
  }

  public static DateTimeFormatter getDateFormatter(FormatStyle style) {
    return DateTimeFormatter.ofLocalizedDate(style).withLocale(getLocaleFormat());
  }

  public static DateTimeFormatter getTimeFormatter() {
    return defaultTimeFormatter.withLocale(getLocaleFormat());
  }

  public static DateTimeFormatter getTimeFormatter(FormatStyle style) {
    return DateTimeFormatter.ofLocalizedTime(style).withLocale(getLocaleFormat());
  }

  public static DateTimeFormatter getDateTimeFormatter() {
    return defaultDateTimeFormatter.withLocale(getLocaleFormat());
  }

  public static DateTimeFormatter getDateTimeFormatter(FormatStyle style) {
    return DateTimeFormatter.ofLocalizedDateTime(style).withLocale(getLocaleFormat());
  }
}
