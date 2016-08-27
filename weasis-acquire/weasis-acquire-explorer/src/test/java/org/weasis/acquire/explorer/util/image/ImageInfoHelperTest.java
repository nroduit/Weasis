/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.acquire.explorer.util.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.modules.junit4.PowerMockRunner;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.gui.dialog.AcquirePublishDialog.EResolution;
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
        
        double MAX = 3000;
        
        assertThat(ImageInfoHelper.calculateRatio(null, null, null)).isNull();
        assertThat(ImageInfoHelper.calculateRatio(imgInfo, null, null)).isNull();
        assertThat(ImageInfoHelper.calculateRatio(imgInfo, EResolution.original, null)).isNull();
        
        assertThat(ImageInfoHelper.calculateRatio(imgInfo, EResolution.original, MAX)).isNull();
        
        PowerMockito.when(imgElt.getTagValue(Matchers.eq(TagW.ImageWidth))).thenReturn(4000);
        PowerMockito.when(imgElt.getTagValue(Matchers.eq(TagW.ImageHeight))).thenReturn(2000);
        assertThat(ImageInfoHelper.calculateRatio(imgInfo, EResolution.hd, MAX)).isEqualTo(0.75d);
        assertThat(ImageInfoHelper.calculateRatio(imgInfo, EResolution.md, MAX)).isEqualTo(0.5d);
        
        PowerMockito.when(imgElt.getTagValue(Matchers.eq(TagW.ImageWidth))).thenReturn(2000);
        PowerMockito.when(imgElt.getTagValue(Matchers.eq(TagW.ImageHeight))).thenReturn(4000);
        assertThat(ImageInfoHelper.calculateRatio(imgInfo, EResolution.hd, MAX)).isEqualTo(0.75d);
        assertThat(ImageInfoHelper.calculateRatio(imgInfo, EResolution.md, MAX)).isEqualTo(0.5d);
    }

}
