/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class AffineTransformOpTest {

  @Test
  void output_size_keeps_the_partial_last_pixel() {
    assertAll(
        () -> assertEquals(17.0, AffineTransformOp.ceilSize(16.7)),
        () -> assertEquals(2.0, AffineTransformOp.ceilSize(1.01)),
        () -> assertEquals(512.0, AffineTransformOp.ceilSize(512.0)));
  }

  @Test
  void output_size_ignores_floating_point_noise() {
    assertAll(
        () -> assertEquals(512.0, AffineTransformOp.ceilSize(512.0000000001)),
        () -> assertEquals(512.0, AffineTransformOp.ceilSize(511.9999999999)));
  }
}
