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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.weasis.acquire.explorer.gui.dialog.AcquirePublishDialog.Resolution;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;

@ExtendWith(MockitoExtension.class)
class PublishDicomTaskTest {
  @Mock AcquireImageInfo imgInfo;
  @Mock ImageElement imgElt;

  @BeforeEach
  void setUp() {
    try (AutoCloseable closeable = MockitoAnnotations.openMocks(this)) {
      // Initialize mocks before each test
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  void testCalculateRatio() {
    Mockito.when(imgInfo.getImage()).thenReturn(imgElt);

    // Assertions for null values
    assertNull(PublishDicomTask.calculateRatio(null, null));
    assertNull(PublishDicomTask.calculateRatio(imgInfo, null));
    assertNull(PublishDicomTask.calculateRatio(imgInfo, Resolution.ORIGINAL));

    Mockito.reset(imgElt);

    // Scenario 1
    Mockito.when(imgElt.getTagValue(TagW.ImageWidth)).thenReturn(4000);
    Mockito.when(imgElt.getTagValue(TagW.ImageHeight)).thenReturn(2000);
    assertTrue(
        isCloseTo(PublishDicomTask.calculateRatio(imgInfo, Resolution.ULTRA_HD), 0.96, 0.05));
    assertTrue(isCloseTo(PublishDicomTask.calculateRatio(imgInfo, Resolution.FULL_HD), 0.48, 0.05));
    assertTrue(isCloseTo(PublishDicomTask.calculateRatio(imgInfo, Resolution.HD_DVD), 0.32, 0.05));

    Mockito.reset(imgElt);

    // Scenario 2
    Mockito.when(imgElt.getTagValue(TagW.ImageWidth)).thenReturn(2000);
    Mockito.when(imgElt.getTagValue(TagW.ImageHeight)).thenReturn(4000);
    assertTrue(
        isCloseTo(PublishDicomTask.calculateRatio(imgInfo, Resolution.ULTRA_HD), 0.96, 0.05));
    assertTrue(isCloseTo(PublishDicomTask.calculateRatio(imgInfo, Resolution.FULL_HD), 0.48, 0.05));
    assertTrue(isCloseTo(PublishDicomTask.calculateRatio(imgInfo, Resolution.HD_DVD), 0.32, 0.05));
  }

  private boolean isCloseTo(Double actual, double expected, double tolerance) {
    double diff = Math.abs(actual - expected);
    return diff <= tolerance;
  }
}
