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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The glossary of the command surface: the terms of the platform ({@link #core()}) and the terms
 * each namespace contributes through {@link CommandExtension#getTerms()}, plus the words allowed in
 * option names that are not nouns (verbs, adjectives, prepositions). It is data read by {@code help
 * glossary}, written into the descriptor and used by the vocabulary checks; a term declared twice
 * with another definition is refused, so two plugins cannot disagree on a word.
 *
 * <p>Resource format, one term per line: {@code term | definition | synonym, synonym}. The third
 * field, optional, lists the words or phrases that must not appear in command text for that
 * concept. Lines starting with {@code #} are comments.
 */
public final class Glossary {
  private static final String TERMS_RESOURCE = "command-glossary.txt"; // NON-NLS
  private static final String WORDS_RESOURCE = "command-words.txt"; // NON-NLS
  private static final Pattern SEPARATOR = Pattern.compile("\\s*\\|\\s*");
  private static final Pattern COMMA = Pattern.compile("\\s*,\\s*");

  private final Map<String, Term> terms = new TreeMap<>();
  private final Set<String> words = new TreeSet<>();

  private Glossary() {}

  /** The terms of the platform and the words allowed in option names, from the launcher. */
  public static Glossary core() {
    Glossary glossary = new Glossary();
    glossary.add(read(Glossary.class, TERMS_RESOURCE), "core"); // NON-NLS
    glossary.words.addAll(readWords(Glossary.class, WORDS_RESOURCE));
    return glossary;
  }

  /** An empty glossary, for a test that builds its own. */
  public static Glossary empty() {
    return new Glossary();
  }

  /** The terms of a resource next to {@code owner}, in the format described above. */
  public static List<Term> read(Class<?> owner, String resource) {
    List<Term> terms = new ArrayList<>();
    for (String line : lines(owner, resource)) {
      String[] fields = SEPARATOR.split(line, -1);
      if (fields.length < 2) {
        throw new IllegalArgumentException(
            resource + ": a term line is 'term | definition | synonyms': " + line);
      }
      List<String> avoid =
          fields.length > 2 && !fields[2].isBlank() ? List.of(COMMA.split(fields[2])) : List.of();
      terms.add(new Term(fields[0], fields[1], avoid, List.of()));
    }
    return terms;
  }

  private static Set<String> readWords(Class<?> owner, String resource) {
    Set<String> words = new TreeSet<>();
    for (String line : lines(owner, resource)) {
      for (String word : line.split("\\s+")) {
        words.add(word.toLowerCase(Locale.ROOT));
      }
    }
    return words;
  }

  private static List<String> lines(Class<?> owner, String resource) {
    try (InputStream in = owner.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException(
            "The vocabulary resource " + resource + " is missing next to " + owner.getName());
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8)
          .lines()
          .map(String::strip)
          .filter(l -> !l.isEmpty() && !l.startsWith("#"))
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Adds the terms of a namespace; a term already there with another definition is refused with
   * the name of the owner, so a plugin cannot redefine a word of the platform.
   */
  public Glossary add(Collection<Term> contributed, String owner) {
    for (Term term : contributed) {
      Term known = terms.get(term.term());
      if (known != null && !known.agreesWith(term)) {
        throw new IllegalArgumentException(
            owner
                + " defines '"
                + term.term()
                + "' as '"
                + term.definition()
                + "' but it already means '"
                + known.definition()
                + "'");
      }
      if (known == null) {
        terms.put(term.term(), term);
      }
    }
    return this;
  }

  /** A copy of this glossary with the terms of {@code extension}. */
  public Glossary with(CommandExtension extension) {
    Glossary copy = new Glossary();
    copy.terms.putAll(terms);
    copy.words.addAll(words);
    copy.add(extension.getTerms(), extension.getNamespace());
    return copy;
  }

  public Collection<Term> terms() {
    return List.copyOf(terms.values());
  }

  public Optional<Term> find(String term) {
    return Optional.ofNullable(terms.get(term.strip().toLowerCase(Locale.ROOT)));
  }

  /**
   * Whether a word may appear in an option name or a result key: a word of a term, or one of the
   * allowed non-noun words.
   */
  public boolean allows(String word) {
    String w = word.toLowerCase(Locale.ROOT);
    if (known(w)) {
      return true;
    }
    // the plural of a known word is the same word: views, entries, boxes
    if (w.endsWith("ies") && known(w.substring(0, w.length() - 3) + "y")) {
      return true;
    }
    if (w.endsWith("es") && known(w.substring(0, w.length() - 2))) {
      return true;
    }
    return w.endsWith("s") && known(w.substring(0, w.length() - 1));
  }

  private boolean known(String w) {
    if (words.contains(w)) {
      return true;
    }
    for (Term term : terms.values()) {
      if (term.words().contains(w)) {
        return true;
      }
    }
    return false;
  }

  /** The synonyms that must not appear in command text, with the term to use instead. */
  public Map<String, String> avoided() {
    Map<String, String> avoided = new TreeMap<>();
    for (Term term : terms.values()) {
      for (String synonym : term.avoid()) {
        avoided.put(synonym.toLowerCase(Locale.ROOT), term.term());
      }
    }
    return avoided;
  }

  /** One line per term, for {@code help glossary}. */
  public String text() {
    List<CommandHelp.Entry> entries =
        terms.values().stream()
            .map(t -> new CommandHelp.Entry(t.term(), summary(t)))
            .toList();
    return CommandHelp.table("Glossary of the command surface", entries, false);
  }

  /** The index form of {@code help glossary --index}: one line per term. */
  public String index() {
    List<CommandHelp.Entry> entries =
        terms.values().stream().map(t -> new CommandHelp.Entry(t.term(), t.definition())).toList();
    return CommandHelp.index("Glossary", entries);
  }

  /** The detail of one term, for {@code help glossary <term>}. */
  public String text(Term term) {
    StringBuilder sb = new StringBuilder(term.term()).append(": ").append(term.definition());
    if (!term.avoid().isEmpty()) {
      sb.append('\n').append("Do not use: ").append(String.join(", ", term.avoid()));
    }
    if (!term.types().isEmpty()) {
      sb.append('\n').append("Types: ").append(String.join(", ", term.types()));
    }
    return sb.append('\n').toString();
  }

  private static String summary(Term term) {
    return term.avoid().isEmpty()
        ? term.definition()
        : term.definition() + " (not: " + String.join(", ", term.avoid()) + ")";
  }

  /** The Markdown table of the terms, for the reference. */
  public String markdown() {
    StringBuilder sb = new StringBuilder("## Glossary\n\n");
    sb.append("| Term | Definition | Do not use |\n|---|---|---|\n");
    for (Term term : terms.values()) {
      sb.append("| ")
          .append(term.term())
          .append(" | ")
          .append(term.definition().replace("|", "\\|"))
          .append(" | ")
          .append(String.join(", ", term.avoid()))
          .append(" |\n");
    }
    return sb.append('\n').toString();
  }

  /** The {@code glossary} array of the descriptor. */
  public void write(JsonWriter out) {
    out.beginArray();
    for (Term term : terms.values()) {
      out.beginObject()
          .name("term") // NON-NLS
          .value(term.term())
          .name("definition") // NON-NLS
          .value(term.definition());
      if (!term.avoid().isEmpty()) {
        out.name("avoid").beginArray(); // NON-NLS
        term.avoid().forEach(out::value);
        out.endArray();
      }
      if (!term.types().isEmpty()) {
        out.name("types").beginArray(); // NON-NLS
        term.types().forEach(out::value);
        out.endArray();
      }
      out.endObject();
    }
    out.endArray();
  }
}
