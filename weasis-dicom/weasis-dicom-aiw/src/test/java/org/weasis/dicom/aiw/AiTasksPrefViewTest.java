/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.aiw;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.dcm4che3.data.Code;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.dicom.aiw.UpsNodes.Node;
import org.weasis.dicom.aiw.pref.AiTasksPrefView;

/** Restore on the AI tasks page undoes the unsaved edits; it must not wipe the saved servers. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class AiTasksPrefViewTest {

  @TempDir Path folder;
  private Path installed;

  private static Node node(String id, String url) {
    return new Node(
        id,
        url,
        true,
        true,
        "VIEWER",
        true,
        45,
        List.of(new Code("CT-ORGAN-SEG", "99SITE", null, "Organ segmentation")));
  }

  @Test
  void restore_then_close_keeps_the_saved_servers() throws IOException {
    installed = UpsNodes.install(folder.resolve("upsNodes.json"));
    UpsNodes.save(List.of(node("pacs", null), node("byUrl", "https://b/rs")));

    AiTasksPrefView view = new AiTasksPrefView();
    view.resetToDefaultValues();
    view.closeAdditionalWindow();

    assertEquals(2, UpsNodes.all().size(), "Restore must not persist an emptied server list");
  }

  @AfterEach
  void restoreDocument() {
    UpsNodes.restore(installed);
  }
}
