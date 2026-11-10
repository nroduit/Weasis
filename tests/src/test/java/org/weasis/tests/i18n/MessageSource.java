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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@code messages*.properties} file read line by line, keeping what {@link java.util.Properties}
 * drops: the order of the keys, the comment lines above each key (the developer note shown by
 * Transifex) and the {@code # max:N} marker of a slot with a character limit.
 */
public final class MessageSource {

  /** One key of the file with its value, note and limit. */
  public record Entry(String key, String value, List<String> note, OptionalInt max, int line) {
    /** True when the value carries a {@code {n}} argument of {@code MessageFormat}. */
    public boolean hasArguments() {
      return ARGUMENT.matcher(value).find();
    }

    /** The argument indexes of the value, as text ({@code 0}, {@code 1}...), in order of use. */
    public List<String> arguments() {
      List<String> list = new ArrayList<>();
      Matcher m = ARGUMENT.matcher(value);
      while (m.find()) {
        list.add(m.group(1));
      }
      return list;
    }
  }

  private static final Pattern ARGUMENT = Pattern.compile("\\{(\\d+)(?:,[^{}]*)?}");
  private static final Pattern MAX = Pattern.compile("^#\\s*max:(\\d+)\\b");
  private static final Pattern KEY = Pattern.compile("^([^=:\\s#!]+)\\s*[=:]\\s*(.*)$");

  private final Path path;
  private final Map<String, Entry> entries;

  private MessageSource(Path path, Map<String, Entry> entries) {
    this.path = path;
    this.entries = entries;
  }

  public static MessageSource read(Path path) throws IOException {
    return parse(path, Files.readAllLines(path, StandardCharsets.UTF_8));
  }

  /**
   * Parses the lines of a properties file; continuation lines ({@code \} at the end) are joined.
   */
  public static MessageSource parse(Path path, List<String> lines) {
    Map<String, Entry> entries = new LinkedHashMap<>();
    List<String> note = new ArrayList<>();
    OptionalInt max = OptionalInt.empty();
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      String trimmed = line.strip();
      if (trimmed.isEmpty()) {
        note.clear();
        max = OptionalInt.empty();
        continue;
      }
      if (trimmed.startsWith("#") || trimmed.startsWith("!")) {
        Matcher mm = MAX.matcher(trimmed);
        if (mm.find()) {
          max = OptionalInt.of(Integer.parseInt(mm.group(1)));
        }
        note.add(trimmed.substring(1).strip());
        continue;
      }
      int start = i;
      StringBuilder joined = new StringBuilder(line);
      while (joined.toString().endsWith("\\") && i + 1 < lines.size()) {
        joined.setLength(joined.length() - 1);
        joined.append(lines.get(++i).stripLeading());
      }
      Matcher m = KEY.matcher(joined);
      if (m.matches()) {
        entries.put(
            m.group(1),
            new Entry(m.group(1), unescape(m.group(2)), List.copyOf(note), max, start + 1));
      }
      note.clear();
      max = OptionalInt.empty();
    }
    return new MessageSource(path, entries);
  }

  /** The {@code \\uXXXX} and {@code \\n} escapes of a properties value resolved. */
  static String unescape(String raw) {
    StringBuilder out = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c != '\\' || i + 1 >= raw.length()) {
        out.append(c);
        continue;
      }
      char n = raw.charAt(++i);
      switch (n) {
        case 'u' -> {
          if (i + 4 < raw.length()) {
            out.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
            i += 4;
          }
        }
        case 'n' -> out.append('\n');
        case 't' -> out.append('\t');
        case 'r' -> out.append('\r');
        default -> out.append(n);
      }
    }
    return out.toString();
  }

  public Path path() {
    return path;
  }

  public Map<String, Entry> entries() {
    return entries;
  }

  public Entry get(String key) {
    return entries.get(key);
  }
}
