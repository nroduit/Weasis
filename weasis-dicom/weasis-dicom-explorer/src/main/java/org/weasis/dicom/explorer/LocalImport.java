/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.explorer;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.net.URI;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.explorer.internal.Activator;

public class LocalImport extends AbstractItemDialogPage implements ImportDicom {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalImport.class);

    private static final String lastDirKey = "lastOpenDir";//$NON-NLS-1$

    private JCheckBox chckbxSearch;
    private JLabel lblImportAFolder;
    private JTextField textField;
    private JButton button;
    private File[] files;

    public LocalImport() {
        super(Messages.getString("LocalImport.local_dev")); //$NON-NLS-1$
        setComponentPosition(0);
        initGUI();
        initialize(true);
    }

    public void initGUI() {
        GridBagLayout gridBagLayout = new GridBagLayout();
        setLayout(gridBagLayout);
        setBorder(new TitledBorder(null, Messages.getString("LocalImport.imp_files") + StringUtil.COLON, //$NON-NLS-1$
            TitledBorder.LEADING, TitledBorder.TOP, null, null));

        lblImportAFolder = new JLabel(Messages.getString("LocalImport.path") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbc_lblImportAFolder = new GridBagConstraints();
        gbc_lblImportAFolder.anchor = GridBagConstraints.WEST;
        gbc_lblImportAFolder.insets = new Insets(5, 5, 0, 0);
        gbc_lblImportAFolder.gridx = 0;
        gbc_lblImportAFolder.gridy = 0;
        add(lblImportAFolder, gbc_lblImportAFolder);

        textField = new JTextField();
        GridBagConstraints gbc_textField = new GridBagConstraints();
        gbc_textField.anchor = GridBagConstraints.WEST;
        gbc_textField.insets = new Insets(5, 2, 0, 0);
        gbc_textField.fill = GridBagConstraints.HORIZONTAL;
        gbc_textField.gridx = 1;
        gbc_textField.gridy = 0;
        JMVUtils.setPreferredWidth(textField, 375, 325);
        textField.setText(Activator.IMPORT_EXPORT_PERSISTENCE.getProperty(lastDirKey, ""));//$NON-NLS-1$
        add(textField, gbc_textField);

        button = new JButton(" ... "); //$NON-NLS-1$
        button.addActionListener(e -> browseImgFile());
        GridBagConstraints gbc_button = new GridBagConstraints();
        gbc_button.anchor = GridBagConstraints.WEST;
        gbc_button.insets = new Insets(5, 5, 0, 5);
        gbc_button.gridx = 2;
        gbc_button.gridy = 0;
        add(button, gbc_button);

        chckbxSearch = new JCheckBox(Messages.getString("LocalImport.recursive")); //$NON-NLS-1$
        chckbxSearch.setSelected(true);
        GridBagConstraints gbc_chckbxSearch = new GridBagConstraints();
        gbc_chckbxSearch.gridwidth = 3;
        gbc_chckbxSearch.insets = new Insets(5, 5, 0, 0);
        gbc_chckbxSearch.anchor = GridBagConstraints.NORTHWEST;
        gbc_chckbxSearch.gridx = 0;
        gbc_chckbxSearch.gridy = 1;
        add(chckbxSearch, gbc_chckbxSearch);

        final JLabel label = new JLabel();
        final GridBagConstraints gridBagConstraints_4 = new GridBagConstraints();
        gridBagConstraints_4.weighty = 1.0;
        gridBagConstraints_4.weightx = 1.0;
        gridBagConstraints_4.gridy = 4;
        gridBagConstraints_4.gridx = 2;
        add(label, gridBagConstraints_4);
    }

    protected void initialize(boolean afirst) {
        if (afirst) {

        }
    }

    public void browseImgFile() {
        String directory = getImportPath();
        if (directory == null) {
            directory = Activator.IMPORT_EXPORT_PERSISTENCE.getProperty(lastDirKey, "");//$NON-NLS-1$
        }
        JFileChooser fileChooser = new JFileChooser(directory);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fileChooser.setMultiSelectionEnabled(true);
        // FileFormatFilter.setImageDecodeFilters(fileChooser);
        File[] selectedFiles = null;
        if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION
            || (selectedFiles = fileChooser.getSelectedFiles()) == null || selectedFiles.length == 0) {
            return;
        } else {
            files = null;
            String lastDir = null;
            if (selectedFiles.length == 1) {
                lastDir = selectedFiles[0].getPath();
                textField.setText(lastDir);
            } else {
                files = selectedFiles;
                lastDir = files[0].getParent();
                textField.setText(Messages.getString("LocalImport.multi_dir")); //$NON-NLS-1$
            }
            if (StringUtil.hasText(lastDir)) {
                Activator.IMPORT_EXPORT_PERSISTENCE.setProperty(lastDirKey, lastDir);
            }
        }
    }

    public void resetSettingsToDefault() {
        initialize(false);
    }

    public void applyChange() {
    }

    protected void updateChanges() {
    }

    @Override
    public void closeAdditionalWindow() {
        applyChange();

    }

    @Override
    public void resetoDefaultValues() {
    }

    private String getImportPath() {
        String path = textField.getText().trim();
        if (path != null && !path.equals("") && !path.equals(Messages.getString("LocalImport.multi_dir"))) { //$NON-NLS-1$ //$NON-NLS-2$
            return path;
        }
        return null;
    }

    public void setImportPath(String path) {
        textField.setText(path);
    }

    @Override
    public void importDICOM(DicomModel dicomModel, JProgressBar info) {
        if (files == null) {
            String path = getImportPath();
            if (path != null) {
                File file = new File(path);
                if (file.canRead()) {
                    files = new File[] { file };
                } else {
                    try {
                        file = new File(new URI(path));
                        if (file.canRead()) {
                            files = new File[] { file };
                        }
                    } catch (Exception e) {
                        LOGGER.error("Cannot import DICOM from {}", path); //$NON-NLS-1$
                    }
                }
            }
        }
        if (files != null) {
            LoadLocalDicom dicom = new LoadLocalDicom(files, chckbxSearch.isSelected(), dicomModel);
            DicomModel.LOADING_EXECUTOR.execute(dicom);
            files = null;
        }
    }
}
