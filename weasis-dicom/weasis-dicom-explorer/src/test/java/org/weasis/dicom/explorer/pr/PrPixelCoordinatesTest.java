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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.model.graphic.Graphic;

/**
 * DICOM PIXEL units put 0\0 at the top left corner of the top left pixel, the model at its centre
 * (issue #338).
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class PrPixelCoordinatesTest {

  // The square of the issue: on the pixel borders of a 16 pixel grid
  private static final float[] SQUARE = {15, 15, 15, 111, 111, 111, 111, 15, 15, 15};

  private static Attributes polyline(float... points) {
    Attributes a = new Attributes();
    a.setString(Tag.GraphicAnnotationUnits, VR.CS, "PIXEL");
    a.setInt(Tag.GraphicDimensions, VR.US, 2);
    a.setInt(Tag.NumberOfGraphicPoints, VR.US, points.length / 2);
    a.setFloat(Tag.GraphicData, VR.FL, points);
    a.setString(Tag.GraphicType, VR.CS, PrGraphicUtil.POLYLINE);
    a.setString(Tag.GraphicFilled, VR.CS, "N");
    return a;
  }

  private static Graphic read(Attributes attributes) throws Exception {
    Graphic graphic =
        PrGraphicUtil.buildGraphic(attributes, Color.YELLOW, false, 128, 128, true, null, false);
    assertNotNull(graphic);
    return graphic;
  }

  @Test
  void pixel_coordinates_are_moved_to_the_pixel_centre_convention() throws Exception {
    Rectangle2D b = read(polyline(SQUARE)).getShape().getBounds2D();

    assertEquals(14.5, b.getMinX(), 1e-6);
    assertEquals(14.5, b.getMinY(), 1e-6);
    assertEquals(110.5, b.getMaxX(), 1e-6);
    assertEquals(110.5, b.getMaxY(), 1e-6);
  }

  @Test
  void written_coordinates_are_the_ones_read() throws Exception {
    Sequence written = new Attributes().newSequence(Tag.GraphicObjectSequence, 1);
    DicomPrSerializer.transformShapeToContour(read(polyline(SQUARE)), written);

    // The contour may repeat a corner to close the path: every point must be a corner
    assertEquals(1, written.size());
    float[] data = written.getFirst().getFloats(Tag.GraphicData);
    for (float value : data) {
      assertTrue(value == 15f || value == 111f, () -> Arrays.toString(data));
    }
  }

  @Test
  void conversions_are_inverse() {
    assertEquals(-0.5, PrGraphicUtil.pixelToModel(0.0));
    assertEquals(0.0, PrGraphicUtil.modelToPixel(PrGraphicUtil.pixelToModel(0.0)));
  }
}
