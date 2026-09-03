/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Point2D;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.opencv.core.Point3;
import org.weasis.core.api.image.measure.PlaneGeometry;
import org.weasis.core.ui.model.graphic.SpatialAnchor;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.dicom.viewer2d.mpr.MprGraphicStore.Placement;

@DisplayNameGeneration(ReplaceUnderscores.class)
class MprGraphicStoreTest {

  /** Axial plane at the given z, 1 mm pixels, same frame of reference for every plane. */
  private static PlaneGeometry axial(double z) {
    return new PlaneGeometry() {
      @Override
      public String getFrameOfReferenceUID() {
        return "1.2.3"; // NON-NLS
      }

      @Override
      public Point3 getNormal() {
        return new Point3(0, 0, 1);
      }

      @Override
      public Point3 toPatient(Point2D p) {
        return new Point3(p.getX(), p.getY(), z);
      }

      @Override
      public Point2D toImage(Point3 p) {
        return new Point2D.Double(p.x, p.y);
      }
    };
  }

  private static SpatialAnchor anchor(Point3... pts) {
    return new SpatialAnchor("1.2.3", new Point3(0, 0, 1), List.of(pts)); // NON-NLS
  }

  @Test
  void graphics_are_in_plane_crossing_or_outside_within_half_thickness() {
    SpatialAnchor flat = anchor(new Point3(0, 0, 10), new Point3(5, 5, 10.2));
    SpatialAnchor oblique = anchor(new Point3(0, 0, 5), new Point3(5, 5, 15));
    SpatialAnchor away = anchor(new Point3(0, 0, 20), new Point3(5, 5, 30));
    assertAll(
        () -> assertEquals(Placement.IN_PLANE, MprGraphicStore.classify(flat, axial(10), 0.5)),
        () -> assertEquals(Placement.CROSSING, MprGraphicStore.classify(oblique, axial(10), 0.5)),
        () -> assertEquals(Placement.OUTSIDE, MprGraphicStore.classify(away, axial(10), 0.5)),
        () -> assertEquals(Placement.OUTSIDE, MprGraphicStore.classify(flat, axial(30), 0.5)),
        () -> assertEquals(Placement.OUTSIDE, MprGraphicStore.classify(null, axial(10), 0.5)));
  }

  @Test
  void another_frame_of_reference_is_never_placed() {
    SpatialAnchor other =
        new SpatialAnchor("9.9", new Point3(0, 0, 1), List.of(new Point3(0, 0, 10))); // NON-NLS
    assertEquals(Placement.OUTSIDE, MprGraphicStore.classify(other, axial(10), 0.5));
  }

  @Test
  void crossing_points_are_interpolated_on_the_plane() {
    List<Point3> pts = List.of(new Point3(0, 0, 0), new Point3(10, 0, 20), new Point3(10, 10, 0));
    Point3 normal = new Point3(0, 0, 1);
    Point3 origin = new Point3(0, 0, 10);
    List<Point3> open = MprGraphicStore.planeIntersections(pts, false, normal, origin);
    List<Point3> closed = MprGraphicStore.planeIntersections(pts, true, normal, origin);
    assertAll(
        () -> assertEquals(List.of(new Point3(5, 0, 10), new Point3(10, 5, 10)), open),
        () -> assertEquals(2, closed.size()),
        () -> assertTrue(open.stream().allMatch(p -> p.z == 10)));
  }

  @Test
  void store_keeps_anchored_complete_graphics_and_reports_anchor_changes() throws Exception {
    MprGraphicStore store = new MprGraphicStore();
    LineGraphic line = new LineGraphic();
    line.setLayerType(LayerType.MEASURE);
    line.buildGraphic(List.of(new Point2D.Double(0, 0), new Point2D.Double(3, 4)));
    assertFalse(store.adopt(line));
    line.anchor(axial(10));
    assertAll(
        () -> assertTrue(store.adopt(line)),
        () -> assertFalse(store.adopt(line)),
        () -> assertEquals(1, store.graphics().size()));
    line.anchor(axial(12));
    assertTrue(store.adopt(line));
    line.fireRemoveAction();
    assertTrue(store.graphics().isEmpty());
  }
}
