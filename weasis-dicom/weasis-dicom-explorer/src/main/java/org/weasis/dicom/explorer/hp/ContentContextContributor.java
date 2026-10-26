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

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import org.dcm4che3.data.Tag;
import org.pf4j.Extension;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.ui.editor.ContextFacts;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.ViewContextContributor;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.HiddenSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.hp.SeriesFeatureExtractor;
import org.weasis.dicom.codec.hp.SeriesFeatures;
import org.weasis.dicom.explorer.DicomModel;

/**
 * The content capabilities of the selected series, as {@link ContextFacts} every plugin can read
 * without knowing DICOM: whether it is a volume ({@code series.volume}), its temporal kind and image
 * count, whether SUV can be computed, whether its pixel spacing is reliable, which objects reference
 * it ({@code series.objects.<modality>}) and whether the study holds a fusion partner ({@code
 * study.fusion}). The relevance predicates of the toolbars, tools and menu entries read them.
 */
@Extension
public class ContentContextContributor implements ViewContextContributor {

  private static final SeriesFeatureExtractor EXTRACTOR = new SeriesFeatureExtractor();
  private static final Set<String> FUNCTIONAL = Set.of("PT", "NM"); // NON-NLS
  private static final Set<String> ANATOMICAL = Set.of("CT", "MR"); // NON-NLS

  private final Supplier<Optional<DicomModel>> model;

  public ContentContextContributor() {
    this(HpStudies::model);
  }

  ContentContextContributor(Supplier<Optional<DicomModel>> model) {
    this.model = Objects.requireNonNull(model);
  }

  @Override
  public void contribute(SeriesViewer<?> viewer, MediaSeries<?> series, Map<String, Object> facts) {
    if (series == null
        || !(series.getMedia(MEDIA_POSITION.FIRST, null, null) instanceof DicomImageElement image)) {
      return;
    }
    SeriesFeatures features = featuresOf(series);
    if (features != null) {
      facts.put(ContextFacts.VOLUME, features.isVolume());
      if (features.temporal() != null) {
        facts.put(ContextFacts.TEMPORAL, features.temporal().name());
      }
      facts.put(ContextFacts.IMAGES, features.imageCount());
      facts.put(
          ContextFacts.SUV,
          features.petCorrection() != null && features.petCorrection().suvCapable());
    }
    facts.put(ContextFacts.CALIBRATED, isCalibrated(image));
    objectsOf(series).forEach((modality, count) -> facts.put(ContextFacts.objects(modality), count));
    facts.put(ContextFacts.FUSION, hasFusionPartner(series, features, model.get().orElse(null)));
  }

  /** The features of a DICOM image series, from the cache of the extractor; null otherwise. */
  @SuppressWarnings("unchecked")
  static SeriesFeatures featuresOf(MediaSeries<?> series) {
    if (series == null
        || !(series.getMedia(MEDIA_POSITION.FIRST, null, null) instanceof DicomImageElement)) {
      return null;
    }
    try {
      return EXTRACTOR.of((MediaSeries<DicomImageElement>) series);
    } catch (RuntimeException e) {
      return null;
    }
  }

  /**
   * Whether the pixel spacing of an image is reliable: Pixel Spacing (0028,0030) of a calibrated
   * plane. An estimate at the detector (Imager Pixel Spacing), a scanned film or the regions of an
   * ultrasound are not, so a calibration stays offered.
   */
  static boolean isCalibrated(DicomImageElement image) {
    if ("US".equals(TagD.getTagValue(image, Tag.Modality, String.class))) { // NON-NLS
      return false;
    }
    double[] spacing = TagD.getTagValue(image, Tag.PixelSpacing, double[].class);
    return spacing != null && spacing.length >= 2 && spacing[0] > 0 && spacing[1] > 0;
  }

  /** The objects referencing the series, counted by modality (KO, PR, SEG, RTSTRUCT...). */
  static Map<String, Integer> objectsOf(MediaSeries<?> series) {
    String uid = TagD.getTagValue(series, Tag.SeriesInstanceUID, String.class);
    if (!StringUtil.hasText(uid)) {
      return Map.of();
    }
    Set<String> hiddenSeries = HiddenSeriesManager.getInstance().reference2Series.get(uid);
    Map<String, Integer> counts =
        hiddenSeries == null || hiddenSeries.isEmpty()
            ? new TreeMap<>()
            : countByModality(
                HiddenSeriesManager.getHiddenElementsFromSeries(
                    HiddenSpecialElement.class, hiddenSeries.toArray(String[]::new)));
    if (!counts.containsKey("KO")) { // NON-NLS
      // key objects reference the series from their evidence sequence, not the series sequence
      int keyObjects = keyObjectsOf(series);
      if (keyObjects > 0) {
        counts.put("KO", keyObjects); // NON-NLS
      }
    }
    return counts;
  }

  @SuppressWarnings("unchecked")
  private static int keyObjectsOf(MediaSeries<?> series) {
    try {
      return DicomModel.getKoSpecialElements((MediaSeries<DicomImageElement>) series).size();
    } catch (RuntimeException e) {
      return 0;
    }
  }

  /** Counts special elements by their modality, in a sorted map. */
  static Map<String, Integer> countByModality(Collection<? extends DicomSpecialElement> elements) {
    Map<String, Integer> counts = new TreeMap<>();
    for (DicomSpecialElement element : elements) {
      String modality = TagD.getTagValue(element, Tag.Modality, String.class);
      if (StringUtil.hasText(modality)) {
        counts.merge(modality, 1, Integer::sum);
      }
    }
    return counts;
  }

  /**
   * Whether another image series of the study can be fused with this one: it shares the frame of
   * reference, or one is functional (PET, NM) and the other anatomical (CT, MR).
   */
  static boolean hasFusionPartner(
      MediaSeries<?> series, SeriesFeatures features, DicomModel model) {
    if (model == null) {
      return false;
    }
    MediaSeriesGroup study = model.getParent(series, DicomModel.study);
    if (study == null) {
      return false;
    }
    String frame =
        features != null && features.frameOfReference() != null
            ? features.frameOfReference()
            : frameOf(series);
    String modality = TagD.getTagValue(series, Tag.Modality, String.class);
    for (MediaSeriesGroup other : model.getChildren(study)) {
      if (other == series
          || !(other instanceof MediaSeries<?> candidate)
          || !(candidate.getMedia(MEDIA_POSITION.FIRST, null, null) instanceof DicomImageElement)) {
        continue;
      }
      if (frame != null && frame.equals(frameOf(candidate))) {
        return true;
      }
      String otherModality = TagD.getTagValue(candidate, Tag.Modality, String.class);
      if (isPair(modality, otherModality)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isPair(String modality, String other) {
    return modality != null
        && other != null
        && ((FUNCTIONAL.contains(modality) && ANATOMICAL.contains(other))
            || (ANATOMICAL.contains(modality) && FUNCTIONAL.contains(other)));
  }

  private static String frameOf(MediaSeries<?> series) {
    String frame = TagD.getTagValue(series, Tag.FrameOfReferenceUID, String.class);
    if (frame == null && series.getMedia(MEDIA_POSITION.FIRST, null, null) instanceof TagReadable first) {
      frame = TagD.getTagValue(first, Tag.FrameOfReferenceUID, String.class);
    }
    return frame;
  }
}
