/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import java.awt.Cursor;
import java.awt.event.KeyEvent;
import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.util.Feature.BasicActionStateValue;
import org.weasis.core.api.gui.util.Feature.BooleanValue;
import org.weasis.core.api.gui.util.Feature.ComboItemListenerValue;
import org.weasis.core.api.gui.util.Feature.CrosshairListenerValue;
import org.weasis.core.api.gui.util.Feature.DoubleValue;
import org.weasis.core.api.gui.util.Feature.PannerListenerValue;
import org.weasis.core.api.gui.util.Feature.SliderChangeListenerValue;
import org.weasis.core.api.gui.util.Feature.SliderCineListenerValue;
import org.weasis.core.api.gui.util.Feature.ToggleButtonListenerValue;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.op.ByteLut;
import org.weasis.core.api.image.util.KernelData;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.model.graphic.Graphic;

public class ActionW {
  public static final String DRAW_CMD_PREFIX = "draw.sub."; // NON-NLS
  public static final BasicActionStateValue NO_ACTION =
      new BasicActionStateValue(
          Messages.getString("ActionW.no"), "none", KeyEvent.VK_N, 0, null); // NON-NLS

  public static final ComboItemListenerValue<SynchView> SYNCH =
      new ComboItemListenerValue<>(
          Messages.getString("ActionW.synch"), "synch", 0, 0, null); // NON-NLS
  public static final BasicActionStateValue VIEW_MODE =
      new BasicActionStateValue(Messages.getString("ActionW.view_mode"), "viewMode", 0, 0, null);
  public static final SliderChangeListenerValue ZOOM =
      new SliderChangeListenerValue(
          Messages.getString("ActionW.zoom"),
          "zoom", // NON-NLS
          KeyEvent.VK_Z,
          0,
          Feature.getSvgCursor("zoom.svg", Messages.getString("ActionW.zoom"), 0.5f, 0.5f));
  public static final SliderCineListenerValue SCROLL_SERIES =
      new SliderCineListenerValue(
          Messages.getString("ActionW.scroll"),
          "sequence", // NON-NLS
          KeyEvent.VK_S,
          0,
          Feature.getSvgCursor("sequence.svg", Messages.getString("ActionW.scroll"), 0.5f, 0.5f));
  public static final SliderChangeListenerValue ROTATION =
      new SliderChangeListenerValue(
          Messages.getString("ActionW.rotate"),
          "rotation", // NON-NLS
          KeyEvent.VK_R,
          0,
          Feature.getSvgCursor("rotation.svg", Messages.getString("ActionW.rotate"), 0.5f, 0.5f));

  public static final BasicActionStateValue CINESPEED =
      new BasicActionStateValue(
          Messages.getString("ActionW.speed"), "cinespeed", 0, 0, null); // NON-NLS
  public static final BasicActionStateValue CINESTART =
      new BasicActionStateValue(
          Messages.getString("ActionW.start"), "cinestart", KeyEvent.VK_C, 0, null); // NON-NLS
  public static final BasicActionStateValue CINESTOP =
      new BasicActionStateValue(
          Messages.getString("ActionW.stop"), "cinestop", 0, 0, null); // NON-NLS
  public static final Feature<SliderChangeListener> WINDOW =
      new SliderChangeListenerValue(
          Messages.getString("ActionW.win"), "window", 0, 0, null); // NON-NLS
  public static final SliderChangeListenerValue LEVEL =
      new SliderChangeListenerValue(Messages.getString("ActionW.level"), "level", 0, 0, null);
  public static final BasicActionStateValue WINLEVEL =
      new BasicActionStateValue(
          Messages.getString("ActionW.wl"),
          "winLevel",
          KeyEvent.VK_W,
          0,
          Feature.getSvgCursor("winLevel.svg", Messages.getString("ActionW.wl"), 0.5f, 0.5f));
  public static final DoubleValue LEVEL_MIN =
      new DoubleValue("", "level_min", 0, 0, null); // NON-NLS
  public static final DoubleValue LEVEL_MAX =
      new DoubleValue("", "level_max", 0, 0, null); // NON-NLS

  public static final ToggleButtonListenerValue FLIP =
      new ToggleButtonListenerValue(
          Messages.getString("ActionW.flip"), "flip", 0, 0, null); // NON-NLS
  public static final ComboItemListenerValue<Object> PRESET =
      new ComboItemListenerValue<>(
          Messages.getString("ActionW.preset"), "preset", 0, 0, null); // NON-NLS
  public static final ToggleButtonListenerValue DEFAULT_PRESET =
      new ToggleButtonListenerValue("", "default_preset", 0, 0, null); // NON-NLS
  public static final ComboItemListenerValue<Object> LUT_SHAPE =
      new ComboItemListenerValue<>(
          Messages.getString("ActionW.lut_shape"), "lut_shape", 0, 0, null); // NON-NLS
  public static final ComboItemListenerValue<ByteLut> LUT =
      new ComboItemListenerValue<>(Messages.getString("ActionW.lut"), "lut", 0, 0, null); // NON-NLS
  public static final ToggleButtonListenerValue INVERT_LUT =
      new ToggleButtonListenerValue(
          Messages.getString("ActionW.invert_lut"), "inverseLut", 0, 0, null);
  public static final BasicActionStateValue RESET =
      new BasicActionStateValue(
          Messages.getString("ActionW.Reset"), "reset", 0, 0, null); // NON-NLS
  public static final BasicActionStateValue SHOW_HEADER =
      new BasicActionStateValue(
          Messages.getString("ActionW.show_header"), "show_header", 0, 0, null); // NON-NLS
  public static final BasicActionStateValue EXPORT_VIEW =
      new BasicActionStateValue(Messages.getString("exporting.view"), "exportImage", 0, 0, null);
  public static final PannerListenerValue PAN =
      new PannerListenerValue(
          Messages.getString("ActionW.pan"),
          "pan", // NON-NLS
          KeyEvent.VK_T,
          0,
          Feature.getSvgCursor("pan.svg", Messages.getString("ActionW.pan"), 0.5f, 0.5f));
  public static final BasicActionStateValue DRAWINGS =
      new BasicActionStateValue(
          Messages.getString("ActionW.draw"), "drawings", 0, 0, null); // NON-NLS
  public static final BasicActionStateValue MEASURE =
      new BasicActionStateValue(
          Messages.getString("ActionW.measure"), "measure", KeyEvent.VK_M, 0, null) { // NON-NLS
        @Override
        public boolean isDrawingAction() {
          return true;
        }
      };
  public static final BasicActionStateValue DRAW =
      new BasicActionStateValue(
          Messages.getString("ActionW.draws"), "draw", KeyEvent.VK_G, 0, null) { // NON-NLS
        @Override
        public boolean isDrawingAction() {
          return true;
        }
      };
  // Starting cmd by "draw.sub." defines a derivative action
  public static final ComboItemListenerValue<Graphic> DRAW_MEASURE =
      new ComboItemListenerValue<>(
          Messages.getString("ActionW.measurement"), DRAW_CMD_PREFIX + MEASURE.cmd(), 0, 0, null);
  public static final ComboItemListenerValue<Graphic> DRAW_GRAPHICS =
      new ComboItemListenerValue<>(
          Messages.getString("ActionW.draw"), DRAW_CMD_PREFIX + DRAW.cmd(), 0, 0, null);
  public static final ComboItemListenerValue<Unit> SPATIAL_UNIT =
      new ComboItemListenerValue<>(
          Messages.getString("ActionW.spatial_unit"), "spunit", 0, 0, null); // NON-NLS
  public static final ComboItemListenerValue<SeriesComparator<?>> SORT_STACK =
      new ComboItemListenerValue<>("", "sortStack", 0, 0, null); // NON-NLS
  public static final BasicActionStateValue CONTEXTMENU =
      new BasicActionStateValue(
          Messages.getString("ActionW.context_menu"), "contextMenu", KeyEvent.VK_Q, 0, null);
  public static final Feature VIEWING_PROTOCOL =
      new BooleanValue("", "viewingProtocol", 0, 0, null); // NON-NLS
  public static final ComboItemListenerValue<GridBagLayoutModel> LAYOUT =
      new ComboItemListenerValue<>(
          Messages.getString("ActionW.layout"), "layout", 0, 0, null); // NON-NLS
  public static final Feature MODE =
      new BooleanValue(Messages.getString("ActionW.switch_mode"), "mode", 0, 0, null); // NON-NLS
  public static final ToggleButtonListenerValue IMAGE_OVERLAY =
      new ToggleButtonListenerValue(
          Messages.getString("ActionW.overlay"), "overlay", 0, 0, null); // NON-NLS
  public static final ToggleButtonListenerValue PR_STATE =
      new ToggleButtonListenerValue(
          Messages.getString("ActionW.PR"), "pr_state", 0, 0, null); // NON-NLS
  public static final ToggleButtonListenerValue KO_TOGGLE_STATE =
      new ToggleButtonListenerValue(
          Messages.getString("ActionW.toggle_ko"),
          "ko_toogle_state", // NON-NLS
          KeyEvent.VK_K,
          0,
          null);
  public static final ComboItemListenerValue<Object> KO_SELECTION =
      new ComboItemListenerValue<>(
          Messages.getString("ActionW.select_ko"), "ko_selection", 0, 0, null); // NON-NLS
  public static final ToggleButtonListenerValue KO_FILTER =
      new ToggleButtonListenerValue(
          Messages.getString("ActionW.filter_ko"), "ko_filter", 0, 0, null); // NON-NLS
  public static final Feature IMAGE_PIX_PADDING =
      new BooleanValue(
          Messages.getString("ActionW.pixpad"), "pixel_padding", 0, 0, null); // NON-NLS
  public static final Feature IMAGE_SHUTTER =
      new BooleanValue(Messages.getString("ActionW.shutter"), "shutter", 0, 0, null); // NON-NLS
  public static final ToggleButtonListenerValue INVERSE_STACK =
      new ToggleButtonListenerValue("", "inverseStack", 0, 0, null); // NON-NLS
  public static final Feature STACK_OFFSET =
      new BooleanValue("", "stackOffset", 0, 0, null); // NON-NLS
  public static final Feature SYNCH_LINK = new BooleanValue("", "synchLink", 0, 0, null); // NON-NLS
  public static final Feature SYNCH_CROSSLINE =
      new BooleanValue("", "synchCrossline", 0, 0, null); // NON-NLS
  public static final ToggleButtonListenerValue LENS =
      new ToggleButtonListenerValue("", "showLens", 0, 0, null); // NON-NLS
  public static final ComboItemListenerValue<KernelData> FILTER =
      new ComboItemListenerValue<>("", "filter", 0, 0, null); // NON-NLS
  public static final Feature CROP = new BooleanValue("", "crop", 0, 0, null); // NON-NLS
  public static final Feature PREPROCESSING =
      new BooleanValue("", "preprocessing", 0, 0, null); // NON-NLS
  public static final SliderChangeListenerValue LENS_ZOOM =
      new SliderChangeListenerValue("", "lensZoom", 0, 0, null); // NON-NLS
  public static final Feature LENS_PAN = new BooleanValue("", "lensPan", 0, 0, null); // NON-NLS
  public static final ToggleButtonListenerValue DRAW_ONLY_ONCE =
      new ToggleButtonListenerValue(
          Messages.getString("ActionW.draw_once"), "drawOnce", 0, 0, null);
  public static final Feature PROGRESSION =
      new BooleanValue("", "img_progress", 0, 0, null); // NON-NLS
  public static final Feature FILTERED_SERIES =
      new BooleanValue("", "filter_series", 0, 0, null); // NON-NLS
  public static final CrosshairListenerValue CROSSHAIR =
      new CrosshairListenerValue(
          Messages.getString("ActionW.crosshair"),
          "crosshair", // NON-NLS
          KeyEvent.VK_H,
          0,
          new Cursor(Cursor.CROSSHAIR_CURSOR));
}
