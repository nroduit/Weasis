/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.weasis.core.api.image.util.Unit;

class AcquireImageValuesTest {

  @Test
  void testConstructor() {
    AcquireImageValues a1 = new AcquireImageValues();

    assertNotNull(a1);
    assertEquals(0, a1.getBrightness());
    assertEquals(1.0, a1.getCalibrationRatio());
    assertEquals(Unit.PIXEL, a1.getCalibrationUnit());
    assertEquals(100, a1.getContrast());
    assertNull(a1.getCropZone());
    assertEquals(0, a1.getFullRotation());
    assertNull(a1.getLayerOffset());
    assertEquals(0, a1.getOrientation());
    assertEquals(0, a1.getRotation());
    assertFalse(a1.isAutoLevel());
  }
}
