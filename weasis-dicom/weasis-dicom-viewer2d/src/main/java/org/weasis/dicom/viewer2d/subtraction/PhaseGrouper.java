/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.subtraction;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

/**
 * Finds the phases of a dynamic series stored as one series: the frames sharing one Temporal
 * Position Index, else the images sharing one Temporal Position Identifier, else one Trigger Time,
 * else the k-th acquisition at every slice position when every position was acquired the same
 * number of times. A phase is handed out as a transient series of the parent's images, so it pairs,
 * previews and builds like a series of its own; the same phase object comes back for the same
 * series until its images change.
 */
public final class PhaseGrouper {

  /** One phase of a dynamic series: its 1-based index, its images and when it was acquired. */
  public record Phase(
      MediaSeries<DicomImageElement> parent,
      int index,
      List<DicomImageElement> images,
      LocalTime time,
      MediaSeries<DicomImageElement> series) {

    public int count() {
      return images.size();
    }
  }

  private record Grouping(int imageCount, List<Phase> phases) {}

  /** The grouping kept on the series itself: two series with one UID are two groupings. */
  private static final TagW PHASES = new TagW("SubtractionPhases", TagType.OBJECT); // NON-NLS

  /** The phase a transient phase series stands for, kept on that series. */
  private static final TagW PHASE = new TagW("SubtractionPhase", TagType.OBJECT); // NON-NLS

  private PhaseGrouper() {}

  /** The phases of the series; one phase holding every image when it is not dynamic. */
  public static synchronized List<Phase> phases(MediaSeries<DicomImageElement> series) {
    int count = series.size(null);
    Grouping grouping =
        series.getTagValue(PHASES) instanceof Grouping kept && kept.imageCount() == count
            ? kept
            : null;
    if (grouping == null) {
      List<Phase> phases = group(series);
      grouping = new Grouping(count, phases);
      series.setTag(PHASES, grouping);
      for (Phase phase : phases) {
        if (phase.series() != series) {
          phase.series().setTag(PHASE, phase);
        }
      }
    }
    return grouping.phases();
  }

  /** Whether the series holds several phases. */
  public static boolean isDynamic(MediaSeries<DicomImageElement> series) {
    return phases(series).size() > 1;
  }

  /** The phase a transient phase series stands for, empty for a real series. */
  public static Optional<Phase> phaseOf(MediaSeries<?> series) {
    return series != null && series.getTagValue(PHASE) instanceof Phase phase
        ? Optional.of(phase)
        : Optional.empty();
  }

  /** The operand of a spec: the phase of the series, or the series when no phase is named. */
  public static MediaSeries<DicomImageElement> operand(
      MediaSeries<DicomImageElement> series, Integer phase) {
    if (phase == null) {
      return series;
    }
    List<Phase> phases = phases(series);
    if (phase > phases.size()) {
      throw new IllegalStateException(
          "The series has " + phases.size() + " phase(s), not " + phase); // NON-NLS
    }
    return phases.get(phase - 1).series();
  }

  private static List<Phase> group(MediaSeries<DicomImageElement> series) {
    // Positions computed and ordered along the normal, as the pairing of the slices needs them
    List<DicomImageElement> images = SubtractionCompatibility.sortedSlices(series);
    // The frames of an enhanced multi-frame image carry the Temporal Position Index of their
    // Frame Content macro; single-frame images the Temporal Position Identifier
    List<List<DicomImageElement>> groups =
        byTag(images, img -> TagD.getTagValue(img, Tag.TemporalPositionIndex, Integer.class));
    if (groups == null) {
      groups =
          byTag(
              images, img -> TagD.getTagValue(img, Tag.TemporalPositionIdentifier, Integer.class));
    }
    if (groups == null) {
      groups = byTag(images, img -> TagD.getTagValue(img, Tag.TriggerTime, Double.class));
    }
    if (groups == null) {
      groups = byRepeatedPositions(images);
    }
    if (groups == null) {
      groups = List.of(images);
    }
    List<Phase> phases = new ArrayList<>(groups.size());
    for (int i = 0; i < groups.size(); i++) {
      List<DicomImageElement> members = groups.get(i);
      members.sort(Comparator.comparingDouble(DicomMediaUtils::getSlicePositionValue));
      phases.add(
          new Phase(
              series,
              i + 1,
              List.copyOf(members),
              acquisitionTime(members.getFirst()),
              groups.size() > 1 ? phaseSeries(series, i + 1, members) : series));
    }
    return phases;
  }

  /** Groups by a temporal tag, in its order; null when the tag does not split the series. */
  private static <T extends Comparable<T>> List<List<DicomImageElement>> byTag(
      List<DicomImageElement> images, Function<DicomImageElement, T> key) {
    TreeMap<T, List<DicomImageElement>> groups = new TreeMap<>();
    for (DicomImageElement image : images) {
      T value = key.apply(image);
      if (value == null) {
        return null;
      }
      groups.computeIfAbsent(value, v -> new ArrayList<>()).add(image);
    }
    return groups.size() > 1 ? new ArrayList<>(groups.values()) : null;
  }

  /**
   * Every position acquired k times gives k phases, the acquisitions at one position ordered by
   * their time; null when the positions were not all acquired the same number of times.
   */
  private static List<List<DicomImageElement>> byRepeatedPositions(List<DicomImageElement> images) {
    TreeMap<Long, List<DicomImageElement>> atPosition = new TreeMap<>();
    for (DicomImageElement image : images) {
      long key = Math.round(DicomMediaUtils.getSlicePositionValue(image) * 1000);
      atPosition.computeIfAbsent(key, k -> new ArrayList<>()).add(image);
    }
    int repeats = atPosition.firstEntry().getValue().size();
    if (repeats < 2 || atPosition.values().stream().anyMatch(l -> l.size() != repeats)) {
      return null;
    }
    Comparator<DicomImageElement> order =
        Comparator.comparing(
                (DicomImageElement img) -> acquisitionTime(img),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(
                img -> TagD.getTagValue(img, Tag.InstanceNumber, Integer.class),
                Comparator.nullsLast(Comparator.naturalOrder()));
    List<List<DicomImageElement>> phases = new ArrayList<>(repeats);
    for (int i = 0; i < repeats; i++) {
      phases.add(new ArrayList<>(atPosition.size()));
    }
    for (List<DicomImageElement> acquisitions : atPosition.values()) {
      acquisitions.sort(order);
      for (int i = 0; i < repeats; i++) {
        phases.get(i).add(acquisitions.get(i));
      }
    }
    return phases;
  }

  /** A series of the phase's images that carries the tags of its parent and names the phase. */
  private static MediaSeries<DicomImageElement> phaseSeries(
      MediaSeries<DicomImageElement> parent, int index, List<DicomImageElement> images) {
    String uid = SubtractionCommands.uidOf(parent);
    DicomSeries phase = new DicomSeries(uid + "#" + index); // NON-NLS
    for (Iterator<Entry<TagW, Object>> it = parent.getTagEntrySetIterator(); it.hasNext(); ) {
      Entry<TagW, Object> entry = it.next();
      if (entry.getKey() != PHASES) {
        phase.setTag(entry.getKey(), entry.getValue());
      }
    }
    phase.setTag(TagD.get(Tag.SeriesInstanceUID), uid);
    String description = TagD.getTagValue(parent, Tag.SeriesDescription, String.class);
    phase.setTag(
        TagD.get(Tag.SeriesDescription),
        (description == null ? "" : description + " ") + "p" + index); // NON-NLS
    phase.setTag(TagW.SubseriesInstanceUID, uid + "#" + index); // NON-NLS
    phase.addAll(images);
    return phase;
  }

  private static LocalTime acquisitionTime(DicomImageElement image) {
    LocalTime time = TagD.getTagValue(image, Tag.AcquisitionTime, LocalTime.class);
    return time != null ? time : TagD.getTagValue(image, Tag.ContentTime, LocalTime.class);
  }
}
