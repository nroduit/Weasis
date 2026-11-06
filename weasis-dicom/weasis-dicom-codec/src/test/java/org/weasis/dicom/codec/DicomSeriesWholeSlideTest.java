/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.TagW;

/** A series of whole slide instances is one slide: its type and tooltip. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomSeriesWholeSlideTest {

  private static DicomMediaIO reader(String mimeType) {
    DicomMediaIO reader = mock(DicomMediaIO.class);
    when(reader.getMimeType()).thenReturn(mimeType);
    return reader;
  }

  private static DicomSpecialElement level(String type, int columns, int rows) {
    DicomSpecialElement element = new DicomSpecialElement(reader(DicomMediaIO.SERIES_WSI_MIMETYPE));
    element.setTag(TagD.get(Tag.ImageType), new String[] {"ORIGINAL", "PRIMARY", type, "NONE"});
    element.setTag(TagD.get(Tag.TotalPixelMatrixColumns), columns);
    element.setTag(TagD.get(Tag.TotalPixelMatrixRows), rows);
    return element;
  }

  private static DicomSeries slide(DicomSpecialElement... elements) {
    DicomSeries series = new DicomSeries("1.2.3"); // NON-NLS
    series.setTag(TagD.get(Tag.Modality), "SM"); // NON-NLS
    List<DicomSpecialElement> list = new CopyOnWriteArrayList<>(elements);
    series.setTag(TagW.DicomSpecialElementList, list);
    return series;
  }

  @Test
  void a_series_of_slide_instances_has_the_slide_type_and_tells_its_pyramid() {
    DicomSeries series =
        slide(
            level("VOLUME", 11750, 20168), // NON-NLS
            level("VOLUME", 47003, 80672), // NON-NLS
            level("LABEL", 600, 400), // NON-NLS
            level("VOLUME", 1468, 2521)); // NON-NLS
    assertAll(
        () -> assertTrue(series.isWholeSlide()),
        () -> assertEquals(DicomMediaIO.SERIES_WSI_MIMETYPE, series.getMimeType()),
        () -> assertTrue(series.getToolTips().contains("3 levels, 47003 × 80672 pixels")));
  }

  @Test
  void a_series_of_other_microscopy_images_keeps_the_image_type() {
    DicomSeries series = new DicomSeries("1.2.4"); // NON-NLS
    series.setTag(TagD.get(Tag.Modality), "SM"); // NON-NLS
    DicomSpecialElement other = new DicomSpecialElement(reader(DicomMediaIO.IMAGE_MIMETYPE));
    series.setTag(TagW.DicomSpecialElementList, new CopyOnWriteArrayList<>(List.of(other)));
    assertAll(
        () -> assertFalse(series.isWholeSlide()),
        () -> assertEquals(DicomMediaIO.SERIES_MIMETYPE, series.getMimeType()),
        () -> assertFalse(series.getToolTips().contains("levels")));
  }
}
