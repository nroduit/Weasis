/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image.lut;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.image.util.ValueHistogram.Bins;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ViewCanvas;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ViewerColorMapHostTest {

  @SuppressWarnings("unchecked")
  private static ImageViewerEventManager<ImageElement> managerShowing(ImageElement image) {
    ViewCanvas<ImageElement> view = mock(ViewCanvas.class);
    when(view.getImage()).thenReturn(image);
    ImageViewerEventManager<ImageElement> manager = mock(ImageViewerEventManager.class);
    when(manager.getSelectedViewPane()).thenReturn(view);
    return manager;
  }

  @Test
  void the_histogram_of_the_displayed_image_is_computed_once_and_rebinned_per_request() {
    ImageElement image = mock(ImageElement.class);
    when(image.getMinValue(null)).thenReturn(0.0);
    when(image.getMaxValue(null)).thenReturn(4.0);
    AtomicInteger computed = new AtomicInteger();
    var host =
        new ViewerColorMapHost<>(managerShowing(image)) {
          @Override
          protected Bins histogramOf(ImageElement img, double min, double max, int bins) {
            computed.incrementAndGet();
            return new Bins(new double[] {4, 8, 0, 2}, min, max);
          }
        };

    double[] whole = host.histogram(0, 4, 4);
    double[] lower = host.histogram(0, 2, 2);

    assertAll(
        () -> assertArrayEquals(new double[] {0, 4}, host.valueRange().orElseThrow()),
        () -> assertArrayEquals(new double[] {4, 8, 0, 2}, whole, 1e-9),
        () -> assertArrayEquals(new double[] {4, 8}, lower, 1e-9),
        () -> assertEquals(1, computed.get(), "cached per image"),
        () -> assertNull(host.histogram(3, 1, 4), "inverted range"));
  }

  @Test
  void nothing_is_reported_without_a_selected_image() {
    var host = new ViewerColorMapHost<>(managerShowing(null));
    assertAll(
        () -> assertTrue(host.valueRange().isEmpty()), () -> assertNull(host.histogram(0, 1, 8)));
  }
}
