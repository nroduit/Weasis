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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.util.PaletteColorUtils;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;
import org.weasis.core.ui.editor.image.lut.ColorMapFormat;
import org.weasis.core.util.StringUtil;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.op.lut.colormap.ColorMap;
import org.weasis.opencv.op.lut.colormap.ColorMapCompiler;

/**
 * DICOM Color Palette objects (PS3.3 A.58) as color maps. Import yields a sampled map with the
 * palette's label, description, creator and UID as metadata; export writes the map's colors as a
 * Color Palette IOD (PS3.3 A.58): 256 entries of 8 bits, as the IOD requires, with the sRGB Input
 * Device profile the ICC Profile module requires. Alpha, physical domains, modality scope and a
 * resolution above 8 bits have no DICOM form and are dropped.
 */
public final class DicomColorPalette {

  public static final String DEFAULT_CREATOR = "Weasis"; // NON-NLS
  private static final int MAX_LABEL_LENGTH = 16;
  private static final int ENTRIES = 256;
  // The Color Palette IOD constrains the Palette Color LUT entries to 8 bits (PS3.3 A.58.3)
  private static final int BITS = 8;
  private static final String UTF_8 = "ISO_IR 192"; // NON-NLS

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
      };

  private DicomColorPalette() {}

  public static boolean isColorPalette(Attributes ds) {
    return ds != null && UID.ColorPaletteStorage.equals(ds.getString(Tag.SOPClassUID));
  }

  /** The palette of a Color Palette object or of any dataset carrying the palette tags. */
  public static Optional<ColorMap> fromAttributes(Attributes ds) {
    LookupTableCV lut = ds == null ? null : PaletteColorUtils.getPaletteColorLookupTable(ds);
    if (lut == null) {
      return Optional.empty();
    }
    String label = ds.getString(Tag.ContentLabel);
    String uid = ds.getString(Tag.SOPInstanceUID, ds.getString(Tag.PaletteColorLookupTableUID));
    String name = StringUtil.hasText(label) ? label : uid != null ? uid : "Palette"; // NON-NLS
    ColorMap.Builder builder =
        fromLookupTable(name, lut).toBuilder()
            .metadata(ColorMap.META_DICOM_UID, uid)
            .metadata(ColorMap.META_DICOM_LABEL, label)
            .metadata(ColorMap.META_DICOM_DESCRIPTION, ds.getString(Tag.ContentDescription))
            .metadata(ColorMap.META_DICOM_CREATOR, ds.getString(Tag.ContentCreatorName));
    return Optional.of(builder.build());
  }

  /** A sampled map from a decoded palette: 3 byte bands in B, G, R order. */
  public static ColorMap fromLookupTable(String name, LookupTableCV lut) {
    Objects.requireNonNull(lut, "Lookup table cannot be null");
    byte[][] bands = lut.getByteData();
    if (bands == null || bands.length < 3) {
      throw new IllegalArgumentException("A palette needs 3 byte bands");
    }
    return ColorMap.fromBgrTable(name, bands);
  }

  public static Optional<ColorMap> read(Path file) throws IOException {
    try (DicomInputStream in = new DicomInputStream(file.toFile())) {
      Attributes ds = in.readDataset();
      return fromAttributes(ds);
    }
  }

  /**
   * The Color Palette object of a map (PS3.3 A.58): 256 entries of 8 bits, one byte each, and the
   * sRGB profile the colors are expressed in. Reuses the map's DICOM UID when it has one, so a
   * re-exported palette, a well-known one included, keeps its identity.
   */
  public static Attributes toAttributes(ColorMap map) {
    Objects.requireNonNull(map, "Map cannot be null");
    byte[][] bgr = ColorMapCompiler.toBgr(map, ENTRIES);
    String description = map.metadata().getOrDefault(ColorMap.META_DICOM_DESCRIPTION, map.name());
    String creator = map.metadata().getOrDefault(ColorMap.META_DICOM_CREATOR, DEFAULT_CREATOR);
    Attributes ds = new Attributes();
    if (!isAscii(description) || !isAscii(creator)) {
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
    String source = StringUtil.hasText(label) ? label : map.name();
    String cs = source.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 ]", "_").trim();
    return cs.substring(0, Math.min(cs.length(), MAX_LABEL_LENGTH)).trim();
  }

  private static boolean isAscii(String text) {
    return text == null || text.chars().allMatch(c -> c < 128);
  }
}
