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

import java.awt.Cursor;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.Feature.BasicActionStateValue;
import org.weasis.core.api.gui.util.Feature.ComboItemListenerValue;
import org.weasis.core.api.gui.util.Feature.SliderChangeListenerValue;
import org.weasis.core.api.gui.util.Feature.ToggleButtonListenerValue;
import org.weasis.dicom.viewer2d.mip.MipView;
import org.weasis.dicom.viewer3d.dockable.SegmentationTool;
import org.weasis.dicom.viewer3d.geometry.ArcballMouseListener;
import org.weasis.dicom.viewer3d.geometry.Axis;
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

  public static final class ControlsListenerValue extends Feature<ArcballMouseListener> {
    public ControlsListenerValue(
        String title, String command, int keyEvent, int modifier, Cursor cursor) {
      super(title, command, keyEvent, modifier, cursor);
    }
  }

  public static final ComboItemListenerValue<Preset> VOL_PRESET =
      new ComboItemListenerValue<>(ActionW.LUT.getTitle(), "vol.lut", 0, 0, null);

  public static final ComboItemListenerValue<Axis> VOL_AXIS =
      new ComboItemListenerValue<>(Messages.getString("axis"), "vol.axis", 0, 0, null);

  public static final SliderChangeListenerValue VOL_QUALITY =
      new SliderChangeListenerValue(
          Messages.getString("z.axis.sampling"), "vol.quality", 0, 0, null);

  public static final ToggleButtonListenerValue VOL_SLICING =
      new ToggleButtonListenerValue(Messages.getString("slicing"), "vol.slicing", 0, 0, null);

  public static final ToggleButtonListenerValue VOL_SHADING =
      new ToggleButtonListenerValue(Messages.getString("shading"), "vol.shading", 0, 0, null);

  public static final ToggleButtonListenerValue VOL_PROJECTION =
      new ToggleButtonListenerValue(
          Messages.getString("orthographic.projection"), "vol.projection", 0, 0, null);

  public static final ComboItemListenerValue<RenderingType> RENDERING_TYPE =
      new ComboItemListenerValue<>(Messages.getString("type"), "rendering.type", 0, 0, null);

  public static final ComboItemListenerValue<MipView.Type> MIP_TYPE =
      new ComboItemListenerValue<>("MIP", "mip.type", 0, 0, null);

  public static final SliderChangeListenerValue MIP_DEPTH =
      new SliderChangeListenerValue(Messages.getString("mip.depth"), "mip.depth", 0, 0, null);

  public static final SliderChangeListenerValue VOL_OPACITY =
      new SliderChangeListenerValue(Messages.getString("opacity"), "vol.opacity", 0, 0, null);

  public static final BasicActionStateValue ORIENTATION_CUBE =
      new BasicActionStateValue(
          Messages.getString("orientation.cube"), "crosshair-cube", 0, 0, null); // NON-NLS

  public static final ComboItemListenerValue<SegmentationTool.Type> SEG_TYPE =
      new ComboItemListenerValue<>(Messages.getString("type"), "seg.type", 0, 0, null);
}
