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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Point;
import java.awt.geom.Rectangle2D;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.TagD;

/** SCOORD items of tiled whole-slide images: Pixel Origin Interpretation VOLUME. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SRGraphicTest {

  private static final int TILE = 256;

  /** A tiled image of 4 x 3 tiles of 256 pixels; with per-frame positions unless tiledFull. */
  private static Attributes tiledImage(boolean tiledFull, int frames) {
    Attributes dcm = new Attributes();
    dcm.setInt(Tag.Columns, VR.US, TILE);
    dcm.setInt(Tag.Rows, VR.US, TILE);
    dcm.setInt(Tag.TotalPixelMatrixColumns, VR.UL, 4 * TILE);
    dcm.setInt(Tag.TotalPixelMatrixRows, VR.UL, 3 * TILE);
    dcm.setInt(Tag.NumberOfFrames, VR.IS, frames);
    if (tiledFull) {
      dcm.setString(Tag.DimensionOrganizationType, VR.CS, "TILED_FULL");
    } else {
      Sequence seq = dcm.newSequence(Tag.PerFrameFunctionalGroupsSequence, frames);
      for (int f = 0; f < frames; f++) {
        Attributes group = new Attributes();
        Attributes pos = new Attributes();
        // Positions are 1-based; tiles left to right, top to bottom
        pos.setInt(Tag.ColumnPositionInTotalImagePixelMatrix, VR.SL, (f % 4) * TILE + 1);
        pos.setInt(Tag.RowPositionInTotalImagePixelMatrix, VR.SL, (f / 4) * TILE + 1);
        group.newSequence(Tag.PlanePositionSlideSequence, 1).add(pos);
        seq.add(group);
      }
    }
    return dcm;
  }

  private static DicomImageElement frame(Attributes dcm, int frameIndex) {
    DicomMediaIO io = mock(DicomMediaIO.class);
    when(io.getDicomObject()).thenReturn(dcm);
    when(io.getMediaElementNumber()).thenReturn(dcm.getInt(Tag.NumberOfFrames, 1));
    DicomImageElement img = mock(DicomImageElement.class);
    when(img.getMediaReader()).thenReturn(io);
    when(img.getKey()).thenReturn(frameIndex);
    when(img.getTagValue(TagD.get(Tag.Columns))).thenReturn(TILE);
    when(img.getTagValue(TagD.get(Tag.Rows))).thenReturn(TILE);
    return img;
  }

  private static Attributes scoord(String origin, String type, float... data) {
    Attributes a = new Attributes();
    a.setString(Tag.ValueType, VR.CS, "SCOORD");
    a.setString(Tag.GraphicType, VR.CS, type);
    a.setFloat(Tag.GraphicData, VR.FL, data);
    if (origin != null) {
      a.setString(Tag.PixelOriginInterpretation, VR.CS, origin);
    }
    return a;
  }

  @Test
  void tile_origin_comes_from_the_plane_position_or_from_the_tiled_full_layout() {
    assertEquals(new Point(2 * TILE, TILE), SRGraphic.tileOrigin(frame(tiledImage(false, 12), 6)));
    assertEquals(new Point(2 * TILE, TILE), SRGraphic.tileOrigin(frame(tiledImage(true, 12), 6)));
    assertEquals(new Point(0, 0), SRGraphic.tileOrigin(frame(tiledImage(true, 12), 0)));

    Attributes plain = new Attributes();
    plain.setInt(Tag.Columns, VR.US, 512);
    assertNull(SRGraphic.tileOrigin(frame(plain, 0)), "not a tiled image");
  }

  @Test
  void volume_coordinates_are_shifted_to_the_frame_that_contains_them() {
    // Rectangle at total-matrix columns 550..600, rows 300..330: inside tile (2,1) = frame 6
    Attributes item =
        scoord("VOLUME", "POLYLINE", 550, 300, 600, 300, 600, 330, 550, 330, 550, 300);
    SRGraphic g = new SRGraphic("1.1", item, false);

    Graphic onTile = g.build(frame(tiledImage(false, 12), 6), false);
    assertNotNull(onTile);
    Rectangle2D b = onTile.getShape().getBounds2D();
    assertEquals(550 - 2 * TILE, b.getX(), 1e-6);
    assertEquals(300 - TILE, b.getY(), 1e-6);
    assertEquals(50, b.getWidth(), 1e-6);

    assertNull(g.build(frame(tiledImage(false, 12), 0), false), "the first tile does not overlap");
    assertNull(g.build(frame(tiledImage(true, 12), 11), false), "the last tile does not overlap");
  }

  @Test
  void a_region_spanning_two_tiles_is_drawn_on_both() {
    // Line from column 500 to 560 on row 100: crosses the border between tiles 1 and 2 of row 0
    Attributes item = scoord("VOLUME", "POLYLINE", 500, 100, 560, 100);
    SRGraphic g = new SRGraphic("1.1", item, false);

    assertNotNull(g.build(frame(tiledImage(true, 12), 1), false));
    assertNotNull(g.build(frame(tiledImage(true, 12), 2), false));
    assertNull(g.build(frame(tiledImage(true, 12), 3), false));
  }

  @Test
  void frame_coordinates_are_used_as_they_are() {
    Attributes item = scoord("FRAME", "POINT", 10, 10);
    Graphic explicit =
        new SRGraphic("1.1", item, false).build(frame(tiledImage(false, 12), 6), false);
    Graphic implicit =
        new SRGraphic("1.2", scoord(null, "POINT", 10, 10), false)
            .build(frame(tiledImage(false, 12), 6), false);
    assertNotNull(explicit);
    assertNotNull(implicit);
    assertEquals(10, explicit.getShape().getBounds2D().getCenterX(), 1e-6);
    assertEquals(10, implicit.getShape().getBounds2D().getCenterX(), 1e-6);
  }

  @Test
  void volume_origin_on_a_plain_image_falls_back_to_frame_coordinates() {
    Attributes plain = new Attributes();
    plain.setInt(Tag.Columns, VR.US, 512);
    Graphic g =
        new SRGraphic("1.1", scoord("VOLUME", "POINT", 100, 120), false)
            .build(frame(plain, 0), false);
    assertNotNull(g);
    assertEquals(100, g.getShape().getBounds2D().getCenterX(), 1e-6);
  }

  @Test
  void highlighted_graphic_shows_its_label() {
    Attributes plain = new Attributes();
    plain.setInt(Tag.Columns, VR.US, 512);
    SRGraphic g =
        new SRGraphic(
            "1.1",
            scoord(null, "POINT", 100, 120),
            false,
            SRGraphic.RenderingIntent.PRESENTATION_REQUIRED,
            "Lesion 1");

    Graphic normal = g.build(frame(plain, 0), false);
    Graphic highlighted = g.build(frame(plain, 0), true);

    assertNull(normal.getGraphicLabel());
    assertNotNull(highlighted.getGraphicLabel());
    assertEquals("Lesion 1", highlighted.getGraphicLabel().getLabels()[0]);
    assertTrue(highlighted.getLabelVisible());
  }

  @Test
  void rendering_intent_defaults_to_presentation_required() {
    SRGraphic g = new SRGraphic("1.1", scoord(null, "POINT", 1, 1), false);
    assertEquals(SRGraphic.RenderingIntent.PRESENTATION_REQUIRED, g.intent());
    assertTrue(g.isPresentationRequired());
    assertEquals(
        SRGraphic.RenderingIntent.PRESENTATION_REQUIRED,
        new SRGraphic("1.1", scoord(null, "POINT", 1, 1), false, null).intent());
  }
}
