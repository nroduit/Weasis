/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.media.data;

import javax.swing.JProgressBar;

public interface SeriesImporter {

  boolean isStopped();

  JProgressBar getProgressBar();

  boolean stop();

  void resume();

  void setPriority();

  /**
   * Resumes a stopped transfer for a view that already shows the series: the images land in that
   * view and nothing else opens or hangs a viewer for them, unlike {@link #resume()}, which keeps
   * the opening rule of the load the series came from.
   */
  default void resumeForDisplay() {
    resume();
  }

  /**
   * Starts the transfer of a series whose download is attached but not running, as opening the
   * series demands: the resumed transfer goes to the front of the queue. A series with no transfer
   * (local files, finished download) or one already downloading is left alone.
   *
   * @return true when a download was started
   */
  static boolean startPending(MediaSeries<?> series) {
    if (series != null
        && series.getSeriesLoader() instanceof SeriesImporter loader
        && loader.isStopped()) {
      loader.resumeForDisplay();
      return true;
    }
    return false;
  }
}
