/*
 * Copyright (c) 2025 Weasis Team and other contributors.
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
import java.util.List;
import javax.swing.*;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.dicom.explorer.Messages;

public abstract class AbstractListEditor<T> extends JDialog {

  protected final DefaultListSelectionModel selectedModel = new DefaultListSelectionModel();
  protected final JList<T> itemList = new JList<>();

  public AbstractListEditor(Window parent, String title) {
    super(parent, title, ModalityType.APPLICATION_MODAL);
    itemList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    jbInit();
    itemList.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent evt) {
            if (evt.getClickCount() == 2) {
              editItem();
            }
          }
        });
  }

  private void jbInit() {
    this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(GuiUtils.getEmptyBorder(10, 15, 5, 15));

    selectedModel.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    JButton jButtonClose = new JButton(Messages.getString("HttpHeadersEditor.close"));
    jButtonClose.addActionListener(_ -> cancel());

    itemList.setSelectionModel(selectedModel);

    JButton jButtonDelete = new JButton(Messages.getString("HttpHeadersEditor.delete"));
    jButtonDelete.addActionListener(_ -> deleteSelectedItems());

    JButton jButtonEdit = new JButton(Messages.getString("HttpHeadersEditor.edit"));
    jButtonEdit.addActionListener(_ -> editItem());

    JButton jButtonAdd = new JButton(Messages.getString("HttpHeadersEditor.add"));
    jButtonAdd.addActionListener(_ -> addItem());

    JScrollPane scrollPane = new JScrollPane(itemList);
    scrollPane.setPreferredSize(GuiUtils.getDimension(300, 150));

    panel.add(
        GuiUtils.getFlowLayoutPanel(FlowLayout.TRAILING, 0, 10, jButtonClose), BorderLayout.SOUTH);
    panel.add(
        GuiUtils.getHorizontalBoxLayoutPanel(
            10, GuiUtils.getVerticalBoxLayoutPanel(10, jButtonEdit, jButtonAdd, jButtonDelete)),
        BorderLayout.EAST);
    panel.add(scrollPane, BorderLayout.CENTER);
    setContentPane(panel);
  }

  @Override
  protected void processWindowEvent(WindowEvent e) {
    if (e.getID() == WindowEvent.WINDOW_CLOSING) {
      cancel();
    }
    super.processWindowEvent(e);
  }

  public void cancel() {
    dispose();
  }

  public void deleteSelectedItems() {
    if (isNoItemSelected()) {
      return;
    }

    List<T> selectedItems = itemList.getSelectedValuesList();
    for (T item : selectedItems) {
      deleteItem(item);
    }
    selectedModel.clearSelection();
    initializeList();
  }

  public void editItem() {
    if (isNoItemSelected()) {
      return;
    }

    List<T> selectedItems = itemList.getSelectedValuesList();
    if (selectedItems.size() == 1) {
      modifyItem(selectedItems.getFirst());
    } else {
      JOptionPane.showMessageDialog(
          this,
          Messages.getString("HttpHeadersEditor.msg_onlyone"),
          this.getTitle(),
          JOptionPane.ERROR_MESSAGE);
    }
  }

  private boolean isNoItemSelected() {
    if (selectedModel.isSelectionEmpty()) {
      JOptionPane.showMessageDialog(
          this,
          Messages.getString("HttpHeadersEditor.msg_noheader"),
          this.getTitle(),
          JOptionPane.ERROR_MESSAGE);
      return true;
    }
    return false;
  }

  public void addItem() {
    modifyItem(null);
  }

  protected abstract void initializeList();

  protected abstract void deleteItem(T item);

  protected abstract void modifyItem(T item);
}
