/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;

public class DefaultSynchManager<E extends ImageElement> extends SynchManager<E> {

  public DefaultSynchManager(ImageViewerEventManager<E> eventManager) {
    super(eventManager);
  }

  @Override
  public void updateAllListeners(ImageViewerPlugin<E> viewerPlugin, SynchView synchView) {
    // Non DICOM cannot be synchronized
  }

  @Override
  public boolean hasSameOrientation(MediaSeries series1, MediaSeries series2) {
    return false;
  }
}
