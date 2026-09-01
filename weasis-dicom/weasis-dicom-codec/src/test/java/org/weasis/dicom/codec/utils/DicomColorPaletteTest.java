/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.utils;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.nio.file.Path;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.opencv.op.lut.colormap.ColorMap;
import org.weasis.opencv.op.lut.colormap.ColorMapCompiler;
import org.weasis.opencv.op.lut.colormap.ColorMapDomain;
import org.weasis.opencv.op.lut.colormap.Interpolation;

@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomColorPaletteTest {

  private static final ColorMap HOT =
      ColorMap.builder("Hot iron (test)")
          .modalities("PT")
          .domain(ColorMapDomain.absolute("SUVbw", 0, 10))
          .bits(8)
          .stop(0, Color.BLACK, 0f)
          .stop(5, Color.RED)
          .stop(10, Color.YELLOW, 1f)
          .metadata(ColorMap.META_DICOM_CREATOR, "Tester")
          .build();

  @Test
  void attributes_describe_a_16_bit_color_palette_object() {
    Attributes ds = DicomColorPalette.toAttributes(HOT);

    assertAll(
        () -> assertEquals(UID.ColorPaletteStorage, ds.getString(Tag.SOPClassUID)),
        () -> assertTrue(ds.getString(Tag.SOPInstanceUID).startsWith("2.25.")),
        () -> assertEquals("HOT IRON _TEST_", ds.getString(Tag.ContentLabel)),
        () -> assertEquals("Hot iron (test)", ds.getString(Tag.ContentDescription)),
        () -> assertEquals("Tester", ds.getString(Tag.ContentCreatorName)),
        () ->
            assertArrayEquals(
                new int[] {256, 0, 16}, ds.getInts(Tag.RedPaletteColorLookupTableDescriptor)),
        () -> assertEquals(512, ds.getBytes(Tag.RedPaletteColorLookupTableData).length),
        () -> assertTrue(DicomColorPalette.isColorPalette(ds)));
  }

  @Test
  void colors_survive_the_round_trip_as_a_sampled_map() {
    Attributes ds = DicomColorPalette.toAttributes(HOT);
    ColorMap back = DicomColorPalette.fromAttributes(ds).orElseThrow();

    byte[][] expected = ColorMapCompiler.toBgr(HOT, 256);
    byte[][] actual = ColorMapCompiler.toBgr(back, 256);
    assertAll(
        () -> assertEquals("HOT IRON _TEST_", back.name()),
        () -> assertEquals(Interpolation.SAMPLED, back.interpolation()),
        () -> assertEquals(ColorMapDomain.RELATIVE, back.domain(), "physical domain is dropped"),
        () -> assertFalse(back.hasAlpha(), "alpha is dropped"),
        () ->
            assertEquals(
                ds.getString(Tag.SOPInstanceUID), back.metadata().get(ColorMap.META_DICOM_UID)),
        () -> assertEquals("Tester", back.metadata().get(ColorMap.META_DICOM_CREATOR)));
    for (int band = 0; band < 3; band++) {
      assertArrayEquals(expected[band], actual[band], "band " + band);
    }
  }

  @Test
  void a_re_exported_palette_keeps_its_uid_and_a_wide_map_gets_4096_entries() {
    ColorMap wide = HOT.toBuilder().bits(12).metadata(ColorMap.META_DICOM_UID, "1.2.3.4").build();
    Attributes ds = DicomColorPalette.toAttributes(wide);

    assertEquals("1.2.3.4", ds.getString(Tag.SOPInstanceUID));
    assertArrayEquals(
        new int[] {4096, 0, 16}, ds.getInts(Tag.BluePaletteColorLookupTableDescriptor));
    assertEquals(4096, DicomColorPalette.fromAttributes(ds).orElseThrow().stops().size());
  }

  @Test
  void file_round_trip(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("palette.dcm");
    DicomColorPalette.write(file, HOT);

    ColorMap read = DicomColorPalette.read(file).orElseThrow();
    assertEquals(256, read.stops().size());
    assertEquals(Color.YELLOW, read.stops().getLast().color());
    assertEquals(List.of(read), DicomColorPalette.FORMAT.read(file));
  }

  @Test
  void datasets_without_a_palette_are_ignored() {
    assertTrue(DicomColorPalette.fromAttributes(new Attributes()).isEmpty());
    assertFalse(DicomColorPalette.isColorPalette(new Attributes()));
  }
}
