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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.setting.Setting;
import org.weasis.core.api.setting.SettingType;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.ToolVisibility;
import org.weasis.core.ui.util.ToolBarContainer;

/**
 * Evaluates the relevance of the parts of the workspace against a {@link ViewContext} and applies
 * it to the selected viewer: a tool panel or a toolbar that is not relevant leaves the shell while
 * the view is selected, a menu entry is not built, an action is reported as not relevant to the
 * commands. The content capabilities are the common predicates ({@link #VOLUME}, {@link #TEMPORAL},
 * ...), read from the {@link ContextFacts}; an item declares its own through {@link
 * Relevant#isRelevant}. The setting {@code weasis.ui.contextual} turns the filtering off and shows
 * every tool again, whatever the predicates say. Nothing becomes unreachable: a hidden item is
 * listed in the overflow of the toolbar row (<i>More tools…</i>) and {@link #reveal} brings it back
 * for the selected view; an item that is relevant but not applicable now stays, disabled with the
 * reason as tooltip ({@link #notApplicable}).
 */
public final class Relevance {

  private static final Logger LOGGER = LoggerFactory.getLogger(Relevance.class);

  /** Whether irrelevant toolbars, tools and entries are hidden; false shows all tools. */
  public static final Setting<Boolean> CONTEXTUAL =
      Setting.builder(
              SettingType.BOOLEAN,
              AppProperties.CORE_PLUGIN_ID,
              "weasis.ui.contextual", // NON-NLS
              true)
          .description(
              "Whether the toolbars, tools and menu entries that are not relevant for the selected "
                  + "view are hidden; false shows all tools") // NON-NLS
          .declare();

  /** Always relevant. */
  public static final Predicate<ViewContext> ALWAYS = context -> true;

  /** MPR, 3D, curved MPR: the series is a volume. */
  public static final Predicate<ViewContext> VOLUME =
      context -> ContextFacts.flag(context, ContextFacts.VOLUME);

  /** Cine and frame rate: a dynamic acquisition or a multi-frame series. */
  public static final Predicate<ViewContext> TEMPORAL =
      context -> {
        String temporal = ContextFacts.text(context, ContextFacts.TEMPORAL);
        return "DYNAMIC".equals(temporal) || "CINE".equals(temporal); // NON-NLS
      };

  /** SUV and the PET presets: a PET series with the attributes SUV needs. */
  public static final Predicate<ViewContext> SUV =
      context -> ContextFacts.flag(context, ContextFacts.SUV);

  /** Calibration: the pixel spacing is not reliable (absent, estimated, or ultrasound regions). */
  public static final Predicate<ViewContext> CALIBRATION =
      context -> !ContextFacts.flag(context, ContextFacts.CALIBRATED);

  /** Fusion: a second series of the study can be fused with the selected one. */
  public static final Predicate<ViewContext> FUSION =
      context -> ContextFacts.flag(context, ContextFacts.FUSION);

  /** The hanging tools: a protocol hung the viewer. */
  public static final Predicate<ViewContext> PROTOCOL =
      context -> context != null && context.fact(ContextFacts.HANGING_PROTOCOL).isPresent();

  /** The delay that coalesces the contexts published in a burst (a keyboard walk across cells). */
  static final int DEBOUNCE_MS = 80;

  private static final Map<String, Predicate<ViewContext>> ACTIONS = new ConcurrentHashMap<>();
  private static final Set<String> REVEALED = ConcurrentHashMap.newKeySet();
  private static volatile Supplier<ToolBarContainer> toolbars =
      () -> GuiUtils.getUICore().getToolbarContainer();
  private static volatile boolean following;
  private static volatile ViewContext applied = ViewContext.empty();
  private static volatile ViewContext pending = ViewContext.empty();
  private static final Timer DEBOUNCE = new Timer(DEBOUNCE_MS, e -> apply(pending));

  static {
    DEBOUNCE.setRepeats(false);
  }

  private Relevance() {}

  /** KO, PR, SEG, RT menus and tools: objects of that modality reference the series. */
  public static Predicate<ViewContext> objects(String modality) {
    String fact = ContextFacts.objects(modality);
    return context -> ContextFacts.count(context, fact) > 0;
  }

  /** Whether the filtering is on; off, everything is relevant. */
  public static boolean contextual() {
    return Settings.get(CONTEXTUAL);
  }

  /** A predicate applied to a context, true when the filtering is off. */
  public static boolean applies(Predicate<ViewContext> predicate, ViewContext context) {
    return !contextual() || predicate.test(context == null ? ViewContext.empty() : context);
  }

  /**
   * Whether an item is relevant for a context: true for an item that is not {@link Relevant}, when
   * the filtering is off, and when the item's predicate fails (logged).
   */
  public static boolean isRelevant(Object item, ViewContext context) {
    if (!(item instanceof Relevant relevant) || !contextual()) {
      return true;
    }
    try {
      return relevant.isRelevant(context == null ? ViewContext.empty() : context);
    } catch (RuntimeException e) {
      LOGGER.warn("Relevance of {} failed", item.getClass().getName(), e);
      return true;
    }
  }

  // ---- actions

  /** Declares the relevance of an action by its command; the last declaration wins. */
  public static void registerAction(String command, Predicate<ViewContext> predicate) {
    ACTIONS.put(Objects.requireNonNull(command), Objects.requireNonNull(predicate));
  }

  /** Whether an action is relevant: true when none was declared for it. */
  public static boolean isActionRelevant(String command, ViewContext context) {
    Predicate<ViewContext> predicate = command == null ? null : ACTIONS.get(command);
    return predicate == null || applies(predicate, context);
  }

  /** The declared action predicates by command. */
  public static Map<String, Predicate<ViewContext>> actions() {
    return Collections.unmodifiableMap(ACTIONS);
  }

  // ---- application to the selected viewer

  /** Replaces the toolbar container lookup, for tests. */
  public static void setToolbarContainer(Supplier<ToolBarContainer> supplier) {
    toolbars = Objects.requireNonNull(supplier);
  }

  /**
   * Makes the tools and toolbars of the selected viewer follow every published context, debounced:
   * a burst of contexts applies the last one once, {@value #DEBOUNCE_MS} ms after it.
   */
  public static synchronized void follow() {
    if (!following) {
      following = true;
      ViewContexts.addListener(Relevance::schedule);
      Settings.addListener(CONTEXTUAL, value -> schedule(ViewContexts.current()));
    }
  }

  /** Applies a context on the event thread after the debounce delay, dropping the earlier ones. */
  static void schedule(ViewContext context) {
    pending = context == null ? ViewContext.empty() : context;
    DEBOUNCE.restart();
  }

  /**
   * Applies a context to the shell: the tools of its viewer that are visible and relevant are
   * shown, the UI profile it asks for is laid over the workspace ({@link UiProfiles#apply}), then
   * the toolbar row is rebuilt from the relevant bars the profile lists. The items revealed from
   * the overflow stay while the same view is selected.
   */
  public static void apply(ViewContext context) {
    ViewContext current = context == null ? ViewContext.empty() : context;
    if (!current.sameView(applied)) {
      REVEALED.clear();
    }
    applied = current;
    SeriesViewer<?> viewer = current.viewer();
    if (viewer != null) {
      applyTools(viewer.getSeriesViewerUI().tools, current);
    }
    UiProfile profile = UiProfiles.apply(current);
    ToolBarContainer container;
    try {
      container = toolbars.get();
    } catch (RuntimeException e) {
      container = null;
    }
    if (container != null) {
      container.applyContext(current, profile);
    }
  }

  /**
   * Shows the tools that the user keeps visible and the context makes relevant (or the user
   * revealed from the overflow), closes the others.
   *
   * @return the ids of the tools shown
   */
  public static List<String> applyTools(List<DockableTool> tools, ViewContext context) {
    List<String> shown = new ArrayList<>();
    for (DockableTool tool : tools) {
      if (ToolVisibility.isVisible(tool) && (isRelevant(tool, context) || isRevealed(tool))) {
        tool.showDockable();
        shown.add(tool.getToolId());
      } else {
        tool.closeDockable();
      }
    }
    return shown;
  }

  // ---- overflow: nothing becomes unreachable

  /** The tools of the context's viewer that the user keeps visible but the context hides. */
  public static List<DockableTool> hiddenTools(ViewContext context) {
    List<DockableTool> hidden = new ArrayList<>();
    SeriesViewer<?> viewer = context == null ? null : context.viewer();
    if (viewer != null) {
      for (DockableTool tool : viewer.getSeriesViewerUI().tools) {
        if (ToolVisibility.isVisible(tool) && !isRelevant(tool, context) && !isRevealed(tool)) {
          hidden.add(tool);
        }
      }
    }
    return hidden;
  }

  /** Shows a hidden tool for the selected view; the next view hides it again. */
  public static void reveal(DockableTool tool) {
    REVEALED.add(tool.getToolId());
    tool.showDockable();
  }

  /** Whether the user revealed the tool from the overflow for the selected view. */
  public static boolean isRevealed(DockableTool tool) {
    return REVEALED.contains(tool.getToolId());
  }

  /** An item that is relevant but not applicable now: disabled, with the reason as tooltip. */
  public static void notApplicable(JComponent item, String reason) {
    item.setEnabled(false);
    item.setToolTipText(reason);
  }
}
