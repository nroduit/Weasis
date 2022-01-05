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

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.PageProps;
import org.weasis.core.ui.Messages;

public class DicomPrefView extends AbstractItemDialogPage {
  private final JPanel panelList = new JPanel();

  public DicomPrefView() {
    super("DICOM");
    setComponentPosition(20);
    setBorder(new EmptyBorder(15, 10, 10, 10));
    BorderLayout borderLayout = new BorderLayout();
    setLayout(borderLayout);

    JPanel panel2 = new JPanel();
    FlowLayout flowLayout1 = (FlowLayout) panel2.getLayout();
    flowLayout1.setHgap(10);
    flowLayout1.setAlignment(FlowLayout.RIGHT);
    flowLayout1.setVgap(7);
    add(panel2, BorderLayout.SOUTH);

    JButton btnNewButton = new JButton(Messages.getString("restore.values"));
    panel2.add(btnNewButton);
    btnNewButton.addActionListener(e -> resetoDefaultValues());

    panelList.setLayout(new BoxLayout(panelList, BoxLayout.Y_AXIS));
  }

  @Override
  public void closeAdditionalWindow() {
    for (PageProps subpage : getSubPages()) {
      subpage.closeAdditionalWindow();
    }
  }

  @Override
  public void resetoDefaultValues() {}
}
