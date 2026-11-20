/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.print;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.awt.Color;
import java.awt.image.BufferedImage;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.print.PageSpec;
import org.weasis.core.ui.print.PageSpec.Orientation;
import org.weasis.core.ui.print.Paper;

@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomPrintTest {

  private static PageSpec film(Paper paper, Orientation orientation, Color background) {
    return new PageSpec(paper, orientation, PageSpec.Margins.uniform(2), 2, background, null);
  }

  @Test
  void what_is_unset_or_does_not_apply_is_left_to_the_printer() {
    DicomPrintOptions options = new DicomPrintOptions();
    options.setMagnificationType("BILINEAR");
    options.setSmoothingType("SHARP");
    options.setImageDisplayFormat("STANDARD\\2,3");
    Attributes box =
        DicomPrint.filmBoxAttributes(options, film(Paper.A4, Orientation.PORTRAIT, Color.BLACK));
    assertAll(
        () -> assertFalse(box.contains(Tag.SmoothingType), "cubic magnification only"),
        () -> assertFalse(box.contains(Tag.MinDensity)),
        () -> assertFalse(box.contains(Tag.MaxDensity)),
        () -> assertFalse(box.contains(Tag.RequestedResolutionID)),
        () -> assertFalse(box.contains(Tag.ConfigurationInformation)),
        () ->
            assertEquals(
                "STANDARD\\1,1",
                box.getString(Tag.ImageDisplayFormat),
                "the page is composed by Weasis: one image box"));
  }

  @Test
  void the_film_box_takes_size_orientation_and_empty_density_from_the_page() {
    DicomPrintOptions options = new DicomPrintOptions();
    options.setMagnificationType("CUBIC");
    options.setSmoothingType("SHARP");
    options.setMaxDensity(300);
    options.setRequestedResolution("HIGH");
    options.setConfigurationInformation("LUT=m,1");
    options.setTrim("YES");
    options.setBorderDensity("BLACK");
    Attributes box =
        DicomPrint.filmBoxAttributes(
            options, film(Paper.IN_14X17, Orientation.LANDSCAPE, Color.WHITE));
    assertAll(
        () -> assertEquals("14INX17IN", box.getString(Tag.FilmSizeID)),
        () -> assertEquals("LANDSCAPE", box.getString(Tag.FilmOrientation)),
        () -> assertEquals("STANDARD\\1,1", box.getString(Tag.ImageDisplayFormat)),
        () -> assertEquals("CUBIC", box.getString(Tag.MagnificationType)),
        () -> assertEquals("SHARP", box.getString(Tag.SmoothingType)),
        () -> assertEquals("YES", box.getString(Tag.Trim)),
        () -> assertEquals("BLACK", box.getString(Tag.BorderDensity)),
        () -> assertEquals("WHITE", box.getString(Tag.EmptyImageDensity)),
        () -> assertEquals(-1, box.getInt(Tag.MinDensity, -1), "unset: left to the printer"),
        () -> assertEquals(300, box.getInt(Tag.MaxDensity, -1)),
        () -> assertEquals("HIGH", box.getString(Tag.RequestedResolutionID)),
        () -> assertEquals("LUT=m,1", box.getString(Tag.ConfigurationInformation)));
    Attributes black =
        DicomPrint.filmBoxAttributes(options, film(Paper.A4, Orientation.PORTRAIT, Color.BLACK));
    assertAll(
        () -> assertEquals("A4", black.getString(Tag.FilmSizeID)),
        () -> assertEquals("PORTRAIT", black.getString(Tag.FilmOrientation)),
        () -> assertEquals("BLACK", black.getString(Tag.EmptyImageDensity)));
  }

  @Test
  void a_paper_without_film_id_maps_to_the_closest_film() {
    assertAll(
        () -> assertEquals("10INX14IN", DicomPrint.filmSizeIdOf(Paper.LEGAL)),
        () -> assertEquals("A4", DicomPrint.filmSizeIdOf(Paper.custom(205, 300))),
        () -> assertEquals("8INX10IN", DicomPrint.filmSizeIdOf(Paper.IN_8X10)));
  }

  @Test
  void the_film_session_carries_copies_priority_medium_and_destination() {
    DicomPrintOptions options = new DicomPrintOptions();
    options.setPriority("HIGH");
    options.setMediumType("PAPER");
    options.setFilmDestination("PROCESSOR");
    Attributes session = DicomPrint.filmSessionAttributes(options, 3);
    assertAll(
        () -> assertEquals(3, session.getInt(Tag.NumberOfCopies, -1)),
        () -> assertEquals("HIGH", session.getString(Tag.PrintPriority)),
        () -> assertEquals("PAPER", session.getString(Tag.MediumType)),
        () -> assertEquals("PROCESSOR", session.getString(Tag.FilmDestination)),
        () ->
            assertEquals(
                1, DicomPrint.filmSessionAttributes(options, 0).getInt(Tag.NumberOfCopies, -1)));
  }

  @Test
  void a_colour_raster_is_stored_as_interleaved_rgb_whatever_its_byte_order() {
    BufferedImage bgr = new BufferedImage(2, 1, BufferedImage.TYPE_3BYTE_BGR);
    bgr.setRGB(0, 0, new Color(10, 20, 30).getRGB());
    bgr.setRGB(1, 0, new Color(200, 100, 0).getRGB());
    Attributes fromBgr = new Attributes();
    DicomPrint.storeRasterInDicom(bgr, fromBgr, true);

    BufferedImage rgb = DicomPrint.createRGBBufferedImage(2, 1);
    rgb.setRGB(0, 0, new Color(10, 20, 30).getRGB());
    rgb.setRGB(1, 0, new Color(200, 100, 0).getRGB());
    Attributes fromRgb = new Attributes();
    DicomPrint.storeRasterInDicom(rgb, fromRgb, true);

    byte[] expected = {10, 20, 30, (byte) 200, 100, 0};
    assertAll(
        () -> assertArrayEquals(expected, fromBgr.getBytes(Tag.PixelData)),
        () -> assertArrayEquals(expected, fromRgb.getBytes(Tag.PixelData)),
        () -> assertEquals("RGB", fromRgb.getString(Tag.PhotometricInterpretation)),
        () -> assertEquals(3, fromRgb.getInt(Tag.SamplesPerPixel, -1)),
        () -> assertEquals(0, fromRgb.getInt(Tag.PlanarConfiguration, -1)),
        () -> assertEquals(2, fromRgb.getInt(Tag.Columns, -1)),
        () -> assertEquals(1, fromRgb.getInt(Tag.Rows, -1)));
  }

  @Test
  void a_grey_raster_is_stored_as_monochrome2() {
    BufferedImage gray = DicomPrint.createGrayBufferedImage(2, 1);
    gray.getRaster().setSample(0, 0, 0, 7);
    gray.getRaster().setSample(1, 0, 0, 250);
    Attributes attrs = new Attributes();
    DicomPrint.storeRasterInDicom(gray, attrs, false);
    assertAll(
        () -> assertArrayEquals(new byte[] {7, (byte) 250}, attrs.getBytes(Tag.PixelData)),
        () -> assertEquals("MONOCHROME2", attrs.getString(Tag.PhotometricInterpretation)),
        () -> assertEquals(1, attrs.getInt(Tag.SamplesPerPixel, -1)),
        () -> assertEquals(8, attrs.getInt(Tag.BitsStored, -1)));
  }
}
