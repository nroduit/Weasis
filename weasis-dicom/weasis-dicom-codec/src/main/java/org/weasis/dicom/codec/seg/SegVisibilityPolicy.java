/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.seg;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.HiddenSpecialElement;
import org.weasis.dicom.codec.TagD;

/**
 * Decides whether a segmentation starts hidden. Sole owner of that decision, so the three reasons a
 * SEG may not be shown on load are stated — and documented — in one place:
 *
 * <ol>
 *   <li>{@link #HIDE_ALL} — the site turned every segmentation off by default;
 *   <li>{@link #HIDE_KEYWORDS} — the segmentation describes itself as something nobody wants to see
 *       over the anatomy (table removal, patient support, …);
 *   <li>{@link #HIDE_COUNT} — the patient holds, or a transfer {@link #announce announced}, so many
 *       segmentations that showing them together paints them on top of each other.
 * </ol>
 *
 * <p>Any single rule is enough to hide. The result is only a <em>default</em>: {@link
 * org.weasis.dicom.codec.SpecialElementRegion#setVisible} always wins, and the Segmentation tool
 * lists hidden segmentations unchecked so the user can turn on the ones of interest.
 */
public final class SegVisibilityPolicy {

  /** Hides every segmentation on load, whatever it contains. */
  public static final String HIDE_ALL = "weasis.dicom.seg.hide.all"; // NON-NLS

  /**
   * Comma-separated, case-insensitive keywords marking a segmentation as unwanted over the anatomy.
   * An empty value disables the rule.
   */
  public static final String HIDE_KEYWORDS = "weasis.dicom.seg.hide.keywords"; // NON-NLS

  public static final String DEFAULT_HIDE_KEYWORDS =
      "table removal, table segmentation, tabletop, couch, patient support, bed removal"; // NON-NLS

  /**
   * Number of segmentations the patient holds — or is {@link #announce announced} to hold — from
   * which they all start hidden, exactly as {@link #HIDE_ALL} does. {@code 0} disables the rule.
   */
  public static final String HIDE_COUNT = "weasis.dicom.seg.hide.count"; // NON-NLS

  public static final int DEFAULT_HIDE_COUNT = 3;

  /**
   * Segmentation series a transfer announced but has not delivered yet, keyed by patient then by
   * series so re-reading a manifest replaces its own entry rather than adding to it.
   */
  private static final Map<String, Map<String, AnnouncedSegSeries>> ANNOUNCED =
      new ConcurrentHashMap<>();

  /** What a transfer promises for one segmentation series. */
  record AnnouncedSegSeries(String description, int instances) {}

  /**
   * Bumped whenever an input of {@link #isHiddenByDefault} may have changed: a segmentation
   * registered with or dropped from its series, one finished parsing — its segment labels only
   * become matchable then — or the preferences were edited.
   *
   * <p>Segmentations cache their computed default against this value instead of settling on their
   * first answer. Both rules read every segmentation loaded: the files register one by one while
   * the study loads, so one asked early sees fewer siblings than one asked late, and without this
   * the same study would end up with some files hidden and others not purely by load order.
   */
  private static final AtomicLong GENERATION = new AtomicLong();

  private SegVisibilityPolicy() {}

  /** Value a cached default must be recomputed against; see {@link #GENERATION}. */
  public static long generation() {
    return GENERATION.get();
  }

  /** Marks every cached default as stale. */
  public static void invalidate() {
    GENERATION.incrementAndGet();
  }

  /**
   * Records the segmentations a transfer is about to bring, so {@link #HIDE_COUNT} reaches its
   * threshold at once instead of climbing to it over the minutes the transfer takes.
   *
   * <p>Without this the same study answers differently depending on where it comes from. Read from
   * disk, every file registers within a second or two, so the count is settled before anything
   * paints and none of them is ever shown. Downloaded instance by instance, the files arrive over
   * minutes, so the first ones are visible by default — each paying for a full canonical volume
   * build on a study whose segmentations were meant to stay hidden — until enough siblings have
   * landed to hide them again.
   *
   * @param patientPseudoUID patient the series belongs to; the announcement is dropped when unknown
   * @param seriesUID segmentation series, so that announcing it twice does not count it twice
   * @param seriesDescription how the series describes itself, matched against {@link
   *     #HIDE_KEYWORDS}
   * @param instances number of segmentation instances the series will hold
   */
  public static void announce(
      String patientPseudoUID, String seriesUID, String seriesDescription, int instances) {
    if (!StringUtil.hasText(patientPseudoUID) || !StringUtil.hasText(seriesUID) || instances <= 0) {
      return;
    }
    ANNOUNCED
        .computeIfAbsent(patientPseudoUID, _ -> new ConcurrentHashMap<>())
        .put(seriesUID, new AnnouncedSegSeries(seriesDescription, instances));
    invalidate();
  }

  /** Drops the announcement of a series being removed, delivered or not. */
  public static void forgetSeries(String patientPseudoUID, String seriesUID) {
    Map<String, AnnouncedSegSeries> announced =
        patientPseudoUID == null ? null : ANNOUNCED.get(patientPseudoUID);
    if (announced != null && seriesUID != null && announced.remove(seriesUID) != null) {
      invalidate();
    }
  }

  /** Drops every announcement made for a patient being closed. */
  public static void forgetPatient(String patientPseudoUID) {
    if (patientPseudoUID != null && ANNOUNCED.remove(patientPseudoUID) != null) {
      invalidate();
    }
  }

  /**
   * Re-keys the announcements of a patient whose pseudo UID changed. The manifest builds that UID
   * from its own attributes and the first downloaded instance may disagree, in which case {@code
   * DicomModel} merges the two nodes — and the segmentations then look themselves up under the new
   * UID, where the announcement would no longer be.
   */
  public static void renamePatient(String oldPatientPseudoUID, String newPatientPseudoUID) {
    Map<String, AnnouncedSegSeries> announced =
        oldPatientPseudoUID == null ? null : ANNOUNCED.remove(oldPatientPseudoUID);
    if (announced == null || announced.isEmpty() || !StringUtil.hasText(newPatientPseudoUID)) {
      return;
    }
    ANNOUNCED
        .computeIfAbsent(newPatientPseudoUID, _ -> new ConcurrentHashMap<>())
        .putAll(announced);
    invalidate();
  }

  /** Applies the three rules in increasing order of cost. */
  public static boolean isHiddenByDefault(SegSpecialElement seg) {
    if (seg == null) {
      return false;
    }
    WProperties prefs = GuiUtils.getUICore().getSystemPreferences();
    if (prefs.getBooleanProperty(HIDE_ALL, false)) {
      return true;
    }
    List<String> keywords = hideKeywords(prefs.getProperty(HIDE_KEYWORDS, DEFAULT_HIDE_KEYWORDS));
    return matchesHideKeywords(seg, keywords) || isCrowded(seg, prefs, keywords);
  }

  /**
   * Matches the keywords against the segmentation's own descriptions; failing that, against its
   * segment labels, which must <em>all</em> match so a multi-segment object is not hidden because
   * one of its segments happens to be the table.
   */
  private static boolean matchesHideKeywords(SegSpecialElement seg, List<String> keywords) {
    if (keywords.isEmpty()) {
      return false;
    }
    Attributes dicom = dicomObject(seg);
    if (dicom != null
        && (matchesAny(dicom.getString(Tag.SeriesDescription), keywords)
            || matchesAny(dicom.getString(Tag.ContentDescription), keywords)
            || matchesAny(dicom.getString(Tag.ContentLabel), keywords))) {
      return true;
    }
    // Segment labels are only populated once the SEG is parsed; an empty map means "not yet known",
    // not "no segment matches", so it must not answer true.
    return !seg.getSegAttributes().isEmpty()
        && seg.getSegAttributes().values().stream()
            .allMatch(
                r ->
                    matchesAny(r.getLabel(), keywords)
                        || matchesAny(r.getDescription(), keywords)
                        || matchesAny(r.getAlgorithmName(), keywords));
  }

  /**
   * Counts every segmentation the patient holds or is about to hold, ignoring those the keywords
   * already hide — a study whose only extra segmentations are table removals is not crowded, and
   * hiding the few real ones because of them would be surprising.
   *
   * <p>The count spans the patient rather than one series because that is the number the user sees:
   * a cardiac study routinely spreads its segmentations over several series, and counting each
   * separately leaves the single-file ones showing while the crowded ones hide. Reaching the
   * threshold therefore hides all of them, as {@link #HIDE_ALL} does.
   */
  private static boolean isCrowded(
      SegSpecialElement seg, WProperties prefs, List<String> keywords) {
    int threshold = prefs.getIntProperty(HIDE_COUNT, DEFAULT_HIDE_COUNT);
    if (threshold <= 0) {
      return false;
    }
    HiddenSeriesManager manager = HiddenSeriesManager.getInstance();
    String patientUID = (String) seg.getTagValue(TagW.PatientPseudoUID);
    Map<String, AnnouncedSegSeries> announced = announcedOf(patientUID);
    Collection<String> seriesUIDs = crowdingSeries(manager, seg, patientUID, announced);
    return countCrowding(manager.series2Elements, announced, seriesUIDs, keywords) >= threshold;
  }

  /** What is still announced for a patient, never {@code null}; package-private for the tests. */
  static Map<String, AnnouncedSegSeries> announcedOf(String patientPseudoUID) {
    Map<String, AnnouncedSegSeries> announced =
        patientPseudoUID == null ? null : ANNOUNCED.get(patientPseudoUID);
    return announced == null ? Map.of() : announced;
  }

  /**
   * Every series of this segmentation's patient holding, or announced to hold, hidden elements.
   * Falls back to its own series when the patient is unknown.
   */
  private static Collection<String> crowdingSeries(
      HiddenSeriesManager manager,
      SegSpecialElement seg,
      String patientUID,
      Map<String, AnnouncedSegSeries> announced) {
    Set<String> loaded = patientUID == null ? null : manager.patient2Series.get(patientUID);
    if (loaded == null) {
      String seriesUID = TagD.getTagValue(seg, Tag.SeriesInstanceUID, String.class);
      loaded = StringUtil.hasText(seriesUID) ? Set.of(seriesUID) : Set.of();
    }
    if (announced.isEmpty()) {
      return loaded;
    }
    Set<String> union = new HashSet<>(loaded);
    union.addAll(announced.keySet());
    return union;
  }

  /**
   * Segmentations {@code seriesUIDs} hold or will hold, ignoring those the keywords already hide.
   *
   * <p>Each series contributes the larger of what it has delivered and what was announced for it: a
   * transfer only ever adds to a series, so the announcement is the count the rule would otherwise
   * have to wait out, while a series nobody announced — or one already complete — is still counted
   * by what it holds.
   */
  static long countCrowding(
      Map<String, Set<HiddenSpecialElement>> series2Elements,
      Map<String, AnnouncedSegSeries> announced,
      Collection<String> seriesUIDs,
      List<String> keywords) {
    long total = 0;
    for (String seriesUID : seriesUIDs) {
      Set<HiddenSpecialElement> elements = series2Elements.get(seriesUID);
      AnnouncedSegSeries series = announced.get(seriesUID);
      long promised =
          series == null || matchesAny(series.description(), keywords) ? 0L : series.instances();
      total += Math.max(elements == null ? 0L : countCrowding(elements, keywords), promised);
    }
    return total;
  }

  /** Loaded segmentations that the keyword rule does not already hide. */
  static long countCrowding(
      Collection<? extends HiddenSpecialElement> elements, List<String> keywords) {
    return elements.stream()
        .filter(SegSpecialElement.class::isInstance)
        .map(SegSpecialElement.class::cast)
        .filter(seg -> !matchesHideKeywords(seg, keywords))
        .count();
  }

  private static Attributes dicomObject(SegSpecialElement seg) {
    DcmMediaReader reader = seg.getMediaReader();
    return reader == null ? null : reader.getDicomObject();
  }

  /** Splits and normalises the preference value; package-private so the matching can be tested. */
  static List<String> hideKeywords(String pref) {
    if (!StringUtil.hasText(pref)) {
      return List.of();
    }
    return Arrays.stream(pref.split(","))
        .map(SegVisibilityPolicy::normalize)
        .filter(StringUtil::hasText)
        .toList();
  }

  static boolean matchesAny(String value, List<String> keywords) {
    if (!StringUtil.hasText(value)) {
      return false;
    }
    String normalized = normalize(value);
    return keywords.stream().anyMatch(normalized::contains);
  }

  /**
   * Strips case and every separator, so "Table removal" matches "TableRemoval" and "table-removal".
   */
  private static String normalize(String value) {
    return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}]", "");
  }
}
