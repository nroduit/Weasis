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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

public abstract class DropDownButton extends JButton implements PopupMenuListener, ActionListener {

  private final String type;
  private final GroupPopup menuModel;

  protected DropDownButton(String type, Icon icon) {
    this(type, icon, null);
  }

  protected DropDownButton(String type, Icon icon, GroupPopup model) {
    super(icon);
    this.menuModel = model;
    this.type = type;
    init();
  }

  protected DropDownButton(String type, String text, Icon icon, GroupPopup model) {
    super(text, icon);
    this.type = type;
    this.menuModel = model;
    init();
  }

  private void init() {
    addActionListener(this);
  }

  public GroupPopup getMenuModel() {
    return menuModel;
  }

  @Override
  public void actionPerformed(ActionEvent ae) {
    JPopupMenu popup = getPopupMenu();
    popup.addPopupMenuListener(this);
    popup.show(this, 0, getHeight());
  }

  @Override
  public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
    getModel().setRollover(true);
    getModel().setSelected(true);
  }

  @Override
  public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
    getModel().setRollover(false);
    getModel().setSelected(false);
    ((JPopupMenu) e.getSource()).removePopupMenuListener(this);
  }

  @Override
  public void popupMenuCanceled(PopupMenuEvent e) {}

  public String getType() {
    return type;
  }

  protected abstract JPopupMenu getPopupMenu();
}
