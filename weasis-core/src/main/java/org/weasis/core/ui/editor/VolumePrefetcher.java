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

import org.pf4j.ExtensionPoint;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;

/**
 * A plugin that builds the volume of a series ahead of its use, so a reformat, a volume rendering
 * or a derived series asked later finds it ready. A hanging protocol calls it for the series its
 * coming steps will reslice.
 */
public interface VolumePrefetcher extends ExtensionPoint {

  /** Starts the build in the background, if the series qualifies; never blocks. */
  void prefetch(MediaSeries<? extends MediaElement> series);
}
