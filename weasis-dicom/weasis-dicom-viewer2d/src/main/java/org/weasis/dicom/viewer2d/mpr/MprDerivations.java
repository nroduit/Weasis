/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import jakarta.json.JsonObject;
import java.io.IOException;
import java.util.Optional;
import org.dcm4che3.data.Tag;
import org.pf4j.Extension;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.editor.DerivedSeriesProvider;
import org.weasis.dicom.codec.TagD;

/**
 * Lets a hanging protocol fill a cell with the reformat of a series, and a session keep the
 * reformats of a display and build them again on restore.
 */
@Extension
public class MprDerivations implements DerivedSeriesProvider {

  public static final String KIND = "mpr"; // NON-NLS

  @Override
  public String kind() {
    return KIND;
  }

  @Override
  public Optional<JsonObject> describe(MediaSeries<? extends MediaElement> series) {
    return ReformatService.find(TagD.getTagValue(series, Tag.SeriesInstanceUID, String.class))
        .map(ReformatService.Derived::spec);
  }

  @Override
  public void rebuild(JsonObject spec) throws IOException {
    ReformatService.create(
        spec.getString("series"), ReformatService.plane(spec.getString("plane"))); // NON-NLS
  }

  @Override
  public MediaSeries<? extends MediaElement> derive(String series, String mask, JsonObject params)
      throws IOException {
    String plane = params == null ? null : params.getString("plane", null); // NON-NLS
    if (plane == null) {
      throw new IllegalArgumentException("A reformat needs a plane");
    }
    return ReformatService.create(series, ReformatService.plane(plane)).series();
  }
}
