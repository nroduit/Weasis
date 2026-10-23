/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image.export;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CaptureFileNameTest {

  @Test
  @DisplayName("The proposed name is the base followed by a sortable date and time")
  void appendsDateTime() {
    assertEquals(
        "screenshot_20260907-081504", // NON-NLS
        CaptureFileName.of("screenshot", LocalDateTime.of(2026, 9, 7, 8, 15, 4, 999))); // NON-NLS
  }
}
