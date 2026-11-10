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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The translatability rules of the English sources of every module ({@code messages.properties}
 * under {@code src/main/java}), run on the repository itself: valid {@code MessageFormat} patterns
 * and no {@code printf} placeholder, every key referenced by the code exists, every key of a bundle
 * is used, a developer note on the strings that are ambiguous out of context (one word, arguments,
 * a character limit), no hard-coded label in the Swing code, the common words defined once, and
 * every file in UTF-8 with real characters rather than {@code \\uXXXX} escapes.
 */
class I18nSourcesTest {

  /** A module with a bundle: its root, the package of its {@code Messages} class, its sources. */
  record Module(Path root, String pkg, MessageSource bundle, String mainJava, String allJava) {
    String name() {
      return root.getFileName().toString();
    }
  }

  private static final Pattern KEY_CALL =
      Pattern.compile("Messages\\.(getString|format|common)\\(\\s*\"([^\"]+)\"\\s*[),]");
  private static final Pattern QUALIFIED_CALL =
      Pattern.compile(
          "(org\\.weasis(?:\\.[a-z0-9]+)+)\\.Messages\\.(getString|format)\\(\\s*\"([^\"]+)\"\\s*[),]");
  private static final Pattern IMPORTED_MESSAGES =
      Pattern.compile("^import (org\\.weasis(?:\\.[a-z0-9]+)+)\\.Messages;", Pattern.MULTILINE);
  private static final Pattern DYNAMIC_PREFIX = Pattern.compile("\"([A-Za-z0-9_.]+\\.)\"\\s*\\+");
  private static final Pattern ONE_WORD = Pattern.compile("[A-Za-z][A-Za-z-]*\\.?");
  private static final Pattern HARD_CODED =
      Pattern.compile(
          "(?:\\.(?:setText|setToolTipText|setTitle|setAccessibleName|setAccessibleDescription)|new (?:JLabel|JButton|JCheckBox|JRadioButton|JMenuItem|JMenu|JToggleButton|JCheckBoxMenuItem|JRadioButtonMenuItem|TitledBorder))\\(\\s*\"([A-Za-z][^\"]*[a-z]{2}[^\"]*)\"");

  /** A {@code \\uXXXX} escape, not itself escaped by a preceding backslash. */
  private static final Pattern UNICODE_ESCAPE =
      Pattern.compile("(?<!\\\\)(?:\\\\\\\\)*\\\\u([0-9a-fA-F]{4})");

  private static Path repo;
  private static List<Module> modules;

  @BeforeAll
  static void readSources() throws IOException {
    repo = Path.of(System.getProperty("weasis.repo.root", "..")).toAbsolutePath().normalize();
    modules = new ArrayList<>();
    try (Stream<Path> files = Files.walk(repo)) {
      for (Path p :
          files
              .filter(f -> f.getFileName().toString().equals("messages.properties"))
              .filter(f -> f.toString().contains("/src/main/java/"))
              .filter(
                  f -> !f.toString().contains("/target/") && !f.toString().contains("/archetype/"))
              .sorted()
              .toList()) {
        String s = p.toString();
        Path root = Path.of(s.substring(0, s.indexOf("/src/")));
        Path javaRoot = root.resolve("src/main/java");
        String pkg = javaRoot.relativize(p.getParent()).toString().replace('/', '.');
        modules.add(
            new Module(
                root,
                pkg,
                MessageSource.read(p),
                concat(root.resolve("src/main/java")),
                concat(root.resolve("src"))));
      }
    }
    assertTrue(modules.size() >= 15, "modules with a bundle found under " + repo);
  }

  private static String concat(Path dir) throws IOException {
    if (!Files.isDirectory(dir)) {
      return "";
    }
    StringBuilder out = new StringBuilder();
    try (Stream<Path> files = Files.walk(dir)) {
      for (Path f : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
        out.append(withoutComments(Files.readString(f, StandardCharsets.UTF_8))).append('\n');
      }
    }
    return out.toString();
  }

  /** The source without its line and block comments, so that commented-out code does not count. */
  static String withoutComments(String java) {
    return java.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)^\\s*//.*$", "");
  }

  private static Module core() {
    return modules.stream()
        .filter(m -> m.pkg().equals("org.weasis.core"))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void every_pattern_is_a_valid_message_format_without_printf_placeholders() {
    List<String> findings = new ArrayList<>();
    for (Module m : modules) {
      TranslationChecks.checkSource(m.bundle()).forEach(f -> findings.add(m.name() + " " + f));
    }
    assertEquals(List.of(), findings);
  }

  @Test
  void every_key_the_code_asks_for_exists_in_its_bundle() throws IOException {
    Map<String, Module> byPackage = new HashMap<>();
    modules.forEach(m -> byPackage.put(m.pkg(), m));
    MessageSource common = core().bundle();
    List<String> missing = new ArrayList<>();
    for (Module m : modules) {
      try (Stream<Path> files = Files.walk(m.root().resolve("src/main/java"))) {
        for (Path f : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
          String java = withoutComments(Files.readString(f, StandardCharsets.UTF_8));
          Matcher imported = IMPORTED_MESSAGES.matcher(java);
          Module own = imported.find() ? byPackage.getOrDefault(imported.group(1), m) : m;
          Matcher c = KEY_CALL.matcher(java);
          while (c.find()) {
            String key = c.group(2);
            if (c.group(1).equals("common")) {
              if (common.get("common." + key) == null) {
                missing.add(f.getFileName() + ": common." + key);
              }
            } else if (own.bundle().get(key) == null && !qualified(java, c.start())) {
              missing.add(f.getFileName() + ": " + key);
            }
          }
          Matcher q = QUALIFIED_CALL.matcher(java);
          while (q.find()) {
            Module target = byPackage.get(q.group(1));
            if (target != null && target.bundle().get(q.group(3)) == null) {
              missing.add(f.getFileName() + ": " + q.group(1) + " " + q.group(3));
            }
          }
        }
      }
    }
    assertEquals(List.of(), missing);
  }

  /** A call written {@code org.weasis.x.Messages.getString} targets another module's bundle. */
  private static boolean qualified(String java, int callStart) {
    return callStart > 0 && java.charAt(callStart - 1) == '.';
  }

  @Test
  void every_key_of_a_bundle_is_used() {
    String allJava = modules.stream().map(Module::allJava).reduce("", String::concat);
    List<String> unused = new ArrayList<>();
    for (Module m : modules) {
      Set<String> prefixes = new HashSet<>();
      Matcher d = DYNAMIC_PREFIX.matcher(m.mainJava());
      while (d.find()) {
        prefixes.add(d.group(1));
      }
      for (String key : m.bundle().entries().keySet()) {
        if (key.startsWith("common.") && m.pkg().equals("org.weasis.core")) {
          continue;
        }
        boolean used =
            m.allJava().contains("\"" + key + "\"")
                || allJava.contains(m.pkg() + ".Messages.getString(\"" + key + "\")")
                || allJava.contains(m.pkg() + ".Messages.format(\"" + key + "\"")
                || prefixes.stream().anyMatch(key::startsWith);
        if (!used) {
          unused.add(m.name() + ": " + key);
        }
      }
    }
    assertEquals(List.of(), unused);
  }

  @Test
  void ambiguous_strings_carry_a_developer_note() {
    List<String> without = new ArrayList<>();
    for (Module m : modules) {
      for (MessageSource.Entry e : m.bundle().entries().values()) {
        boolean ambiguous =
            e.hasArguments() || e.max().isPresent() || ONE_WORD.matcher(e.value()).matches();
        if (!ambiguous) {
          continue;
        }
        List<String> note = e.note();
        if (note.isEmpty()) {
          without.add(m.name() + ": " + e.key() + " (" + e.value() + ")");
        } else if (e.hasArguments() && note.stream().noneMatch(n -> n.contains("{0}"))) {
          without.add(m.name() + ": " + e.key() + " says nothing about {0}");
        }
      }
    }
    assertEquals(List.of(), without);
  }

  @Test
  void swing_labels_are_not_hard_coded() {
    List<String> findings = new ArrayList<>();
    for (Module m : modules) {
      for (String line : m.mainJava().split("\n")) {
        String s = line.strip();
        if (s.startsWith("//") || s.startsWith("*") || s.contains("NON-NLS")) {
          continue;
        }
        Matcher h = HARD_CODED.matcher(s);
        if (h.find()) {
          findings.add(m.name() + ": " + s);
        }
      }
    }
    assertEquals(List.of(), findings);
  }

  /**
   * The sources are UTF-8 with real characters: the files, the diffs and the developer notes read
   * as text, and Transifex receives them as such ({@code UNICODEPROPERTIES} resources of {@code
   * weasis-5}). Since Java 9 a {@code PropertyResourceBundle} reads UTF-8 (JEP 226). An escape
   * stays allowed for a character that cannot be seen: a control, format or space character other
   * than the plain space (a non-breaking space, a zero-width joiner).
   */
  @Test
  void sources_are_utf8_without_unicode_escapes() throws IOException {
    List<String> findings = new ArrayList<>();
    for (Module m : modules) {
      Path file = m.bundle().path();
      try {
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(Files.readAllBytes(file)));
      } catch (CharacterCodingException e) {
        findings.add(m.name() + ": not valid UTF-8");
        continue;
      }
      List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
      for (int i = 0; i < lines.size(); i++) {
        Matcher u = UNICODE_ESCAPE.matcher(lines.get(i));
        while (u.find()) {
          int c = Integer.parseInt(u.group(1), 16);
          if (visible(c)) {
            findings.add(
                m.name()
                    + ":"
                    + (i + 1)
                    + " \\u"
                    + u.group(1)
                    + " is printable, write "
                    + Character.toString(c));
          }
        }
      }
    }
    assertEquals(List.of(), findings);
  }

  private static boolean visible(int c) {
    return switch (Character.getType(c)) {
      case Character.CONTROL,
          Character.FORMAT,
          Character.SURROGATE,
          Character.PRIVATE_USE,
          Character.UNASSIGNED,
          Character.LINE_SEPARATOR,
          Character.PARAGRAPH_SEPARATOR ->
          false;
      case Character.SPACE_SEPARATOR -> c == ' ';
      default -> true;
    };
  }

  @Test
  void the_common_words_are_defined_once() {
    MessageSource common = core().bundle();
    Map<String, String> commonByText = new LinkedHashMap<>();
    common.entries().values().stream()
        .filter(e -> e.key().startsWith("common."))
        .forEach(e -> commonByText.put(e.value(), e.key()));
    assertTrue(commonByText.size() >= 60, "the common section of weasis-core");
    // A module key that repeats a common word is reported: it is either a context the common word
    // does not fit (an overlay, an action label, a measurement name) or a key to move.
    Map<String, List<String>> repeats = new TreeMap<>();
    for (Module m : modules) {
      for (MessageSource.Entry e : m.bundle().entries().values()) {
        String c = commonByText.get(e.value());
        if (c != null && !e.key().startsWith("common.")) {
          repeats.computeIfAbsent(c, k -> new ArrayList<>()).add(m.name() + ":" + e.key());
        }
      }
    }
    repeats.forEach((c, keys) -> System.out.println("common word repeated " + c + " <- " + keys));
    assertTrue(repeats.size() < 80, "too many common words repeated in the modules: " + repeats);
  }
}
