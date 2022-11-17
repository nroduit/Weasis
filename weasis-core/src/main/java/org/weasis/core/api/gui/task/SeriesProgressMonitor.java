/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.task;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.Objects;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesImporter;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnail;

public class SeriesProgressMonitor extends FilterInputStream {
  private static final String INTERRUPTION_LABEL = "progress"; // NON-NLS
  protected final Series<?> series;
  protected int nread = 0;
  protected int size;

  public SeriesProgressMonitor(final Series<?> series, InputStream in) {
    super(in);
    this.series = Objects.requireNonNull(series);
    try {
      size = in.available();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  protected boolean isLoadingSeriesCanceled() {
    SeriesImporter loader = series.getSeriesLoader();
    return loader == null || loader.isStopped();
  }

  protected void updateSeriesProgression(long addSize) {
    series.setFileSize(series.getFileSize() + addSize);
    GuiExecutor.instance()
        .execute(
            () -> {
              Thumbnail thumb = (Thumbnail) series.getTagValue(TagW.Thumbnail);
              if (thumb != null) {
                thumb.repaint();
              }
            });
  }

  @Override
  public int read() throws IOException {
    if (isLoadingSeriesCanceled()) {
      InterruptedIOException exc = new InterruptedIOException(INTERRUPTION_LABEL);
      exc.bytesTransferred = nread;
      series.setFileSize(series.getFileSize() - nread);
      nread = 0;
      throw exc;
    }
    int c = in.read();
    if (c >= 0) {
      nread++;
      updateSeriesProgression(1);
    }

    return c;
  }

  @Override
  public int read(byte[] b) throws IOException {
    if (isLoadingSeriesCanceled()) {
      InterruptedIOException exc = new InterruptedIOException(INTERRUPTION_LABEL);
      exc.bytesTransferred = nread;
      series.setFileSize(series.getFileSize() - nread);
      nread = 0;
      throw exc;
    }
    int nr = in.read(b);
    if (nr > 0) {
      nread += nr;
      updateSeriesProgression(nr);
    }
    return nr;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    if (isLoadingSeriesCanceled()) {
      InterruptedIOException exc = new InterruptedIOException(INTERRUPTION_LABEL);
      exc.bytesTransferred = nread;
      series.setFileSize(series.getFileSize() - nread);
      nread = 0;
      throw exc;
    }
    int nr = in.read(b, off, len);
    if (nr > 0) {
      nread += nr;
      updateSeriesProgression(nr);
    }
    return nr;
  }

  @Override
  public long skip(long n) throws IOException {
    long nr = in.skip(n);
    if (nr > 0) {
      nread += (int) nr;
      updateSeriesProgression(nr);
    }
    return nr;
  }

  @Override
  public synchronized void reset() throws IOException {
    in.reset();
    nread = size - in.available();
    updateSeriesProgression(nread);
  }
}
