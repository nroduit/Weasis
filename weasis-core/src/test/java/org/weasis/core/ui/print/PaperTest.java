/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.print;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.print.attribute.standard.MediaSizeName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class PaperTest {

  @Test
  void catalogue_sizes_are_portrait_in_millimetres() {
    assertAll(
        () -> assertEquals(210, Paper.A4.widthMm()),
        () -> assertEquals(297, Paper.A4.heightMm()),
        () -> assertEquals(14 * 25.4, Paper.IN_14X17.widthMm(), 1e-9),
        () -> assertEquals(17 * 25.4, Paper.IN_14X17.heightMm(), 1e-9),
        () -> assertTrue(Paper.CATALOGUE.stream().allMatch(p -> p.widthMm() <= p.heightMm())));
  }

  @Test
  void film_size_ids_follow_dicom_ps3_3() {
    assertAll(
        () -> assertEquals("8INX10IN", Paper.IN_8X10.filmSizeId()),
        () -> assertEquals("8_5INX11IN", Paper.LETTER.filmSizeId()),
        () -> assertEquals("14INX17IN", Paper.IN_14X17.filmSizeId()),
        () -> assertEquals("24CMX30CM", Paper.CM_24X30.filmSizeId()),
        () -> assertEquals("A4", Paper.A4.filmSizeId()),
        () -> assertNull(Paper.A5.filmSizeId()),
        () -> assertFalse(Paper.LEGAL.hasFilmSizeId()));
  }

  @Test
  void lookup_by_size_ignores_orientation_and_half_millimetre_rounding() {
    assertAll(
        () -> assertSame(Paper.A4, Paper.fromMm(297, 210)),
        () -> assertSame(Paper.A4, Paper.fromMm(209.9, 297.3)),
        () -> assertSame(Paper.LETTER, Paper.fromMm(216, 279)),
        () -> assertSame(Paper.A4, Paper.fromPoints(595, 842)),
        () -> assertSame(Paper.LETTER, Paper.fromPoints(612, 792)));
  }

  @Test
  void unknown_size_gives_a_custom_portrait_paper() {
    Paper custom = Paper.fromMm(300, 100);
    assertAll(
        () -> assertEquals(100, custom.widthMm()),
        () -> assertEquals(300, custom.heightMm()),
        () -> assertNull(custom.filmSizeId()),
        () -> assertEquals("100x300mm", custom.id()));
  }

  @Test
  void lookup_by_film_size_id_and_media() {
    assertAll(
        () -> assertSame(Paper.IN_11X14, Paper.fromFilmSizeId("11INX14IN").orElseThrow()),
        () -> assertTrue(Paper.fromFilmSizeId("7INX7IN").isEmpty()),
        () -> assertTrue(Paper.fromFilmSizeId(null).isEmpty()),
        () -> assertSame(Paper.A3, Paper.fromMedia(MediaSizeName.ISO_A3).orElseThrow()),
        () -> assertTrue(Paper.fromMedia(MediaSizeName.ISO_B4).isEmpty()));
  }

  @Test
  void nearest_catalogue_paper() {
    assertAll(
        () -> assertSame(Paper.A4, Paper.nearest(200, 290)),
        () -> assertSame(Paper.IN_14X17, Paper.nearest(350, 430)));
  }

  @Test
  void unit_conversions() {
    assertAll(
        () -> assertEquals(25.4, Paper.pointsToMm(72), 1e-9),
        () -> assertEquals(72, Paper.mmToPoints(25.4), 1e-9),
        () ->
            assertThrows(IllegalArgumentException.class, () -> new Paper("x", 0, 10, null, null)));
  }
}
