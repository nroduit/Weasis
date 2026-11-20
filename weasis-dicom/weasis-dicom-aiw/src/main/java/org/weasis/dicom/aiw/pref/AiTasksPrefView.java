/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.aiw.pref;

import java.awt.Dimension;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;
import net.miginfocom.swing.MigLayout;
import org.dcm4che3.data.Code;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WrapFlowLayout;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.aiw.Messages;
import org.weasis.dicom.aiw.UpsNodes;
import org.weasis.dicom.aiw.UpsNodes.Node;
import org.weasis.dicom.explorer.DicomWebOrigin;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.UsageType;
import org.weasis.dicom.explorer.pref.node.DicomWebNode;

/**
 * The AI tasks page: the DICOMweb servers that take tasks (the document {@code upsNodes.json} of
 * the user), whether tasks may be created there, and the tasks each one offers. A server is a
 * DICOMweb node of the preferences or a URL; the page writes the document when it is applied.
 */
public class AiTasksPrefView extends AbstractItemDialogPage {

  /** The title and the position of the page in the preference tree, known before it is built. */
  public static final String PAGE_TITLE = Messages.getString("AiTasksPrefView.title");

  public static final int PAGE_POSITION = 609;

  private static final Logger LOGGER = LoggerFactory.getLogger(AiTasksPrefView.class);

  /** A server being edited. */
  private static final class Server {
    String id;
    String url;
    String label;
    boolean enabled = true;
    boolean writeAllowed;
    String requesterAet = "WEASIS"; // NON-NLS
    boolean requesterInPath;
    int pollSeconds = 30;
    final List<String[]> catalog = new ArrayList<>();

    static Server of(Node node) {
      Server s = new Server();
      s.id = node.id();
      s.url = node.url();
      s.enabled = node.enabled();
      s.writeAllowed = node.writeAllowed();
      s.requesterAet = node.requesterAet();
      s.requesterInPath = node.requesterInPath();
      s.pollSeconds = node.pollSeconds();
      for (Code code : node.catalog()) {
        s.catalog.add(
            new String[] {
              code.getCodeValue(), code.getCodingSchemeDesignator(), code.getCodeMeaning()
            });
      }
      return s;
    }

    Node toNode() {
      List<Code> codes = new ArrayList<>();
      for (String[] row : catalog) {
        if (StringUtil.hasText(row[0]) && StringUtil.hasText(row[1])) {
          codes.add(
              new Code(
                  row[0].trim(),
                  row[1].trim(),
                  null,
                  StringUtil.hasText(row[2]) ? row[2].trim() : row[0].trim()));
        }
      }
      return new Node(
          id, url, enabled, writeAllowed, requesterAet, requesterInPath, pollSeconds, codes);
    }
  }

  private final List<Server> servers = new ArrayList<>();
  private final ServerTable serverModel = new ServerTable();
  private final CatalogTable catalogModel = new CatalogTable();
  private final JTable serverTable = new JTable(serverModel);
  private final JTable catalogTable = new JTable(catalogModel);
  private final JTextField requesterAet = new JTextField(12);
  private final JCheckBox requesterInPath =
      new JCheckBox(Messages.getString("AiTasksPrefView.aet_in_path"));
  private final JSpinner pollSeconds = new JSpinner(new SpinnerNumberModel(30, 0, 3600, 5));
  private final JPanel detail = section("AiTasksPrefView.server"); // NON-NLS
  // the settings of the selected server, and what is said in their place when none is selected
  private final JPanel form = new JPanel(new MigLayout("ins 0, fillx, wrap 2", "[][grow]"));
  private final JTextArea noServer = hint("AiTasksPrefView.no_server"); // NON-NLS
  private Server shown;

  public AiTasksPrefView() {
    super(PAGE_TITLE, PAGE_POSITION);
    initGUI();
    load();
  }

  private void initGUI() {
    setLayout(new MigLayout("ins 0, fillx, wrap 1", "[grow,fill]")); // NON-NLS

    JPanel list = section("AiTasksPrefView.servers"); // NON-NLS
    list.add(hint("AiTasksPrefView.servers.hint"), HINT); // NON-NLS
    serverTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    serverTable.getSelectionModel().addListSelectionListener(_ -> showSelected());
    // the check box columns stay narrow: the width goes to the name of the server
    for (int column = 1; column <= 2; column++) {
      serverTable.getColumnModel().getColumn(column).setMaxWidth(GuiUtils.getScaleLength(150));
      serverTable
          .getColumnModel()
          .getColumn(column)
          .setPreferredWidth(GuiUtils.getScaleLength(120));
    }
    list.add(scroll(serverTable, 4), TABLE);
    JButton addNode = new JButton(Messages.getString("AiTasksPrefView.add_node"));
    addNode.addActionListener(_ -> chooseNode(addNode));
    JButton addUrl = new JButton(Messages.getString("AiTasksPrefView.add_url"));
    addUrl.addActionListener(_ -> askUrl());
    JButton remove = new JButton(Messages.getString("AiTasksPrefView.remove"));
    remove.addActionListener(_ -> removeSelected());
    list.add(buttons(addNode, addUrl, remove), "span, growx, wmin 0"); // NON-NLS
    add(list, "wmin 0"); // NON-NLS

    form.add(new JLabel(Messages.getString("AiTasksPrefView.catalog") + StringUtil.COLON), "span");
    catalogTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    catalogTable.setDefaultEditor(Object.class, new DefaultCellEditor(new JTextField()));
    catalogTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE); // NON-NLS
    form.add(scroll(catalogTable, 4), TABLE);
    JButton addTask = new JButton(Messages.getString("AiTasksPrefView.add_task"));
    addTask.addActionListener(_ -> addTask());
    JButton removeTask = new JButton(Messages.getString("AiTasksPrefView.remove"));
    removeTask.addActionListener(_ -> removeTask());
    form.add(buttons(addTask, removeTask), "span, growx, wmin 0"); // NON-NLS
    form.add(hint("AiTasksPrefView.catalog.hint"), HINT); // NON-NLS

    form.add(
        new JLabel(Messages.getString("AiTasksPrefView.aet") + StringUtil.COLON),
        "gaptop unrel"); // NON-NLS
    form.add(requesterAet, "w 140lp!, gaptop unrel"); // NON-NLS
    form.add(requesterInPath, "skip 1, wmin 0"); // NON-NLS
    form.add(new JLabel(Messages.getString("AiTasksPrefView.poll") + StringUtil.COLON));
    form.add(pollSeconds, "split 2"); // NON-NLS
    form.add(new JLabel(Messages.getString("AiTasksPrefView.seconds")));
    form.add(hint("AiTasksPrefView.poll.hint"), HINT); // NON-NLS

    detail.add(noServer, "span, growx, wmin 0, hidemode 3"); // NON-NLS
    detail.add(form, "span, growx, wmin 0, hidemode 3"); // NON-NLS
    add(detail, "wmin 0"); // NON-NLS

    getProperties().setProperty(PreferenceDialog.KEY_SHOW_APPLY, Boolean.TRUE.toString());
    getProperties().setProperty(PreferenceDialog.KEY_SHOW_RESTORE, Boolean.TRUE.toString());
  }

  /** The constraints of an explanation: the whole row, wrapped to the width of the section. */
  private static final String HINT = "span, growx, wmin 0, wrap"; // NON-NLS

  /** The constraints of a table: the whole row, as wide as the page and never wider. */
  private static final String TABLE = "span, growx, wmin 0, wrap"; // NON-NLS

  /**
   * A table in a scroll pane as tall as a few rows; its preferred width is small so that the page
   * decides the width, not the columns.
   */
  private static JScrollPane scroll(JTable table, int rows) {
    table.setFillsViewportHeight(true);
    JScrollPane pane = new JScrollPane(table);
    int height = table.getRowHeight() * (rows + 1) + GuiUtils.getScaleLength(6);
    pane.setPreferredSize(new Dimension(GuiUtils.getScaleLength(200), height));
    return pane;
  }

  /** Buttons that go to the next line when the page is narrow. */
  private static JPanel buttons(JButton... items) {
    JPanel panel = new JPanel(new WrapFlowLayout(GuiUtils.getScaleLength(6), 3));
    for (JButton item : items) {
      panel.add(item);
    }
    return panel;
  }

  private static JPanel section(String titleKey) {
    JPanel panel = new JPanel(new MigLayout("ins 2 6 6 6, fillx", "[][grow]")); // NON-NLS
    panel.setBorder(GuiUtils.getTitledBorder(Messages.getString(titleKey)));
    return panel;
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

  private static List<DicomWebNode> webNodes() {
    List<DicomWebNode> nodes = new ArrayList<>();
    try {
      for (AbstractDicomNode node :
          AbstractDicomNode.loadDicomNodes(AbstractDicomNode.Type.WEB, UsageType.BOTH)) {
        if (node instanceof DicomWebNode web && StringUtil.hasText(web.getId())) {
          nodes.add(web);
        }
      }
    } catch (RuntimeException e) {
      LOGGER.warn("Cannot read the DICOMweb nodes", e);
    }
    return nodes;
  }

  private void load() {
    storeShown();
    shown = null;
    servers.clear();
    List<DicomWebNode> nodes = webNodes();
    for (Node node : UpsNodes.all()) {
      Server server = Server.of(node);
      server.label = label(server, nodes);
      servers.add(server);
    }
    serverModel.fireTableDataChanged();
    if (!servers.isEmpty()) {
      serverTable.setRowSelectionInterval(0, 0);
    }
    showSelected();
  }

  private static String label(Server server, List<DicomWebNode> nodes) {
    for (DicomWebNode node : nodes) {
      if (node.getId().equals(server.id)) {
        return node.getDescription() + " (" + node.getUrl() + ")";
      }
    }
    return server.url == null ? server.id : server.url;
  }

  /** Offers the DICOMweb nodes of the preferences that are not listed yet. */
  private void chooseNode(JButton invoker) {
    JPopupMenu menu = new JPopupMenu();
    for (DicomWebNode node : webNodes()) {
      if (servers.stream().anyMatch(s -> node.getId().equals(s.id))) {
        continue;
      }
      JMenuItem item = new JMenuItem(node.getDescription() + " (" + node.getUrl() + ")");
      item.addActionListener(
          _ -> {
            Server server = new Server();
            server.id = node.getId();
            server.label = item.getText();
            add(server);
          });
      menu.add(item);
    }
    if (menu.getComponentCount() == 0) {
      JMenuItem none = new JMenuItem(Messages.getString("AiTasksPrefView.no_node"));
      none.setEnabled(false);
      menu.add(none);
    }
    menu.show(invoker, 0, invoker.getHeight());
  }

  private void askUrl() {
    String url =
        JOptionPane.showInputDialog(
            this,
            Messages.getString("AiTasksPrefView.url_prompt"),
            getTitle(),
            JOptionPane.PLAIN_MESSAGE);
    url = DicomWebOrigin.normalize(url);
    if (!StringUtil.hasText(url)) {
      return;
    }
    String address = url;
    if (servers.stream().anyMatch(s -> address.equals(s.url) || address.equals(s.id))) {
      return;
    }
    Server server = new Server();
    server.id = address;
    server.url = address;
    server.label = address;
    add(server);
  }

  private void add(Server server) {
    servers.add(server);
    serverModel.fireTableDataChanged();
    int row = servers.size() - 1;
    serverTable.setRowSelectionInterval(row, row);
  }

  private void removeSelected() {
    int row = serverTable.getSelectedRow();
    if (row >= 0) {
      shown = null;
      servers.remove(row);
      serverModel.fireTableDataChanged();
      showSelected();
    }
  }

  private void addTask() {
    if (shown != null) {
      stopEditing();
      shown.catalog.add(new String[] {"", "", ""});
      catalogModel.fireTableDataChanged();
      int row = shown.catalog.size() - 1;
      catalogTable.setRowSelectionInterval(row, row);
      catalogTable.editCellAt(row, 0);
      catalogTable.requestFocusInWindow();
    }
  }

  private void removeTask() {
    int row = catalogTable.getSelectedRow();
    if (shown != null && row >= 0) {
      stopEditing();
      shown.catalog.remove(row);
      catalogModel.fireTableDataChanged();
    }
  }

  private void stopEditing() {
    if (catalogTable.isEditing()) {
      catalogTable.getCellEditor().stopCellEditing();
    }
  }

  /** Keeps what the fields say for the server shown, before another one takes its place. */
  private void storeShown() {
    stopEditing();
    if (shown != null) {
      shown.requesterAet = requesterAet.getText().trim();
      shown.requesterInPath = requesterInPath.isSelected();
      shown.pollSeconds = (Integer) pollSeconds.getValue();
    }
  }

  private void showSelected() {
    storeShown();
    int row = serverTable.getSelectedRow();
    shown = row >= 0 && row < servers.size() ? servers.get(row) : null;
    boolean has = shown != null;
    form.setVisible(has);
    noServer.setVisible(!has);
    // the section is named after the server whose settings it shows
    detail.setBorder(
        GuiUtils.getTitledBorder(
            has
                ? Messages.format("AiTasksPrefView.server_of", shown.label)
                : Messages.getString("AiTasksPrefView.server")));
    requesterAet.setText(has ? shown.requesterAet : "");
    requesterInPath.setSelected(has && shown.requesterInPath);
    pollSeconds.setValue(has ? shown.pollSeconds : 30);
    catalogModel.fireTableDataChanged();
  }

  @Override
  public void resetToDefaultValues() {
    shown = null;
    servers.clear();
    serverModel.fireTableDataChanged();
    showSelected();
  }

  @Override
  public void closeAdditionalWindow() {
    storeShown();
    try {
      UpsNodes.save(servers.stream().map(Server::toNode).toList());
    } catch (IOException | RuntimeException e) {
      LOGGER.warn("Cannot store {}", UpsNodes.DOCUMENT, e);
      JOptionPane.showMessageDialog(this, e.getMessage(), getTitle(), JOptionPane.ERROR_MESSAGE);
    }
  }

  /** The servers: where, whether it is used, whether tasks may be created there. */
  private final class ServerTable extends AbstractTableModel {
    private final String[] columns = {
      Messages.getString("AiTasksPrefView.col_server"),
      Messages.getString("AiTasksPrefView.col_enabled"),
      Messages.getString("AiTasksPrefView.col_write")
    };

    @Override
    public int getRowCount() {
      return servers.size();
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
      return column == 0 ? String.class : Boolean.class;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return column > 0;
    }

    @Override
    public Object getValueAt(int row, int column) {
      Server s = servers.get(row);
      return switch (column) {
        case 0 -> s.label;
        case 1 -> s.enabled;
        default -> s.writeAllowed;
      };
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
      if (value instanceof Boolean flag) {
        Server s = servers.get(row);
        if (column == 1) {
          s.enabled = flag;
        } else {
          s.writeAllowed = flag;
        }
      }
    }
  }

  /** The tasks of the server shown: code value, coding scheme designator, meaning. */
  private final class CatalogTable extends AbstractTableModel {
    private final String[] columns = {
      Messages.getString("AiTasksPrefView.col_code"),
      Messages.getString("AiTasksPrefView.col_scheme"),
      Messages.getString("AiTasksPrefView.col_meaning")
    };

    @Override
    public int getRowCount() {
      return shown == null ? 0 : shown.catalog.size();
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
    public boolean isCellEditable(int row, int column) {
      return true;
    }

    @Override
    public Object getValueAt(int row, int column) {
      return shown.catalog.get(row)[column];
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
      if (shown != null && row < shown.catalog.size()) {
        shown.catalog.get(row)[column] = value == null ? "" : value.toString();
      }
    }
  }
}
