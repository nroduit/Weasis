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

import java.util.List;
import org.weasis.core.ui.model.layer.LayerItem;

/**
 * A view that cannot be re-rendered off-screen, such as the 3D view, so a capture paints it live.
 * It applies a {@link DisplayProfile} for the time of that one paint only, and leaves out what
 * belongs to the screen alone (progress messages).
 */
public interface LiveCaptureView {

  /** The annotation items its Display tool offers, the only ones a profile can change. */
  List<LayerItem> getCaptureItems();

  /**
   * Prepares one paint for capture under {@code profile}, or as displayed when it is {@code null}.
   *
   * @return the action that restores the display, to run right after the paint
   */
  Runnable beginCapture(DisplayProfile profile);
}
