/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.setting.Setting;
import org.weasis.core.api.setting.SettingCatalog;
import org.weasis.core.api.setting.SettingType;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.api.setting.Settings.Origin;
import org.weasis.core.api.setting.Settings.Resolution;
import org.weasis.core.ui.editor.SeriesViewer;

/**
 * Whether a tool is shown, as a typed setting of the catalog: {@code weasis.tool.<id>.visible}, one
 * per stable tool id, declared when the tool is first seen, contextual (a hanging protocol's UI
 * profile may override it while it is active), lockable by the site. The user's choice in the View
 * menu is written to it. Without a value in any layer the flag the 4.x preferences set on the tool
 * applies ({@code InsertableUtil}: the java preferences of the viewer and the legacy site keys
 * {@code <plugin>.<class>.<container>.visible}), so an installation keeps its choices.
 */
public final class ToolVisibility {

  public static final String PREFIX = "weasis.tool."; // NON-NLS
  public static final String SUFFIX = ".visible"; // NON-NLS

  private static final String GROUP = AppProperties.CORE_PLUGIN_ID;
  private static final Map<String, Setting<Boolean>> SETTINGS = new ConcurrentHashMap<>();
  private static final Set<String> WATCHED = ConcurrentHashMap.newKeySet();
  private static volatile Supplier<Optional<? extends SeriesViewer<?>>> selectedViewer =
      () -> GuiUtils.getUICore().getSelectedPlugin();

  private ToolVisibility() {}

  /** Replaces the selected viewer lookup, for tests. */
  public static void setSelectedViewer(Supplier<Optional<? extends SeriesViewer<?>>> supplier) {
    selectedViewer = Objects.requireNonNull(supplier);
  }

  /** The key of the setting of a tool id. */
  public static String key(String toolId) {
    return PREFIX + toolId + SUFFIX;
  }

  /** The setting of a tool id, declared in the catalog on first use. */
  @SuppressWarnings("unchecked")
  public static Setting<Boolean> setting(String toolId) {
    Objects.requireNonNull(toolId);
    return SETTINGS.computeIfAbsent(
        toolId,
        id -> {
          Optional<Setting<?>> known = SettingCatalog.find(key(id));
          if (known.isPresent() && known.get().type() == SettingType.BOOLEAN) {
            return (Setting<Boolean>) known.get();
          }
          return Setting.builder(SettingType.BOOLEAN, GROUP, key(id), true)
              .contextual()
              .description("Whether the tool '" + id + "' is shown") // NON-NLS
              .declare();
        });
  }

  /**
   * Whether the tool is shown: a context, the user or the site decides through the typed setting;
   * without a value in any layer, the flag of the 4.x preferences on the tool.
   */
  public static boolean isVisible(DockableTool tool) {
    Resolution<Boolean> resolution = Settings.resolve(setting(tool.getToolId()));
    return resolution.origin() == Origin.DEFAULT ? tool.isComponentEnabled() : resolution.value();
  }

  /** Where the visibility of a tool comes from. */
  public static Origin origin(DockableTool tool) {
    return Settings.resolve(setting(tool.getToolId())).origin();
  }

  /**
   * The user's choice: written to the setting (refused when the site locks it) and applied to the
   * tool, shown or closed.
   *
   * @return false when the site locks the setting; the tool then stays as the site says
   */
  public static boolean setVisible(DockableTool tool, boolean visible) {
    boolean written = Settings.set(setting(tool.getToolId()), visible);
    if (written) {
      tool.setComponentEnabled(visible);
      if (visible) {
        tool.showDockable();
      } else {
        tool.closeDockable();
      }
    }
    return written;
  }

  /**
   * Follows the setting of a tool id: when a context, a write or a site reload changes it, the tool
   * of that id on the selected viewer is shown or closed.
   */
  public static void watch(String toolId) {
    if (WATCHED.add(toolId)) {
      Settings.addListener(setting(toolId), value -> GuiExecutor.execute(() -> apply(toolId)));
    }
  }

  private static void apply(String toolId) {
    selectedViewer
        .get()
        .ifPresent(
            viewer -> {
              for (DockableTool tool : viewer.getSeriesViewerUI().tools) {
                if (toolId.equals(tool.getToolId())) {
                  if (isVisible(tool)) {
                    tool.showDockable();
                  } else {
                    tool.closeDockable();
                  }
                }
              }
            });
  }
}
