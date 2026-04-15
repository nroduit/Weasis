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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import javax.swing.text.html.HTMLEditorKit;
import net.miginfocom.swing.MigLayout;
import org.osgi.service.prefs.Preferences;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.gui.util.ShortcutManager;
import org.weasis.core.api.gui.util.ShortcutManager.ShortcutEntry;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.util.StringUtil;

/**
 * Preference page that displays all registered keyboard shortcuts in a table and allows the user to
 * customize them. Conflicts are highlighted and the user is warned before saving duplicates.
 */
public class ShortcutPrefView extends AbstractItemDialogPage {

  private static final String TITLE = Messages.getString("shortcuts");

  private static final int COL_CATEGORY = 0;
  private static final int COL_ACTION = 1;
  private static final int COL_SHORTCUT = 2;
  private static final int COL_DEFAULT = 3;

  private final ShortcutTableModel tableModel;
  private final JTable table;
  private final TableRowSorter<ShortcutTableModel> sorter;
  private final JTextField searchField;

  public ShortcutPrefView() {
    super(TITLE, 105);

    getProperties().put(PreferenceDialog.KEY_SHOW_RESTORE, Boolean.TRUE.toString());
    getProperties().put(PreferenceDialog.KEY_SHOW_APPLY, Boolean.TRUE.toString());

    setLayout(new BorderLayout(5, 5));

    // -- Search / filter bar --
    searchField = new JTextField(20);
    searchField.setToolTipText(Messages.getString("search.shortcut.filter"));
    JPanel searchPanel =
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.LEADING,
            5,
            2,
            new JLabel(Messages.getString("filter") + StringUtil.COLON),
            searchField);
    add(searchPanel, BorderLayout.NORTH);

    // -- Table --
    tableModel = new ShortcutTableModel();
    table = new JTable(tableModel);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setRowHeight(24);
    table.getColumnModel().getColumn(COL_CATEGORY).setPreferredWidth(100);
    table.getColumnModel().getColumn(COL_ACTION).setPreferredWidth(160);
    table.getColumnModel().getColumn(COL_SHORTCUT).setPreferredWidth(120);
    table.getColumnModel().getColumn(COL_DEFAULT).setPreferredWidth(120);
    table.setDefaultRenderer(Object.class, new ShortcutCellRenderer());

    sorter = new TableRowSorter<>(tableModel);
    table.setRowSorter(sorter);

    table.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
              editSelectedShortcut();
            }
          }
        });

    JScrollPane scrollPane = new JScrollPane(table);
    scrollPane.setPreferredSize(new Dimension(550, 300));
    add(scrollPane, BorderLayout.CENTER);

    // -- Bottom buttons --
    JButton editButton = new JButton(Messages.getString("edit.shortcut"));
    editButton.addActionListener(_ -> editSelectedShortcut());

    JButton clearButton = new JButton(Messages.getString("clear.shortcut"));
    clearButton.addActionListener(_ -> clearSelectedShortcut());

    JPanel buttonPanel =
        GuiUtils.getFlowLayoutPanel(FlowLayout.TRAILING, 5, 2, editButton, clearButton);
    add(buttonPanel, BorderLayout.SOUTH);

    // -- Filter listener --
    searchField
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent e) {
                applyFilter();
              }

              @Override
              public void removeUpdate(DocumentEvent e) {
                applyFilter();
              }

              @Override
              public void changedUpdate(DocumentEvent e) {
                applyFilter();
              }
            });
  }

  private void applyFilter() {
    String text = searchField.getText().trim();
    if (text.isEmpty()) {
      sorter.setRowFilter(null);
    } else {
      sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text)); // NON-NLS
    }
  }

  private void editSelectedShortcut() {
    int viewRow = table.getSelectedRow();
    if (viewRow < 0) {
      return;
    }
    int modelRow = table.convertRowIndexToModel(viewRow);
    ShortcutEntry entry = tableModel.getEntry(modelRow);

    // Open a small dialog that captures the next key press
    KeyCaptureDialog dialog = new KeyCaptureDialog(entry);
    if (dialog.isConfirmed()) {
      int newKeyCode = dialog.getCapturedKeyCode();
      int newModifier = dialog.getCapturedModifier();

      // Check for conflicts
      List<ShortcutEntry> conflicts =
          ShortcutManager.getInstance().findConflicts(entry.getId(), newKeyCode, newModifier);
      if (!conflicts.isEmpty()) {
        StringBuilder msg = new StringBuilder();
        msg.append(Messages.getString("shortcut.conflict.message")).append("\n\n");
        for (ShortcutEntry conflict : conflicts) {
          msg.append("• ").append(conflict.getDescription());
          msg.append(" (")
              .append(conflict.getCategory())
              .append(" — ")
              .append(conflict.getContext().getDisplayName())
              .append(")\n");
        }
        msg.append("\n").append(Messages.getString("shortcut.conflict.continue"));
        int result =
            JOptionPane.showConfirmDialog(
                this,
                msg.toString(),
                Messages.getString("shortcut.conflict.title"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.YES_OPTION) {
          return;
        }
      }

      ShortcutManager.getInstance().setShortcut(entry.getId(), newKeyCode, newModifier);
      tableModel.fireTableRowsUpdated(modelRow, modelRow);
    }
  }

  private void clearSelectedShortcut() {
    int viewRow = table.getSelectedRow();
    if (viewRow < 0) {
      return;
    }
    int modelRow = table.convertRowIndexToModel(viewRow);
    ShortcutEntry entry = tableModel.getEntry(modelRow);
    ShortcutManager.getInstance().setShortcut(entry.getId(), 0, 0);
    tableModel.fireTableRowsUpdated(modelRow, modelRow);
  }

  @Override
  public void closeAdditionalWindow() {
    // Apply changes: persist and update Feature objects
    ShortcutManager manager = ShortcutManager.getInstance();
    manager.applyToFeatures();
    Preferences prefs =
        BundlePreferences.getDefaultPreferences(AppProperties.getBundleContext(this.getClass()));
    manager.savePreferences(prefs);
  }

  @Override
  public void resetToDefaultValues() {
    ShortcutManager.getInstance().resetAllToDefaults();
    tableModel.refresh();
  }

  // ----- Table Model -----

  private static class ShortcutTableModel extends AbstractTableModel {
    private static final int COLUMN_COUNT = 4;

    private List<ShortcutEntry> data;

    ShortcutTableModel() {
      refresh();
    }

    void refresh() {
      data = ShortcutManager.getInstance().getShortcutList();
      fireTableDataChanged();
    }

    ShortcutEntry getEntry(int row) {
      return data.get(row);
    }

    @Override
    public int getRowCount() {
      return data.size();
    }

    @Override
    public int getColumnCount() {
      return COLUMN_COUNT;
    }

    @Override
    public String getColumnName(int column) {
      return switch (column) {
        case COL_CATEGORY -> Messages.getString("shortcut.column.category");
        case COL_ACTION -> Messages.getString("shortcut.column.action");
        case COL_SHORTCUT -> Messages.getString("shortcut.column.shortcut");
        case COL_DEFAULT -> Messages.getString("shortcut.column.default");
        default -> "";
      };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      ShortcutEntry entry = data.get(rowIndex);
      return switch (columnIndex) {
        case COL_CATEGORY -> entry.getCategory();
        case COL_ACTION -> entry.getDescription();
        case COL_SHORTCUT -> entry.getShortcutText();
        case COL_DEFAULT -> entry.getDefaultShortcutText();
        default -> "";
      };
    }
  }

  // ----- Cell renderer that highlights conflicts -----

  private static class ShortcutCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component c =
          super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (!isSelected) {
        int modelRow = table.convertRowIndexToModel(row);
        ShortcutTableModel model = (ShortcutTableModel) table.getModel();
        ShortcutEntry entry = model.getEntry(modelRow);
        if (entry.isModified()) {
          c.setForeground(IconColor.ACTIONS_BLUE.getColor());
        } else {
          c.setForeground(table.getForeground());
        }
        // Highlight conflicts in red
        if (column == COL_SHORTCUT && entry.getKeyCode() != 0) {
          List<ShortcutEntry> conflicts =
              ShortcutManager.getInstance()
                  .findConflicts(entry.getId(), entry.getKeyCode(), entry.getModifier());
          if (!conflicts.isEmpty()) {
            c.setForeground(IconColor.ACTIONS_RED.getColor());
          }
        }
      }
      return c;
    }
  }

  // ----- Key capture dialog -----

  private class KeyCaptureDialog {
    private final boolean confirmed;
    private int capturedKeyCode;
    private int capturedModifier;
    protected JTextField captureField = new JTextField(20);

    KeyCaptureDialog(ShortcutEntry entry) {
      captureField.setEditable(false);
      captureField.setText(Messages.getString("press.shortcut.key"));
      captureField.setHorizontalAlignment(JTextField.CENTER);
      captureField.setFont(FontItem.H3.getFont());

      // Request focus on captureField as soon as the dialog is shown
      captureField.addHierarchyListener(
          e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0
                && captureField.isShowing()) {
              SwingUtilities.invokeLater(captureField::requestFocusInWindow);
            }
          });

      captureField.addKeyListener(
          new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
              e.consume();
            }

            @Override
            public void keyPressed(KeyEvent e) {
              int kc = e.getKeyCode();
              // Ignore pure modifier keys
              if (kc == KeyEvent.VK_SHIFT
                  || kc == KeyEvent.VK_CONTROL
                  || kc == KeyEvent.VK_ALT
                  || kc == KeyEvent.VK_META) {
                return;
              }
              capturedKeyCode = kc;
              capturedModifier = e.getModifiers();
              KeyStroke ks = KeyStroke.getKeyStroke(capturedKeyCode, capturedModifier);
              captureField.setText(ShortcutManager.formatKeyStroke(ks));
              e.consume();
            }

            @Override
            public void keyReleased(KeyEvent e) {
              e.consume();
            }
          });

      JEditorPane descPane = new JEditorPane();
      descPane.setEditorKit(new HTMLEditorKit());
      descPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
      descPane.setFont(UIManager.getFont("Label.font"));
      descPane.setText(
          "<html>"
              + Messages.getString("edit.shortcut.for")
              + StringUtil.COLON
              + "<br><b>"
              + entry.getDescription()
              + "</b>"
              + "</html>");
      descPane.setEditable(false);
      descPane.setOpaque(false);
      descPane.setBorder(null);

      JScrollPane descScroll = new JScrollPane(descPane);
      descScroll.setBorder(null);
      descScroll.getViewport().setOpaque(false);
      descScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      descScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

      JPanel panel = new JPanel(new MigLayout("insets 12 10 6 10, wrap 1, fillx", "[fill, 380]"));
      panel.add(descScroll, "gapbottom 8, h ::100"); // cap at 100 px; scrolls when exceeded
      panel.add(captureField, "h 46!");

      ActionListener al = _ -> captureField.requestFocusInWindow();
      Timer timer = new Timer(250, al);
      timer.setRepeats(false);
      timer.start();
      int result =
          JOptionPane.showConfirmDialog(
              ShortcutPrefView.this,
              panel,
              Messages.getString("edit.shortcut"),
              JOptionPane.OK_CANCEL_OPTION,
              JOptionPane.PLAIN_MESSAGE);
      confirmed = result == JOptionPane.OK_OPTION && capturedKeyCode != 0;
    }

    boolean isConfirmed() {
      return confirmed;
    }

    int getCapturedKeyCode() {
      return capturedKeyCode;
    }

    int getCapturedModifier() {
      return capturedModifier;
    }
  }
}
