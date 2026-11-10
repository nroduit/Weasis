/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.command.CommandVocabulary;

/** The view3d namespace follows the command vocabulary: labels, types, words, synonyms, handles. */
class VocabularyTest {

  @Test
  void theNamespaceFollowsTheVocabulary() {
    assertEquals(List.of(), CommandVocabulary.allViolations("view3d", new Viewer3dCommands())); // NON-NLS
  }
}
