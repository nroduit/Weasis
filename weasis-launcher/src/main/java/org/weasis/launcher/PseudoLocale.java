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
 * The pseudo-locale {@code en-XA}: the English strings rewritten with accented letters, wrapped in
 * brackets and lengthened by about a third, so that a run of the application shows every hard-coded
 * string as plain text and every label that cannot take a longer translation as a cut bracket.
 * Selected like any language ({@code locale.lang.code=en-XA}); no translation package is fetched
 * for it.
 *
 * <p>The parts a translator does not touch keep their text: {@code {0}} arguments of {@code
 * MessageFormat}, HTML tags and entities, the {@code $V} value patterns of the DICOM display
 * formats.
 */
public final class PseudoLocale {

  public static final Locale LOCALE = Locale.forLanguageTag("en-XA"); // NON-NLS

  private static final Pattern KEPT =
      Pattern.compile("\\{[^{}]*}|<[^<>]*>|&[a-zA-Z#0-9]+;|\\$V(?::[a-z]\\$[^$]*\\$)?");
  private static final String FROM = "aeiouycnAEIOUYCN";
  private static final String TO = "åéïöüÿçñÅÉÏÖÜŸÇÑ";

  private PseudoLocale() {}

  /** True for the pseudo-locale, whatever its variant. */
  public static boolean isPseudo(Locale locale) {
    return locale != null
        && LOCALE.getLanguage().equals(locale.getLanguage())
        && LOCALE.getCountry().equals(locale.getCountry());
  }

  /** A bundle whose every string is the pseudo-localized form of the given bundle's string. */
  public static ResourceBundle wrap(ResourceBundle english) {
    return new ResourceBundle() {
      @Override
      protected Object handleGetObject(String key) {
        Object value = english.getObject(key);
        return value instanceof String s ? transform(s) : value;
      }

      @Override
      public Enumeration<String> getKeys() {
        return Collections.enumeration(english.keySet());
      }

      @Override
      public Locale getLocale() {
        return LOCALE;
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
