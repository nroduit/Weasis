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
import java.awt.Color;
import javax.swing.Icon;
import org.weasis.core.api.util.ResourceUtil;

public class SynchViewButton extends ViewButton {

  protected eState state = eState.OFF;
  protected int size;

  public enum eState {
    OFF,
    ON,
    MANUAL
  }

  public SynchViewButton(ShowPopup popup) {
    this(popup, 20);
  }

  public SynchViewButton(ShowPopup popup, int size) {
    super(popup, ResourceUtil.getIcon(ResourceUtil.ActionIcon.SYNCH).derive(size, size), "sync"); // NON-NLS
    this.size = size;
  }

  public eState getState() {
    return state;
  }

  public void setState(eState state) {
    this.state = state;
  }

  @Override
  public Icon getIcon() {
    return switch (state) {
      case ON -> ResourceUtil.getIcon(ResourceUtil.ActionIcon.SYNCH).derive(size, size);
      case OFF -> ResourceUtil.getIcon(ResourceUtil.ActionIcon.SYNCH).derive(size, size).setColorFilter(new FlatSVGIcon.ColorFilter().add(new Color(0x6E6E6E), new Color(0xFF0000)));
      case MANUAL -> ResourceUtil.getIcon(ResourceUtil.ActionIcon.SYNCH).derive(size, size).setColorFilter(new FlatSVGIcon.ColorFilter().add(new Color(0x6E6E6E), new Color(0x0000FF)));
    };
  }
}
