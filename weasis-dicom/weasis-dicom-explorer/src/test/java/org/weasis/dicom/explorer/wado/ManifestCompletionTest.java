/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.wado;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.weasis.dicom.mf.WadoParameters;

/**
 * Tests the parser-level partial-level detection in {@link ManifestCompletion#isPartialLevel}. A
 * level is partial (its lower levels must be queried through DICOMweb) only when it belongs to a
 * {@code DICOM_WEB} arcQuery and has no children.
 */
class ManifestCompletionTest {

  private static WadoParameters wadoRs() {
    return new WadoParameters("", true, true);
  }

  private static WadoParameters wadoUri() {
    return new WadoParameters("", true, false);
  }

  @Test
  void wadoRsLevelWithoutChildrenIsPartial() {
    assertTrue(ManifestCompletion.isPartialLevel(wadoRs(), false));
  }

  @Test
  void wadoRsLevelWithChildrenIsNotPartial() {
    assertFalse(ManifestCompletion.isPartialLevel(wadoRs(), true));
  }

  @Test
  void wadoUriLevelIsNeverPartial() {
    // A childless WADO-URI level cannot be completed by query, so it is never "partial".
    assertFalse(ManifestCompletion.isPartialLevel(wadoUri(), false));
    assertFalse(ManifestCompletion.isPartialLevel(wadoUri(), true));
  }

  @Test
  void nullWadoIsNotPartial() {
    assertFalse(ManifestCompletion.isPartialLevel(null, false));
  }
}
