/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.setting;

import org.weasis.core.api.gui.util.AppProperties;

/**
 * Settings of {@code weasis-core}. The keys are the ones already stored, so the values written by
 * earlier versions keep applying.
 */
public final class CoreSettings {
  private static final String GROUP = AppProperties.CORE_PLUGIN_ID;

  /**
   * The command policy a person may adjust in the preferences, on top of the configuration's and
   * only where the site did not lock it: {@code allow}, {@code refuse}, or empty to leave the
   * configuration's value; the hosts and roots as comma-separated and path-separated lists.
   */
  public static final Setting<String> COMMANDS_NETWORK_READ =
      Setting.builder(
              SettingType.STRING, GROUP, "weasis.commands.policy.network.read", "") // NON-NLS
          .description(
              "Network reads of a remote caller: allow, refuse, or the configuration's value")
          .declare();

  public static final Setting<String> COMMANDS_NETWORK_READ_HOSTS =
      Setting.builder(
              SettingType.STRING, GROUP, "weasis.commands.policy.network.readHosts", "") // NON-NLS
          .description("Hosts a remote caller may read from, comma-separated; empty for any")
          .declare();

  public static final Setting<String> COMMANDS_NETWORK_WRITE =
      Setting.builder(
              SettingType.STRING, GROUP, "weasis.commands.policy.network.write", "") // NON-NLS
          .description(
              "Network writes of a remote caller: allow, refuse, or the configuration's value")
          .declare();

  public static final Setting<String> COMMANDS_NETWORK_WRITE_HOSTS =
      Setting.builder(
              SettingType.STRING, GROUP, "weasis.commands.policy.network.writeHosts", "") // NON-NLS
          .description("Hosts a remote caller may write to, comma-separated; empty for any")
          .declare();

  public static final Setting<String> COMMANDS_FILE_WRITE_ROOTS =
      Setting.builder(
              SettingType.STRING, GROUP, "weasis.commands.policy.files.write.roots", "") // NON-NLS
          .description(
              "Folders a remote caller may write under, path-separated; empty for the configuration's")
          .declare();

  public static final Setting<Boolean> CONFIRM_CLOSING =
      Setting.builder(SettingType.BOOLEAN, GROUP, "weasis.confirm.closing", false) // NON-NLS
          .description("Ask for a confirmation before closing the application")
          .declare();

  /** Width of the content of the left side panel, in pixels; computed from the tools if unset. */
  public static final Setting<Integer> SIDE_PANEL_LEFT_WIDTH =
      Setting.builder(SettingType.INTEGER, GROUP, "weasis.sidepanel.left.width", 0) // NON-NLS
          .scope(Scope.MACHINE)
          .description("Width of the left side panel")
          .declare();

  /** Width of the content of the right side panel, in pixels; computed from the tools if unset. */
  public static final Setting<Integer> SIDE_PANEL_RIGHT_WIDTH =
      Setting.builder(SettingType.INTEGER, GROUP, "weasis.sidepanel.right.width", 0) // NON-NLS
          .scope(Scope.MACHINE)
          .description("Width of the right side panel")
          .declare();

  /**
   * Tool id of the explorer the explorer side shows first when several are loaded (the DICOM
   * explorer, the file explorer, a catalog); the first explorer by position when it is not loaded.
   */
  public static final Setting<String> EXPLORER_DEFAULT =
      Setting.builder(
              SettingType.STRING, GROUP, "weasis.explorer.default", "dicom-explorer") // NON-NLS
          .description("Explorer shown first when several explorers are loaded")
          .since("5.0.0")
          .declare();

  /** Share of the top slot of the explorer side when both slots are shown. */
  public static final Setting<Double> SIDE_PANEL_EXPLORER_RATIO =
      Setting.builder(SettingType.DOUBLE, GROUP, "weasis.sidepanel.explorer.ratio", 0.0) // NON-NLS
          .scope(Scope.MACHINE)
          .description("Share of the top slot of the explorer side")
          .declare();

  /** Share of the top slot of the tools side when both slots are shown. */
  public static final Setting<Double> SIDE_PANEL_TOOLS_RATIO =
      Setting.builder(SettingType.DOUBLE, GROUP, "weasis.sidepanel.tools.ratio", 0.0) // NON-NLS
          .scope(Scope.MACHINE)
          .description("Share of the top slot of the tools side")
          .declare();

  /**
   * Size in pixels, before the display scale factor, of the icons of the button stripes of the side
   * panels (explorer and tools).
   */
  public static final Setting<Integer> SIDE_PANEL_ICON_SIZE =
      Setting.builder(SettingType.INTEGER, GROUP, "weasis.sidepanel.icon.size", 24) // NON-NLS
          .description("Size of the icons of the side panel button stripes, in pixels")
          .since("5.0.0")
          .declare();

  /**
   * Runs only the display operations whose input or parameters changed since their last run; off,
   * every operation runs on every pass.
   */
  public static final Setting<Boolean> DISPLAY_OP_TRACKING =
      Setting.builder(SettingType.BOOLEAN, GROUP, "weasis.display.op.tracking", true) // NON-NLS
          .description("Run only the display operations that changed")
          .since("5.0.0")
          .declare();

  /**
   * After a pass that skipped operations, runs the whole chain on copies and logs an error when the
   * images differ. For troubleshooting: it doubles the cost of a pass.
   */
  public static final Setting<Boolean> DISPLAY_OP_VERIFY =
      Setting.builder(SettingType.BOOLEAN, GROUP, "weasis.display.op.verify", false) // NON-NLS
          .description("Check each display pass against a full run")
          .since("5.0.0")
          .declare();

  /**
   * Releases at once the intermediate display images a pass replaced, instead of leaving their
   * native memory to the garbage collector. Only for operations that declare they own their output.
   */
  public static final Setting<Boolean> DISPLAY_OP_RELEASE =
      Setting.builder(SettingType.BOOLEAN, GROUP, "weasis.display.op.release", true) // NON-NLS
          .description("Release replaced intermediate display images at once")
          .since("5.0.0")
          .declare();

  /**
   * Converts the colors of the displayed color images from the ICC profile they declare (sRGB when
   * none) to the profile of the display.
   */
  public static final Setting<Boolean> COLOR_MANAGEMENT =
      Setting.builder(SettingType.BOOLEAN, GROUP, "weasis.color.management", true) // NON-NLS
          .description("Convert color images from their ICC profile to the display profile")
          .since("5.0.0")
          .declare();

  /** ICC profile file of the display of this workstation; empty means sRGB. */
  public static final Setting<String> DISPLAY_PROFILE =
      Setting.builder(SettingType.STRING, GROUP, "weasis.color.display.profile", "") // NON-NLS
          .scope(Scope.MACHINE)
          .description("ICC profile file of the display (empty: sRGB)")
          .since("5.0.0")
          .declare();

  /**
   * Zooms with the mouse (wheel or drag) around the pointer instead of the center of the view, as
   * most viewers do; the slider, the keyboard and the commands keep zooming around the center.
   */
  public static final Setting<Boolean> ZOOM_AT_CURSOR =
      Setting.builder(SettingType.BOOLEAN, GROUP, "weasis.zoom.cursor", true) // NON-NLS
          .description("Zoom with the mouse around the pointer")
          .since("5.0.0")
          .declare();

  private CoreSettings() {}
}
