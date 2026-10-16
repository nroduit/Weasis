/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.hp;

import java.util.List;
import java.util.Optional;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.ui.editor.image.HangingLayout;
import org.weasis.dicom.codec.hp.StudySeries;
import org.weasis.dicom.explorer.DicomModel;

/** The study lookups the hanging protocol UI of other plugins needs from the explorer. */
public final class HpStudies {

  private HpStudies() {}

  /** The DICOM model of the application, empty when no explorer is open. */
  public static Optional<DicomModel> model() {
    return Optional.ofNullable(HpCommands.findModel());
  }

  /** The image series of a study with their features. */
  public static List<StudySeries> studySeries(DicomModel model, MediaSeriesGroup study) {
    return HpCommands.studySeries(model, study);
  }

  /** The study of the first series a layout shows, empty when it shows none that is loaded. */
  public static Optional<MediaSeriesGroup> studyOf(DicomModel model, HangingLayout layout) {
    for (HangingLayout.Cell cell : layout.cells()) {
      String uid = cell.seriesId().orElse(null);
      if (uid != null) {
        Optional<MediaSeriesGroup> study =
            model.findSeries(uid).map(s -> model.getParent(s, DicomModel.study));
        if (study.isPresent()) {
          return study;
        }
      }
    }
    return Optional.empty();
  }
}
