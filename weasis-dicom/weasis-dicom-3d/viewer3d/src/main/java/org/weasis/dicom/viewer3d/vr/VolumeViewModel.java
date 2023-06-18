/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr;

import org.weasis.core.ui.model.utils.imp.DefaultViewModel;

public class VolumeViewModel extends DefaultViewModel {

  @Override
  public double getModelOffsetX() {
    if (hasContent()) {}
    return 0;
  }

  @Override
  public double getModelOffsetY() {
    if (hasContent()) {}
    return 0;
  }

  @Override
  public double getViewScale() {
    if (hasContent()) {
      double displayZoom = 1;
      //     double displayZoom = getActualDisplayZoom();
      if (displayZoom <= 0) {
        displayZoom = 1;
      }
      return displayZoom;
    }
    return 1;
  }

  public boolean hasContent() {
    //  return (getVolTexture() != null);
    return false;
  }
}
