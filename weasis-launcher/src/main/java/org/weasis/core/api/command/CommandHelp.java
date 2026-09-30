/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.command;

import java.util.ArrayList;
import java.util.List;

/**
 * Text forms of one level of the navigable help: a title followed by entries (namespaces or verbs),
 * rendered either as a readable table or as an index that a client parses back.
 */
public final class CommandHelp {

  /** Width of the rendered text, the same as the Picocli usage messages. */
  public static final int WIDTH = 100;

  private static final String INDENT = "  ";

  /** A namespace or a verb with its one-line summary. */
  public record Entry(String name, String summary) {}

  /** A parsed index: the title of the level and its entries. */
  public record Index(String title, List<Entry> entries) {}

  private CommandHelp() {}

  /** The index form: the title on the first line, then one entry per line as name, tab, summary. */
  public static String index(String title, List<Entry> entries) {
    StringBuilder sb = new StringBuilder(singleLine(title)).append('\n');
    for (Entry entry : entries) {
      sb.append(entry.name()).append('\t').append(singleLine(entry.summary())).append('\n');
    }
    return sb.toString();
  }

  /** Parses {@link #index(String, List)}; lines without a tab after the first are ignored. */
  public static Index parseIndex(String text) {
    List<String> lines = text.lines().toList();
    if (lines.isEmpty()) {
      return new Index("", List.of());
    }
    List<Entry> entries = new ArrayList<>();
    for (String line : lines.subList(1, lines.size())) {
      int tab = line.indexOf('\t');
      if (tab > 0) {
        entries.add(new Entry(line.substring(0, tab), line.substring(tab + 1).strip()));
      }
    }
    return new Index(lines.getFirst(), List.copyOf(entries));
  }

  /**
   * The readable form: the wrapped title, a blank line, then an aligned and optionally numbered row
   * per entry.
   */
  public static String table(String title, List<Entry> entries, boolean numbered) {
    StringBuilder sb = new StringBuilder();
    if (!title.isBlank()) {
      sb.append(wrap(title, WIDTH, "")).append('\n').append('\n');
    }
    int nameWidth = entries.stream().mapToInt(e -> e.name().length()).max().orElse(0);
    int numberWidth = numbered ? String.valueOf(entries.size()).length() : 0;
    for (int i = 0; i < entries.size(); i++) {
      Entry entry = entries.get(i);
      String head =
          INDENT
              + (numbered ? padLeft(String.valueOf(i + 1), numberWidth) + INDENT : "")
              + padRight(entry.name(), nameWidth)
              + INDENT;
      sb.append(head)
          .append(wrap(entry.summary(), WIDTH - head.length(), " ".repeat(head.length())))
          .append('\n');
    }
    return sb.toString();
  }

  /** The first sentence of a description, for a one-line summary. */
  public static String firstSentence(String text) {
    String line = singleLine(text);
    int end = line.indexOf(". ");
    return end < 0 ? line : line.substring(0, end + 1);
  }

  /** Word-wraps {@code text} to {@code width}; continuation lines start with {@code indent}. */
  public static String wrap(String text, int width, String indent) {
    StringBuilder sb = new StringBuilder();
    int column = 0;
    for (String word : text.strip().split("\\s+")) {
      if (word.isEmpty()) {
        continue;
      }
      if (column > 0 && column + 1 + word.length() > width) {
        sb.append('\n').append(indent);
        column = 0;
      } else if (column > 0) {
        sb.append(' ');
        column++;
      }
      sb.append(word);
      column += word.length();
    }
    return sb.toString();
  }

  private static String singleLine(String text) {
    return text == null ? "" : text.replaceAll("\\s+", " ").strip();
  }

  private static String padRight(String s, int width) {
    return s.length() >= width ? s : s + " ".repeat(width - s.length());
  }

  private static String padLeft(String s, int width) {
    return s.length() >= width ? s : " ".repeat(width - s.length()) + s;
  }
}
