/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.items;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.data.PrDicomObject;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.HiddenSpecialElement;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.codec.PRSpecialElement;
import org.weasis.dicom.codec.RejectedKOSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.seg.SegSpecialElement;

/**
 * The images of interest of a series: those a key object selection, an annotated presentation state
 * or a segmentation points at, and those drawn on in Weasis. The sources are kept, so a badge or a
 * filter can say which one. Read on demand from the hidden-series index and the graphic models of
 * the images; nothing is decoded.
 *
 * @param imagesBySource the SOP Instance UIDs of interest, per source; sources without any are
 *     absent
 */
public record Interest(Map<InterestSource, Set<String>> imagesBySource) {

  public static final Interest NONE = new Interest(Map.of());

  public Interest {
    Map<InterestSource, Set<String>> copy = new EnumMap<>(InterestSource.class);
    imagesBySource.forEach(
        (k, v) -> {
          if (v != null && !v.isEmpty()) {
            copy.put(k, Collections.unmodifiableSet(new LinkedHashSet<>(v)));
          }
        });
    imagesBySource = Collections.unmodifiableMap(copy);
  }

  public boolean isEmpty() {
    return imagesBySource.isEmpty();
  }

  public Set<InterestSource> sources() {
    return imagesBySource.isEmpty()
        ? Set.of()
        : Collections.unmodifiableSet(EnumSet.copyOf(imagesBySource.keySet()));
  }

  /** The distinct images of interest, whatever points at them. */
  public Set<String> images() {
    Set<String> all = new LinkedHashSet<>();
    imagesBySource.values().forEach(all::addAll);
    return all;
  }

  public int count() {
    return images().size();
  }

  public int count(InterestSource source) {
    Set<String> set = imagesBySource.get(source);
    return set == null ? 0 : set.size();
  }

  /** Reads the images of interest of a series. */
  public static Interest of(DicomSeries series) {
    if (series == null) {
      return NONE;
    }
    String seriesUID = TagD.getTagValue(series, Tag.SeriesInstanceUID, String.class);
    Map<InterestSource, Set<String>> result = new EnumMap<>(InterestSource.class);
    addFromObjects(seriesUID, series, result);
    addFromGraphics(series, result);
    return result.isEmpty() ? NONE : new Interest(result);
  }

  private static void addFromObjects(
      String seriesUID, DicomSeries series, Map<InterestSource, Set<String>> result) {
    if (!StringUtil.hasText(seriesUID)) {
      return;
    }
    Set<String> origins = HiddenSeriesManager.getInstance().reference2Series.get(seriesUID);
    if (origins == null) {
      return;
    }
    for (String origin : origins) {
      Set<HiddenSpecialElement> elements =
          HiddenSeriesManager.getInstance().series2Elements.get(origin);
      if (elements == null) {
        continue;
      }
      for (HiddenSpecialElement element : elements) {
        switch (element) {
          case RejectedKOSpecialElement _ -> {
            // a rejection note marks images to hide, not to look at
          }
          case KOSpecialElement ko ->
              add(result, InterestSource.KEY_OBJECT, ko.getReferencedSOPInstanceUIDSet(seriesUID));
          case PRSpecialElement pr -> addFromPresentationState(pr, seriesUID, series, result);
          case SegSpecialElement seg -> {
            Map<String, ?> refs = seg.getRefMap().get(seriesUID);
            if (refs != null) {
              add(result, InterestSource.SEGMENTATION, refs.keySet());
            }
          }
          default -> {
            // other objects (RT) do not point at images of interest
          }
        }
      }
    }
  }

  /** A presentation state counts when it annotates; it names images or applies to the series. */
  private static void addFromPresentationState(
      PRSpecialElement pr,
      String seriesUID,
      DicomSeries series,
      Map<InterestSource, Set<String>> result) {
    PrDicomObject pro = pr.getPrDicomObject();
    if (pro == null || pro.getGraphicAnnotationSequence().isEmpty()) {
      return;
    }
    Set<String> images = new LinkedHashSet<>();
    for (Attributes item : pro.getReferencedSeriesSequence()) {
      if (!seriesUID.equals(item.getString(Tag.SeriesInstanceUID))) {
        continue;
      }
      Sequence refs = item.getSequence(Tag.ReferencedImageSequence);
      if (refs == null || refs.isEmpty()) {
        images.addAll(allImages(series));
      } else {
        for (Attributes ref : refs) {
          String uid = ref.getString(Tag.ReferencedSOPInstanceUID);
          if (StringUtil.hasText(uid)) {
            images.add(uid);
          }
        }
      }
    }
    add(result, InterestSource.PRESENTATION_STATE, images);
  }

  private static void addFromGraphics(DicomSeries series, Map<InterestSource, Set<String>> result) {
    Set<String> images = new LinkedHashSet<>();
    for (MediaElement media : series.getMedias(null, null)) {
      if (media.getTagValue(TagW.PresentationModel) instanceof GraphicModel model
          && hasDrawnGraphic(model)) {
        String uid = TagD.getTagValue(media, Tag.SOPInstanceUID, String.class);
        if (StringUtil.hasText(uid)) {
          images.add(uid);
        }
      }
    }
    add(result, InterestSource.GRAPHICS, images);
  }

  /** Measurements, drawings, annotations and points, not the layers of DICOM objects. */
  static boolean hasDrawnGraphic(GraphicModel model) {
    for (Graphic graphic : model.getAllGraphics()) {
      LayerType type = graphic.getLayerType();
      if (type == LayerType.MEASURE
          || type == LayerType.DRAW
          || type == LayerType.ANNOTATION
          || type == LayerType.POINTS) {
        return true;
      }
    }
    return false;
  }

  private static Set<String> allImages(DicomSeries series) {
    Set<String> uids = new LinkedHashSet<>();
    for (MediaElement media : series.getMedias(null, null)) {
      String uid = TagD.getTagValue(media, Tag.SOPInstanceUID, String.class);
      if (StringUtil.hasText(uid)) {
        uids.add(uid);
      }
    }
    return uids;
  }

  private static void add(
      Map<InterestSource, Set<String>> result, InterestSource source, Set<String> uids) {
    if (uids != null && !uids.isEmpty()) {
      result.computeIfAbsent(source, _ -> new LinkedHashSet<>()).addAll(uids);
    }
  }
}
