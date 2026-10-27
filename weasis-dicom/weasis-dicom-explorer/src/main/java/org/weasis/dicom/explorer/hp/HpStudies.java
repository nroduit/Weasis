/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.hp;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.ui.editor.ViewerPlacement;
import org.weasis.core.ui.editor.image.HangingLayout;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.hp.DerivationTables;
import org.weasis.dicom.codec.hp.HangingEngine;
import org.weasis.dicom.codec.hp.HangingEngine.Candidate;
import org.weasis.dicom.codec.hp.HangingEngine.ProtocolScore;
import org.weasis.dicom.codec.hp.HangingPlan;
import org.weasis.dicom.codec.hp.PriorStudy;
import org.weasis.dicom.codec.hp.SeriesFeatureExtractor;
import org.weasis.dicom.codec.hp.SeriesFeatures;
import org.weasis.dicom.codec.hp.StudySeries;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.items.Interest;
import org.weasis.dicom.explorer.items.InterestSource;

/** The study lookups the hanging protocol UI of other plugins needs from the explorer. */
public final class HpStudies {

  private HpStudies() {}

  /** The DICOM model of the application, empty when no explorer is open. */
  public static Optional<DicomModel> model() {
    return Optional.ofNullable(HpCommands.findModel());
  }

  /** The image series of a study with their features. */
  public static List<StudySeries> studySeries(DicomModel model, MediaSeriesGroup study) {
    return HpCommands.studySeries(model, study);
  }

  /**
   * The feature extractor that knows the images of interest: the explorer counts the images a key
   * object, a presentation state with annotations or a segment references and the ones drawn on in
   * Weasis ({@link Interest}), which the {@code interest} selectors of a protocol test.
   */
  public static SeriesFeatureExtractor extractor() {
    return new SeriesFeatureExtractor(DerivationTables.getInstance(), HpStudies::interestOf);
  }

  /** The images of interest of a series as the features carry them. */
  static SeriesFeatures.Interest interestOf(MediaSeries<DicomImageElement> series) {
    return series instanceof DicomSeries dicom
        ? toFeature(Interest.of(dicom))
        : SeriesFeatures.Interest.NONE;
  }

  /** The protocols that apply to a loaded study, best first, with its loaded priors. */
  public static List<ProtocolScore> candidates(DicomModel model, MediaSeriesGroup study) {
    List<StudySeries> series = HpCommands.studySeries(model, study);
    List<PriorStudy> priors = HpCommands.priorsOf(model, study, series);
    return HangingEngine.select(HangingEngine.candidates(), series, priors);
  }

  /**
   * Hangs the first step of a protocol on a loaded study, re-hanging the viewer of the patient; on
   * the event dispatch thread.
   *
   * @throws IllegalArgumentException when the protocol id is unknown
   */
  public static HangingPlanApplier.Applied hang(
      DicomModel model, MediaSeriesGroup study, String protocolId) {
    List<Candidate> candidates = HangingEngine.candidates();
    List<StudySeries> series = HpCommands.studySeries(model, study);
    List<PriorStudy> priors = HpCommands.priorsOf(model, study, series);
    List<ProtocolScore> scores = HangingEngine.select(candidates, series, priors);
    HangingPlan plan =
        HpCommands.planOf(candidates, scores, series, priors, protocolId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown protocol: " + protocolId));
    HangingPlanApplier.Context context =
        new HangingPlanApplier.Context(
            model, model.getParent(study, DicomModel.patient), study, candidates, scores);
    HangingPlan prepared = HangingPlanApplier.prepare(plan, 0);
    return new HangingPlanApplier()
        .apply(prepared, 0, context, ViewerPlacement.reuseViewer(), null);
  }

  /** The explorer's interest as a feature: the count and the source ids, upper case. */
  static SeriesFeatures.Interest toFeature(Interest interest) {
    if (interest == null || interest.isEmpty()) {
      return SeriesFeatures.Interest.NONE;
    }
    Set<String> sources = new LinkedHashSet<>();
    for (InterestSource source : interest.sources()) {
      sources.add(source.getId().toUpperCase(Locale.ROOT));
    }
    return new SeriesFeatures.Interest(interest.count(), sources);
  }

  /** The study of the first series a layout shows, empty when it shows none that is loaded. */
  public static Optional<MediaSeriesGroup> studyOf(DicomModel model, HangingLayout layout) {
    for (HangingLayout.Cell cell : layout.cells()) {
      String uid = cell.seriesId().orElse(null);
      if (uid != null) {
        Optional<MediaSeriesGroup> study =
            model.findSeries(uid).map(s -> model.getParent(s, DicomModel.study));
        if (study.isPresent()) {
          return study;
        }
      }
    }
    return Optional.empty();
  }
}
