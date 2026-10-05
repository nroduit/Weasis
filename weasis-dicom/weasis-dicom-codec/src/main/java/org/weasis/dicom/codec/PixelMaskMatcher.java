/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.MaskingModelRegistry;
import org.weasis.core.api.media.data.PixelMask;
import org.weasis.core.api.media.data.TagReadable;

/**
 * Finds the {@link PixelMask} of the masking document that applies to a DICOM image: the device key
 * must accept the series, the image must have the geometry the regions were drawn for, and the
 * profile in force must not be excluded by the entry.
 *
 * <p>The most specific entry wins, the user document before the site one; ties keep the document
 * order. Reading the device from the tags is why this lives in the codec, while the entries
 * themselves are stored and merged by {@link MaskingModelRegistry}.
 */
public final class PixelMaskMatcher {

  /**
   * How far the image aspect ratio may differ from the one the regions were drawn on. A banner sits
   * in a band of the layout, not at a fixed offset: a mask stretched across a format change either
   * misses the text or covers the anatomy, so a different ratio is refused rather than scaled.
   */
  public static final double RATIO_TOLERANCE = 0.01;

  /** Why an entry does not apply. */
  public enum Reason {
    DISABLED,
    DEVICE,
    GEOMETRY,
    PROFILE
  }

  /** An entry with the reason it does not apply, or null when it does. */
  public record Candidate(PixelMask mask, Reason reason) {
    public boolean applies() {
      return reason == null;
    }
  }

  private PixelMaskMatcher() {}

  /** The entry that applies, or empty. */
  public static Optional<PixelMask> best(TagReadable series, TagReadable image, String profileId) {
    return evaluate(series, image, profileId).stream()
        .filter(Candidate::applies)
        .map(Candidate::mask)
        .findFirst();
  }

  /**
   * Every entry of the merged document with its verdict, the ones that apply first, then the others
   * in document order. What the preference page and the {@code redact:} commands show.
   */
  public static List<Candidate> evaluate(TagReadable series, TagReadable image, String profileId) {
    MaskingModelRegistry registry = MaskingModelRegistry.getInstance();
    String modality = value(series, image, Tag.Modality);
    String station = value(series, image, Tag.StationName);
    String manufacturer = value(series, image, Tag.Manufacturer);
    String model = value(series, image, Tag.ManufacturerModelName);
    String institution = value(series, image, Tag.InstitutionName);
    Integer columns = TagD.getTagValue(image, Tag.Columns, Integer.class);
    Integer rows = TagD.getTagValue(image, Tag.Rows, Integer.class);

    Comparator<PixelMask> order =
        Comparator.comparingInt((PixelMask m) -> m.match().specificity())
            .thenComparing(m -> registry.masks().origin(m.id()).ordinal())
            .reversed();
    return registry.masks().entries().stream()
        .sorted(order)
        .map(
            mask ->
                new Candidate(
                    mask,
                    reject(
                        mask,
                        modality,
                        station,
                        manufacturer,
                        model,
                        institution,
                        columns,
                        rows,
                        profileId)))
        .sorted(Comparator.comparing(c -> c.applies() ? 0 : 1))
        .toList();
  }

  private static Reason reject(
      PixelMask mask,
      String modality,
      String station,
      String manufacturer,
      String model,
      String institution,
      Integer columns,
      Integer rows,
      String profileId) {
    if (!mask.enabled()) {
      return Reason.DISABLED;
    }
    if (!mask.match().matches(modality, station, manufacturer, model, institution)) {
      return Reason.DEVICE;
    }
    if (!fits(mask, columns, rows)) {
      return Reason.GEOMETRY;
    }
    return profileId == null || mask.appliesTo(profileId) ? null : Reason.PROFILE;
  }

  /** Whether an image of that size has the shape the regions were drawn for. */
  public static boolean fits(PixelMask mask, Integer columns, Integer rows) {
    if (columns == null || rows == null || columns <= 0 || rows <= 0) {
      return false;
    }
    double ratio = (double) columns / rows;
    double reference = mask.reference().ratio();
    return Math.abs(ratio - reference) <= RATIO_TOLERANCE * reference;
  }

  /** The value of the image when it carries the tag, otherwise the one of its series. */
  private static String value(TagReadable series, TagReadable image, int tag) {
    String value = TagD.getTagValue(image, tag, String.class);
    return value == null ? TagD.getTagValue(series, tag, String.class) : value;
  }
}
