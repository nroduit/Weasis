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

import java.awt.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import javax.swing.AbstractButton;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.DynamicMenu;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.KeyBinding;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.ToolVisibility;
import org.weasis.core.ui.util.ToolBarContainer;
import org.weasis.core.ui.util.Toolbar;

/**
 * Every action of the workspace, from the sources that register themselves: the menu bar of the
 * main window, the toolbars of the selected viewer (shown or hidden by the context) and its tool
 * panels. The command palette searches it and {@code ui:actions} / {@code ui:action} expose it, so
 * an item the context hides stays reachable (<i>nothing becomes unreachable</i>). Read on the event
 * dispatch thread.
 */
public final class ActionCatalog {

  private static final Logger LOGGER = LoggerFactory.getLogger(ActionCatalog.class);
  private static final String SEPARATOR = " › "; // NON-NLS

  private static final List<Supplier<List<ActionEntry>>> SOURCES = new CopyOnWriteArrayList<>();
  private static volatile boolean configured;

  private ActionCatalog() {}

  /** Registers the toolbar row and the tools of the selected viewer as sources. */
  public static synchronized void configure() {
    if (!configured) {
      configured = true;
      addSource(() -> ofToolbars(GuiUtils.getUICore().getToolbarContainer()));
      addSource(() -> ofTools(ViewContexts.current()));
    }
  }

  /** Adds a source of actions; it is read again at every lookup. */
  public static void addSource(Supplier<List<ActionEntry>> source) {
    SOURCES.add(Objects.requireNonNull(source));
  }

  public static void removeSource(Supplier<List<ActionEntry>> source) {
    SOURCES.remove(source);
  }

  /** Every action, one per id (the first source wins), by group then label. */
  public static List<ActionEntry> entries() {
    Map<String, ActionEntry> byId = new LinkedHashMap<>();
    for (Supplier<List<ActionEntry>> source : SOURCES) {
      try {
        for (ActionEntry entry : source.get()) {
          byId.putIfAbsent(entry.id(), entry);
        }
      } catch (RuntimeException e) {
        LOGGER.warn("Action source failed", e);
      }
    }
    List<ActionEntry> all = new ArrayList<>(byId.values());
    all.sort(Comparator.comparing(ActionEntry::group).thenComparing(ActionEntry::label));
    return all;
  }

  public static Optional<ActionEntry> find(String id) {
    return entries().stream().filter(entry -> entry.id().equals(id)).findFirst();
  }

  /**
   * The actions matching a query, best first: every word of the query must be found in the label,
   * the group or the id, a word at the start of the label ranks before one inside it.
   */
  public static List<ActionEntry> search(String query) {
    String[] words = query == null ? new String[0] : query.toLowerCase(Locale.ROOT).split("\\s+");
    List<Match> matches = new ArrayList<>();
    for (ActionEntry entry : entries()) {
      int score = score(entry, words);
      if (score >= 0) {
        matches.add(new Match(entry, score));
      }
    }
    matches.sort(Comparator.comparingInt(Match::score));
    return matches.stream().map(Match::entry).toList();
  }

  private record Match(ActionEntry entry, int score) {}

  /** 0 for an exact match, higher for a weaker one, -1 when a word is missing. */
  private static int score(ActionEntry entry, String[] words) {
    String label = entry.label().toLowerCase(Locale.ROOT);
    String rest = (entry.group() + " " + entry.id()).toLowerCase(Locale.ROOT);
    int score = 0;
    for (String word : words) {
      if (word.isEmpty()) {
        continue;
      }
      if (label.startsWith(word)) {
        score += 0;
      } else if (label.contains(word)) {
        score += 1;
      } else if (rest.contains(word)) {
        score += 2;
      } else {
        return -1;
      }
    }
    return score;
  }

  // ---- sources

  /** The leaves of a menu bar: a dynamic menu is built first, a disabled item is left out. */
  public static List<ActionEntry> ofMenuBar(JMenuBar bar) {
    List<ActionEntry> entries = new ArrayList<>();
    for (int i = 0; i < bar.getMenuCount(); i++) {
      JMenu menu = bar.getMenu(i);
      if (menu != null) {
        collect(menu, menu.getText(), "", "menu/" + ActionEntry.slug(menu.getText()), entries);
      }
    }
    return entries;
  }

  private static void collect(
      JMenu menu, String group, String path, String id, List<ActionEntry> entries) {
    if (menu instanceof DynamicMenu dynamic) {
      dynamic.popupMenuWillBecomeVisible();
    }
    for (Component component : menu.getMenuComponents()) {
      if (!(component instanceof JMenuItem item) || item.getText() == null) {
        continue;
      }
      String label = path.isEmpty() ? item.getText() : path + SEPARATOR + item.getText();
      String itemId = id + "/" + ActionEntry.slug(item.getText());
      if (item instanceof JMenu sub) {
        collect(sub, group, label, itemId, entries);
      } else if (item.isEnabled()) {
        entries.add(
            new ActionEntry(
                itemId, label, group, text(item.getAccelerator()), "", true, item::doClick));
      }
    }
  }

  /**
   * The buttons of the registered toolbars, shown or not: a button of a toolbar out of the row is
   * reported as not relevant; a drop-down button only opens a popup and is left out.
   */
  public static List<ActionEntry> ofToolbars(ToolBarContainer container) {
    List<ActionEntry> entries = new ArrayList<>();
    if (container == null) {
      return entries;
    }
    List<Toolbar> shown = container.shownBars();
    for (Toolbar bar : container.getRegisteredToolBars()) {
      if (Insertable.Type.EMPTY.equals(bar.getPanelType())) {
        continue;
      }
      String prefix = "toolbar/" + bar.getToolbarId() + "/"; // NON-NLS
      boolean relevant = shown.contains(bar);
      for (Component component : bar.getComponent().getComponents()) {
        if (component instanceof AbstractButton button && !(button instanceof DropDownButton)) {
          String label = labelOf(button);
          if (label != null) {
            String command = button.getActionCommand();
            boolean named = command != null && !command.equals(button.getText());
            entries.add(
                new ActionEntry(
                    prefix + ActionEntry.slug(named ? command : label),
                    label,
                    bar.getComponentName(),
                    "",
                    named ? command : "",
                    relevant && button.isEnabled(),
                    button::doClick));
          }
        }
      }
    }
    return entries;
  }

  /** The tool panels of the context's viewer; running one shows it for the selected view. */
  public static List<ActionEntry> ofTools(ViewContext context) {
    List<ActionEntry> entries = new ArrayList<>();
    SeriesViewer<?> viewer = context == null ? null : context.viewer();
    if (viewer == null) {
      return entries;
    }
    for (DockableTool tool : viewer.getSeriesViewerUI().tools) {
      if (Insertable.Type.EMPTY.equals(tool.getPanelType())) {
        continue;
      }
      boolean relevant = ToolVisibility.isVisible(tool) && Relevance.isRelevant(tool, context);
      entries.add(
          new ActionEntry(
              "tool/" + tool.getToolId(), // NON-NLS
              tool.getComponentName(),
              Messages.common("tools"),
              "",
              "",
              relevant,
              () -> Relevance.reveal(tool)));
    }
    return entries;
  }

  private static String labelOf(AbstractButton button) {
    String tip = button.getToolTipText();
    if (tip != null && !tip.isBlank()) {
      return tip.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").strip(); // html tooltips
    }
    String text = button.getText();
    return text == null || text.isBlank() ? null : text;
  }

  private static String text(KeyStroke stroke) {
    return stroke == null ? "" : new KeyBinding(stroke.getKeyCode(), stroke.getModifiers()).text();
  }
}
