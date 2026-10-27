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

import org.weasis.core.api.gui.util.Feature.ComboItemListenerValue;
import org.weasis.core.api.gui.util.Feature.SliderChangeListenerValue;
import org.weasis.core.api.gui.util.Feature.ToggleButtonListenerValue;
import org.weasis.dicom.viewer2d.Messages;

/**
 * The event-manager actions behind the subtraction section of the image tool: the live preview
 * toggle, the mask series and the weight of the mask. The mask and the weight drive the preview
 * view when one is selected and the next build otherwise, through {@link SubtractionController}.
 */
public final class SubtractionAction {

  /** Shows the difference live in the selected view, before any series is built. */
  public static final ToggleButtonListenerValue PREVIEW =
      new ToggleButtonListenerValue(
          Messages.getString("SubtractionTool.preview"),
          "subtraction.preview",
          0,
          0,
          null); // NON-NLS

  public static final ComboItemListenerValue<Object> MASK =
      new ComboItemListenerValue<>(
          Messages.getString("SubtractionTool.mask"), "subtraction.mask", 0, 0, null); // NON-NLS

  /** Weight of the mask in percent, 100 subtracting it fully. */
  public static final SliderChangeListenerValue FACTOR =
      new SliderChangeListenerValue(
          Messages.getString("SubtractionTool.factor"),
          "subtraction.factor",
          0,
          0,
          null); // NON-NLS

  /** Sub-pixel shift of the mask of a run, in rows (pixels), positive downwards. */
  public static final SliderChangeListenerValue SHIFT_ROWS =
      new SliderChangeListenerValue(
          Messages.getString("SubtractionTool.shift_rows"),
          "subtraction.shift.rows",
          0,
          0,
          null); // NON-NLS

  /** Sub-pixel shift of the mask of a run, in columns (pixels), positive to the right. */
  public static final SliderChangeListenerValue SHIFT_COLUMNS =
      new SliderChangeListenerValue(
          Messages.getString("SubtractionTool.shift_columns"),
          "subtraction.shift.columns",
          0,
          0,
          null); // NON-NLS

  private SubtractionAction() {}
}
