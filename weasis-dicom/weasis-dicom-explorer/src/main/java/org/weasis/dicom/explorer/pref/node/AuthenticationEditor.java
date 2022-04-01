/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.pref.node;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.WindowConstants;
import org.weasis.core.api.auth.AuthMethod;
import org.weasis.core.api.auth.OAuth2ServiceFactory;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.dicom.explorer.Messages;

public class AuthenticationEditor extends JDialog {

  private final DefaultListSelectionModel selectedModel = new DefaultListSelectionModel();
  private final JList<AuthMethod> jList1 = new JList<>();
  private final JComboBox<AuthMethod> comboBox;

  public AuthenticationEditor(Window parent, JComboBox<AuthMethod> comboBox) {
    super(parent, Messages.getString("authentication.manager"), ModalityType.APPLICATION_MODAL);
    this.comboBox = comboBox;
    jList1.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    jbInit();
    initializeList();
    jList1.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent evt) {
            if (evt.getClickCount() == 2) {
              editHeader();
            }
          }
        });
    pack();
  }

  private void jbInit() {
    this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());
    panel.setBorder(GuiUtils.getEmptyBorder(10, 15, 5, 15));

    selectedModel.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    JButton jButtonClose = new JButton(Messages.getString("HttpHeadersEditor.close"));
    jButtonClose.addActionListener(e -> cancel());

    jList1.setSelectionModel(selectedModel);

    JButton jButtonDelete = new JButton(Messages.getString("HttpHeadersEditor.delete"));
    jButtonDelete.addActionListener(e -> deleteSelectedComponents());

    JButton jButtonEdit = new JButton(Messages.getString("HttpHeadersEditor.edit"));
    jButtonEdit.addActionListener(e -> editHeader());

    JButton jButtonAdd = new JButton(Messages.getString("HttpHeadersEditor.add"));
    jButtonAdd.addActionListener(e -> add());

    JScrollPane jScrollPane1 = new JScrollPane(jList1);
    jScrollPane1.setPreferredSize(GuiUtils.getDimension(300, 150));

    panel.add(
        GuiUtils.getFlowLayoutPanel(FlowLayout.TRAILING, 0, 10, jButtonClose), BorderLayout.SOUTH);
    panel.add(
        GuiUtils.getHorizontalBoxLayoutPanel(
            10, GuiUtils.getVerticalBoxLayoutPanel(10, jButtonEdit, jButtonAdd, jButtonDelete)),
        BorderLayout.EAST);
    panel.add(jScrollPane1, BorderLayout.CENTER);
    setContentPane(panel);
  }

  // Overridden so we can exit when window is closed
  @Override
  protected void processWindowEvent(WindowEvent e) {
    if (e.getID() == WindowEvent.WINDOW_CLOSING) {
      cancel();
    }
    super.processWindowEvent(e);
  }

  // Close the dialog
  public void cancel() {
    dispose();
  }

  private synchronized void initializeList() {
    ComboBoxModel<AuthMethod> model = comboBox.getModel();
    int size = model.getSize();
    List<AuthMethod> list = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      AuthMethod auth = model.getElementAt(i);
      if (!OAuth2ServiceFactory.noAuth.equals(auth)) {
        list.add(auth);
      }
    }
    jList1.setListData(list.toArray(new AuthMethod[0]));
  }

  public void deleteSelectedComponents() {
    if (isNoComponentSelected()) {
      return;
    }

    List<AuthMethod> selItems = jList1.getSelectedValuesList();
    for (AuthMethod val : selItems) {
      comboBox.removeItem(val);
    }
    selectedModel.clearSelection();
    initializeList();
  }

  public void editHeader() {
    if (isNoComponentSelected()) {
      return;
    }

    List<AuthMethod> selItems = jList1.getSelectedValuesList();
    if (selItems.size() == 1) {
      modify(selItems.get(0));
    } else {
      JOptionPane.showMessageDialog(
          this, Messages.getString("no.item"), this.getTitle(), JOptionPane.ERROR_MESSAGE);
    }
  }

  private boolean isNoComponentSelected() {
    if (selectedModel.isSelectionEmpty()) {
      JOptionPane.showMessageDialog(
          this, Messages.getString("only.one.item"), this.getTitle(), JOptionPane.ERROR_MESSAGE);
      return true;
    }
    return false;
  }

  private void add() {
    modify(null);
  }

  private void modify(AuthMethod input) {
    AuthMethodDialog dialog =
        new AuthMethodDialog(this, Messages.getString("auth.method"), input, comboBox);
    GuiUtils.showCenterScreen(dialog);
    if (input == null) {
      initializeList();
    }
  }
}
