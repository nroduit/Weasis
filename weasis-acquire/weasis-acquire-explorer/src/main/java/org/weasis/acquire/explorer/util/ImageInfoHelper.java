/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.explorer.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.gui.dialog.AcquirePublishDialog.Resolution;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;

public class ImageInfoHelper {
    public static final Logger LOGGER = LoggerFactory.getLogger(ImageInfoHelper.class);

    private ImageInfoHelper() {
        super();
    }

    /**
     * Calculate the ratio between the image and the given resolution
     *
     * @param imgInfo
     * @param resolution
     * @return
     */
    public static Double calculateRatio(AcquireImageInfo imgInfo, Resolution resolution) {
        try {
            Objects.requireNonNull(imgInfo);
            Objects.requireNonNull(resolution);

            double expectedImageSize = resolution.getMaxSize();

            ImageElement imgElt = imgInfo.getImage();
            Integer width = (Integer) imgElt.getTagValue(TagW.ImageWidth);
            Integer height = (Integer) imgElt.getTagValue(TagW.ImageHeight);
            double currentImageSize = Math.max(width, height);
            return BigDecimal.valueOf(expectedImageSize / currentImageSize).setScale(5, RoundingMode.HALF_UP)
                .doubleValue();
        } catch (NullPointerException e) {
            LOGGER.warn("An error occurs when calculate ratio for : " + imgInfo + ", resolution=> " + resolution, e); //$NON-NLS-1$ //$NON-NLS-2$
            return null;
        }
    }
}
