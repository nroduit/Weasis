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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.codec.geometry.ImageOrientation.Plan;
import org.weasis.dicom.codec.hp.DerivationTables.MrSample;
import org.weasis.dicom.codec.hp.SeriesFeatures.Anatomy;
import org.weasis.dicom.codec.hp.SeriesFeatures.Contrast;
import org.weasis.dicom.codec.hp.SeriesFeatures.CtKernel;
import org.weasis.dicom.codec.hp.SeriesFeatures.Interest;
import org.weasis.dicom.codec.hp.SeriesFeatures.Orientation;
import org.weasis.dicom.codec.hp.SeriesFeatures.PetCorrection;
import org.weasis.dicom.codec.hp.SeriesFeatures.Role;
import org.weasis.dicom.codec.hp.SeriesFeatures.Temporal;
import org.weasis.dicom.ref.AnatomicItem;
import org.weasis.dicom.ref.AnatomicRegion;
import org.weasis.dicom.ref.RegionGroup;

/**
 * Derives the {@link SeriesFeatures} of a series from the tags the codec keeps on the series and
 * its images and, for the attributes it does not keep, from the dataset of a sample of images. The
 * result is cached on the series and derived again when images were added since.
 *
 * <p>Attribute readings follow PS3.3; every departure (a vendor table, a reading of the series
 * description) is marked where it happens.
 */
public final class SeriesFeatureExtractor {

  /** The cache on the series: the features and the image count they were derived from. */
  public static final TagW FEATURES = new TagW("SeriesFeatures", TagType.OBJECT); // NON-NLS

  /** At most this many images are read for the per-image attributes. */
  static final int SAMPLE = 64;

  private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{Alnum}+]+");
  private static final Set<String> STOP_WORDS =
      Set.of("THE", "OF", "AND", "WITH", "WO", "W", "O", "A", "AN", "SERIES", "SER", "IMAGES");

  /** View Code Sequence values of the mammography views, PS3.16 CID 4014. */
  private static final Map<String, String> VIEW_CODES =
      Map.ofEntries(
          Map.entry("R-10242", "CC"), // NON-NLS
          Map.entry("399162004", "CC"), // NON-NLS
          Map.entry("R-10226", "MLO"), // NON-NLS
          Map.entry("399368009", "MLO"), // NON-NLS
          Map.entry("R-10224", "ML"), // NON-NLS
          Map.entry("399260004", "ML"), // NON-NLS
          Map.entry("R-10228", "LM"), // NON-NLS
          Map.entry("399352003", "LM"), // NON-NLS
          Map.entry("R-102D2", "FB"), // NON-NLS
          Map.entry("399196000", "FB"), // NON-NLS
          Map.entry("R-102CF", "XCCL"), // NON-NLS
          Map.entry("399091000", "XCCL"), // NON-NLS
          Map.entry("R-102D0", "XCCM"), // NON-NLS
          Map.entry("399033000", "XCCM")); // NON-NLS

  private record Cached(int size, SeriesFeatures features) {}

  private final DerivationTables tables;
  private final InterestProvider interest;

  public SeriesFeatureExtractor(DerivationTables tables, InterestProvider interest) {
    this.tables = Objects.requireNonNull(tables);
    this.interest = Objects.requireNonNull(interest);
  }

  /** The extractor on the shared tables, without images of interest. */
  public SeriesFeatureExtractor() {
    this(DerivationTables.getInstance(), InterestProvider.NONE);
  }

  /** The features of the series, from the cache when no image was added since. */
  public SeriesFeatures of(MediaSeries<DicomImageElement> series) {
    synchronized (series) {
      int size = series.size(null);
      if (series.getTagValue(FEATURES) instanceof Cached cached && cached.size() == size) {
        return cached.features();
      }
      SeriesFeatures features = extract(series);
      series.setTag(FEATURES, new Cached(size, features));
      return features;
    }
  }

  /** Drops the cache so that the next {@link #of} derives the features again. */
  public static void invalidate(MediaSeries<?> series) {
    series.setTag(FEATURES, null);
  }

  /** Derives the features without touching the cache. */
  public SeriesFeatures extract(MediaSeries<DicomImageElement> series) {
    List<DicomImageElement> images = series.copyOfMedias(null, SortSeriesStack.instanceNumber);
    List<DicomImageElement> sample = sample(images);
    DicomImageElement first = images.isEmpty() ? null : images.getFirst();
    Attributes dcm = first == null ? null : dataset(first);
    Attributes shared = shared(dcm);

    String modality = text(series, first, Tag.Modality);
    String sopClass = text(series, first, Tag.SOPClassUID);
    List<String> imageType = strings(dcm, Tag.ImageType);
    List<String> tokens = tokens(series, dcm);
    Double tr = decimal(dcm, shared, Tag.RepetitionTime, Tag.MRTimingAndRelatedParametersSequence);
    Double te = decimal(dcm, shared, Tag.EchoTime, Tag.MREchoSequence);
    Double ti = decimal(dcm, shared, Tag.InversionTime, Tag.MRTimingAndRelatedParametersSequence);

    Role role = role(modality, sopClass, imageType, tokens);
    boolean image = role != Role.DOCUMENT && role != Role.KEY_IMAGES;
    return new SeriesFeatures(
        modality,
        sopClass,
        role,
        image ? orientation(sample) : Orientation.UNKNOWN,
        anatomy(first, dcm),
        laterality(series, dcm, shared),
        view(dcm),
        contrast(dcm, tokens),
        "MR".equals(modality) // NON-NLS
            ? tables.mrWeighting(
                new MrSample(
                    strings(dcm, shared, Tag.ScanningSequence, Tag.MRModifierSequence),
                    strings(dcm, shared, Tag.SequenceVariant, Tag.MRModifierSequence),
                    imageType,
                    tokens,
                    tr,
                    te,
                    ti,
                    bValue(sample, dcm, shared)))
            : null,
        "CT".equals(modality) ? kernel(series, dcm, shared) : null, // NON-NLS
        image ? sliceThickness(sample, shared) : null,
        image ? spacing(sample) : null,
        "PT".equals(modality) ? petCorrection(dcm) : null, // NON-NLS
        temporal(series, first, dcm),
        text(series, first, Tag.FrameOfReferenceUID),
        tokens,
        Optional.ofNullable(interest.interestOf(series)).orElse(Interest.NONE),
        images.size());
  }

  private static List<DicomImageElement> sample(List<DicomImageElement> images) {
    if (images.size() <= SAMPLE) {
      return images;
    }
    List<DicomImageElement> sample = new ArrayList<>(SAMPLE);
    double step = (images.size() - 1) / (double) (SAMPLE - 1);
    for (int i = 0; i < SAMPLE; i++) {
      sample.add(images.get((int) Math.round(i * step)));
    }
    return sample;
  }

  // ---- roles and orientation

  static Role role(String modality, String sopClass, List<String> imageType, List<String> tokens) {
    if ("KO".equals(modality)
        || UID.KeyObjectSelectionDocumentStorage.equals(sopClass)) { // NON-NLS
      return Role.KEY_IMAGES;
    }
    if ("SR".equals(modality) || "DOC".equals(modality) || "PR".equals(modality)) { // NON-NLS
      return UID.XRayRadiationDoseSRStorage.equals(sopClass) ? Role.DOSE_REPORT : Role.DOCUMENT;
    }
    if (UID.EncapsulatedPDFStorage.equals(sopClass)
        || UID.EncapsulatedCDAStorage.equals(sopClass)) {
      return Role.DOCUMENT;
    }
    Set<String> types = upper(imageType);
    if (types.contains("LOCALIZER")) { // NON-NLS
      return Role.LOCALIZER;
    }
    // The dose report of a CT is often a Secondary Capture whose Image Type value 3 or series
    // description says so: Image Type is DERIVED\SECONDARY (PS3.3 C.8.6.1) and carries no "dose"
    // value; reading the description is a departure from a strict reading of the attributes.
    if (types.contains("DOSE_INFO") // NON-NLS
        || types.contains("DOSE_REPORT") // NON-NLS
        || (isSecondaryCapture(sopClass) && tokens.contains("DOSE"))) { // NON-NLS
      return Role.DOSE_REPORT;
    }
    if (types.stream().anyMatch(t -> t.contains("MIP") || t.equals("MAXIMUM"))) { // NON-NLS
      return Role.MIP;
    }
    if (types.stream().anyMatch(t -> t.contains("REFORMAT") || t.equals("MPR"))) { // NON-NLS
      return Role.REFORMAT;
    }
    if (isSecondaryCapture(sopClass) || types.contains("SCREEN SAVE")) { // NON-NLS
      return Role.SCREENSHOT;
    }
    if (!imageType.isEmpty() && "DERIVED".equalsIgnoreCase(imageType.getFirst())) { // NON-NLS
      return Role.DERIVED;
    }
    return Role.ORIGINAL;
  }

  private static boolean isSecondaryCapture(String sopClass) {
    return sopClass != null && UID.nameOf(sopClass).contains("Secondary Capture"); // NON-NLS
  }

  static Orientation orientation(List<DicomImageElement> sample) {
    EnumSet<Plan> plans = EnumSet.noneOf(Plan.class);
    for (DicomImageElement image : sample) {
      Plan plan = ImageOrientation.getPlan(image);
      if (plan != null) {
        plans.add(plan);
      }
    }
    if (plans.isEmpty()) {
      return Orientation.UNKNOWN;
    }
    if (plans.size() > 1) {
      return Orientation.MIXED;
    }
    return switch (plans.iterator().next()) {
      case AXIAL -> Orientation.AXIAL;
      case CORONAL -> Orientation.CORONAL;
      case SAGITTAL -> Orientation.SAGITTAL;
      default -> Orientation.OBLIQUE;
    };
  }

  // ---- anatomy, laterality, view

  static Anatomy anatomy(DicomImageElement first, Attributes dcm) {
    AnatomicRegion region =
        first != null && first.getTagValue(TagW.AnatomicRegion) instanceof AnatomicRegion r
            ? r
            : AnatomicRegion.read(dcm);
    if (region == null || region.getRegion() == null) {
      return null;
    }
    AnatomicItem item = region.getRegion();
    String legacy = item.getLegacyCode();
    String scheme =
        item.getCodingScheme() == null ? "SCT" : item.getCodingScheme().getDesignator(); // NON-NLS
    String notation =
        legacy != null && !legacy.isBlank() ? legacy : scheme + ":" + item.getCodeValue();
    Set<String> groups =
        region.getGroups().stream()
            .map(RegionGroup::name)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    return new Anatomy(notation, item.getCodeValue(), item.getCodeMeaning(), groups);
  }

  static String laterality(
      MediaSeries<DicomImageElement> series, Attributes dcm, Attributes shared) {
    // Image Laterality (0020,0062) is the per-image value, Laterality (0020,0060) the series one;
    // enhanced objects carry Frame Laterality in the Frame Anatomy functional group (C.7.6.16.2.8)
    String value = string(dcm, Tag.ImageLaterality);
    if (value == null) {
      Object series0 = series.getTagValue(TagD.get(Tag.Laterality));
      value = series0 instanceof String s ? s : string(dcm, Tag.Laterality);
    }
    if (value == null && shared != null) {
      Attributes anatomy = shared.getNestedDataset(Tag.FrameAnatomySequence);
      value = anatomy == null ? null : anatomy.getString(Tag.FrameLaterality);
    }
    if (value == null) {
      return null;
    }
    return switch (value.trim().toUpperCase(Locale.ROOT)) {
      case "L", "R", "B", "U" -> value.trim().toUpperCase(Locale.ROOT); // NON-NLS
      default -> null;
    };
  }

  static String view(Attributes dcm) {
    if (dcm == null) {
      return null;
    }
    Attributes code = dcm.getNestedDataset(Tag.ViewCodeSequence);
    if (code != null) {
      String mapped = VIEW_CODES.get(code.getString(Tag.CodeValue, ""));
      if (mapped != null) {
        return mapped;
      }
      String meaning = code.getString(Tag.CodeMeaning);
      if (meaning != null && !meaning.isBlank()) {
        return meaning.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
      }
    }
    String position = string(dcm, Tag.ViewPosition);
    return position == null ? null : position.toUpperCase(Locale.ROOT);
  }

  // ---- contrast

  static Contrast contrast(Attributes dcm, List<String> tokens) {
    if (dcm == null) {
      return Contrast.UNKNOWN;
    }
    boolean with =
        dcm.containsValue(Tag.ContrastBolusAgent)
            || dcm.containsValue(Tag.ContrastBolusAgentSequence)
            || dcm.containsValue(Tag.ContrastBolusRoute)
            || dcm.containsValue(Tag.ContrastBolusVolume);
    if (!with && !dcm.contains(Tag.ContrastBolusAgent)) {
      // Contrast/Bolus Agent is type 2 in the Contrast/Bolus module (C.7.6.4): absent means the
      // module is absent, so nothing is known; present and empty means no agent.
      return tokens.stream()
              .anyMatch(t -> t.equals("C+") || t.equals("CE") || t.equals("CONTRAST")) // NON-NLS
          ? new Contrast(Contrast.Agent.WITH, phase(tokens))
          : Contrast.UNKNOWN;
    }
    return with ? new Contrast(Contrast.Agent.WITH, phase(tokens)) : Contrast.NONE;
  }

  /**
   * The phase is not a DICOM attribute of the image (the Contrast/Bolus Usage functional group says
   * only whether the agent is visible); it is read from the description tokens, which is a
   * departure from a strict reading of the attributes.
   */
  private static Contrast.Phase phase(List<String> tokens) {
    if (tokens.stream().anyMatch(t -> t.startsWith("ART"))) { // NON-NLS
      return Contrast.Phase.ARTERIAL;
    }
    if (tokens.stream()
        .anyMatch(t -> t.startsWith("PORT") || t.startsWith("VEN") || t.equals("PV"))) { // NON-NLS
      return Contrast.Phase.PORTAL;
    }
    if (tokens.stream().anyMatch(t -> t.startsWith("DELAY") || t.equals("LATE"))) { // NON-NLS
      return Contrast.Phase.DELAYED;
    }
    return null;
  }

  // ---- CT, MR, PET

  CtKernel kernel(MediaSeries<DicomImageElement> series, Attributes dcm, Attributes shared) {
    String kernel = string(dcm, Tag.ConvolutionKernel);
    if (kernel == null && shared != null) {
      Attributes recon = shared.getNestedDataset(Tag.CTReconstructionSequence);
      kernel = recon == null ? null : recon.getString(Tag.ConvolutionKernel);
    }
    if (kernel == null) {
      return null;
    }
    Object manufacturer = series.getTagValue(TagD.get(Tag.Manufacturer));
    String vendor = manufacturer instanceof String s ? s : string(dcm, Tag.Manufacturer);
    return new CtKernel(kernel, tables.kernelFamily(vendor, kernel));
  }

  private static Double bValue(List<DicomImageElement> sample, Attributes dcm, Attributes shared) {
    Double max = null;
    for (DicomImageElement image : sample) {
      Double b = TagD.getTagValue(image, Tag.DiffusionBValue, Double.class);
      if (b != null && (max == null || b > max)) {
        max = b;
      }
    }
    if (max != null) {
      return max;
    }
    Double root = decimal(dcm, shared, Tag.DiffusionBValue, Tag.MRDiffusionSequence);
    return root;
  }

  static PetCorrection petCorrection(Attributes dcm) {
    if (dcm == null) {
      return null;
    }
    Set<String> corrected = upper(strings(dcm, Tag.CorrectedImage));
    boolean attenuation = corrected.contains("ATTN"); // NON-NLS
    // SUV needs Bq/ml units, decay correction, the injected dose and time, and the patient weight
    // (PS3.16 TID 14021 / QIBA SUV computation); anything else cannot be scaled.
    Attributes radio = dcm.getNestedDataset(Tag.RadiopharmaceuticalInformationSequence);
    boolean suv =
        "BQML".equals(dcm.getString(Tag.Units)) // NON-NLS
            && dcm.containsValue(Tag.DecayCorrection)
            && dcm.containsValue(Tag.PatientWeight)
            && radio != null
            && radio.containsValue(Tag.RadionuclideTotalDose)
            && (radio.containsValue(Tag.RadiopharmaceuticalStartTime)
                || radio.containsValue(Tag.RadiopharmaceuticalStartDateTime));
    return new PetCorrection(attenuation, suv);
  }

  // ---- geometry

  static Double sliceThickness(List<DicomImageElement> sample, Attributes shared) {
    List<Double> values = new ArrayList<>();
    for (DicomImageElement image : sample) {
      Double t = TagD.getTagValue(image, Tag.SliceThickness, Double.class);
      if (t != null && t > 0) {
        values.add(t);
      }
    }
    if (values.isEmpty() && shared != null) {
      Attributes measures = shared.getNestedDataset(Tag.PixelMeasuresSequence);
      Double t = measures == null ? null : measures.getDouble(Tag.SliceThickness, 0);
      if (t != null && t > 0) {
        values.add(t);
      }
    }
    return median(values);
  }

  /** The median distance between consecutive slices, along the normal of the first image. */
  static Double spacing(List<DicomImageElement> sample) {
    List<Double> positions = new ArrayList<>();
    double[] normal = null;
    for (DicomImageElement image : sample) {
      double[] pos = TagD.getTagValue(image, Tag.ImagePositionPatient, double[].class);
      if (pos != null && pos.length == 3) {
        if (normal == null) {
          normal = normal(image);
        }
        if (normal != null) {
          positions.add(pos[0] * normal[0] + pos[1] * normal[1] + pos[2] * normal[2]);
          continue;
        }
      }
      Double location = TagD.getTagValue(image, Tag.SliceLocation, Double.class);
      if (location != null) {
        positions.add(location);
      }
    }
    if (positions.size() < 2) {
      return null;
    }
    positions.sort(null);
    List<Double> gaps = new ArrayList<>();
    for (int i = 1; i < positions.size(); i++) {
      double gap = positions.get(i) - positions.get(i - 1);
      if (gap > 1e-4) {
        gaps.add(gap);
      }
    }
    return median(gaps);
  }

  private static double[] normal(TagReadable image) {
    double[] o = TagD.getTagValue(image, Tag.ImageOrientationPatient, double[].class);
    if (o == null || o.length != 6) {
      return null;
    }
    return new double[] {
      o[1] * o[5] - o[2] * o[4], o[2] * o[3] - o[0] * o[5], o[0] * o[4] - o[1] * o[3]
    };
  }

  private static Double median(List<Double> values) {
    if (values.isEmpty()) {
      return null;
    }
    List<Double> sorted = new ArrayList<>(values);
    sorted.sort(null);
    int n = sorted.size();
    return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
  }

  // ---- temporal

  static Temporal temporal(
      MediaSeries<DicomImageElement> series, DicomImageElement first, Attributes dcm) {
    if (dcm == null) {
      return Temporal.STATIC;
    }
    Integer temporalPositions = dcm.getInt(Tag.NumberOfTemporalPositions, 0);
    if (temporalPositions > 1 || dcm.containsValue(Tag.TemporalPositionIndex)) {
      return Temporal.DYNAMIC;
    }
    Integer frames =
        first == null ? null : TagD.getTagValue(first, Tag.NumberOfFrames, Integer.class);
    boolean cineTiming =
        dcm.containsValue(Tag.CineRate)
            || dcm.containsValue(Tag.FrameTime)
            || dcm.containsValue(Tag.FrameTimeVector)
            || dcm.containsValue(Tag.RecommendedDisplayFrameRate);
    if (cineTiming && frames != null && frames > 1) {
      return Temporal.CINE;
    }
    String modality = text(series, first, Tag.Modality);
    // A multi-frame XA, RF or US object without frame timing is still a cine run in practice
    // (PS3.3 C.8.7 and C.8.5 make the timing attributes conditional); departure for robustness.
    if (frames != null
        && frames > 1
        && modality != null
        && Set.of("XA", "RF", "US").contains(modality)) { // NON-NLS
      return Temporal.CINE;
    }
    return Temporal.STATIC;
  }

  // ---- description tokens

  static List<String> tokens(MediaSeries<DicomImageElement> series, Attributes dcm) {
    Set<String> tokens = new LinkedHashSet<>();
    Object description = series.getTagValue(TagD.get(Tag.SeriesDescription));
    addTokens(tokens, description instanceof String s ? s : string(dcm, Tag.SeriesDescription));
    addTokens(tokens, string(dcm, Tag.ProtocolName));
    return List.copyOf(tokens);
  }

  private static void addTokens(Set<String> tokens, String text) {
    if (text == null) {
      return;
    }
    for (String token : TOKEN_SPLIT.split(text.toUpperCase(Locale.ROOT))) {
      if (!token.isEmpty() && !STOP_WORDS.contains(token)) {
        tokens.add(token);
      }
    }
  }

  // ---- attribute access

  private static Attributes dataset(DicomImageElement image) {
    return image.getMediaReader() == null ? null : image.getMediaReader().getDicomObject();
  }

  private static Attributes shared(Attributes dcm) {
    return dcm == null ? null : dcm.getNestedDataset(Tag.SharedFunctionalGroupsSequence);
  }

  private static String text(
      MediaSeries<DicomImageElement> series, DicomImageElement image, int tag) {
    Object value = series.getTagValue(TagD.get(tag));
    if (value == null && image != null) {
      value = image.getTagValue(TagD.get(tag));
    }
    return value instanceof String s && !s.isBlank() ? s.trim() : null;
  }

  private static String string(Attributes dcm, int tag) {
    if (dcm == null) {
      return null;
    }
    String value = dcm.getString(tag);
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static List<String> strings(Attributes dcm, int tag) {
    String[] values = dcm == null ? null : dcm.getStrings(tag);
    return values == null ? List.of() : Arrays.stream(values).map(String::trim).toList();
  }

  /** The values at the root, else in that functional group of the shared groups. */
  private static List<String> strings(Attributes dcm, Attributes shared, int tag, int group) {
    List<String> root = strings(dcm, tag);
    if (!root.isEmpty() || shared == null) {
      return root;
    }
    return strings(shared.getNestedDataset(group), tag);
  }

  private static Double decimal(Attributes dcm, Attributes shared, int tag, int group) {
    Double value = decimal(dcm, tag);
    if (value == null && shared != null) {
      value = decimal(shared.getNestedDataset(group), tag);
    }
    return value;
  }

  private static Double decimal(Attributes dcm, int tag) {
    if (dcm == null || !dcm.containsValue(tag)) {
      return null;
    }
    try {
      return dcm.getDouble(tag, Double.NaN);
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static Set<String> upper(Collection<String> values) {
    return values.stream()
        .map(v -> v.trim().toUpperCase(Locale.ROOT))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  @SuppressWarnings("unused")
  private static <T> Function<T, T> identity() {
    return Function.identity();
  }
}
