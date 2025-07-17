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

import javax.swing.*;
import org.weasis.core.api.util.ResourceUtil;

public class PlayViewButton extends ViewButton {

  protected eState state = eState.PLAY;
  protected int size;

  public enum eState {
    PLAY,
    PAUSE
  }

  public PlayViewButton(ShowPopup popup) {
    this(20, popup);
  }

  public PlayViewButton(int size, ShowPopup popup) {
    super(popup, getResizedIcon(ResourceUtil.ActionIcon.EXECUTE, size), "play"); // NON-NLS
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
      case PLAY -> getResizedIcon(ResourceUtil.ActionIcon.EXECUTE, size);
      case PAUSE -> getResizedIcon(ResourceUtil.ActionIcon.SUSPEND, size);
    };
  }
}
