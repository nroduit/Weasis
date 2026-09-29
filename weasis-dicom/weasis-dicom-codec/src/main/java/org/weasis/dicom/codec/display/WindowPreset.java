/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.display;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.ref.AnatomicRegion;
import org.weasis.dicom.ref.AnatomySelector;
import org.weasis.opencv.op.lut.LutShape;

/**
 * A configured window/level preset, as stored in the JSON preset documents.
 *
 * @param id stable identifier: {@code weasis.}, {@code site.} or {@code user.}
 * @param modalities DICOM modality codes, stored in upper case as the CS value representation
 *     requires (PS3.5 6.2); empty applies to every modality
 * @param shape a DICOM VOI LUT Function (LINEAR, LINEAR_EXACT, SIGMOID) or one of the Weasis
 *     extensions; an extension cannot be written into a DICOM object such as a presentation state
 * @param key shortcut digit, {@code 3} to {@code 9}; null for none
 * @param defaultPreset the default for an image that carries no window/level and no VOI LUT
 */
public record WindowPreset(
    String id,
    String name,
    Set<String> modalities,
    String category,
    List<String> tags,
    boolean hidden,
    double window,
    double level,
    Domain domain,
    LutShape shape,
    Character key,
    When when,
    boolean defaultPreset) {

  public enum DomainKind {
    /** Window and level in modality values (Hounsfield units for CT). */
    ABSOLUTE,
    /** Window and level in percent of the range of the image. */
    PERCENT
  }

  public static final String REFERENCE_IMAGE = "image"; // NON-NLS
  public static final String REFERENCE_SERIES = "series"; // NON-NLS

  /** Id prefix of the presets of the user document. */
  public static final String USER_PREFIX = "user."; // NON-NLS

  /**
   * @param unit informative unit of an absolute domain, e.g. {@code HU}
   * @param reference range of a percent domain: {@code image} (default) or {@code series}
   */
  public record Domain(DomainKind kind, String unit, String reference) {
    public static final Domain ABSOLUTE = new Domain(DomainKind.ABSOLUTE, null, null);

    public Domain {
      kind = Objects.requireNonNullElse(kind, DomainKind.ABSOLUTE);
    }
  }

  /**
   * Conditions an image must meet for the preset to be offered.
   *
   * @param minBitsStored lowest Bits Stored; the default 9 excludes 8-bit secondary captures
   * @param requiresRescale only for an image with a Modality LUT (rescale or LUT sequence)
   * @param bodyParts Body Part Examined codes, upper case; empty applies to every body part
   * @param preferredBodyParts Body Part Examined codes for which the preset is listed first; it is
   *     still offered for the other body parts
   */
  public record When(
      int minBitsStored,
      boolean requiresRescale,
      Set<String> bodyParts,
      Set<String> preferredBodyParts) {
    public static final int DEFAULT_MIN_BITS_STORED = 9;
    public static final When DEFAULT = new When(DEFAULT_MIN_BITS_STORED, false, Set.of());

    public When {
      minBitsStored = Math.max(0, minBitsStored);
      bodyParts = bodyParts == null ? Set.of() : normalizeCodes(bodyParts.stream());
      preferredBodyParts =
          preferredBodyParts == null ? Set.of() : normalizeCodes(preferredBodyParts.stream());
    }

    public When(int minBitsStored, boolean requiresRescale, Set<String> bodyParts) {
      this(minBitsStored, requiresRescale, bodyParts, Set.of());
    }

    /**
     * Whether the preset is listed first for that anatomy: it is selected by one of the preferred
     * or of the required tokens.
     *
     * @param anatomy the anatomy of the image, null when unknown
     */
    public boolean prefers(AnatomicRegion anatomy) {
      return selects(preferredBodyParts, anatomy) || selects(bodyParts, anatomy);
    }

    /**
     * @param anatomy the anatomy of the image, null when unknown
     */
    public boolean matches(int bitsStored, boolean hasRescale, AnatomicRegion anatomy) {
      return bitsStored >= minBitsStored
          && (!requiresRescale || hasRescale)
          && (bodyParts.isEmpty() || selects(bodyParts, anatomy));
    }

    /**
     * Whether a token of the anatomy notation (a region group, {@code SCHEME:code} or a Body Part
     * Examined term) selects the anatomy. A token the notation does not resolve, such as a local
     * term, is compared with the Body Part Examined of the image.
     */
    static boolean selects(Set<String> tokens, AnatomicRegion anatomy) {
      if (anatomy == null || tokens.isEmpty()) {
        return false;
      }
      String term = anatomy.getRegion().getLegacyCode();
      for (String token : tokens) {
        boolean selected =
            AnatomySelector.parse(token)
                .map(s -> s.matches(anatomy))
                .orElseGet(() -> token.equalsIgnoreCase(term));
        if (selected) {
          return true;
        }
      }
      return false;
    }
  }

  public WindowPreset {
    if (!StringUtil.hasText(id)) {
      throw new IllegalArgumentException("A window preset needs an id");
    }
    if (!StringUtil.hasText(name)) {
      throw new IllegalArgumentException("Window preset '%s' needs a name".formatted(id));
    }
    if (!Double.isFinite(window) || window <= 0 || !Double.isFinite(level)) {
      throw new IllegalArgumentException(
          "Window preset '%s' has an invalid window or level".formatted(id));
    }
    modalities = modalities == null ? Set.of() : normalizeCodes(modalities.stream());
    tags = tags == null ? List.of() : List.copyOf(tags);
    domain = Objects.requireNonNullElse(domain, Domain.ABSOLUTE);
    shape = Objects.requireNonNullElse(shape, LutShape.LINEAR);
    when = Objects.requireNonNullElse(when, When.DEFAULT);
  }

  /** The same preset with another id. */
  public WindowPreset withId(String newId) {
    return new WindowPreset(
        newId,
        name,
        modalities,
        category,
        tags,
        hidden,
        window,
        level,
        domain,
        shape,
        key,
        when,
        defaultPreset);
  }

  /** The same preset with an id in the user namespace, for an import or a copy. */
  public WindowPreset asUserPreset() {
    if (id.startsWith(USER_PREFIX)) {
      return this;
    }
    int dot = id.indexOf('.');
    return withId(USER_PREFIX + (dot > 0 && dot < id.length() - 1 ? id.substring(dot + 1) : id));
  }

  /** Codes typed with any separator: {@code ct; mr ,CT pt} gives CT, MR, PT. */
  public static Set<String> parseCodes(String text) {
    if (!StringUtil.hasText(text)) {
      return Set.of();
    }
    return normalizeCodes(Arrays.stream(text.split("[,;\\s]+"))); // NON-NLS
  }

  private static Set<String> normalizeCodes(Stream<String> codes) {
    return codes
        .filter(StringUtil::hasText)
        .map(c -> c.trim().toUpperCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());
  }

  public boolean appliesTo(String modality) {
    return modalities.isEmpty() || modalities.contains(modality);
  }

  public boolean isPercent() {
    return domain.kind() == DomainKind.PERCENT;
  }

  /** The AWT key code of {@link #key()}, 0 when there is none. */
  public int keyCode() {
    return key == null ? 0 : key;
  }

  /** The preset with its own values, for an absolute domain. */
  public PresetWindowLevel toPresetWindowLevel() {
    return toPresetWindowLevel(window, level);
  }

  /**
   * The preset resolved on a range of modality values: its own values for an absolute domain, the
   * percentages of the range for a percent domain.
   *
   * @return null when the range cannot give a window
   */
  public PresetWindowLevel resolve(double rangeMin, double rangeMax) {
    if (!isPercent()) {
      return toPresetWindowLevel();
    }
    double range = rangeMax - rangeMin;
    double w = range * window / 100.0;
    double l = rangeMin + range * level / 100.0;
    if (!Double.isFinite(w) || w <= 0 || !Double.isFinite(l)) {
      return null;
    }
    return toPresetWindowLevel(w, l);
  }

  private PresetWindowLevel toPresetWindowLevel(double w, double l) {
    PresetWindowLevel preset = new PresetWindowLevel(name, w, l, shape);
    preset.setId(id);
    preset.setKeyCode(keyCode());
    return preset;
  }
}
