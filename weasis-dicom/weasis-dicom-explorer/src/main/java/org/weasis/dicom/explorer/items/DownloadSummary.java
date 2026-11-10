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

import java.util.Collection;

/**
 * How far the series of a study are downloaded: the counts the study header and the study content
 * panel show as "n of N series downloaded".
 */
public record DownloadSummary(int total, int complete, int pending, int downloading) {

  public static DownloadSummary of(Collection<SeriesItem> items) {
    int complete = 0;
    int pending = 0;
    int downloading = 0;
    for (SeriesItem item : items) {
      switch (item.downloadState()) {
        case COMPLETE -> complete++;
        case PENDING -> pending++;
        case DOWNLOADING -> downloading++;
      }
    }
    return new DownloadSummary(items.size(), complete, pending, downloading);
  }

  public static DownloadSummary of(StudyContents contents) {
    return of(contents.series());
  }

  /** True when every series is on the local device. */
  public boolean isComplete() {
    return complete == total;
  }

  /** True when a download could be started. */
  public boolean hasPending() {
    return pending > 0;
  }
}
