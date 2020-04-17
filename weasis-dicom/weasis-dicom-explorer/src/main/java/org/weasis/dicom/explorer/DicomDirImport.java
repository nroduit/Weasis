/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.explorer;

import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.internal.Activator;
import org.weasis.dicom.explorer.wado.LoadSeries;

public class DicomDirImport extends AbstractItemDialogPage implements ImportDicom {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomDirImport.class);

    private static final String lastDICOMDIR = "lastDicomDir";//$NON-NLS-1$

    private JLabel lblImportAFolder;
    private JTextField textField;
    private JButton btnSearch;
    private JButton btncdrom;
    private JCheckBox chckbxWriteInCache;

    public DicomDirImport() {
        super(Messages.getString("DicomDirImport.dicomdir")); //$NON-NLS-1$
        setComponentPosition(5);
        initGUI();
        initialize(true);
    }

    public void initGUI() {
        GridBagLayout gridBagLayout = new GridBagLayout();
        setLayout(gridBagLayout);
        setBorder(new TitledBorder(null, Messages.getString("DicomDirImport.dicomdir"), TitledBorder.LEADING, //$NON-NLS-1$
            TitledBorder.TOP, null, null));

        lblImportAFolder = new JLabel(Messages.getString("DicomDirImport.path") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbc_lblImportAFolder = new GridBagConstraints();
        gbc_lblImportAFolder.anchor = GridBagConstraints.WEST;
        gbc_lblImportAFolder.insets = new Insets(5, 5, 5, 5);
        gbc_lblImportAFolder.gridx = 0;
        gbc_lblImportAFolder.gridy = 0;
        add(lblImportAFolder, gbc_lblImportAFolder);

        textField = new JTextField();
        GridBagConstraints gbc_textField = new GridBagConstraints();
        gbc_textField.anchor = GridBagConstraints.WEST;
        gbc_textField.insets = new Insets(5, 2, 5, 5);
        gbc_textField.fill = GridBagConstraints.HORIZONTAL;
        gbc_textField.gridx = 1;
        gbc_textField.gridy = 0;
        JMVUtils.setPreferredWidth(textField, 375, 325);
        textField.setText(Activator.IMPORT_EXPORT_PERSISTENCE.getProperty(lastDICOMDIR, ""));//$NON-NLS-1$
        add(textField, gbc_textField);

        btnSearch = new JButton(" ... "); //$NON-NLS-1$
        btnSearch.addActionListener(e -> browseImgFile());
        GridBagConstraints gbc_button = new GridBagConstraints();
        gbc_button.anchor = GridBagConstraints.WEST;
        gbc_button.insets = new Insets(5, 5, 5, 0);
        gbc_button.gridx = 2;
        gbc_button.gridy = 0;
        add(btnSearch, gbc_button);

        btncdrom = new JButton(Messages.getString("DicomDirImport.detect"), //$NON-NLS-1$
            new ImageIcon(DicomDirImport.class.getResource("/icon/16x16/cd.png"))); //$NON-NLS-1$
        btncdrom.addActionListener(e -> {
            File dcmdir = getDcmDirFromMedia();
            if (dcmdir != null) {
                String path = dcmdir.getPath();
                textField.setText(path);
                Activator.IMPORT_EXPORT_PERSISTENCE.setProperty(lastDICOMDIR, path);
            }
        });
        GridBagConstraints gbc_btnNewButton = new GridBagConstraints();
        gbc_btnNewButton.gridwidth = 3;
        gbc_btnNewButton.anchor = GridBagConstraints.WEST;
        gbc_btnNewButton.insets = new Insets(5, 5, 5, 5);
        gbc_btnNewButton.gridx = 0;
        gbc_btnNewButton.gridy = 1;
        add(btncdrom, gbc_btnNewButton);

        chckbxWriteInCache = new JCheckBox(Messages.getString("DicomDirImport.cache"));//$NON-NLS-1$
        GridBagConstraints gbc_chckbxWriteInCache = new GridBagConstraints();
        gbc_chckbxWriteInCache.gridwidth = 3;
        gbc_chckbxWriteInCache.anchor = GridBagConstraints.WEST;
        gbc_chckbxWriteInCache.insets = new Insets(0, 0, 5, 0);
        gbc_chckbxWriteInCache.gridx = 0;
        gbc_chckbxWriteInCache.gridy = 2;
        add(chckbxWriteInCache, gbc_chckbxWriteInCache);

        final JLabel label = new JLabel();
        final GridBagConstraints gridBagConstraints_4 = new GridBagConstraints();
        gridBagConstraints_4.weighty = 1.0;
        gridBagConstraints_4.weightx = 1.0;
        gridBagConstraints_4.gridy = 5;
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
            directory = Activator.IMPORT_EXPORT_PERSISTENCE.getProperty(lastDICOMDIR, "");//$NON-NLS-1$
        }
        JFileChooser fileChooser = new JFileChooser(directory);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setFileFilter(new FileFilter() {

            @Override
            public String getDescription() {
                return "DICOMDIR"; //$NON-NLS-1$
            }

            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) {
                    return true;
                }
                if (f.getName().equalsIgnoreCase("dicomdir") || f.getName().equalsIgnoreCase("dicomdir.")) { //$NON-NLS-1$ //$NON-NLS-2$
                    return true;
                }
                return false;
            }
        });
        File selectedFile = null;
        if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION
            || (selectedFile = fileChooser.getSelectedFile()) == null) {
            return;
        } else {
            String path = selectedFile.getPath();
            textField.setText(path);
            Activator.IMPORT_EXPORT_PERSISTENCE.setProperty(lastDICOMDIR, path);
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
        if (path != null && !path.trim().equals("")) { //$NON-NLS-1$
            return path;
        }
        return null;
    }

    @Override
    public void importDICOM(DicomModel dicomModel, JProgressBar info) {
        File file = null;
        String path = getImportPath();
        if (path != null) {
            File f = new File(path);
            if (f.canRead()) {
                file = f;
            } else {
                try {
                    f = new File(new URI(path));
                    if (f.canRead()) {
                        file = f;
                    }
                } catch (Exception e) {
                    LOGGER.error("Cannot read {}", path); //$NON-NLS-1$
                }
            }
        }
        List<LoadSeries> loadSeries = loadDicomDir(file, dicomModel, chckbxWriteInCache.isSelected());

        if (loadSeries != null && !loadSeries.isEmpty()) {
            DicomModel.LOADING_EXECUTOR.execute(new LoadDicomDir(loadSeries, dicomModel));
        } else {
            LOGGER.error("Cannot import DICOM from {}", file); //$NON-NLS-1$

            int response = JOptionPane.showConfirmDialog(this, Messages.getString("DicomExplorer.mes_import_manual"), //$NON-NLS-1$
                this.getTitle(), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

            if (response == JOptionPane.YES_OPTION) {
                Dialog dialog = WinUtil.getParentDialog(this);
                if (dialog instanceof DicomImport) {
                    DicomImport dcmImport = (DicomImport) dialog;
                    dcmImport.setCancelVeto(true); // Invalidate if closing the dialog
                    dcmImport.showPage(Messages.getString("DicomImport.imp_dicom")); //$NON-NLS-1$
                    if (file != null) {
                        AbstractItemDialogPage page = dcmImport.getCurrentPage();
                        if (page instanceof LocalImport) {
                            ((LocalImport) page).setImportPath(file.getParent());
                        }
                    }
                }
            }
        }
    }

    public static List<LoadSeries> loadDicomDir(File file, DicomModel dicomModel, boolean writeIncache) {
        List<LoadSeries> loadSeries = null;
        if (file != null) {
            if (file.canRead()) {
                DicomDirLoader dirImport = new DicomDirLoader(file, dicomModel, writeIncache);
                loadSeries = dirImport.readDicomDir();
            }
        }
        return loadSeries;
    }

    public static File getDcmDirFromMedia() {
        final List<File> dvs = new ArrayList<>();
        try {
            if (AppProperties.OPERATING_SYSTEM.startsWith("win")) { //$NON-NLS-1$
                dvs.addAll(Arrays.asList(File.listRoots()));
            } else if (AppProperties.OPERATING_SYSTEM.startsWith("mac")) { //$NON-NLS-1$
                dvs.addAll(Arrays.asList(new File("/Volumes").listFiles())); //$NON-NLS-1$
            } else {
                dvs.addAll(Arrays.asList(new File("/media").listFiles())); //$NON-NLS-1$
                dvs.addAll(Arrays.asList(new File("/mnt").listFiles())); //$NON-NLS-1$
                File userDir = new File("/media/" + System.getProperty("user.name", "local")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                if (userDir.exists()) {
                    dvs.addAll(Arrays.asList(userDir.listFiles()));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error when reading device directories: {}", e.getMessage()); //$NON-NLS-1$
        }

        Collections.reverse(dvs);
        String[] dicomdir = { "DICOMDIR", "dicomdir", "DICOMDIR.", "dicomdir." }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

        for (File drive : dvs) {
            // Detect read-only media
            if (drive.canRead() && !drive.isHidden()) {
                for (int j = 0; j < dicomdir.length; j++) {
                    File f = new File(drive, dicomdir[j]);
                    if (f.canRead() && !f.canWrite()) {
                        return f;
                    }
                }
            }
        }

        return null;
    }
}
