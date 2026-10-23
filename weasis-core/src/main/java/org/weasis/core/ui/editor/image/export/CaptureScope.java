/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image.export;

import java.util.ArrayList;
import java.util.List;
import org.weasis.core.Messages;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;

/**
 * What area of the interface each captured frame covers. The scope also decides how the frame is
 * produced: a single 2D view is re-rendered off-screen, everything else is painted as displayed.
 */
public enum CaptureScope {
  VIEW("capture.scope.view"), // NON-NLS
  CONTAINER("capture.scope.container"), // NON-NLS
  APPLICATION_WINDOW("capture.scope.window"); // NON-NLS

  private final String key;

  CaptureScope(String key) {
    this.key = key;
  }

  /** The scopes that can be captured, narrowest first: a missing view or layout is left out. */
  public static CaptureScope[] available(ViewCanvas<?> view, ImageViewerPlugin<?> container) {
    List<CaptureScope> scopes = new ArrayList<>();
    if (view != null) {
      scopes.add(VIEW);
    }
    if (container != null) {
      scopes.add(CONTAINER);
    }
    scopes.add(APPLICATION_WINDOW);
    return scopes.toArray(new CaptureScope[0]);
  }

  @Override
  public String toString() {
    return Messages.getString(key);
  }
}
