/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.test.utils;

import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaReader;

@ExtendWith(MockitoExtension.class)
public class ModelListHelper extends XmlSerialisationHelper {
  public static final String UUID_1 = "uuid.1." + UUID.randomUUID(); // NON-NLS
  public static final String UUID_2 = "uuid.2." + UUID.randomUUID(); // NON-NLS
  public static final String UUID_3 = "uuid.3." + UUID.randomUUID(); // NON-NLS

  @Mock protected MediaReader mediaIO;
  @Mock protected Object key;

  protected ImageElement mockImage(String seriesUuid, String uuid) {
    ImageElement img = Mockito.mock(ImageElement.class);

    if (Objects.isNull(uuid) && Objects.isNull(seriesUuid)) {
      Mockito.when(img.getTagValue(ArgumentMatchers.any())).thenReturn(null);
    } else if (Objects.nonNull(uuid) && Objects.isNull(seriesUuid)) {
      Mockito.when(img.getTagValue(ArgumentMatchers.any())).thenReturn(uuid, null, uuid);
    } else {
      Mockito.when(img.getTagValue(ArgumentMatchers.any())).thenReturn(uuid, seriesUuid);
    }

    MediaReader mediaReader = Mockito.mock(MediaReader.class);
    Mockito.when(img.getMediaReader()).thenReturn(mediaReader);
    Mockito.when(mediaReader.getMediaElementNumber()).thenReturn(1);
    return img;
  }
}
