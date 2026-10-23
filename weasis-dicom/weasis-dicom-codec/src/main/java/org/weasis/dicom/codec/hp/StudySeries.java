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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.TagD;

/**
 * One series of the study the engine matches: the series, the id a plan refers to, its normalized
 * features and the dataset of its first image, on which the DICOM selectors and the protocol
 * definitions are evaluated.
 *
 * <p>A series holding several projection views (e.g. the four mammography views of some vendors in
 * one series) gives one entry per view, laterality and view code: each entry starts on the first
 * image of its view, which a cell then shows.
 *
 * @param series the series
 * @param uid the Series Instance UID, the id a {@link HangingPlan} stores
 * @param features the normalized features
 * @param dataset the dataset of the first image, empty when the series has no readable image
 * @param image the image the entry starts on, from 1 in instance number order; 0 for the series
 * @param instance the SOP Instance UID of that image, null for the series
 */
public record StudySeries(
    MediaSeries<DicomImageElement> series,
    String uid,
    SeriesFeatures features,
    Attributes dataset,
    int image,
    String instance) {

  public StudySeries {
    Objects.requireNonNull(series, "series");
    uid = uid == null ? String.valueOf(series.getTagValue(series.getTagID())) : uid;
    dataset = dataset == null ? new Attributes() : dataset;
  }

  /** An entry for the whole series. */
  public StudySeries(
      MediaSeries<DicomImageElement> series,
      String uid,
      SeriesFeatures features,
      Attributes dataset) {
    this(series, uid, features, dataset, 0, null);
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
      Attributes dataset = dataset(first);
      Object uid = s.getTagValue(s.getTagID());
      String id = uid == null ? null : uid.toString();
      SeriesFeatures features = extractor.of(s);
      List<StudySeries> views = views(s, id, features);
      if (views.size() > 1) {
        result.addAll(views);
      } else {
        result.add(new StudySeries(s, id, features, dataset));
      }
    }
    return result;
  }

  /** One entry per laterality and view of a projection series; one or none otherwise. */
  private static List<StudySeries> views(
      MediaSeries<DicomImageElement> series, String uid, SeriesFeatures features) {
    if (series.size(null) < 2) {
      return List.of();
    }
    List<DicomImageElement> images = series.copyOfMedias(null, SortSeriesStack.instanceNumber);
    record View(String laterality, String view) {}
    Map<View, Integer> starts = new LinkedHashMap<>();
    Map<View, Integer> counts = new LinkedHashMap<>();
    for (int i = 0; i < images.size(); i++) {
      Attributes dcm = dataset(images.get(i));
      String view = SeriesFeatureExtractor.view(dcm);
      if (view == null) {
        return List.of(); // not a series of projection views
      }
      View key = new View(SeriesFeatureExtractor.laterality(series, dcm), view);
      starts.putIfAbsent(key, i);
      counts.merge(key, 1, Integer::sum);
    }
    if (starts.size() < 2) {
      return List.of();
    }
    List<StudySeries> result = new ArrayList<>();
    starts.forEach(
        (key, index) -> {
          DicomImageElement image = images.get(index);
          Attributes dcm = dataset(image);
          Object sop = image.getTagValue(TagD.get(Tag.SOPInstanceUID));
          String instance =
              sop != null ? sop.toString() : dcm == null ? null : dcm.getString(Tag.SOPInstanceUID);
          result.add(
              new StudySeries(
                  series,
                  uid,
                  features == null
                      ? null
                      : features.withView(key.laterality(), key.view(), counts.get(key)),
                  dcm,
                  index + 1,
                  instance));
        });
    return result;
  }

  private static Attributes dataset(DicomImageElement image) {
    return image == null || image.getMediaReader() == null
        ? null
        : image.getMediaReader().getDicomObject();
  }

  /** Whether the entry is one view of a multi-view series. */
  public boolean isView() {
    return instance != null;
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
