/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.util.Locale;
import java.util.stream.Stream;
import org.weasis.core.api.gui.util.ActionW;

/** What a mouse button does in a view, spelled as the commands and the UI profiles do. */
public enum MouseActionType {
  SEQUENCE(ActionW.SCROLL_SERIES.cmd()),
  WINDOW_LEVEL(ActionW.WINLEVEL.cmd()),
  ZOOM(ActionW.ZOOM.cmd()),
  PAN(ActionW.PAN.cmd()),
  ROTATION(ActionW.ROTATION.cmd()),
  CROSSHAIR(ActionW.CROSSHAIR.cmd()),
  MEASURE(ActionW.MEASURE.cmd()),
  DRAW(ActionW.DRAW.cmd()),
  CONTEXT_MENU(ActionW.CONTEXTMENU.cmd()),
  NONE(ActionW.NO_ACTION.cmd());

  private final String command;

  MouseActionType(String command) {
    this.command = command;
  }

  /** The action command of {@link ActionW}. */
  public String command() {
    return command;
  }

  /**
   * The action of a command or of a name, whatever spelling the caller used.
   *
   * @throws IllegalArgumentException when neither matches
   */
  public static MouseActionType ofCommand(String value) {
    for (MouseActionType action : values()) {
      if (action.command.equalsIgnoreCase(value) || action.toString().equalsIgnoreCase(value)) {
        return action;
      }
    }
    throw new IllegalArgumentException(
        "expected one of " + Stream.of(values()).map(Object::toString).toList());
  }

  @Override
  public String toString() {
    return name().toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
