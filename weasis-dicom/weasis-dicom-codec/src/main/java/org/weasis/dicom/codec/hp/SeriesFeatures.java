/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.hp;

import java.util.List;
import java.util.Set;

/**
 * The normalized features of a series, derived once from its attributes so that hanging protocol
 * selectors can match on stable values instead of vendor-specific descriptions. Every field is null
 * (or {@code UNKNOWN}) when the attributes do not allow a reading; nothing is guessed from a
 * missing attribute.
 *
 * @param modality Modality (0008,0060)
 * @param sopClass SOP Class UID (0008,0016)
 * @param role what the series is for, see {@link Role}
 * @param orientation the plane of the images, see {@link Orientation}
 * @param anatomy the coded region, null when the series carries none
 * @param laterality {@code L}, {@code R}, {@code B} or {@code U}; null when absent
 * @param view the view of a projection image, {@code CC}, {@code MLO}, {@code PA}...
 * @param contrast contrast use and phase
 * @param mrWeighting the weighting of an MR series, null for other modalities
 * @param ctKernel the reconstruction kernel of a CT series and its family
 * @param sliceThickness the median Slice Thickness in mm, null when unknown
 * @param spacing the median distance between consecutive slices in mm, null when unknown
 * @param petCorrection the corrections of a PET series, null for other modalities
 * @param temporal whether the series is a static stack, a dynamic acquisition or a cine
 * @param frameOfReference Frame of Reference UID (0020,0052)
 * @param descriptionTokens the normalized tokens of the series description and protocol name
 * @param interest the images of interest the series holds
 * @param imageCount the number of images (or frames) the features were derived from
 */
public record SeriesFeatures(
    String modality,
    String sopClass,
    Role role,
    Orientation orientation,
    Anatomy anatomy,
    String laterality,
    String view,
    Contrast contrast,
    MrWeighting mrWeighting,
    CtKernel ctKernel,
    Double sliceThickness,
    Double spacing,
    PetCorrection petCorrection,
    Temporal temporal,
    String frameOfReference,
    List<String> descriptionTokens,
    Interest interest,
    int imageCount) {

  /** What the series is for, from Image Type (0008,0008) and the SOP class. */
  public enum Role {
    ORIGINAL,
    DERIVED,
    LOCALIZER,
    SCREENSHOT,
    DOSE_REPORT,
    REFORMAT,
    MIP,
    KEY_IMAGES,
    DOCUMENT
  }

  /** The plane of the images; {@code MIXED} when the images of the series disagree. */
  public enum Orientation {
    AXIAL,
    CORONAL,
    SAGITTAL,
    OBLIQUE,
    MIXED,
    UNKNOWN
  }

  /** Contrast agent use, with the phase when the attributes or the description give it. */
  public record Contrast(Agent agent, Phase phase) {
    public static final Contrast UNKNOWN = new Contrast(Agent.UNKNOWN, null);
    public static final Contrast NONE = new Contrast(Agent.NONE, null);

    public enum Agent {
      NONE,
      WITH,
      UNKNOWN
    }

    public enum Phase {
      ARTERIAL,
      PORTAL,
      DELAYED
    }
  }

  /** The weighting of an MR series. */
  public enum MrWeighting {
    T1,
    T2,
    PD,
    FLAIR,
    STIR,
    DWI,
    ADC,
    SWI,
    PERFUSION,
    DIXON,
    UNKNOWN
  }

  /** The reconstruction kernel of a CT series and the family the derivation table puts it in. */
  public record CtKernel(String kernel, Family family) {
    public enum Family {
      SOFT,
      LUNG,
      BONE,
      UNKNOWN
    }
  }

  /** The corrections of a PET series and whether SUV can be computed from it. */
  public record PetCorrection(boolean attenuationCorrected, boolean suvCapable) {}

  /** Static stack, dynamic acquisition (temporal positions) or cine. */
  public enum Temporal {
    STATIC,
    DYNAMIC,
    CINE
  }

  /**
   * The coded anatomy of the series.
   *
   * @param notation the Weasis notation: the legacy body part when the region has one ({@code
   *     CHEST}), else {@code <scheme>:<code>}
   * @param code the code value
   * @param meaning the code meaning
   * @param groups the names of the region groups the region lies in
   */
  public record Anatomy(String notation, String code, String meaning, Set<String> groups) {}

  /**
   * The images of interest of the series, see the explorer's notion of interest.
   *
   * @param count the number of images of interest, 0 when none
   * @param sources where the interest comes from ({@code KO}, {@code PR}, {@code SEG}...)
   */
  public record Interest(int count, Set<String> sources) {
    public static final Interest NONE = new Interest(0, Set.of());
  }

  /** The same features for the images of one view of a multi-view series. */
  public SeriesFeatures withView(String laterality, String view, int imageCount) {
    return new SeriesFeatures(
        modality,
        sopClass,
        role,
        orientation,
        anatomy,
        laterality,
        view,
        contrast,
        mrWeighting,
        ctKernel,
        sliceThickness,
        spacing,
        petCorrection,
        temporal,
        frameOfReference,
        descriptionTokens,
        interest,
        imageCount);
  }

  /** True when the series is a stack of images of one plane that MPR and 3D can use. */
  public boolean isVolume() {
    return imageCount > 2
        && spacing != null
        && spacing > 0
        && orientation != Orientation.MIXED
        && orientation != Orientation.UNKNOWN
        && frameOfReference != null;
  }

  /** The features as JSON, one member per feature, absent when unknown. */
  public jakarta.json.JsonObject toJson() {
    jakarta.json.JsonObjectBuilder json = jakarta.json.Json.createObjectBuilder();
    for (FeatureSelector.Key key : FeatureSelector.Key.values()) {
      Object value = key.read(this);
      switch (value) {
        case null -> {}
        case String text -> json.add(key.id(), text);
        case Boolean flag -> json.add(key.id(), flag);
        case Integer number -> json.add(key.id(), number);
        case Number number -> json.add(key.id(), number.doubleValue());
        case java.util.Collection<?> values -> {
          jakarta.json.JsonArrayBuilder array = jakarta.json.Json.createArrayBuilder();
          values.forEach(v -> array.add(String.valueOf(v)));
          json.add(key.id(), array);
        }
        default -> json.add(key.id(), String.valueOf(value));
      }
    }
    return json.build();
  }
}
