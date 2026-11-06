/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.ann;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.weasis.dicom.wsi.model.SyntheticSlides.tiledFull;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.wsi.ann.BulkAnnotations.GraphicType;
import org.weasis.dicom.wsi.ann.BulkAnnotations.Group;
import org.weasis.dicom.wsi.model.Slide;
import org.weasis.dicom.wsi.model.SlideBuilder;
import org.weasis.dicom.wsi.view.AnnotationOverlay;

@DisplayNameGeneration(ReplaceUnderscores.class)
class BulkAnnotationsTest {

  /** A two-level slide whose level 1 (downsample 2) is the instance "L1". */
  private static Slide slide() {
    return SlideBuilder.build(
            List.of(tiledFull("L0", 1024, 1024, 256), tiledFull("L1", 512, 512, 256))) // NON-NLS
        .getFirst();
  }

  /** An object annotating "L1": two triangles and three points, in level 1 pixels. */
  private static Attributes annotations(String referenced) {
    Attributes dcm = new Attributes();
    dcm.setString(Tag.AnnotationCoordinateType, VR.CS, "2D"); // NON-NLS
    dcm.setString(Tag.PixelOriginInterpretation, VR.CS, "VOLUME"); // NON-NLS
    Attributes ref = new Attributes();
    ref.setString(Tag.ReferencedSOPInstanceUID, VR.UI, referenced);
    dcm.newSequence(Tag.ReferencedImageSequence, 1).add(ref);
    Sequence groups = dcm.newSequence(Tag.AnnotationGroupSequence, 2);
    Attributes polygons = new Attributes();
    polygons.setInt(Tag.AnnotationGroupNumber, VR.US, 1);
    polygons.setString(Tag.AnnotationGroupUID, VR.UI, "1.2.3.4"); // NON-NLS
    polygons.setString(Tag.AnnotationGroupLabel, VR.LO, "Nuclei"); // NON-NLS
    polygons.setString(Tag.GraphicType, VR.CS, "POLYGON"); // NON-NLS
    polygons.setInt(Tag.NumberOfAnnotations, VR.UL, 2);
    polygons.setFloat(
        Tag.PointCoordinatesData, VR.OF, 10, 10, 20, 10, 10, 20, 100, 100, 120, 100, 100, 130);
    polygons.setInt(Tag.LongPrimitivePointIndexList, VR.OL, 1, 7);
    polygons.setInt(Tag.RecommendedDisplayCIELabValue, VR.US, 65535, 32896, 32896);
    groups.add(polygons);
    Attributes points = new Attributes();
    points.setInt(Tag.AnnotationGroupNumber, VR.US, 2);
    points.setString(Tag.AnnotationGroupLabel, VR.LO, "Centres"); // NON-NLS
    points.setString(Tag.GraphicType, VR.CS, "POINT"); // NON-NLS
    points.setInt(Tag.NumberOfAnnotations, VR.UL, 3);
    points.setFloat(Tag.PointCoordinatesData, VR.OF, 1, 1, 200, 200, 250, 5);
    groups.add(points);
    return dcm;
  }

  @Test
  void groups_are_read_in_the_pixels_of_the_referenced_level_scaled_to_the_base() {
    BulkAnnotations read =
        BulkAnnotations.read(annotations("L1"), slide()).orElseThrow(); // NON-NLS
    Group nuclei = read.groups().getFirst();
    Group centres = read.groups().get(1);
    assertAll(
        () -> assertEquals(2, read.groups().size()),
        () -> assertEquals("Nuclei", nuclei.label()), // NON-NLS
        () -> assertEquals(GraphicType.POLYGON, nuclei.type()),
        () -> assertEquals(2, nuclei.count()),
        () -> assertArrayEquals(new int[] {0, 3, 6}, nuclei.starts()),
        () -> assertEquals(2.0, nuclei.scaleX(), 1e-9, "level 1 is the half of the base"),
        () -> assertEquals(Color.WHITE, nuclei.color(), "L* 100, a* b* 0"),
        () -> assertEquals(3, centres.count()),
        () -> assertEquals(250.0, centres.points().x(2), 1e-6),
        () -> assertTrue(read.referencedInstanceUIDs().contains("L1"))); // NON-NLS
  }

  @Test
  void an_object_of_another_slide_or_in_3d_coordinates_is_not_read() {
    Attributes threeD = annotations("L1"); // NON-NLS
    threeD.setString(Tag.AnnotationCoordinateType, VR.CS, "3D"); // NON-NLS
    assertAll(
        () -> assertTrue(BulkAnnotations.read(annotations("X"), slide()).isEmpty()), // NON-NLS
        () -> assertTrue(BulkAnnotations.read(threeD, slide()).isEmpty()));
  }

  @Test
  void the_index_finds_the_items_of_a_viewport_up_to_the_cap() {
    BulkAnnotations read =
        BulkAnnotations.read(annotations("L1"), slide()).orElseThrow(); // NON-NLS
    AnnotationIndex nuclei = AnnotationIndex.build(read.groups().getFirst(), 1024, 1024);
    AnnotationIndex centres = AnnotationIndex.build(read.groups().get(1), 1024, 1024);
    float[] bounds = nuclei.bounds(1, new float[4]);
    assertAll(
        () -> assertArrayEquals(new float[] {200, 200, 240, 260}, bounds, "base pixels"),
        () ->
            assertArrayEquals(
                new int[] {0}, nuclei.query(new Rectangle2D.Double(0, 0, 50, 50), 10)),
        () -> assertEquals(2, nuclei.query(new Rectangle2D.Double(0, 0, 1024, 1024), 10).length),
        () ->
            assertEquals(
                1, nuclei.query(new Rectangle2D.Double(0, 0, 1024, 1024), 1).length, "cap"),
        () -> assertEquals(0, nuclei.query(new Rectangle2D.Double(500, 500, 10, 10), 10).length),
        () -> assertEquals(3, centres.query(new Rectangle2D.Double(0, 0, 1024, 1024), 10).length));
  }

  @Test
  void the_overlay_draws_the_items_in_the_colour_of_their_group() {
    BulkAnnotations read =
        BulkAnnotations.read(annotations("L1"), slide()).orElseThrow(); // NON-NLS
    AnnotationIndex nuclei = AnnotationIndex.build(read.groups().getFirst(), 1024, 1024);
    BufferedImage image = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    int painted =
        AnnotationOverlay.paint(
            g, new AffineTransform(), new Rectangle2D.Double(0, 0, 300, 300), List.of(nuclei), 100);
    g.dispose();
    // The second triangle spans (200,200) - (240,260) in base pixels: its left edge is white
    int left = image.getRGB(200, 230) & 0xFFFFFF;
    assertAll(
        () -> assertEquals(2, painted),
        () -> assertTrue(left > 0x808080, "drawn: %06x".formatted(left)),
        () -> assertEquals(0, image.getRGB(150, 150) & 0xFFFFFF, "nothing between the triangles"));
  }

  @Test
  void a_cielab_value_becomes_an_srgb_colour() {
    assertAll(
        () ->
            assertEquals(
                Color.WHITE, BulkAnnotations.color(new int[] {65535, 32896, 32896}).orElseThrow()),
        () ->
            assertEquals(
                Color.BLACK, BulkAnnotations.color(new int[] {0, 32896, 32896}).orElseThrow()),
        () -> assertTrue(BulkAnnotations.color(new int[] {65535}).isEmpty()));
  }
}
