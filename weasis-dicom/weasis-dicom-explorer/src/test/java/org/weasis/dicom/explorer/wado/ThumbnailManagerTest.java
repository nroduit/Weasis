/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.wado;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.net.HttpUtils;
import org.weasis.core.api.net.URLParameters;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.mf.SopInstance;
import org.weasis.dicom.mf.WadoParameters;

/** A series read from a DICOMDIR carries no thumbnail URL, only a local file to decode. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ThumbnailManagerTest {

  @Test
  void a_DICOMDIR_series_without_icon_image_sequence_attempts_no_download() {
    DicomSeries dicomSeries = mock(DicomSeries.class);
    // readDicomDirIcon() returns null when the DICOMDIR holds no Icon Image Sequence.
    when(dicomSeries.getTagValue(TagW.DirectDownloadThumbnail)).thenReturn(null);

    SopInstance instance = new SopInstance("1.2.840.10008.1.2.3.4", null);
    instance.setDirectDownloadFile("file:///media/DICOM/IMG00001");

    ThumbnailManager thumbnailManager =
        new ThumbnailManager(dicomSeries, mock(DicomModel.class), new URLParameters(Map.of()));

    try (MockedStatic<HttpUtils> httpUtils = mockStatic(HttpUtils.class)) {
      thumbnailManager.loadThumbnail(instance, new WadoParameters("", true), null);

      // any() rather than anyString(): the bug was a request issued with a null URL.
      httpUtils.verify(() -> HttpUtils.getHttpResponse(any(), any(), any()), never());
    }
  }
}
