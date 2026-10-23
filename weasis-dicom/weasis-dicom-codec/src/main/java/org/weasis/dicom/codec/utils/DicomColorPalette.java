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

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonReader;
import java.awt.Color;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.util.LookupTableUtils;
import org.dcm4che3.img.util.PaletteColorUtils;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.image.lut.ColorMapJson;
import org.weasis.core.ui.editor.image.lut.ColorMapFormat;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.Messages;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.op.lut.colormap.ColorMap;
import org.weasis.opencv.op.lut.colormap.ColorMapCompiler;
import org.weasis.opencv.op.lut.colormap.ColorStop;
import org.weasis.opencv.op.lut.colormap.DomainKind;
import org.weasis.opencv.op.lut.colormap.Interpolation;
import org.weasis.opencv.op.lut.colormap.OutsideColors;

/**
 * DICOM Color Palette objects (PS3.3 A.58) as color maps. Import yields a sampled map with the
 * palette's label, description, creator and UID as metadata; export writes the map's colors as a
 * Color Palette IOD (PS3.3 A.58): 256 entries of 8 bits, as the IOD requires, with the sRGB Input
 * Device profile the ICC Profile module requires. Alpha, physical domains, modality scope and a
 * resolution above 8 bits have no DICOM form: the whole map is also written as JSON in a private
 * element, which import restores when the palette still holds the colors the map compiles to.
 */
public final class DicomColorPalette {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomColorPalette.class);

  public static final String DEFAULT_CREATOR = "Weasis"; // NON-NLS
  private static final int MAX_LABEL_LENGTH = 16;
  private static final int ENTRIES = 256;
  // The Color Palette IOD constrains the Palette Color LUT entries to 8 bits (PS3.3 A.58.3)
  private static final int BITS = 8;
  private static final String UTF_8 = "ISO_IR 192"; // NON-NLS

  /** Private creator of the element holding the map as {@link ColorMapJson} text. */
  public static final String PRIVATE_CREATOR = "WEASIS COLORMAP 1"; // NON-NLS

  /** The JSON element, (0077,xx01) in the block of {@link #PRIVATE_CREATOR}. */
  public static final int TAG_COLOR_MAP_JSON = 0x00770001;

  // A palette edited by another tool no longer matches its JSON; a compiler update may move a level
  private static final int MAX_LEVEL_DIFFERENCE = 2;

  /** File format registered in the editor by the DICOM viewer. */
  public static final ColorMapFormat FORMAT =
      new ColorMapFormat() {
        @Override
        public String description() {
          return "DICOM color palette"; // NON-NLS
        }

        @Override
        public List<String> extensions() {
          return List.of("dcm"); // NON-NLS
        }

        @Override
        public List<ColorMap> read(Path file) throws IOException {
          return DicomColorPalette.read(file).map(List::of).orElse(List.of());
        }

        @Override
        public boolean canWrite() {
          return true;
        }

        @Override
        public void write(Path file, ColorMap map) throws IOException {
          DicomColorPalette.write(file, map);
        }

        @Override
        public String exportNote(ColorMap map) {
          return exceedsPalette(map) ? Messages.getString("DicomColorPalette.export.note") : null;
        }
      };

  private DicomColorPalette() {}

  /**
   * Whether {@code map} renders differently from its 256 opaque colors, so that only Weasis,
   * through the private JSON element, shows it as authored.
   */
  static boolean exceedsPalette(ColorMap map) {
    return map.hasAlpha()
        || map.hasMaterial()
        || map.lighting() != null
        || map.bits() > BITS
        || map.domain().kind() != DomainKind.RELATIVE
        || !OutsideColors.CLAMP.equals(map.outside());
  }

  public static boolean isColorPalette(Attributes ds) {
    return ds != null && UID.ColorPaletteStorage.equals(ds.getString(Tag.SOPClassUID));
  }

  /**
   * The palette of a Color Palette object or of any dataset carrying the palette tags: the map of
   * the private JSON element when the palette still matches it, else a sampled map of the palette
   * with its alpha table, if any.
   */
  public static Optional<ColorMap> fromAttributes(Attributes ds) {
    LookupTableCV lut = ds == null ? null : PaletteColorUtils.getPaletteColorLookupTable(ds);
    if (lut == null) {
      return Optional.empty();
    }
    String label = ds.getString(Tag.ContentLabel);
    String description = ds.getString(Tag.ContentDescription);
    String uid = ds.getString(Tag.SOPInstanceUID, ds.getString(Tag.PaletteColorLookupTableUID));
    ColorMap.Builder builder =
        embeddedMap(ds, lut)
            .orElseGet(() -> sampledMap(paletteName(label, description, uid), lut, ds))
            .toBuilder()
            .metadata(ColorMap.META_DICOM_UID, uid)
            .metadata(ColorMap.META_DICOM_LABEL, label)
            .metadata(ColorMap.META_DICOM_DESCRIPTION, description)
            .metadata(ColorMap.META_DICOM_CREATOR, ds.getString(Tag.ContentCreatorName));
    return Optional.of(builder.build());
  }

  // The export derives the label from the description (the map name): the description is the name
  private static String paletteName(String label, String description, String uid) {
    if (StringUtil.hasText(description)
        && (!StringUtil.hasText(label) || codeString(description).equals(label.trim()))) {
      return description;
    }
    return StringUtil.hasText(label) ? label : uid != null ? uid : "Palette"; // NON-NLS
  }

  /** A sampled map from a decoded palette: 3 byte bands in B, G, R order. */
  public static ColorMap fromLookupTable(String name, LookupTableCV lut) {
    return sampledMap(name, lut, null);
  }

  /**
   * One stop per entry. Entry {@code i} colors input {@code first + i}, inputs below the first
   * mapped value take the first entry (PS3.3 C.7.6.3.1.5), so positions run over {@code [0, first +
   * n - 1]}; the alpha table of {@code ds}, when it has one of the same length, becomes the alpha
   * of the stops.
   */
  private static ColorMap sampledMap(String name, LookupTableCV lut, Attributes ds) {
    Objects.requireNonNull(lut, "Lookup table cannot be null");
    byte[][] bands = lut.getByteData();
    if (bands == null || bands.length < 3) {
      throw new IllegalArgumentException("A palette needs 3 byte bands");
    }
    int first = Math.max(0, lut.getOffset(0));
    byte[] alpha = alphaTable(ds, bands[0].length);
    if (first == 0 && alpha == null) {
      return ColorMap.fromBgrTable(name, bands);
    }
    int n = bands[0].length;
    double last = (double) first + n - 1;
    List<ColorStop> stops = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      Color color =
          new Color(
              Byte.toUnsignedInt(bands[2][i]),
              Byte.toUnsignedInt(bands[1][i]),
              Byte.toUnsignedInt(bands[0][i]));
      Float a = alpha == null ? null : Byte.toUnsignedInt(alpha[i]) / 255f;
      stops.add(new ColorStop((first + i) / last, color, a, null, null));
    }
    return ColorMap.builder(name)
        .id("legacy." + ColorMap.slug(name)) // NON-NLS
        .interpolation(Interpolation.SAMPLED)
        .stops(stops)
        .build();
  }

  // The Alpha Palette Color LUT, null when absent, fully opaque or not one entry per color
  private static byte[] alphaTable(Attributes ds, int entries) {
    if (ds == null || !ds.containsValue(Tag.AlphaPaletteColorLookupTableDescriptor)) {
      return null;
    }
    try {
      int[] desc = LookupTableUtils.lutDescriptor(ds, Tag.AlphaPaletteColorLookupTableDescriptor);
      byte[] data =
          LookupTableUtils.lutData(
              ds,
              desc,
              Tag.AlphaPaletteColorLookupTableData,
              Tag.SegmentedAlphaPaletteColorLookupTableData);
      if (data == null || data.length != entries) {
        return null;
      }
      for (byte b : data) {
        if (b != (byte) 0xFF) {
          return data;
        }
      }
    } catch (IllegalArgumentException e) {
      LOGGER.warn("Ignoring the alpha palette: {}", e.getMessage());
    }
    return null;
  }

  /** The map of the private JSON element, when present, valid and still matching the palette. */
  private static Optional<ColorMap> embeddedMap(Attributes ds, LookupTableCV lut) {
    String json = embeddedJson(ds);
    if (json == null) {
      return Optional.empty();
    }
    ColorMap map;
    try (JsonReader reader = Json.createReader(new StringReader(json))) {
      map = ColorMapJson.fromJson(reader.readObject());
    } catch (JsonException | IllegalArgumentException | ClassCastException e) {
      LOGGER.warn("Ignoring the color map JSON of a palette: {}", e.getMessage());
      return Optional.empty();
    }
    if (!matches(ColorMapCompiler.toBgr(map, ENTRIES), lut)) {
      LOGGER.info("The palette no longer matches its color map JSON, reading the palette only");
      return Optional.empty();
    }
    return Optional.of(map);
  }

  private static String embeddedJson(Attributes ds) {
    try {
      // Bytes rather than a string: a file re-encoded in implicit VR holds the element as UN
      byte[] bytes = ds.getBytes(PRIVATE_CREATOR, TAG_COLOR_MAP_JSON);
      if (bytes == null || bytes.length == 0) {
        return null;
      }
      String text = new String(bytes, StandardCharsets.UTF_8).strip();
      return text.isEmpty() ? null : text;
    } catch (IOException e) {
      return null;
    }
  }

  private static boolean matches(byte[][] expected, LookupTableCV lut) {
    byte[][] bands = lut.getByteData();
    if (lut.getOffset(0) != 0 || bands == null || bands.length < 3) {
      return false;
    }
    for (int band = 0; band < 3; band++) {
      if (bands[band].length != expected[band].length) {
        return false;
      }
      for (int i = 0; i < expected[band].length; i++) {
        int diff = Byte.toUnsignedInt(bands[band][i]) - Byte.toUnsignedInt(expected[band][i]);
        if (Math.abs(diff) > MAX_LEVEL_DIFFERENCE) {
          return false;
        }
      }
    }
    return true;
  }

  public static Optional<ColorMap> read(Path file) throws IOException {
    try (DicomInputStream in = new DicomInputStream(file.toFile())) {
      Attributes ds = in.readDataset();
      return fromAttributes(ds);
    }
  }

  /**
   * The Color Palette object of a map (PS3.3 A.58): 256 entries of 8 bits, one byte each, and the
   * sRGB profile the colors are expressed in, plus the map as JSON in a private element for a
   * lossless return to Weasis. Reuses the map's DICOM UID when it has one, so a re-exported
   * palette, a well-known one included, keeps its identity.
   */
  public static Attributes toAttributes(ColorMap map) {
    Objects.requireNonNull(map, "Map cannot be null");
    byte[][] bgr = ColorMapCompiler.toBgr(map, ENTRIES);
    String description = map.metadata().getOrDefault(ColorMap.META_DICOM_DESCRIPTION, map.name());
    String creator = map.metadata().getOrDefault(ColorMap.META_DICOM_CREATOR, DEFAULT_CREATOR);
    String json = ColorMapJson.toJson(map).toString();
    Attributes ds = new Attributes();
    if (!isAscii(description) || !isAscii(creator) || !isAscii(json)) {
      ds.setString(Tag.SpecificCharacterSet, VR.CS, UTF_8);
    }
    ds.setString(Tag.SOPClassUID, VR.UI, UID.ColorPaletteStorage);
    ds.setString(
        Tag.SOPInstanceUID,
        VR.UI,
        UIDUtils.createUIDIfNull(map.metadata().get(ColorMap.META_DICOM_UID)));
    ds.setInt(Tag.InstanceNumber, VR.IS, 1);
    ds.setString(Tag.ContentLabel, VR.CS, contentLabel(map));
    ds.setString(Tag.ContentDescription, VR.LO, description);
    ds.setString(Tag.ContentCreatorName, VR.PN, creator);
    int[] descriptor = {ENTRIES, 0, BITS};
    ds.setInt(Tag.RedPaletteColorLookupTableDescriptor, VR.US, descriptor);
    ds.setInt(Tag.GreenPaletteColorLookupTableDescriptor, VR.US, descriptor);
    ds.setInt(Tag.BluePaletteColorLookupTableDescriptor, VR.US, descriptor);
    ds.setBytes(Tag.RedPaletteColorLookupTableData, VR.OW, bgr[2]);
    ds.setBytes(Tag.GreenPaletteColorLookupTableData, VR.OW, bgr[1]);
    ds.setBytes(Tag.BluePaletteColorLookupTableData, VR.OW, bgr[0]);
    ds.setBytes(Tag.ICCProfile, VR.OB, SrgbInputProfile.data());
    ds.setString(Tag.ColorSpace, VR.CS, "SRGB"); // NON-NLS
    ds.setString(PRIVATE_CREATOR, TAG_COLOR_MAP_JSON, VR.UT, json);
    return ds;
  }

  public static void write(Path file, ColorMap map) throws IOException {
    Attributes ds = toAttributes(map);
    Attributes fmi = ds.createFileMetaInformation(UID.ExplicitVRLittleEndian);
    try (DicomOutputStream out = new DicomOutputStream(file.toFile())) {
      out.writeDataset(fmi, ds);
    }
  }

  /** Content Label: code string of at most 16 characters, upper case, underscores for the rest. */
  static String contentLabel(ColorMap map) {
    String label = map.metadata().get(ColorMap.META_DICOM_LABEL);
    return codeString(StringUtil.hasText(label) ? label : map.name());
  }

  private static String codeString(String source) {
    String cs = source.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 ]", "_").trim();
    return cs.substring(0, Math.min(cs.length(), MAX_LABEL_LENGTH)).trim();
  }

  private static boolean isAscii(String text) {
    return text == null || text.chars().allMatch(c -> c < 128);
  }
}
