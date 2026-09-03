/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image.dockable;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.text.DecimalFormatSymbols;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.GraphicToolContext;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.opencv.data.PlanarImage;

/** Pointing at the curve reads a sample and marks its pixel on the image. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class IntensityProfilePanelTest {

  /** A view on a 20 x 20 image whose pixel value is its column, with 0.5 mm pixels. */
  private static ViewCanvas<?> view() {
    PlanarImage image = mock(PlanarImage.class);
    when(image.width()).thenReturn(20);
    when(image.height()).thenReturn(20);
    when(image.get(anyInt(), anyInt()))
        .thenAnswer(inv -> new double[] {inv.getArgument(1, Integer.class)});
    MeasurableLayer layer = mock(MeasurableLayer.class);
    when(layer.hasContent()).thenReturn(true);
    when(layer.getSourceRenderedImage()).thenReturn(image);
    when(layer.getPixelValueUnit()).thenReturn("HU"); // NON-NLS
    when(layer.getMeasurementAdapter(any()))
        .thenReturn(new MeasurementsAdapter(0.5, 0, 0, false, 0, "mm")); // NON-NLS
    when(layer.pixelToRealValue(any()))
        .thenAnswer(inv -> ((Number) inv.getArgument(0)).doubleValue());
    ViewCanvas<?> view = mock(ViewCanvas.class);
    when(view.getMeasurableLayer()).thenReturn(layer);
    return view;
  }

  private static IntensityProfilePanel panelOn(ViewCanvas<?> view) throws Exception {
    LineGraphic line = new LineGraphic();
    line.setLayerType(LayerType.MEASURE);
    line.buildGraphic(List.of(new Point2D.Double(2, 5), new Point2D.Double(12, 5)));
    IntensityProfilePanel panel = new IntensityProfilePanel();
    panel.setSize(212, 120);
    panel.update(new GraphicToolContext(null, view, line));
    return panel;
  }

  private static MouseEvent mouse(IntensityProfilePanel panel, int id, int x) {
    return new MouseEvent(panel, id, 0, 0, x, 50, 0, false);
  }

  @Test
  void the_abscissa_selects_the_nearest_sample_inside_the_margins() {
    assertAll(
        () -> assertEquals(-1, IntensityProfilePanel.sampleAt(0, 0, 0), "no curve, no sample"),
        () -> assertEquals(-1, IntensityProfilePanel.sampleAt(50, 212, 1)),
        () -> assertEquals(0, IntensityProfilePanel.sampleAt(-50, 212, 11)),
        () -> assertEquals(0, IntensityProfilePanel.sampleAt(6, 212, 11)),
        () -> assertEquals(5, IntensityProfilePanel.sampleAt(106, 212, 11)),
        () -> assertEquals(10, IntensityProfilePanel.sampleAt(206, 212, 11)),
        () -> assertEquals(10, IntensityProfilePanel.sampleAt(900, 212, 11)));
  }

  @Test
  void pointing_at_the_curve_marks_the_pixel_and_leaving_it_removes_the_mark() throws Exception {
    ViewCanvas<?> view = view();
    IntensityProfilePanel panel = panelOn(view);

    panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_MOVED, 106));
    verify(view).setProbePosition(new Point2D.Double(7.5, 5.5));

    panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_EXITED, 106));
    verify(view).setProbePosition(null);
  }

  @Test
  void the_readout_gives_the_distance_from_the_start_and_the_value() throws Exception {
    IntensityProfilePanel panel = panelOn(view());
    char mark = DecimalFormatSymbols.getInstance().getDecimalSeparator();
    String readout = panel.readout(5);
    assertAll(
        () -> assertTrue(readout.startsWith("2" + mark + "5 mm"), readout), // 5 pixels of 0.5 mm
        () -> assertTrue(readout.endsWith("7 HU"), readout)); // NON-NLS
  }
}
