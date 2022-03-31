/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire;

import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ViewCanvas;

/**
 * Group all shared and useful methods and attributes.
 *
 * @author Yannick LARVOR
 * @since 2.5.0
 */
public class AcquireObject {
  protected AcquireObject() {
    // Do nothing
  }

  /**
   * Retrieve current selected pane view
   *
   * @return Image view
   */
  public static ViewCanvas<ImageElement> getView() {
    return EventManager.getInstance().getSelectedViewPane();
  }

  /**
   * Retrieve current image info (UUID, processes, default values, etc...)
   *
   * @return Image info
   */
  public static AcquireImageInfo getImageInfo() {
    return AcquireManager.getCurrentAcquireImageInfo();
  }
}
