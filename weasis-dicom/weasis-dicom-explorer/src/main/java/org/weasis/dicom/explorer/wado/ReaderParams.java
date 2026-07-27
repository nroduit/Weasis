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

import java.util.Map;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.pref.node.DicomWebNode;

/** Shared state passed to the manifest parsers while building the {@link DicomModel}. */
class ReaderParams {
  private final DicomModel model;
  private final Map<String, LoadSeries> seriesMap;
  DicomWebNode wadoUri;
  boolean bulkSeriesRetrieve;

  ReaderParams(DicomModel model, Map<String, LoadSeries> seriesMap) {
    this.model = model;
    this.seriesMap = seriesMap;
  }

  DicomModel getModel() {
    return model;
  }

  Map<String, LoadSeries> getSeriesMap() {
    return seriesMap;
  }
}
