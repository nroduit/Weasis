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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.image.lut.ColorMapJson;
import org.weasis.core.api.image.lut.ColorMapRegistry;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.op.lut.colormap.ColorMap;
import org.weasis.opencv.op.lut.colormap.ColorMapCompiler;
import org.weasis.opencv.op.lut.colormap.ColorMapDomain;
import org.weasis.opencv.op.lut.colormap.Interpolation;
import org.weasis.opencv.op.lut.colormap.Lighting;
import org.weasis.opencv.op.lut.colormap.OutsideColors;
import org.weasis.opencv.op.lut.colormap.Rgba;

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

  // The palette as another viewer leaves it: the private JSON element removed
  private static Attributes withoutJson(ColorMap map) {
    Attributes ds = DicomColorPalette.toAttributes(map);
    ds.removePrivateAttributes(DicomColorPalette.PRIVATE_CREATOR, 0x0077);
    return ds;
  }

  @Test
  void attributes_describe_a_conformant_color_palette_object() {
    Attributes ds = DicomColorPalette.toAttributes(HOT);

    assertAll(
        () -> assertEquals(UID.ColorPaletteStorage, ds.getString(Tag.SOPClassUID)),
        () -> assertTrue(ds.getString(Tag.SOPInstanceUID).startsWith("2.25.")),
        () -> assertEquals("HOT IRON _TEST_", ds.getString(Tag.ContentLabel)),
        () -> assertEquals("Hot iron (test)", ds.getString(Tag.ContentDescription)),
        () -> assertEquals("Tester", ds.getString(Tag.ContentCreatorName)),
        () -> assertEquals(1, ds.getInt(Tag.InstanceNumber, 0), "Type 1"),
        () ->
            assertArrayEquals(
                new int[] {256, 0, 8},
                ds.getInts(Tag.RedPaletteColorLookupTableDescriptor),
                "8 bits in the Color Palette IOD"),
        () -> assertEquals(256, ds.getBytes(Tag.RedPaletteColorLookupTableData).length),
        () -> assertArrayEquals(SrgbInputProfile.data(), ds.getBytes(Tag.ICCProfile)),
        () ->
            assertEquals(
                "scnr",
                new String(ds.getBytes(Tag.ICCProfile), 12, 4, StandardCharsets.US_ASCII),
                "Input Device class, PS3.3 C.11.15.1.1"),
        () -> assertEquals("SRGB", ds.getString(Tag.ColorSpace)),
        () -> assertFalse(ds.contains(Tag.SpecificCharacterSet), "ASCII text"),
        () -> assertTrue(DicomColorPalette.isColorPalette(ds)));
  }

  @Test
  void the_whole_map_survives_the_round_trip_through_the_private_json() {
    ColorMap full =
        HOT.toBuilder()
            .id("site.hot")
            .bits(12)
            .category("Test")
            .tags("pet")
            .outside(new OutsideColors(null, Rgba.of(Color.WHITE), Rgba.TRANSPARENT))
            .lighting(Lighting.DEFAULT)
            .metadata("source", "unit test")
            .build();
    Attributes ds = DicomColorPalette.toAttributes(full);
    ColorMap back = DicomColorPalette.fromAttributes(ds).orElseThrow();

    String uid = ds.getString(Tag.SOPInstanceUID);
    ColorMap expected =
        full.toBuilder()
            .metadata(ColorMap.META_DICOM_UID, uid)
            .metadata(ColorMap.META_DICOM_LABEL, "HOT IRON _TEST_")
            .metadata(ColorMap.META_DICOM_DESCRIPTION, "Hot iron (test)")
            .build();
    assertEquals(ColorMapJson.toJson(expected), ColorMapJson.toJson(back));
  }

  @Test
  void colors_survive_the_round_trip_as_a_sampled_map() {
    Attributes ds = withoutJson(HOT);
    ColorMap back = DicomColorPalette.fromAttributes(ds).orElseThrow();

    byte[][] expected = ColorMapCompiler.toBgr(HOT, 256);
    byte[][] actual = ColorMapCompiler.toBgr(back, 256);
    assertAll(
        () -> assertEquals("Hot iron (test)", back.name(), "the description the label came from"),
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
  void a_palette_changed_by_another_tool_ignores_its_stale_json() throws Exception {
    Attributes ds = DicomColorPalette.toAttributes(HOT);
    byte[] red = ds.getBytes(Tag.RedPaletteColorLookupTableData);
    red[0] = (byte) 200;
    ds.setBytes(Tag.RedPaletteColorLookupTableData, VR.OW, red);

    ColorMap back = DicomColorPalette.fromAttributes(ds).orElseThrow();
    assertEquals(Interpolation.SAMPLED, back.interpolation());
    assertEquals(new Color(200, 0, 0), back.stops().getFirst().color());
  }

  @Test
  void an_invalid_json_falls_back_to_the_palette() {
    Attributes ds = DicomColorPalette.toAttributes(HOT);
    ds.setString(
        DicomColorPalette.PRIVATE_CREATOR, DicomColorPalette.TAG_COLOR_MAP_JSON, VR.UT, "{");

    assertEquals(
        Interpolation.SAMPLED, DicomColorPalette.fromAttributes(ds).orElseThrow().interpolation());
  }

  @Test
  void the_label_names_a_palette_whose_description_is_not_its_source() {
    Attributes ds = withoutJson(HOT);
    ds.setString(Tag.ContentLabel, VR.CS, "HOT_IRON");
    ds.setString(Tag.ContentDescription, VR.LO, "Hot Iron palette");

    assertEquals("HOT_IRON", DicomColorPalette.fromAttributes(ds).orElseThrow().name());
  }

  @Test
  void the_alpha_palette_becomes_the_alpha_of_the_stops() {
    Attributes ds = withoutJson(HOT);
    byte[] alpha = new byte[256];
    for (int i = 0; i < alpha.length; i++) {
      alpha[i] = (byte) i;
    }
    ds.setInt(Tag.AlphaPaletteColorLookupTableDescriptor, VR.US, 256, 0, 8);
    ds.setBytes(Tag.AlphaPaletteColorLookupTableData, VR.OW, alpha);

    ColorMap back = DicomColorPalette.fromAttributes(ds).orElseThrow();
    assertAll(
        () -> assertTrue(back.hasAlpha()),
        () -> assertEquals(0f, back.stops().getFirst().alpha()),
        () -> assertEquals(1f, back.stops().getLast().alpha()),
        () -> assertEquals(Color.YELLOW, back.stops().getLast().color()));
  }

  @Test
  void an_opaque_alpha_palette_adds_nothing() {
    Attributes ds = withoutJson(HOT);
    byte[] alpha = new byte[256];
    java.util.Arrays.fill(alpha, (byte) 0xFF);
    ds.setInt(Tag.AlphaPaletteColorLookupTableDescriptor, VR.US, 256, 0, 8);
    ds.setBytes(Tag.AlphaPaletteColorLookupTableData, VR.OW, alpha);

    assertFalse(DicomColorPalette.fromAttributes(ds).orElseThrow().hasAlpha());
  }

  @Test
  void entries_start_at_the_first_mapped_value() {
    byte[] ramp = {0, (byte) 255};
    LookupTableCV lut =
        new LookupTableCV(new byte[][] {ramp, ramp, ramp}, new int[] {2, 2, 2}, true);
    ColorMap map = DicomColorPalette.fromLookupTable("offset", lut);

    assertAll(
        () -> assertEquals(2.0 / 3.0, map.stops().getFirst().position(), 1e-9),
        () -> assertEquals(1.0, map.stops().getLast().position(), 1e-9),
        () -> assertEquals(Color.BLACK, map.stops().getFirst().color()));
  }

  @Test
  void a_re_exported_palette_keeps_its_uid_and_a_wide_map_is_resampled_to_8_bits() {
    ColorMap wide = HOT.toBuilder().bits(12).metadata(ColorMap.META_DICOM_UID, "1.2.3.4").build();
    Attributes ds = DicomColorPalette.toAttributes(wide);

    assertEquals("1.2.3.4", ds.getString(Tag.SOPInstanceUID));
    assertArrayEquals(new int[] {256, 0, 8}, ds.getInts(Tag.BluePaletteColorLookupTableDescriptor));
    ds.removePrivateAttributes(DicomColorPalette.PRIVATE_CREATOR, 0x0077);
    assertEquals(256, DicomColorPalette.fromAttributes(ds).orElseThrow().stops().size());
  }

  @Test
  void a_well_known_palette_is_exported_with_its_registered_uid() {
    ColorMap hotIron =
        new ColorMapRegistry(null, null).findByDicomUid("1.2.840.10008.1.5.1").orElseThrow();
    Attributes ds = DicomColorPalette.toAttributes(hotIron);

    assertAll(
        () -> assertEquals("1.2.840.10008.1.5.1", ds.getString(Tag.SOPInstanceUID)),
        () -> assertEquals("HOT_IRON", ds.getString(Tag.ContentLabel)));
  }

  @Test
  void non_ascii_text_declares_utf_8() {
    ColorMap map = HOT.toBuilder().metadata(ColorMap.META_DICOM_CREATOR, "Rémi^Müller").build();
    Attributes ds = DicomColorPalette.toAttributes(map);

    assertEquals("ISO_IR 192", ds.getString(Tag.SpecificCharacterSet));
    assertEquals("Rémi^Müller", ds.getString(Tag.ContentCreatorName));
  }

  @Test
  void file_round_trip(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("palette.dcm");
    DicomColorPalette.write(file, HOT);

    ColorMap read = DicomColorPalette.read(file).orElseThrow();
    assertEquals(HOT.stops(), read.stops());
    assertEquals(HOT.domain(), read.domain());
    assertEquals(List.of(read), DicomColorPalette.FORMAT.read(file));
  }

  @Test
  void the_export_notes_what_other_viewers_will_not_show() {
    ColorMap plain = ColorMap.builder("Plain").stop(0, Color.BLACK).stop(1, Color.WHITE).build();

    assertAll(
        () -> assertEquals(null, DicomColorPalette.FORMAT.exportNote(plain)),
        () -> assertTrue(DicomColorPalette.FORMAT.exportNote(HOT) != null, "alpha and SUV domain"),
        () -> assertTrue(DicomColorPalette.exceedsPalette(plain.toBuilder().bits(12).build())));
  }

  @Test
  void datasets_without_a_palette_are_ignored() {
    assertTrue(DicomColorPalette.fromAttributes(new Attributes()).isEmpty());
    assertFalse(DicomColorPalette.isColorPalette(new Attributes()));
  }
}
