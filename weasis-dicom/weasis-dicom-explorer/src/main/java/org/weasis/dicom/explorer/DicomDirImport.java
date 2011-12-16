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

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.net.URI;
import java.util.Properties;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.explorer.internal.Activator;

public class DicomDirImport extends AbstractItemDialogPage implements ImportDicom {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomDirImport.class);

    private static final String lastDirKey = "lastOpenDir";//$NON-NLS-1$

    private JCheckBox chckbxSearch;
    private JTextField textField;
    private File files;
    private final Properties props;
    private JCheckBox chckbxNewCheckBox;
    private JButton btnOpen;

    public DicomDirImport() {
        setTitle(Messages.getString("LocalImport.local_dev")); //$NON-NLS-1$
        props = new Properties();
        FileUtil.readProperties(new File(Activator.PREFERENCES.getDataFolder(), "local-import.properties"), props);//$NON-NLS-1$
        initGUI();
        initialize(true);
    }

    public void initGUI() {
        FlowLayout flowLayout = (FlowLayout) getLayout();
        flowLayout.setAlignment(FlowLayout.LEFT);
        setBorder(new TitledBorder(null, "DICOMDIR", TitledBorder.LEADING, TitledBorder.TOP, null, null));

        btnOpen = new JButton("Open");
        btnOpen.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
            }
        });
        add(btnOpen);

        chckbxNewCheckBox = new JCheckBox("Store in temporary cache");
        add(chckbxNewCheckBox);

    }

    protected void initialize(boolean afirst) {
        if (afirst) {

        }
    }

    public void browseImgFile() {
        String directory = getImportPath();
        if (directory == null) {
            directory = props.getProperty(lastDirKey, "");//$NON-NLS-1$
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
            // files = selectedFiles;
            // String lastDir = null;
            // if (files.length == 1) {
            // lastDir = files[0].getPath();
            // textField.setText(lastDir);
            // } else {
            // lastDir = files[0].getParent();
            //                textField.setText(Messages.getString("LocalImport.multi_dir")); //$NON-NLS-1$
            // }
            // if (lastDir != null) {
            // props.setProperty(lastDirKey, lastDir);
            // }
        }
    }

    public void resetSettingsToDefault() {
        initialize(false);
    }

    public void applyChange() {
        FileUtil.storeProperties(
            new File(Activator.PREFERENCES.getDataFolder(), "local-import.properties"), props, null);//$NON-NLS-1$
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

    @Override
    public void importDICOM(DicomModel dicomModel, JProgressBar info) {
        if (files == null) {
            String path = getImportPath();
            if (path != null) {
                File file = new File(path);
                if (file.canRead()) {
                    // files = new File[] { file };
                } else {
                    try {
                        file = new File(new URI(path));
                        if (file.canRead()) {
                            // files = new File[] { file };
                        }
                    } catch (Exception e) {
                        LOGGER.error("Cannot import DICOM from {}", path); //$NON-NLS-1$
                    }
                }
            }
        }
        if (files != null) {
            // LoadLocalDicom dicom = new LoadLocalDicom(files, chckbxSearch.isSelected(), dicomModel);
            // DicomModel.loadingExecutor.execute(dicom);
        }
    }
}
