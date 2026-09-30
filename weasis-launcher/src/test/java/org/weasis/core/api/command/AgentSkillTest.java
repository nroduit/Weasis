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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The instructions follow the instance: its version, its namespaces, its sections. */
class AgentSkillTest {

  private static final List<String> FULL =
      List.of("weasis", "workspace", "dicom", "view2d", "view3d", "image", "redact");

  @Test
  void theInstructionsNameTheSurfaceOfThisInstance() {
    String text = AgentSkill.render(FULL);
    assertAll(
        () -> assertFalse(text.contains("@{"), "every placeholder is filled"),
        () -> assertFalse(text.contains("<!--if:"), "no block marker is left"),
        () -> assertTrue(text.contains("command API " + CommandManager.API_VERSION), text),
        () -> assertTrue(text.contains("dicom, image, redact, view2d, view3d"), text),
        () -> assertTrue(text.contains("## Workflow"), text),
        () -> assertTrue(text.contains("weasis-cli view2d:capture"), text));
  }

  @Test
  void aSectionOfAMissingNamespaceIsLeftOut() {
    String text = AgentSkill.render(List.of("weasis", "workspace", "dicom", "view2d", "image"));
    assertAll(
        () -> assertFalse(text.contains("view3d:info"), "no 3D section without the 3D namespace"),
        () -> assertFalse(text.contains("redact:match"), "no redaction rule without the namespace"),
        () -> assertTrue(text.contains("view2d:measure"), "the rest is there"),
        () -> assertFalse(text.contains("<!--endif-->"), text));
  }
}
