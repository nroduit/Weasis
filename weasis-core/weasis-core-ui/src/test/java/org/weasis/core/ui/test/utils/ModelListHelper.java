/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.test.utils;

import java.util.Objects;
import java.util.UUID;

import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaReader;

public class ModelListHelper extends XmlSerialisationHelper {
    public static final String UUID_1 = "uuid.1." + UUID.randomUUID().toString(); //$NON-NLS-1$
    public static final String UUID_2 = "uuid.2." + UUID.randomUUID().toString(); //$NON-NLS-1$
    public static final String UUID_3 = "uuid.3." + UUID.randomUUID().toString(); //$NON-NLS-1$

    @Mock
    protected MediaReader mediaIO;
    @Mock
    protected Object key;

    protected ImageElement mockImage(String seriesUuid, String uuid) {
        ImageElement img = PowerMockito.mock(ImageElement.class);

        if (Objects.isNull(uuid) && Objects.isNull(seriesUuid)) {
            PowerMockito.when(img.getTagValue(ArgumentMatchers.any())).thenReturn(null);
        } else if (Objects.nonNull(uuid) && Objects.isNull(seriesUuid)) {
            PowerMockito.when(img.getTagValue(ArgumentMatchers.any())).thenReturn(uuid, null, uuid);
        } else {
            PowerMockito.when(img.getTagValue(ArgumentMatchers.any())).thenReturn(uuid, seriesUuid);
        }

        MediaReader mediaReader = PowerMockito.mock(MediaReader.class);
        PowerMockito.when(img.getMediaReader()).thenReturn(mediaReader);
        PowerMockito.when(mediaReader.getMediaElementNumber()).thenReturn(1);
        PowerMockito.when(img.getKey()).thenReturn(0);
        return img;
    }
}
