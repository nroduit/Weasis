/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.command.CommandVocabulary;

/** Every value of the namespace is labelled and carries the same type everywhere. */
class VocabularyTest {

  @Test
  void theWorkspaceNamespaceFollowsTheVocabulary() {
    List<String> violations = CommandVocabulary.allViolations("workspace", new WorkspaceCommands());
    assertEquals(List.of(), violations);
  }

  @Test
  void theOtherCoreNamespacesFollowTheVocabulary() {
    assertEquals(
        List.of(),
        CommandVocabulary.allViolations(
            "config", new org.weasis.core.api.util.ConfigCommands())); // NON-NLS
    assertEquals(
        List.of(),
        CommandVocabulary.allViolations("print", new org.weasis.core.ui.print.PrintCommands()));
    assertEquals(
        List.of(),
        CommandVocabulary.allViolations("ui", new org.weasis.core.ui.editor.UiCommands()));
    assertEquals(
        List.of(),
        CommandVocabulary.allViolations(
            "color", new org.weasis.core.ui.editor.image.ColorCommands())); // NON-NLS
    assertEquals(
        List.of(),
        CommandVocabulary.allViolations(
            "measure", new org.weasis.core.ui.editor.image.MeasureCommands())); // NON-NLS
    assertEquals(
        List.of(),
        CommandVocabulary.resultViolations(
            "measure", new org.weasis.core.ui.editor.image.MeasureCommands())); // NON-NLS
  }
}
