/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.weasis.core.api.command.ScreenRole;

/**
 * Maps the {@link ScreenRole screen roles} to the monitors of the workstation. Until the Screens
 * preference page stores a mapping, the assignment is derived: the screen of the main window is the
 * control screen; the other screens, from left to right then top to bottom, are diagnostic-1 and
 * diagnostic-2; portrait is the first other screen taller than wide; color is diagnostic-1. A role
 * no screen fills degrades to the nearest one, so every role resolves to one monitor and a
 * single-screen workstation maps every role to its screen.
 */
public final class ScreenRoles {

  /** A screen as the assignment sees it: a device id and its bounds in the virtual desktop. */
  public record Screen(String id, Rectangle bounds) {

    public Screen {
      Objects.requireNonNull(id);
      Objects.requireNonNull(bounds);
    }

    public boolean isPortrait() {
      return bounds.height > bounds.width;
    }
  }

  private ScreenRoles() {}

  /**
   * Assigns every role to one of the screens.
   *
   * @param mainScreenId the id of the screen holding the main window; an unknown id makes the first
   *     screen the control screen
   * @return the screen of each role; empty when there is no screen
   */
  public static Map<ScreenRole, Screen> assign(String mainScreenId, List<Screen> screens) {
    Map<ScreenRole, Screen> roles = new EnumMap<>(ScreenRole.class);
    if (screens == null || screens.isEmpty()) {
      return roles;
    }
    Screen control =
        screens.stream()
            .filter(s -> s.id().equals(mainScreenId))
            .findFirst()
            .orElse(screens.getFirst());
    List<Screen> others =
        screens.stream()
            .filter(s -> s != control)
            .sorted(
                Comparator.comparingInt((Screen s) -> s.bounds().x)
                    .thenComparingInt(s -> s.bounds().y))
            .toList();
    if (others.isEmpty()) {
      others = List.of(control);
    }
    Screen first = others.getFirst();
    roles.put(ScreenRole.CONTROL, control);
    roles.put(ScreenRole.DIAGNOSTIC_1, first);
    roles.put(ScreenRole.DIAGNOSTIC_2, others.size() > 1 ? others.get(1) : first);
    roles.put(
        ScreenRole.PORTRAIT, others.stream().filter(Screen::isPortrait).findFirst().orElse(first));
    roles.put(ScreenRole.COLOR, first);
    return roles;
  }

  /** The roles on the monitors of this workstation; empty when headless. */
  public static Map<ScreenRole, GraphicsDevice> current(Window mainWindow) {
    GraphicsDevice[] devices;
    GraphicsDevice defaultDevice;
    try {
      GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
      devices = environment.getScreenDevices();
      defaultDevice = environment.getDefaultScreenDevice();
    } catch (HeadlessException e) {
      return Map.of();
    }
    Map<String, GraphicsDevice> byId = new HashMap<>();
    List<Screen> screens = new ArrayList<>();
    for (GraphicsDevice device : devices) {
      byId.put(device.getIDstring(), device);
      screens.add(new Screen(device.getIDstring(), device.getDefaultConfiguration().getBounds()));
    }
    GraphicsConfiguration mainConfiguration =
        mainWindow == null ? null : mainWindow.getGraphicsConfiguration();
    String mainId =
        (mainConfiguration == null ? defaultDevice : mainConfiguration.getDevice()).getIDstring();
    Map<ScreenRole, GraphicsDevice> result = new EnumMap<>(ScreenRole.class);
    assign(mainId, screens).forEach((role, screen) -> result.put(role, byId.get(screen.id())));
    return result;
  }

  /** The monitor a role resolves to on this workstation; empty when headless. */
  public static Optional<GraphicsDevice> resolve(ScreenRole role, Window mainWindow) {
    return Optional.ofNullable(current(mainWindow).get(role));
  }
}
