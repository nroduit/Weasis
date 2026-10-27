/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

import jakarta.json.JsonObject;
import java.io.IOException;
import java.util.Optional;
import org.pf4j.ExtensionPoint;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;

/**
 * A plugin that derives series from loaded ones (a subtraction, a projection) and can derive them
 * again from a small spec, so a session stores the spec rather than the pixels and a restore
 * rebuilds the series under the same identifiers.
 */
public interface DerivedSeriesProvider extends ExtensionPoint {

  /** The kind of derivation, the key a session stores the spec under. */
  String kind();

  /** The spec of a series this provider derived, empty for any other series. */
  Optional<JsonObject> describe(MediaSeries<? extends MediaElement> series);

  /**
   * Derives the series of a spec again and registers it in its explorer, with the identifiers the
   * spec always gives; nothing happens when it is already there. Off the event dispatch thread.
   *
   * @throws IOException when the series cannot be written
   * @throws IllegalStateException when its sources are not loaded or no longer qualify
   */
  void rebuild(JsonObject spec) throws IOException;
}
