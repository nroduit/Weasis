/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.extras.FlatSVGIcon.ColorFilter;
import java.awt.Color;
import javax.swing.Icon;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.util.ResourceUtil;

public class ManualSynchViewButton extends ViewButton {
  private static final FlatSVGIcon ICON =
      ResourceUtil.getIcon(ResourceUtil.ActionIcon.HAND, 20, 20);

  protected State state = State.OFF;

  public enum State {
    OFF("Off", IconColor.ACTIONS_RED.getColor()),
    ON("On", IconColor.ACTIONS_GREEN.getColor());

    private final String name;
    private final FlatSVGIcon icon;
    private final Color color;

    State(String name, Color color) {
      this.name = name;
      this.color = color;
      this.icon = GuiUtils.getDerivedIcon(ICON, new ColorFilter().add(new Color(0x6E6E6E), color));
    }

    public String getName() {
      return name;
    }

    @Override
    public String toString() {
      return name;
    }

    public FlatSVGIcon getIcon() {
      return icon;
    }

    public Color getColor() {
      return color;
    }
  }

  public ManualSynchViewButton(ShowPopup popup) {
    super(popup, State.OFF.getIcon(), "manualsync"); // NON-NLS
  }

  public State getState() {
    return state;
  }

  public void setState(State state) {
    this.state = state;
  }

  @Override
  public Icon getIcon() {
    return state.getIcon();
  }
}
