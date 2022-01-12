/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.pref;

import java.awt.GridLayout;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.PageItem;

public class DicomPrefView extends AbstractItemDialogPage {

  public DicomPrefView(PreferenceDialog dialog) {
    super("DICOM");
    setComponentPosition(20);
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(GuiUtils.getEmptydBorder(15, 10, 10, 10));


    JPanel panel = new JPanel();
    panel.setLayout(new GridLayout(0, 2));
    add(panel, 0);
    add(GuiUtils.createVerticalStrut(15), 1);

    getSubPages().forEach(
        p -> {
          JButton button = new JButton();
          button.setText(p.getTitle());
          button.addActionListener(a -> dialog.showPage(p.getTitle()));
          panel.add(button);
        });
    add(panel);

    getProperties().setProperty(PreferenceDialog.KEY_SHOW_RESTORE, Boolean.TRUE.toString());
  }

  @Override
  public void closeAdditionalWindow() {
    for (PageItem subpage : getSubPages()) {
      subpage.closeAdditionalWindow();
    }
  }

  @Override
  public void resetToDefaultValues() {}
}
