/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.tests.i18n;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The rules a translated {@code messages_<lang>.properties} must satisfy against its English
 * source, and the rules the source itself must satisfy. Pure JDK, so that the same class runs in
 * the Weasis build (on the sources) and in the weasis-i18n build (on the downloaded translations):
 * {@code java TranslationChecks.java <dir>...} checks every {@code messages*.properties} found
 * under the directories and exits with 1 on a finding.
 *
 * <ul>
 *   <li>a value with a {@code {} is a valid {@link MessageFormat} pattern, and a lone apostrophe
 *       in it fails (it swallows the next quoted section: {@code l'image} must be {@code
 *       l''image});
 *   <li>no {@code printf} placeholder ({@code %s}, {@code %d}) is left;
 *   <li>a translation uses the same arguments as its source, no more, no less;
 *   <li>a translation longer than the {@code # max:N} limit of its key fails;
 *   <li>a key present in a translation but not in the source is reported (a missing key is fine,
 *       the bundle falls back to English).
 * </ul>
 */
public final class TranslationChecks {

  private static final Pattern PRINTF =
      Pattern.compile("%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[sdfn]");
  private static final Pattern LONE_APOSTROPHE = Pattern.compile("(?<!')'(?!')");
  private static final Pattern TRANSLATION =
      Pattern.compile("messages_([A-Za-z0-9_.]+)\\.properties");

  private TranslationChecks() {}

  /** A finding: the file, the key, the rule broken. */
  public record Finding(Path file, String key, String message) {
    @Override
    public String toString() {
      return file.getFileName() + " " + key + ": " + message;
    }
  }

  /** The findings on a source file alone. */
  public static List<Finding> checkSource(MessageSource source) {
    List<Finding> findings = new ArrayList<>();
    for (MessageSource.Entry e : source.entries().values()) {
      checkPattern(source.path(), e.key(), e.value(), findings);
      e.max()
          .ifPresent(
              max -> {
                if (e.value().length() > max) {
                  findings.add(
                      new Finding(
                          source.path(),
                          e.key(),
                          "the English text is longer than its own limit max:" + max));
                }
              });
    }
    return findings;
  }

  /** The findings on a translation against its source. */
  public static List<Finding> checkTranslation(MessageSource source, MessageSource translation) {
    List<Finding> findings = new ArrayList<>();
    for (MessageSource.Entry t : translation.entries().values()) {
      MessageSource.Entry s = source.get(t.key());
      if (s == null) {
        findings.add(
            new Finding(translation.path(), t.key(), "key absent from the English source"));
        continue;
      }
      checkPattern(translation.path(), t.key(), t.value(), findings);
      Set<String> expected = new HashSet<>(s.arguments());
      Set<String> found = new HashSet<>(t.arguments());
      if (!expected.equals(found)) {
        findings.add(
            new Finding(
                translation.path(),
                t.key(),
                "arguments " + found + " differ from the source " + expected));
      }
      s.max()
          .ifPresent(
              max -> {
                if (t.value().length() > max) {
                  findings.add(
                      new Finding(
                          translation.path(),
                          t.key(),
                          t.value().length() + " characters, the slot allows max:" + max));
                }
              });
    }
    return findings;
  }

  private static void checkPattern(Path file, String key, String value, List<Finding> findings) {
    Matcher printf = PRINTF.matcher(value);
    if (printf.find()) {
      findings.add(
          new Finding(
              file, key, "printf placeholder " + printf.group() + ": use {0} and Messages.format"));
    }
    if (value.indexOf('{') < 0) {
      return;
    }
    if (LONE_APOSTROPHE.matcher(value).find()) {
      findings.add(
          new Finding(file, key, "lone apostrophe in a MessageFormat pattern: write '' for '"));
    }
    try {
      new MessageFormat(value, Locale.ROOT);
    } catch (IllegalArgumentException e) {
      findings.add(new Finding(file, key, "invalid MessageFormat pattern: " + e.getMessage()));
    }
  }

  /**
   * Checks every {@code messages.properties} under the directories with its sibling translations.
   */
  public static List<Finding> checkDirectories(List<Path> dirs) throws IOException {
    List<Finding> findings = new ArrayList<>();
    for (Path dir : dirs) {
      Set<Path> folders = new HashSet<>();
      try (Stream<Path> files = Files.walk(dir)) {
        files
            .filter(p -> p.getFileName().toString().startsWith("messages"))
            .filter(p -> p.getFileName().toString().endsWith(".properties"))
            .forEach(p -> folders.add(p.getParent()));
      }
      for (Path folder : folders.stream().sorted().toList()) {
        Path source = folder.resolve("messages.properties");
        MessageSource english = Files.exists(source) ? MessageSource.read(source) : null;
        if (english != null) {
          findings.addAll(checkSource(english));
        }
        try (Stream<Path> siblings = Files.list(folder)) {
          for (Path t :
              siblings
                  .filter(p -> TRANSLATION.matcher(p.getFileName().toString()).matches())
                  .sorted()
                  .toList()) {
            MessageSource translation = MessageSource.read(t);
            // Without the English source (the weasis-i18n build only holds the translations), the
            // patterns are still checked; the arguments and the limits need the source.
            findings.addAll(
                english == null
                    ? checkSource(translation)
                    : checkTranslation(english, translation));
          }
        }
      }
    }
    return findings;
  }

  public static void main(String[] args) throws IOException {
    List<Path> dirs =
        args.length == 0 ? List.of(Path.of(".")) : Stream.of(args).map(Path::of).toList();
    List<Finding> findings = checkDirectories(dirs);
    findings.forEach(f -> System.out.println(f.file() + " " + f.key() + ": " + f.message()));
    System.out.println(findings.size() + " finding(s)");
    if (!findings.isEmpty()) {
      System.exit(1);
    }
  }
}
