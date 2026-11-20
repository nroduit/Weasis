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

import java.awt.Component;
import java.awt.GraphicsConfiguration;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.color.ColorManagement;
import org.weasis.core.api.image.color.IccProfile;
import org.weasis.core.api.setting.CoreSettings;
import org.weasis.core.api.setting.Settings;

/**
 * Changes the color management of the display, for the preferences and the {@code color:} verbs,
 * and redraws the open views with it.
 */
public final class DisplayColor {

  private DisplayColor() {}

  /**
   * Switches color management.
   *
   * @return false when the site locks the setting
   */
  public static boolean setEnabled(boolean enabled) {
    boolean done = Settings.set(CoreSettings.COLOR_MANAGEMENT, enabled);
    refreshViews();
    return done;
  }

  /**
   * Uses the profile in {@code file} for the display of this workstation.
   *
   * @throws IOException when the file holds no RGB profile or the site locks the setting
   */
  public static IccProfile setDisplayProfile(Path file) throws IOException {
    Path absolute = file.toAbsolutePath();
    IccProfile profile = ColorManagement.readProfile(absolute);
    if (!Settings.set(CoreSettings.DISPLAY_PROFILE, absolute.toString())) {
      throw new IOException("The display profile is locked by the site configuration");
    }
    refreshViews();
    return profile;
  }

  /**
   * Back to the site display profile, or sRGB.
   *
   * @return false when the site locks the setting
   */
  public static boolean resetDisplayProfile() {
    boolean done = Settings.reset(CoreSettings.DISPLAY_PROFILE);
    refreshViews();
    return done;
  }

  /**
   * Gives the monitor {@code screenId} a profile of its own, in the local settings of this
   * workstation.
   *
   * @throws IOException when the file holds no RGB profile
   */
  public static IccProfile setScreenProfile(String screenId, Path file) throws IOException {
    ColorManagement.setScreenProfile(screenId, Objects.requireNonNull(file));
    refreshViews();
    return ColorManagement.displayProfile(screenId);
  }

  /** The monitor {@code screenId} follows the profile of the workstation again. */
  public static void resetScreenProfile(String screenId) {
    try {
      ColorManagement.setScreenProfile(screenId, null);
    } catch (IOException e) {
      throw new IllegalStateException(e); // no file is read when a profile is removed
    }
    refreshViews();
  }

  /** The device id of the monitor a component is shown on, or null when it is not displayed. */
  public static String screenId(Component component) {
    GraphicsConfiguration gc = component == null ? null : component.getGraphicsConfiguration();
    return gc == null ? null : gc.getDevice().getIDstring();
  }

  /** Runs a display pass on every open image view, whose color operation sees the change. */
  public static void refreshViews() {
    GuiExecutor.invokeAndRethrow(
        () -> {
          List<ViewerPlugin<?>> plugins = GuiUtils.getUICore().getViewerPlugins();
          synchronized (plugins) {
            for (ViewerPlugin<?> plugin : plugins) {
              if (plugin instanceof ImageViewerPlugin<?> viewer) {
                for (ViewCanvas<?> view : viewer.getImagePanels()) {
                  view.getImageLayer().updateDisplayOperations();
                  view.getJComponent().repaint();
                }
              }
            }
          }
        });
  }
}
