/*
 * Copyright (c) 2012 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d;

import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Feature.BasicActionStateValue;
import org.weasis.core.api.gui.util.Feature.ComboItemListenerValue;
import org.weasis.core.api.gui.util.Feature.SliderChangeListenerValue;
import org.weasis.core.api.gui.util.Feature.ToggleButtonListenerValue;
import org.weasis.dicom.viewer3d.vr.Preset;
import org.weasis.dicom.viewer3d.vr.RenderingType;

public class ActionVol {

  private ActionVol() {}

  public static final SliderChangeListenerValue SCROLLING =
      new SliderChangeListenerValue(
          ActionW.SCROLL_SERIES.getTitle(),
          ActionW.SCROLL_SERIES.cmd(),
          ActionW.SCROLL_SERIES.getKeyCode(),
          ActionW.SCROLL_SERIES.getModifier(),
          ActionW.SCROLL_SERIES.getCursor());

  public static final ComboItemListenerValue<Preset> VOL_PRESET =
      new ComboItemListenerValue<>(ActionW.LUT.getTitle(), "vol.lut", 0, 0, null);

  public static final SliderChangeListenerValue VOL_QUALITY =
      new SliderChangeListenerValue("Rendering Quality", "vol.quality", 0, 0, null);

  public static final ToggleButtonListenerValue VOL_SLICING =
      new ToggleButtonListenerValue("Slicing", "vol.slicing", 0, 0, null);

  public static final ToggleButtonListenerValue VOL_SHADING =
      new ToggleButtonListenerValue("Shading", "vol.shading", 0, 0, null);

  public static final ComboItemListenerValue<RenderingType> RENDERING_TYPE =
      new ComboItemListenerValue<>("Type", "rendering.type", 0, 0, null);

  public static final SliderChangeListenerValue MIP_DEPTH =
      new SliderChangeListenerValue("Mip Depth", "mip.depth", 0, 0, null);

  public static final SliderChangeListenerValue VOL_OPACITY =
      new SliderChangeListenerValue("Opacity", "vol.opacity", 0, 0, null);

  public static final BasicActionStateValue HIDE_CROSSHAIR_CENTER =
      new BasicActionStateValue(
          Messages.getString("hide.crosshair.center"),
          "crosshair-hide-center",
          0,
          0,
          null); // NON-NLS

  public static final BasicActionStateValue RECENTERING_CROSSHAIR =
      new BasicActionStateValue(
          Messages.getString("recentering.crosshair"), "crosshair-recenter", 0, 0, null); // NON-NLS

  public static final BasicActionStateValue ORIENTATION_CUBE =
      new BasicActionStateValue(
          Messages.getString("orientation.cube"), "crosshair-cube", 0, 0, null); // NON-NLS
}
