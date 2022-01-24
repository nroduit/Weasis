/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.util.StringUtil;

public abstract class AbstractTagSearchPanel extends JPanel implements KeyListener {
  protected final JTextField textFieldSearch = new JTextField();
  protected final JToolBar navigateToolbar = new JToolBar();

  protected AbstractTagSearchPanel() {
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    add(new JLabel(Messages.getString("DicomFieldsView.search") + StringUtil.COLON_AND_SPACE));

    GuiUtils.setPreferredWidth(textFieldSearch, 300, 100);
    textFieldSearch
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {

              @Override
              public void insertUpdate(DocumentEvent e) {
                filter();
              }

              @Override
              public void removeUpdate(DocumentEvent e) {
                filter();
              }

              @Override
              public void changedUpdate(DocumentEvent e) {
                filter();
              }
            });
    add(textFieldSearch);
    textFieldSearch.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);

    JButton up = new JButton(GuiUtils.getUpArrowIcon());
    up.setToolTipText(Messages.getString("DicomFieldsView.previous"));
    up.addActionListener(evt -> previous());
    navigateToolbar.add(up);
    JButton down = new JButton(GuiUtils.getDownArrowIcon());
    down.setToolTipText(Messages.getString("DicomFieldsView.next"));
    down.addActionListener(evt -> next());
    navigateToolbar.add(down);

    textFieldSearch.putClientProperty(
        FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, navigateToolbar);
  }

  protected abstract void filter();

  protected abstract void previous();

  protected abstract void next();

  @Override
  public void keyTyped(KeyEvent e) {}

  @Override
  public void keyReleased(KeyEvent e) {
    if (e.isShiftDown() && e.getKeyCode() == KeyEvent.VK_F3) {
      previous();
    } else if (e.getKeyCode() == KeyEvent.VK_F3) {
      next();
    }
  }

  @Override
  public void keyPressed(KeyEvent e) {}
}
