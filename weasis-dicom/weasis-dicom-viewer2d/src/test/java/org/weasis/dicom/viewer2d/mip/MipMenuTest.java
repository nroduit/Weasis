/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mip;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.explorer.LoadLocalDicom;
import org.weasis.dicom.viewer2d.EventManager;

@DisplayName("MipMenu")
@SuppressWarnings("unchecked")
class MipMenuTest {

  private final ImageViewerPlugin<DicomImageElement> container = mock(ImageViewerPlugin.class);
  private final ViewCanvas<DicomImageElement> view = mock(ViewCanvas.class);

  @Test
  @DisplayName("keeps no MIP view when the view cannot be replaced")
  void disposesViewThatWasNotPlaced() {
    assertNull(createMipView(false));
  }

  @Test
  @DisplayName("returns the MIP view that took the place of the view")
  void returnsPlacedView() {
    assertInstanceOf(MipView.class, createMipView(true));
  }

  private MipView createMipView(boolean replaced) {
    MediaSeries<DicomImageElement> series = mock(MediaSeries.class);
    when(series.size(null)).thenReturn(10);
    when(view.getSeries()).thenReturn(series);
    when(view.getActionsInView()).thenReturn(new HashMap<>());
    when(container.replaceView(any(), any())).thenReturn(replaced);

    try (MockedStatic<LoadLocalDicom> loader = mockStatic(LoadLocalDicom.class);
        MockedConstruction<MipView> views = mockConstruction(MipView.class)) {
      loader.when(() -> LoadLocalDicom.confirmSplittingMultiPhaseSeries(series)).thenReturn(series);

      MipView result =
          MipMenu.createMipView(mock(EventManager.class, RETURNS_DEEP_STUBS), container, view);

      MipView created = views.constructed().getFirst();
      verify(container).replaceView(view, created);
      if (replaced) {
        assertSame(created, result);
      } else {
        verify(created).disposeView();
      }
      return result;
    }
  }
}
