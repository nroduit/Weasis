/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.qr;

import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.wado.LoadSeries;

/**
 * Series of a WADO-URI retrieve, queried with C-FIND and downloaded over HTTP. Its SOP instances
 * are listed when the download starts, so the archive is only asked for the content of a series
 * that is about to be transferred.
 */
public class LoadWadoUriSeries extends LoadSeries {

  private final RetrieveContext context;

  public LoadWadoUriSeries(
      DicomSeries dicomSeries,
      RetrieveContext context,
      int concurrentDownloads,
      boolean startDownloading) {
    super(dicomSeries, context.getDicomModel(), null, concurrentDownloads, true, startDownloading);
    this.context = context;
    setPOpeningStrategy(context.getOpeningStrategy());
  }

  /** Continues a stopped download, keeping the progress bar already shown on the thumbnail. */
  private LoadWadoUriSeries(LoadWadoUriSeries previous) {
    super(
        previous.getDicomSeries(),
        previous.context.getDicomModel(),
        null,
        previous.getProgressBar(),
        previous.getConcurrentDownloads(),
        true,
        previous.isStartDownloading());
    this.context = previous.context;
    setPOpeningStrategy(previous.getOpeningStrategy());
  }

  @Override
  protected Boolean doInBackground() {
    context.fillInstances(getDicomSeries());
    return super.doInBackground();
  }

  @Override
  protected LoadSeries createResumeTask() {
    return new LoadWadoUriSeries(this);
  }
}
