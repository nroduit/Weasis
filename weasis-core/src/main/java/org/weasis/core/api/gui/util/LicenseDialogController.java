/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

import javax.swing.JOptionPane;
import javax.swing.text.Document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;

import com.formdev.flatlaf.util.StringUtils;

/**
 * Default behavior for a license controller.
 */
public class LicenseDialogController implements LicenseController {

  public enum STATUS {
    START_PROCESSING,
    END_PROCESSING
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(LicenseDialogController.class);

  static final String CANCEL_COMMAND = "cancel"; // NON-NLS
  static final String OK_COMMAND = "ok"; // NON-NLS
  static final String TEST_COMMAND = "test";
  private final File licenceFile;

  private final AbstractTabLicense licencesItem;
  private final Document document;
  private final Consumer<STATUS> statusSetter;

  public LicenseDialogController(
      Document document, AbstractTabLicense licencesItem, Consumer<STATUS> statusSetter) {
    this.licencesItem = licencesItem;
    this.document = document;
    this.statusSetter = statusSetter;
    this.licenceFile =
        new File(
            AppProperties.WEASIS_PATH
                + File.separator
                + licencesItem.getLicenseName()
                + ".license"); // NON-NLS
    readLicenseContents();
  }

  private void readLicenseContents() {
    if (licenceFile.exists()) {
      try {
        String licenseContents = Files.readString(licenceFile.toPath());
        document.insertString(0, licenseContents, null);
      } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);
      }
    }
  }

  /**
   * Confirms changes at the user's local installation/PC, by executing the following
   * steps:
   * <ol>
   * <li>Executes the same as the {@link #testButton} button.</li>
   * <li>Save activation code.</li>
   * <li>Changes config.properties to add new information about new plugins.</li>
   * <li>Update UI reading new icon and messages from the remote boot OSGI jar file. This 
   * jar will be found using the server license field URL.</li>
   * <li>Ask user to re-start Weasis.</li>
   * </ol>
   */
  @Override
  public void save() {
    execute(
        controller -> {
          try {
            String contents = document.getText(0, document.getLength());
            if (!StringUtils.isEmpty(contents.trim())) {
              if (test()) {
                  if (licenceFile.exists()) {
                      int option =
                          JOptionPane.showConfirmDialog(
                              null,
                              Messages.getString("license.file.exists"),
                              Messages.getString("license"),
                              JOptionPane.YES_NO_OPTION);
                      if (option == JOptionPane.YES_OPTION) {
                        generateBackupFile();
                        writeFileContents(contents);
                      }
                    } else {
                      writeFileContents(contents);
                    }
                    JOptionPane.showMessageDialog(
                        licencesItem,
                        Messages.getString("license.successfully.saved"),
                        Messages.getString("license"),
                        JOptionPane.INFORMATION_MESSAGE);
                    changeConfigProperties();
                    updateUI();
                    askUserToRestart();
                  } else {
                    JOptionPane.showMessageDialog(
                        licencesItem,
                        Messages.getString("license.field.empty"),
                        Messages.getString("license"),
                        JOptionPane.WARNING_MESSAGE);
                  }
              }
          } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            JOptionPane.showMessageDialog(
                licencesItem,
                Messages.getString("license.error.saving"),
                Messages.getString("license"),
                JOptionPane.ERROR_MESSAGE);
          }
        });
  }

  private void askUserToRestart() {
  }

  private void updateUI() {
  }

  private void changeConfigProperties() {
  }

  /**
   * Do not changes anything at user's local PC/installation. Executes the following steps
   * just to validate license/activation code.
   * <ol>
   * <li>Ping URL at the server license field. The ping needs to return HTTP 200 code in order to move
   * to next step.</li>
   * <li>Download the OSGi boot jar file from server license URL, installs and activates it.</li>
   * <li>Executes a check running a check test method inside the bundle, just to guarantee that the interfaces match.</li>
   * </ol>
   */
  @Override
  public boolean test() {
      if (pingLicenseServerURL()) {
          return downloadBootJarAndTestBundleAccess();
      } else {
          JOptionPane.showMessageDialog(
                  licencesItem,
                  Messages.getString("license.error.testing"),
                  Messages.getString("license"),
                  JOptionPane.ERROR_MESSAGE);
          return false;
      }
  }

  private boolean downloadBootJarAndTestBundleAccess() {
      return true;
  }

  private boolean pingLicenseServerURL() {
    return true;
  }

  @Override
  public void cancel() {
  }

  private void generateBackupFile() {
    try {
      Files.copy(
          licenceFile.toPath(),
          Paths.get(licenceFile.getPath() + "-backup"),
          StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  private void writeFileContents(String contents) {
    try (FileWriter fw = new FileWriter(licenceFile, StandardCharsets.UTF_8, false)) {
      fw.write(contents);
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  private void execute(Consumer<LicenseDialogController> c) {
    try {
      statusSetter.accept(STATUS.START_PROCESSING);
      disable();
      c.accept(this);
    } finally {
      statusSetter.accept(STATUS.END_PROCESSING);
      enable();
    }
  }

  private void enable() {
    licencesItem.getSaveButton().setEnabled(true);
    licencesItem.getTestButton().setEnabled(true);
  }

  private void disable() {
    licencesItem.getSaveButton().setEnabled(false);
    licencesItem.getTestButton().setEnabled(false);
  }

}
