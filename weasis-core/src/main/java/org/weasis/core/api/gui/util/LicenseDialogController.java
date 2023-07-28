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

import com.formdev.flatlaf.util.StringUtils;
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

public class LicenseDialogController implements LicenseController {

  public enum STATUS {
    START_PROCESSING,
    END_PROCESSING
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(LicenseDialogController.class);

  static final String CANCEL_COMMAND = "cancel"; // NON-NLS
  static final String OK_COMMAND = "ok"; // NON-NLS
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

  @Override
  public void save() {
    execute(
        controller -> {
          try {
            String contents = document.getText(0, document.getLength());
            if (!StringUtils.isEmpty(contents.trim())) {
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
            } else {
              JOptionPane.showMessageDialog(
                  licencesItem,
                  Messages.getString("license.field.empty"),
                  Messages.getString("license"),
                  JOptionPane.WARNING_MESSAGE);
            }
          } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            JOptionPane.showMessageDialog(
                licencesItem,
                Messages.getString("error.saving.license"),
                Messages.getString("license"),
                JOptionPane.ERROR_MESSAGE);
          }
        });
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
    licencesItem.getCancelButton().setEnabled(true);
  }

  private void disable() {
    licencesItem.getCancelButton().setEnabled(false);
  }
}
