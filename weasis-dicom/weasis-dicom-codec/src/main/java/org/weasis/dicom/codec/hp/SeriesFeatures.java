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

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.weasis.core.api.provenance.Provenance;
import org.weasis.core.api.provenance.Sourced;
import org.weasis.core.api.provenance.Sourced.Contest;
import org.weasis.dicom.codec.hp.FeatureSelector.Key;

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
    int imageCount,
    Map<String, Provenance> provenance,
    Map<String, List<Contest>> contests) {

  public SeriesFeatures {
    provenance = provenance == null ? Map.of() : Map.copyOf(provenance);
    contests = contests == null ? Map.of() : Map.copyOf(contests);
  }

  /** The features with their provenance and no contest, as the extractor builds them. */
  public SeriesFeatures(
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
      int imageCount,
      Map<String, Provenance> provenance) {
    this(
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
        imageCount,
        provenance,
        Map.of());
  }

  /** The features without provenance, for the callers that predate it and the tests. */
  public SeriesFeatures(
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
    this(
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
        imageCount,
        Map.of());
  }

  /** Where a feature came from, by the id of its key; null for a raw or unknown one. */
  public Provenance provenanceOf(String key) {
    return provenance.get(key);
  }

  /** A feature with its provenance. */
  public Sourced<Object> sourced(FeatureSelector.Key key) {
    return new Sourced<>(key.read(this), provenance.get(key.id()));
  }

  /** The provenance of every derived feature, by key id. */
  public jakarta.json.JsonObject provenanceJson() {
    jakarta.json.JsonObjectBuilder json = jakarta.json.Json.createObjectBuilder();
    provenance.forEach((key, p) -> json.add(key, p.toJsonBuilder()));
    return json.build();
  }

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
        imageCount,
        provenance,
        contests);
  }

  /**
   * The features with one derived value replaced and its provenance recorded: what a provider or a
   * correction says. The value is typed as the component is ({@code Role}, {@code MrWeighting}, a
   * {@code String} laterality or view...); see {@link #parse} for the text form.
   *
   * @throws IllegalArgumentException for a feature that is not derived (modality, counts, UIDs)
   */
  public SeriesFeatures with(Key key, Object value, Provenance source) {
    Map<String, Provenance> sources = new LinkedHashMap<>(provenance);
    if (source != null) {
      sources.put(key.id(), source);
    } else {
      sources.remove(key.id());
    }
    Copy copy = new Copy(this, sources, contests);
    switch (key) {
      case ROLE -> copy.role = (Role) value;
      case ORIENTATION -> copy.orientation = (Orientation) value;
      case ANATOMY -> copy.anatomy = (Anatomy) value;
      case LATERALITY -> copy.laterality = (String) value;
      case VIEW -> copy.view = (String) value;
      case CONTRAST -> copy.contrast = (Contrast) value;
      case MR_WEIGHTING -> copy.mrWeighting = (MrWeighting) value;
      case CT_KERNEL_FAMILY -> copy.ctKernel = (CtKernel) value;
      case TEMPORAL -> copy.temporal = (Temporal) value;
      default -> throw new IllegalArgumentException(key.id() + " is read, never derived");
    }
    return copy.build();
  }

  /** The features with a losing proposal recorded for a feature. */
  public SeriesFeatures withContest(Key key, Contest contest) {
    Map<String, List<Contest>> all = new LinkedHashMap<>(contests);
    List<Contest> mine = new ArrayList<>(all.getOrDefault(key.id(), List.of()));
    mine.add(contest);
    all.put(key.id(), List.copyOf(mine));
    return new Copy(this, provenance, all).build();
  }

  /** The losing proposals for a feature, empty when none contested it. */
  public List<Contest> contestsOf(String key) {
    return contests.getOrDefault(key, List.of());
  }

  /** The value in force of a derived feature with its provenance and contests. */
  public Sourced<Object> sourcedOf(Key key) {
    return new Sourced<>(key.read(this), provenanceOf(key.id()), contestsOf(key.id()));
  }

  /**
   * A derived value from its text, as a command or a provider states it: the enum name for a role,
   * an orientation, a weighting, a temporal kind, a contrast agent or a kernel family; the text
   * itself for a laterality or a view; the notation for an anatomy.
   */
  public Object parse(Key key, String text) {
    String t = text == null ? "" : text.trim();
    String upper = t.toUpperCase(Locale.ROOT);
    return switch (key) {
      case ROLE -> constant(Role.class, key, upper);
      case ORIENTATION -> constant(Orientation.class, key, upper);
      case ANATOMY ->
          anatomy != null && t.equals(anatomy.notation())
              ? anatomy
              : new Anatomy(t, null, null, Set.of());
      case LATERALITY, VIEW -> t;
      case CONTRAST ->
          new Contrast(
              constant(Contrast.Agent.class, key, upper),
              contrast == null ? null : contrast.phase());
      case MR_WEIGHTING -> constant(MrWeighting.class, key, upper);
      case CT_KERNEL_FAMILY ->
          new CtKernel(
              ctKernel == null ? null : ctKernel.kernel(),
              constant(CtKernel.Family.class, key, upper));
      case TEMPORAL -> constant(Temporal.class, key, upper);
      default -> throw new IllegalArgumentException(key.id() + " is read, never derived");
    };
  }

  /** The constant named by the text, or the list of the names a caller may use. */
  private static <E extends Enum<E>> E constant(Class<E> type, Key key, String upper) {
    for (E constant : type.getEnumConstants()) {
      if (constant.name().equals(upper)) {
        return constant;
      }
    }
    throw new IllegalArgumentException(
        key.id()
            + " is one of "
            + Arrays.stream(type.getEnumConstants())
                .map(c -> c.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "))
            + ", not '"
            + upper.toLowerCase(Locale.ROOT)
            + "'");
  }

  /** The contests by feature, for {@code hp:features}. */
  public JsonObjectBuilder contestsJson() {
    JsonObjectBuilder json = Json.createObjectBuilder();
    contests.forEach(
        (key, list) -> {
          JsonArrayBuilder array = Json.createArrayBuilder();
          for (Contest contest : list) {
            JsonObjectBuilder entry = Json.createObjectBuilder();
            if (contest.value() != null) {
              entry.add("value", contest.value()); // NON-NLS
            }
            if (contest.provenance() != null) {
              entry.add("provenance", contest.provenance().toJsonBuilder()); // NON-NLS
            }
            array.add(entry);
          }
          json.add(key, array);
        });
    return json;
  }

  /** A mutable copy for {@link #with}: one field changes, the rest is carried over. */
  private static final class Copy {
    private final SeriesFeatures f;
    private final Map<String, Provenance> sources;
    private final Map<String, List<Contest>> all;
    Role role;
    Orientation orientation;
    Anatomy anatomy;
    String laterality;
    String view;
    Contrast contrast;
    MrWeighting mrWeighting;
    CtKernel ctKernel;
    Temporal temporal;

    Copy(SeriesFeatures f, Map<String, Provenance> sources, Map<String, List<Contest>> all) {
      this.f = f;
      this.sources = sources;
      this.all = all;
      this.role = f.role;
      this.orientation = f.orientation;
      this.anatomy = f.anatomy;
      this.laterality = f.laterality;
      this.view = f.view;
      this.contrast = f.contrast;
      this.mrWeighting = f.mrWeighting;
      this.ctKernel = f.ctKernel;
      this.temporal = f.temporal;
    }

    SeriesFeatures build() {
      return new SeriesFeatures(
          f.modality,
          f.sopClass,
          role,
          orientation,
          anatomy,
          laterality,
          view,
          contrast,
          mrWeighting,
          ctKernel,
          f.sliceThickness,
          f.spacing,
          f.petCorrection,
          temporal,
          f.frameOfReference,
          f.descriptionTokens,
          f.interest,
          f.imageCount,
          sources,
          all);
    }
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
