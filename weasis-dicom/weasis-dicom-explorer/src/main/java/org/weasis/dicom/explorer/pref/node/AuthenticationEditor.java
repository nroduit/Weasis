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
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
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
import javax.swing.border.Border;
import org.weasis.core.api.auth.AuthMethod;
import org.weasis.core.api.auth.OAuth2ServiceFactory;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.dicom.explorer.Messages;

public class AuthenticationEditor extends JDialog {

  private final DefaultListSelectionModel selctedModel = new DefaultListSelectionModel();
  private final JPanel panel1 = new JPanel();
  private final BorderLayout borderLayout1 = new BorderLayout();
  private final JButton jButtonClose = new JButton();
  private final GridBagLayout gridBagLayout3 = new GridBagLayout();
  private final JPanel jPanelComponentBar = new JPanel();
  private final JList<AuthMethod> jList1 = new JList<>();
  private final JPanel jPanelComponentAction = new JPanel();
  private final JButton jButtonDelete = new JButton();
  private final JButton jButtonEdit = new JButton();
  private final GridBagLayout gridBagLayout2 = new GridBagLayout();
  private final JButton jButtonAdd = new JButton();
  private final JScrollPane jScrollPane1 = new JScrollPane();
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
    selctedModel.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    Border border1 = BorderFactory.createEmptyBorder(5, 5, 5, 5);
    Border border2 =
        BorderFactory.createCompoundBorder(
            BorderFactory.createEtchedBorder(), BorderFactory.createEmptyBorder(5, 5, 5, 5));
    panel1.setLayout(borderLayout1);

    jButtonClose.setText(Messages.getString("HttpHeadersEditor.close"));
    jButtonClose.addActionListener(e -> cancel());
    jPanelComponentBar.setLayout(gridBagLayout3);
    jList1.setBorder(border2);
    jList1.setSelectionModel(selctedModel);
    jPanelComponentAction.setLayout(gridBagLayout2);
    jButtonDelete.addActionListener(e -> deleteSelectedComponents());
    jButtonDelete.setText(Messages.getString("HttpHeadersEditor.delete"));

    jButtonEdit.setText(Messages.getString("HttpHeadersEditor.edit"));
    jButtonEdit.addActionListener(e -> editHeader());

    jButtonAdd.addActionListener(e -> add());
    jButtonAdd.setText(Messages.getString("HttpHeadersEditor.add"));

    jScrollPane1.setBorder(border1);
    jScrollPane1.setPreferredSize(new Dimension(300, 150));
    panel1.add(jPanelComponentBar, BorderLayout.SOUTH);

    jPanelComponentBar.add(
        jButtonClose,
        new GridBagConstraints(
            2,
            0,
            1,
            1,
            0.5,
            0.0,
            GridBagConstraints.EAST,
            GridBagConstraints.NONE,
            new Insets(10, 0, 10, 20),
            0,
            0));
    panel1.add(jPanelComponentAction, BorderLayout.EAST);
    panel1.add(jScrollPane1, BorderLayout.CENTER);
    jScrollPane1.getViewport().add(jList1, null);
    this.getContentPane().add(panel1, BorderLayout.CENTER);
    jPanelComponentAction.add(
        jButtonEdit,
        new GridBagConstraints(
            0,
            1,
            1,
            1,
            1.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(7, 5, 0, 10),
            0,
            0));
    jPanelComponentAction.add(
        jButtonAdd,
        new GridBagConstraints(
            0,
            0,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(15, 0, 0, 5),
            0,
            0));
    jPanelComponentAction.add(
        jButtonDelete,
        new GridBagConstraints(
            0,
            2,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(7, 0, 0, 5),
            0,
            0));
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
    selctedModel.clearSelection();
    initializeList();
  }

  public void editHeader() {
    if (isNoComponentSelected()) {
      return;
    }

    List<AuthMethod> selItems = jList1.getSelectedValuesList();
    if (selItems.size() == 1) {
      modifiy(selItems.get(0));
    } else {
      JOptionPane.showMessageDialog(
          this, Messages.getString("no.item"), this.getTitle(), JOptionPane.ERROR_MESSAGE);
    }
  }

  private boolean isNoComponentSelected() {
    if (selctedModel.isSelectionEmpty()) {
      JOptionPane.showMessageDialog(
          this, Messages.getString("only.one.item"), this.getTitle(), JOptionPane.ERROR_MESSAGE);
      return true;
    }
    return false;
  }

  private void add() {
    modifiy(null);
  }

  private void modifiy(AuthMethod input) {
    AuthMethodDialog dialog =
        new AuthMethodDialog(this, Messages.getString("auth.method"), input, comboBox);
    JMVUtils.showCenterScreen(dialog);
    if (input == null) {
      initializeList();
    }
  }
}
