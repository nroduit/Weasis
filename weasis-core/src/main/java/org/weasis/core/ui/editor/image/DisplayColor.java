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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
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
