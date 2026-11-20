/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.pref.hp;

import java.awt.Component;
import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.hp.HangingProtocolDocuments;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.api.util.LayeredEntries.Origin;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.hp.HangingEngine;
import org.weasis.dicom.codec.hp.ProtocolExchange;
import org.weasis.dicom.codec.hp.ProtocolJson;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.hp.HpSettings;
import org.weasis.dicom.explorer.hp.ProtocolEntries;
import org.weasis.dicom.explorer.hp.ProtocolEntries.Row;

/**
 * The hanging protocols page: how a study opens, what happens to kept sessions, the retention, and
 * the protocols of every layer with their enabled flag, to duplicate into the user layer, delete,
 * import or export. The same actions as the {@code hp:} and {@code config:} verbs.
 */
public class HangingProtocolPrefView extends AbstractItemDialogPage {

  /** The title and the position of the page in the preference tree, known before it is built. */
  public static final String PAGE_TITLE = Messages.getString("HangingProtocolPrefView.title");

  public static final int PAGE_POSITION = 608;

  private static final Logger LOGGER = LoggerFactory.getLogger(HangingProtocolPrefView.class);

  private final JComboBox<HpSettings.Opening> opening =
      new JComboBox<>(HpSettings.Opening.values());
  private final JComboBox<HpSettings.Restore> restore =
      new JComboBox<>(HpSettings.Restore.values());
  private final JCheckBox keepOnClose =
      new JCheckBox(Messages.getString("HangingProtocolPrefView.keep"));
  private final JCheckBox remoteNotice =
      new JCheckBox(Messages.getString("HangingProtocolPrefView.remote_notice"));
  private final JSpinner sessionsMax = new JSpinner(new SpinnerNumberModel(100, 0, 10000, 1));
  private final JSpinner sessionsDays = new JSpinner(new SpinnerNumberModel(90, 0, 3650, 1));
  private final ProtocolTable table = new ProtocolTable();

  public HangingProtocolPrefView() {
    super(PAGE_TITLE, PAGE_POSITION);
    opening.setRenderer(named());
    restore.setRenderer(named());
    initGUI();
  }

  private void initGUI() {
    // One column: every section takes the width of the page and its explanations wrap to it
    setLayout(new MigLayout("ins 0, fillx, wrap 1", "[grow,fill]")); // NON-NLS

    JPanel open = section("HangingProtocolPrefView.behaviour"); // NON-NLS
    open.add(label("HangingProtocolPrefView.opening")); // NON-NLS
    open.add(opening, "wrap"); // NON-NLS
    open.add(hint("HangingProtocolPrefView.opening.hint"), HINT); // NON-NLS
    add(open);

    JPanel protocols = section("HangingProtocolPrefView.protocols"); // NON-NLS
    protocols.add(hint("HangingProtocolPrefView.protocols.hint"), HINT); // NON-NLS
    JTable view = new JTable(table);
    view.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    view.setAutoCreateRowSorter(true);
    JScrollPane scroll = new JScrollPane(view);
    scroll.setPreferredSize(
        new Dimension(GuiUtils.getScaleLength(520), GuiUtils.getScaleLength(170)));
    protocols.add(scroll, "span, growx, wmin 0, wrap"); // NON-NLS
    JButton duplicate = new JButton(Messages.getString("HangingProtocolPrefView.duplicate"));
    duplicate.addActionListener(e -> selected(view).ifPresent(this::duplicate));
    JButton delete = new JButton(Messages.common("delete"));
    delete.addActionListener(e -> selected(view).ifPresent(this::delete));
    JButton importButton = new JButton(Messages.getString("HangingProtocolPrefView.import"));
    importButton.addActionListener(e -> importProtocols());
    JButton export = new JButton(Messages.getString("HangingProtocolPrefView.export"));
    export.addActionListener(e -> selected(view).ifPresent(this::export));
    protocols.add(
        GuiUtils.getFlowLayoutPanel(
            0, 0, duplicate, gap(), delete, gap(), importButton, gap(), export),
        "span"); // NON-NLS
    add(protocols);

    JPanel sessions = section("HangingProtocolPrefView.sessions"); // NON-NLS
    sessions.add(hint("HangingProtocolPrefView.sessions.hint"), HINT); // NON-NLS
    sessions.add(label("HangingProtocolPrefView.restore"), "gaptop rel"); // NON-NLS
    sessions.add(restore, "wrap"); // NON-NLS
    sessions.add(hint("HangingProtocolPrefView.restore.hint"), HINT); // NON-NLS
    sessions.add(keepOnClose, "span, wrap, gaptop rel"); // NON-NLS
    sessions.add(label("HangingProtocolPrefView.sessions_max"), "gaptop rel"); // NON-NLS
    sessions.add(
        GuiUtils.getFlowLayoutPanel(
            0,
            0,
            sessionsMax,
            GuiUtils.boxHorizontalStrut(15),
            label("HangingProtocolPrefView.sessions_days"), // NON-NLS
            GuiUtils.boxHorizontalStrut(5),
            sessionsDays),
        "wrap"); // NON-NLS
    sessions.add(hint("HangingProtocolPrefView.retention.hint"), HINT); // NON-NLS
    add(sessions);

    JPanel remote = section("HangingProtocolPrefView.remote"); // NON-NLS
    remote.add(remoteNotice, "span, wrap"); // NON-NLS
    remote.add(hint("HangingProtocolPrefView.remote.hint"), HINT); // NON-NLS
    add(remote);

    load();
    getProperties().setProperty(PreferenceDialog.KEY_SHOW_APPLY, Boolean.TRUE.toString());
    getProperties().setProperty(PreferenceDialog.KEY_SHOW_RESTORE, Boolean.TRUE.toString());
    getProperties()
        .setProperty(
            PreferenceDialog.KEY_HELP, "read-studies/open-browse/hanging-protocols/"); // NON-NLS
  }

  /** The constraints of an explanation: the whole row, wrapped to the width of the section. */
  private static final String HINT = "span, growx, wmin 0, wrap"; // NON-NLS

  private static JPanel section(String titleKey) {
    JPanel panel = new JPanel(new MigLayout("ins 2 6 6 6, fillx", "[][grow]")); // NON-NLS
    panel.setBorder(GuiUtils.getTitledBorder(Messages.getString(titleKey)));
    return panel;
  }

  private static JLabel label(String key) {
    return new JLabel(Messages.getString(key) + StringUtil.COLON);
  }

  private static JComponent gap() {
    return GuiUtils.boxHorizontalStrut(8);
  }

  /** What an item does, in small text that wraps to the width it is given. */
  private static JTextArea hint(String key) {
    JTextArea area = new JTextArea(Messages.getString(key));
    area.setEditable(false);
    area.setFocusable(false);
    area.setOpaque(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setBorder(null);
    area.setFont(UIManager.getFont("small.font")); // NON-NLS
    return area;
  }

  private void load() {
    opening.setSelectedItem(Settings.get(HpSettings.OPENING));
    restore.setSelectedItem(Settings.get(HpSettings.RESTORE));
    keepOnClose.setSelected(Settings.get(HpSettings.KEEP_ON_CLOSE));
    remoteNotice.setSelected(Settings.get(HpSettings.REMOTE_NOTICE));
    sessionsMax.setValue(Settings.get(HpSettings.SESSIONS_MAX));
    sessionsDays.setValue(Settings.get(HpSettings.SESSIONS_MAX_DAYS));
    table.reload();
  }

  private static java.util.Optional<Row> selected(JTable view) {
    int row = view.getSelectedRow();
    if (row < 0) {
      return java.util.Optional.empty();
    }
    ProtocolTable model = (ProtocolTable) view.getModel();
    return java.util.Optional.of(model.rows.get(view.convertRowIndexToModel(row)));
  }

  private void duplicate(Row row) {
    try {
      ProtocolEntries.duplicate(row.id());
      table.reload();
    } catch (IOException e) {
      report(e);
    }
  }

  private void delete(Row row) {
    if (row.origin() != Origin.USER) {
      JOptionPane.showMessageDialog(
          this,
          Messages.getString("HangingProtocolPrefView.not_user"),
          getTitle(),
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    try {
      ProtocolEntries.delete(row.id());
      table.reload();
    } catch (IOException e) {
      report(e);
    }
  }

  private void importProtocols() {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle(Messages.getString("HangingProtocolPrefView.import"));
    if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    Path file = chooser.getSelectedFile().toPath();
    try {
      for (ProtocolExchange.Imported imported : ProtocolExchange.read(file)) {
        HangingProtocolDocuments.protocols().put(ProtocolJson.toJson(imported.document()));
        if (!imported.warnings().isEmpty()) {
          LOGGER.info(
              "Imported {} with warnings {}", imported.document().id(), imported.warnings());
        }
      }
      table.reload();
    } catch (IOException | RuntimeException e) {
      report(e);
    }
  }

  private void export(Row row) {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle(Messages.getString("HangingProtocolPrefView.export"));
    chooser.setSelectedFile(new java.io.File(row.id() + ".json")); // NON-NLS
    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    Path file = chooser.getSelectedFile().toPath();
    try {
      ProtocolJson.Document document =
          HangingEngine.candidates().stream()
              .map(HangingEngine.Candidate::document)
              .filter(d -> d.id().equals(row.id()))
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("Unknown protocol: " + row.id()));
      if (file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".dcm")) {
        ProtocolExchange.writeDicom(document, file, true);
      } else {
        ProtocolExchange.writeJson(document, file, true);
      }
    } catch (IOException | RuntimeException e) {
      report(e);
    }
  }

  private void report(Exception e) {
    LOGGER.warn("Protocol page action failed", e);
    JOptionPane.showMessageDialog(this, e.getMessage(), getTitle(), JOptionPane.ERROR_MESSAGE);
  }

  @Override
  public void resetToDefaultValues() {
    Settings.reset(HpSettings.OPENING);
    Settings.reset(HpSettings.RESTORE);
    Settings.reset(HpSettings.KEEP_ON_CLOSE);
    Settings.reset(HpSettings.REMOTE_NOTICE);
    Settings.reset(HpSettings.SESSIONS_MAX);
    Settings.reset(HpSettings.SESSIONS_MAX_DAYS);
    load();
  }

  @Override
  public void closeAdditionalWindow() {
    Settings.set(HpSettings.OPENING, (HpSettings.Opening) opening.getSelectedItem());
    Settings.set(HpSettings.RESTORE, (HpSettings.Restore) restore.getSelectedItem());
    Settings.set(HpSettings.KEEP_ON_CLOSE, keepOnClose.isSelected());
    Settings.set(HpSettings.REMOTE_NOTICE, remoteNotice.isSelected());
    Settings.set(HpSettings.SESSIONS_MAX, (Integer) sessionsMax.getValue());
    Settings.set(HpSettings.SESSIONS_MAX_DAYS, (Integer) sessionsDays.getValue());
  }

  /** The protocols of the layers; the enabled column writes through to the user layer. */
  static final class ProtocolTable extends AbstractTableModel {
    private final String[] columns = {
      Messages.getString("HangingProtocolPrefView.col_id"),
      Messages.common("name"),
      Messages.getString("HangingProtocolPrefView.col_layer"),
      Messages.getString("HangingProtocolPrefView.col_enabled")
    };
    private List<Row> rows = List.of();

    void reload() {
      rows = ProtocolEntries.rows();
      fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
      return rows.size();
    }

    @Override
    public int getColumnCount() {
      return columns.length;
    }

    @Override
    public String getColumnName(int column) {
      return columns[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
      return column == 3 ? Boolean.class : String.class;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return column == 3 && !rows.get(row).locked();
    }

    @Override
    public Object getValueAt(int row, int column) {
      Row r = rows.get(row);
      return switch (column) {
        case 0 -> r.id();
        case 1 -> r.name();
        case 2 -> r.layer();
        default -> r.enabled();
      };
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
      if (column == 3 && value instanceof Boolean enabled) {
        try {
          ProtocolEntries.setEnabled(rows.get(row).id(), enabled);
        } catch (IOException e) {
          LOGGER.warn("Cannot store the protocol flag", e);
        }
        reload();
      }
    }
  }

  // The values of a setting are shown by their name in the language of the interface
  private static DefaultListCellRenderer named() {
    return new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(
          JList<?> list, Object value, int index, boolean selected, boolean focus) {
        super.getListCellRendererComponent(list, value, index, selected, focus);
        if (value instanceof HpSettings.Opening o) {
          setText(
              Messages.getString(
                  o == HpSettings.Opening.PROTOCOL
                      ? "HangingProtocolPrefView.opening.protocol"
                      : "HangingProtocolPrefView.opening.first_series"));
        } else if (value instanceof HpSettings.Restore r) {
          setText(
              Messages.getString(
                  switch (r) {
                    case AUTO -> "HangingProtocolPrefView.restore.auto";
                    case OFFER -> "HangingProtocolPrefView.restore.offer";
                    case NEVER -> "HangingProtocolPrefView.restore.never";
                  }));
        }
        return this;
      }
    };
  }
}
