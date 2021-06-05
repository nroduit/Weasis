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

import java.util.HashMap;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GUIEntry;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.SynchData.Mode;

public class SynchView implements GUIEntry {
  public static final SynchView NONE =
      new SynchView(
          Messages.getString("SynchView.none"),
          "None", // NON-NLS
          Mode.NONE,
          new ImageIcon(SynchView.class.getResource("/icon/22x22/none.png")),
          new HashMap<String, Boolean>());
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
    actions.put(ActionW.INVERSESTACK.cmd(), true);
    actions.put(ActionW.SORTSTACK.cmd(), true);
    actions.put(ActionW.SPATIAL_UNIT.cmd(), true);
    DEFAULT_TILE =
        new SynchView(
            Messages.getString("SynchView.def_t"),
            "Tile", // NON-NLS
            Mode.TILE,
            new ImageIcon(SynchView.class.getResource("/icon/22x22/tile.png")),
            actions);

    actions = new HashMap<>();
    actions.put(ActionW.SCROLL_SERIES.cmd(), true);
    actions.put(ActionW.PAN.cmd(), true);
    actions.put(ActionW.ZOOM.cmd(), true);
    actions.put(ActionW.ROTATION.cmd(), true);
    actions.put(ActionW.FLIP.cmd(), true);
    actions.put(ActionW.SPATIAL_UNIT.cmd(), true);
    DEFAULT_STACK =
        new SynchView(
            Messages.getString("SynchView.def_s"),
            "Stack", // NON-NLS
            Mode.STACK,
            new ImageIcon(SynchView.class.getResource("/icon/22x22/sequence.png")),
            actions);
  }

  private final String name;
  private final String command;
  private final Icon icon;
  private final SynchData synchData;

  public SynchView(
      String name, String command, Mode mode, Icon icon, Map<String, Boolean> actions) {
    if (name == null) {
      throw new IllegalArgumentException("A parameter is null!");
    }
    this.synchData = new SynchData(mode, actions);
    this.name = name;
    this.command = command;
    this.icon = icon;
  }

  public String getName() {
    return name;
  }

  public SynchData getSynchData() {
    return synchData;
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
  public Icon getIcon() {
    return icon;
  }

  @Override
  public String getUIName() {
    return name;
  }
}
