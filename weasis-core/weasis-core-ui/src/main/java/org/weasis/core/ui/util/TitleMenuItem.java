/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import javax.swing.JLabel;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import org.weasis.core.api.gui.util.GuiUtils;

/**
 * Title menu item for JPopupMenu
 *
 * <p>The area of title allows to release the click without hiding the popup.
 */
public class TitleMenuItem extends JLabel implements MenuElement {

  public TitleMenuItem(String title) {
    setBorder(GuiUtils.getEmptyBorder(2, 4, 2, 4));
    setText(title);
  }

  @Override
  public void processMouseEvent(MouseEvent e, MenuElement[] path, MenuSelectionManager manager) {
    // Do nothing
  }

  @Override
  public void processKeyEvent(KeyEvent e, MenuElement[] path, MenuSelectionManager manager) {
    // Do nothing
  }

  @Override
  public void menuSelectionChanged(boolean isIncluded) {
    // Do nothing
  }

  @Override
  public MenuElement[] getSubElements() {
    return new MenuElement[0];
  }

  @Override
  public Component getComponent() {
    return this;
  }
}
