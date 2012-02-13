/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.explorer;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.dicom.explorer.internal.Activator;
import org.weasis.dicom.explorer.wado.LoadSeries;

public class DicomDirImport extends AbstractItemDialogPage implements ImportDicom {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomDirImport.class);

    private static final String lastDICOMDIR = "lastDicomDir";//$NON-NLS-1$

    private JCheckBox chckbxCache;
    private JLabel lblImportAFolder;
    private JTextField textField;
    private JButton btnSearch;
    private JButton btncdrom;

    public DicomDirImport() {
        setTitle("DICOMDIR (CD)");
        initGUI();
        initialize(true);
    }

    public void initGUI() {
        GridBagLayout gridBagLayout = new GridBagLayout();
        setLayout(gridBagLayout);
        setBorder(new TitledBorder(null, "DICOMDIR", TitledBorder.LEADING, TitledBorder.TOP, null, null));

        lblImportAFolder = new JLabel("Path:");
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
        btnSearch.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                browseImgFile();
            }
        });
        GridBagConstraints gbc_button = new GridBagConstraints();
        gbc_button.anchor = GridBagConstraints.WEST;
        gbc_button.insets = new Insets(5, 5, 5, 0);
        gbc_button.gridx = 2;
        gbc_button.gridy = 0;
        add(btnSearch, gbc_button);

        btncdrom = new JButton("Detect CD-ROM", new ImageIcon(DicomDirImport.class.getResource("/icon/16x16/cd.png"))); //$NON-NLS-1$
        btncdrom.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                File dcmdir = getDcmDirFromMedia();
                if (dcmdir != null) {
                    String path = dcmdir.getPath();
                    textField.setText(path);
                    Activator.IMPORT_EXPORT_PERSISTENCE.setProperty(lastDICOMDIR, path);
                    // By default, copy images in cache for cdrom
                    chckbxCache.setSelected(true);
                }
            }
        });
        GridBagConstraints gbc_btnNewButton = new GridBagConstraints();
        gbc_btnNewButton.gridwidth = 2;
        gbc_btnNewButton.anchor = GridBagConstraints.WEST;
        gbc_btnNewButton.insets = new Insets(5, 5, 5, 5);
        gbc_btnNewButton.gridx = 0;
        gbc_btnNewButton.gridy = 1;
        add(btncdrom, gbc_btnNewButton);

        chckbxCache = new JCheckBox("Copy images in cache temporarily"); //$NON-NLS-1$
        GridBagConstraints gbc_chckbxSearch = new GridBagConstraints();
        gbc_chckbxSearch.gridwidth = 3;
        gbc_chckbxSearch.insets = new Insets(5, 5, 5, 0);
        gbc_chckbxSearch.anchor = GridBagConstraints.NORTHWEST;
        gbc_chckbxSearch.gridx = 0;
        gbc_chckbxSearch.gridy = 2;
        add(chckbxCache, gbc_chckbxSearch);

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
                return "DICOMDIR";
            }

            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) {
                    return true;
                }
                if (f.getName().equalsIgnoreCase("dicomdir") || f.getName().equalsIgnoreCase("dicomdir.")) {
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
        loadDicomDir(file, dicomModel);
    }

    public static void loadDicomDir(File file, DicomModel dicomModel) {
        if (file != null) {
            ArrayList<LoadSeries> loadSeries = null;
            if (file.canRead()) {
                DicomDirLoader dirImport = new DicomDirLoader(file, dicomModel);
                loadSeries = dirImport.readDicomDir();
            }
            if (loadSeries != null && loadSeries.size() > 0) {
                DicomModel.loadingExecutor.execute(new LoadDicomDir(loadSeries, dicomModel));
            } else {
                LOGGER.error("Cannot import DICOM from {}", file);
            }
        }
    }

    public static File getDcmDirFromMedia() {
        String os = AbstractProperties.OPERATING_SYSTEM;
        File[] drives = null;
        if (os.startsWith("win")) {
            drives = File.listRoots();
        } else if (os.startsWith("mac")) {
            drives = new File("/Volumes").listFiles();
        } else {
            drives = new File("/media").listFiles();
        }
        List<File> dvs = Arrays.asList(drives);
        Collections.reverse(dvs);
        String[] dicomdir = { "DICOMDIR", "dicomdir", "DICOMDIR.", "dicomdir." };

        for (File drive : dvs) {
            // Detect read-only media
            if (drive.canRead() && !drive.canWrite() && !drive.isHidden()) {
                for (int j = 0; j < dicomdir.length; j++) {
                    File f = new File(drive, dicomdir[j]);
                    if (f.canRead()) {
                        return f;
                    }
                }
            }
        }
        return null;
    }
}
