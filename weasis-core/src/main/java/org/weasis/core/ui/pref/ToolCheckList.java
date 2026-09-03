/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.pref;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GuiUtils;

/**
 * Ordered list of tools with a check box each: the checked ones, in this order, are a palette. The
 * selected row is moved with the two buttons, with Alt + arrow or by dragging it; a click on the
 * check box or the space bar toggles it.
 */
class ToolCheckList extends JPanel {

  /** A tool of the list. */
  static final class Item {
    private final String key;
    private final String name;
    private final transient Icon icon;
    private boolean checked;

    Item(String key, String name, Icon icon, boolean checked) {
      this.key = key;
      this.name = name;
      this.icon = icon;
      this.checked = checked;
    }

    String key() {
      return key;
    }

    boolean isChecked() {
      return checked;
    }
  }

  private final DefaultListModel<Item> model = new DefaultListModel<>();
  private final JList<Item> list = new JList<>(model);
  private final JButton up = new JButton(Messages.getString("ProfilePrefView.up"));
  private final JButton down = new JButton(Messages.getString("ProfilePrefView.down"));
  private final int checkBoxWidth = new JCheckBox().getPreferredSize().width;

  ToolCheckList(String title) {
    super(new BorderLayout(0, GuiUtils.getScaleLength(3)));
    setBorder(GuiUtils.getTitledBorder(title));
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(new RowRenderer());
    list.setVisibleRowCount(9);
    list.addListSelectionListener(_ -> updateButtons());
    list.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            int index = list.locationToIndex(e.getPoint());
            Rectangle cell = index < 0 ? null : list.getCellBounds(index, index);
            if (cell != null && cell.contains(e.getPoint()) && list.isEnabled()) {
              boolean onBox = e.getX() - cell.x <= checkBoxWidth + GuiUtils.getScaleLength(4);
              if (onBox || e.getClickCount() == 2) {
                toggle(index);
              }
            }
          }
        });
    bind(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), () -> toggle(list.getSelectedIndex()));
    bind(KeyStroke.getKeyStroke(KeyEvent.VK_UP, KeyEvent.ALT_DOWN_MASK), () -> move(-1));
    bind(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.ALT_DOWN_MASK), () -> move(1));
    list.setDragEnabled(true);
    list.setDropMode(DropMode.INSERT);
    list.setTransferHandler(new ReorderHandler());

    up.addActionListener(_ -> move(-1));
    down.addActionListener(_ -> move(1));
    add(new JScrollPane(list), BorderLayout.CENTER);
    add(GuiUtils.getFlowLayoutPanel(up, down), BorderLayout.SOUTH);
    updateButtons();
  }

  private void bind(KeyStroke key, Runnable action) {
    list.getInputMap(JComponent.WHEN_FOCUSED).put(key, key.toString());
    list.getActionMap()
        .put(
            key.toString(),
            new AbstractAction() {
              @Override
              public void actionPerformed(java.awt.event.ActionEvent e) {
                if (list.isEnabled()) {
                  action.run();
                }
              }
            });
  }

  void setItems(List<Item> items) {
    model.clear();
    items.forEach(model::addElement);
    updateButtons();
  }

  /** Keys of the checked tools, in the order of the list. */
  List<String> checkedKeys() {
    List<String> keys = new ArrayList<>();
    for (int i = 0; i < model.size(); i++) {
      if (model.get(i).checked) {
        keys.add(model.get(i).key);
      }
    }
    return keys;
  }

  /** Keys of all the tools, in the order of the list. */
  List<String> keys() {
    List<String> keys = new ArrayList<>();
    for (int i = 0; i < model.size(); i++) {
      keys.add(model.get(i).key);
    }
    return keys;
  }

  void setChecked(String key, boolean checked) {
    for (int i = 0; i < model.size(); i++) {
      if (model.get(i).key.equals(key)) {
        model.get(i).checked = checked;
        model.set(i, model.get(i));
      }
    }
  }

  void select(int index) {
    list.setSelectedIndex(index);
  }

  int selectedIndex() {
    return list.getSelectedIndex();
  }

  void toggle(int index) {
    if (index >= 0 && index < model.size()) {
      Item item = model.get(index);
      item.checked = !item.checked;
      model.set(index, item);
    }
  }

  /** Moves the selected row up (-1) or down (1) and keeps it selected. */
  void move(int direction) {
    moveRow(list.getSelectedIndex(), list.getSelectedIndex() + direction);
  }

  /** Moves a row to the place of another one. */
  void moveRow(int from, int to) {
    if (from < 0 || from >= model.size() || to < 0 || to >= model.size() || from == to) {
      return;
    }
    Item item = model.remove(from);
    model.add(to, item);
    list.setSelectedIndex(to);
    list.ensureIndexIsVisible(to);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    list.setEnabled(enabled);
    updateButtons();
  }

  private void updateButtons() {
    int index = list.getSelectedIndex();
    up.setEnabled(list.isEnabled() && index > 0);
    down.setEnabled(list.isEnabled() && index >= 0 && index < model.size() - 1);
  }

  /** A check box, then the icon and the name of the tool. */
  private static final class RowRenderer extends JPanel
      implements javax.swing.ListCellRenderer<Item> {
    private final JCheckBox box = new JCheckBox();
    private final JLabel label = new JLabel();

    RowRenderer() {
      super(new BorderLayout(GuiUtils.getScaleLength(4), 0));
      box.setOpaque(false);
      add(box, BorderLayout.WEST);
      add(label, BorderLayout.CENTER);
      setBorder(GuiUtils.getEmptyBorder(1, 2, 1, 2));
    }

    /** Lays out the row even when the list has no peer, as when it is printed off screen. */
    @Override
    public void validate() {
      doLayout();
    }

    @Override
    public Component getListCellRendererComponent(
        JList<? extends Item> list, Item value, int index, boolean selected, boolean focus) {
      box.setSelected(value.checked);
      box.setEnabled(list.isEnabled());
      label.setText(value.name);
      label.setIcon(value.icon);
      label.setEnabled(list.isEnabled());
      setBackground(selected ? list.getSelectionBackground() : list.getBackground());
      label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
      return this;
    }
  }

  /** Drag and drop of a row inside the list. */
  private final class ReorderHandler extends TransferHandler {
    @Override
    public int getSourceActions(JComponent c) {
      return list.isEnabled() ? MOVE : NONE;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
      return new StringSelection(String.valueOf(list.getSelectedIndex()));
    }

    @Override
    public boolean canImport(TransferSupport support) {
      return support.isDrop()
          && support.getComponent() == list
          && support.isDataFlavorSupported(DataFlavor.stringFlavor);
    }

    @Override
    public boolean importData(TransferSupport support) {
      if (!canImport(support)) {
        return false;
      }
      try {
        int from =
            Integer.parseInt(
                (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor));
        int to = ((JList.DropLocation) support.getDropLocation()).getIndex();
        moveRow(from, to > from ? to - 1 : to);
        return true;
      } catch (Exception e) {
        return false;
      }
    }
  }
}
