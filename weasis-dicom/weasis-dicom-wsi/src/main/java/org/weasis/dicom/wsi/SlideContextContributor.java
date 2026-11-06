/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi;

import java.util.Map;
import org.pf4j.Extension;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.editor.ContextFacts;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.ViewContextContributor;

/**
 * Asks for the slide reading profile when a slide viewer is selected: the left button pans and the
 * wheel zooms, as in pathology viewers, and the measurement tools follow the pathology profile. A
 * hanging protocol that names a profile keeps it.
 */
@Extension
public class SlideContextContributor implements ViewContextContributor {

  /** The built-in UI profile of the slide viewer. */
  public static final String UI_PROFILE = "weasis.sm"; // NON-NLS

  @Override
  public void contribute(SeriesViewer<?> viewer, MediaSeries<?> series, Map<String, Object> facts) {
    if (viewer instanceof WsiContainer) {
      facts.putIfAbsent(ContextFacts.UI_PROFILE, UI_PROFILE);
    }
  }
}
