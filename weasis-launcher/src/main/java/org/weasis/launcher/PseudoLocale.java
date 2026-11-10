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

import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The pseudo-locales, selected like any language ({@code locale.lang.code=en-XA}); no translation
 * package is fetched for them.
 *
 * <ul>
 *   <li>{@code en-XA}: the English strings rewritten with accented letters, wrapped in brackets and
 *       lengthened by about a third, so that a run of the application shows every hard-coded string
 *       as plain text and every label that cannot take a longer translation as a cut bracket.
 *   <li>{@code ar-XB}: the English strings forced to read right to left (they read backwards), and
 *       the interface mirrored as in Arabic ({@link UiOrientation}), so that every hard-coded
 *       string reads forwards and every layout written with absolute sides stays on the wrong side.
 * </ul>
 *
 * <p>The parts a translator does not touch keep their text: {@code {0}} arguments of {@code
 * MessageFormat}, HTML tags and entities, the {@code $V} value patterns of the DICOM display
 * formats.
 */
public final class PseudoLocale {

  public static final Locale LOCALE = Locale.forLanguageTag("en-XA"); // NON-NLS

  /** The right-to-left pseudo-locale. */
  public static final Locale RTL_LOCALE = Locale.forLanguageTag("ar-XB"); // NON-NLS

  private static final char RLM = '\u200F';
  private static final char RLO = '\u202E';
  private static final char PDF = '\u202C';

  private static final Pattern KEPT =
      Pattern.compile("\\{[^{}]*}|<[^<>]*>|&[a-zA-Z#0-9]+;|\\$V(?::[a-z]\\$[^$]*\\$)?");
  private static final String FROM = "aeiouycnAEIOUYCN";
  private static final String TO = "åéïöüÿçñÅÉÏÖÜŸÇÑ";

  private PseudoLocale() {}

  /** True for a pseudo-locale ({@code en-XA} or {@code ar-XB}), whatever its variant. */
  public static boolean isPseudo(Locale locale) {
    return same(LOCALE, locale) || same(RTL_LOCALE, locale);
  }

  /** True for the right-to-left pseudo-locale {@code ar-XB}, whatever its variant. */
  public static boolean isRightToLeft(Locale locale) {
    return same(RTL_LOCALE, locale);
  }

  private static boolean same(Locale pseudo, Locale locale) {
    return locale != null
        && pseudo.getLanguage().equals(locale.getLanguage())
        && pseudo.getCountry().equals(locale.getCountry());
  }

  /**
   * A bundle whose every string is the {@code en-XA} form of the given bundle's string.
   *
   * @see #wrap(ResourceBundle, Locale)
   */
  public static ResourceBundle wrap(ResourceBundle english) {
    return wrap(english, LOCALE);
  }

  /**
   * A bundle whose every string is the pseudo-localized form, for the given pseudo-locale, of the
   * given bundle's string: {@link #transformRtl} for {@code ar-XB}, {@link #transform} otherwise.
   */
  public static ResourceBundle wrap(ResourceBundle english, Locale pseudo) {
    boolean rtl = isRightToLeft(pseudo);
    Locale locale = rtl ? RTL_LOCALE : LOCALE;
    return new ResourceBundle() {
      @Override
      protected Object handleGetObject(String key) {
        Object value = english.getObject(key);
        if (value instanceof String s) {
          return rtl ? transformRtl(s) : transform(s);
        }
        return value;
      }

      @Override
      public Enumeration<String> getKeys() {
        return Collections.enumeration(english.keySet());
      }

      @Override
      public Locale getLocale() {
        return locale;
      }
    };
  }

  /**
   * The pseudo-localized form of a string: accented letters, a {@code [} {@code ]} frame, {@code ~}
   * padding to about 130 % of the length. A string without a letter is returned as is; an HTML
   * string keeps its root tag outside the frame so that Swing still renders it as HTML.
   */
  public static String transform(String text) {
    if (text == null || text.chars().noneMatch(Character::isLetter)) {
      return text;
    }
    String lower = text.toLowerCase(Locale.ROOT);
    if (lower.startsWith("<html>")) { // NON-NLS
      int end = lower.lastIndexOf("</html>"); // NON-NLS
      String body = end < 0 ? text.substring(6) : text.substring(6, end);
      return text.substring(0, 6) + frame(body) + (end < 0 ? "" : text.substring(end));
    }
    return frame(text);
  }

  private static String frame(String text) {
    StringBuilder out = new StringBuilder(text.length() + text.length() / 2).append('[');
    Matcher m = KEPT.matcher(text);
    int last = 0;
    int letters = 0;
    while (m.find()) {
      letters += accent(text.substring(last, m.start()), out);
      out.append(m.group());
      last = m.end();
    }
    letters += accent(text.substring(last), out);
    out.append("~".repeat(Math.max(1, Math.round(letters * 0.3f))));
    return out.append(']').toString();
  }

  /**
   * The {@code ar-XB} form of a string: each run of text forced right to left (U+202E RIGHT-TO-LEFT
   * OVERRIDE … U+202C), the whole between two U+200F RIGHT-TO-LEFT MARK, so that it reads backwards
   * and lays out right to left. The arguments, tags, entities and value patterns are left outside
   * the override, so that a filled value reads forwards. A string without a letter is returned as
   * is; an HTML string keeps its root tag first so that Swing still renders it as HTML.
   */
  public static String transformRtl(String text) {
    if (text == null || text.chars().noneMatch(Character::isLetter)) {
      return text;
    }
    String lower = text.toLowerCase(Locale.ROOT);
    if (lower.startsWith("<html>")) { // NON-NLS
      int end = lower.lastIndexOf("</html>"); // NON-NLS
      String body = end < 0 ? text.substring(6) : text.substring(6, end);
      return text.substring(0, 6) + overridden(body) + (end < 0 ? "" : text.substring(end));
    }
    return overridden(text);
  }

  private static String overridden(String text) {
    StringBuilder out = new StringBuilder(text.length() + 8).append(RLM);
    Matcher m = KEPT.matcher(text);
    int last = 0;
    while (m.find()) {
      override(text.substring(last, m.start()), out);
      out.append(m.group());
      last = m.end();
    }
    override(text.substring(last), out);
    return out.append(RLM).toString();
  }

  private static void override(String segment, StringBuilder out) {
    if (!segment.isEmpty()) {
      out.append(RLO).append(segment).append(PDF);
    }
  }

  private static int accent(String segment, StringBuilder out) {
    int letters = 0;
    for (int i = 0; i < segment.length(); i++) {
      char c = segment.charAt(i);
      int index = FROM.indexOf(c);
      out.append(index < 0 ? c : TO.charAt(index));
      if (Character.isLetter(c)) {
        letters++;
      }
    }
    return letters;
  }
}
