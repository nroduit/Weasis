/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.TabFocusPolicy;
import org.weasis.core.ui.editor.ViewerOpenOptions;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.dicom.explorer.HangingProtocols.OpeningViewer;

public class PluginOpeningStrategy {

  private final OpeningViewer openingMode;
  private final boolean closePreviousViewers;
  private boolean fullImportSession;
  private final Instant loadStartedAt;

  private final Set<MediaSeriesGroup> openPatients = Collections.synchronizedSet(new HashSet<>());

  public PluginOpeningStrategy(OpeningViewer openingMode) {
    this(openingMode, HangingProtocols.isClosePreviousFromPreferences());
  }

  public PluginOpeningStrategy(OpeningViewer openingMode, boolean closePreviousViewers) {
    this.openingMode = Objects.requireNonNullElse(openingMode, OpeningViewer.ALL_PATIENTS);
    this.closePreviousViewers = closePreviousViewers;
    this.fullImportSession = true;
    this.loadStartedAt = Instant.now();
  }

  public boolean containsPatient(MediaSeriesGroup patient) {
    return openPatients.contains(Objects.requireNonNull(patient));
  }

  public boolean isFullImportSession() {
    return fullImportSession;
  }

  public void setFullImportSession(boolean fullImportSession) {
    this.fullImportSession = fullImportSession;
  }

  public void reset() {
    if (fullImportSession) {
      openPatients.clear();
    }
  }

  public void addPatient(MediaSeriesGroup patient) {
    openPatients.add(Objects.requireNonNull(patient));
  }

  public void removePatient(MediaSeriesGroup patient) {
    openPatients.remove(patient);
  }

  public void prepareImport() {
    if (closePreviousViewers && (fullImportSession || openPatients.isEmpty())) {
      GuiUtils.getUICore()
          .closeSeriesViewer(new ArrayList<>(GuiUtils.getUICore().getViewerPlugins()));
    }
  }

  public void openViewerPlugin(
      MediaSeriesGroup patient, DicomModel dicomModel, Series<?> dicomSeries) {
    Objects.requireNonNull(dicomModel);
    Objects.requireNonNull(dicomSeries);

    if (DicomModel.isHiddenModality(dicomSeries)) {
      return;
    }

    if (OpeningViewer.NONE.equals(openingMode)) {
      return;
    }

    boolean isPatientOpen = containsPatient(patient);
    if (!isPatientOpen) {
      String mime = dicomSeries.getMimeType();
      SeriesViewerFactory plugin = GuiUtils.getUICore().getViewerFactory(mime);
      if (plugin != null
          && !("sr/dicom".equals(mime)) // NON-NLS
          && !(plugin instanceof MimeSystemAppFactory)) {
        // When viewers are already open, use an auto-by-duration policy so the new tab
        // opens in the background if loading takes longer than the threshold (better UX:
        // the user's current work is not interrupted by slow loads).
        // The loadStartedAt instant is captured when this PluginOpeningStrategy is constructed,
        // i.e. when local file loading begins or when manifest download starts.
        boolean hasExistingViewers = !GuiUtils.getUICore().getViewerPlugins().isEmpty();
        TabFocusPolicy focusPolicy =
            hasExistingViewers
                ? TabFocusPolicy.autoByDuration(
                    TabFocusPolicy.DEFAULT_AUTO_THRESHOLD, loadStartedAt)
                : TabFocusPolicy.foreground();
        ViewerOpenOptions opts = ViewerOpenOptions.defaults().withTabFocusPolicy(focusPolicy);
        addPatient(patient);
        new ViewerPluginBuilder(plugin, List.of(dicomSeries), dicomModel, opts).open();
      }
    }
  }
}
