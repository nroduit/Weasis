/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.main;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.weasis.core.api.media.data.SeriesThumbnail;
import org.weasis.core.api.media.data.SeriesThumbnail.Badge;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.items.Interest;
import org.weasis.dicom.explorer.items.InterestSource;

/**
 * The badge of a thumbnail for the images of interest of its series: the count after a star, and a
 * tooltip line naming the sources. The interest is derived again at most every half second, since a
 * thumbnail paints far more often than key objects or graphics change.
 */
public final class InterestBadge implements Supplier<Badge> {

  private static final long REFRESH_MS = 500;

  private final DicomSeries series;
  private Badge badge;
  private long computedAt = -REFRESH_MS;

  InterestBadge(DicomSeries series) {
    this.series = series;
  }

  /** Gives the thumbnail the badge of the images of interest of its series. */
  public static void attach(SeriesThumbnail thumbnail, DicomSeries series) {
    thumbnail.setBadge(new InterestBadge(series));
  }

  @Override
  public Badge get() {
    long now = System.currentTimeMillis();
    if (now - computedAt > REFRESH_MS) {
      badge = of(Interest.of(series));
      computedAt = now;
    }
    return badge;
  }

  /** The badge for an interest, null when there is none. */
  static Badge of(Interest interest) {
    if (interest == null || interest.isEmpty()) {
      return null;
    }
    List<String> parts = new ArrayList<>();
    for (InterestSource source : interest.sources()) {
      parts.add(FacetChipBar.sourceLabel(source) + " " + interest.count(source));
    }
    String tooltip =
        MessageFormat.format(Messages.getString("DicomExplorer.interest_tip"), interest.count())
            + " ("
            + String.join(", ", parts)
            + ")";
    return new Badge("★" + interest.count(), tooltip);
  }
}
