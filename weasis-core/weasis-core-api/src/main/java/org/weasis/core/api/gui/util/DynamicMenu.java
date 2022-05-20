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

import com.formdev.flatlaf.util.SystemInfo;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

public abstract class DynamicMenu extends JMenu {

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

  public abstract void popupMenuWillBecomeVisible();

  public void popupMenuWillBecomeInvisible() {
    // Wait the action performed of JMenuItem (Bug on Mac menu bar)
    TimerTask task =
        new TimerTask() {

          @Override
          public void run() {
            removeAll();
          }
        };
    Timer timer = new Timer();
    timer.schedule(task, 250);
  }

  public void popupMenuCanceled() {}

  public void addPopupMenuListener() {
    // #WEA-6 - workaround, PopupMenuListener doesn't work on Mac in the top bar
    if (SystemInfo.isMacOS
        && Boolean.TRUE
            .toString()
            .equals(
                System.getProperty(
                    "apple.laf.useScreenMenuBar", Boolean.FALSE.toString()))) { // NON-NLS
      this.addChangeListener(
          e -> {
            if (DynamicMenu.this.isSelected()) {
              DynamicMenu.this.removeAll();
              DynamicMenu.this.popupMenuWillBecomeVisible();
            } else {
              DynamicMenu.this.popupMenuWillBecomeInvisible();
            }
          });
    } else {
      JPopupMenu menuExport = this.getPopupMenu();
      menuExport.addPopupMenuListener(
          new PopupMenuListener() {

            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
              DynamicMenu.this.popupMenuWillBecomeVisible();
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
              DynamicMenu.this.popupMenuWillBecomeInvisible();
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
              DynamicMenu.this.popupMenuCanceled();
            }
          });
    }
  }
}
