/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.util.HashMap;
import java.util.Map;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GUIEntry;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.image.SynchData.Mode;

public class SynchView implements GUIEntry {
  public static final SynchView DEFAULT_TILE;
  public static final SynchView DEFAULT_STACK;

  static {
    HashMap<String, Boolean> actions = new HashMap<>();
    actions.put(ActionW.SCROLL_SERIES.cmd(), true);
    actions.put(ActionW.PAN.cmd(), true);
    actions.put(ActionW.ZOOM.cmd(), true);
    actions.put(ActionW.ROTATION.cmd(), true);
    actions.put(ActionW.FLIP.cmd(), true);
    actions.put(ActionW.WINDOW.cmd(), true);
    actions.put(ActionW.LEVEL.cmd(), true);
    actions.put(ActionW.PRESET.cmd(), true);
    actions.put(ActionW.LUT_SHAPE.cmd(), true);
    actions.put(ActionW.LUT.cmd(), true);
    actions.put(ActionW.INVERT_LUT.cmd(), true);
    actions.put(ActionW.FILTER.cmd(), true);
    actions.put(ActionW.INVERSE_STACK.cmd(), true);
    actions.put(ActionW.SORT_STACK.cmd(), true);
    actions.put(ActionW.SPATIAL_UNIT.cmd(), true);
    DEFAULT_TILE =
        new SynchView(
            Messages.getString("SynchView.def_t"),
            "Tile", // NON-NLS
            Mode.TILE,
            true,
            ActionIcon.TILE,
            actions);

    actions = new HashMap<>();
    // Stack mode default: only Scroll is propagated. Every other option is initialized to false
    // so the user must explicitly enable Pan / Zoom / W/L / etc. (per-view via the sync popup
    // or globally via the toolbar dropdown). Keys are still listed so the popup checkboxes
    // render with explicit "off" states (rather than appearing as missing entries).
    actions.put(ActionW.SCROLL_SERIES.cmd(), true);
    actions.put(ActionW.PAN.cmd(), false);
    actions.put(ActionW.ZOOM.cmd(), false);
    actions.put(ActionW.ROTATION.cmd(), false);
    actions.put(ActionW.FLIP.cmd(), false);
    actions.put(ActionW.WINDOW.cmd(), false);
    actions.put(ActionW.LEVEL.cmd(), false);
    actions.put(ActionW.PRESET.cmd(), false);
    actions.put(ActionW.LUT_SHAPE.cmd(), false);
    actions.put(ActionW.LUT.cmd(), false);
    actions.put(ActionW.INVERT_LUT.cmd(), false);
    actions.put(ActionW.FILTER.cmd(), false);
    actions.put(ActionW.SPATIAL_UNIT.cmd(), false);
    DEFAULT_STACK =
        new SynchView(
            Messages.getString("SynchView.def_s"),
            "Stack", // NON-NLS
            Mode.STACK,
            false,
            ActionIcon.SEQUENCE,
            actions);
  }

  private final String name;
  private final String command;
  private final FlatSVGIcon svgIcon;
  private SynchData synchData;
  private SynchData originalSynchData;

  public SynchView(
      String name,
      String command,
      Mode mode,
      boolean synch,
      ActionIcon icon,
      Map<String, Boolean> actions) {
    if (name == null) {
      throw new IllegalArgumentException("A parameter is null!");
    }
    this.synchData = new SynchData(mode, actions, synch);
    this.originalSynchData = new SynchData(mode, actions, synch);
    this.name = name;
    this.command = command;
    this.svgIcon = ResourceUtil.getIcon(icon);
  }

  public String getName() {
    return name;
  }

  public SynchData getSynchData() {
    return synchData;
  }

  public void resetSynchData() {
    this.synchData = originalSynchData.copy();
  }

  /**
   * Set the enabled state of a synchronizable action and persist it across {@link
   * #resetSynchData()} calls (which happen on layout changes and viewer container creation).
   *
   * <p>Updates both the live {@link #synchData} and the {@link #originalSynchData} snapshot so user
   * preferences survive view/layout changes.
   *
   * @param cmd the action command (e.g. {@code ActionW.ZOOM.cmd()})
   * @param enabled whether the action should be propagated to other views
   */
  public void setActionEnabled(String cmd, boolean enabled) {
    if (cmd == null) {
      return;
    }
    synchData.getActions().put(cmd, enabled);
    originalSynchData.getActions().put(cmd, enabled);
  }

  public boolean isSynch() {
    return synchData != null && synchData.isSynchActivated();
  }

  @Override
  public String toString() {
    return name;
  }

  public String getCommand() {
    return command;
  }

  @Override
  public String getDescription() {
    return null;
  }

  @Override
  public FlatSVGIcon getIcon() {
    return svgIcon;
  }

  @Override
  public String getUIName() {
    return name;
  }
}
