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

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.weasis.acquire.explorer.gui.dialog.AcquirePublishDialog.Resolution;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;

@ExtendWith(MockitoExtension.class)
class PublishDicomTaskTest {
  @Mock AcquireImageInfo imgInfo;
  @Mock ImageElement imgElt;

  @Test
  void testCalculateRatio() {
    Mockito.when(imgInfo.getImage()).thenReturn(imgElt);

    assertThat(PublishDicomTask.calculateRatio(null, null)).isNull();
    assertThat(PublishDicomTask.calculateRatio(imgInfo, null)).isNull();
    assertThat(PublishDicomTask.calculateRatio(imgInfo, Resolution.ORIGINAL)).isNull();

    Mockito.when(imgElt.getTagValue(TagW.ImageWidth)).thenReturn(4000);
    Mockito.when(imgElt.getTagValue(TagW.ImageHeight)).thenReturn(2000);
    assertThat(PublishDicomTask.calculateRatio(imgInfo, Resolution.ULTRA_HD))
        .isCloseTo(0.96, Percentage.withPercentage(1));
    assertThat(PublishDicomTask.calculateRatio(imgInfo, Resolution.FULL_HD))
        .isCloseTo(0.48, Percentage.withPercentage(1));
    assertThat(PublishDicomTask.calculateRatio(imgInfo, Resolution.HD_DVD))
        .isCloseTo(0.32, Percentage.withPercentage(1));

    Mockito.when(imgElt.getTagValue(TagW.ImageWidth)).thenReturn(2000);
    Mockito.when(imgElt.getTagValue(TagW.ImageHeight)).thenReturn(4000);
    assertThat(PublishDicomTask.calculateRatio(imgInfo, Resolution.ULTRA_HD))
        .isCloseTo(0.96, Percentage.withPercentage(1));
    assertThat(PublishDicomTask.calculateRatio(imgInfo, Resolution.FULL_HD))
        .isCloseTo(0.48, Percentage.withPercentage(1));
    assertThat(PublishDicomTask.calculateRatio(imgInfo, Resolution.HD_DVD))
        .isCloseTo(0.32, Percentage.withPercentage(1));
  }
}
