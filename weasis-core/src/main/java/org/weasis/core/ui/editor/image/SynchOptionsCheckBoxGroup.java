/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JPopupMenu;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.CheckBoxModel;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.GroupCheckBoxMenu;

/**
 * Checkbox menu group for selecting which synchronization actions are active (scroll, pan, zoom,
 * rotation, etc.).
 *
 * <p>Each item is backed by one or more {@link Feature}s (Window/Level are merged into a single
 * "Window/Level" entry). Callers should toggle the state of every command returned by {@link
 * SyncOption#commands()} so {@link SynchData#getActions()} stays consistent.
 */
public class SynchOptionsCheckBoxGroup extends GroupCheckBoxMenu {

  /**
   * A user-visible synchronization option. May target several action commands (e.g. Window/Level
   * targets both {@link ActionW#WINDOW} and {@link ActionW#LEVEL}). The {@link #primary()} feature
   * drives the displayed label and the menu item's {@link
   * javax.swing.JMenuItem#getActionCommand()}.
   */
  public record SyncOption(Feature<?> primary, List<String> commands) {
    public SyncOption(Feature<?> feature) {
      this(feature, List.of(feature.cmd()));
    }
  }

  /** Ordered list of synchronizable options shown in the popup. */
  private static final List<SyncOption> SYNC_OPTIONS =
      List.of(
          new SyncOption(ActionW.SCROLL_SERIES),
          new SyncOption(ActionW.PAN),
          new SyncOption(ActionW.ZOOM),
          new SyncOption(ActionW.ROTATION),
          new SyncOption(ActionW.FLIP),
          // Window and Level are merged into a single user-facing toggle.
          new SyncOption(ActionW.WINLEVEL, List.of(ActionW.WINDOW.cmd(), ActionW.LEVEL.cmd())),
          new SyncOption(ActionW.SPATIAL_UNIT));

  /** Returns the immutable list of synchronizable options exposed by this menu. */
  public static List<SyncOption> getSyncOptions() {
    return SYNC_OPTIONS;
  }

  /**
   * Return the flat list of every {@link SynchData} action key managed by the popup (after
   * expanding multi-target options like Window/Level into their underlying commands).
   */
  public static List<String> getAllManagedCommands() {
    List<String> commands = new ArrayList<>();
    for (SyncOption opt : SYNC_OPTIONS) {
      commands.addAll(opt.commands());
    }
    return commands;
  }

  public SynchOptionsCheckBoxGroup() {
    List<CheckBoxModel> items = new ArrayList<>();
    for (SyncOption option : SYNC_OPTIONS) {
      // Store the Feature object — Feature.toString() returns getTitle(), so the display is
      // correct.
      items.add(new CheckBoxModel(option.primary(), true));
    }
    this.setModel(items);
  }

  /**
   * Returns a list of {@link JCheckBoxMenuItem} items, one per synchronizable option, with:
   *
   * <ul>
   *   <li>text set to the option's human-readable title,
   *   <li>initial selected state taken from {@code currentActions} (a missing entry is treated as
   *       {@code false}; for multi-command options the state is {@code true} only when every
   *       underlying command is enabled),
   *   <li>{@code actionCommand} set to the primary feature's {@link Feature#cmd()} string.
   * </ul>
   *
   * <p>Callers must consult {@link #getSyncOptions()} (or {@link SyncOption#commands()}) to know
   * which action keys to update on the underlying {@link SynchData#getActions()} map when the user
   * toggles an item.
   */
  public List<JCheckBoxMenuItem> createSyncOptionItems(Map<String, Boolean> currentActions) {
    Map<String, Boolean> source = currentActions != null ? currentActions : Collections.emptyMap();
    List<JCheckBoxMenuItem> result = new ArrayList<>();
    for (SyncOption option : SYNC_OPTIONS) {
      boolean selected = true;
      for (String cmd : option.commands()) {
        if (!Boolean.TRUE.equals(source.get(cmd))) {
          selected = false;
          break;
        }
      }
      JCheckBoxMenuItem item = new JCheckBoxMenuItem(option.primary().getTitle(), selected);
      item.setActionCommand(option.primary().cmd());
      result.add(item);
    }
    return result;
  }

  /**
   * Build a ready-to-show {@link JPopupMenu} containing the sync option checkboxes. The {@code
   * onToggle} consumer is invoked for every underlying SynchData action key affected by the user's
   * click (Window/Level expands to two notifications: one for {@code window} and one for {@code
   * level}).
   *
   * @param currentActions actions map driving the initial selected state of each checkbox
   * @param onToggle callback receiving {@code (actionCmd, selected)} for every affected key
   * @return a populated popup menu (caller is responsible for {@code show(...)}-ing it)
   */
  public JPopupMenu buildPopupMenu(
      Map<String, Boolean> currentActions, BiConsumer<String, Boolean> onToggle) {
    JPopupMenu popup = new JPopupMenu();
    addStayOpenItemsTo(popup, currentActions, onToggle);
    return popup;
  }

  /**
   * Append the stay-open synchronization checkbox items to {@code target}. Equivalent to {@link
   * #buildPopupMenu(Map, BiConsumer)} but works against any container, so the same item set can
   * also be inserted into a {@link javax.swing.JMenu} (cascading menu).
   */
  public void addStayOpenItemsTo(
      java.awt.Container target,
      Map<String, Boolean> currentActions,
      BiConsumer<String, Boolean> onToggle) {
    for (JCheckBoxMenuItem item : createSyncOptionItems(currentActions)) {
      JCheckBoxMenuItem stayOpen = wrapAsStayOpen(item);
      target.add(stayOpen);
      String triggerCmd = stayOpen.getActionCommand();
      List<String> targets =
          SYNC_OPTIONS.stream()
              .filter(o -> o.primary().cmd().equals(triggerCmd))
              .findFirst()
              .map(SyncOption::commands)
              .orElse(List.of(triggerCmd));
      stayOpen.addActionListener(
          e -> {
            boolean selected = stayOpen.isSelected();
            for (String cmd : targets) {
              onToggle.accept(cmd, selected);
            }
          });
    }
  }

  /**
   * Wrap a {@link JCheckBoxMenuItem} so that clicking it toggles the selection without dismissing
   * the enclosing popup. Useful for multi-toggle menus where the user typically flips several
   * options before exiting.
   */
  private static JCheckBoxMenuItem wrapAsStayOpen(JCheckBoxMenuItem source) {
    JCheckBoxMenuItem stayOpen =
        new JCheckBoxMenuItem(source.getText(), source.isSelected()) {
          @Override
          protected void processMouseEvent(MouseEvent evt) {
            if (evt.getID() == MouseEvent.MOUSE_RELEASED && contains(evt.getPoint())) {
              doClick();
              setArmed(true);
            } else {
              super.processMouseEvent(evt);
            }
          }
        };
    stayOpen.setActionCommand(source.getActionCommand());
    return stayOpen;
  }
}
