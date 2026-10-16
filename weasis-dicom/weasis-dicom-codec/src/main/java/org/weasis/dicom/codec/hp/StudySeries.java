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
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.dcm4che3.data.Attributes;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.dicom.codec.DicomImageElement;

/**
 * One series of the study the engine matches: the series, the id a plan refers to, its normalized
 * features and the dataset of its first image, on which the DICOM selectors and the protocol
 * definitions are evaluated.
 *
 * @param series the series
 * @param uid the Series Instance UID, the id a {@link HangingPlan} stores
 * @param features the normalized features
 * @param dataset the dataset of the first image, empty when the series has no readable image
 */
public record StudySeries(
    MediaSeries<DicomImageElement> series,
    String uid,
    SeriesFeatures features,
    Attributes dataset) {

  public StudySeries {
    Objects.requireNonNull(series, "series");
    uid = uid == null ? String.valueOf(series.getTagValue(series.getTagID())) : uid;
    dataset = dataset == null ? new Attributes() : dataset;
  }

  /** Describes the study series of a collection, skipping those without an image. */
  public static List<StudySeries> of(
      Collection<? extends MediaSeries<DicomImageElement>> series,
      SeriesFeatureExtractor extractor) {
    List<StudySeries> result = new ArrayList<>();
    for (MediaSeries<DicomImageElement> s : series) {
      if (s == null || s.size(null) == 0) {
        continue;
      }
      DicomImageElement first = s.getMedia(0, null, null);
      Attributes dataset =
          first == null || first.getMediaReader() == null
              ? null
              : first.getMediaReader().getDicomObject();
      Object uid = s.getTagValue(s.getTagID());
      result.add(new StudySeries(s, uid == null ? null : uid.toString(), extractor.of(s), dataset));
    }
    return result;
  }

  /** The number of images, for the tie-breaks. */
  public int imageCount() {
    return features == null ? series.size(null) : features.imageCount();
  }

  /** A short description for the reasons: modality, description tokens and image count. */
  public String describe() {
    StringBuilder text = new StringBuilder();
    if (features != null && features.modality() != null) {
      text.append(features.modality()).append(' ');
    }
    if (features != null && features.descriptionTokens() != null) {
      text.append(String.join(" ", features.descriptionTokens())).append(' ');
    }
    return text.append('(').append(imageCount()).append(" images)").toString(); // NON-NLS
  }
}
