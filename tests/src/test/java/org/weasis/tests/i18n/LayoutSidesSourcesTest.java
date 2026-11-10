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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The layouts of the interface are written in leading and trailing terms, so that they mirror when
 * the interface is right to left: no absolute side ({@code WEST}, {@code EAST}, {@code LEFT},
 * {@code RIGHT}, MigLayout {@code left}, {@code right}, {@code gapleft}, {@code gapright}) in the
 * main sources of any module, unless the line, or the line before it, says why with a {@value
 * #MARK} comment (a button drawn in the image, a time axis, the macOS window buttons...).
 */
class LayoutSidesSourcesTest {

  static final String MARK = "// absolute side:";

  /** The absolute constants of the Swing layouts and the MigLayout keywords. */
  static final Pattern ABSOLUTE =
      Pattern.compile(
          String.join(
              "|",
              "\\bGridBagConstraints\\.(?:WEST|EAST|NORTHWEST|NORTHEAST|SOUTHWEST|SOUTHEAST)\\b",
              "\\bFlowLayout\\.(?:LEFT|RIGHT)\\b",
              "\\bBorderLayout\\.(?:WEST|EAST)\\b",
              "\\b(?:SwingConstants|JLabel|JButton|JTextField|JFormattedTextField|JTabbedPane"
                  + "|AbstractButton|JToggleButton|JCheckBox|JRadioButton"
                  + "|DefaultTableCellRenderer)\\.(?:LEFT|RIGHT)\\b",
              "\\bBoxLayout\\.X_AXIS\\b",
              // a vertical box aligns its children by absolute sides, PAGE_AXIS by reading order
              "\\bnew BoxLayout\\([^;]*\\bBoxLayout\\.Y_AXIS\\b",
              // MigLayout, inside a string literal: align left, gapleft, a [left] column, "right"
              "\"[^\"\\n]*\\b(?:align|al|alignx|ax)\\s+(?:left|right)\\b",
              "\"[^\"\\n]*\\bgap(?:left|right)\\b",
              "\"[^\"\\n]*\\[(?:[^\\]\"\\n]*,\\s*)?(?:left|right)\\s*[,\\]]",
              "\\badd\\([^\"\\n]*,\\s*\"(?:[^\"\\n]*[,\\s])?(?:left|right)(?:[,\\s][^\"\\n]*)?\""));

  @Test
  void the_pattern_finds_absolute_sides_and_ignores_relative_ones() {
    for (String s :
        List.of(
            "add(panel, BorderLayout.WEST);",
            "new FlowLayout(FlowLayout.LEFT, 0, 0)",
            "c.anchor = GridBagConstraints.NORTHEAST;",
            "label.setHorizontalAlignment(SwingConstants.RIGHT);",
            "new BoxLayout(this, BoxLayout.X_AXIS)",
            "setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));",
            "add(button, \"align right, gaptop 15\");",
            "add(label, \"gapleft 10lp\");",
            "new MigLayout(\"\", \"[right][grow,fill]\")",
            "\"[grow,left]\"",
            "add(new JLabel(name), \"right\");",
            "add(label, \"span, split 3, right, gaptop 20\");")) {
      assertTrue(ABSOLUTE.matcher(s).find(), s);
    }
    for (String s :
        List.of(
            "add(panel, BorderLayout.LINE_START);",
            "new FlowLayout(FlowLayout.LEADING, 0, 0)",
            "c.anchor = GridBagConstraints.FIRST_LINE_END;",
            "new BoxLayout(this, BoxLayout.LINE_AXIS)",
            "new BoxLayout(this, BoxLayout.PAGE_AXIS)",
            "boolean vertical = axis == BoxLayout.PAGE_AXIS || axis == BoxLayout.Y_AXIS;",
            "add(button, \"align trailing, gapafter 15\");",
            "new MigLayout(\"\", \"[trailing][grow,fill]\")",
            "public static final String T_LEFT = \"left\";",
            "int width = insets.left + insets.right;",
            "drawLine(left, bottom, right, bottom);")) {
      assertFalse(ABSOLUTE.matcher(s).find(), s);
    }
  }

  @Test
  void no_absolute_side_in_the_layouts_without_a_reason() throws IOException {
    Path repo = Path.of(System.getProperty("weasis.repo.root", "..")).toAbsolutePath().normalize();
    List<String> findings = new ArrayList<>();
    int files = 0;
    try (Stream<Path> walk = Files.walk(repo)) {
      for (Path f :
          walk.filter(p -> p.toString().endsWith(".java"))
              .filter(p -> p.toString().contains("/src/main/java/"))
              .filter(
                  p -> !p.toString().contains("/target/") && !p.toString().contains("/archetype/"))
              .sorted()
              .toList()) {
        files++;
        List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
          String line = lines.get(i);
          String code = line.strip();
          if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")) {
            continue;
          }
          if (line.contains(MARK) || (i > 0 && lines.get(i - 1).contains(MARK))) {
            continue;
          }
          if (ABSOLUTE.matcher(line).find()) {
            findings.add(repo.relativize(f) + ":" + (i + 1) + ": " + code);
          }
        }
      }
    }
    assertTrue(files > 1000, "main sources found under " + repo);
    assertEquals(List.of(), findings, "absolute sides without " + MARK);
  }
}
