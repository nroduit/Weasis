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

import java.util.Map;
import org.pf4j.Extension;
import org.weasis.core.api.media.data.MediaSeries;

import org.weasis.core.ui.editor.ContextFacts;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.ViewContextContributor;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.dicom.codec.hp.SeriesFeatures;

/**
 * The hanging facts of the view context: the protocol that hung the viewer and its reading step
 * ({@code hp.protocol}, {@code hp.protocolName}, {@code hp.step} from 1, {@code hp.steps}), and the
 * features of the selected series ({@code hp.features}, a {@link SeriesFeatures}; {@code
 * hp.modality}, {@code hp.anatomy} as text for predicates that need no dependency).
 */
@Extension
public class HangingContextContributor implements ViewContextContributor {

  public static final String PROTOCOL = ContextFacts.HANGING_PROTOCOL;
  public static final String PROTOCOL_NAME = "hp.protocolName"; // NON-NLS
  public static final String STEP = "hp.step"; // NON-NLS
  public static final String STEPS = "hp.steps"; // NON-NLS
  public static final String FEATURES = "hp.features"; // NON-NLS
  public static final String MODALITY = "hp.modality"; // NON-NLS
  public static final String ANATOMY = "hp.anatomy"; // NON-NLS

  @Override
  public void contribute(SeriesViewer<?> viewer, MediaSeries<?> series, Map<String, Object> facts) {
    if (viewer instanceof ViewerPlugin<?> plugin) {
      HangingPlanApplier.activeOn(plugin)
          .ifPresent(
              applied -> {
                facts.put(PROTOCOL, applied.plan().protocolId());
                facts.put(PROTOCOL_NAME, applied.plan().name());
                facts.put(STEP, applied.step() + 1);
                facts.put(STEPS, applied.plan().steps().size());
              });
    }
    SeriesFeatures features = ContentContextContributor.featuresOf(series);
    if (features != null) {
      facts.put(FEATURES, features);
      if (features.modality() != null) {
        facts.put(MODALITY, features.modality());
      }
      if (features.anatomy() != null) {
        facts.put(ANATOMY, features.anatomy().notation());
      }
    }
  }
}
