package org.weasis.base.ui.gui;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

import javax.swing.ButtonModel;
import javax.swing.JOptionPane;
import javax.swing.text.Document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.base.ui.Messages;

import com.formdev.flatlaf.util.StringUtils;

public class LicenseDialogController {

    public enum STATUS {
        START_PROCESSING, END_PROCESSING
    };

    private static final Logger LOGGER = LoggerFactory.getLogger(LicenseDialogController.class);

    // TODO change the file name to a generic one, like .extension-license
    private static final String LICENSE_FILE_NAME = ".animati-license";
    private static final String BACKUP_EXTENSION = "backup";
    static final String CANCEL_COMMAND = "cancel";
    static final String TEST_COMMAND = "test";
    static final String OK_COMMAND = "ok";
    private static final File LICENSES_FILE = new File(
            System.getProperty("user.home") + File.separator + LICENSE_FILE_NAME);
    private static final File BACKUP_FILE = new File(
            System.getProperty("user.home") + File.separator + LICENSE_FILE_NAME + "-" + BACKUP_EXTENSION);

    private ButtonModel[] buttonModels;
    private Document document;
    private Consumer<STATUS> statusSetter;

    public LicenseDialogController(Document document, ButtonModel[] buttonModels, Consumer<STATUS> statusSetter) {
        this.buttonModels = buttonModels;
        this.document = document;
        this.statusSetter = statusSetter;
        readLicenseContents();
    }

    private void readLicenseContents() {
        if (LICENSES_FILE.exists()) {
            try {
                String licenseContents = Files.readString(LICENSES_FILE.toPath());
                document.insertString(0, licenseContents, null);
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    public void save() {
        execute(controller -> {
            try {
                String contents = document.getText(0, document.getLength());
                if (!StringUtils.isEmpty(contents.trim())) {
                    if (LICENSES_FILE.exists()) {
                        int option = JOptionPane.showConfirmDialog(null,
                                Messages.getString("LicenseDialog.license.file.exists"),
                                Messages.getString("LicenseDialog.license.file.exists.title"),
                                JOptionPane.YES_NO_OPTION);
                        if (option == JOptionPane.YES_OPTION) {
                            generateBackupFile();
                            writeFileContents(contents);
                        }
                    } else {
                        writeFileContents(contents);
                    }
                } else {
                    JOptionPane.showMessageDialog(null, Messages.getString("LicenseDialog.license.field.empty"),
                            Messages.getString("LicenseDialog.license.field.empty.title"), JOptionPane.OK_OPTION);
                }
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    private void generateBackupFile() {
        try {
            Files.copy(LICENSES_FILE.toPath(), BACKUP_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void writeFileContents(String contents) {
        try (FileWriter fw = new FileWriter(LICENSES_FILE, false)) {
            fw.write(contents);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public void test() {
        execute(controller -> {

        });
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
        for (int i = 0; i < buttonModels.length; i++) {
            ButtonModel bm = buttonModels[i];
            if (!bm.getActionCommand().equals(CANCEL_COMMAND)) {
                bm.setEnabled(true);
            }
        }

    }

    private void disable() {
        for (int i = 0; i < buttonModels.length; i++) {
            ButtonModel bm = buttonModels[i];
            if (!bm.getActionCommand().equals(CANCEL_COMMAND)) {
                bm.setEnabled(false);
            }
        }
    }
}
