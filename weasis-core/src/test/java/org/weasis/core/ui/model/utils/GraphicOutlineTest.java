/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.utils;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.pref.ViewSetting;

/** A dark halo keeps a bright line visible on a bright image. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class GraphicOutlineTest {

  private static LineGraphic line(LayerType layerType, boolean outlined) throws Exception {
    LineGraphic g =
        new LineGraphic() {
          @Override
          public boolean isOutlined() {
            return outlined;
          }
        };
    g.setLayerType(layerType);
    g.setPaint(Color.WHITE);
    g.buildGraphic(List.of(new Point2D.Double(2, 10), new Point2D.Double(38, 10)));
    return g;
  }

  /** Paints the graphic on a white image and returns the pixel just above the line. */
  private static int pixelBesideTheLine(Graphic g) {
    BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2d = image.createGraphics();
    g2d.setColor(Color.WHITE);
    g2d.fillRect(0, 0, 40, 20);
    g.paint(g2d, null);
    g2d.dispose();
    return image.getRGB(20, 9) & 0xffffff;
  }

  @Test
  void a_white_line_on_a_white_image_gets_a_dark_surround() throws Exception {
    assertAll(
        () -> assertNotEquals(0xffffff, pixelBesideTheLine(line(LayerType.MEASURE, true))),
        () -> assertEquals(0xffffff, pixelBesideTheLine(line(LayerType.MEASURE, false))));
  }

  @Test
  void only_the_measurements_and_drawings_of_the_user_are_outlined() {
    LineGraphic measure = new LineGraphic();
    measure.setLayerType(LayerType.MEASURE);
    LineGraphic draw = new LineGraphic();
    draw.setLayerType(LayerType.DRAW);
    LineGraphic presentationState = new LineGraphic();
    presentationState.setLayerType(LayerType.DICOM_PR);
    assertAll(
        () -> assertTrue(measure.isOutlined()),
        () -> assertTrue(draw.isOutlined()),
        () -> assertFalse(presentationState.isOutlined()));
  }

  @Test
  void the_halo_is_wider_and_keeps_the_dashes() {
    BasicStroke dashed =
        new BasicStroke(
            1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {5f, 5f}, 0f);
    BasicStroke halo = (BasicStroke) GraphicOutline.widen(dashed);
    assertAll(
        () -> assertEquals(1.5f + GraphicOutline.EXTRA_WIDTH, halo.getLineWidth()),
        () -> assertEquals(BasicStroke.JOIN_ROUND, halo.getLineJoin()),
        () -> assertArrayEquals(new float[] {5f, 5f}, halo.getDashArray()));
  }

  @Test
  void luminance_tells_dark_colors_from_bright_ones() {
    assertAll(
        () -> assertEquals(1.0, GraphicOutline.relativeLuminance(Color.WHITE), 1e-9),
        () -> assertEquals(0.0, GraphicOutline.relativeLuminance(Color.BLACK), 1e-9),
        () ->
            assertTrue(
                GraphicOutline.relativeLuminance(ViewSetting.DEFAULT_LINE_COLOR)
                    > GraphicOutline.MIN_LINE_LUMINANCE),
        () ->
            assertTrue(
                GraphicOutline.relativeLuminance(Color.BLUE) < GraphicOutline.MIN_LINE_LUMINANCE),
        () ->
            assertFalse(
                GraphicOutline.relativeLuminance(Color.GREEN) < GraphicOutline.MIN_LINE_LUMINANCE));
  }
}
