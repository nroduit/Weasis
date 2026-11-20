/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.Timer;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

/**
 * A menu whose items are built each time it opens, from the state of the application at that
 * moment.
 *
 * <p>It is filled when the menu is selected and emptied shortly after it is deselected, through the
 * {@link MenuListener} of Swing: the one notification every look and feel sends, the screen menu
 * bar of macOS included (which never sends the popup events a {@code PopupMenuListener} waits for).
 * The same code therefore runs on every system. A menu is always filled from nothing, so its items
 * cannot be listed twice, and a reader of the menu bar (the action catalog of the command palette)
 * gets the items in a menu of its own with {@link #detached()}: the menu of the menu bar, native on
 * macOS, is only touched when the user opens it, and no item stays in it with a live accelerator.
 */
public abstract class DynamicMenu extends JMenu {

  /** Delay before a closed menu is emptied, so the action of the item chosen fires first. */
  private static final int CLEAR_DELAY_MS = 250;

  /** The delayed clearing started when the menu closed, null when none is waiting. */
  private transient Timer pendingClear;

  private boolean listening;

  protected DynamicMenu() {
    super();
  }

  protected DynamicMenu(Action a) {
    super(a);
  }

  protected DynamicMenu(String s, boolean b) {
    super(s, b);
  }

  protected DynamicMenu(String s) {
    super(s);
  }

  /**
   * Adds the items to a menu that is empty: this menu when the user opens it, or another one for a
   * reader ({@link #detached()}). Only add to {@code menu}; never to {@code this}.
   */
  protected abstract void fill(JMenu menu);

  /**
   * Fills this menu from nothing: a clearing still pending from the last time it closed is dropped
   * and what is left in the menu is removed first.
   */
  public final void refill() {
    cancelPendingClear();
    removeAll();
    fill(this);
  }

  /**
   * The items this menu would show now, in a menu that is not in the menu bar. Running an item of
   * it acts as the one of the menu bar would.
   */
  public final JMenu detached() {
    JMenu copy = new JMenu(getText());
    fill(copy);
    return copy;
  }

  /** Empties the menu a moment after it closed. */
  private void clearLater() {
    cancelPendingClear();
    // A Swing timer, so the removal runs on the event thread: the items of the screen menu bar of
    // macOS are native, and changing them from another thread can freeze the interface. The delay
    // lets the action of the chosen item fire before its item leaves the menu.
    Timer timer = new Timer(CLEAR_DELAY_MS, null);
    timer.addActionListener(
        e -> {
          // only the clearing of the last closing empties the menu, and never an open one
          if (pendingClear == timer) {
            pendingClear = null;
            if (!isSelected()) {
              removeAll();
            }
          }
        });
    timer.setRepeats(false);
    pendingClear = timer;
    timer.start();
  }

  private void cancelPendingClear() {
    if (pendingClear != null) {
      pendingClear.stop();
      pendingClear = null;
    }
  }

  /**
   * Starts filling the menu when it opens and emptying it when it closes. Call it once, when the
   * menu is created.
   */
  public void addPopupMenuListener() {
    if (listening) {
      return;
    }
    listening = true;
    addMenuListener(
        new MenuListener() {
          @Override
          public void menuSelected(MenuEvent e) {
            refill();
          }

          @Override
          public void menuDeselected(MenuEvent e) {
            clearLater();
          }

          @Override
          public void menuCanceled(MenuEvent e) {
            clearLater();
          }
        });
  }
}
