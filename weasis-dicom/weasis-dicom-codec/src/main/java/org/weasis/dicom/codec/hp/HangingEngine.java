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

import jakarta.json.JsonObject;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.hp.HangingProtocolDocuments;
import org.weasis.core.api.util.LayeredDocument;
import org.weasis.core.api.util.LayeredEntries.Origin;
import org.weasis.core.ui.editor.image.DefaultView2d.ZoomType;
import org.weasis.core.ui.editor.image.HangingLayout;
import org.weasis.core.ui.editor.image.Justification;
import org.weasis.core.ui.editor.image.ViewState;
import org.weasis.core.ui.editor.image.ViewState.ViewData;
import org.weasis.core.ui.editor.image.ViewState.ViewPresentation;
import org.weasis.dicom.codec.hp.HangingPlan.Alternative;
import org.weasis.dicom.codec.hp.HangingPlan.CellPlan;
import org.weasis.dicom.codec.hp.HangingPlan.Match;
import org.weasis.dicom.codec.hp.HangingPlan.Step;
import org.weasis.dicom.codec.hp.HangingPlan.Unresolved;
import org.weasis.dicom.codec.hp.ProtocolJson.Document;
import org.weasis.dicom.codec.hp.WeasisExtensions.Display;
import org.weasis.dicom.codec.hp.WeasisExtensions.DisplaySet;
import org.weasis.dicom.codec.hp.WeasisExtensions.ImageSet;
import org.weasis.dicom.codec.hp.WeasisExtensions.ViewerType;
import org.weasis.dicom.hp.AbstractPriorValue;
import org.weasis.dicom.hp.HPComparator;
import org.weasis.dicom.hp.HPDefinition;
import org.weasis.dicom.hp.HPDisplaySet;
import org.weasis.dicom.hp.HPImageBox;
import org.weasis.dicom.hp.HPImageSet;
import org.weasis.dicom.hp.HPScrollingGroup;
import org.weasis.dicom.hp.HPSelector;
import org.weasis.dicom.hp.HPTimeBasedImageSet;
import org.weasis.dicom.hp.HangingProtocol;
import org.weasis.dicom.hp.RelativeTime;
import org.weasis.dicom.hp.enums.ImageBoxLayoutType;
import org.weasis.dicom.macro.Code;

/**
 * Matches hanging protocols against the series of a study, in the three steps of the specification,
 * and produces a {@link HangingPlan}:
 *
 * <ol>
 *   <li><b>Protocol selection</b>: the candidates are the enabled protocols with a definition that
 *       applies to a series of the study (modality, anatomy, laterality); each one is scored by the
 *       specificity of that definition, the share of its current image sets that resolve and its
 *       level (user over group over site over built-in).
 *   <li><b>Image set resolution</b>: the DICOM selectors ({@code MATCH} / {@code NO_MATCH}) and the
 *       feature filters are hard; the feature preferences rank the series that pass, so the engine
 *       proposes the best candidate instead of failing when several match.
 *   <li><b>Display set filling</b>: each display set of a presentation group takes the best series
 *       of its image set that no other display set of the group shows; an empty display set keeps
 *       its cell as a placeholder ({@code MAINTAIN_LAYOUT}) or is removed ({@code ADAPT_LAYOUT}).
 * </ol>
 *
 * <p>The time-based image sets bind each image set to one study of the patient: the current study,
 * or a {@link PriorStudy} chosen by a relative time window from the current study date or by an
 * abstract prior rank; within the studies a set accepts, the most recent one with a matching series
 * wins.
 *
 * <p>A display set with a Display Set Patient Orientation gets the rotation and flip that turn the
 * Patient Orientation of its first image into the requested one (e.g. mirrored mammography views).
 */
public final class HangingEngine {

  private static final Logger LOGGER = LoggerFactory.getLogger(HangingEngine.class);

  /** The key of a protocol entry that disables it; absent means enabled. */
  public static final String ENABLED = "enabled"; // NON-NLS

  /** The reason of a prior image set when the patient has no prior study the set accepts. */
  static final String NO_PRIOR = "no prior study of the patient fits"; // NON-NLS

  /** A protocol the engine may apply, with the layer it comes from. */
  public record Candidate(Document document, Origin origin, boolean enabled) {
    public Candidate {
      Objects.requireNonNull(document, "document");
    }

    public static Candidate of(Document document) {
      return new Candidate(document, Origin.BUILT_IN, true);
    }
  }

  /** A protocol that applies to the study, with its score. */
  public record ProtocolScore(Candidate candidate, double score, List<String> reasons) {
    public ProtocolScore {
      reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public Document document() {
      return candidate.document();
    }
  }

  /** The score of one series for one image set. */
  record Scored(StudySeries series, double score, Match match, List<String> reasons) {}

  /**
   * The candidates of one image set, best first, or why there are none.
   *
   * @param prior the prior study the candidates come from, null for the current study
   */
  record Resolution(
      HPImageSet imageSet, List<Scored> candidates, String unresolved, PriorStudy prior) {
    Resolution(HPImageSet imageSet, List<Scored> candidates, String unresolved) {
      this(imageSet, candidates, unresolved, null);
    }

    boolean isResolved() {
      return !candidates.isEmpty();
    }
  }

  private HangingEngine() {}

  // ---- protocols ---------------------------------------------------------------------------

  /** The protocols of every layer of {@link HangingProtocolDocuments#protocols()}. */
  public static List<Candidate> candidates() {
    return candidates(HangingProtocolDocuments.protocols());
  }

  static List<Candidate> candidates(LayeredDocument document) {
    List<Candidate> list = new ArrayList<>();
    for (LayeredDocument.Entry entry : document.entries()) {
      JsonObject json = entry.json();
      try {
        list.add(
            new Candidate(
                ProtocolJson.fromJson(json), entry.origin(), json.getBoolean(ENABLED, true)));
      } catch (RuntimeException e) {
        LOGGER.warn("Protocol {} is not readable: {}", entry.id(), e.getMessage());
      }
    }
    return list;
  }

  // ---- step 1: protocol selection ----------------------------------------------------------

  /**
   * The enabled protocols that apply to the study, best first. A protocol applies when one of its
   * definitions holds for a series of the study; a protocol without definition applies to
   * everything with the lowest specificity.
   */
  public static List<ProtocolScore> select(List<Candidate> protocols, List<StudySeries> study) {
    return select(protocols, study, List.of());
  }

  /**
   * The enabled protocols that apply to the study, best first, the time-based image sets drawing
   * from the given priors of the patient.
   *
   * @param priors the prior studies of the patient, most recent first (see {@link PriorStudy#of})
   */
  public static List<ProtocolScore> select(
      List<Candidate> protocols, List<StudySeries> study, List<PriorStudy> priors) {
    List<ProtocolScore> scores = new ArrayList<>();
    for (Candidate candidate : protocols) {
      if (!candidate.enabled()) {
        continue;
      }
      score(candidate, study, priors).ifPresent(scores::add);
    }
    scores.sort(Comparator.comparingDouble(ProtocolScore::score).reversed());
    return scores;
  }

  static Optional<ProtocolScore> score(
      Candidate candidate, List<StudySeries> study, List<PriorStudy> priors) {
    HangingProtocol protocol = candidate.document().protocol();
    List<String> reasons = new ArrayList<>();
    double specificity = -1;
    List<HPDefinition> definitions = protocol.getHangingProtocolDefinitions();
    boolean lastResort = definitions.isEmpty();
    if (lastResort) {
      specificity = 0;
      reasons.add("no definition: applies to every study, as a last resort"); // NON-NLS
    } else {
      for (HPDefinition definition : definitions) {
        if (study.stream().anyMatch(s -> definition.appliesTo(s.dataset()))) {
          double value = specificity(definition);
          if (value > specificity) {
            specificity = value;
          }
        }
      }
      if (specificity < 0) {
        return Optional.empty();
      }
      reasons.add("definition applies, specificity " + (int) specificity); // NON-NLS
    }

    Map<Integer, Resolution> resolutions = resolveImageSets(candidate.document(), study, priors);
    long current = 0;
    long resolved = 0;
    long priorSets = 0;
    long priorsResolved = 0;
    for (Resolution r : resolutions.values()) {
      if (isCurrent(r.imageSet())) {
        current++;
        resolved += r.isResolved() ? 1 : 0;
      } else {
        priorSets++;
        priorsResolved += r.isResolved() ? 1 : 0;
      }
    }
    if (current > 0 && resolved == 0) {
      return Optional.empty(); // it would hang nothing
    }
    double share = current == 0 ? 1 : (double) resolved / current;
    reasons.add(resolved + " of " + current + " image sets resolve"); // NON-NLS
    // available priors favour a comparison protocol; missing ones cost nothing
    double priorBonus = priorSets == 0 ? 0 : 0.5 * priorsResolved / priorSets;
    if (priorSets > 0) {
      reasons.add(priorsResolved + " of " + priorSets + " prior image sets resolve"); // NON-NLS
    }
    double levelBonus = levelBonus(candidate.origin(), protocol.getHangingProtocolLevel());
    if (levelBonus > 0) {
      reasons.add("level " + levelName(candidate.origin(), protocol)); // NON-NLS
    }
    Integer screens = protocol.getNumberOfScreens();
    // a last resort scores below any protocol whose definition applies (specificity >= 1)
    double score =
        (lastResort ? share : specificity + 3 * share)
            + priorBonus
            + levelBonus
            - (screens != null && screens > 1 ? 0.05 : 0);
    return Optional.of(new ProtocolScore(candidate, score, reasons));
  }

  /** Modality and anatomy weigh two, laterality and procedure codes one. */
  static double specificity(HPDefinition definition) {
    double value = 0;
    if (hasText(definition.getModality())) {
      value += 2;
    }
    if (!definition.getAnatomicRegionCode().isEmpty()) {
      value += 2;
    }
    if (hasText(definition.getLaterality())) {
      value += 1;
    }
    if (!definition.getProcedureCodes().isEmpty()) {
      value += 1;
    }
    return value;
  }

  private static double levelBonus(Origin origin, String level) {
    if (origin == Origin.USER) {
      return 0.3;
    }
    if (origin == Origin.SITE) {
      return "USER_GROUP".equalsIgnoreCase(level) ? 0.2 : 0.1; // NON-NLS
    }
    return 0;
  }

  private static String levelName(Origin origin, HangingProtocol protocol) {
    String level = protocol.getHangingProtocolLevel();
    return hasText(level) ? level.toLowerCase(Locale.ROOT) : origin.name().toLowerCase(Locale.ROOT);
  }

  // ---- step 2: image set resolution ----------------------------------------------------------

  /** The candidates of every image set of the protocol, by Image Set Number. */
  static Map<Integer, Resolution> resolveImageSets(
      Document document, List<StudySeries> study, List<PriorStudy> priors) {
    Map<Integer, Resolution> result = new LinkedHashMap<>();
    for (HPImageSet imageSet : document.protocol().getImageSets()) {
      int number = imageSet.getImageSetNumber();
      ImageSet extension = document.weasis().imageSets().get(number);
      result.put(number, resolve(imageSet, extension, study, priors));
    }
    return result;
  }

  /**
   * Resolves an image set on the studies its time-based items accept, the most recent first: the
   * first study with a matching series binds the set.
   */
  static Resolution resolve(
      HPImageSet imageSet, ImageSet extension, List<StudySeries> study, List<PriorStudy> priors) {
    if (isCurrent(imageSet)) {
      return resolve(imageSet, extension, study);
    }
    LocalDateTime now = PriorStudy.dateOf(study);
    List<Attributes> items = timeBasedItems(imageSet);
    if (items.stream().anyMatch(HangingEngine::acceptsCurrent)) {
      Resolution resolution = resolve(imageSet, extension, study);
      if (resolution.isResolved()) {
        return resolution;
      }
    }
    String unresolved = NO_PRIOR;
    for (PriorStudy prior : priors) {
      if (items.stream().noneMatch(i -> accepts(i, prior, priors.size(), now))) {
        continue;
      }
      Resolution resolution = resolve(imageSet, extension, prior.series());
      if (resolution.isResolved()) {
        return new Resolution(imageSet, resolution.candidates(), null, prior);
      }
      unresolved =
          resolution.unresolved()
              + " in the prior of " // NON-NLS
              + (prior.date() == null ? prior.uid() : prior.date().toLocalDate());
    }
    return new Resolution(imageSet, List.of(), unresolved);
  }

  /** Whether a time-based item accepts the current study (a window starting at 0). */
  private static boolean acceptsCurrent(Attributes item) {
    HPTimeBasedImageSet timeBased = new HPTimeBasedImageSet(item);
    if (timeBased.hasAbstractPriorValue() || timeBased.hasAbstractPriorCode()) {
      return false;
    }
    // Relative Time (0072,0038) is US: 0 as the start of the window means now
    return !timeBased.hasRelativeTime() || item.getInt(Tag.RelativeTime, 0) == 0;
  }

  /**
   * Whether a time-based item accepts a prior: an Abstract Prior Value range of ranks (1 the most
   * recent prior, -1 the oldest), an Abstract Prior Code read by its meaning (most recent, oldest
   * or first, else any prior), or a Relative Time window before the current study date.
   */
  static boolean accepts(Attributes item, PriorStudy prior, int count, LocalDateTime now) {
    HPTimeBasedImageSet timeBased = new HPTimeBasedImageSet(item);
    if (timeBased.hasAbstractPriorValue()) {
      AbstractPriorValue value = timeBased.getAbstractPriorValue();
      int start = absoluteRank(value.getStart(), count);
      int end = absoluteRank(value.getEnd(), count);
      return prior.rank() >= Math.min(start, end) && prior.rank() <= Math.max(start, end);
    }
    if (timeBased.hasAbstractPriorCode()) {
      Code code = timeBased.getAbstractPriorCode();
      String meaning =
          code == null || code.getCodeMeaning() == null
              ? ""
              : code.getCodeMeaning().toLowerCase(Locale.ROOT);
      if (meaning.contains("recent")) { // NON-NLS
        return prior.rank() == 1;
      }
      if (meaning.contains("oldest") || meaning.contains("first")) { // NON-NLS
        return prior.rank() == count;
      }
      return true;
    }
    if (timeBased.hasRelativeTime()) {
      if (now == null || prior.date() == null) {
        return false;
      }
      RelativeTime window;
      try {
        window = timeBased.getRelativeTime();
      } catch (RuntimeException e) {
        return false; // no or unknown Relative Time Units
      }
      ChronoUnit unit =
          window.getUnits() == null ? ChronoUnit.DAYS : window.getUnits().getChronoUnit();
      LocalDateTime latest = now.minus(window.getStart(), unit);
      LocalDateTime earliest = now.minus(window.getEnd(), unit);
      return !prior.date().isAfter(latest) && !prior.date().isBefore(earliest);
    }
    return false;
  }

  /** A rank from the most recent prior (1); a negative rank counts from the oldest (-1). */
  private static int absoluteRank(int rank, int count) {
    return rank < 0 ? count + 1 + rank : rank;
  }

  static Resolution resolve(HPImageSet imageSet, ImageSet extension, List<StudySeries> study) {
    List<FeatureSelector> selectors = extension == null ? List.of() : extension.selectors();
    List<FeatureSelector> preferences = selectors.stream().filter(s -> !s.isFilter()).toList();

    List<StudySeries> passing = new ArrayList<>();
    Map<StudySeries, List<String>> reasons = new HashMap<>();
    for (StudySeries series : study) {
      List<String> why = new ArrayList<>();
      if (passesDicomSelectors(imageSet, series, why) && passesFilters(selectors, series, why)) {
        passing.add(series);
        reasons.put(series, why);
      }
    }
    if (passing.isEmpty()) {
      return new Resolution(
          imageSet, List.of(), "no series matches " + describe(imageSet, extension)); // NON-NLS
    }

    Map<StudySeries, Double> preferenceScore = new HashMap<>();
    Map<StudySeries, Integer> satisfied = new HashMap<>();
    for (StudySeries series : passing) {
      preferenceScore.put(series, 0.0);
      satisfied.put(series, 0);
    }
    for (FeatureSelector preference : preferences) {
      if (preference.isNumeric()) {
        rankNumeric(preference, passing, preferenceScore, satisfied, reasons);
      } else {
        for (StudySeries series : passing) {
          if (preference.accepts(series.features())) {
            preferenceScore.merge(series, preference.weight(), Double::sum);
            satisfied.merge(series, 1, Integer::sum);
            reasons.get(series).add(preference.describe());
          }
        }
      }
    }

    List<Scored> scored = new ArrayList<>();
    for (StudySeries series : passing) {
      int hits = satisfied.get(series);
      Match match =
          preferences.isEmpty() || hits == preferences.size()
              ? Match.EXACT
              : hits > 0 ? Match.PREFERRED : Match.FALLBACK;
      double tieBreak =
          (FeatureSelector.isOriginal(series.features()) ? 0.01 : 0)
              + Math.min(0.009, series.imageCount() * 1e-5);
      scored.add(
          new Scored(series, preferenceScore.get(series) + tieBreak, match, reasons.get(series)));
    }
    scored.sort(Comparator.comparingDouble(Scored::score).reversed());
    return new Resolution(imageSet, scored, null);
  }

  /** A numeric preference gives its weight to the best candidate and a share to the others. */
  private static void rankNumeric(
      FeatureSelector preference,
      List<StudySeries> passing,
      Map<StudySeries, Double> scores,
      Map<StudySeries, Integer> satisfied,
      Map<StudySeries, List<String>> reasons) {
    List<StudySeries> measured =
        passing.stream().filter(s -> preference.number(s.features()).isPresent()).toList();
    if (measured.isEmpty()) {
      return;
    }
    Comparator<StudySeries> order =
        Comparator.comparingDouble(s -> preference.number(s.features()).orElse(0.0));
    if (preference.prefer() == FeatureSelector.Extreme.MAX) {
      order = order.reversed();
    }
    List<StudySeries> ranked = measured.stream().sorted(order).toList();
    double best = preference.number(ranked.getFirst().features()).orElse(0.0);
    for (int rank = 0; rank < ranked.size(); rank++) {
      StudySeries series = ranked.get(rank);
      double value = preference.number(series.features()).orElse(0.0);
      double share = value == best ? 1 : 1 - (double) rank / ranked.size();
      scores.merge(series, preference.weight() * share, Double::sum);
      if (value == best) {
        satisfied.merge(series, 1, Integer::sum);
        reasons.get(series).add(preference.describe() + ": " + value); // NON-NLS
      }
    }
  }

  /** The DICOM selectors, each honoured by its usage flag. */
  static boolean passesDicomSelectors(HPImageSet imageSet, StudySeries series, List<String> why) {
    for (HPSelector selector : imageSet.getImageSetSelectors()) {
      boolean matches;
      try {
        matches = selector.matches(series.dataset(), 0);
      } catch (RuntimeException e) {
        matches = false;
      }
      boolean exclude =
          "NO_MATCH".equalsIgnoreCase(selector.getImageSetSelectorUsageFlag()); // NON-NLS
      if (matches == exclude) {
        return false;
      }
      why.add((exclude ? "not " : "") + describe(selector)); // NON-NLS
    }
    return true;
  }

  static boolean passesFilters(
      List<FeatureSelector> selectors, StudySeries series, List<String> why) {
    for (FeatureSelector selector : selectors) {
      if (selector.isFilter()) {
        if (!selector.passes(series.features())) {
          return false;
        }
        why.add(selector.describe());
      }
    }
    return true;
  }

  /**
   * The time-based items of an image set: its sequence, or, for a set the model read from a dataset
   * (weasis-dicom-tools 5.35.1 builds it on the time-based item itself), its own attributes.
   */
  public static List<Attributes> timeBasedItems(HPImageSet imageSet) {
    Sequence items = imageSet.getTimeBasedImageSetsSequence();
    if (items != null && !items.isEmpty()) {
      List<Attributes> list = new ArrayList<>();
      items.forEach(list::add);
      return list;
    }
    Attributes own = imageSet.getAttributes();
    return own != null && own.contains(Tag.ImageSetSelectorCategory) ? List.of(own) : List.of();
  }

  /** Current unless every time-based item of the set is a prior. */
  static boolean isCurrent(HPImageSet imageSet) {
    List<Attributes> items = timeBasedItems(imageSet);
    if (items.isEmpty()) {
      return true;
    }
    for (Attributes item : items) {
      HPTimeBasedImageSet timeBased = new HPTimeBasedImageSet(item);
      if (timeBased.hasAbstractPriorValue() || timeBased.hasAbstractPriorCode()) {
        continue;
      }
      if (!timeBased.hasRelativeTime() || timeBased.getRelativeTime().isCurrentTime()) {
        return true;
      }
    }
    return false;
  }

  private static String describe(HPSelector selector) {
    Attributes a = selector.getAttributes();
    Integer tag = selector.getSelectorAttribute();
    if (tag == null) {
      return "selector"; // NON-NLS
    }
    String keyword = org.dcm4che3.data.ElementDictionary.keywordOf(tag, null);
    String[] values = null;
    for (int t :
        new int[] {
          Tag.SelectorCSValue,
          Tag.SelectorLOValue,
          Tag.SelectorSHValue,
          Tag.SelectorUIValue,
          Tag.SelectorISValue,
          Tag.SelectorDSValue
        }) {
      if (a.contains(t)) {
        values = a.getStrings(t);
        break;
      }
    }
    return keyword + (values == null ? "" : " in " + List.of(values)); // NON-NLS
  }

  private static String describe(HPImageSet imageSet, ImageSet extension) {
    if (extension != null && extension.label() != null) {
      return extension.label();
    }
    List<Attributes> items = timeBasedItems(imageSet);
    if (!items.isEmpty()) {
      String label = new HPTimeBasedImageSet(items.getFirst()).getImageSetLabel();
      if (hasText(label)) {
        return label;
      }
    }
    return "image set " + imageSet.getImageSetNumber(); // NON-NLS
  }

  // ---- step 3: display set filling -----------------------------------------------------------

  /** Matches the best protocol and plans it, empty when no protocol applies. */
  public static Optional<HangingPlan> match(List<Candidate> protocols, List<StudySeries> study) {
    return match(protocols, study, List.of());
  }

  /** Matches the best protocol with the priors of the patient and plans it. */
  public static Optional<HangingPlan> match(
      List<Candidate> protocols, List<StudySeries> study, List<PriorStudy> priors) {
    List<ProtocolScore> scores = select(protocols, study, priors);
    if (scores.isEmpty()) {
      return Optional.empty();
    }
    ProtocolScore best = scores.getFirst();
    return Optional.of(plan(best, study, priors));
  }

  /** The plan of a scored protocol. */
  public static HangingPlan plan(ProtocolScore scored, List<StudySeries> study) {
    return plan(scored, study, List.of());
  }

  /** The plan of a scored protocol, the prior image sets drawing from the given priors. */
  public static HangingPlan plan(
      ProtocolScore scored, List<StudySeries> study, List<PriorStudy> priors) {
    return plan(scored.document(), study, priors, scored.score(), scored.reasons());
  }

  /** The plan of a protocol, whether it applies to the study or not. */
  public static HangingPlan plan(Document document, List<StudySeries> study) {
    return plan(document, study, List.of());
  }

  /** The plan of a protocol with the priors of the patient, whether it applies or not. */
  public static HangingPlan plan(
      Document document, List<StudySeries> study, List<PriorStudy> priors) {
    return plan(document, study, priors, 0, List.of());
  }

  private static HangingPlan plan(
      Document document,
      List<StudySeries> study,
      List<PriorStudy> priors,
      double score,
      List<String> reasons) {
    HangingProtocol protocol = document.protocol();
    Map<Integer, Resolution> resolutions = resolveImageSets(document, study, priors);
    boolean adapt =
        "ADAPT_LAYOUT".equalsIgnoreCase(protocol.getPartialDataDisplayHandling()); // NON-NLS
    Set<HPDisplaySet> scrolled = new HashSet<>();
    for (HPScrollingGroup group : protocol.getScrollingGroups()) {
      scrolled.addAll(group.getDisplaySets());
    }

    TreeMap<Integer, List<HPDisplaySet>> groups = new TreeMap<>();
    for (HPDisplaySet displaySet : protocol.getDisplaySets()) {
      Integer group = displaySet.getDisplaySetPresentationGroup();
      groups.computeIfAbsent(group == null ? 1 : group, g -> new ArrayList<>()).add(displaySet);
    }

    List<Step> steps = new ArrayList<>();
    for (Map.Entry<Integer, List<HPDisplaySet>> entry : groups.entrySet()) {
      Step step = step(document, entry.getKey(), entry.getValue(), resolutions, adapt, scrolled);
      if (adapt && step.cells().isEmpty()) {
        continue; // ADAPT_LAYOUT: a step with nothing to show is skipped
      }
      steps.add(step);
    }

    List<Unresolved> unresolved = new ArrayList<>();
    resolutions.forEach(
        (number, resolution) -> {
          if (!resolution.isResolved()) {
            unresolved.add(
                new Unresolved(
                    number,
                    describe(resolution.imageSet(), document.weasis().imageSets().get(number)),
                    resolution.unresolved()));
          }
        });
    return new HangingPlan(
        document.id(),
        protocol.getHangingProtocolName(),
        protocol.getHangingProtocolLevel(),
        score,
        reasons,
        steps,
        unresolved);
  }

  private static Step step(
      Document document,
      int group,
      List<HPDisplaySet> displaySets,
      Map<Integer, Resolution> resolutions,
      boolean adapt,
      Set<HPDisplaySet> scrolled) {
    HangingProtocol protocol = document.protocol();
    Set<StudySeries> used = new HashSet<>();
    List<CellPlan> cells = new ArrayList<>();
    HangingLayout.Builder layout = HangingLayout.builder();
    String synch = null;
    for (HPDisplaySet displaySet : displaySets) {
      HPImageSet imageSet = displaySet.getImageSet();
      int imageSetNumber = imageSet == null ? -1 : imageSet.getImageSetNumber();
      Resolution resolution = resolutions.get(imageSetNumber);
      int displaySetNumber =
          displaySet.getDisplaySetNumber() == null
              ? cells.size() + 1
              : displaySet.getDisplaySetNumber();
      DisplaySet extension = document.weasis().displaySets().get(displaySetNumber);
      ViewerType viewer = viewerOf(displaySet, extension);
      String label = displaySet.getDisplaySetLabel();
      if (synch == null) {
        synch = synchOf(displaySet, extension, scrolled);
      }

      if (resolution == null || !resolution.isResolved()) {
        String expected = resolution == null ? "no image set" : resolution.unresolved(); // NON-NLS
        if (adapt) {
          continue; // ADAPT_LAYOUT removes the cell and the grid re-flows
        }
        addCell(
            cells,
            layout,
            displaySet,
            extension,
            new CellPlan(
                cells.size(),
                displaySetNumber,
                label,
                imageSetNumber,
                null,
                Match.EMPTY,
                0,
                viewer,
                List.of("expected: " + expected), // NON-NLS
                List.of(),
                null,
                0),
            null);
        continue;
      }

      List<Scored> fresh =
          resolution.candidates().stream().filter(c -> !used.contains(c.series())).toList();
      Integer repeat = extension == null ? null : extension.repeat();
      int max = repeat == null ? 1 : repeat <= 0 ? Integer.MAX_VALUE : repeat;
      if (fresh.isEmpty()) {
        if (adapt) {
          continue; // nothing new to show: ADAPT_LAYOUT drops the cell
        }
        // every candidate is already shown by this step: show the best one again
        Scored shared = resolution.candidates().getFirst();
        List<String> reasons = new ArrayList<>();
        reasons.add("already shown by another cell of this step"); // NON-NLS
        reasons.addAll(shared.reasons());
        addCell(
            cells,
            layout,
            displaySet,
            extension,
            new CellPlan(
                cells.size(),
                displaySetNumber,
                label,
                imageSetNumber,
                shared.series().uid(),
                Match.SHARED,
                shared.score(),
                viewer,
                reasons,
                alternatives(resolution, shared),
                studyOf(shared.series(), resolution),
                rankOf(resolution)),
            shared.series());
        continue;
      }
      int shown = 0;
      for (Scored chosen : fresh) {
        if (shown++ >= max) {
          break;
        }
        used.add(chosen.series());
        addCell(
            cells,
            layout,
            displaySet,
            extension,
            new CellPlan(
                cells.size(),
                displaySetNumber,
                label,
                imageSetNumber,
                chosen.series().uid(),
                chosen.match(),
                chosen.score(),
                viewer,
                chosen.reasons(),
                alternatives(resolution, chosen),
                studyOf(chosen.series(), resolution),
                rankOf(resolution)),
            chosen.series());
      }
    }
    layout.grid(gridFor(cells.size())).synch(synch).select(cells.isEmpty() ? 0 : 1);
    return new Step(
        group, protocol.getDisplaySetPresentationGroupDescription(group), layout.build(), cells);
  }

  /** Appends the plan of a cell and the matching layout cell with its presentation. */
  private static void addCell(
      List<CellPlan> cells,
      HangingLayout.Builder layout,
      HPDisplaySet displaySet,
      DisplaySet extension,
      CellPlan cell,
      StudySeries series) {
    layout.cell(
        new HangingLayout.Cell(
            cell.cell(),
            tiling(displaySet),
            List.of(viewState(cell.series(), series, displaySet, extension))));
    cells.add(cell);
  }

  /** The Study Instance UID of a cell's series, from its dataset or the prior it comes from. */
  private static String studyOf(StudySeries series, Resolution resolution) {
    String uid = series.dataset().getString(Tag.StudyInstanceUID);
    if (uid == null && resolution.prior() != null) {
      uid = resolution.prior().uid();
    }
    return uid;
  }

  private static int rankOf(Resolution resolution) {
    return resolution.prior() == null ? 0 : resolution.prior().rank();
  }

  /** The other candidates of the image set, best first. */
  private static List<Alternative> alternatives(Resolution resolution, Scored chosen) {
    List<Alternative> alternatives = new ArrayList<>();
    for (Scored candidate : resolution.candidates()) {
      if (candidate != chosen) {
        alternatives.add(
            new Alternative(
                candidate.series().uid(), candidate.score(), candidate.series().describe()));
      }
    }
    return alternatives;
  }

  private static ViewerType viewerOf(HPDisplaySet displaySet, DisplaySet extension) {
    if (extension != null && extension.viewerType() != null) {
      return extension.viewerType();
    }
    String reformatting = displaySet.getReformattingOperationType();
    if ("MPR".equalsIgnoreCase(reformatting)) { // NON-NLS
      return ViewerType.MPR;
    }
    if ("3D_RENDERING".equalsIgnoreCase(reformatting)) { // NON-NLS
      return ViewerType.VOLUME_3D;
    }
    return ViewerType.VIEWER_2D;
  }

  private static HangingLayout.Tiling tiling(HPDisplaySet displaySet) {
    List<HPImageBox> boxes = displaySet.getImageBoxes();
    if (boxes.isEmpty()) {
      return null;
    }
    HPImageBox box = boxes.getFirst();
    // the typed getter refuses an absent Image Box Layout Type (type 1 in DICOM, lenient here)
    String type = box.getAttributes().getString(Tag.ImageBoxLayoutType);
    if (!ImageBoxLayoutType.TILED.name().equalsIgnoreCase(type)) {
      return null;
    }
    Integer cols = box.getImageBoxTileHorizontalDimension();
    Integer rows = box.getImageBoxTileVerticalDimension();
    int c = cols == null ? 1 : cols;
    int r = rows == null ? 1 : rows;
    return c == 1 && r == 1 ? null : new HangingLayout.Tiling(c, r);
  }

  /** The presentation of a cell: the display state by reference and the DICOM intent. */
  static ViewState viewState(String seriesUid, HPDisplaySet displaySet, DisplaySet extension) {
    return viewState(seriesUid, null, displaySet, extension);
  }

  /**
   * The presentation of a cell, with the rotation and flip that give the first image of the series
   * (may be null) the Display Set Patient Orientation; one view of a multi-view series starts on
   * its own image.
   */
  static ViewState viewState(
      String seriesUid, StudySeries series, HPDisplaySet displaySet, DisplaySet extension) {
    Attributes dataset = series == null ? null : series.dataset();
    Display display = extension == null ? null : extension.display();
    String sort = null;
    boolean inverse = false;
    List<HPComparator> sorting = displaySet.getSortingOperations();
    if (!sorting.isEmpty()) {
      Attributes a = sorting.getFirst().getAttributes();
      String category = a.getString(Tag.SortByCategory);
      Integer tag = sorting.getFirst().getSelectorAttribute();
      if ("ALONG_AXIS".equalsIgnoreCase(category)) { // NON-NLS
        sort = "slice-position"; // NON-NLS
      } else if ("BY_ACQ_TIME".equalsIgnoreCase(category)) { // NON-NLS
        sort = "acquisition-time"; // NON-NLS
      } else if (tag != null && tag == Tag.InstanceNumber) {
        sort = "instance"; // NON-NLS
      } else if (tag != null && tag == Tag.ContentTime) {
        sort = "content-time"; // NON-NLS
      } else if (tag != null && tag == Tag.DiffusionBValue) {
        sort = "b-value"; // NON-NLS
      }
      inverse = "DECREASING".equalsIgnoreCase(a.getString(Tag.SortingDirection)); // NON-NLS
    }
    ZoomType zoomType = ZoomType.BEST_FIT;
    if (display != null && display.zoomMode() != null) {
      try {
        zoomType = ZoomType.valueOf(display.zoomMode().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
        zoomType = ZoomType.BEST_FIT;
      }
    } else if ("Y".equalsIgnoreCase(displaySet.getShowImageTrueSizeFlag())) { // NON-NLS
      zoomType = ZoomType.REAL;
    }
    DisplayOrientation.Framing framing =
        dataset == null
            ? DisplayOrientation.Framing.NONE
            : DisplayOrientation.framing(
                dataset.getStrings(Tag.PatientOrientation),
                displaySet.getAttributes().getStrings(Tag.DisplaySetPatientOrientation));
    ViewPresentation view =
        new ViewPresentation(
            null,
            null,
            zoomType,
            0,
            0,
            0,
            framing.rotation(),
            framing.flip(),
            null,
            null,
            display == null ? null : display.preset(),
            display == null ? null : display.lutShape(),
            display == null ? null : display.colorMap(),
            "Y".equalsIgnoreCase(displaySet.getShowGrayscaleInverted()), // NON-NLS
            sort,
            inverse,
            Justification.of(
                displaySet.getAttributes().getString(Tag.DisplaySetHorizontalJustification),
                displaySet.getAttributes().getString(Tag.DisplaySetVerticalJustification)),
            display == null ? null : display.scaleGroup());
    ViewData data =
        seriesUid == null
            ? ViewData.NONE
            : series != null && series.isView()
                ? new ViewData(seriesUid, series.image(), series.instance(), null)
                : new ViewData(seriesUid, 0, null, null);
    return new ViewState(data, view);
  }

  private static String synchOf(
      HPDisplaySet displaySet, DisplaySet extension, Set<HPDisplaySet> scrolled) {
    if (extension != null && extension.display() != null && extension.display().synch() != null) {
      return extension.display().synch();
    }
    return scrolled.contains(displaySet) ? "stack" : null; // NON-NLS
  }

  /** The grid id of the 2D viewer for that many cells, rows first. */
  public static String gridFor(int cells) {
    if (cells <= 1) {
      return "1x1"; // NON-NLS
    }
    if (cells == 2) {
      return "1x2"; // NON-NLS
    }
    if (cells == 3) {
      return "1x3"; // NON-NLS
    }
    if (cells == 4) {
      return "2x2"; // NON-NLS
    }
    if (cells <= 6) {
      return "2x3"; // NON-NLS
    }
    if (cells <= 8) {
      return "2x4"; // NON-NLS
    }
    int rows = (int) Math.ceil(Math.sqrt(cells));
    int cols = (int) Math.ceil((double) cells / rows);
    return rows + "x" + cols; // NON-NLS
  }

  private static boolean hasText(String text) {
    return text != null && !text.isBlank();
  }
}
