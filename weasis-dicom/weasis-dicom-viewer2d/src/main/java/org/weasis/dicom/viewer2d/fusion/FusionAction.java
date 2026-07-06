/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.fusion;

import org.weasis.core.api.gui.util.Feature.ComboItemListenerValue;
import org.weasis.core.api.gui.util.Feature.SliderChangeListenerValue;
import org.weasis.core.api.gui.util.Feature.ToggleButtonListenerValue;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.opencv.op.lut.ByteLut;

/**
 * EventManager action identifiers for the PET/CT fusion controls. The command strings match the
 * {@link FusionOp} parameters so the values flow straight to the op. The series combo is typed
 * {@code <Object>} (like {@code ActionW.PRESET}) so its list can be replaced dynamically per study.
 */
public final class FusionAction {

  public static final ToggleButtonListenerValue ENABLE =
      new ToggleButtonListenerValue(
          Messages.getString("FusionTool.enable"), FusionOp.P_FUSION_ENABLED, 0, 0, null);

  public static final ComboItemListenerValue<Object> SERIES =
      new ComboItemListenerValue<>(
          Messages.getString("FusionTool.series"), FusionOp.P_FUSION_SERIES, 0, 0, null);

  public static final ComboItemListenerValue<ByteLut> LUT =
      new ComboItemListenerValue<>(
          Messages.getString("FusionTool.lut"), FusionOp.P_FUSION_LUT, 0, 0, null);

  public static final SliderChangeListenerValue BASE_OPACITY =
      new SliderChangeListenerValue(
          Messages.getString("FusionTool.opacity"), FusionOp.P_OPACITY_BASE, 0, 0, null);

  public static final SliderChangeListenerValue OVERLAY_OPACITY =
      new SliderChangeListenerValue(
          Messages.getString("FusionTool.opacity"), FusionOp.P_OPACITY_OVERLAY, 0, 0, null);

  private FusionAction() {}
}
