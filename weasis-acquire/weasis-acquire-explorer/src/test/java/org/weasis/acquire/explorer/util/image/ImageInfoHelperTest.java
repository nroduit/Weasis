/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.explorer.util.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.Percentage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.modules.junit4.PowerMockRunner;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.gui.dialog.AcquirePublishDialog.Resolution;
import org.weasis.acquire.explorer.util.ImageInfoHelper;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;

@RunWith(PowerMockRunner.class)
public class ImageInfoHelperTest {
    @Mock
    AcquireImageInfo imgInfo;
    @Mock
    ImageElement imgElt;

    @Test
    public void testCalculateRatio() {
        PowerMockito.when(imgInfo.getImage()).thenReturn(imgElt);

        double MAX = Resolution.ULTRA_HD.getMaxSize();

        assertThat(ImageInfoHelper.calculateRatio(null, null)).isNull();
        assertThat(ImageInfoHelper.calculateRatio(imgInfo, null)).isNull();

        PowerMockito.when(imgElt.getTagValue(ArgumentMatchers.eq(TagW.ImageWidth))).thenReturn(4000);
        PowerMockito.when(imgElt.getTagValue(ArgumentMatchers.eq(TagW.ImageHeight))).thenReturn(2000);
        assertThat(ImageInfoHelper.calculateRatio(imgInfo, Resolution.ULTRA_HD)).isCloseTo(0.96,
            Percentage.withPercentage(1));
        assertThat(ImageInfoHelper.calculateRatio(imgInfo, Resolution.FULL_HD)).isCloseTo(0.48,
            Percentage.withPercentage(1));
        assertThat(ImageInfoHelper.calculateRatio(imgInfo, Resolution.HD_DVD)).isCloseTo(0.32,
            Percentage.withPercentage(1));

        PowerMockito.when(imgElt.getTagValue(ArgumentMatchers.eq(TagW.ImageWidth))).thenReturn(2000);
        PowerMockito.when(imgElt.getTagValue(ArgumentMatchers.eq(TagW.ImageHeight))).thenReturn(4000);
        assertThat(ImageInfoHelper.calculateRatio(imgInfo, Resolution.ULTRA_HD)).isCloseTo(0.96,
            Percentage.withPercentage(1));
        assertThat(ImageInfoHelper.calculateRatio(imgInfo, Resolution.FULL_HD)).isCloseTo(0.48,
            Percentage.withPercentage(1));
        assertThat(ImageInfoHelper.calculateRatio(imgInfo, Resolution.HD_DVD)).isCloseTo(0.32,
            Percentage.withPercentage(1));
    }

}
