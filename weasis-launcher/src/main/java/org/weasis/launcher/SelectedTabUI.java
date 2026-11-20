/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.launcher;

import com.formdev.flatlaf.ui.FlatTabbedPaneUI;
import java.awt.Color;
import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;

/**
 * The tabs of FlatLaf with a selected tab that keeps its selection color under the pointer, as a
 * selected button does: the hover color is for the tabs that are not selected.
 */
public class SelectedTabUI extends FlatTabbedPaneUI {

  public static ComponentUI createUI(JComponent c) {
    return new SelectedTabUI();
  }

  @Override
  protected Color getTabBackground(int tabPlacement, int tabIndex, boolean isSelected) {
    if (isSelected && selectedBackground != null && tabPane.isEnabledAt(tabIndex)) {
      return selectedBackground;
    }
    return super.getTabBackground(tabPlacement, tabIndex, isSelected);
  }
}
