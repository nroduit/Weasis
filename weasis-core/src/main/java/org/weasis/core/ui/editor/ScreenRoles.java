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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.weasis.core.Messages;
import org.weasis.core.api.command.ScreenRole;
import org.weasis.core.api.service.WProperties;

/**
 * Maps the {@link ScreenRole screen roles} to the monitors of the workstation. A role the user gave
 * to a monitor on the Screens preference page (local setting {@code weasis.screen.<id>.role}, never
 * in the remote preferences) wins; the other roles are derived: the screen of the main window is
 * the control screen; the other screens, from left to right then top to bottom, are diagnostic-1
 * and diagnostic-2; portrait is the first other screen taller than wide; color is diagnostic-1. A
 * role no screen fills degrades to the nearest one, so every role resolves to one monitor, and the
 * mapping says which roles are <em>fulfilled</em> by a monitor of their own: a restore places a
 * window on a fulfilled role and folds it into the main window otherwise.
 */
public final class ScreenRoles {

  /** Local setting prefix: {@code weasis.screen.<id>.role}. */
  public static final String ROLE_PREFIX = "weasis.screen."; // NON-NLS

  public static final String ROLE_SUFFIX = ".role"; // NON-NLS

  /** Local setting: how a window whose role has no monitor folds into the main window. */
  public static final String FOLD_KEY = "weasis.screen.fold"; // NON-NLS

  /** How a window whose monitor is missing joins the main window on a restore. */
  public enum FoldMode {
    TABS,
    SPLIT
  }

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

  /**
   * The screen of every role, which roles have a monitor of their own ({@code fulfilled}) and which
   * ones the user set ({@code explicit}).
   */
  public record Mapping(
      Map<ScreenRole, Screen> screens, Set<ScreenRole> fulfilled, Set<ScreenRole> explicit) {

    public Mapping {
      screens = Map.copyOf(screens);
      fulfilled = Set.copyOf(fulfilled);
      explicit = Set.copyOf(explicit);
    }

    public static final Mapping EMPTY = new Mapping(Map.of(), Set.of(), Set.of());

    public Screen screen(ScreenRole role) {
      return screens.get(role);
    }

    public boolean isFulfilled(ScreenRole role) {
      return fulfilled.contains(role);
    }

    public boolean isExplicit(ScreenRole role) {
      return explicit.contains(role);
    }

    public boolean isEmpty() {
      return screens.isEmpty();
    }

    /** The role a screen answers to: the first fulfilled role mapped to it, by role order. */
    public Optional<ScreenRole> roleOf(String screenId) {
      for (ScreenRole role : ScreenRole.values()) {
        Screen screen = screens.get(role);
        if (fulfilled.contains(role) && screen != null && screen.id().equals(screenId)) {
          return Optional.of(role);
        }
      }
      return Optional.empty();
    }
  }

  private static volatile Supplier<WProperties> store = () -> null;

  private ScreenRoles() {}

  /** The localized name of a role for menus and pages: "Diagnostic screen 1". */
  public static String label(ScreenRole role) {
    return Messages.getString("ScreenRoles." + role.key());
  }

  /** The local settings the explicit roles and the fold mode live in; set once by the UI core. */
  public static void setStore(Supplier<WProperties> localPersistence) {
    store = Objects.requireNonNull(localPersistence);
  }

  /** Derives every role from the screens alone. */
  public static Mapping assign(String mainScreenId, List<Screen> screens) {
    return assign(mainScreenId, screens, Map.of());
  }

  /**
   * Assigns every role to one of the screens.
   *
   * @param mainScreenId the id of the screen holding the main window; an unknown id makes the first
   *     screen the control screen
   * @param explicit the roles the user gave, by screen id; an id that is not a screen is ignored
   * @return the screen of each role; empty when there is no screen
   */
  public static Mapping assign(
      String mainScreenId, List<Screen> screens, Map<String, ScreenRole> explicit) {
    if (screens == null || screens.isEmpty()) {
      return Mapping.EMPTY;
    }
    Map<ScreenRole, Screen> roles = new EnumMap<>(ScreenRole.class);
    Set<ScreenRole> fulfilled = EnumSet.noneOf(ScreenRole.class);
    Set<ScreenRole> explicitRoles = EnumSet.noneOf(ScreenRole.class);
    Map<String, Screen> byId = new LinkedHashMap<>();
    screens.forEach(s -> byId.put(s.id(), s));
    if (explicit != null) {
      explicit.forEach(
          (id, role) -> {
            Screen screen = byId.get(id);
            if (screen != null && role != null && !roles.containsKey(role)) {
              roles.put(role, screen);
              fulfilled.add(role);
              explicitRoles.add(role);
            }
          });
    }
    Screen control = roles.get(ScreenRole.CONTROL);
    if (control == null) {
      control = byId.getOrDefault(mainScreenId, screens.getFirst());
      roles.put(ScreenRole.CONTROL, control);
      fulfilled.add(ScreenRole.CONTROL);
    }
    Screen mainScreen = control;
    Comparator<Screen> leftToRight =
        Comparator.comparingInt((Screen s) -> s.bounds().x).thenComparingInt(s -> s.bounds().y);
    // the screens left for the derived roles: not the control one, not an explicit one
    List<Screen> free =
        screens.stream()
            .filter(s -> s != mainScreen && !roles.containsValue(s))
            .sorted(leftToRight)
            .toList();
    List<Screen> others =
        free.isEmpty()
            ? screens.stream().filter(s -> s != mainScreen).sorted(leftToRight).toList()
            : free;
    boolean hasOthers = !others.isEmpty();
    if (!hasOthers) {
      others = List.of(mainScreen);
    }
    Screen first = others.getFirst();
    Optional<Screen> portrait = others.stream().filter(Screen::isPortrait).findFirst();
    derive(roles, fulfilled, ScreenRole.DIAGNOSTIC_1, first, hasOthers);
    derive(
        roles,
        fulfilled,
        ScreenRole.DIAGNOSTIC_2,
        others.size() > 1 ? others.get(1) : first,
        others.size() > 1);
    derive(roles, fulfilled, ScreenRole.PORTRAIT, portrait.orElse(first), portrait.isPresent());
    derive(roles, fulfilled, ScreenRole.COLOR, first, hasOthers);
    return new Mapping(roles, fulfilled, explicitRoles);
  }

  private static void derive(
      Map<ScreenRole, Screen> roles,
      Set<ScreenRole> fulfilled,
      ScreenRole role,
      Screen screen,
      boolean ownMonitor) {
    if (!roles.containsKey(role)) {
      roles.put(role, screen);
      if (ownMonitor) {
        fulfilled.add(role);
      }
    }
  }

  /** The monitors of this workstation by device id; empty when headless. */
  public static Map<String, GraphicsDevice> devices() {
    Map<String, GraphicsDevice> byId = new LinkedHashMap<>();
    try {
      for (GraphicsDevice device :
          GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
        byId.put(device.getIDstring(), device);
      }
    } catch (HeadlessException e) {
      return Map.of();
    }
    return byId;
  }

  /** The mapping on the monitors of this workstation, the main window's screen as control. */
  public static Mapping mapping(Window mainWindow) {
    Map<String, GraphicsDevice> devices = devices();
    if (devices.isEmpty()) {
      return Mapping.EMPTY;
    }
    List<Screen> screens = new ArrayList<>();
    devices.forEach(
        (id, d) -> screens.add(new Screen(id, d.getDefaultConfiguration().getBounds())));
    GraphicsConfiguration mainConfiguration =
        mainWindow == null ? null : mainWindow.getGraphicsConfiguration();
    String mainId =
        mainConfiguration != null
            ? mainConfiguration.getDevice().getIDstring()
            : GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getIDstring();
    return assign(mainId, screens, explicitRoles());
  }

  /** The monitor of every role on this workstation; empty when headless. */
  public static Map<ScreenRole, GraphicsDevice> current(Window mainWindow) {
    Map<String, GraphicsDevice> devices = devices();
    Map<ScreenRole, GraphicsDevice> result = new EnumMap<>(ScreenRole.class);
    mapping(mainWindow)
        .screens()
        .forEach((role, screen) -> result.put(role, devices.get(screen.id())));
    return result;
  }

  /** The monitor a role resolves to on this workstation; empty when headless. */
  public static Optional<GraphicsDevice> resolve(ScreenRole role, Window mainWindow) {
    return Optional.ofNullable(current(mainWindow).get(role));
  }

  /** The roles the user gave to monitors, by device id, from the local settings. */
  public static Map<String, ScreenRole> explicitRoles() {
    WProperties properties = store.get();
    Map<String, ScreenRole> roles = new HashMap<>();
    if (properties == null) {
      return roles;
    }
    for (String key : properties.stringPropertyNames()) {
      if (key.startsWith(ROLE_PREFIX) && key.endsWith(ROLE_SUFFIX)) {
        String id = key.substring(ROLE_PREFIX.length(), key.length() - ROLE_SUFFIX.length());
        try {
          roles.put(id, ScreenRole.parse(properties.getProperty(key)));
        } catch (IllegalArgumentException e) {
          // an unreadable value counts as no choice
        }
      }
    }
    return roles;
  }

  /** Gives a monitor a role in the local settings, or none again with a null role. */
  public static void setExplicitRole(String screenId, ScreenRole role) {
    WProperties properties = store.get();
    if (properties == null || screenId == null || screenId.isBlank()) {
      return;
    }
    String key = ROLE_PREFIX + screenId + ROLE_SUFFIX;
    if (role == null) {
      properties.remove(key);
    } else {
      properties.setProperty(key, role.key());
    }
  }

  /** How a window whose monitor is missing folds into the main window: tabs unless set. */
  public static FoldMode foldMode() {
    WProperties properties = store.get();
    String value = properties == null ? null : properties.getProperty(FOLD_KEY);
    return value != null && value.trim().toLowerCase(Locale.ROOT).equals("split") // NON-NLS
        ? FoldMode.SPLIT
        : FoldMode.TABS;
  }
}
