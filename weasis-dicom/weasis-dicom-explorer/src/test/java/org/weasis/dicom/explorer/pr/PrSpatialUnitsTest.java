/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.pr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.weasis.core.ui.model.graphic.Graphic;

/** DISPLAY units and the rotation of compound graphics (PS3.3 C.10.5 and C.10.6). */
@DisplayNameGeneration(ReplaceUnderscores.class)
class PrSpatialUnitsTest {

  // Specified Displayed Area in PIXEL units: columns 10 to 110, rows 20 to 70
  private static final Rectangle2D AREA = new Rectangle2D.Double(10, 20, 100, 50);

  /**
   * A DISPLAY point maps to the image corner that the rotation (clockwise) then the flip bring to
   * that place on the screen.
   */
  @ParameterizedTest
  @CsvSource({
    // rotation, flip, display x, display y, pixel x, pixel y
    "0, false, 0, 0, 10, 20",
    "0, false, 1, 1, 110, 70",
    "0, false, 0.5, 0.5, 60, 45",
    "90, false, 0, 0, 10, 70",
    "90, false, 1, 0, 10, 20",
    "90, false, 0, 1, 110, 70",
    "180, false, 0, 0, 110, 70",
    "270, false, 0, 0, 110, 20",
    "0, true, 0, 0, 110, 20",
    "90, true, 0, 0, 10, 20",
    "90, true, 1, 0, 10, 70",
  })
  void display_units_follow_the_spatial_transformation(
      int rotation, boolean flip, double u, double v, double x, double y) {
    Point2D p =
        PrGraphicUtil.displayToPixel(AREA, rotation, flip)
            .transform(new Point2D.Double(u, v), null);

    assertEquals(x, p.getX(), 1e-9);
    assertEquals(y, p.getY(), 1e-9);
  }

  @ParameterizedTest
  @CsvSource({"0, false", "90, false", "270, true"})
  void display_graphic_is_placed_in_the_displayed_area(int rotation, boolean flip)
      throws Exception {
    // The whole Specified Displayed Area: the same rectangle whatever the transformation
    Attributes a = new Attributes();
    a.setString(Tag.GraphicAnnotationUnits, VR.CS, "DISPLAY");
    a.setInt(Tag.GraphicDimensions, VR.US, 2);
    a.setInt(Tag.NumberOfGraphicPoints, VR.US, 5);
    a.setFloat(Tag.GraphicData, VR.FL, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0);
    a.setString(Tag.GraphicType, VR.CS, PrGraphicUtil.POLYLINE);
    a.setString(Tag.GraphicFilled, VR.CS, "N");

    Graphic graphic =
        PrGraphicUtil.buildGraphic(
            a,
            Color.YELLOW,
            false,
            AREA.getWidth(),
            AREA.getHeight(),
            false,
            PrGraphicUtil.displayToPixel(AREA, rotation, flip),
            false);

    assertNotNull(graphic);
    Rectangle2D b = graphic.getShape().getBounds2D();
    assertEquals(9.5, b.getMinX(), 1e-4);
    assertEquals(19.5, b.getMinY(), 1e-4);
    assertEquals(109.5, b.getMaxX(), 1e-4);
    assertEquals(69.5, b.getMaxY(), 1e-4);
  }

  @ParameterizedTest
  @CsvSource({
    // A positive angle turns counterclockwise: the rectangle right of its corner goes above it
    "90, -0.5, -4.5, 1.5, -0.5",
    "-90, -2.5, -0.5, -0.5, 3.5",
  })
  void compound_rotation_is_counterclockwise(
      float angle, double minX, double minY, double maxX, double maxY) throws Exception {
    Attributes a = new Attributes();
    a.setString(Tag.GraphicAnnotationUnits, VR.CS, "PIXEL");
    a.setInt(Tag.GraphicDimensions, VR.US, 2);
    a.setInt(Tag.NumberOfGraphicPoints, VR.US, 2);
    a.setFloat(Tag.GraphicData, VR.FL, 0, 0, 4, 2);
    a.setString(Tag.GraphicType, VR.CS, PrGraphicUtil.RECTANGLE);
    a.setString(Tag.GraphicFilled, VR.CS, "N");
    a.setFloat(Tag.RotationAngle, VR.FL, angle);
    a.setFloat(Tag.RotationPoint, VR.FL, 0, 0);

    Graphic graphic = PrGraphicUtil.buildGraphic(a, Color.YELLOW, false, 1, 1, false, null, true);

    assertNotNull(graphic);
    Rectangle2D b = graphic.getShape().getBounds2D();
    assertEquals(minX, b.getMinX(), 1e-4);
    assertEquals(minY, b.getMinY(), 1e-4);
    assertEquals(maxX, b.getMaxX(), 1e-4);
    assertEquals(maxY, b.getMaxY(), 1e-4);
  }
}
