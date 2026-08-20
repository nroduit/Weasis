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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
 *   <li>{@link #HIDE_COUNT} — so many segmentations are loaded that showing them together paints
 *       them on top of each other.
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
   * Number of segmentations loaded for the patient from which they all start hidden, exactly as
   * {@link #HIDE_ALL} does. {@code 0} disables the rule.
   */
  public static final String HIDE_COUNT = "weasis.dicom.seg.hide.count"; // NON-NLS

  public static final int DEFAULT_HIDE_COUNT = 3;

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
   * Counts every segmentation loaded for the patient, ignoring those the keywords already hide — a
   * study whose only extra segmentations are table removals is not crowded, and hiding the few real
   * ones because of them would be surprising.
   *
   * <p>The count spans the patient rather than one series because that is the number the user sees:
   * a cardiac study routinely spreads its segmentations over several series, and counting each
   * separately leaves the single-file ones showing while the crowded ones hide. Reaching the
   * threshold therefore hides all of them, as {@link #HIDE_ALL} does.
   */
  private static boolean isCrowded(
      SegSpecialElement seg, WProperties prefs, List<String> keywords) {
    int threshold = prefs.getIntProperty(HIDE_COUNT, DEFAULT_HIDE_COUNT);
    return threshold > 0 && countCrowding(loadedSegmentations(seg), keywords) >= threshold;
  }

  /** Every hidden element of this segmentation's patient, or of its own series when unknown. */
  private static Collection<HiddenSpecialElement> loadedSegmentations(SegSpecialElement seg) {
    HiddenSeriesManager manager = HiddenSeriesManager.getInstance();
    String patientUID = (String) seg.getTagValue(TagW.PatientPseudoUID);
    Set<String> seriesUIDs = patientUID == null ? null : manager.patient2Series.get(patientUID);
    if (seriesUIDs == null) {
      String seriesUID = TagD.getTagValue(seg, Tag.SeriesInstanceUID, String.class);
      seriesUIDs = StringUtil.hasText(seriesUID) ? Set.of(seriesUID) : Set.of();
    }
    return elementsOf(manager.series2Elements, seriesUIDs);
  }

  /** Flattens the hidden elements of {@code seriesUIDs}; package-private so it can be tested. */
  static List<HiddenSpecialElement> elementsOf(
      Map<String, Set<HiddenSpecialElement>> series2Elements, Collection<String> seriesUIDs) {
    List<HiddenSpecialElement> elements = new ArrayList<>();
    for (String seriesUID : seriesUIDs) {
      Set<HiddenSpecialElement> series = series2Elements.get(seriesUID);
      if (series != null) {
        elements.addAll(series);
      }
    }
    return elements;
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
