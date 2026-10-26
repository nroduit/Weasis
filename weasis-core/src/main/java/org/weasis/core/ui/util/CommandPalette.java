/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.editor.ActionCatalog;
import org.weasis.core.ui.editor.ActionEntry;

/**
 * A searchable list of every action of the {@link ActionCatalog}: typing filters, Enter runs the
 * selected one and closes, Escape closes. An action the selected view hides is listed dimmed with
 * its group, and still runs.
 */
public final class CommandPalette extends JDialog {

  private static final int ROWS = 12;

  private final JTextField field = new JTextField();
  private final DefaultListModel<ActionEntry> model = new DefaultListModel<>();
  private final JList<ActionEntry> list = new JList<>(model);

  private CommandPalette(Window owner) {
    super(owner);
    setUndecorated(true);
    setModalityType(ModalityType.MODELESS);
    JPanel content = new JPanel(new BorderLayout());
    content.setBorder(BorderFactory.createLineBorder(list.getSelectionBackground()));
    field.setToolTipText(Messages.getString("CommandPalette.tip"));
    field.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
    content.add(field, BorderLayout.NORTH);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setVisibleRowCount(ROWS);
    list.setCellRenderer(new Renderer());
    content.add(new JScrollPane(list), BorderLayout.CENTER);
    setContentPane(content);

    field.getDocument().addDocumentListener(new Filter());
    field.addKeyListener(new Keys());
    list.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
              runSelected();
            }
          }
        });
    addWindowFocusListener(
        new WindowAdapter() {
          @Override
          public void windowLostFocus(WindowEvent e) {
            dispose();
          }
        });
    refresh();
    setPreferredSize(new Dimension(GuiUtils.getScaleLength(560), getPreferredSize().height));
    pack();
    setLocationRelativeTo(owner);
  }

  /** Opens the palette over a window, the text field focused. */
  public static CommandPalette show(Window owner) {
    CommandPalette palette = new CommandPalette(owner);
    palette.setVisible(true);
    palette.field.requestFocusInWindow();
    return palette;
  }

  private void refresh() {
    String query = field.getText();
    List<ActionEntry> entries =
        query == null || query.isBlank() ? ActionCatalog.entries() : ActionCatalog.search(query);
    model.clear();
    model.addAll(entries);
    if (!entries.isEmpty()) {
      list.setSelectedIndex(0);
      list.ensureIndexIsVisible(0);
    }
  }

  private void runSelected() {
    ActionEntry entry = list.getSelectedValue();
    dispose();
    if (entry != null) {
      entry.run().run();
    }
  }

  private void move(int delta) {
    int size = model.size();
    if (size > 0) {
      int index = Math.floorMod(list.getSelectedIndex() + delta, size);
      list.setSelectedIndex(index);
      list.ensureIndexIsVisible(index);
    }
  }

  private final class Filter implements DocumentListener {
    @Override
    public void insertUpdate(DocumentEvent e) {
      refresh();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
      refresh();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
      refresh();
    }
  }

  private final class Keys extends KeyAdapter {
    @Override
    public void keyPressed(KeyEvent e) {
      switch (e.getKeyCode()) {
        case KeyEvent.VK_ENTER -> runSelected();
        case KeyEvent.VK_ESCAPE -> dispose();
        case KeyEvent.VK_DOWN -> move(1);
        case KeyEvent.VK_UP -> move(-1);
        case KeyEvent.VK_PAGE_DOWN -> move(ROWS);
        case KeyEvent.VK_PAGE_UP -> move(-ROWS);
        default -> {
          return;
        }
      }
      e.consume();
    }
  }

  private static final class Renderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(
        JList<?> list, Object value, int index, boolean selected, boolean focused) {
      JLabel label =
          (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
      if (value instanceof ActionEntry entry) {
        StringBuilder html = new StringBuilder("<html>");
        if (!entry.relevant()) {
          html.append("<span style='color:gray'>");
        }
        html.append(escape(entry.label()));
        if (!entry.relevant()) {
          html.append("</span>");
        }
        html.append(" <span style='color:gray'>— ").append(escape(entry.group()));
        if (!entry.shortcut().isEmpty()) {
          html.append(" (").append(escape(entry.shortcut())).append(')');
        }
        html.append("</span></html>");
        label.setText(html.toString());
        label.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
      }
      return label;
    }

    private static String escape(String text) {
      return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
  }
}
