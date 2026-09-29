/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.media.data;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import org.weasis.core.util.StringUtil;

/**
 * Regions to burn over the pixels of the images of one acquisition device, for the identity a
 * modality prints into the image. Stored in the masking document and matched against a series by
 * the DICOM codec; the geometry is normalized, so one entry serves every format of the same layout
 * (see {@link MaskRegion}).
 *
 * @param id stable identifier, unique in the merged document
 * @param name display name
 * @param match the device this applies to
 * @param reference size of the frame the regions were drawn on
 * @param regions regions, in document order
 * @param profiles ids of the profiles this applies to, empty for all of them
 * @param enabled whether the entry is used
 */
public record PixelMask(
    String id,
    String name,
    DeviceKey match,
    Reference reference,
    List<MaskRegion> regions,
    List<String> profiles,
    boolean enabled) {

  /** Size of the frame the regions were normalized against. */
  public record Reference(int columns, int rows) {
    public Reference {
      if (columns <= 0 || rows <= 0) {
        throw new IllegalArgumentException("Reference size must be positive");
      }
    }

    public double ratio() {
      return (double) columns / rows;
    }
  }

  /**
   * The device an entry applies to. A null or empty field matches anything; a value ending with
   * {@code *} matches by prefix. Comparison ignores case and surrounding spaces.
   */
  public record DeviceKey(
      String modality,
      String stationName,
      String manufacturer,
      String modelName,
      String institutionName) {

    public static final DeviceKey ANY = new DeviceKey(null, null, null, null, null);

    public DeviceKey {
      modality = trim(modality);
      stationName = trim(stationName);
      manufacturer = trim(manufacturer);
      modelName = trim(modelName);
      institutionName = trim(institutionName);
    }

    private static String trim(String value) {
      return StringUtil.hasText(value) ? value.trim() : null;
    }

    /** The constrained fields, in the order they are matched; empty when the key accepts any. */
    public List<String> values() {
      return Stream.of(modality, stationName, manufacturer, modelName, institutionName)
          .filter(Objects::nonNull)
          .toList();
    }

    /** How specific the key is: the entry with the most constrained fields wins. */
    public int specificity() {
      return values().size();
    }

    /** Whether every field of the key accepts the values of that device. */
    public boolean matches(
        String modality,
        String stationName,
        String manufacturer,
        String modelName,
        String institutionName) {
      return accepts(this.modality, modality)
          && accepts(this.stationName, stationName)
          && accepts(this.manufacturer, manufacturer)
          && accepts(this.modelName, modelName)
          && accepts(this.institutionName, institutionName);
    }

    /** A pattern with no value to compare against never matches, except the empty pattern. */
    public static boolean accepts(String pattern, String value) {
      if (pattern == null) {
        return true;
      }
      if (!StringUtil.hasText(value)) {
        return false;
      }
      String expected = pattern.toUpperCase(Locale.ROOT);
      String actual = value.trim().toUpperCase(Locale.ROOT);
      return expected.endsWith("*")
          ? actual.startsWith(expected.substring(0, expected.length() - 1))
          : actual.equals(expected);
    }
  }

  public PixelMask {
    Objects.requireNonNull(id);
    Objects.requireNonNull(reference);
    name = StringUtil.hasText(name) ? name : id;
    match = match == null ? DeviceKey.ANY : match;
    regions = List.copyOf(regions);
    profiles = List.copyOf(profiles);
  }

  /** Whether the entry serves that profile; an entry without a restriction serves all of them. */
  public boolean appliesTo(String profileId) {
    return profiles.isEmpty() || profiles.contains(profileId);
  }

  public PixelMask withRegions(List<MaskRegion> regions) {
    return new PixelMask(id, name, match, reference, regions, profiles, enabled);
  }

  public PixelMask withEnabled(boolean enabled) {
    return new PixelMask(id, name, match, reference, regions, profiles, enabled);
  }
}
