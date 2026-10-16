/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.api.media.data.TagW;

public final class SortSeriesStack {

  // Comparator cannot be a generic list of DicomOpImage because the Collection to sort has an
  // AbstractImage type
  public static final SeriesComparator<DicomImageElement> instanceNumber =
      new SeriesComparator<>() {

        @Override
        public int compare(DicomImageElement m1, DicomImageElement m2) {
          Integer val1 = TagD.getTagValue(m1, Tag.InstanceNumber, Integer.class);
          Integer val2 = TagD.getTagValue(m2, Tag.InstanceNumber, Integer.class);
          if (val1 == null || val2 == null) {
            return 0;
          }
          return val1.compareTo(val2);
        }

        @Override
        public String toString() {
          return Messages.getString("SortSeriesStack.inst");
        }
      };
  public static final SeriesComparator<DicomImageElement> slicePosition =
      new SeriesComparator<>() {

        @Override
        public int compare(DicomImageElement m1, DicomImageElement m2) {
          Double val1 = (Double) m1.getTagValue(TagW.SlicePosition);
          Double val2 = (Double) m2.getTagValue(TagW.SlicePosition);
          if (val1 == null || val2 == null) {
            return 0;
          }
          return Double.compare(val1, val2);
        }

        @Override
        public String toString() {
          return Messages.getString("SortSeriesStack.pos_orient");
        }
      };

  public static final SeriesComparator<DicomImageElement> sliceLocation =
      new SeriesComparator<>() {

        @Override
        public int compare(DicomImageElement m1, DicomImageElement m2) {
          Double val1 = TagD.getTagValue(m1, Tag.SliceLocation, Double.class);
          Double val2 = TagD.getTagValue(m2, Tag.SliceLocation, Double.class);
          if (val1 == null || val2 == null) {
            return 0;
          }
          return val1.compareTo(val2);
        }

        @Override
        public String toString() {
          return Messages.getString("SortSeriesStack.location");
        }
      };

  public static final SeriesComparator<DicomImageElement> acquisitionTime =
      new SeriesComparator<>() {

        @Override
        public int compare(DicomImageElement m1, DicomImageElement m2) {
          LocalTime val1 = TagD.getTagValue(m1, Tag.AcquisitionTime, LocalTime.class);
          LocalTime val2 = TagD.getTagValue(m2, Tag.AcquisitionTime, LocalTime.class);
          if (val1 == null || val2 == null) {
            return 0;
          }
          return val1.compareTo(val2);
        }

        @Override
        public String toString() {
          return Messages.getString("SortSeriesStack.time");
        }
      };
  public static final SeriesComparator<DicomImageElement> contentTime =
      new SeriesComparator<>() {

        @Override
        public int compare(DicomImageElement m1, DicomImageElement m2) {
          LocalTime val1 = TagD.getTagValue(m1, Tag.ContentTime, LocalTime.class);
          LocalTime val2 = TagD.getTagValue(m2, Tag.ContentTime, LocalTime.class);
          if (val1 == null || val2 == null) {
            return 0;
          }
          return val1.compareTo(val2);
        }

        @Override
        public String toString() {
          return Messages.getString("SortSeriesStack.content_time");
        }
      };

  public static final SeriesComparator<DicomImageElement> diffusionBValue =
      new SeriesComparator<>() {

        @Override
        public int compare(DicomImageElement m1, DicomImageElement m2) {
          Double val1 = TagD.getTagValue(m1, Tag.DiffusionBValue, Double.class);
          Double val2 = TagD.getTagValue(m2, Tag.DiffusionBValue, Double.class);
          if (val1 == null || val2 == null) {
            return 0;
          }
          return val1.compareTo(val2);
        }

        @Override
        public String toString() {
          return Messages.getString("SortSeriesStack.dvalue");
        }
      };

  private SortSeriesStack() {}

  public static SeriesComparator<DicomImageElement>[] getValues() {
    return new SeriesComparator[] {
      instanceNumber, slicePosition, sliceLocation, contentTime, acquisitionTime, diffusionBValue
    };
  }

  /** Stable, non-localized keys of the comparators, as snapshots and commands name them. */
  private static final Map<String, SeriesComparator<DicomImageElement>> BY_KEY = byKey();

  private static Map<String, SeriesComparator<DicomImageElement>> byKey() {
    Map<String, SeriesComparator<DicomImageElement>> map = new LinkedHashMap<>();
    map.put("instance", instanceNumber); // NON-NLS
    map.put("slice-position", slicePosition); // NON-NLS
    map.put("slice-location", sliceLocation); // NON-NLS
    map.put("content-time", contentTime); // NON-NLS
    map.put("acquisition-time", acquisitionTime); // NON-NLS
    map.put("b-value", diffusionBValue); // NON-NLS
    return map;
  }

  public static Optional<String> keyOf(Comparator<?> comparator) {
    return BY_KEY.entrySet().stream()
        .filter(e -> e.getValue() == comparator)
        .map(Map.Entry::getKey)
        .findFirst();
  }

  public static Optional<SeriesComparator<DicomImageElement>> fromKey(String key) {
    return Optional.ofNullable(key).map(BY_KEY::get);
  }
}
