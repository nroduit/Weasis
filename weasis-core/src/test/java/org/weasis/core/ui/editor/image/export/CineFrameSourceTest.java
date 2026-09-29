/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image.export;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.weasis.core.api.gui.util.SliderChangeListener;

@DisplayName("Cine frame source")
class CineFrameSourceTest {

  private static CineFrameSource source(
      SliderChangeListener slider, int first, int last, int stride, FrameGrabber grabber) {
    return new CineFrameSource(grabber, slider, first, last, stride, 40);
  }

  @Test
  @DisplayName("counts the frames the range and the stride select")
  void frameCount() {
    SliderChangeListener slider = mock(SliderChangeListener.class);
    FrameGrabber grabber = mock(FrameGrabber.class);
    assertAll(
        () -> assertEquals(10, source(slider, 1, 10, 1, grabber).frameCount()),
        () -> assertEquals(5, source(slider, 1, 10, 2, grabber).frameCount()),
        () -> assertEquals(4, source(slider, 1, 10, 3, grabber).frameCount()),
        () -> assertEquals(1, source(slider, 7, 7, 1, grabber).frameCount()));
  }

  @Test
  @DisplayName("walks the range with the requested stride")
  void showFrameStepsTheSlider() {
    SliderChangeListener slider = mock(SliderChangeListener.class);
    FrameGrabber grabber = mock(FrameGrabber.class);
    BufferedImage frame = new BufferedImage(2, 2, BufferedImage.TYPE_3BYTE_BGR);
    when(grabber.grab()).thenReturn(frame);

    CineFrameSource source = source(slider, 3, 9, 2, grabber);
    for (int i = 0; i < source.frameCount(); i++) {
      source.showFrame(i);
      assertAll(
          () -> assertEquals(1.0, source.renderProgress()),
          () -> assertEquals(frame, source.captureFrame()));
    }

    ArgumentCaptor<Integer> values = ArgumentCaptor.forClass(Integer.class);
    verify(slider, atLeastOnce()).setSliderValue(values.capture());
    assertEquals(List.of(3, 5, 7, 9), values.getAllValues());
  }

  @Test
  @DisplayName("puts the view back where the export found it")
  void closeRestoresTheInitialFrame() {
    SliderChangeListener slider = mock(SliderChangeListener.class);
    when(slider.getSliderValue()).thenReturn(6);
    FrameGrabber grabber = mock(FrameGrabber.class);
    when(grabber.grab()).thenReturn(new BufferedImage(2, 2, BufferedImage.TYPE_3BYTE_BGR));

    CineFrameSource source = source(slider, 1, 10, 1, grabber);
    source.showFrame(4);
    source.close();

    ArgumentCaptor<Integer> values = ArgumentCaptor.forClass(Integer.class);
    verify(slider, atLeastOnce()).setSliderValue(values.capture());
    assertEquals(6, values.getAllValues().getLast());
  }

  @Test
  @DisplayName("keeps the same display time for every frame")
  void durationIsUniform() {
    CineFrameSource source =
        source(mock(SliderChangeListener.class), 1, 10, 1, mock(FrameGrabber.class));
    assertAll(
        () -> assertEquals(40, source.durationMs(0)), () -> assertEquals(40, source.durationMs(9)));
  }
}
