/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.sr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.joml.Vector3d;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;

@DisplayNameGeneration(ReplaceUnderscores.class)
class SRScoordProjectorTest {

  private static final String FOR = "1.2.3.4";

  /** Axial slice at z = 50 mm, 1 mm pixels, 2 mm slice spacing, top-left pixel at (-100,-100). */
  private static GeometryOfSlice axialAtZ50() {
    return new GeometryOfSlice(
        new Vector3d(1, 0, 0),
        new Vector3d(0, 1, 0),
        new Vector3d(-100, -100, 50),
        new Vector3d(1, 1, 2),
        2.0,
        new Vector3d(256, 256, 20));
  }

  private static Attributes scoord3d(String type, float... xyz) {
    Attributes a = new Attributes();
    a.setString(Tag.ValueType, VR.CS, "SCOORD3D");
    a.setString(Tag.GraphicType, VR.CS, type);
    a.setFloat(Tag.GraphicData, VR.FL, xyz);
    a.setString(Tag.ReferencedFrameOfReferenceUID, VR.UI, FOR);
    return a;
  }

  private static Graphic project(Attributes item, String imageFor) {
    return SRScoordProjector.project(item, axialAtZ50(), imageFor, Color.MAGENTA, 1f);
  }

  private static void assertBounds(Graphic g, double x, double y, double w, double h) {
    Rectangle2D b = g.getShape().getBounds2D();
    assertEquals(x, b.getX(), 1e-3);
    assertEquals(y, b.getY(), 1e-3);
    assertEquals(w, b.getWidth(), 1e-3);
    assertEquals(h, b.getHeight(), 1e-3);
  }

  @Test
  void planar_polygon_in_the_slice_is_drawn_in_pixel_coordinates() {
    Attributes item = scoord3d("POLYGON", -90, -80, 50, -50, -80, 50, -50, -60, 50, -90, -60, 50);

    Graphic g = project(item, FOR);

    assertNotNull(g);
    assertBounds(g, 10, 20, 40, 20);
    assertEquals(1f, g.getLineThickness());
  }

  @Test
  void polygon_in_another_slice_is_not_drawn() {
    Attributes item = scoord3d("POLYGON", -90, -80, 57, -50, -80, 57, -50, -60, 57, -90, -60, 57);

    assertNull(project(item, FOR));
  }

  @Test
  void points_within_half_spacing_belong_to_the_slice() {
    // 2 mm spacing: |dz| <= 1 mm is in, 1.5 mm is out
    assertNotNull(project(scoord3d("POINT", 0, 0, 50.9f), FOR));
    assertNull(project(scoord3d("POINT", 0, 0, 51.5f), FOR));
  }

  @Test
  void polyline_crossing_the_slice_contributes_the_intersection_point() {
    Attributes item = scoord3d("POLYLINE", -100, -100, 40, -80, -100, 60);

    Graphic g = project(item, FOR);

    assertNotNull(g);
    // The segment crosses z = 50 at its middle: x = -90 -> pixel 10, y = -100 -> pixel 0
    Rectangle2D b = g.getShape().getBounds2D();
    assertEquals(10, b.getCenterX(), 1e-3);
    assertEquals(0, b.getCenterY(), 1e-3);
  }

  @Test
  void ellipse_in_the_slice_uses_its_axes() {
    // Major axis 40 mm along x, minor axis 20 mm along y, centered on (-60,-60)
    Attributes item = scoord3d("ELLIPSE", -80, -60, 50, -40, -60, 50, -60, -70, 50, -60, -50, 50);

    Graphic g = project(item, FOR);

    assertNotNull(g);
    assertBounds(g, 20, 30, 40, 20);
  }

  private static Attributes ellipsoid(float cx, float cy, float cz, float a, float b, float c) {
    return scoord3d(
        "ELLIPSOID",
        cx - a,
        cy,
        cz,
        cx + a,
        cy,
        cz,
        cx,
        cy - b,
        cz,
        cx,
        cy + b,
        cz,
        cx,
        cy,
        cz - c,
        cx,
        cy,
        cz + c);
  }

  @Test
  void ellipsoid_section_is_exact_on_every_slice_it_spans() {
    // Semi-axes 20, 10 and 5 mm, centered on (-60, -60, 50); slices are 2 mm apart at z = 50
    Attributes item = ellipsoid(-60, -60, 50, 20, 10, 5);

    Graphic center = project(item, FOR);
    assertNotNull(center);
    assertBounds(center, 20, 30, 40, 20);

    // 3 mm above the center the section shrinks by sqrt(1 - (3/5)^2) = 0.8
    GeometryOfSlice above =
        new GeometryOfSlice(
            new Vector3d(1, 0, 0),
            new Vector3d(0, 1, 0),
            new Vector3d(-100, -100, 53),
            new Vector3d(1, 1, 2),
            2.0,
            new Vector3d(256, 256, 20));
    Graphic upper = SRScoordProjector.project(item, above, FOR, Color.MAGENTA, 1f);
    assertNotNull(upper);
    assertBounds(upper, 24, 32, 32, 16);

    GeometryOfSlice outside =
        new GeometryOfSlice(
            new Vector3d(1, 0, 0),
            new Vector3d(0, 1, 0),
            new Vector3d(-100, -100, 56),
            new Vector3d(1, 1, 2),
            2.0,
            new Vector3d(256, 256, 20));
    assertNull(SRScoordProjector.project(item, outside, FOR, Color.MAGENTA, 1f));
  }

  @Test
  void ellipsoid_section_follows_a_tilted_ellipsoid() {
    // Axes rotated 45 degrees in the xz plane: the axial section through the center is still an
    // ellipse whose x extent is the 45-degree cut of the first and third axes
    float s = (float) (10 / Math.sqrt(2));
    Attributes item =
        scoord3d(
            "ELLIPSOID",
            -60 - s,
            -60,
            50 - s,
            -60 + s,
            -60,
            50 + s,
            -60,
            -70,
            50,
            -60,
            -50,
            50,
            -60 + s,
            -60,
            50 - s,
            -60 - s,
            -60,
            50 + s);

    Graphic g = project(item, FOR);

    assertNotNull(g);
    Rectangle2D b = g.getShape().getBounds2D();
    assertEquals(40, b.getCenterX(), 1e-3);
    assertEquals(40, b.getCenterY(), 1e-3);
    assertEquals(
        20, b.getWidth(), 0.05, "section of two equal 10 mm axes is a 10 mm radius circle");
    assertEquals(20, b.getHeight(), 0.05);
  }

  @Test
  void a_different_frame_of_reference_is_ignored() {
    Attributes item = scoord3d("POINT", 0, 0, 50);

    assertNull(project(item, "9.9.9"));
    assertNotNull(project(item, null), "an image without Frame of Reference is not rejected");
  }

  @Test
  void highlight_thickness_is_applied() {
    Graphic g =
        SRScoordProjector.project(
            scoord3d("POINT", 0, 0, 50),
            axialAtZ50(),
            FOR,
            Color.MAGENTA,
            SRGraphic.HIGHLIGHT_THICKNESS);

    assertNotNull(g);
    assertEquals(SRGraphic.HIGHLIGHT_THICKNESS, g.getLineThickness());
  }
}
