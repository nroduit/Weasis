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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import javax.swing.JProgressBar;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.SeriesImporter;
import org.weasis.dicom.codec.DicomSeries;

class DownloadSummaryTest {

  /** A transfer attached to a series: stopped (pending) or running. */
  private static final class FakeImporter implements SeriesImporter {
    private boolean stopped;
    boolean resumed;

    FakeImporter(boolean stopped) {
      this.stopped = stopped;
    }

    @Override
    public boolean isStopped() {
      return stopped;
    }

    @Override
    public JProgressBar getProgressBar() {
      return null;
    }

    @Override
    public boolean stop() {
      stopped = true;
      return true;
    }

    @Override
    public void resume() {
      stopped = false;
      resumed = true;
    }

    @Override
    public void setPriority() {}
  }

  private static SeriesItem series(String uid, SeriesImporter loader) {
    DicomSeries series = new DicomSeries(uid);
    series.setSeriesLoader(loader);
    return SeriesItem.of(series, null);
  }

  @Test
  void theSummaryCountsTheSeriesByDownloadState() {
    DownloadSummary summary =
        DownloadSummary.of(
            List.of(
                series("1", null),
                series("2", new FakeImporter(true)),
                series("3", new FakeImporter(true)),
                series("4", new FakeImporter(false))));
    assertAll(
        () -> assertEquals(4, summary.total()),
        () -> assertEquals(1, summary.complete()),
        () -> assertEquals(2, summary.pending()),
        () -> assertEquals(1, summary.downloading()),
        () -> assertTrue(summary.hasPending()),
        () -> assertFalse(summary.isComplete()),
        () -> assertTrue(DownloadSummary.of(List.of(series("5", null))).isComplete()));
  }

  @Test
  void downloadResumesThePendingTransfersOnly() {
    FakeImporter pending = new FakeImporter(true);
    FakeImporter running = new FakeImporter(false);
    SeriesItem pendingItem = series("2", pending);
    SeriesItem runningItem = series("3", running);
    int started = ItemActions.download(List.of(series("1", null), pendingItem, runningItem));
    assertAll(
        () -> assertEquals(1, started),
        () -> assertTrue(pending.resumed),
        () -> assertFalse(running.resumed),
        () -> assertTrue(ItemActions.stop(runningItem)),
        () -> assertFalse(ItemActions.stop(pendingItem), "nothing was running"));
  }
}
