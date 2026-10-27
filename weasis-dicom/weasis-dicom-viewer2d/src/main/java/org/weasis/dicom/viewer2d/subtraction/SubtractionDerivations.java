/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.subtraction;

import jakarta.json.JsonObject;
import java.io.IOException;
import java.util.Optional;
import org.pf4j.Extension;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.editor.DerivedSeriesProvider;

/** Lets a session keep the spec of a subtraction series and build it again on restore. */
@Extension
public class SubtractionDerivations implements DerivedSeriesProvider {

  public static final String KIND = "subtraction"; // NON-NLS

  @Override
  public String kind() {
    return KIND;
  }

  @Override
  public Optional<JsonObject> describe(MediaSeries<? extends MediaElement> series) {
    return SubtractionService.find(SubtractionCommands.uidOf(series))
        .map(derived -> derived.spec().toJson().build());
  }

  @Override
  public void rebuild(JsonObject spec) throws IOException {
    SubtractionService.create(SubtractionSpec.fromJson(spec));
  }
}
