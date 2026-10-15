/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.seg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.opencv.seg.Segment;

class MaskContoursTest {
  @Test
  void preservesEveryThreeByThreeMaskIncludingHolesAndDiagonalContacts() {
    for (int bits = 1; bits < 512; bits++) {
      byte[] mask = new byte[9];
      for (int i = 0; i < mask.length; i++) mask[i] = (byte) ((bits >> i) & 1);
      List<Segment> segments = MaskContours.build(mask, 3, 3);
      SegContour contour = new SegContour("test", segments);
      contour.setAttributes(new SegRegion<>(1, "test", Color.RED));
      Shape shape = contour.getSegGraphic().getShape();
      double twiceArea = 0;
      for (Segment segment : segments) {
        assertTrue(segment.size() >= 4);
        for (int i = 0; i < segment.size(); i++) {
          Point2D a = segment.get(i);
          Point2D b = segment.get((i + 1) % segment.size());
          assertTrue(a.getX() == b.getX() || a.getY() == b.getY(), "diagonal edge");
          twiceArea += a.getX() * b.getY() - b.getX() * a.getY();
        }
      }
      assertEquals(Integer.bitCount(bits), twiceArea / 2, "mask " + bits);
      for (int y = 0; y < 3; y++) {
        for (int x = 0; x < 3; x++) {
          boolean occupied = mask[y * 3 + x] != 0;
          for (double dy : new double[] {-0.4, 0, 0.4}) {
            for (double dx : new double[] {-0.4, 0, 0.4}) {
              assertEquals(occupied, shape.contains(x + dx, y + dy), "mask " + bits);
            }
          }
        }
      }
    }
  }

  @Test
  void isolatedPixelOccupiesItsWholeImagePixel() {
    List<Segment> segments = MaskContours.build(new byte[] {0, 0, 0, 1, 0, 0}, 3, 2);
    assertEquals(1, segments.size());
    SegContour contour = new SegContour("test", segments);
    contour.setAttributes(new SegRegion<>(1, "test", Color.RED));
    assertEquals(
        new Rectangle2D.Double(-0.5, 0.5, 1, 1), contour.getSegGraphic().getShape().getBounds2D());
  }

  @Test
  void diagonalPixelsHaveSeparateContours() {
    assertEquals(2, MaskContours.build(new byte[] {1, 0, 0, 1}, 2, 2).size());
  }

  @Test
  void emptyMaskHasNoContours() {
    assertTrue(MaskContours.build(new byte[6], 3, 2).isEmpty());
  }
}
