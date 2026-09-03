/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.profile;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicRegistry;
import org.weasis.core.ui.model.graphic.ToolCategory;
import org.weasis.core.ui.model.graphic.imp.BuiltinGraphicTools;
import org.weasis.core.ui.model.graphic.imp.area.SelectGraphic;
import org.weasis.core.ui.model.graphic.profile.MeasurementProfile.Defaults;
import org.weasis.core.ui.model.utils.ImageStatistics;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.pref.ViewSetting;

/** Turns a profile into palettes and settings, and captures the current settings as a profile. */
public final class MeasurementProfiles {

  private MeasurementProfiles() {}

  /** The palette of a category for a profile: its tools in order, selection always first. */
  public static List<Graphic> tools(
      MeasurementProfile profile, GraphicRegistry registry, ToolCategory category) {
    List<Graphic> all = registry.prototypes(category);
    if (profile == null || profile.tools() == null) {
      return all;
    }
    List<Graphic> result = new ArrayList<>();
    all.stream().filter(SelectGraphic.class::isInstance).findFirst().ifPresent(result::add);
    for (String key : profile.tools()) {
      registry
          .prototype(key)
          .filter(all::contains)
          .filter(g -> !result.contains(g))
          .ifPresent(result::add);
    }
    return result;
  }

  /** The palette of a category for the registry's active profile. */
  public static List<Graphic> activeTools(ToolCategory category) {
    return tools(
        MeasurementProfileRegistry.getInstance().active(), GraphicRegistry.getInstance(), category);
  }

  private static ProfileOverlay overlay;

  private static synchronized ProfileOverlay overlay() {
    if (overlay == null) {
      overlay =
          new ProfileOverlay(
              MeasureTool.viewSetting,
              GraphicRegistry.getInstance(),
              ImageStatistics.ALL_MEASUREMENTS);
    }
    return overlay;
  }

  /**
   * Puts the user's own settings back in place of what the active profile laid over them; to call
   * before the preferences are saved, so that a profile never becomes the user's choice.
   */
  public static void restoreUserSettings() {
    overlay().restore();
  }

  /**
   * Applies the palettes of a profile to a viewer type, and its defaults, labels and statistics
   * over the user's settings; the sections it does not state show the user's own again.
   */
  public static void apply(MeasurementProfile profile, ImageViewerEventManager<?> eventManager) {
    GraphicRegistry registry = GraphicRegistry.getInstance();
    if (eventManager != null) {
      eventManager
          .getAction(ActionW.DRAW_MEASURE)
          .ifPresent(
              a ->
                  a.setDataListWithoutTriggerAction(
                      tools(profile, registry, ToolCategory.MEASURE).toArray(new Graphic[0])));
      eventManager
          .getAction(ActionW.DRAW_GRAPHICS)
          .ifPresent(
              a ->
                  a.setDataListWithoutTriggerAction(
                      tools(profile, registry, ToolCategory.DRAW).toArray(new Graphic[0])));
    }
    overlay().apply(profile);
    MeasureTool.updateMeasureProperties();
    MeasureTool.refreshViewLabels();
  }

  /** The current palettes and settings as a new user profile. */
  public static MeasurementProfile capture(
      String id, String name, List<String> modalities, ImageViewerEventManager<?> eventManager) {
    GraphicRegistry registry = GraphicRegistry.getInstance();
    List<String> tools = new ArrayList<>();
    List<Feature<? extends ActionState>> actions =
        List.of(ActionW.DRAW_MEASURE, ActionW.DRAW_GRAPHICS);
    for (Feature<? extends ActionState> action : actions) {
      Optional<? extends ActionState> state =
          eventManager == null ? Optional.empty() : eventManager.getAction(action);
      if (state.isPresent() && state.get() instanceof ComboItemListener<?> combo) {
        for (Object item : combo.getAllItem()) {
          if (item instanceof Graphic g && !(g instanceof SelectGraphic)) {
            registry.keyOf(g).ifPresent(tools::add);
          }
        }
      }
    }
    if (tools.isEmpty()) {
      tools = null;
    }
    ViewSetting setting = MeasureTool.viewSetting;
    Color color = setting.getLineColor();
    Defaults defaults =
        new Defaults(
            color,
            setting.getLineWidth(),
            setting.isFilled(),
            setting.getFillOpacity(),
            setting.isUprightByDrag(),
            setting.getDecimals());
    Map<String, List<String>> labels = new LinkedHashMap<>();
    for (ToolCategory category : List.of(ToolCategory.MEASURE, ToolCategory.ADVANCED)) {
      for (Graphic g : registry.prototypes(category)) {
        List<Measurement> list = g.getMeasurementList();
        if (list != null && !list.isEmpty()) {
          registry
              .keyOf(g)
              .ifPresent(
                  key ->
                      labels.put(
                          key,
                          list.stream()
                              .filter(m -> Boolean.TRUE.equals(m.getGraphicLabel()))
                              .map(Measurement::getKey)
                              .toList()));
        }
      }
    }
    List<String> statistics = new ArrayList<>();
    for (Measurement m : ImageStatistics.ALL_MEASUREMENTS) {
      if (Boolean.TRUE.equals(m.getComputed())) {
        statistics.add(m.getKey());
      }
    }
    return new MeasurementProfile(id, name, modalities, tools, defaults, labels, statistics, false);
  }

  /** Keys of the tools of a palette, for the editor. */
  public static List<String> keysOf(List<Graphic> prototypes) {
    GraphicRegistry registry = GraphicRegistry.getInstance();
    List<String> keys = new ArrayList<>();
    prototypes.forEach(g -> registry.keyOf(g).ifPresent(keys::add));
    return keys;
  }

  static boolean isSelection(String key) {
    return BuiltinGraphicTools.SELECT.equals(key) || BuiltinGraphicTools.DRAW_SELECT.equals(key);
  }
}
