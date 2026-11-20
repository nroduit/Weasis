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

import java.awt.FlowLayout;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.weasis.core.Messages;
import org.weasis.core.api.command.CommandManager;
import org.weasis.core.api.command.CommandPolicy;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.setting.CommandPolicyLayers;
import org.weasis.core.api.setting.CoreSettings;
import org.weasis.core.api.setting.Setting;
import org.weasis.core.api.setting.Settings;

/**
 * The *Commands* page: what a remote caller (a script, an agent) may do on this instance, as the
 * configuration and the preferences decide it. The policy is shown whole; the network and the
 * folders a remote caller may write under can be changed here unless the site locked the policy.
 */
public class CommandsPrefView extends AbstractItemDialogPage {

  public static final String PAGE_NAME = Messages.getString("CommandsPrefView.title");

  private final JLabel summary = new JLabel();
  private final JComboBox<String> read = choices();
  private final JTextField readHosts = new JTextField(24);
  private final JComboBox<String> write = choices();
  private final JTextField writeHosts = new JTextField(24);
  private final JTextField roots = new JTextField(32);
  private final JLabel note = new JLabel();

  public CommandsPrefView() {
    super(PAGE_NAME, 125);
    setBorder(GuiUtils.getEmptyBorder(15, 10, 10, 10));
    add(summary);
    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));
    add(row(Messages.getString("CommandsPrefView.network.read"), read, readHosts));
    add(row(Messages.getString("CommandsPrefView.network.write"), write, writeHosts));
    add(row(Messages.getString("CommandsPrefView.files.roots"), roots, null));
    add(GuiUtils.boxVerticalStrut(ITEM_SEPARATOR));
    add(note);
    add(GuiUtils.boxYLastElement(LAST_FILLER_HEIGHT));
    load();
    getProperties().setProperty(PreferenceDialog.KEY_SHOW_APPLY, Boolean.TRUE.toString());
    getProperties().setProperty(PreferenceDialog.KEY_HELP, "automate/commands/"); // NON-NLS
  }

  private static JComboBox<String> choices() {
    return new JComboBox<>(
        new String[] {
          Messages.getString("CommandsPrefView.configuration"),
          Messages.getString("CommandsPrefView.allow"),
          Messages.getString("CommandsPrefView.refuse")
        });
  }

  private static JPanel row(
      String label, javax.swing.JComponent first, javax.swing.JComponent second) {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING, ITEM_SEPARATOR_SMALL, 0));
    panel.add(new JLabel(label + org.weasis.core.util.StringUtil.COLON));
    panel.add(first);
    if (second != null) {
      panel.add(
          new JLabel(
              Messages.getString("CommandsPrefView.hosts")
                  + org.weasis.core.util.StringUtil.COLON));
      panel.add(second);
    }
    return panel;
  }

  private void load() {
    CommandManager manager = CommandManager.current();
    CommandPolicy policy = manager == null ? CommandPolicy.defaults() : manager.policy();
    boolean locked = manager != null && manager.basePolicy().locked();
    summary.setText(
        Messages.format(
            "CommandsPrefView.summary",
            manager == null ? "?" : manager.policySource(),
            policy.remote().enabled()
                ? String.join(", ", policy.remote().namespaces())
                : Messages.getString("CommandsPrefView.closed"),
            policy.files().writeRoots().stream()
                .map(Object::toString)
                .reduce((a, b) -> a + ", " + b)
                .orElse(""),
            word(policy.network().read()),
            word(policy.network().write()),
            word(policy.assumeYesAllowed()),
            word(policy.shutdownAllowed()),
            word(policy.events().enabled())));
    select(read, Settings.get(CoreSettings.COMMANDS_NETWORK_READ));
    readHosts.setText(Settings.get(CoreSettings.COMMANDS_NETWORK_READ_HOSTS));
    select(write, Settings.get(CoreSettings.COMMANDS_NETWORK_WRITE));
    writeHosts.setText(Settings.get(CoreSettings.COMMANDS_NETWORK_WRITE_HOSTS));
    roots.setText(Settings.get(CoreSettings.COMMANDS_FILE_WRITE_ROOTS));
    boolean editable = !locked;
    for (javax.swing.JComponent c : List.of(read, readHosts, write, writeHosts, roots)) {
      c.setEnabled(editable && !Settings.isLocked(settingOf(c)));
    }
    note.setText(
        Messages.getString(
            locked ? "CommandsPrefView.locked" : "CommandsPrefView.applies")); // NON-NLS
  }

  private Setting<String> settingOf(javax.swing.JComponent c) {
    if (c == read) {
      return CoreSettings.COMMANDS_NETWORK_READ;
    }
    if (c == readHosts) {
      return CoreSettings.COMMANDS_NETWORK_READ_HOSTS;
    }
    if (c == write) {
      return CoreSettings.COMMANDS_NETWORK_WRITE;
    }
    if (c == writeHosts) {
      return CoreSettings.COMMANDS_NETWORK_WRITE_HOSTS;
    }
    return CoreSettings.COMMANDS_FILE_WRITE_ROOTS;
  }

  private static String word(boolean allowed) {
    return Messages.getString(allowed ? "CommandsPrefView.allow" : "CommandsPrefView.refuse");
  }

  private static void select(JComboBox<String> box, String value) {
    int index =
        CommandPolicyLayers.ALLOW.equalsIgnoreCase(value)
            ? 1
            : CommandPolicyLayers.REFUSE.equalsIgnoreCase(value) ? 2 : 0;
    box.setSelectedIndex(index);
  }

  private static String value(JComboBox<String> box) {
    return switch (box.getSelectedIndex()) {
      case 1 -> CommandPolicyLayers.ALLOW;
      case 2 -> CommandPolicyLayers.REFUSE;
      default -> "";
    };
  }

  @Override
  public void closeAdditionalWindow() {
    CommandManager manager = CommandManager.current();
    if (manager != null && manager.basePolicy().locked()) {
      return;
    }
    Settings.set(CoreSettings.COMMANDS_NETWORK_READ, value(read));
    Settings.set(CoreSettings.COMMANDS_NETWORK_READ_HOSTS, readHosts.getText().trim());
    Settings.set(CoreSettings.COMMANDS_NETWORK_WRITE, value(write));
    Settings.set(CoreSettings.COMMANDS_NETWORK_WRITE_HOSTS, writeHosts.getText().trim());
    Settings.set(CoreSettings.COMMANDS_FILE_WRITE_ROOTS, roots.getText().trim());
    CommandPolicyLayers.apply();
    load();
  }

  @Override
  public void resetToDefaultValues() {
    Settings.reset(CoreSettings.COMMANDS_NETWORK_READ);
    Settings.reset(CoreSettings.COMMANDS_NETWORK_READ_HOSTS);
    Settings.reset(CoreSettings.COMMANDS_NETWORK_WRITE);
    Settings.reset(CoreSettings.COMMANDS_NETWORK_WRITE_HOSTS);
    Settings.reset(CoreSettings.COMMANDS_FILE_WRITE_ROOTS);
    CommandPolicyLayers.apply();
    load();
  }
}
