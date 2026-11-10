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

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import org.pf4j.PluginDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.util.StringUtil;
import org.weasis.launcher.integrity.Integrity;
import org.weasis.plugin.WeasisPluginManager;
import org.weasis.plugin.manager.LoadedPlugin;
import org.weasis.plugin.manager.LoadedPlugins;
import org.weasis.plugin.manager.PluginGate;
import org.weasis.plugin.manager.PluginPolicy;
import org.weasis.plugin.manager.PluginSignature;
import org.weasis.plugin.manager.UserPluginRegistry;

/**
 * The <i>Plugins</i> page: what is loaded, from where, under which policy; the user's own plugins
 * added, removed, enabled or disabled within the site policy, effective at the next start. Under a
 * policy that forbids the user source, or a locked set, the page is read-only and says why.
 */
public class PluginsPrefView extends AbstractItemDialogPage {

  private static final Logger LOGGER = LoggerFactory.getLogger(PluginsPrefView.class);

  public static final String PAGE_NAME = Messages.getString("PluginsPrefView.title");

  private final PluginTable table = new PluginTable();
  private final JLabel policyLabel = new JLabel();
  private final JLabel noticeLabel = new JLabel();
  private final JButton add = new JButton(Messages.getString("PluginsPrefView.add"));
  private final JButton remove = new JButton(Messages.common("remove"));
  private final JButton toggle = new JButton(Messages.getString("PluginsPrefView.toggle"));

  public PluginsPrefView() {
    super(PAGE_NAME, 130);
    setBorder(GuiUtils.getEmptyBorder(15, 10, 10, 10));
    JTable view = new JTable(table);
    view.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    view.setAutoCreateRowSorter(true);
    view.setDefaultEditor(Object.class, null);
    JScrollPane scroll = new JScrollPane(view);
    scroll.setPreferredSize(
        new Dimension(GuiUtils.getScaleLength(640), GuiUtils.getScaleLength(260)));
    add(policyLabel);
    add(GuiUtils.boxVerticalStrut(5));
    add(scroll);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 5, 5));
    buttons.add(add);
    buttons.add(toggle);
    buttons.add(remove);
    add(buttons);
    add(noticeLabel);
    add(GuiUtils.boxYLastElement(LAST_FILLER_HEIGHT));
    add.addActionListener(e -> importPlugin());
    remove.addActionListener(e -> selected(view).ifPresent(this::removePlugin));
    toggle.addActionListener(e -> selected(view).ifPresent(this::togglePlugin));
    view.getSelectionModel().addListSelectionListener(e -> updateButtons(selected(view)));
    load();
    getProperties().setProperty(PreferenceDialog.KEY_HELP, "plugins"); // NON-NLS
  }

  private static WeasisPluginManager manager() {
    return GuiUtils.getUICore().getPluginManager();
  }

  private static Optional<PluginGate> gate() {
    WeasisPluginManager manager = manager();
    return manager == null ? Optional.empty() : Optional.ofNullable(manager.getGate());
  }

  /** Whether the user may change the set here: a policy above core-only and no lock. */
  static boolean editable(Optional<PluginGate> gate) {
    return gate.map(
            g ->
                g.registry() != null
                    && !g.policy().lock()
                    && g.policy().policy() != PluginPolicy.Policy.OFF
                    && g.policy().policy() != PluginPolicy.Policy.CORE_ONLY)
        .orElse(false);
  }

  private void load() {
    LoadedPlugins manifest = LoadedPlugins.current();
    Optional<PluginGate> gate = gate();
    String policy = manifest.policy() == null ? "?" : manifest.policy().key();
    policyLabel.setText(
        Messages.format(
            "PluginsPrefView.policy",
            policy,
            manifest.locked()
                ? Messages.getString("PluginsPrefView.locked")
                : Messages.getString("PluginsPrefView.unlocked"),
            manifest.fingerprint().substring(0, 16)));
    table.reload(manifest, gate);
    boolean editable = editable(gate);
    add.setEnabled(editable);
    noticeLabel.setText(
        editable
            ? Messages.getString("PluginsPrefView.restart")
            : Messages.getString("PluginsPrefView.readonly"));
    updateButtons(Optional.empty());
  }

  private void updateButtons(Optional<Row> row) {
    boolean user = row.map(r -> LoadedPlugin.USER.equals(r.plugin().source())).orElse(false);
    boolean editable = editable(gate());
    remove.setEnabled(editable && user);
    toggle.setEnabled(editable && user);
    toggle.setText(
        row.map(r -> LoadedPlugin.DISABLED.equals(r.plugin().state())).orElse(false)
            ? Messages.common("enable")
            : Messages.getString("PluginsPrefView.disable"));
  }

  private static Optional<Row> selected(JTable view) {
    int row = view.getSelectedRow();
    if (row < 0) {
      return Optional.empty();
    }
    PluginTable model = (PluginTable) view.getModel();
    return Optional.of(model.rows.get(view.convertRowIndexToModel(row)));
  }

  private void importPlugin() {
    Optional<PluginGate> gate = gate();
    if (!editable(gate)) {
      return;
    }
    JFileChooser chooser = new JFileChooser();
    chooser.setFileFilter(new FileNameExtensionFilter("Plugin (*.jar)", "jar")); // NON-NLS
    if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    Path jar = chooser.getSelectedFile().toPath();
    try {
      PluginDescriptor descriptor = manager().describe(jar);
      if (descriptor == null || descriptor.getPluginId() == null) {
        throw new IOException(Messages.getString("PluginsPrefView.no_descriptor"));
      }
      UserPluginRegistry registry = gate.get().registry().load();
      Path target = registry.dir().resolve(jar.getFileName());
      String digest = Integrity.sha256(jar);
      Optional<Integrity.Key> signer =
          PluginSignature.read(jar).flatMap(s -> s.verify(gate.get().policy().siteKeys()));
      PluginPolicy.Tier tier =
          signer.isPresent() ? PluginPolicy.Tier.SITE : PluginPolicy.Tier.UNVERIFIED;
      List<String> capabilities = PluginGate.capabilitiesOf(descriptor);
      String summary =
          Messages.format(
              "PluginsPrefView.confirm",
              descriptor.getPluginId(),
              descriptor.getVersion(),
              descriptor.getProvider() == null ? "?" : descriptor.getProvider(),
              tier.key(),
              capabilities.isEmpty() ? "-" : String.join(", ", capabilities),
              digest.substring(0, 16));
      int choice =
          JOptionPane.showConfirmDialog(
              this,
              summary,
              Messages.getString("PluginsPrefView.add"),
              JOptionPane.OK_CANCEL_OPTION,
              tier == PluginPolicy.Tier.UNVERIFIED
                  ? JOptionPane.WARNING_MESSAGE
                  : JOptionPane.QUESTION_MESSAGE);
      if (choice != JOptionPane.OK_OPTION) {
        return;
      }
      Files.createDirectories(registry.dir());
      Files.copy(jar, target, StandardCopyOption.REPLACE_EXISTING);
      Path sidecar = jar.resolveSibling(jar.getFileName() + PluginSignature.SIDECAR_SUFFIX);
      if (Files.isRegularFile(sidecar)) {
        Files.copy(
            sidecar,
            target.resolveSibling(target.getFileName() + PluginSignature.SIDECAR_SUFFIX),
            StandardCopyOption.REPLACE_EXISTING);
      }
      registry.put(
          new UserPluginRegistry.Entry(
              target.getFileName().toString(),
              descriptor.getPluginId(),
              descriptor.getVersion(),
              digest,
              signer.map(Integrity.Key::id).orElse(null),
              tier.key(),
              Instant.now().toString(),
              AppProperties.WEASIS_USER,
              true));
      PluginGate.Decision decision = gate.get().decide(target, descriptor);
      if (!decision.load()) {
        registry.remove(descriptor.getPluginId());
        Files.deleteIfExists(target);
        JOptionPane.showMessageDialog(
            this,
            Messages.format("PluginsPrefView.refused", decision.plugin().reason()),
            Messages.getString("PluginsPrefView.add"),
            JOptionPane.WARNING_MESSAGE);
        return;
      }
      registry.store();
      table.addImported(decision.plugin().withState("imported", null)); // NON-NLS
    } catch (IOException | RuntimeException e) {
      LOGGER.warn("Cannot import {}", jar, e);
      JOptionPane.showMessageDialog(
          this,
          e.getMessage(),
          Messages.getString("PluginsPrefView.add"),
          JOptionPane.ERROR_MESSAGE);
    }
  }

  private void removePlugin(Row row) {
    Optional<PluginGate> gate = gate();
    if (!editable(gate)) {
      return;
    }
    try {
      UserPluginRegistry registry = gate.get().registry().load();
      Optional<UserPluginRegistry.Entry> entry = registry.remove(row.plugin().id());
      if (entry.isPresent()) {
        Path jar = registry.dir().resolve(entry.get().file());
        Files.deleteIfExists(jar);
        Files.deleteIfExists(
            jar.resolveSibling(entry.get().file() + PluginSignature.SIDECAR_SUFFIX));
        registry.store();
      }
      table.replace(row, row.plugin().withState("removed", null)); // NON-NLS
    } catch (IOException e) {
      LOGGER.warn("Cannot remove {}", row.plugin().id(), e);
    }
  }

  private void togglePlugin(Row row) {
    Optional<PluginGate> gate = gate();
    if (!editable(gate)) {
      return;
    }
    try {
      UserPluginRegistry registry = gate.get().registry().load();
      Optional<UserPluginRegistry.Entry> entry = registry.byId(row.plugin().id());
      if (entry.isEmpty()) {
        return;
      }
      boolean on = !entry.get().enabled();
      registry.put(entry.get().withEnabled(on));
      registry.store();
      table.replace(
          row,
          row.plugin()
              .withState(
                  on ? LoadedPlugin.STARTED : LoadedPlugin.DISABLED,
                  on ? null : "disabled by the user"));
    } catch (IOException e) {
      LOGGER.warn("Cannot change {}", row.plugin().id(), e);
    }
  }

  @Override
  public void resetToDefaultValues() {
    load();
  }

  @Override
  public void closeAdditionalWindow() {
    // every change is written when it is made
  }

  record Row(LoadedPlugin plugin) {}

  static final class PluginTable extends AbstractTableModel {
    private final String[] columns = {
      Messages.getString("PluginsPrefView.col_id"),
      Messages.getString("PluginsPrefView.col_version"),
      Messages.getString("PluginsPrefView.col_source"),
      Messages.getString("PluginsPrefView.col_tier"),
      Messages.getString("PluginsPrefView.col_state"),
      Messages.getString("PluginsPrefView.col_reason")
    };
    final java.util.List<Row> rows = new java.util.ArrayList<>();

    void reload(LoadedPlugins manifest, Optional<PluginGate> gate) {
      rows.clear();
      manifest.plugins().forEach(p -> rows.add(new Row(p)));
      // the candidates of the drop-in folder never offered to pf4j (the folder was not a root)
      gate.map(PluginGate::registry)
          .ifPresent(
              registry ->
                  registry.load().candidates().stream()
                      .filter(
                          c ->
                              rows.stream()
                                  .noneMatch(
                                      r -> c.getFileName().toString().equals(r.plugin().file())))
                      .forEach(
                          c ->
                              rows.add(
                                  new Row(
                                      new LoadedPlugin(
                                          c.getFileName().toString(),
                                          null,
                                          LoadedPlugin.USER,
                                          c.getFileName().toString(),
                                          null,
                                          null,
                                          PluginPolicy.Tier.UNVERIFIED,
                                          List.of(),
                                          LoadedPlugin.CANDIDATE,
                                          Messages.getString("PluginsPrefView.candidate"))))));
      fireTableDataChanged();
    }

    void addImported(LoadedPlugin plugin) {
      rows.add(new Row(plugin));
      fireTableDataChanged();
    }

    void replace(Row row, LoadedPlugin plugin) {
      int i = rows.indexOf(row);
      if (i >= 0) {
        rows.set(i, new Row(plugin));
        fireTableRowsUpdated(i, i);
      }
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
    public Object getValueAt(int rowIndex, int columnIndex) {
      LoadedPlugin p = rows.get(rowIndex).plugin();
      return switch (columnIndex) {
        case 0 -> StringUtil.getEmptyStringIfNull(p.id());
        case 1 -> StringUtil.getEmptyStringIfNull(p.version());
        case 2 -> p.source();
        case 3 -> p.tier().key();
        case 4 -> p.state();
        default -> StringUtil.getEmptyStringIfNull(p.reason());
      };
    }
  }
}
