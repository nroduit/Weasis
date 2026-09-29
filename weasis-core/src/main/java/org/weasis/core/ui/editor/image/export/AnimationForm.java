/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image.export;

import static org.weasis.core.api.gui.Insertable.ITEM_SEPARATOR_SMALL;

import java.awt.FlowLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.util.StringUtil;

/**
 * The two-column form of the export dialog: a label and a row of controls, both flush left. The
 * source builders add their own rows to it, so their controls line up with the common ones.
 */
public final class AnimationForm {

  private final JPanel panel =
      new JPanel(
          new MigLayout("insets 0, wrap 2, gapy 4lp", "[left]8lp[left, grow, fill]")); // NON-NLS

  JPanel getPanel() {
    return panel;
  }

  /** One row: {@code label} (or none) and its controls, laid out left to right. */
  public JPanel row(String label, JComponent... fields) {
    JPanel fieldPanel =
        GuiUtils.getFlowLayoutPanel(FlowLayout.LEADING, ITEM_SEPARATOR_SMALL, 0, fields);
    if (label == null) {
      panel.add(fieldPanel, "skip 1"); // NON-NLS
    } else {
      panel.add(new JLabel(label + StringUtil.COLON));
      panel.add(fieldPanel);
    }
    return fieldPanel;
  }

  /** One component over both columns. */
  public void span(JComponent component) {
    panel.add(component, "span 2, growx, wmin 0"); // NON-NLS
  }
}
