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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The glossary: the core terms load, a word is allowed by a term or by the word list. */
class GlossaryTest {

  @Test
  void theCoreGlossaryLoadsAndAnswers() {
    Glossary glossary = Glossary.core();
    Term view = glossary.find("view").orElseThrow();
    assertAll(
        () -> assertTrue(glossary.terms().size() > 40, "terms: " + glossary.terms().size()),
        () -> assertTrue(view.definition().contains("viewer type"), view.definition()),
        () ->
            assertEquals(List.of("tab", "group"), glossary.find("tab group").orElseThrow().words()),
        () -> assertTrue(glossary.allows("view")),
        () -> assertTrue(glossary.allows("group")),
        () -> assertTrue(glossary.allows("by"), "allowed word"),
        () -> assertFalse(glossary.allows("zzz")),
        () -> assertEquals("tab group", glossary.avoided().get("dock")),
        () -> assertEquals("selected", glossary.avoided().get("current view")),
        () -> assertTrue(glossary.text().contains("tab group"), glossary.text()),
        () -> assertTrue(glossary.markdown().startsWith("## Glossary"), glossary.markdown()));
  }

  @Test
  void aTermRedefinedIsRefusedAndARepeatIsNot() {
    Glossary glossary = Glossary.core();
    glossary.add(
        List.of(Term.of("view", glossary.find("view").orElseThrow().definition())), "same");
    IllegalArgumentException refused =
        assertThrows(
            IllegalArgumentException.class,
            () -> glossary.add(List.of(Term.of("view", "something else")), "other"));
    assertTrue(refused.getMessage().startsWith("other defines 'view'"), refused.getMessage());
    Glossary extended =
        glossary.add(List.of(Term.of("slide scanner", "a device", "scanner")), "wsi");
    assertAll(
        () -> assertTrue(extended.allows("scanner") || extended.allows("slide")),
        () -> assertEquals("slide scanner", extended.avoided().get("scanner")));
  }

  @Test
  void everyCoreTermHasADefinitionAndAnAgentLine() {
    Glossary glossary = Glossary.core();
    String skill = AgentSkill.render(List.of("weasis"), glossary);
    assertAll(
        () -> assertTrue(glossary.terms().stream().allMatch(t -> !t.definition().isBlank())),
        () -> assertTrue(skill.contains("- **pane**:"), skill),
        () -> assertTrue(skill.contains("(not canvas index)"), skill));
  }
}
