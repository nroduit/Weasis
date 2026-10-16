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

  /** Height of the expanded import / export footer, in pixels; scaled default if unset. */
  public static final Setting<Integer> FOOTER_HEIGHT =
      Setting.builder(SettingType.INTEGER, GROUP, "weasis.footer.split.height", 0) // NON-NLS
          .scope(Scope.MACHINE)
          .description("Height of the import and export footer")
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
   * images differ. For diagnosis: it doubles the cost of a pass.
   */
  public static final Setting<Boolean> DISPLAY_OP_VERIFY =
      Setting.builder(SettingType.BOOLEAN, GROUP, "weasis.display.op.verify", false) // NON-NLS
          .description("Check each display pass against a full run")
          .since("5.0.0")
          .declare();

  private CoreSettings() {}
}
