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

import java.util.List;

/**
 * One word of the command vocabulary: the term every option name, JSON key and description uses for
 * a concept, its definition, and the synonyms that must not appear in command text. A term is a
 * lower-case English noun phrase; {@code types} names the semantic types that carry it.
 */
public record Term(String term, String definition, List<String> avoid, List<String> types) {

  public Term {
    if (term == null || term.isBlank()) {
      throw new IllegalArgumentException("A term has a name");
    }
    if (definition == null || definition.isBlank()) {
      throw new IllegalArgumentException("Term '" + term + "' has no definition");
    }
    term = term.strip();
    definition = definition.strip();
    avoid = avoid == null ? List.of() : List.copyOf(avoid);
    types = types == null ? List.of() : List.copyOf(types);
  }

  public static Term of(String term, String definition, String... avoid) {
    return new Term(term, definition, List.of(avoid), List.of());
  }

  /** The words of the term: {@code tab group} → {@code tab}, {@code group}. */
  public List<String> words() {
    return List.of(term.split("[\\s/-]+"));
  }

  /** Whether this term and {@code other} say the same thing, so both may be declared. */
  public boolean agreesWith(Term other) {
    return term.equals(other.term) && definition.equals(other.definition);
  }
}
