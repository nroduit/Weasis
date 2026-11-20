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

import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.Messages;
import org.weasis.core.api.command.CommandManager;
import org.weasis.core.api.command.CommandPolicy;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.setting.CommandPolicyLayers;
import org.weasis.core.api.setting.CoreSettings;
import org.weasis.core.api.setting.Setting;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.util.StringUtil;

/**
 * The *Commands* page: what a remote caller (a script, an agent) may do on this instance, as the
 * configuration and the preferences decide it. The policy is shown whole; the network and the
 * folders a remote caller may write under can be changed here unless the site locked the policy.
 */
public class CommandsPrefView extends AbstractItemDialogPage {

  public static final String PAGE_NAME = Messages.getString("CommandsPrefView.title");

  private final JTextArea summary = wrappingText();
  private final JComboBox<String> read = choices();
  private final JTextField readHosts = new JTextField(24);
  private final JComboBox<String> write = choices();
  private final JTextField writeHosts = new JTextField(24);
  private final JTextField roots = new JTextField(32);
  private final JTextArea note = wrappingText();

  public CommandsPrefView() {
    super(PAGE_NAME, 125);
    // One grid: the texts wrap to the width of the page, the fields take what is left
    setLayout(new MigLayout("ins 0, fillx, wrap 4", "[][][][grow,fill]")); // NON-NLS
    add(summary, "span, growx, wmin 0, gapbottom unrel"); // NON-NLS
    addRow(Messages.getString("CommandsPrefView.network.read"), read, readHosts);
    addRow(Messages.getString("CommandsPrefView.network.write"), write, writeHosts);
    add(new JLabel(Messages.getString("CommandsPrefView.files.roots") + StringUtil.COLON));
    add(roots, "span 3, growx, wmin 80lp"); // NON-NLS
    add(note, "span, growx, wmin 0, gaptop unrel"); // NON-NLS
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

  private void addRow(String label, JComboBox<String> choice, JTextField hosts) {
    add(new JLabel(label + StringUtil.COLON));
    add(choice);
    add(new JLabel(Messages.getString("CommandsPrefView.hosts") + StringUtil.COLON));
    add(hosts, "wmin 80lp"); // NON-NLS
  }

  /** A text shown like a label that wraps to the width it is given. */
  private static JTextArea wrappingText() {
    JTextArea area = new JTextArea();
    area.setEditable(false);
    area.setFocusable(false);
    area.setOpaque(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setBorder(null);
    area.setFont(javax.swing.UIManager.getFont("Label.font")); // NON-NLS
    return area;
  }

  // The summary message is written in HTML, one line per sentence
  private static String plainText(String html) {
    return html.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]*>", "").strip(); // NON-NLS
  }

  private void load() {
    CommandManager manager = CommandManager.current();
    CommandPolicy policy = manager == null ? CommandPolicy.defaults() : manager.policy();
    boolean locked = manager != null && manager.basePolicy().locked();
    summary.setText(
        plainText(
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
                word(policy.events().enabled()))));
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
