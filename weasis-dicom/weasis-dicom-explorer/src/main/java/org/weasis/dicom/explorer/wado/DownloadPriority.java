/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.wado;

import java.util.concurrent.atomic.AtomicInteger;
import org.weasis.core.api.media.data.MediaSeriesGroup;

public class DownloadPriority {

  public static final AtomicInteger COUNTER = new AtomicInteger(Integer.MAX_VALUE - 1);
  private final MediaSeriesGroup patient;
  private final MediaSeriesGroup study;
  private final MediaSeriesGroup series;
  private final boolean concurrentDownload;
  private Integer priority;

  public DownloadPriority(
      MediaSeriesGroup patient,
      MediaSeriesGroup study,
      MediaSeriesGroup series,
      boolean concurrentDownload) {
    this.patient = patient;
    this.study = study;
    this.series = series;
    this.concurrentDownload = concurrentDownload;
    priority = Integer.MAX_VALUE;
  }

  public MediaSeriesGroup getPatient() {
    return patient;
  }

  public MediaSeriesGroup getStudy() {
    return study;
  }

  public MediaSeriesGroup getSeries() {
    return series;
  }

  public boolean hasConcurrentDownload() {
    return concurrentDownload;
  }

  public Integer getPriority() {
    return priority;
  }

  public void setPriority(Integer priority) {
    this.priority = priority == null ? Integer.MAX_VALUE : priority;
  }
}
