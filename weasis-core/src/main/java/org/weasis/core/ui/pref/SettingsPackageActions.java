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

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Optional;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.command.CommandManager;
import org.weasis.core.api.setting.SettingsPackage;
import org.weasis.core.api.setting.SettingsPackage.Change;
import org.weasis.core.api.setting.SettingsPackage.Environment;
import org.weasis.core.api.setting.SettingsPackage.Layer;
import org.weasis.core.api.setting.SettingsPackage.Mode;
import org.weasis.core.api.setting.SettingsPackage.Plan;
import org.weasis.core.api.util.DocumentVersions;
import org.weasis.core.api.util.SiteDocuments;

/** The Export and Import buttons of the preference dialog: a settings package of the user. */
final class SettingsPackageActions {
  private static final Logger LOGGER = LoggerFactory.getLogger(SettingsPackageActions.class);

  private SettingsPackageActions() {}

  static void exportSettings(Component parent) {
    Environment env = SettingsPackage.currentEnvironment();
    JFileChooser chooser = chooser();
    chooser.setSelectedFile(new File(SettingsPackage.defaultName(Layer.USER, env.source())));
    JCheckBox machine = new JCheckBox(Messages.getString("PreferenceDialog.export.machine"));
    chooser.setAccessory(machine);
    if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    Path file = chooser.getSelectedFile().toPath();
    if (!file.getFileName().toString().endsWith(".zip")) { // NON-NLS
      file = file.resolveSibling(file.getFileName() + ".zip"); // NON-NLS
    }
    try {
      SettingsPackage.write(file, Layer.USER, machine.isSelected(), env);
    } catch (IOException e) {
      LOGGER.error("Cannot export the settings into {}", file, e);
      error(parent, e);
    }
  }

  static void importSettings(Component parent) {
    JFileChooser chooser = chooser();
    if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    Path file = chooser.getSelectedFile().toPath();
    Environment env = SettingsPackage.currentEnvironment();
    try {
      Plan plan = SettingsPackage.importPackage(file, Mode.MERGE, true, env, null);
      int[] t = totals(plan);
      if (t[0] + t[1] == 0) {
        JOptionPane.showMessageDialog(
            parent, Messages.getString("PreferenceDialog.import.nothing"));
        return;
      }
      StringBuilder message =
          new StringBuilder(
              MessageFormat.format(
                  Messages.getString("PreferenceDialog.import.plan"),
                  plan.manifest().source().getOrDefault("user", "?"), // NON-NLS
                  plan.manifest().created(),
                  t[0],
                  t[1],
                  t[2],
                  t[3]));
      if (!plan.errors().isEmpty()) {
        message.append("\n\n").append(Messages.getString("PreferenceDialog.import.errors"));
        plan.errors().forEach(e -> message.append("\n- ").append(e));
      }
      if (!plan.warnings().isEmpty()) {
        message.append("\n\n").append(Messages.getString("PreferenceDialog.import.references"));
        plan.warnings().forEach(w -> message.append("\n- ").append(w));
      }
      Object[] options = {
        Messages.getString("PreferenceDialog.import.merge"),
        Messages.getString("PreferenceDialog.import.replace"),
        Messages.getString("cancel")
      };
      int choice =
          JOptionPane.showOptionDialog(
              parent,
              message.toString(),
              Messages.getString("PreferenceDialog.import.settings"),
              JOptionPane.YES_NO_CANCEL_OPTION,
              JOptionPane.QUESTION_MESSAGE,
              null,
              options,
              options[0]);
      if (choice != 0 && choice != 1) {
        return;
      }
      Optional<String> checkpoint =
          CommandManager.stateBoundary()
              .checkpoint("before settings import " + file.getFileName(), null);
      Plan done =
          SettingsPackage.importPackage(
              file,
              choice == 0 ? Mode.MERGE : Mode.REPLACE,
              false,
              env,
              SettingsPackage.backupFolder());
      checkpoint.ifPresent(id -> DocumentVersions.importBackup(id, done.backup()));
      SiteDocuments.reloadAll();
      JOptionPane.showMessageDialog(
          parent,
          MessageFormat.format(
              Messages.getString("PreferenceDialog.import.done"), String.valueOf(done.backup())));
    } catch (IOException | RuntimeException e) {
      LOGGER.error("Cannot import the settings package {}", file, e);
      error(parent, e);
    }
  }

  /** added, replaced, unchanged, skipped. */
  private static int[] totals(Plan plan) {
    int[] t = new int[4];
    for (Change c : plan.changes()) {
      t[0] += c.added();
      t[1] += c.replaced();
      t[2] += c.unchanged();
      t[3] += c.skipped();
    }
    return t;
  }

  private static JFileChooser chooser() {
    JFileChooser chooser = new JFileChooser();
    chooser.setFileFilter(
        new FileNameExtensionFilter(
            Messages.getString("PreferenceDialog.package.files"), "zip")); // NON-NLS
    return chooser;
  }

  private static void error(Component parent, Exception e) {
    JOptionPane.showMessageDialog(
        parent, e.getMessage(), Messages.getString("error"), JOptionPane.ERROR_MESSAGE);
  }
}
