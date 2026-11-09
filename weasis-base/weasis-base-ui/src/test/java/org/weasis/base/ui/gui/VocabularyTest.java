/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.ui.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.command.CommandVocabulary;

/** Every value of the namespace is labelled and carries the same type everywhere. */
class VocabularyTest {

  @Test
  void theWeasisNamespaceFollowsTheVocabulary() {
    assertEquals(List.of(), CommandVocabulary.violations("weasis", new WeasisCommands()));
  }

  @Test
  void thePluginsNamespaceFollowsTheVocabularyAndDeclaresItsResults() {
    assertEquals(
        List.of(), CommandVocabulary.violations("plugins", new PluginCommands())); // NON-NLS
    assertEquals(
        List.of(), CommandVocabulary.resultViolations("plugins", new PluginCommands())); // NON-NLS
  }
}
